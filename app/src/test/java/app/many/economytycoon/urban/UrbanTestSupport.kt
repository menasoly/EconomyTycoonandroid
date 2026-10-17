package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Fixtures shared by the urban tests. */
object UrbanTestSupport {
    val params = UrbanParams()
    val city = CityId(1)
    val country = CountryId(1)

    fun inputs(population: Long = 150_000L, wage: Money = Money.units(90), low: Long = 450_000L) =
        CityInputs(population, wage, lowIncomeSharePpm = low)

    fun service(seed: Long = 7L, p: UrbanParams = params) = UrbanService(UrbanState(p, seed))

    fun withCity(seed: Long = 7L, population: Long = 150_000L, id: CityId = city, p: UrbanParams = params): UrbanService {
        val svc = service(seed, p)
        svc.addCity(id, country, "Testville", inputs(population), Tick(0)).getOrThrow()
        return svc
    }

    fun plan(svc: UrbanService, id: CityId = city): CityPlan = svc.state.requirePlan(id)

    /** Advances [days] days from tick [from]; month starts (every 30th tick) run the monthly cycle. Returns the next tick. */
    fun run(svc: UrbanService, from: Long, days: Int, population: Long = 150_000L, collect: MutableList<UrbanNotice>? = null, intents: MutableList<app.many.economytycoon.finance.SettlementIntent>? = null): Long {
        var t = from
        repeat(days) {
            t++
            val step = svc.step(Tick(t), t % 30L == 0L) { inputs(population) }
            collect?.addAll(step.notices)
            intents?.addAll(step.intents)
        }
        return t
    }

    /** A district with [floorArea] of buildings; zero by default. */
    fun district(index: Int = 0, zone: ZoneUse = ZoneUse.RESIDENTIAL, floorArea: Long = 0L, land: Long = 40_000L, rent: Money = Money.units(11)): District {
        val d = District(DistrictId(index + 1L), city, index, "District $index", zone, DensityRules.defaultsFor(zone), land)
        d.stock.addNew(floorArea)
        d.rentPerSqm = rent
        return d
    }

    /** The nine districts of a default city, all zoned [zone] unless [zones] says otherwise. */
    fun districts(zone: ZoneUse = ZoneUse.RESIDENTIAL, floorArea: Long = 20_000L, zones: Map<Int, ZoneUse> = emptyMap()): List<District> =
        List(9) { district(it, zones[it] ?: zone, floorArea) }

    fun geometry() = CityGeometry(12, 4, 2_500L)

    /** Roads between every pair of neighbouring districts of the default layout. */
    fun gridNetwork(roadClass: RoadClass = RoadClass.ARTERIAL, lanes: Int = 2): TransitNetwork {
        val g = geometry()
        val net = TransitNetwork(g)
        var id = 1L
        for (d in 0 until g.districtCount) for (o in g.neighbours(d)) if (o > d) net.addRoad(LinkId(id++), d, o, roadClass, lanes).getOrThrow()
        return net
    }

    /** Plans a line and puts it into service. */
    fun activeLine(net: TransitNetwork, id: Long, name: String, mode: TransitMode, stops: List<Int>, headway: Int = 300, fare: Money = Money.units(2)): TransitLine {
        val line = net.planLine(TransitLineId(id), name, mode, stops, headway, fare, false).getOrThrow()
        line.status = LineStatus.ACTIVE
        return line
    }

    /** Districts with residents in the first column and jobs in the last, to make a commute. */
    fun commuterDistricts(residents: Long = 6_000L, jobs: Long = 4_000L): List<District> {
        val ds = districts()
        for (i in 0 until 9) {
            when (i % 3) { 0 -> ds[i].residents = residents; 2 -> ds[i].jobs = jobs; else -> ds[i].jobs = jobs / 4 }
        }
        return ds
    }

    /** Structural invariants that must hold after any update. */
    fun checkInvariants(plan: CityPlan, p: UrbanParams = params) {
        assertTrue("residents fit the population", plan.population <= plan.inputs.population)
        for (d in plan.districts) {
            assertTrue("area ${d.name}", d.stock.totalArea >= 0L)
            for (b in 0 until BuildingStock.BANDS) {
                assertTrue(d.stock.areaOf(b) >= 0L)
                assertTrue(d.stock.conditionOf(b) in 0L..Scale.PPM)
            }
            assertTrue("vacancy ${d.vacancyPpm}", d.vacancyPpm in 0L..Scale.PPM)
            assertTrue("low share ${d.lowIncomeSharePpm}", d.lowIncomeSharePpm in 0L..Scale.PPM)
            assertTrue("backlash", d.backlashPpm in 0L..Scale.PPM)
            assertTrue("residents ${d.residents} <= capacity", d.residents <= Zoning.capacity(d.zone, d.floorArea, p) || d.floorArea < d.residentialArea)
            assertTrue("rent", !d.rentPerSqm.isNegative)
            assertTrue("accessibility", d.accessibilityPermille in 0..1000)
            assertTrue("protection", d.tenantProtectionPermille in 0..1000)
        }
        assertTrue("cash", !plan.budget.cash.isNegative)
        assertTrue("debt", !plan.budget.debt.isNegative)
        assertEquals(1, plan.council.seats.size % 2)
        assertTrue(plan.traffic.carSharePpm + plan.traffic.transitSharePpm + plan.traffic.walkSharePpm == Scale.PPM || plan.traffic.commuters == 0L)
    }
}
