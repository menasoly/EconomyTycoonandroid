package app.many.economytycoon.workforce

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CohortId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.PaymentLine
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.finance.SettlementResult
import app.many.economytycoon.finance.SettlementStatus

data class WorkforceParams(
    val payIntervalTicks: Int = 7,
    /** Share of jobseekers that apply for openings each tick. */
    val applicantFlowPpm: Long = 60_000,
    val payrollTaxPpm: Long = 100_000,
    val contractTermTicks: Int = 360,
    val trainingCostShareOfWage: Long = 300_000,
)

data class CompanyWorkforceStats(
    val company: CompanyId,
    val headcount: Long,
    val pending: Long,
    val dailyPayroll: Money,
    val averageMoralePpm: Long,
    val averageSkillPpm: Long,
    val vacancies: Long,
    val onStrike: Long,
    val unpaidTicks: Int,
)

/**
 * Employment engine. Workers are tracked as cohorts; hiring draws on labor-market supply, wages follow
 * the market, morale and pay punctuality drive attrition, and payroll leaves as settlement intents.
 */
class WorkforceService(
    private val ids: IdAllocators,
    val catalog: RoleCatalog,
    val markets: LaborMarkets,
    val params: WorkforceParams = WorkforceParams(),
) : Hashable {
    private val cohorts = java.util.TreeMap<CohortKey, Cohort>()
    private val byId = java.util.TreeMap<CohortId, Cohort>()
    private val unpaid = java.util.TreeMap<CompanyId, Int>()
    private val benefits = java.util.TreeMap<CompanyId, Long>()
    private val reputation = java.util.TreeMap<CompanyId, Long>()
    private val contracts = java.util.TreeMap<CompanyId, MutableList<LaborContract>>()
    private val severanceOwed = java.util.TreeMap<CompanyId, Money>()
    private val layoffShock = java.util.TreeMap<CohortKey, Long>()
    private var lastPayTick = Tick(0)

    /**
     * Where the skill term of productivity comes from instead of the cohort's own plain skill number. Null (the default)
     * keeps the plain number; the talent module sets it, and only for a cohort it knows, once it is switched on.
     */
    var skillSource: ((CohortKey) -> Long?)? = null

    /** Share of a cohort away on a course today, ppm. Set by the talent module each tick; empty (and not saved) otherwise. */
    private val awayPpm = java.util.TreeMap<CohortKey, Long>()

    fun cohort(key: CohortKey): Cohort? = cohorts[key]
    fun cohort(id: CohortId): Cohort? = byId[id]
    fun all(): List<Cohort> = cohorts.values.toList()
    fun cohortsOf(company: CompanyId): List<Cohort> = cohorts.values.filter { it.key.company == company }
    fun cohortsAt(facility: app.many.economytycoon.core.FacilityId): List<Cohort> = cohorts.values.filter { it.key.facility == facility }

    fun benefits(company: CompanyId) = benefits[company] ?: 0L
    fun reputation(company: CompanyId) = reputation[company] ?: 500_000L
    fun unpaidTicks(company: CompanyId) = unpaid[company] ?: 0
    fun contractsOf(company: CompanyId): List<LaborContract> = contracts[company]?.toList() ?: emptyList()
    fun severanceOwed(company: CompanyId): Money = severanceOwed[company] ?: Money.ZERO

    // ----- configuration --------------------------------------------------------------------

    /** Creates the cohort if missing, paying the going market wage. */
    fun ensure(key: CohortKey): Cohort = cohorts.getOrPut(key) {
        val role = catalog.role(key.role)
        val c = Cohort(ids.cohort(), key, markets.marketWage(MarketKey(key.region, key.role)))
        c.skillPpm = when (role.tier) { SkillTier.BASIC -> 350_000L; SkillTier.TRAINED -> 450_000L; SkillTier.SPECIALIST -> 550_000L; SkillTier.EXPERT -> 650_000L }
        byId[c.id] = c
        c
    }

    fun setTarget(key: CohortKey, target: Long): Outcome<Cohort> {
        if (target < 0) return Outcome.reject(Rules.INVALID_QUANTITY, "target cannot be negative")
        val c = ensure(key)
        c.target = target
        return Outcome.ok(c)
    }

    fun setWage(key: CohortKey, wage: Money): Outcome<Cohort> {
        if (wage.raw <= 0) return Outcome.reject(Rules.INVALID_PRICE, "wage must be positive")
        val min = markets.minimumWage(key.region)
        if (wage < min) return Outcome.reject(Rules.REGULATION, "below the legal minimum wage")
        val c = ensure(key)
        c.wage = wage
        return Outcome.ok(c)
    }

    fun setLoad(key: CohortKey, loadPpm: Long) { cohorts[key]?.loadPpm = loadPpm.coerceIn(0L, 2_000_000L) }
    fun setTraining(key: CohortKey, intensityPpm: Long) { cohorts[key]?.trainingPpm = intensityPpm.coerceIn(0L, 500_000L) }
    fun setBenefits(company: CompanyId, ppm: Long) { benefits[company] = ppm.coerceIn(0L, 300_000L) }
    fun setReputation(company: CompanyId, ppm: Long) { reputation[company] = ppm.coerceIn(0L, 1_000_000L) }
    fun setUnionized(key: CohortKey, union: Boolean) { cohorts[key]?.unionized = union }

    /** Puts a cohort on strike until the given tick; production at its facility stalls while it lasts. */
    fun startStrike(key: CohortKey, until: Tick) { cohorts[key]?.let { it.strikeUntil = maxOf(it.strikeUntil, until.value) } }

    /** Ends a strike early, for a settlement. */
    fun endStrike(key: CohortKey) { cohorts[key]?.strikeUntil = -1 }

    /** Raises a cohort's contract wage by a share, never below the legal minimum. Returns the new wage. */
    fun raiseWage(key: CohortKey, raisePpm: Long): Money? {
        val c = cohorts[key] ?: return null
        val raised = Money(c.wage.raw + mulDiv(c.wage.raw, raisePpm, 1_000_000L))
        c.wage = Money.max(raised, markets.minimumWage(key.region))
        return c.wage
    }

    fun adjustMorale(key: CohortKey, deltaPpm: Long) { cohorts[key]?.let { it.moralePpm = (it.moralePpm + deltaPpm).coerceIn(0L, 1_000_000L) } }

    fun addContract(c: LaborContract) { contracts.getOrPut(c.company) { ArrayList() }.add(c) }

    // ----- derived quantities ---------------------------------------------------------------

    fun wageRatio(c: Cohort): Long {
        val market = markets.marketWage(MarketKey(c.key.region, c.key.role))
        return if (market.raw == 0L) 1_000_000L else mulDiv(c.wage.raw, 1_000_000L, market.raw)
    }

    fun productivity(c: Cohort, tick: Tick): Long =
        Morale.productivity(skillSource?.invoke(c.key) ?: c.skillPpm, c.moralePpm, c.burnoutPpm, c.onStrike(tick))

    /** Labor units delivered per tick: heads at work times their productivity, in 1/1000 of a head. */
    fun effectiveLabor(key: CohortKey, tick: Tick): Long {
        val c = cohorts[key] ?: return 0L
        var atWork = c.headcount * (1_000_000L - c.trainingPpm / 2) / 1_000_000L
        val away = awayPpm[key] ?: 0L
        if (away > 0L) atWork = atWork * (1_000_000L - away.coerceAtMost(1_000_000L)) / 1_000_000L
        return mulDiv(atWork * 1000L, productivity(c, tick), 1_000_000L)
    }

    /** Fraction of the required headcount actually staffed, ppm, capped at 1.0. */
    fun staffingPpm(key: CohortKey, required: Long, tick: Tick): Long {
        if (required <= 0) return 1_000_000L
        val labor = effectiveLabor(key, tick)
        return mulDiv(labor, 1_000_000L, required * 1000L).coerceIn(0L, 1_000_000L)
    }

    fun stats(company: CompanyId, tick: Tick): CompanyWorkforceStats {
        val cs = cohortsOf(company)
        val head = cs.sumOf { it.headcount }
        var moraleW = 0L; var skillW = 0L
        for (c in cs) { moraleW += c.moralePpm * c.headcount; skillW += c.skillPpm * c.headcount }
        return CompanyWorkforceStats(
            company, head, cs.sumOf { it.pendingCount }, Money.sum(cs.map { it.wage * it.headcount }),
            if (head == 0L) 0L else moraleW / head, if (head == 0L) 0L else skillW / head,
            cs.sumOf { it.openings }, cs.filter { it.onStrike(tick) }.sumOf { it.headcount }, unpaidTicks(company),
        )
    }

    fun employedInRegion(region: RegionId): Long = cohorts.values.filter { it.key.region == region }.sumOf { it.headcount }
    fun employedBy(company: CompanyId): Long = cohortsOf(company).sumOf { it.headcount }

    fun averageWageInRegion(region: RegionId): Money {
        var total = 0L; var heads = 0L
        for (c in cohorts.values) if (c.key.region == region) { total += c.wage.raw * c.headcount; heads += c.headcount }
        return if (heads == 0L) Money.ZERO else Money(total / heads)
    }

    // ----- the tick -------------------------------------------------------------------------

    fun beginTick() {
        markets.beginTick()
        if (awayPpm.isNotEmpty()) awayPpm.clear()
    }

    // ----- changes asked for by the talent module -----------------------------------------------

    /** Records the share of a cohort that is away on a course for today's output. */
    fun setAwayShare(key: CohortKey, ppm: Long) {
        if (cohorts[key] == null) return
        if (ppm <= 0L) awayPpm.remove(key) else awayPpm[key] = ppm.coerceAtMost(1_000_000L)
    }

    /** Adds people who have just been hired (graduates, key hires) to a cohort, up to its open places. Returns how many joined. */
    fun addPeople(key: CohortKey, count: Long): Long {
        val c = cohorts[key] ?: return 0L
        val n = count.coerceAtMost(c.openings)
        if (n <= 0L) return 0L
        markets.take(MarketKey(key.region, key.role), n)
        join(c, n)
        return n
    }

    /** Moves people between two cohorts of the same or different companies; the destination is created if missing and never left under its head count. */
    fun movePeople(from: CohortKey, to: CohortKey, count: Long, newWage: Money?): Long {
        val src = cohorts[from] ?: return 0L
        val n = count.coerceIn(0L, src.headcount)
        if (n <= 0L) return 0L
        src.headcount -= n
        val dst = ensure(to)
        join(dst, n)
        if (dst.target < dst.headcount) dst.target = dst.headcount
        if (newWage != null && newWage > dst.wage) dst.wage = newWage
        return n
    }

    /** Removes people for good (retirement, a key person leaving, emigration). They do not return to the local job seekers. */
    fun removePeople(key: CohortKey, count: Long): Long {
        val c = cohorts[key] ?: return 0L
        val n = count.coerceIn(0L, c.headcount)
        c.headcount -= n
        return n
    }

    /** Raises a cohort's wage to [wage]; never lowers it. */
    fun raiseWageTo(key: CohortKey, wage: Money) {
        val c = cohorts[key] ?: return
        if (wage > c.wage) c.wage = wage
    }

    private fun join(c: Cohort, joined: Long) {
        val total = c.headcount + joined
        c.tenureTicks = c.tenureTicks * c.headcount / total
        c.moralePpm = (c.moralePpm * c.headcount + 650_000L * joined) / total
        c.headcount = total
    }

    /** Hiring, attrition, morale and skill dynamics for one tick. */
    fun step(tick: Tick, random: RandomProvider) {
        arrivals(tick)
        allocateHiring(tick)
        layoffs(tick)
        for (c in cohorts.values.toList()) dynamics(c, tick)
        accrueWages(tick)
        renegotiate(tick, random)
        // Record the market's view for wage adjustment.
        val demandByMarket = java.util.TreeMap<MarketKey, Long>()
        val employedByMarket = java.util.TreeMap<MarketKey, Long>()
        for (c in cohorts.values) {
            val k = MarketKey(c.key.region, c.key.role)
            demandByMarket[k] = (demandByMarket[k] ?: 0L) + c.openings
            employedByMarket[k] = (employedByMarket[k] ?: 0L) + c.headcount
        }
        for ((k, d) in demandByMarket) { val m = markets.market(k); m.demand = d; m.vacancies = d }
        for ((k, e) in employedByMarket) markets.market(k).employed = e
        if (tick.value > 0 && tick.value % 30L == 0L) markets.adjustWages()
        for ((k, v) in layoffShock.toList()) { val nv = v * 9 / 10; if (nv < 1_000L) layoffShock.remove(k) else layoffShock[k] = nv }
    }

    private fun arrivals(tick: Tick) {
        for (c in cohorts.values) {
            if (c.pending.isEmpty()) continue
            val it = c.pending.iterator()
            var joined = 0L
            while (it.hasNext()) {
                val p = it.next()
                if (p.arrival <= tick) { joined += p.count; it.remove() }
            }
            if (joined > 0) {
                val total = c.headcount + joined
                // New hires start at a lower tenure, dragging the average down.
                c.tenureTicks = c.tenureTicks * c.headcount / total
                c.moralePpm = (c.moralePpm * c.headcount + 650_000L * joined) / total
                c.headcount = total
            }
        }
    }

    private fun allocateHiring(tick: Tick) {
        // Group cohorts with openings by market so they share one pool of jobseekers.
        val groups = java.util.TreeMap<MarketKey, MutableList<Cohort>>()
        for (c in cohorts.values) if (c.openings > 0 && !c.onStrike(tick)) groups.getOrPut(MarketKey(c.key.region, c.key.role)) { ArrayList() }.add(c)
        for ((mk, list) in groups) {
            val m = markets.market(mk)
            val flow = mulDiv(m.available, params.applicantFlowPpm, 1_000_000L).coerceAtLeast(if (m.available > 0) 1L else 0L)
            if (flow == 0L) continue
            val weights = LongArray(list.size) { i ->
                val c = list[i]
                mulDiv(c.openings, Morale.appeal(wageRatio(c), reputation(c.key.company)), 1_000_000L).coerceAtLeast(1L)
            }
            val shares = splitLargestRemainder(flow.coerceAtMost(list.sumOf { it.openings }), weights)
            val role = catalog.role(mk.role)
            for (i in list.indices) {
                val c = list[i]
                val want = shares[i].coerceAtMost(c.openings)
                if (want <= 0) continue
                val got = markets.take(mk, want)
                if (got > 0) c.pending.add(PendingHire(tick + role.timeToFillTicks.toLong(), got))
            }
        }
    }

    private fun layoffs(tick: Tick) {
        for (c in cohorts.values) {
            val excess = c.headcount - c.target
            if (excess <= 0) continue
            // Layoffs take effect immediately and are paid severance; people return to the job pool.
            val owed = Negotiation.severance(c.wage, excess, c.tenureTicks, severanceDays(c.key.company))
            severanceOwed[c.key.company] = (severanceOwed[c.key.company] ?: Money.ZERO) + owed
            c.headcount -= excess
            markets.release(MarketKey(c.key.region, c.key.role), excess)
            val shock = if (c.headcount + excess == 0L) 0L else mulDiv(excess, 1_000_000L, c.headcount + excess)
            layoffShock[c.key] = (layoffShock[c.key] ?: 0L) + shock
        }
    }

    private fun severanceDays(company: CompanyId): Int = contracts[company]?.firstOrNull()?.severanceDaysPerYear ?: 15

    private fun dynamics(c: Cohort, tick: Tick) {
        val company = c.key.company
        val onStrike = c.onStrike(tick)
        val inputs = MoraleInputs(
            wageRatioPpm = wageRatio(c), unpaidTicks = unpaidTicks(company), loadPpm = c.loadPpm,
            benefitsPpm = benefits(company), layoffShockPpm = layoffShock[c.key] ?: 0L, tenureTicks = c.tenureTicks, onStrike = onStrike,
        )
        c.moralePpm = Morale.step(c.moralePpm, Morale.target(inputs))
        c.burnoutPpm = Morale.burnoutStep(c.burnoutPpm, c.loadPpm, c.moralePpm)
        if (c.headcount > 0) c.tenureTicks++
        // Attrition with remainder carry so small cohorts still churn at the right average rate.
        val rate = Morale.quitRatePpm(wageRatio(c), c.moralePpm, c.burnoutPpm)
        val numerator = c.headcount * rate + c.carry[0]
        c.carry[0] = numerator % 1_000_000L
        val quits = (numerator / 1_000_000L).coerceAtMost(c.headcount)
        if (quits > 0) {
            c.headcount -= quits
            markets.release(MarketKey(c.key.region, c.key.role), quits)
        }
        // Training raises skill up to the cap.
        if (c.trainingPpm > 0 && c.headcount > 0) {
            val role = catalog.role(c.key.role)
            val gain = Morale.skillGain(role.trainingTicks, c.trainingPpm)
            c.skillPpm = (c.skillPpm + gain).coerceAtMost(1_000_000L)
        }
    }

    private fun accrueWages(tick: Tick) {
        for (c in cohorts.values) {
            if (c.headcount == 0L) continue
            if (c.onStrike(tick)) continue // strikers are not paid
            val gross = c.wage * c.headcount
            val withBenefits = gross + gross.times(Rate(benefits(c.key.company)))
            c.accruedWages += withBenefits
        }
    }

    private fun renegotiate(tick: Tick, random: RandomProvider) {
        for ((company, list) in contracts) {
            val rnd = random.stream("workforce.negotiation", company.value)
            val iterator = list.listIterator()
            while (iterator.hasNext()) {
                val k = iterator.next()
                if (tick < k.end) continue
                val targets = cohortsOf(company).filter { k.role == null || it.key.role == k.role }
                if (targets.isEmpty()) { iterator.remove(); continue }
                val c = targets.first()
                val market = markets.marketWage(MarketKey(c.key.region, c.key.role))
                val res = Negotiation.resolve(rnd, c.wage, market, Rate.percent(3), c.moralePpm, reputation(company), k.collective)
                for (t in targets) {
                    when (res.outcome) {
                        NegotiationOutcome.STRIKE -> t.strikeUntil = tick.value + res.strikeTicks
                        else -> t.wage = Money.max(res.newWage, markets.minimumWage(t.key.region))
                    }
                }
                val renewed = k.copy(wage = res.newWage, start = tick, end = tick + params.contractTermTicks.toLong())
                iterator.set(renewed)
            }
        }
    }

    // ----- payroll --------------------------------------------------------------------------

    /**
     * Pay-day settlement intents: wages to households, payroll tax to government, severance and benefits
     * included. Returns an empty list on non-pay ticks.
     */
    fun payrollIntents(tick: Tick, households: LedgerOwner, government: (CompanyId) -> LedgerOwner): List<SettlementIntent> {
        val onPayDay = tick.value > 0 && tick.value % params.payIntervalTicks == 0L
        val out = ArrayList<SettlementIntent>()
        val companies = (cohorts.values.map { it.key.company } + severanceOwed.keys).distinct().sorted()
        for (company in companies) {
            val owner = LedgerOwner.company(company)
            var wages = Money.ZERO
            if (onPayDay) for (c in cohortsOf(company)) { wages += c.accruedWages }
            val severance = severanceOwed[company] ?: Money.ZERO
            val total = wages + severance
            if (total.isPositive) {
                val lines = ArrayList<PaymentLine>()
                if (wages.isPositive) lines.add(PaymentLine(Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER, wages))
                if (severance.isPositive) lines.add(PaymentLine(Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER, severance))
                out.add(SettlementIntent("payroll:${company.value}:${tick.value}", owner, households, SettlementPriority.WAGES, ReasonCode.WAGES, lines, deferrable = true, memo = "payroll"))
                val payroll = wages + severance
                val tax = payroll.times(Rate(params.payrollTaxPpm))
                if (tax.isPositive) {
                    out.add(SettlementIntent.simple("payroll-tax:${company.value}:${tick.value}", owner, government(company), tax,
                        SettlementPriority.STATUTORY, ReasonCode.TAX, Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER, deferrable = true, memo = "payroll tax"))
                }
            }
            if (onPayDay) for (c in cohortsOf(company)) c.accruedWages = Money.ZERO
            severanceOwed.remove(company)
        }
        if (onPayDay) lastPayTick = tick
        return out
    }

    /** Feeds settlement outcomes back: late wages build up a company's unpaid streak. */
    fun applyResults(results: List<SettlementResult>) {
        for (r in results) {
            if (!r.intent.key.startsWith("payroll:")) continue
            val company = CompanyId(r.intent.key.split(":")[1].toLong())
            when (r.status) {
                SettlementStatus.PAID -> unpaid.remove(company)
                SettlementStatus.DEFERRED, SettlementStatus.UNPAID -> unpaid[company] = (unpaid[company] ?: 0) + params.payIntervalTicks
                else -> Unit
            }
        }
    }

    // ----- lifecycle ------------------------------------------------------------------------

    /** Removes a company's workforce (bankruptcy or sale). Workers return to the jobseeker pool. */
    fun dissolve(company: CompanyId) {
        for (c in cohortsOf(company)) {
            markets.release(MarketKey(c.key.region, c.key.role), c.headcount)
            cohorts.remove(c.key)
            byId.remove(c.id)
        }
        unpaid.remove(company); benefits.remove(company); reputation.remove(company); contracts.remove(company); severanceOwed.remove(company)
    }

    /** Replaces labor with automation: lowers the target, paying severance for those let go at once. */
    fun automate(key: CohortKey, newTarget: Long): Outcome<Long> {
        val c = cohorts[key] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such cohort")
        if (newTarget < 0 || newTarget > c.target) return Outcome.reject(Rules.INVALID_QUANTITY, "automation can only reduce staffing")
        val removed = c.target - newTarget
        c.target = newTarget
        return Outcome.ok(removed)
    }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("workforce")
        for (c in cohorts.values) c.hashInto(hasher)
        for ((k, v) in unpaid) hasher.putLong(k.value).putInt(v)
        for ((k, v) in benefits) hasher.putLong(k.value).putLong(v)
        for ((k, v) in reputation) hasher.putLong(k.value).putLong(v)
        for ((k, v) in severanceOwed) hasher.putLong(k.value).putMoney(v)
        for ((k, v) in markets.markets()) hasher.putLong(k.region.value).putString(k.role.key).putLong(v.available).putLong(v.wageIndexPpm).putLong(v.employed)
        for ((k, v) in layoffShock) hasher.putLong(k.company.value).putLong(v)
    }

    internal fun lastPay() = lastPayTick
    internal fun restoreState(
        list: List<Cohort>, unpaidMap: Map<CompanyId, Int>, benefitsMap: Map<CompanyId, Long>, reputationMap: Map<CompanyId, Long>,
        contractMap: Map<CompanyId, List<LaborContract>>, severance: Map<CompanyId, Money>, shock: Map<CohortKey, Long>, lastPay: Tick,
    ) {
        cohorts.clear(); byId.clear()
        for (c in list) { cohorts[c.key] = c; byId[c.id] = c }
        unpaid.clear(); unpaid.putAll(unpaidMap)
        benefits.clear(); benefits.putAll(benefitsMap)
        reputation.clear(); reputation.putAll(reputationMap)
        contracts.clear(); for ((k, v) in contractMap) contracts[k] = v.toMutableList()
        severanceOwed.clear(); severanceOwed.putAll(severance)
        layoffShock.clear(); layoffShock.putAll(shock)
        lastPayTick = lastPay
    }
    internal fun exportUnpaid(): Map<CompanyId, Int> = unpaid
    internal fun exportBenefits(): Map<CompanyId, Long> = benefits
    internal fun exportReputation(): Map<CompanyId, Long> = reputation
    internal fun exportContracts(): Map<CompanyId, List<LaborContract>> = contracts
    internal fun exportSeverance(): Map<CompanyId, Money> = severanceOwed
    internal fun exportShock(): Map<CohortKey, Long> = layoffShock
}
