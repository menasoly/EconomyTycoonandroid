package app.many.economytycoon.marketing

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

enum class CampaignStatus(val label: String) {
    /** Booked, waiting for its start day. */
    SCHEDULED("Booked"),
    ACTIVE("Running"),
    PAUSED("Paused"),
    /** Ran its full length. */
    COMPLETED("Finished"),
    CANCELLED("Cancelled"),
    /** Stopped because the bills went unpaid. */
    SUSPENDED("Suspended");

    val isLive: Boolean get() = this == SCHEDULED || this == ACTIVE || this == PAUSED
    val isEnded: Boolean get() = !isLive
}

/** The advertisement itself. Quality is what the agency delivered; freshness is how much people have not yet tired of it. */
class Creative(qualityPpm: Long, freshnessPpm: Long = Ppm.ONE) {
    var qualityPpm: Long = Ppm.clamp(qualityPpm)
        internal set
    var freshnessPpm: Long = Ppm.clamp(freshnessPpm)
        internal set
    /** Version number, raised each time the campaign gets a new creative. */
    var version: Int = 1
        internal set
    var lastRefreshed: Tick? = null
        internal set

    internal fun restore(quality: Long, freshness: Long, version: Int, lastRefreshed: Tick?) {
        qualityPpm = Ppm.clamp(quality); freshnessPpm = Ppm.clamp(freshness); this.version = version; this.lastRefreshed = lastRefreshed
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(qualityPpm).putLong(freshnessPpm).putInt(version).putLong(lastRefreshed?.value ?: -1L)
    }
}

/**
 * One booking of one channel for one product in one region (or one city of it). Money is per day. The service that
 * owns campaigns is the only code that changes them.
 */
class Campaign(
    val id: CampaignId,
    val company: CompanyId,
    val good: GoodId,
    val region: RegionId,
    val city: CityId?,
    val channel: Channel,
    val segment: TargetSegment,
    val createdAt: Tick,
    val startTick: Tick,
    dailyBudget: Money,
    daysBooked: Int,
    val creative: Creative,
) {
    var dailyBudget: Money = dailyBudget
        internal set
    var status: CampaignStatus = if (startTick > createdAt) CampaignStatus.SCHEDULED else CampaignStatus.ACTIVE
        internal set
    /** Days of running still booked. Paused days do not count. */
    var daysRemaining: Int = daysBooked
        internal set
    var daysBooked: Int = daysBooked
        internal set
    var daysRun: Int = 0
        internal set
    var pausedDays: Int = 0
        internal set
    /** Money paid up front and not yet used. */
    var prepaid: Money = Money.ZERO
        internal set
    var spentTotal: Money = Money.ZERO
        internal set
    var refundedTotal: Money = Money.ZERO
        internal set
    var feesTotal: Money = Money.ZERO
        internal set
    /** Consecutive unpaid days; resets with the next payment. */
    var missedPayments: Int = 0
        internal set
    /** People reached so far, counting each day's reach afresh. */
    var impressions: Long = 0L
        internal set
    var lastReachPpm: Long = 0L
        internal set
    var endedAt: Tick? = null
        internal set
    var lastPaidTick: Tick? = null
        internal set

    val key: BrandKey get() = BrandKey(company, good, region)
    val isLive: Boolean get() = status.isLive
    val isRunning: Boolean get() = status == CampaignStatus.ACTIVE

    /** What the rest of the booking will cost at today's budget. */
    val remainingCommitment: Money get() = dailyBudget * daysRemaining.toLong()

    /** Money actually laid out so far, net of anything handed back. */
    val netSpend: Money get() = spentTotal - refundedTotal

    /** Two campaigns clash when they would book the same channel, in the same place, for the same product. */
    fun sameSlot(other: Campaign): Boolean =
        company == other.company && good == other.good && region == other.region && city == other.city && channel == other.channel

    internal fun restore(
        status: CampaignStatus, daysRemaining: Int, daysBooked: Int, daysRun: Int, pausedDays: Int, prepaid: Money, spent: Money,
        refunded: Money, fees: Money, missed: Int, impressions: Long, lastReach: Long, endedAt: Tick?, lastPaid: Tick?,
    ) {
        this.status = status; this.daysRemaining = daysRemaining; this.daysBooked = daysBooked; this.daysRun = daysRun
        this.pausedDays = pausedDays; this.prepaid = prepaid; spentTotal = spent; refundedTotal = refunded; feesTotal = fees
        missedPayments = missed; this.impressions = impressions; lastReachPpm = lastReach; this.endedAt = endedAt; lastPaidTick = lastPaid
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putString(good.key).putLong(region.value).putLong(city?.value ?: -1L)
            .putInt(channel.ordinal).putInt(segment.ordinal).putLong(createdAt.value).putLong(startTick.value)
            .putMoney(dailyBudget).putInt(status.ordinal).putInt(daysRemaining).putInt(daysBooked).putInt(daysRun).putInt(pausedDays)
            .putMoney(prepaid).putMoney(spentTotal).putMoney(refundedTotal).putMoney(feesTotal).putInt(missedPayments)
            .putLong(impressions).putLong(lastReachPpm).putLong(endedAt?.value ?: -1L).putLong(lastPaidTick?.value ?: -1L)
        creative.hashInto(h)
    }
}
