package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Point

/**
 * The lasting attractions of a place, each 0..1,000,000. They change slowly, through public investment, so they are
 * data and not rules. Climate is not here: it comes from the region's zone and the season.
 */
data class DestinationDrivers(
    val infrastructurePpm: Long = 500_000L,
    val safetyPpm: Long = 700_000L,
    val culturePpm: Long = 400_000L,
    val naturePpm: Long = 400_000L,
    val nightlifePpm: Long = 300_000L,
) {
    init {
        require(infrastructurePpm in 0..1_000_000L && safetyPpm in 0..1_000_000L && culturePpm in 0..1_000_000L &&
            naturePpm in 0..1_000_000L && nightlifePpm in 0..1_000_000L) { "drivers are between 0 and 1,000,000" }
    }
}

/** What one destination did on one day. Money is the room revenue of every property in the destination. */
data class DestinationDay(
    val tick: Long,
    /** Visitors that arrived, all segments. */
    val arrivals: Long,
    /** Rooms the visitors asked for on this stay date, including those turned away. */
    val roomNightDemand: Long,
    val roomsSold: Long,
    val roomsAvailable: Long,
    val deniedRoomNights: Long,
    val walkedGuests: Long,
    val roomRevenue: Money,
    /** People sleeping in the destination's properties. */
    val guestsPresent: Long,
    val attractionVisits: Long,
    /** Mean appeal over the four segments, ppm. */
    val appealPpm: Long,
) {
    val occupancyPpm: Long get() = if (roomsAvailable <= 0L) 0L else roomsSold * 1_000_000L / roomsAvailable
    val adr: Money get() = if (roomsSold <= 0L) Money.ZERO else Money(roomRevenue.raw / roomsSold)
}

/** A month of [DestinationDay]s rolled up, kept for seasonality charts. */
data class DestinationMonth(
    val month: Long,
    val arrivals: Long,
    val roomsSold: Long,
    val roomsAvailable: Long,
    val deniedRoomNights: Long,
    val roomRevenue: Money,
) {
    val occupancyPpm: Long get() = if (roomsAvailable <= 0L) 0L else roomsSold * 1_000_000L / roomsAvailable
    val adr: Money get() = if (roomsSold <= 0L) Money.ZERO else Money(roomRevenue.raw / roomsSold)
    val revPar: Money get() = if (roomsAvailable <= 0L) Money.ZERO else Money(roomRevenue.raw / roomsAvailable)
}

/**
 * A place visitors go to. The identity fields never change; the rest moves with the simulation and is written only by
 * [TourismService]. Prices are expressed as a level where 1,000,000 is the reference cost of a visit.
 */
class Destination(
    val id: DestinationId,
    val name: String,
    val city: CityId,
    val region: RegionId,
    val country: CountryId,
    val climate: ClimateZone,
    val location: Point,
    val coastal: Boolean,
) {
    var drivers: DestinationDrivers = DestinationDrivers()
        internal set

    /** Cost of living for a visitor, independent of what hotels charge. */
    var basePriceLevelPpm: Long = 1_000_000L
        internal set

    /** The level visitors actually meet: the base level pulled toward what rooms cost compared with the reference. */
    var priceLevelPpm: Long = 1_000_000L
        internal set

    /** Word of mouth, 0..1,000,000, following the reviews of properties and attractions. */
    var reputationPpm: Long = 600_000L
        internal set

    /** Accumulated marketing effect per segment, 0..1,000,000. It decays every day. */
    val marketingPpm: LongArray = LongArray(VisitorSegment.COUNT)

    /** Levy charged to each guest per night, and a share of room revenue, both paid to the government. */
    var levyPerGuestNight: Money = Money.ZERO
        internal set
    var levyRevenuePpm: Long = 0L
        internal set

    /** Value added tax on accommodation and tickets. */
    var vatPpm: Long = 0L
        internal set

    /** Month (1..12) in which an annual festival runs, 0 for none, and for how many days. */
    var festivalMonth: Int = 0
        internal set
    var festivalDays: Int = 7
        internal set

    /** Share of the day's visitors that live in the same country as the destination. */
    var domesticSharePpm: Long = 0L
        internal set

    /** Last pickup shortfall: persons that wanted a room and found none, a running average. */
    var unmetPpm: Long = 0L
        internal set

    /** Room-nights asked for and turned away, by stay date, for the nights still to come. */
    internal val demandMap = java.util.TreeMap<Long, Long>()
    internal val deniedMap = java.util.TreeMap<Long, Long>()

    internal val dayList = ArrayList<DestinationDay>()
    internal val monthList = ArrayList<DestinationMonth>()

    val days: List<DestinationDay> get() = dayList.toList()
    val months: List<DestinationMonth> get() = monthList.toList()
    val lastDay: DestinationDay? get() = dayList.lastOrNull()

    /** Sets the long-lived drivers; used when the world is built and when public works change them. */
    internal fun replaceDrivers(d: DestinationDrivers) { drivers = d }
    internal fun setPriceLevel(base: Long, effective: Long) { basePriceLevelPpm = base; priceLevelPpm = effective }
    internal fun setReputation(ppm: Long) { reputationPpm = ppm.coerceIn(0L, 1_000_000L) }
    internal fun setTaxes(levyPerGuest: Money, levyPpm: Long, vat: Long) { levyPerGuestNight = levyPerGuest; levyRevenuePpm = levyPpm; vatPpm = vat }
    internal fun setFestival(month: Int, days: Int) { festivalMonth = month; festivalDays = days }
    internal fun setDomesticShare(ppm: Long) { domesticSharePpm = ppm }
    internal fun setUnmet(ppm: Long) { unmetPpm = ppm }

    internal fun pushDay(day: DestinationDay) {
        dayList += day
        if (dayList.size > HISTORY_DAYS) dayList.subList(0, dayList.size - HISTORY_DAYS).clear()
    }

    internal fun pushMonth(m: DestinationMonth) {
        monthList += m
        if (monthList.size > HISTORY_MONTHS) monthList.subList(0, monthList.size - HISTORY_MONTHS).clear()
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(name).putLong(city.value).putLong(region.value).putLong(country.value).putInt(climate.ordinal)
        h.putInt(location.x).putInt(location.y).putBoolean(coastal)
        h.putLong(drivers.infrastructurePpm).putLong(drivers.safetyPpm).putLong(drivers.culturePpm).putLong(drivers.naturePpm).putLong(drivers.nightlifePpm)
        h.putLong(basePriceLevelPpm).putLong(priceLevelPpm).putLong(reputationPpm)
        for (m in marketingPpm) h.putLong(m)
        h.putMoney(levyPerGuestNight).putLong(levyRevenuePpm).putLong(vatPpm).putInt(festivalMonth).putInt(festivalDays)
        h.putLong(domesticSharePpm).putLong(unmetPpm)
        h.putInt(demandMap.size); for ((k, v) in demandMap) h.putLong(k).putLong(v)
        h.putInt(deniedMap.size); for ((k, v) in deniedMap) h.putLong(k).putLong(v)
        h.putInt(dayList.size)
        for (d in dayList) {
            h.putLong(d.tick).putLong(d.arrivals).putLong(d.roomNightDemand).putLong(d.roomsSold).putLong(d.roomsAvailable)
            h.putLong(d.deniedRoomNights).putLong(d.walkedGuests).putMoney(d.roomRevenue).putLong(d.guestsPresent).putLong(d.attractionVisits).putLong(d.appealPpm)
        }
        h.putInt(monthList.size)
        for (m in monthList) h.putLong(m.month).putLong(m.arrivals).putLong(m.roomsSold).putLong(m.roomsAvailable).putLong(m.deniedRoomNights).putMoney(m.roomRevenue)
    }

    companion object {
        const val HISTORY_DAYS = 120
        const val HISTORY_MONTHS = 36
    }
}
