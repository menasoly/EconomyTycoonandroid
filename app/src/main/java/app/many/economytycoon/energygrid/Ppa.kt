package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** How a power purchase agreement delivers. */
enum class PpaProfile(val label: String) {
    /** A flat number of kilowatts every hour; the seller is penalised for any hour it falls short. */
    BASELOAD("Baseload"),
    /** A share of one named unit's output, whatever it is. */
    AS_PRODUCED("As produced"),
}

enum class PpaStatus(val label: String, val live: Boolean) {
    OFFERED("Offered", false),
    ACTIVE("Active", true),
    COMPLETED("Completed", false),
    TERMINATED("Terminated early", false),
    DECLINED("Declined or lapsed", false),
}

/**
 * A long-term contract for difference between a seller with generation and a buyer with load. Energy still moves
 * through the pool; the agreement fixes the price. Each month the buyer pays the seller the difference between the
 * strike price and what the pool paid for the delivered energy, a negative amount meaning the seller pays the buyer.
 * Hours in which a baseload seller could not deliver are penalised.
 */
class Ppa(
    val id: PpaId,
    val grid: GridId,
    val seller: CompanyId,
    val buyer: CompanyId,
    val profile: PpaProfile,
    /** Contracted kilowatts for baseload; unused for as-produced agreements. */
    val volumeKw: Long,
    /** Share of the unit's output for as-produced agreements, in ppm; unused for baseload. */
    val sharePpm: Long,
    /** The unit an as-produced agreement follows. */
    val unit: UnitId?,
    val strikePerMwh: Money,
    val startsAt: Long,
    val endsAt: Long,
    /** Penalty on undelivered baseload energy as a share of the strike price. */
    val penaltyPpm: Long,
    val offeredAt: Long,
) {
    init {
        require(seller != buyer) { "a company cannot contract with itself" }
        require(strikePerMwh.raw > 0L) { "the strike price must be positive" }
        require(endsAt > startsAt) { "an agreement needs a duration" }
        require(penaltyPpm in 0L..Rate.ONE_PPM * 2) { "penalty out of range" }
        when (profile) {
            PpaProfile.BASELOAD -> require(volumeKw > 0L) { "a baseload agreement needs a volume" }
            PpaProfile.AS_PRODUCED -> require(unit != null && sharePpm in 1L..Rate.ONE_PPM) { "an as-produced agreement names a unit and a share" }
        }
    }

    var status: PpaStatus = PpaStatus.OFFERED
        internal set
    var closedAt: Long = -1L
        internal set

    // Accruals since the last monthly settlement.
    var deliveredKwh: Long = 0L
        internal set
    var shortfallKwh: Long = 0L
        internal set
    /** What the pool paid for the delivered energy, and what replacing the shortfall would have cost at pool prices. */
    var marketValue: Money = Money.ZERO
        internal set
    var shortfallMarketValue: Money = Money.ZERO
        internal set

    // Totals since the start.
    var totalDeliveredKwh: Long = 0L
        internal set
    var totalShortfallKwh: Long = 0L
        internal set
    var netPaidBySeller: Money = Money.ZERO
        internal set
    var daysDelivering: Int = 0
        internal set

    val isActive: Boolean get() = status == PpaStatus.ACTIVE
    fun runsAt(tick: Long): Boolean = isActive && tick >= startsAt && tick < endsAt
    fun remainingDays(tick: Long): Long = (endsAt - maxOf(tick, startsAt)).coerceAtLeast(0L)

    /** Energy expected per day, which prices an early exit. */
    fun expectedDailyKwh(): Long =
        if (profile == PpaProfile.BASELOAD) volumeKw * GridUnits.HOURS_PER_DAY
        else if (daysDelivering > 0) totalDeliveredKwh / daysDelivering else 0L

    /** Fee for leaving early: a share of the strike value of the energy still to come. */
    fun exitFee(tick: Long, exitPpm: Long): Money =
        Money(mulDiv(GridUnits.cost(strikePerMwh, expectedDailyKwh()).raw, remainingDays(tick) * exitPpm, Rate.ONE_PPM, RoundingMode.HALF_EVEN))

    /** Adds one hour of delivery. [spot] is the pool price of that hour. */
    internal fun accrueHour(deliveredKw: Long, shortfallKw: Long, spot: Money) {
        deliveredKwh += deliveredKw; totalDeliveredKwh += deliveredKw
        shortfallKwh += shortfallKw; totalShortfallKwh += shortfallKw
        marketValue = marketValue + GridUnits.cost(spot, deliveredKw)
        shortfallMarketValue = shortfallMarketValue + GridUnits.cost(spot, shortfallKw)
    }

    internal fun clearAccrual() {
        deliveredKwh = 0L; shortfallKwh = 0L; marketValue = Money.ZERO; shortfallMarketValue = Money.ZERO
    }

    internal fun restore(
        status: PpaStatus, closedAt: Long, delivered: Long, shortfall: Long, market: Money, shortfallMarket: Money,
        totalDelivered: Long, totalShortfall: Long, netPaid: Money, days: Int,
    ) {
        this.status = status; this.closedAt = closedAt; deliveredKwh = delivered; shortfallKwh = shortfall; marketValue = market
        shortfallMarketValue = shortfallMarket; totalDeliveredKwh = totalDelivered; totalShortfallKwh = totalShortfall
        netPaidBySeller = netPaid; daysDelivering = days
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(grid.value).putLong(seller.value).putLong(buyer.value).putInt(profile.ordinal).putLong(volumeKw).putLong(sharePpm)
        h.putLong(unit?.value ?: -1L).putMoney(strikePerMwh).putLong(startsAt).putLong(endsAt).putLong(penaltyPpm).putLong(offeredAt)
        h.putInt(status.ordinal).putLong(closedAt).putLong(deliveredKwh).putLong(shortfallKwh).putMoney(marketValue).putMoney(shortfallMarketValue)
        h.putLong(totalDeliveredKwh).putLong(totalShortfallKwh).putMoney(netPaidBySeller).putInt(daysDelivering)
    }
}

/** What one monthly settlement of a PPA amounts to. Positive [netToSeller] means the buyer pays the seller. */
data class PpaSettlement(
    val ppa: PpaId,
    val deliveredKwh: Long,
    val shortfallKwh: Long,
    val difference: Money,
    val penalty: Money,
    val replacementPremium: Money,
) {
    /** The net payment from buyer to seller; negative when the seller owes. */
    val netToSeller: Money get() = difference - penalty - replacementPremium
}

object PpaSettler {
    /**
     * The settlement of what [p] accrued. The difference is strike value less pool value of the delivered energy. For
     * shortfall energy the seller pays a penalty on the strike price, and also the amount by which replacing it at
     * pool prices cost more than the strike.
     */
    fun settle(p: Ppa): PpaSettlement {
        val strikeValue = GridUnits.cost(p.strikePerMwh, p.deliveredKwh)
        val difference = strikeValue - p.marketValue
        val shortfallStrike = GridUnits.cost(p.strikePerMwh, p.shortfallKwh)
        val penalty = Money(mulDiv(shortfallStrike.raw, p.penaltyPpm, Rate.ONE_PPM, RoundingMode.HALF_EVEN))
        val premium = (p.shortfallMarketValue - shortfallStrike).coerceAtLeast(Money.ZERO)
        return PpaSettlement(p.id, p.deliveredKwh, p.shortfallKwh, difference, penalty, premium)
    }
}
