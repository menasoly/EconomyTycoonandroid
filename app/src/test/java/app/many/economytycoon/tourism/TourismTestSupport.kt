package app.many.economytycoon.tourism

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** A small tourism world for the tests: one destination, a home market and a foreign one, and helpers to add things. */
class TourismWorld(
    val seed: Long = 7L,
    climate: ClimateZone = ClimateZone.MEDITERRANEAN,
    startTick: Long = 150L,
    homeOutbound: Long = 140L,
    abroadOutbound: Long = 200L,
    drivers: DestinationDrivers = DestinationDrivers(infrastructurePpm = 700_000L, safetyPpm = 800_000L, culturePpm = 600_000L, naturePpm = 650_000L, nightlifePpm = 500_000L),
) {
    val service = TourismService()
    val state get() = service.state
    var random = RandomProvider(seed)
    var tick = startTick
    var inputs = DayInputs()
    val owner = CompanyId(100)
    val rival = CompanyId(200)

    val dest: Destination = service.addDestination(CityId(1), RegionId(1), CountryId(1), "Costa", climate, Point(500, 500), true, drivers, festivalMonth = 0).getOrThrow()
    val home: OriginMarket = service.addOrigin("Homeland", CountryId(1), Point(450, 450), homeOutbound).getOrThrow()
    val abroad: OriginMarket = service.addOrigin("Overseas", CountryId(2), Point(100, 100), abroadOutbound, 1_100_000L).getOrThrow()

    fun hotel(
        stars: Int = 3, rooms: IntArray = intArrayOf(80, 15, 5), who: CompanyId? = owner, kind: PropertyKind = PropertyKind.HOTEL,
        name: String = "Hotel ${state.properties.size + 1}", destination: Destination = dest, rack: Money? = null,
    ): Property = service.addProperty(destination.id, who, null, kind, stars, rooms, Tick(tick), name, rack, instant = true).getOrThrow().property

    fun attraction(kind: AttractionKind = AttractionKind.MUSEUM, who: CompanyId? = owner, capacity: Int = kind.referenceCapacity): Attraction =
        service.addAttraction(dest.id, who, null, kind, "${kind.label} ${state.attractions.size + 1}", Tick(tick), capacity, instant = true).getOrThrow().attraction

    fun platform(kind: IntermediaryKind = IntermediaryKind.ONLINE_PLATFORM, commission: Long = 150_000L, who: CompanyId? = null): Intermediary =
        service.addIntermediary(kind, who, "${kind.label} ${state.intermediaries.size + 1}", commission).getOrThrow()

    fun operator(who: CompanyId? = null, strength: Long = 1_000_000L, margin: Long = 180_000L): TourOperator =
        service.addOperator(who, "Operator ${state.operators.size + 1}", strength, margin).getOrThrow()

    fun allotment(
        op: TourOperator, p: Property, rooms: Int = 10, room: RoomClass = RoomClass.STANDARD, netShare: Long = 650_000L, days: Int = 120,
        release: Int = 3, committed: Long = 0L, side: ContractSide = ContractSide.PROPERTY,
    ): Allotment {
        val rack = p.rackRate(room)
        val net = Money(rack.raw * netShare / 1_000_000L)
        val proposer = if (side == ContractSide.PROPERTY) requireNotNull(p.owner) else requireNotNull(op.owner)
        return service.proposeAllotment(side, proposer, op.id, p.id, room, rooms, net, Tick(tick), days, release, committed, Tick(tick)).getOrThrow()
    }

    fun day(inputs: DayInputs = this.inputs): DayReport {
        val r = service.advanceDay(Tick(tick), inputs, random)
        tick++
        return r
    }

    fun days(n: Int, inputs: DayInputs = this.inputs): List<DayReport> = List(n) { day(inputs) }

    /** Settles the month the last day advanced belongs to. */
    fun settle(): SettlementRun = service.settleMonth(Tick(tick - 1))
}

object TourismInvariants {
    /** Properties that must hold after any number of days. */
    fun check(state: TourismState) {
        for (p in state.properties) {
            assertTrue("rooms of ${p.id}", p.rooms.all { it >= 0 } && p.totalRooms >= 1)
            assertTrue("condition of ${p.id}", p.conditionPpm in 0..1_000_000L)
            assertTrue("service level of ${p.id}", p.serviceLevelPpm in 0..1_000_000L)
            assertTrue("rating of ${p.id}", p.reviews.scorePpm in 0..1_000_000L)
            for ((date, nb) in p.bookMap) {
                for (c in RoomClass.values()) {
                    val i = c.ordinal
                    assertTrue("booked non-negative", nb.booked[i] >= 0 && nb.operatorRooms[i] >= 0 && nb.operatorRooms[i] <= nb.booked[i])
                    assertTrue("booking within the sell limit on $date: ${nb.booked[i]} > ${p.sellLimit(c)} for $c of ${p.id}", nb.booked[i] <= p.sellLimit(c) + 0)
                    assertTrue("operator revenue part of revenue", nb.operatorRevenueRaw[i] <= nb.revenueRaw[i])
                }
                assertEquals("segment rooms add up", nb.total, nb.bySegment.sum())
                assertEquals("channel rooms add up", nb.total, nb.byChannel.sum())
            }
            for (d in p.days) {
                assertTrue("sold within available on ${d.tick}", d.roomsSold <= d.roomsAvailable)
                assertTrue("non-negative money", d.roomRevenue.raw >= 0L && d.ancillaryRevenue.raw >= 0L)
            }
        }
        for (d in state.destinations) {
            for (day in d.days) assertTrue("destination sold within available", day.roomsSold <= day.roomsAvailable)
            assertTrue("reputation", d.reputationPpm in 0..1_000_000L)
            for (m in d.marketingPpm) assertTrue("marketing", m in 0..1_000_000L)
        }
        for (a in state.allotments) {
            for (date in a.soldDates) assertTrue("allotment sold within size", a.soldOn(date) <= a.roomsPerNight)
        }
    }
}

/** A busy world with a bit of everything, run for a while, for tests of the save format and the hash. */
object RichWorld {
    fun build(seed: Long = 7L, days: Int = 50): TourismWorld {
        val w = TourismWorld(seed)
        w.service.setTaxes(w.dest.id, Money.units(2), 20_000L, 80_000L).getOrThrow()
        w.service.setFestival(w.dest.id, 7, 10).getOrThrow()
        val a = w.hotel(3)
        val b = w.hotel(4, intArrayOf(60, 30, 10), rack = Money.units(190))
        w.hotel(2, intArrayOf(100, 0, 0), who = w.rival)
        w.service.setPricing(b.id, RatePolicy.DYNAMIC, b.settings.rackRate).getOrThrow()
        w.service.setOverbooking(a.id, 50_000L).getOrThrow()
        w.attraction(AttractionKind.MUSEUM)
        val platform = w.platform()
        w.service.listWith(a.id, platform.id).getOrThrow()
        val op = w.operator()
        w.allotment(op, b, rooms = 8, committed = 500_000L)
        w.service.launchCampaign(w.dest.id, w.owner, VisitorSegment.FAMILY, Money.units(50_000), Tick(w.tick)).getOrThrow()
        w.service.triggerShock(ShockKind.STRIKE, w.dest.id, null, Tick(w.tick + 10), 6, 600_000L, "Rail strike").getOrThrow()
        w.days(days)
        w.settle()
        return w
    }
}
