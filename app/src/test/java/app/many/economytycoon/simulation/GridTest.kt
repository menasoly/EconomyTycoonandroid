package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {
    private val power = GoodId("electricity")
    private val region = RegionId(1)

    private fun grid(baselinePeople: Long = 100_000): Grid {
        val g = Grid(power, Money.units(10))
        g.setBaseline(region, baselinePeople)
        g.beginTick()
        return g
    }

    private fun facility(owner: Long = 1): app.many.economytycoon.production.Facility {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        return sim.economy.production.grantFacility(CompanyId(owner), FacilityTypeId("grain_farm"), sim.economy.world.regions().first().id, null, null, null, sim.economy.tick)
    }

    @Test fun priceRisesWithLoad() {
        val f = facility()
        val g = Grid(power, Money.units(10))
        g.setBaseline(f.region, 100_000)
        g.beginTick()
        val idle = g.price(f.region)
        g.draw(f, Quantity((g.capacity(f.region) * 9) / 10))
        assertTrue(g.price(f.region) > idle)
        assertTrue(g.loadPpm(f.region) in 800_000L..1_000_000L)
    }

    @Test fun drawIsLimitedByRemainingCapacityAndBilled() {
        val g = Grid(power, Money.units(10))
        val f = facility()
        g.setBaseline(f.region, 1_000)
        g.beginTick()
        val cap = g.capacity(f.region)
        val got = g.draw(f, Quantity(cap * 2))
        assertEquals(cap, got.raw)
        assertEquals(Quantity.ZERO, g.draw(f, Quantity.units(1)))
        assertTrue(g.bills()[f.owner]!!.raw > 0)
        g.clearBills()
        assertTrue(g.bills().isEmpty())
    }

    @Test fun plantOutputFeedsTheNextTicksCapacity() {
        val g = grid()
        val before = g.capacity(region)
        g.recordProduction(region, CompanyId(5), Quantity.units(500))
        g.beginTick()
        assertEquals(before + 500_000L, g.capacity(region))
    }

    @Test fun collectedMoneyIsSharedBetweenPlantsAndTheUtility() {
        val g = grid(10_000)
        g.recordProduction(region, CompanyId(5), Quantity.units(500))
        g.beginTick()
        val parts = g.payees(region, Money.units(1_000))
        assertEquals(Money.units(1_000), Money(parts.sumOf { it.second.raw }))
        assertTrue(parts.any { it.first == CompanyId(5) })
        assertTrue(parts.any { it.first == null })
    }

    @Test fun withoutAnyCapacityTheUtilityTakesEverything() {
        val g = Grid(power, Money.units(10))
        val parts = g.payees(region, Money.units(10))
        assertEquals(listOf<Pair<CompanyId?, Money>>(null to Money.units(10)), parts)
    }

    @Test fun savedProductionRoundTripsThroughRestore() {
        val g = grid()
        g.recordProduction(region, CompanyId(3), Quantity.units(40))
        val saved = g.pendingProduction()
        val h = grid()
        h.restoreProduction(saved)
        h.beginTick()
        g.beginTick()
        assertEquals(g.capacity(region), h.capacity(region))
    }
}
