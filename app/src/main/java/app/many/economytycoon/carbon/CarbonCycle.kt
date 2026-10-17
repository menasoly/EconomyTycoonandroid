package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.ema
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.ReasonCode

/*
 * The daily cycle. The order inside one day matters and is fixed:
 *   1. the period that has just ended is closed (month end: grid, monitoring, taxes, running costs; year end: compliance);
 *   2. the new year's permit supply, free allocation and reserve review are set up;
 *   3. today's emissions are measured;
 *   4. auctions, then deadlines, then offset projects and abatement measures advance.
 * A tick is never processed twice, and days are expected one after another.
 */

internal fun CarbonService.runDay(ctx: CarbonService.Ctx, activities: List<FacilityActivity>): CarbonStepResult {
    if (ctx.tick.value <= state.lastTick) return CarbonStepResult.EMPTY
    if (state.lastTick < 0L) seedOffsetMarket()
    if (ctx.tick.value > 0L && CarbonCalendar.isMonthStart(ctx.tick)) closePeriod(ctx)
    ensureYears(ctx)
    observe(ctx, activities)
    flushPendingFree(ctx)
    runAuctions(ctx)
    settleDeadlines(ctx)
    advanceOffsets(ctx)
    advanceAbatement(ctx)
    state.lastTick = ctx.tick.value
    return ctx.result()
}

// ---- measuring -----------------------------------------------------------------------------------

private fun CarbonService.observe(ctx: CarbonService.Ctx, activities: List<FacilityActivity>) {
    val alpha = Rate(params.smoothingPpm)
    val counted = HashSet<Pair<CompanyId, CountryId>>()
    for (a in activities.sortedBy { it.facility.value }) {
        if (!env.isActive(a.company)) continue
        val covered = state.regime(a.country)?.covers(a.family, ctx.year) == true
        val record = state.inventory.recordFor(a)
        if (record.owner != a.company) record.owner = a.company
        val effects = if (params.efficiencyRealisedInProduction) record.effects.filter { it.kind != AbatementKind.EFFICIENCY_RETROFIT } else record.effects
        val emissions = calculator.facilityDay(a, state.gridIntensity(a.country), effects)
        state.inventory.record(emissions, ctx.tick, ctx.year, covered, alpha)
        if (counted.add(a.company to a.country)) state.inventory.markDay(a.company, a.country, ctx.year)
    }
}

/** The part of a company's year that its regime prices: covered direct emissions, plus electricity if the regime reaches that far. */
internal fun coveredOf(cy: CompanyYear, step: RegimeStep): Tonnes =
    if (step.coversIndirect && cy.covered.isPositive) cy.covered + cy.indirect else cy.covered

// ---- month end -----------------------------------------------------------------------------------

private fun CarbonService.closePeriod(ctx: CarbonService.Ctx) {
    val last = Tick(ctx.tick.value - 1L)
    val year = CarbonCalendar.yearOf(last)
    val month = CarbonCalendar.monthOfYear(last)
    updateGrid(year)
    recordMonitoring(year, month)
    assessTaxes(ctx, year)
    payAbatementRunning(ctx)
    state.offsets.exchange.closeMonth()
    addMarketCredits()
    if (CarbonCalendar.isYearStart(ctx.tick)) closeYear(ctx, year)
}

/** Moves each country's grid intensity toward what its power stations released per unit of electricity this year. */
private fun CarbonService.updateGrid(year: Int) {
    for (n in state.inventory.nationalYears()) {
        if (n.year != year || !n.electricityProduced.isPositive) continue
        val sample = mulDiv(n.power.raw * 1_000L, app.many.economytycoon.core.Quantity.SCALE, n.electricityProduced.raw, RoundingMode.HALF_EVEN)
        val previous = state.gridIntensity(n.country)
        state.setGrid(n.country, if (previous == null) sample else ema(previous, sample, Rate(params.gridSmoothingPpm)))
    }
}

private fun CarbonService.recordMonitoring(year: Int, month: Int) {
    for (cy in state.inventory.companyYears()) {
        if (cy.year != year || !cy.covered.isPositive) continue
        val step = state.regime(cy.country)?.stepFor(year) ?: continue
        state.compliance.open(cy.company, cy.country).monthly[month] = coveredOf(cy, step).raw
    }
}

private fun CarbonService.assessTaxes(ctx: CarbonService.Ctx, year: Int) {
    for (cy in state.inventory.companyYears()) {
        if (cy.year != year || !cy.covered.isPositive || !env.isActive(cy.company)) continue
        val regime = state.regime(cy.country) ?: continue
        if (regime.kind != RegimeKind.CARBON_TAX) continue
        val step = regime.stepFor(year) ?: continue
        val account = state.compliance.open(cy.company, cy.country)
        val covered = coveredOf(cy, step)
        val due = Levies.taxDue(step, year, covered, account.taxAssessed)
        if (!due.isPositive) continue
        ctx.settle.charge("tax", company(cy.company), government(cy.country), due, ReasonCode.TAX, "carbon tax")
        account.taxAssessed += due
        takeRevenue(cy.country, regime, due)
        ctx.emit(CarbonEvent.TaxCharged(ctx.tick, cy.country, cy.company, due, covered))
    }
}

/** Counts money the government takes in through carbon pricing and sets the green fund's share aside. */
internal fun CarbonService.takeRevenue(country: CountryId, regime: CarbonRegime, amount: Money) {
    state.noteReceipt(country, amount)
    state.addToFund(country, Money(mulDiv(amount.raw, regime.greenFundSharePpm, Rate.ONE_PPM)))
}

private fun CarbonService.seedOffsetMarket() {
    val ex = state.offsets.exchange
    for (k in OffsetKind.values()) ex.restore(k, ex.stock(k) + OffsetProfiles.of(k).marketMonthly * 3L, ex.spot(k), ex.boughtThisMonth(k), ex.addedThisMonth(k))
}

private fun CarbonService.addMarketCredits() {
    val r = random.stream("carbon.offset-market")
    val jitter = params.marketSupplyJitterPpm
    for (k in OffsetKind.values()) {
        val swing = r.nextInRange(-jitter, jitter)
        state.offsets.exchange.add(k, OffsetProfiles.of(k).marketMonthly.times(Rate(Rate.ONE_PPM + swing)))
    }
}

// ---- year end ------------------------------------------------------------------------------------

private fun CarbonService.closeYear(ctx: CarbonService.Ctx, year: Int) {
    val companies = java.util.TreeSet<CompanyId>()
    for (cy in state.inventory.companyYears().filter { it.year == year && !it.closed }) {
        companies += cy.company
        val regime = state.regime(cy.country)
        val step = regime?.stepFor(year)
        if (regime == null || step == null || !regime.isRegulated || !cy.covered.isPositive) {
            state.reports.file(statement(cy, regime?.kind ?: RegimeKind.NONE, ComplianceStatus.UNREGULATED, Tonnes.ZERO, Tonnes.ZERO, Tonnes.ZERO, Tonnes.ZERO, Money.ZERO, Tonnes.ZERO))
            continue
        }
        closeRegulated(ctx, cy, regime, step, year)
    }
    state.inventory.close(year)
    state.inventory.prune(ctx.year, params.keepYears)
    state.reports.prune(ctx.year, params.keepYears)
    for (c in companies) refreshRating(ctx, c, year)
}

private fun CarbonService.closeRegulated(ctx: CarbonService.Ctx, cy: CompanyYear, regime: CarbonRegime, step: RegimeStep, year: Int) {
    val account = state.compliance.open(cy.company, cy.country)
    account.pending?.let { settleYear(ctx, account, it) }
    val covered = coveredOf(cy, step)
    val free = account.freeThisYear
    var status: ComplianceStatus
    var charge = Money.ZERO
    var obligation = Tonnes.ZERO
    if (covered < step.threshold) {
        status = ComplianceStatus.EXEMPT
        account.violations = 0
    } else when (regime.kind) {
        RegimeKind.CAP_AND_TRADE -> {
            obligation = covered + account.carry
            val deadline = Tick(ctx.tick.value + regime.surrenderWindowDays)
            account.pending = PendingYear(year, covered, obligation, deadline)
            account.carry = Tonnes.ZERO
            val market = state.market(cy.country)
            val usable = market?.holdings?.held(cy.company, firstValidVintage(regime, year), year) ?: Tonnes.ZERO
            status = if (usable >= obligation) ComplianceStatus.COMPLIANT else ComplianceStatus.SHORTFALL
        }
        RegimeKind.CARBON_TAX -> {
            status = ComplianceStatus.TAXED
            charge = account.taxAssessed
            account.violations = 0
        }
        RegimeKind.INTENSITY_STANDARD -> {
            val a = Levies.assess(step, year, covered, cy.coveredOutput)
            charge = a.levy
            status = if (a.levy.isPositive) ComplianceStatus.LEVIED else ComplianceStatus.COMPLIANT
            account.violations = 0
            if (charge.isPositive && env.isActive(cy.company)) {
                ctx.settle.charge("levy", company(cy.company), government(cy.country), charge, ReasonCode.TAX, "carbon intensity levy")
                takeRevenue(cy.country, regime, charge)
            }
        }
        RegimeKind.NONE -> status = ComplianceStatus.UNREGULATED
    }
    if (regime.kind != RegimeKind.CAP_AND_TRADE || status == ComplianceStatus.EXEMPT) {
        account.addRecord(
            ComplianceRecord(year, regime.kind, status, covered, obligation, Tonnes.ZERO, Tonnes.ZERO, Tonnes.ZERO, Tonnes.ZERO, Money.ZERO, charge, free),
            params.keepRecords,
        )
    }
    state.reports.file(statement(cy, regime.kind, status, obligation, Tonnes.ZERO, Tonnes.ZERO, Tonnes.ZERO, charge, free))
    account.monthly.clear()
    account.taxAssessed = Money.ZERO
    ctx.emit(CarbonEvent.YearClosed(ctx.tick, cy.country, cy.company, year, covered, status))
}

private fun CarbonService.statement(
    cy: CompanyYear, kind: RegimeKind, status: ComplianceStatus, obligation: Tonnes, surrendered: Tonnes, offsets: Tonnes, shortfall: Tonnes,
    charges: Money, free: Tonnes,
): AnnualStatement {
    val prior = state.reports.statement(cy.company, cy.country, cy.year - 1)?.footprint
    val change = if (prior == null || !prior.isPositive) null else mulDiv(cy.footprint.raw - prior.raw, Rate.ONE_PPM, prior.raw, RoundingMode.HALF_EVEN)
    return AnnualStatement(
        cy.company, cy.country, cy.year, cy.direct, cy.indirect, cy.biogenic, cy.covered, cy.avoided, cy.outputValue, cy.intensityGramsPerUnit,
        cy.dominantFamily(), kind, status, obligation, surrendered, offsets, shortfall, charges, free, change,
    )
}

/** Scores the company on its largest statement for [year] and files the rating, raising an event when the grade moves. */
internal fun CarbonService.refreshRating(ctx: CarbonService.Ctx, company: CompanyId, year: Int) {
    val all = state.reports.statementsOf(company)
    val mine = all.filter { it.year == year }
    val primary = mine.maxWithOrNull(compareBy<AnnualStatement>({ it.footprint }, { -it.country.value })) ?: return
    val benchmark = Benchmarks.forFamily(Benchmarks.compute(state.inventory.companiesIn(primary.country, year)), primary.dominantFamily)
    val violations = state.compliance.account(company, primary.country)?.violations ?: 0
    val rating = RatingModel.rate(primary, benchmark, violations, all.count { it.year < year })
    val before = state.reports.rating(company)
    state.reports.rate(rating)
    if (before?.grade != rating.grade) ctx.emit(CarbonEvent.RatingChanged(ctx.tick, primary.country, company, before?.grade, rating.grade, rating.score))
}

// ---- the compliance deadline ---------------------------------------------------------------------

private fun CarbonService.settleDeadlines(ctx: CarbonService.Ctx) {
    for (a in state.compliance.accounts()) {
        val p = a.pending ?: continue
        if (ctx.tick >= p.deadline) settleYear(ctx, a, p)
    }
}

/**
 * Settles a pending year. In order: permits the company holds are handed in; if the company allows it, offsets are
 * retired up to the regime's limit; if it allows borrowing, permits of the next vintage are handed in (the interest is
 * carried into next year's obligation); whatever is left is a shortfall, fined by the step's penalty with a surcharge
 * for repeat offenders, and carried over when the regime makes the company make it good.
 */
internal fun CarbonService.settleYear(ctx: CarbonService.Ctx, account: ComplianceAccount, p: PendingYear) {
    val country = account.key.country
    val company = account.key.company
    val regime = state.regime(country)
    val market = state.market(country)
    val step = regime?.stepFor(p.year)
    account.pending = null
    if (regime == null || step == null || market == null) {
        account.addRecord(
            ComplianceRecord(p.year, RegimeKind.NONE, ComplianceStatus.UNREGULATED, p.verified, p.obligation, p.surrendered, p.offsets, p.borrowed, Tonnes.ZERO, Money.ZERO, Money.ZERO, Tonnes.ZERO),
            params.keepRecords,
        )
        return
    }
    val active = env.isActive(company)
    if (active) {
        surrenderPermits(ctx, market, regime, company, p, p.outstanding)
        if (account.policy.autoOffsets && p.outstanding.isPositive) retireForCompliance(ctx, company, country, regime, p)
        if (account.policy.autoBorrow && p.outstanding.isPositive) borrowNext(ctx, account, market, regime, p)
    }
    val shortfall = p.outstanding
    val penalty = Levies.penalty(step, regime, shortfall, account.violations)
    val status: ComplianceStatus
    if (shortfall.isPositive) {
        status = ComplianceStatus.SHORTFALL
        if (active && penalty.isPositive) {
            ctx.settle.charge("penalty", company(company), government(country), penalty, ReasonCode.FEE, "carbon permit shortfall penalty")
            takeRevenue(country, regime, penalty)
        }
        account.violations += 1
        if (regime.makeGood) account.carry += shortfall
    } else {
        status = ComplianceStatus.COMPLIANT
        account.violations = 0
    }
    val earlier = state.reports.statement(company, country, p.year)
    val free = earlier?.freePermits ?: Tonnes.ZERO
    val record = ComplianceRecord(p.year, regime.kind, status, p.verified, p.obligation, p.surrendered, p.offsets, p.borrowed, shortfall, penalty, penalty, free)
    account.addRecord(record, params.keepRecords)
    if (earlier != null) {
        state.reports.file(earlier.copy(status = status, surrendered = p.surrendered + p.borrowed, offsetsUsed = p.offsets, shortfall = shortfall, charges = penalty))
    }
    ctx.emit(CarbonEvent.TrueUp(ctx.tick, country, company, record))
    refreshRating(ctx, company, p.year)
}

private fun CarbonService.retireForCompliance(ctx: CarbonService.Ctx, company: CompanyId, country: CountryId, regime: CarbonRegime, p: PendingYear) {
    for (kind in regime.eligibleOffsets) {
        val room = offsetAllowance(regime, p)
        if (!room.isPositive) return
        val (taken, cost) = state.offsets.debit(company, kind, room)
        if (!taken.isPositive) continue
        p.offsets += taken
        state.offsets.noteRetired(company, country, p.year, taken)
        ctx.settle.writeDown(company(company), Chart.INTANGIBLES, cost, "credits retired")
    }
}

private fun CarbonService.borrowNext(ctx: CarbonService.Ctx, account: ComplianceAccount, market: PermitMarket, regime: CarbonRegime, p: PendingYear) {
    val limit = (p.verified.times(Rate(regime.borrowLimitPpm)) - p.borrowed).coerceAtLeast(Tonnes.ZERO)
    val want = Tonnes.min(limit, p.outstanding)
    if (!want.isPositive) return
    val taken = market.holdings.take(account.key.company, want, p.year + 1, p.year + 1)
    if (!taken.taken.isPositive) return
    p.borrowed += taken.taken
    account.carry += taken.taken.times(Rate(regime.borrowInterestPpm))
    ctx.settle.writeDown(company(account.key.company), Chart.INTANGIBLES, taken.cost, "permits borrowed against ${p.year}")
}
