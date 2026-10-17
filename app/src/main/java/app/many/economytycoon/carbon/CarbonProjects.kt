package app.many.economytycoon.carbon

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.ReasonCode

// ---- offsets: buying, retiring, funding -----------------------------------------------------------

internal fun CarbonService.buyOffsets(ctx: CarbonService.Ctx, c: CarbonCommand.BuyOffsets): Outcome<CarbonEffect> {
    if (c.quantity < params.minTrade) return Outcome.reject(Rules.INVALID_QUANTITY, "the smallest purchase is ${params.minTrade.format()} t")
    val exchange = state.offsets.exchange
    val quote = exchange.quote(c.kind, c.quantity) ?: return Outcome.reject(Rules.INSUFFICIENT_STOCK, "the market holds only ${exchange.stock(c.kind).format()} t of ${c.kind.name.lowercase()} credits")
    if (!ctx.cash.spend(c.company, quote.total)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "you could not pay ${quote.total}")
    exchange.remove(c.kind, c.quantity)
    state.offsets.credit(c.company, c.kind, c.quantity, quote.total)
    ctx.settle.purchase("credits", company(c.company), contractors, quote.total, "carbon credits bought")
    return Outcome.ok(CarbonEffect(ctx.settle.build(), "${c.quantity.format()} t of credits bought at ${quote.unitPrice} each"))
}

internal fun CarbonService.retireOffsets(ctx: CarbonService.Ctx, c: CarbonCommand.RetireOffsets): Outcome<CarbonEffect> {
    if (!c.quantity.isPositive) return Outcome.reject(Rules.INVALID_QUANTITY, "retire a positive amount")
    if (state.offsets.held(c.company, c.kind) < c.quantity) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "you do not hold that many credits")
    var year = ctx.year
    var message = "${c.quantity.format()} t of credits retired"
    var pending: PendingYear? = null
    if (c.forCompliance) {
        val regime = state.regime(c.country)
        val p = state.compliance.account(c.company, c.country)?.pending
        if (regime == null || p == null) return Outcome.reject(Rules.WRONG_STATE, "no year is waiting to be settled")
        if (c.kind !in regime.eligibleOffsets) return Outcome.reject(Rules.REGULATION, "${c.kind.name.lowercase()} credits are not accepted in that country")
        if (c.quantity > p.outstanding) return Outcome.reject(Rules.INVALID_QUANTITY, "you only owe ${p.outstanding.format()} t")
        val limit = offsetAllowance(regime, p)
        if (c.quantity > limit) return Outcome.reject(Rules.LIMIT_EXCEEDED, "only ${limit.format()} t more may be met with credits")
        pending = p
        year = p.year
        message = "${c.quantity.format()} t of credits retired against ${p.year}"
    }
    val (taken, cost) = state.offsets.debit(c.company, c.kind, c.quantity)
    pending?.let { it.offsets += taken }
    state.offsets.noteRetired(c.company, c.country, year, taken)
    ctx.settle.writeDown(company(c.company), Chart.INTANGIBLES, cost, "credits retired")
    return Outcome.ok(CarbonEffect(ctx.settle.build(), message))
}

/** How much more of an obligation credits may still cover: the regime's share of the obligation less what is already met that way. */
internal fun CarbonService.offsetAllowance(regime: CarbonRegime, pending: PendingYear): Tonnes {
    val step = regime.stepFor(pending.year) ?: return Tonnes.ZERO
    val limit = pending.obligation.times(Rate(step.offsetLimitPpm))
    return Tonnes.min((limit - pending.offsets).coerceAtLeast(Tonnes.ZERO), pending.outstanding)
}

internal fun CarbonService.fundOffsetProject(ctx: CarbonService.Ctx, c: CarbonCommand.FundOffsetProject): Outcome<CarbonEffect> {
    if (c.annual < params.minOffsetProject || c.annual > params.maxOffsetProject) {
        return Outcome.reject(Rules.INVALID_QUANTITY, "a project yields between ${params.minOffsetProject.format()} and ${params.maxOffsetProject.format()} t a year")
    }
    if (state.offsets.projectsOf(c.company).count { it.isLive } >= params.maxOffsetProjectsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many projects in progress")
    val profile = OffsetProfiles.of(c.kind)
    val capex = c.annual.cost(profile.upfrontPerAnnualTonne)
    if (!ctx.cash.spend(c.company, capex)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the project costs $capex")
    val project = OffsetProject(state.counters.offsetProject(), c.kind, c.company, c.host, c.annual, capex, ctx.tick)
    OffsetLifecycle.schedule(project, profile)
    state.offsets.put(project)
    ctx.settle.purchase("project", company(c.company), contractors, capex, "carbon offset project")
    return Outcome.ok(CarbonEffect(ctx.settle.build(), "Project ${project.id} started; credits begin after ${profile.leadDays + profile.validationDays + profile.issuanceEveryDays} days"))
}

/** Advances the offset projects and books what they produce. */
internal fun CarbonService.advanceOffsets(ctx: CarbonService.Ctx) {
    val outcomes = OffsetLifecycle.advance(state.offsets, ctx.tick) { random.stream("carbon.offsets", it.value) }
    for (o in outcomes) {
        val p = o.project
        val owner = p.owner
        when (o) {
            is OffsetOutcome.Validated, is OffsetOutcome.Reversed -> Unit
            is OffsetOutcome.Rejected -> if (owner != null) {
                val back = Money(mulDiv(o.writtenOff.raw, params.offsetRefundPpm, Rate.ONE_PPM))
                ctx.settle.refund("project", contractors, company(owner), back, Chart.INTANGIBLES, "offset project rejected")
                ctx.settle.writeDown(company(owner), Chart.INTANGIBLES, o.writtenOff - back, "offset project rejected")
            }
            is OffsetOutcome.Issued -> if (owner != null) state.offsets.credit(owner, p.kind, o.credits, o.cost) else state.offsets.exchange.add(p.kind, o.credits)
            is OffsetOutcome.Completed -> if (owner != null) {
                state.offsets.credit(owner, p.kind, o.released, Money.ZERO)
                ctx.settle.writeDown(company(owner), Chart.INTANGIBLES, o.writtenOff, "offset project ended")
            } else state.offsets.exchange.add(p.kind, o.released)
        }
        ctx.emit(CarbonEvent.OffsetProjectChanged(ctx.tick, p.host, o))
    }
}

// ---- abatement -----------------------------------------------------------------------------------

internal fun CarbonService.startAbatement(ctx: CarbonService.Ctx, c: CarbonCommand.StartAbatement): Outcome<CarbonEffect> {
    if (env.ownerOf(c.facility) != c.company) return Outcome.reject(Rules.NOT_OWNER, "that is not your facility")
    val record = state.inventory.facility(c.facility) ?: return Outcome.reject(Rules.PREREQUISITE_MISSING, "the facility has not been measured yet")
    val live = state.abatement.liveAt(c.facility)
    if (live.size >= params.maxLiveAbatementPerFacility) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many measures under way at that facility")
    if (live.any { it.kind == c.kind }) return Outcome.reject(Rules.DUPLICATE, "that measure is already under way")
    val option = catalog.optionsFor(record, env.buildCost(c.facility)).firstOrNull { it.kind == c.kind }
        ?: return Outcome.reject(Rules.PREREQUISITE_MISSING, "that measure is not available at this facility")
    if (!ctx.cash.spend(c.company, option.capex)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the measure costs ${option.capex}")
    val completes = ctx.tick + option.buildDays.toLong()
    val ends = completes + option.lifeYears.toLong() * CarbonCalendar.DAYS_PER_YEAR
    val project = AbatementProject(
        state.counters.abatement(), c.company, c.facility, c.kind, option.capex, option.opexPerYear, option.effect, option.annualAbatement, ctx.tick, completes, ends,
    )
    state.abatement.put(project)
    ctx.settle.capital("abate", company(c.company), contractors, option.capex, "emissions abatement: ${c.kind.label}")
    return Outcome.ok(CarbonEffect(ctx.settle.build(), "${c.kind.label} ordered: ${option.annualAbatement.format()} t a year after ${option.buildDays} days"))
}

internal fun CarbonService.cancelAbatement(ctx: CarbonService.Ctx, c: CarbonCommand.CancelAbatement): Outcome<CarbonEffect> {
    val p = state.abatement.project(c.project) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such project")
    if (p.company != c.company) return Outcome.reject(Rules.NOT_OWNER, "that is not your project")
    when (p.status) {
        AbatementStatus.BUILDING -> {
            p.status = AbatementStatus.CANCELLED
            val back = Money(mulDiv(p.capex.raw, params.abatementRefundPpm, Rate.ONE_PPM))
            ctx.settle.refund("abate", contractors, company(c.company), back, Chart.FIXED_ASSETS, "abatement cancelled")
            ctx.settle.writeDown(company(c.company), Chart.FIXED_ASSETS, p.capex - back, "abatement cancelled")
            return Outcome.ok(CarbonEffect(ctx.settle.build(), "Project cancelled; $back recovered"))
        }
        AbatementStatus.OPERATING -> {
            retireAbatement(ctx, p)
            return Outcome.ok(CarbonEffect(ctx.settle.build(), "Measure decommissioned"))
        }
        else -> return Outcome.reject(Rules.WRONG_STATE, "that project is already over")
    }
}

private fun CarbonService.retireAbatement(ctx: CarbonService.Ctx, p: AbatementProject) {
    p.status = AbatementStatus.RETIRED
    val record = state.inventory.facility(p.facility)
    record?.installed?.remove(p.effect)
    ctx.emit(CarbonEvent.AbatementRetired(ctx.tick, record?.country ?: return, p.company, p.facility, p.kind))
}

/** Finishes measures whose build time has passed, pays their subsidy, and retires those at the end of their life. */
internal fun CarbonService.advanceAbatement(ctx: CarbonService.Ctx) {
    for (p in state.abatement.projects()) {
        if (p.status == AbatementStatus.BUILDING && ctx.tick >= p.completesAt) completeAbatement(ctx, p)
        else if (p.status == AbatementStatus.OPERATING && ctx.tick >= p.endsAt) retireAbatement(ctx, p)
    }
}

private fun CarbonService.completeAbatement(ctx: CarbonService.Ctx, p: AbatementProject) {
    p.status = AbatementStatus.OPERATING
    val record = state.inventory.facility(p.facility) ?: return
    record.installed += p.effect
    val country = record.country
    val share = state.regime(country)?.stepFor(ctx.year)?.abatementSubsidyPpm ?: 0L
    val wanted = Money(mulDiv(p.capex.raw, share, Rate.ONE_PPM)) + greenHook.bonus(p.company, country, p.kind, p.capex)
    val grant = Money.min(wanted, Money.min(state.greenFund(country), ctx.cash.governmentAvailable(country)))
    if (grant.isPositive && ctx.cash.governmentSpend(country, grant)) {
        ctx.settle.subsidy("abate", government(country), company(p.company), grant, "support for emissions abatement")
        state.drawFromFund(country, grant)
        p.addSubsidy(grant)
    }
    ctx.emit(CarbonEvent.AbatementDone(ctx.tick, country, p.company, p.facility, p.kind, p.subsidy))
}

/** Running costs of the measures in operation, paid once a month. */
internal fun CarbonService.payAbatementRunning(ctx: CarbonService.Ctx) {
    for (p in state.abatement.projects()) {
        if (p.status != AbatementStatus.OPERATING || !env.isActive(p.company)) continue
        val monthly = Money(p.opexPerYear.raw / 12L)
        ctx.settle.running("abate", company(p.company), contractors, monthly, "running costs of ${p.kind.label.lowercase()}")
        p.addOpex(monthly)
    }
}

// ---- border adjustment ---------------------------------------------------------------------------

/** Assesses the carbon border adjustment on a batch of trade flows. */
internal fun CarbonService.assessBorder(ctx: CarbonService.Ctx, flows: List<TradeFlow>, footprints: ProductFootprints): CarbonStepResult {
    for (flow in flows) {
        val importPolicy = state.border.policy(flow.to)?.takeIf { it.enabled }
        val exportPolicy = state.border.policy(flow.from)?.takeIf { it.enabled }
        if (importPolicy == null && exportPolicy == null) continue
        val grams = footprints.gramsPerUnit(flow.good)
        val a = BorderAdjuster.assess(flow, grams, carbonPrice(flow.from, ctx.year), carbonPrice(flow.to, ctx.year), importPolicy, exportPolicy, ctx.year)
        val importer = flow.importer
        if (a.levy.isPositive && importer != null && env.isActive(importer)) {
            ctx.settle.charge("border", company(importer), government(flow.to), a.levy, ReasonCode.TAX, "carbon border adjustment")
            state.border.noteLevy(flow.to, a.levy)
            state.noteReceipt(flow.to, a.levy)
            ctx.emit(CarbonEvent.BorderLevy(ctx.tick, flow.to, importer, a.levy, a.embedded))
        }
        val exporter = flow.exporter
        if (a.rebate.isPositive && exporter != null && env.isActive(exporter) && ctx.cash.governmentSpend(flow.from, a.rebate)) {
            ctx.settle.subsidy("border", government(flow.from), company(exporter), a.rebate, "carbon border rebate")
            state.border.noteRebate(flow.from, a.rebate)
        }
    }
    return ctx.result()
}
