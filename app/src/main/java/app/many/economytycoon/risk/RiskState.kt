package app.many.economytycoon.risk

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.EventId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.PolicyId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/**
 * What can go wrong. [annualPpm] is the yearly chance of the event for one region (or, for fire, one facility).
 * The two factors say how much of a building's condition and of its stock a full-strength event destroys.
 */
enum class Peril(val label: String, val annualPpm: Long, val buildingFactorPpm: Long, val stockFactorPpm: Long, val regional: Boolean) {
    FIRE("Fire", 20_000L, 700_000L, 800_000L, false),
    STORM("Storm", 120_000L, 450_000L, 150_000L, true),
    FLOOD("Flood", 80_000L, 500_000L, 700_000L, true),
    QUAKE("Earthquake", 30_000L, 750_000L, 200_000L, true),
}

/** How much a policy covers. Each level includes everything the one before it does. */
enum class CoverLevel(val label: String, val perils: Set<Peril>) {
    BASIC("Basic", setOf(Peril.FIRE, Peril.STORM)),
    STANDARD("Standard", setOf(Peril.FIRE, Peril.STORM, Peril.FLOOD)),
    FULL("Full", setOf(Peril.FIRE, Peril.STORM, Peril.FLOOD, Peril.QUAKE)),
}

/** The excess the holder pays on every claim, as a share of the insured value. A bigger excess means a cheaper premium. */
enum class Deductible(val label: String, val sharePpm: Long, val premiumPpm: Long) {
    LOW("Low", 20_000L, 1_000_000L),
    MEDIUM("Medium", 50_000L, 850_000L),
    HIGH("High", 100_000L, 700_000L),
}

enum class PolicyStatus { ACTIVE, LAPSED, CANCELLED }

data class RiskParams(
    /** Days between premium payments. */
    val billingInterval: Int = 30,
    /** Extra cost over the expected losses, ppm of 1. */
    val loadingPpm: Long = 1_500_000L,
    /** Average share of full strength an event reaches; used to price cover, not to roll events. */
    val meanSeverityPpm: Long = 300_000L,
    val minSeverityPpm: Long = 60_000L,
    val maxSeverityPpm: Long = 800_000L,
    /** The building makes up this much of what is insured; the rest is stock. */
    val buildingShareOfValuePpm: Long = 800_000L,
    /** Lowest and highest share of a loss the insurer will reimburse. */
    val minCoveragePpm: Long = 500_000L,
    val maxCoveragePpm: Long = 1_000_000L,
    /** Added to the premium for each claim and halved roughly every three billings. */
    val claimSurchargePpm: Long = 150_000L,
    val surchargeDecayPpm: Long = 800_000L,
    val maxPoliciesPerCompany: Int = 30,
    /** The insurer's opening reserve. */
    val insurerReserve: Money = Money.units(5_000_000),
    val conditionFloorPpm: Long = 50_000L,
    val disasterHistory: Int = 60,
    val claimHistory: Int = 200,
    /** Condition below which fire is more likely, and by how much. */
    val neglectedConditionPpm: Long = 500_000L,
    val neglectFireMultiplePpm: Long = 1_500_000L,
)

/** A cover on one facility: its building and the stock inside. */
class Policy(
    val id: PolicyId,
    val holder: CompanyId,
    val facility: FacilityId,
    val region: RegionId,
    val level: CoverLevel,
    val coveragePpm: Long,
    val deductible: Deductible,
    val boughtAt: Tick,
) {
    var status: PolicyStatus = PolicyStatus.ACTIVE
        internal set
    /** Value of building and stock when last priced; claims are capped by it. */
    var insuredValue: Money = Money.ZERO
        internal set
    var premium: Money = Money.ZERO
        internal set
    var paidThrough: Tick = boughtAt
        internal set
    var premiumsPaid: Money = Money.ZERO
        internal set
    var claimsPaid: Money = Money.ZERO
        internal set
    var claimCount: Int = 0
        internal set
    var lastClaim: Tick? = null
        internal set
    var surchargePpm: Long = 0L
        internal set
    var endedAt: Tick? = null
        internal set

    val isActive: Boolean get() = status == PolicyStatus.ACTIVE
    fun covers(peril: Peril) = isActive && peril in level.perils
    val deductibleAmount: Money get() = insuredValue.scale(deductible.sharePpm, 1_000_000L)

    internal fun restore(
        status: PolicyStatus, insured: Money, premium: Money, paidThrough: Tick, premiumsPaid: Money, claimsPaid: Money,
        claimCount: Int, lastClaim: Tick?, surcharge: Long, endedAt: Tick?,
    ) {
        this.status = status; insuredValue = insured; this.premium = premium; this.paidThrough = paidThrough
        this.premiumsPaid = premiumsPaid; this.claimsPaid = claimsPaid; this.claimCount = claimCount
        this.lastClaim = lastClaim; surchargePpm = surcharge; this.endedAt = endedAt
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(holder.value).putLong(facility.value).putLong(region.value).putInt(level.ordinal).putLong(coveragePpm)
        h.putInt(deductible.ordinal).putLong(boughtAt.value).putInt(status.ordinal).putLong(insuredValue.raw).putLong(premium.raw)
        h.putLong(paidThrough.value).putLong(premiumsPaid.raw).putLong(claimsPaid.raw).putInt(claimCount).putLong(lastClaim?.value ?: -1L)
        h.putLong(surchargePpm).putLong(endedAt?.value ?: -1L)
    }
}

/** One event that struck, with the damage it did across everyone. */
data class Disaster(
    val id: EventId,
    val peril: Peril,
    val region: RegionId,
    val tick: Tick,
    val severityPpm: Long,
    val facilitiesHit: Int,
    val totalLoss: Money,
    val insuredLoss: Money,
)

/** What one facility lost and what its insurer paid. */
data class Claim(
    val disaster: EventId,
    val peril: Peril,
    val policy: PolicyId?,
    val holder: CompanyId,
    val facility: FacilityId,
    val tick: Tick,
    val buildingLoss: Money,
    val stockLoss: Money,
    val payout: Money,
    val shortfall: Money,
) {
    val loss: Money get() = buildingLoss + stockLoss
    val insured: Boolean get() = policy != null
}

sealed class RiskEvent {
    abstract val tick: Tick
    data class Struck(val disaster: Disaster) : RiskEvent() { override val tick: Tick get() = disaster.tick }
    data class Paid(val claim: Claim) : RiskEvent() { override val tick: Tick get() = claim.tick }
    data class Uncovered(val claim: Claim) : RiskEvent() { override val tick: Tick get() = claim.tick }
    data class Lapsed(val policy: PolicyId, val holder: CompanyId, val facility: FacilityId, override val tick: Tick) : RiskEvent()
}
