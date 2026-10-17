package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Small worlds for the energy grid tests. Power figures are kilowatts. */
class GridWorld(val seed: Long = 42L, params: GridParams = GridParams(operatorPlans = false), climate: ClimateZone = ClimateZone.TEMPERATE) {
    val service = EnergyGridService()
    val state get() = service.state
    var random = RandomProvider(seed)
    var tick = 0L
    val inputs = DayInputs()
    val grid: Grid = service.addGrid(RegionId(1), CountryId(1), "North", climate, params).getOrThrow()
    val owner = CompanyId(100)
    val buyer = CompanyId(200)
    private var nextFacility = 1L

    fun unit(tech: Technology, kw: Long, owner: CompanyId? = null, grid: Grid = this.grid): GenUnit =
        service.addUnit(grid.id, tech, kw, owner, Tick(0)).getOrThrow()

    fun households(kw: Long, grid: Grid = this.grid): GridLoad = service.setHouseholdKw(grid.id, kw).getOrThrow()

    fun facility(kw: Long, customer: CustomerClass = CustomerClass.INDUSTRIAL, company: CompanyId = buyer, grid: Grid = this.grid): GridLoad =
        service.connectFacility(grid.id, customer, company, FacilityId(nextFacility++), kw).getOrThrow()

    fun otherGrid(region: Long = 2L, climate: ClimateZone = ClimateZone.TEMPERATE): Grid =
        service.addGrid(RegionId(region), CountryId(1), "Region $region", climate, GridParams(operatorPlans = false)).getOrThrow()

    fun day(inputs: DayInputs = this.inputs): DayReport {
        val r = service.advanceDay(Tick(tick), inputs, random)
        tick++
        return r
    }

    fun days(n: Int, inputs: DayInputs = this.inputs): List<DayReport> = List(n) { day(inputs) }
}

object GridInvariants {
    /** Checks the properties that must hold after any number of days. */
    fun check(state: EnergyGridState) {
        for (g in state.grids) {
            for (u in g.units) {
                assertTrue("output ${u.outputKw} within ${u.capacityKw} for ${u.id}", u.outputKw in 0L..u.capacityKw)
                assertTrue("soc of ${u.id}", u.socKwh in 0L..maxOf(u.storageCapacityKwh, 0L))
                if (!u.status.canProduce) assertEquals("idle unit ${u.id}", 0L, u.outputKw)
                assertTrue(u.efficiencyPpm >= 0L)
            }
            for (l in g.loads) assertTrue(l.monthServedKwh >= 0L && l.monthShedKwh >= 0L)
            var lastTick = -1L
            for (d in g.history) {
                assertTrue("history is in order", d.tick > lastTick)
                lastTick = d.tick
                assertTrue(d.unservedKwh in 0L..d.demandKwh)
                assertTrue(d.lowPrice <= d.averagePrice && d.averagePrice <= d.peakPrice)
                assertTrue(d.generationKwh.all { it >= 0L })
            }
            assertTrue(g.blackouts.count { it.open } <= 1)
        }
        for (p in state.ppas) assertTrue(p.deliveredKwh >= 0L && p.shortfallKwh >= 0L)
    }
}

/** A busy two-grid world with every kind of record in it, for save and hash tests. */
fun richWorld(days: Int = 45): GridWorld {
    val w = GridWorld(params = GridParams(operatorPlans = true))
    val seller = CompanyId(100)
    w.unit(Technology.COAL, 100_000L, owner = seller)
    w.unit(Technology.GAS, 80_000L, owner = seller)
    w.unit(Technology.WIND, 50_000L, owner = CompanyId(101))
    w.unit(Technology.SOLAR, 40_000L)
    w.unit(Technology.STORAGE, 20_000L, owner = CompanyId(101))
    w.households(50_000L)
    val factory = w.facility(30_000L)
    w.facility(10_000L, CustomerClass.INTERRUPTIBLE)
    val b = w.otherGrid()
    w.unit(Technology.GAS, 40_000L, grid = b)
    w.households(45_000L, grid = b)
    w.service.addLink(w.grid.id, b.id, 30_000L, 40_000L, app.many.economytycoon.core.Money.units(1)).getOrThrow()
    w.service.setHouseholdCap(b.id, app.many.economytycoon.core.Money.units(70))
    val ppa = w.service.offerPpa(
        w.grid.id, seller, w.buyer, PpaProfile.BASELOAD, 60_000L, 0L, null, app.many.economytycoon.core.Money.units(55), Tick(0), 200, 150_000L, Tick(0),
    ).getOrThrow()
    w.service.acceptPpa(ppa.id, w.buyer, Tick(0)).getOrThrow()
    check(factory.baseKw > 0L)
    w.days(days)
    return w
}
