package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick

data class BrandKey(val company: CompanyId, val region: RegionId) : Comparable<BrandKey> {
    override fun compareTo(other: BrandKey): Int {
        val c = company.compareTo(other.company)
        return if (c != 0) c else region.compareTo(other.region)
    }
}

/** How a company is seen by people looking for work in one region. */
class EmployerBrand {
    /** How many job seekers have heard of the company at all, ppm. */
    var awarenessPpm: Long = 0L
        internal set
    /** How good a place to work they think it is, ppm; 500,000 is neutral. */
    var appealPpm: Long = 500_000L
        internal set

    internal fun restore(awareness: Long, appeal: Long) { awarenessPpm = awareness; appealPpm = appeal }
    fun hashInto(h: CanonicalHasher) { h.putLong(awarenessPpm).putLong(appealPpm) }
}

enum class CampaignStatus { ACTIVE, DONE, CANCELLED }

/** A push to recruit graduates and school leavers for one role in one region. */
class RecruitCampaign(
    val id: RecruitCampaignId,
    val company: CompanyId,
    val region: RegionId,
    val role: RoleId,
    val budget: Money,
    val startTick: Tick,
    val endTick: Tick,
) {
    var status: CampaignStatus = CampaignStatus.ACTIVE
        internal set
    var hired: Long = 0L
        internal set
    /** Fraction of a hire carried to the next month, ppm. */
    var carryPpm: Long = 0L
        internal set

    val isActive: Boolean get() = status == CampaignStatus.ACTIVE
    val durationDays: Long get() = endTick.value - startTick.value

    internal fun restore(status: CampaignStatus, hired: Long, carry: Long) { this.status = status; this.hired = hired; carryPpm = carry }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putLong(region.value).putString(role.key).putMoney(budget)
        h.putLong(startTick.value).putLong(endTick.value).putInt(status.ordinal).putLong(hired).putLong(carryPpm)
    }
}

data class BrandParams(
    val neutralPpm: Long = 500_000L,
    /** Monthly pace at which the appeal moves to what the company's pay and morale deserve. */
    val appealPaceMonthlyPpm: Long = 80_000L,
    val wageWeightPpm: Long = 900_000L,
    val moraleWeightPpm: Long = 500_000L,
    val trainingWeightPpm: Long = 150_000L,
    val minAppealPpm: Long = 100_000L,
    val maxAppealPpm: Long = 950_000L,
    /** Share of the awareness that is lost every month without advertising. */
    val awarenessDecayPpm: Long = 40_000L,
    /** Headcount at which a company is known to everyone locally without advertising. */
    val fameHeadcount: Long = 3_000L,
    val fameFloorPpm: Long = 350_000L,
    /** Monthly campaign spend, in units per 100 people of the region's working-age pool, that fills the awareness gap by half. */
    val halfSaturationSpend: Money = Money.units(20_000),
    val awarenessGainCapPpm: Long = 300_000L,
    /** Extra share of hires who are the best of the pool while a campaign runs at full awareness. */
    val bestShareBoostPpm: Long = 300_000L,
    /** Share of the pool a campaign at full brand reaches in a month. */
    val monthlyConversionPpm: Long = 12_000L,
    val minCampaignDays: Int = 30,
    val maxCampaignDays: Int = 180,
    val maxCampaignsPerCompany: Int = 4,
    val minBudget: Money = Money.units(1_000),
    val keepDays: Int = 360,
)
