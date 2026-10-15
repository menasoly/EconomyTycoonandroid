package app.many.economytycoon.risk

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.PolicyId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Region
import app.many.economytycoon.world.Terrain

/**
 * Keeps the register of insurance policies and the history of disasters and claims, and prices cover.
 * Moving money and damaging buildings is left to the risk runner; this class decides what things cost
 * and what is allowed.
 */
class RiskService(private val ids: IdAllocators, val params: RiskParams = RiskParams()) {
    private val policies = java.util.TreeMap<PolicyId, Policy>()
    private val disasters = ArrayDeque<Disaster>()
    private val claims = ArrayDeque<Claim>()
    var reserveSeeded: Boolean = false
        internal set

    /** The insurer holds premiums and pays claims; it is an account of its own, apart from every company. */
    val insurer: LedgerOwner = LedgerOwner.bank(INSURER_ID)

    fun policy(id: PolicyId): Policy? = policies[id]
    fun allPolicies(): List<Policy> = policies.values.toList()
    fun policiesOf(company: CompanyId): List<Policy> = policies.values.filter { it.holder == company }
    fun activePoliciesOf(company: CompanyId): List<Policy> = policiesOf(company).filter { it.isActive }
    fun activeFor(facility: FacilityId): Policy? = policies.values.firstOrNull { it.facility == facility && it.isActive }
    fun activePolicies(): List<Policy> = policies.values.filter { it.isActive }
    fun disasters(): List<Disaster> = disasters.toList()
    fun claims(): List<Claim> = claims.toList()
    fun claimsOf(company: CompanyId): List<Claim> = claims.filter { it.holder == company }

    // ---- pricing -------------------------------------------------------------------------------

    /** How likely [peril] is on one day in [region], parts per million. */
    fun chancePerTickPpm(region: Region, peril: Peril): Long = mulDiv(peril.annualPpm, regionFactorPpm(region, peril), 1_000_000L) / TICKS_PER_YEAR

    fun regionFactorPpm(region: Region, peril: Peril): Long {
        var f = 1_000_000L
        fun by(ppm: Long) { f = mulDiv(f, ppm, 1_000_000L) }
        when (peril) {
            Peril.FIRE -> if (region.climate == ClimateZone.ARID) by(1_300_000L)
            Peril.STORM -> {
                if (region.coastal) by(1_800_000L)
                when (region.climate) {
                    ClimateZone.TROPICAL -> by(1_600_000L)
                    ClimateZone.COLD -> by(800_000L)
                    ClimateZone.ARID -> by(500_000L)
                    else -> {}
                }
            }
            Peril.FLOOD -> {
                if (region.coastal) by(2_000_000L)
                when (region.climate) {
                    ClimateZone.TROPICAL -> by(1_400_000L)
                    ClimateZone.ARID -> by(400_000L)
                    else -> {}
                }
                when (region.terrain) {
                    Terrain.PLAINS, Terrain.WETLAND -> by(1_300_000L)
                    Terrain.MOUNTAINS -> by(600_000L)
                    else -> {}
                }
            }
            Peril.QUAKE -> when (region.terrain) {
                Terrain.MOUNTAINS -> by(3_000_000L)
                Terrain.HILLS -> by(1_500_000L)
                Terrain.PLAINS, Terrain.COAST -> by(600_000L)
                else -> {}
            }
        }
        return f
    }

    /** Share of an insured asset a full-strength [peril] destroys, given how value splits between building and stock. */
    fun lossShareOfValuePpm(peril: Peril): Long {
        val b = params.buildingShareOfValuePpm
        return mulDiv(peril.buildingFactorPpm, b, 1_000_000L) + mulDiv(peril.stockFactorPpm, 1_000_000L - b, 1_000_000L)
    }

    /** Fire is likelier in a run-down or polluting building. */
    fun fireMultiplePpm(conditionPpm: Long, pollutionPpm: Long): Long {
        var m = 1_000_000L + pollutionPpm.coerceIn(0L, 1_000_000L) / 2
        if (conditionPpm < params.neglectedConditionPpm) m = mulDiv(m, params.neglectFireMultiplePpm, 1_000_000L)
        return m
    }

    /** What cover costs for a year, as parts per million of the insured value, before any claims surcharge. */
    fun annualRatePpm(region: Region, level: CoverLevel, coveragePpm: Long, deductible: Deductible, conditionPpm: Long = 1_000_000L, pollutionPpm: Long = 0L): Long {
        var expected = 0L
        for (peril in level.perils) {
            var yearly = mulDiv(peril.annualPpm, regionFactorPpm(region, peril), 1_000_000L)
            if (peril == Peril.FIRE) yearly = mulDiv(yearly, fireMultiplePpm(conditionPpm, pollutionPpm), 1_000_000L)
            expected += mulDiv(mulDiv(yearly, params.meanSeverityPpm, 1_000_000L), lossShareOfValuePpm(peril), 1_000_000L)
        }
        val loaded = mulDiv(expected, params.loadingPpm, 1_000_000L)
        return mulDiv(mulDiv(loaded, coveragePpm, 1_000_000L), deductible.premiumPpm, 1_000_000L)
    }

    /** The premium for one billing period. */
    fun premiumFor(insured: Money, ratePpm: Long, surchargePpm: Long = 0L): Money {
        val yearly = insured.scale(ratePpm, 1_000_000L).scale(1_000_000L + surchargePpm, 1_000_000L)
        return yearly.scale(params.billingInterval.toLong(), TICKS_PER_YEAR)
    }

    // ---- register ------------------------------------------------------------------------------

    fun checkChoice(coveragePpm: Long): app.many.economytycoon.core.Rejection? =
        if (coveragePpm !in params.minCoveragePpm..params.maxCoveragePpm) {
            app.many.economytycoon.core.Rejection(Rules.INVALID_ARGUMENT, "choose cover between ${params.minCoveragePpm / 10_000}% and ${params.maxCoveragePpm / 10_000}%")
        } else null

    fun sell(
        holder: CompanyId, facility: FacilityId, region: app.many.economytycoon.core.RegionId, level: CoverLevel, coveragePpm: Long, deductible: Deductible,
        insured: Money, premium: Money, tick: Tick,
    ): Outcome<Policy> {
        checkChoice(coveragePpm)?.let { return Outcome.Rejected(it) }
        if (activeFor(facility) != null) return Outcome.reject(Rules.WRONG_STATE, "that facility is already insured")
        if (activePoliciesOf(holder).size >= params.maxPoliciesPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "a company can hold at most ${params.maxPoliciesPerCompany} policies")
        val p = Policy(ids.policy(), holder, facility, region, level, coveragePpm, deductible, tick)
        p.insuredValue = insured
        p.premium = premium
        p.paidThrough = Tick(tick.value + params.billingInterval)
        p.premiumsPaid = premium
        policies[p.id] = p
        return Outcome.ok(p)
    }

    fun cancel(holder: CompanyId, id: PolicyId, tick: Tick): Outcome<Policy> {
        val p = policies[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown policy")
        if (p.holder != holder) return Outcome.reject(Rules.NOT_OWNER, "that policy belongs to another company")
        if (!p.isActive) return Outcome.reject(Rules.WRONG_STATE, "the policy has already ended")
        p.status = PolicyStatus.CANCELLED
        p.endedAt = tick
        return Outcome.ok(p)
    }

    fun lapse(p: Policy, tick: Tick) {
        if (!p.isActive) return
        p.status = PolicyStatus.LAPSED
        p.endedAt = tick
    }

    /** Ends every policy a company holds, for when it ceases to exist. */
    fun endAllOf(company: CompanyId, tick: Tick) {
        for (p in activePoliciesOf(company)) { p.status = PolicyStatus.CANCELLED; p.endedAt = tick }
    }

    fun billed(p: Policy, insured: Money, premium: Money, tick: Tick) {
        p.insuredValue = insured
        p.premium = premium
        p.premiumsPaid = p.premiumsPaid + premium
        p.paidThrough = Tick(tick.value + params.billingInterval)
        p.surchargePpm = mulDiv(p.surchargePpm, params.surchargeDecayPpm, 1_000_000L)
    }

    fun paidOut(p: Policy, amount: Money, tick: Tick) {
        p.claimsPaid = p.claimsPaid + amount
        p.claimCount += 1
        p.lastClaim = tick
        p.surchargePpm += params.claimSurchargePpm
    }

    fun record(d: Disaster) {
        disasters.addLast(d)
        while (disasters.size > params.disasterHistory) disasters.removeFirst()
    }

    fun record(c: Claim) {
        claims.addLast(c)
        while (claims.size > params.claimHistory) claims.removeFirst()
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(if (reserveSeeded) 1 else 0).putInt(policies.size)
        for (p in policies.values) p.hashInto(h)
        h.putInt(disasters.size)
        for (d in disasters) h.putLong(d.id.value).putInt(d.peril.ordinal).putLong(d.region.value).putLong(d.tick.value).putLong(d.severityPpm).putInt(d.facilitiesHit).putLong(d.totalLoss.raw).putLong(d.insuredLoss.raw)
        h.putInt(claims.size)
        for (c in claims) {
            h.putLong(c.disaster.value).putInt(c.peril.ordinal).putLong(c.policy?.value ?: -1L).putLong(c.holder.value).putLong(c.facility.value).putLong(c.tick.value)
            h.putLong(c.buildingLoss.raw).putLong(c.stockLoss.raw).putLong(c.payout.raw).putLong(c.shortfall.raw)
        }
    }

    internal fun restore(p: Policy) { policies[p.id] = p }
    internal fun restoreDisaster(d: Disaster) { disasters.addLast(d) }
    internal fun restoreClaim(c: Claim) { claims.addLast(c) }
    internal fun clearAll() { policies.clear(); disasters.clear(); claims.clear(); reserveSeeded = false }

    companion object {
        const val INSURER_ID = 9_001L
        const val TICKS_PER_YEAR = 360L
    }
}
