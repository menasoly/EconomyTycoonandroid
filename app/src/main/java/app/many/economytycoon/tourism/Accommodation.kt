package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** Kinds of room a property sells. The ordinal is part of the save format. */
enum class RoomClass(val label: String, val rateMultiplierPpm: Long) {
    STANDARD("Standard", 1_000_000L), SUPERIOR("Superior", 1_350_000L), SUITE("Suite", 2_100_000L);

    companion object { val COUNT = values().size }
}

/** The kinds of accommodation. Costs are at three stars and scale with [AccommodationRules.starFactorPpm]. */
enum class PropertyKind(
    val label: String,
    val minStars: Int,
    val maxStars: Int,
    /** Rate relative to a hotel of the same stars. */
    val rateFactorPpm: Long,
    val buildCostPerRoom: Money,
    val fixedCostPerRoomDay: Money,
    val variableCostPerOccupiedRoom: Money,
    /** Spending per guest and night on food, drink and extras that the property earns. */
    val ancillaryPerGuest: Money,
    /** Staff that run the building whether or not rooms are occupied, per hundred rooms. */
    val baseStaffPerHundredRooms: Int,
    /** Staff needed per ten occupied rooms at three stars. */
    val staffPerTenOccupied: Int,
    val buildDays: Int,
) {
    HOSTEL("Hostel", 1, 2, 450_000L, Money.units(9_000), Money.units(3), Money.units(4), Money.units(4), 6, 2, 30),
    HOTEL("Hotel", 1, 5, 1_000_000L, Money.units(60_000), Money.units(14), Money.units(18), Money.units(16), 12, 5, 90),
    RESORT("Resort", 3, 5, 1_300_000L, Money.units(110_000), Money.units(26), Money.units(30), Money.units(42), 20, 8, 150),
    BOUTIQUE("Boutique hotel", 3, 5, 1_200_000L, Money.units(85_000), Money.units(20), Money.units(24), Money.units(30), 15, 7, 120);

    fun allowsStars(stars: Int): Boolean = stars in minStars..maxStars
}

/** How the nightly price is set. */
enum class RatePolicy(val label: String) { FIXED("Fixed rates"), DYNAMIC("Dynamic pricing") }

/** What a guest gets back on cancelling. Cheaper policies tie the guest in. */
enum class CancellationPolicy(val label: String, val cancelFactorPpm: Long, val chargesNoShow: Boolean, val discountPpm: Long) {
    FLEXIBLE("Flexible", 1_000_000L, false, 0L),
    MODERATE("Moderate", 600_000L, false, 50_000L),
    NON_REFUNDABLE("Non-refundable", 150_000L, true, 120_000L),
}

enum class PropertyStatus { UNDER_CONSTRUCTION, OPEN, CLOSED }

/** Price settings. Rack rate is for a standard room; other classes scale by [RoomClass.rateMultiplierPpm]. */
data class RateSettings(
    val policy: RatePolicy,
    val rackRate: Money,
    /** The lowest and highest multiple of the rack rate dynamic pricing may quote, ppm. */
    val floorPpm: Long = 600_000L,
    val ceilingPpm: Long = 1_800_000L,
    /** How hard the price follows the pickup forecast, ppm. */
    val aggressionPpm: Long = 800_000L,
    /** Occupancy the owner is aiming for, ppm. */
    val targetOccupancyPpm: Long = 780_000L,
) {
    init {
        require(rackRate.raw > 0L) { "a rack rate must be positive" }
        require(floorPpm in 1..ceilingPpm) { "floor must not exceed ceiling" }
        require(aggressionPpm in 0..2_000_000L) { "aggression out of range" }
        require(targetOccupancyPpm in 100_000L..1_000_000L) { "target occupancy out of range" }
    }
}

/** Rooms booked for one future night. Counts per room class; revenue is the sum of the rates quoted at booking. */
class NightBook {
    val booked = IntArray(RoomClass.COUNT)
    val revenueRaw = LongArray(RoomClass.COUNT)
    val bySegment = IntArray(VisitorSegment.COUNT)
    val byChannel = IntArray(Channel.COUNT)
    /** Rooms of [booked] that come from tour operator allotments, and what the operators pay for them. */
    val operatorRooms = IntArray(RoomClass.COUNT)
    val operatorRevenueRaw = LongArray(RoomClass.COUNT)
    /** Commission owed on guest bookings, raw money by intermediary id. */
    val commissionRaw = java.util.TreeMap<Long, Long>()
    /** Rooms booked through each intermediary, by intermediary id. */
    val roomsVia = java.util.TreeMap<Long, Int>()

    val total: Int get() = booked.sum()
    fun revenue(): Money = Money(revenueRaw.sum())
    fun commission(): Money = Money(commissionRaw.values.sum())
    fun addCommission(intermediary: IntermediaryId, raw: Long) { if (raw != 0L) commissionRaw.merge(intermediary.value, raw, Long::plus) }
    fun addVia(intermediary: IntermediaryId, rooms: Int) { roomsVia.merge(intermediary.value, rooms, Int::plus) }

    fun hashInto(h: CanonicalHasher) {
        for (v in booked) h.putInt(v)
        for (v in revenueRaw) h.putLong(v)
        for (v in bySegment) h.putInt(v)
        for (v in byChannel) h.putInt(v)
        for (v in operatorRooms) h.putInt(v)
        for (v in operatorRevenueRaw) h.putLong(v)
        h.putInt(commissionRaw.size)
        for ((k, v) in commissionRaw) h.putLong(k).putLong(v)
        h.putInt(roomsVia.size)
        for ((k, v) in roomsVia) h.putLong(k).putInt(v)
    }
}

/** What a property did on one night. */
data class StayDay(
    val tick: Long,
    val roomsAvailable: Int,
    val roomsSold: Int,
    val cancelled: Int,
    val noShows: Int,
    val walked: Int,
    val roomRevenue: Money,
    val ancillaryRevenue: Money,
    val commissions: Money,
    val walkCost: Money,
    val operatingCost: Money,
    val wageCost: Money,
    val levy: Money,
    val guests: Int,
    val satisfactionPpm: Long,
    val staffNeeded: Int,
    val staffHad: Int,
) {
    val occupancyPpm: Long get() = if (roomsAvailable <= 0) 0L else roomsSold * 1_000_000L / roomsAvailable
    val adr: Money get() = if (roomsSold <= 0) Money.ZERO else Money(roomRevenue.raw / roomsSold)
    val revPar: Money get() = if (roomsAvailable <= 0) Money.ZERO else Money(roomRevenue.raw / roomsAvailable)
    val totalRevenue: Money get() = roomRevenue + ancillaryRevenue

    /** Gross operating profit: all revenue less commissions, walking costs, running costs and wages. */
    val gop: Money get() = totalRevenue - commissions - walkCost - operatingCost - wageCost
}

/** The running totals of the current month; cleared when the month is settled. */
class PropertyMonth {
    var guestRoomRevenue = Money.ZERO
        internal set
    var operatorRoomRevenue = Money.ZERO
        internal set
    var ancillaryRevenue = Money.ZERO
        internal set
    var domesticRevenue = Money.ZERO
        internal set
    var commissions = Money.ZERO
        internal set
    var walkCost = Money.ZERO
        internal set
    var operatingCost = Money.ZERO
        internal set
    var wageCost = Money.ZERO
        internal set
    var levy = Money.ZERO
        internal set
    var roomsSold = 0L
        internal set
    var roomsAvailable = 0L
        internal set
    internal val commissionBy = java.util.TreeMap<IntermediaryId, Money>()

    fun commissionOf(i: IntermediaryId): Money = commissionBy[i] ?: Money.ZERO
    val commissionIntermediaries: List<IntermediaryId> get() = commissionBy.keys.toList()

    internal fun add(day: StayDay, guestRevenue: Money, operatorRevenue: Money, domesticShare: Long) {
        guestRoomRevenue += guestRevenue; operatorRoomRevenue += operatorRevenue; ancillaryRevenue += day.ancillaryRevenue
        domesticRevenue += Money(mulDiv(guestRevenue.raw + day.ancillaryRevenue.raw, domesticShare, 1_000_000L))
        commissions += day.commissions; walkCost += day.walkCost; operatingCost += day.operatingCost; wageCost += day.wageCost; levy += day.levy
        roomsSold += day.roomsSold; roomsAvailable += day.roomsAvailable
    }

    internal fun addCommission(i: IntermediaryId, amount: Money) { commissionBy[i] = (commissionBy[i] ?: Money.ZERO) + amount }

    internal fun clear() {
        guestRoomRevenue = Money.ZERO; operatorRoomRevenue = Money.ZERO; ancillaryRevenue = Money.ZERO; domesticRevenue = Money.ZERO
        commissions = Money.ZERO; walkCost = Money.ZERO; operatingCost = Money.ZERO; wageCost = Money.ZERO; levy = Money.ZERO
        roomsSold = 0L; roomsAvailable = 0L; commissionBy.clear()
    }

    fun hashInto(h: CanonicalHasher) {
        h.putMoney(guestRoomRevenue).putMoney(operatorRoomRevenue).putMoney(ancillaryRevenue).putMoney(domesticRevenue).putMoney(commissions)
        h.putMoney(walkCost).putMoney(operatingCost).putMoney(wageCost).putMoney(levy).putLong(roomsSold).putLong(roomsAvailable)
        h.putInt(commissionBy.size)
        for ((k, v) in commissionBy) h.putLong(k.value).putMoney(v)
    }
}

/** One accommodation property. Identity never changes; everything else is written by [TourismService]. */
class Property(
    val id: PropertyId,
    val destination: DestinationId,
    val owner: CompanyId?,
    val facility: FacilityId?,
    val kind: PropertyKind,
    val name: String,
) {
    var stars: Int = 3
        internal set
    val rooms: IntArray = IntArray(RoomClass.COUNT)
    var settings: RateSettings = RateSettings(RatePolicy.FIXED, Money.units(100))
        internal set
    var overbookPpm: Long = 0L
        internal set
    var cancellation: CancellationPolicy = CancellationPolicy.FLEXIBLE
        internal set
    var conditionPpm: Long = 1_000_000L
        internal set
    var status: PropertyStatus = PropertyStatus.OPEN
        internal set
    /** First night the property can sell, for one under construction. */
    var opensAt: Long = 0L
        internal set
    var openedAt: Long = 0L
        internal set
    /** How well staffed the property was on its last night, ppm of the staff it needed. */
    var serviceLevelPpm: Long = 1_000_000L
        internal set
    var lifetimeRevenue: Money = Money.ZERO
        internal set
    var lifetimeRoomNights: Long = 0L
        internal set
    var lifetimeWalked: Long = 0L
        internal set
    var invested: Money = Money.ZERO
        internal set

    val reviews = ReviewBook()
    internal val listed = java.util.TreeSet<IntermediaryId>()
    internal val bookMap = java.util.TreeMap<Long, NightBook>()
    internal val dayList = ArrayList<StayDay>()
    val month = PropertyMonth()

    val listedOn: Set<IntermediaryId> get() = listed.toSet()
    val days: List<StayDay> get() = dayList.toList()
    val lastDay: StayDay? get() = dayList.lastOrNull()
    fun book(stayDate: Long): NightBook? = bookMap[stayDate]
    val bookedNights: List<Long> get() = bookMap.keys.toList()

    val totalRooms: Int get() = rooms.sum()
    val isOpen: Boolean get() = status == PropertyStatus.OPEN

    /** Rooms taken out of service by wear, ppm of all rooms. Below 60% condition rooms start to fail. */
    fun outOfOrderPpm(): Long = if (conditionPpm >= 600_000L) 0L else ((600_000L - conditionPpm) * 3L / 4L).coerceAtMost(450_000L)

    /** Sellable rooms of a class after rooms out of order. */
    fun sellable(c: RoomClass): Int = (rooms[c.ordinal].toLong() * (1_000_000L - outOfOrderPpm()) / 1_000_000L).toInt()

    fun sellableTotal(): Int = RoomClass.values().sumOf { sellable(it) }

    /** Rooms the property will accept bookings for in a class, counting the overbooking allowance. */
    fun sellLimit(c: RoomClass): Int {
        val base = sellable(c)
        return base + (base.toLong() * overbookPpm / 1_000_000L).toInt()
    }

    /** The posted rate of a class, before dynamic pricing. */
    fun rackRate(c: RoomClass): Money = Money(mulDiv(settings.rackRate.raw, c.rateMultiplierPpm, 1_000_000L))

    internal fun pushDay(day: StayDay) {
        dayList += day
        if (dayList.size > HISTORY_DAYS) dayList.subList(0, dayList.size - HISTORY_DAYS).clear()
    }

    internal fun nightBook(stayDate: Long): NightBook = bookMap.getOrPut(stayDate) { NightBook() }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(destination.value).putLong(owner?.value ?: -1L).putLong(facility?.value ?: -1L).putInt(kind.ordinal).putString(name)
        h.putInt(stars)
        for (r in rooms) h.putInt(r)
        h.putInt(settings.policy.ordinal).putMoney(settings.rackRate).putLong(settings.floorPpm).putLong(settings.ceilingPpm)
        h.putLong(settings.aggressionPpm).putLong(settings.targetOccupancyPpm)
        h.putLong(overbookPpm).putInt(cancellation.ordinal).putLong(conditionPpm).putInt(status.ordinal).putLong(opensAt).putLong(openedAt)
        h.putLong(serviceLevelPpm).putMoney(lifetimeRevenue).putLong(lifetimeRoomNights).putLong(lifetimeWalked).putMoney(invested)
        reviews.hashInto(h)
        h.putInt(listed.size); for (i in listed) h.putLong(i.value)
        h.putInt(bookMap.size)
        for ((k, v) in bookMap) { h.putLong(k); v.hashInto(h) }
        h.putInt(dayList.size)
        for (d in dayList) {
            h.putLong(d.tick).putInt(d.roomsAvailable).putInt(d.roomsSold).putInt(d.cancelled).putInt(d.noShows).putInt(d.walked)
            h.putMoney(d.roomRevenue).putMoney(d.ancillaryRevenue).putMoney(d.commissions).putMoney(d.walkCost).putMoney(d.operatingCost)
            h.putMoney(d.wageCost).putMoney(d.levy).putInt(d.guests).putLong(d.satisfactionPpm).putInt(d.staffNeeded).putInt(d.staffHad)
        }
        month.hashInto(h)
    }

    companion object {
        const val HISTORY_DAYS = 120
    }
}

/** Fixed rules about what accommodation costs and charges; all pure. */
object AccommodationRules {
    /** Cost and rate scale by stars: 1 to 5. */
    fun starFactorPpm(stars: Int): Long = when (stars.coerceIn(1, 5)) {
        1 -> 500_000L
        2 -> 750_000L
        3 -> 1_000_000L
        4 -> 1_600_000L
        else -> 2_600_000L
    }

    /** The rack rate a standard room of this kind and stars would carry at the reference price level. */
    fun referenceRack(kind: PropertyKind, stars: Int): Money {
        val threeStar = Money.units(105)
        return Money(mulDiv(mulDiv(threeStar.raw, starFactorPpm(stars), 1_000_000L), kind.rateFactorPpm, 1_000_000L))
    }

    fun buildCost(kind: PropertyKind, stars: Int, rooms: Int): Money =
        Money(mulDiv(kind.buildCostPerRoom.raw * rooms, starFactorPpm(stars), 1_000_000L))

    /** The cost of lifting a property from its current stars to [to], on all its rooms. */
    fun upgradeCost(kind: PropertyKind, from: Int, to: Int, rooms: Int): Money {
        val diff = buildCost(kind, to, rooms) - buildCost(kind, from, rooms)
        return Money(diff.raw * 6 / 10).coerceAtLeast(Money.ZERO)
    }

    fun fixedCostPerDay(kind: PropertyKind, stars: Int, rooms: Int): Money =
        Money(mulDiv(kind.fixedCostPerRoomDay.raw * rooms, starFactorPpm(stars), 1_000_000L))

    fun variableCost(kind: PropertyKind, stars: Int, occupied: Int): Money =
        Money(mulDiv(kind.variableCostPerOccupiedRoom.raw * occupied, starFactorPpm(stars), 1_000_000L))

    /** Spending on food, drink and extras per guest and night. */
    fun ancillaryPerGuest(kind: PropertyKind, stars: Int): Money =
        Money(mulDiv(kind.ancillaryPerGuest.raw, starFactorPpm(stars), 1_000_000L))

    /** The money a property earns when sold for cash: the build cost less wear. */
    fun bookValue(p: Property): Money = Money(mulDiv(buildCost(p.kind, p.stars, p.totalRooms).raw, 300_000L + p.conditionPpm * 7L / 10L, 1_000_000L))
}
