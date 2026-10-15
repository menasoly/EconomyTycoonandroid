package app.many.economytycoon.world

import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.SegmentId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.WorldTestSupport.generate
import app.many.economytycoon.world.WorldTestSupport.hash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldSystemsTest {
    // ----- population -----------------------------------------------------------------------

    @Test fun naturalChangeGrowsAHealthyPopulationSlowly() {
        val p = PopulationState(CityId(1), 200_000, 650_000, 150_000)
        val before = p.total
        repeat(12) { Demography.naturalChange(p, DemographicParams(), 30) }
        assertTrue(p.total > before)
        assertTrue(p.total < before * 11 / 10)
    }

    @Test fun smallPopulationsStillEvolveThroughRemainderCarry() {
        val p = PopulationState(CityId(1), 40, 120, 20)
        repeat(120) { Demography.naturalChange(p, DemographicParams(), 30) }
        assertTrue(p.working != 120L || p.seniors != 20L)
    }

    @Test fun migrationConservesTotalPopulation() {
        val (w, _) = generate(9)
        val pops = w.populations()
        val before = pops.sumOf { it.total }
        pops.first().averageWage = Money.units(300)
        repeat(24) { Demography.migrate(pops, DemographicParams(), Money.units(90), 30) }
        assertEquals(before, pops.sumOf { it.total })
    }

    @Test fun higherWagesAttractWorkers() {
        val rich = PopulationState(CityId(1), 100, 500_000, 100); rich.averageWage = Money.units(180)
        val poor = PopulationState(CityId(2), 100, 500_000, 100); poor.averageWage = Money.units(40); poor.employed = 100_000
        val ref = Money.units(90)
        assertTrue(Demography.attractiveness(rich, ref) > Demography.attractiveness(poor, ref))
        val before = rich.working
        repeat(12) { Demography.migrate(listOf(rich, poor), DemographicParams(), ref, 30) }
        assertTrue(rich.working > before)
    }

    @Test fun bandSharesAlwaysSumToOne() {
        val p = PopulationState(CityId(1), 100, 100_000, 100)
        for (wage in listOf(20L, 60L, 90L, 200L, 600L)) {
            p.averageWage = Money.units(wage)
            repeat(60) { Demography.updateBands(p, Money.units(90), 350_000) }
            assertEquals(1_000_000L, p.lowShare + p.middleShare + p.highShare)
            assertTrue(p.highShare >= 0)
        }
        assertEquals(p.total, IncomeBand.values().sumOf { p.inBand(it) })
    }

    @Test fun rentRisesWithCrowding() {
        val p = PopulationState(CityId(1), 100_000, 400_000, 100_000)
        repeat(60) { Demography.updateRent(p, 300_000) }
        val crowded = p.rentIndexPpm
        repeat(60) { Demography.updateRent(p, 1_200_000) }
        assertTrue(crowded > p.rentIndexPpm)
    }

    // ----- deposits -------------------------------------------------------------------------

    private fun deposit(kind: DepositKind = DepositKind.FINITE, regrow: Long = 0L) =
        Deposit(DepositId(1), RegionId(1), Point(1, 1), GoodId("iron_ore"), kind, Quantity.units(1_000), 1_000_000L, regrow)

    @Test fun extractionDepletesAndGetsHarder() {
        val d = deposit()
        val first = d.extract(Quantity.units(100))
        assertEquals(Quantity.units(100), first)
        d.extract(Quantity.units(750))
        val hard = d.extract(Quantity.units(100))
        assertTrue(hard < Quantity.units(100))
        assertEquals(Quantity.units(50), d.remaining)
        d.extract(Quantity.units(10_000))
        assertTrue(d.exhausted)
        assertEquals(Quantity.ZERO, d.extract(Quantity.units(1)))
    }

    @Test fun renewableDepositsRegrowUpToCapacity() {
        val d = deposit(DepositKind.RENEWABLE, 10_000)
        d.extract(Quantity.units(500))
        repeat(200) { d.regrow() }
        assertEquals(Quantity.units(1_000), d.remaining)
    }

    @Test fun finiteDepositsNeverRegrow() {
        val d = deposit(DepositKind.FINITE, 10_000)
        d.extract(Quantity.units(500))
        assertEquals(Quantity.ZERO, d.regrow())
    }

    @Test fun claimingRequiresSurveyAndIsExclusive() {
        val reg = DepositRegistry()
        reg.add(deposit())
        assertFalse(reg.claim(DepositId(1), CompanyId(1)))
        assertTrue(reg.survey(DepositId(1)))
        assertFalse(reg.survey(DepositId(1)))
        assertTrue(reg.claim(DepositId(1), CompanyId(1)))
        assertFalse(reg.claim(DepositId(1), CompanyId(2)))
        assertEquals(Quantity.units(1_000), reg.knownReserve(GoodId("iron_ore")))
    }

    // ----- network --------------------------------------------------------------------------

    private fun line(): TransportNetwork {
        val n = TransportNetwork()
        n.add(Segment(SegmentId(1), CityId(1), CityId(2), TransportMode.ROAD, 100, Quantity.units(1_000)))
        n.add(Segment(SegmentId(2), CityId(2), CityId(3), TransportMode.ROAD, 100, Quantity.units(1_000)))
        n.add(Segment(SegmentId(3), CityId(1), CityId(3), TransportMode.ROAD, 400, Quantity.units(1_000)))
        return n
    }

    @Test fun routingPrefersTheCheaperPath() {
        val r = line().route(CityId(1), CityId(3), 0)!!
        assertEquals(listOf(SegmentId(1), SegmentId(2)), r.segments)
        assertEquals(200, r.distance)
    }

    @Test fun closedSegmentsAreAvoidedUntilTheyReopen() {
        val n = line()
        n.close(SegmentId(1), 50)
        assertEquals(listOf(SegmentId(3)), n.route(CityId(1), CityId(3), 10)!!.segments)
        assertEquals(listOf(SegmentId(1), SegmentId(2)), n.route(CityId(1), CityId(3), 60)!!.segments)
    }

    @Test fun congestionDivertsTraffic() {
        val n = line()
        n.segment(SegmentId(1))!!.reserve(Quantity.units(990))
        n.invalidate()
        assertEquals(listOf(SegmentId(3)), n.route(CityId(1), CityId(3), 0)!!.segments)
    }

    @Test fun reservationNeverExceedsCapacity() {
        val s = Segment(SegmentId(1), CityId(1), CityId(2), TransportMode.RAIL, 50, Quantity.units(100))
        assertEquals(Quantity.units(60), s.reserve(Quantity.units(60)))
        assertEquals(Quantity.units(40), s.reserve(Quantity.units(60)))
        assertEquals(Quantity.ZERO, s.reserve(Quantity.units(1)))
        s.resetLoad()
        assertEquals(Quantity.units(100), s.available())
    }

    @Test fun modeRestrictionsAreHonored() {
        val n = line()
        n.add(Segment(SegmentId(4), CityId(1), CityId(3), TransportMode.AIR, 280, Quantity.units(100)))
        assertNull(n.route(CityId(1), CityId(3), 0, setOf(TransportMode.SEA)))
        val air = n.route(CityId(1), CityId(3), 0, setOf(TransportMode.AIR))!!
        assertEquals(listOf(TransportMode.AIR), air.modes)
        assertTrue(air.ticks < n.route(CityId(1), CityId(3), 0, setOf(TransportMode.ROAD))!!.ticks)
    }

    @Test fun unreachableCitiesHaveNoRoute() {
        val n = line()
        assertNull(n.route(CityId(1), CityId(99), 0))
        assertEquals(0, n.route(CityId(2), CityId(2), 0)!!.distance)
    }

    @Test fun wearLowersQualityAndRepairRestoresIt() {
        val s = Segment(SegmentId(1), CityId(1), CityId(2), TransportMode.ROAD, 50, Quantity.units(100))
        repeat(100) { s.reserve(Quantity.units(100)); s.wear(); s.resetLoad() }
        assertTrue(s.qualityPpm < 1_000_000L)
        s.repair(2_000_000L)
        assertEquals(1_000_000L, s.qualityPpm)
    }

    // ----- land -----------------------------------------------------------------------------

    @Test fun landPriceReflectsLocationAndZone() {
        val core = Parcel(app.many.economytycoon.core.ParcelId(1), RegionId(1), CityId(1), Point(100, 100), ZoneType.COMMERCIAL, 40, Terrain.PLAINS)
        val edge = Parcel(app.many.economytycoon.core.ParcelId(2), RegionId(1), CityId(1), Point(130, 100), ZoneType.COMMERCIAL, 40, Terrain.PLAINS)
        val farm = Parcel(app.many.economytycoon.core.ParcelId(3), RegionId(1), CityId(1), Point(100, 100), ZoneType.AGRICULTURAL, 40, Terrain.PLAINS)
        val center = Point(100, 100)
        val pc = LandPricing.price(core, 500_000, center)
        assertTrue(pc > LandPricing.price(edge, 500_000, center))
        assertTrue(pc > LandPricing.price(farm, 500_000, center))
        assertTrue(LandPricing.price(core, 1_000_000, center) > pc)
    }

    @Test fun ownershipIsExclusiveAndProtectedLandCannotBeBought() {
        val reg = LandRegistry()
        val p = Parcel(app.many.economytycoon.core.ParcelId(1), RegionId(1), CityId(1), Point(1, 1), ZoneType.INDUSTRIAL, 100, Terrain.PLAINS)
        val park = Parcel(app.many.economytycoon.core.ParcelId(2), RegionId(1), CityId(1), Point(2, 2), ZoneType.PROTECTED, 100, Terrain.PLAINS)
        reg.add(p); reg.add(park)
        assertTrue(reg.assign(p.id, CompanyId(1), Money.units(10)))
        assertFalse(reg.assign(p.id, CompanyId(2), Money.units(10)))
        assertFalse(reg.assign(park.id, CompanyId(1), Money.units(10)))
        assertFalse(reg.rezone(park.id, ZoneType.COMMERCIAL))
        reg.release(p.id)
        assertTrue(reg.rezone(p.id, ZoneType.COMMERCIAL))
        assertEquals(ZoneType.COMMERCIAL, p.effectiveZone)
    }

    // ----- weather and climate --------------------------------------------------------------

    @Test fun weatherIsDeterministicForASeed() {
        fun run(): List<WeatherEvent> {
            val (w, _) = generate(77)
            val rp = RandomProvider(1234)
            for (t in 1L..720L) w.updateTick(Tick(t), rp)
            return w.weather.active() + w.weather.history()
        }
        val a = run(); val b = run()
        assertTrue(a.isNotEmpty())
        assertEquals(a, b)
    }

    @Test fun droughtHurtsFarmingAndFloodSlowsTransport() {
        val region = Region(RegionId(1), app.many.economytycoon.core.CountryId(1), "r", Point(1, 1), Terrain.PLAINS, ClimateZone.TEMPERATE, 1000, 800_000, false)
        val ws = WeatherSystem(listOf(region))
        val base = ws.agriMultiplier(region, Tick(100))
        ws.restore(listOf(WeatherEvent(region.id, WeatherKind.DROUGHT, Tick(90), 30, 1_000_000), WeatherEvent(region.id, WeatherKind.FLOOD, Tick(90), 30, 1_000_000)), emptyList())
        assertTrue(ws.agriMultiplier(region, Tick(100)) < base)
        assertTrue(ws.transportMultiplier(region, Tick(100)) > 1_500_000)
    }

    @Test fun coldClimatesHaveNoWinterHarvest() {
        assertEquals(0L, ClimateTable.profile(ClimateZone.COLD, app.many.economytycoon.core.Season.WINTER).agriYield)
    }

    // ----- whole world ----------------------------------------------------------------------

    @Test fun yearOfSimulationKeepsThePopulationPositiveAndStable() {
        val (w, _) = generate(101)
        val rp = RandomProvider(5)
        val start = w.totalPopulation()
        for (t in 1L..720L) w.updateTick(Tick(t), rp)
        val end = w.totalPopulation()
        assertTrue(end > start * 9 / 10)
        assertTrue(end < start * 13 / 10)
        assertTrue(w.populations().all { it.total > 0 })
    }

    @Test fun saveAndLoadReproducesTheWorldHash() {
        val (w, _) = generate(55)
        val rp = RandomProvider(2)
        for (t in 1L..200L) w.updateTick(Tick(t), rp)
        w.deposits.all().first().extract(Quantity.units(10))
        val bw = ByteWriter(); WorldCodec.write(w, bw)
        val back = WorldCodec.read(ByteReader(bw.toByteArray()))
        assertEquals(hash(w), hash(back))
        assertEquals(w.cities().size, back.cities().size)
        assertNotNull(back.network.route(w.cities().first().id, w.cities().last().id, 0))
    }

    @Test fun loadedWorldContinuesIdenticallyToTheOriginal() {
        val (a, _) = generate(56)
        val rpA = RandomProvider(3)
        for (t in 1L..100L) a.updateTick(Tick(t), rpA)
        val bw = ByteWriter(); WorldCodec.write(a, bw)
        val b = WorldCodec.read(ByteReader(bw.toByteArray()))
        val rpB = RandomProvider(3)
        rpB.restore(rpA.snapshot())
        for (t in 101L..300L) { a.updateTick(Tick(t), rpA); b.updateTick(Tick(t), rpB) }
        assertEquals(hash(a), hash(b))
    }

    @Test fun truncatedWorldSaveIsRejected() {
        val (w, _) = generate(57, WorldSize.SMALL)
        val bw = ByteWriter(); WorldCodec.write(w, bw)
        val bytes = bw.toByteArray()
        var failed = false
        try { WorldCodec.read(ByteReader(bytes.copyOf(bytes.size - 40))) } catch (e: Exception) { failed = true }
        assertTrue(failed)
    }
}
