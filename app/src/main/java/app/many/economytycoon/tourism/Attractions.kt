package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** Kinds of ticketed attraction. Boosts are what a full-quality, reference-sized one adds to the destination driver. */
enum class AttractionKind(
    val label: String,
    val outdoor: Boolean,
    val referenceCapacity: Int,
    val referenceTicket: Money,
    /** Share of the tourists present who visit on a given day, ppm. */
    val attendancePpm: Long,
    val cultureBoostPpm: Long,
    val natureBoostPpm: Long,
    val nightlifeBoostPpm: Long,
    val fixedCostPerDay: Money,
    val costPerVisit: Money,
    val buildCost: Money,
) {
    MUSEUM("Museum", false, 1_500, Money.units(14), 220_000L, 160_000L, 0L, 0L, Money.units(900), Money.units(1), Money.units(1_200_000)),
    THEME_PARK("Theme park", true, 9_000, Money.units(55), 150_000L, 40_000L, 0L, 60_000L, Money.units(14_000), Money.units(9), Money.units(9_000_000)),
    NATURE_PARK("Nature park", true, 4_000, Money.units(10), 180_000L, 0L, 170_000L, 0L, Money.units(700), Money.units(1), Money.units(800_000)),
    CONCERT_HALL("Concert hall", false, 2_500, Money.units(35), 90_000L, 50_000L, 0L, 150_000L, Money.units(2_800), Money.units(4), Money.units(3_000_000)),
    SKI_LIFT("Ski lifts", true, 6_000, Money.units(40), 130_000L, 0L, 120_000L, 20_000L, Money.units(5_000), Money.units(3), Money.units(5_500_000)),
    BEACH_CLUB("Beach club", true, 1_200, Money.units(25), 140_000L, 0L, 90_000L, 70_000L, Money.units(1_100), Money.units(5), Money.units(1_400_000)),
}

/** What an attraction did on one day. */
data class AttractionDay(
    val tick: Long,
    val demand: Long,
    val visits: Long,
    val turnedAway: Long,
    val waitMinutes: Int,
    val revenue: Money,
    val cost: Money,
    val satisfactionPpm: Long,
)

/** An attraction. Identity never changes; the owner sets prices and capacity through the service. */
class Attraction(
    val id: AttractionId,
    val destination: DestinationId,
    val owner: CompanyId?,
    val facility: FacilityId?,
    val kind: AttractionKind,
    val name: String,
) {
    var capacityPerDay: Int = kind.referenceCapacity
        internal set
    var ticketPrice: Money = kind.referenceTicket
        internal set
    var qualityPpm: Long = 800_000L
        internal set
    var open: Boolean = true
        internal set
    var opensAt: Long = 0L
        internal set
    var queueSatisfactionPpm: Long = 800_000L
        internal set
    var lifetimeVisits: Long = 0L
        internal set
    var lifetimeRevenue: Money = Money.ZERO
        internal set
    var invested: Money = Money.ZERO
        internal set

    var monthRevenue: Money = Money.ZERO
        internal set
    var monthCost: Money = Money.ZERO
        internal set
    var monthVisits: Long = 0L
        internal set

    val reviews = ReviewBook()
    internal val dayList = ArrayList<AttractionDay>()
    val days: List<AttractionDay> get() = dayList.toList()
    val lastDay: AttractionDay? get() = dayList.lastOrNull()

    /** Share of the destination's appeal this attraction adds, scaled by size and quality, per driver. */
    fun cultureBoostPpm(): Long = boost(kind.cultureBoostPpm)
    fun natureBoostPpm(): Long = boost(kind.natureBoostPpm)
    fun nightlifeBoostPpm(): Long = boost(kind.nightlifeBoostPpm)

    private fun boost(full: Long): Long {
        if (!open || full == 0L) return 0L
        val size = mulDiv(capacityPerDay.toLong(), 1_000_000L, kind.referenceCapacity.toLong()).coerceAtMost(1_500_000L)
        return mulDiv(mulDiv(full, size, 1_000_000L), qualityPpm, 1_000_000L)
    }

    internal fun pushDay(d: AttractionDay) {
        dayList += d
        if (dayList.size > HISTORY_DAYS) dayList.subList(0, dayList.size - HISTORY_DAYS).clear()
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(destination.value).putLong(owner?.value ?: -1L).putLong(facility?.value ?: -1L).putInt(kind.ordinal).putString(name)
        h.putInt(capacityPerDay).putMoney(ticketPrice).putLong(qualityPpm).putBoolean(open).putLong(opensAt).putLong(queueSatisfactionPpm)
        h.putLong(lifetimeVisits).putMoney(lifetimeRevenue).putMoney(invested).putMoney(monthRevenue).putMoney(monthCost).putLong(monthVisits)
        reviews.hashInto(h)
        h.putInt(dayList.size)
        for (d in dayList) h.putLong(d.tick).putLong(d.demand).putLong(d.visits).putLong(d.turnedAway).putInt(d.waitMinutes).putMoney(d.revenue).putMoney(d.cost).putLong(d.satisfactionPpm)
    }

    companion object { const val HISTORY_DAYS = 90 }
}

/** The inputs of one day's attendance. */
data class AttractionDayInputs(
    /** Tourists in the destination today who might visit. */
    val touristsPresent: Long,
    /** Weather penalty on pleasantness, ppm; outdoor attractions suffer from it. */
    val weatherPenaltyPpm: Long,
    /** Share of extra visits by residents, ppm of the tourist demand. */
    val localSharePpm: Long = 150_000L,
)

/** Pure rules for queues, attendance and ticket pricing. */
object AttractionModel {
    /** Utilisation up to which nobody queues, ppm. */
    const val QUEUE_FREE_PPM = 700_000L
    const val MAX_WAIT_MINUTES = 180
    /** Satisfaction lost per minute waited, ppm. */
    const val PER_MINUTE_PPM = 4_000L

    /** Demand falls as the ticket rises over the reference and rises when it is cheaper, 1,000,000 at the reference. */
    fun priceFactorPpm(ticket: Money, reference: Money): Long {
        if (reference.raw <= 0L) return 1_000_000L
        val ratio = mulDiv(ticket.raw, 1_000_000L, reference.raw)
        return (1_000_000L - mulDiv(ratio - 1_000_000L, 700_000L, 1_000_000L)).coerceIn(80_000L, 1_700_000L)
    }

    fun demand(a: Attraction, inputs: AttractionDayInputs): Long {
        var d = mulDiv(inputs.touristsPresent, a.kind.attendancePpm, 1_000_000L)
        d = mulDiv(d, priceFactorPpm(a.ticketPrice, a.kind.referenceTicket), 1_000_000L)
        d = mulDiv(d, 500_000L + a.qualityPpm / 2L, 1_000_000L)
        if (a.kind.outdoor) d = mulDiv(d, 1_000_000L - inputs.weatherPenaltyPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        return d + mulDiv(d, inputs.localSharePpm, 1_000_000L)
    }

    /** Minutes in the queue when [demand] people want in against [capacity] places. */
    fun waitMinutes(demand: Long, capacity: Int): Int {
        if (capacity <= 0) return MAX_WAIT_MINUTES
        val u = mulDiv(demand, 1_000_000L, capacity.toLong())
        if (u <= QUEUE_FREE_PPM) return 0
        return mulDiv(u - QUEUE_FREE_PPM, 400L, 1_000_000L).toInt().coerceAtMost(MAX_WAIT_MINUTES)
    }

    /** How the visit went: the queue, being turned away, and whether the ticket was worth it. */
    fun satisfactionPpm(waitMinutes: Int, turnedAway: Long, demand: Long, ticket: Money, reference: Money, qualityPpm: Long): Long {
        val queue = 1_000_000L - waitMinutes * PER_MINUTE_PPM
        val denied = if (demand <= 0L) 0L else mulDiv(turnedAway, 600_000L, demand)
        val value = (500_000L + (1_000_000L - mulDiv(ticket.raw, 1_000_000L, reference.raw.coerceAtLeast(1L))) / 2L).coerceIn(0L, 1_000_000L)
        val base = (queue - denied).coerceIn(0L, 1_000_000L)
        return ((base * 5L + value * 2L + qualityPpm * 3L) / 10L).coerceIn(0L, 1_000_000L)
    }

    fun runDay(a: Attraction, inputs: AttractionDayInputs, tick: Long): AttractionDay {
        if (!a.open || tick < a.opensAt) return AttractionDay(tick, 0L, 0L, 0L, 0, Money.ZERO, Money.ZERO, a.queueSatisfactionPpm)
        val demand = demand(a, inputs)
        val visits = minOf(demand, a.capacityPerDay.toLong())
        val turned = demand - visits
        val wait = waitMinutes(demand, a.capacityPerDay)
        val revenue = a.ticketPrice * visits
        val fixed = Money(mulDiv(a.kind.fixedCostPerDay.raw, a.capacityPerDay.toLong(), a.kind.referenceCapacity.toLong()))
        val cost = fixed + a.kind.costPerVisit * visits
        val sat = satisfactionPpm(wait, turned, demand, a.ticketPrice, a.kind.referenceTicket, a.qualityPpm)
        return AttractionDay(tick, demand, visits, turned, wait, revenue, cost, sat)
    }

    /** Combined pull of a destination's attractions on its culture, nature and nightlife drivers. */
    fun boosts(list: List<Attraction>): Triple<List<Long>, List<Long>, List<Long>> =
        Triple(list.map { it.cultureBoostPpm() }, list.map { it.natureBoostPpm() }, list.map { it.nightlifeBoostPpm() })
}
