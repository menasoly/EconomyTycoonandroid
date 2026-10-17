package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** A unit's offer of firm capacity: how much it can count on, and the daily price it asks for each megawatt. */
data class CapacityBid(val unit: UnitId, val firmKw: Long, val pricePerMwDay: Money)

data class CapacityAward(val unit: UnitId, val kw: Long)

/** The result of one auction, and the commitments it created. */
data class CapacityRound(
    val heldAt: Long,
    val requirementKw: Long,
    val awards: List<CapacityAward>,
    /** Price per megawatt-day paid to every award. */
    val clearingPrice: Money,
    val shortfallKw: Long,
) {
    val clearedKw: Long get() = awards.sumOf { it.kw }
    val validUntil: Long get() = heldAt + Calendar.DAYS_PER_YEAR

    fun awardOf(unit: UnitId): Long = awards.firstOrNull { it.unit == unit }?.kw ?: 0L

    /** What the round pays out each day. */
    fun dailyPayment(): Money = awards.fold(Money.ZERO) { acc, a -> acc + CapacityMarket.dailyPayment(a.kw, clearingPrice) }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(heldAt).putLong(requirementKw).putMoney(clearingPrice).putLong(shortfallKw).putInt(awards.size)
        for (a in awards) h.putLong(a.unit.value).putLong(a.kw)
    }
}

/**
 * The capacity market. Once a year the operator buys enough firm capacity to cover the expected peak plus the target
 * reserve. Units bid the "missing money" they need over a day, which is their fixed costs less what they expect to earn
 * from energy sales above running cost. The cheapest bids are taken until the requirement is met and all awards are
 * paid the price of the last bid taken. When bids run out, the shortfall is recorded and the price is the cap.
 */
object CapacityMarket {
    /** The highest daily price per megawatt the auction will pay. */
    val DEFAULT_CAP: Money = Money.units(900)

    fun requirementKw(peakKw: Long, targetReservePpm: Long): Long =
        peakKw + mulDiv(peakKw, targetReservePpm, Rate.ONE_PPM, RoundingMode.UP)

    /** Firm capacity a unit of this kind can offer, counting only what the technology can be relied on for. */
    fun firmKw(tech: Technology, capacityKw: Long): Long = mulDiv(capacityKw, tech.profile.capacityCreditPpm, Rate.ONE_PPM, RoundingMode.DOWN)

    /** Fixed cost of a megawatt of this technology for one day: operating cost plus its capital spread over its life. */
    fun fixedCostPerMwDay(tech: Technology): Money {
        val perKwDay = tech.profile.fixedOmPerKwYear.raw / Calendar.DAYS_PER_YEAR + tech.profile.capexPerKw.raw / tech.profile.lifetimeTicks
        return Money(perKwDay * GridUnits.KW_PER_MW)
    }

    /** What a megawatt of firm capacity expects to earn in a day above its running cost, given the recent average price. */
    fun energyRentPerMwDay(tech: Technology, marginalCost: Money, averagePrice: Money): Money {
        val margin = (averagePrice - marginalCost).coerceAtLeast(Money.ZERO)
        val mwhPerMwDay = GridUnits.HOURS_PER_DAY.toLong() * tech.profile.capacityCreditPpm
        return Money(mulDiv(margin.raw, mwhPerMwDay, Rate.ONE_PPM, RoundingMode.DOWN))
    }

    /** The daily price a unit asks for: its fixed cost less its expected energy rent, never below zero. */
    fun bidPerMwDay(tech: Technology, marginalCost: Money, averagePrice: Money): Money =
        (fixedCostPerMwDay(tech) - energyRentPerMwDay(tech, marginalCost, averagePrice)).coerceAtLeast(Money.ZERO)

    fun dailyPayment(kw: Long, pricePerMwDay: Money): Money = Money(mulDiv(pricePerMwDay.raw, kw, GridUnits.KW_PER_MW, RoundingMode.HALF_EVEN))

    fun auction(heldAt: Long, requirementKw: Long, bids: List<CapacityBid>, cap: Money = DEFAULT_CAP): CapacityRound {
        if (requirementKw <= 0L) return CapacityRound(heldAt, 0L, emptyList(), Money.ZERO, 0L)
        val ordered = bids.filter { it.firmKw > 0L && it.pricePerMwDay <= cap }.sortedWith(compareBy({ it.pricePerMwDay.raw }, { it.unit.value }))
        val awards = ArrayList<CapacityAward>()
        var left = requirementKw
        var price = Money.ZERO
        for (b in ordered) {
            if (left <= 0L) break
            val take = minOf(left, b.firmKw)
            awards += CapacityAward(b.unit, take)
            left -= take
            price = b.pricePerMwDay
        }
        if (left > 0L) price = cap
        return CapacityRound(heldAt, requirementKw, awards.sortedBy { it.unit }, price, left)
    }
}
