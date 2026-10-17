package app.many.economytycoon.planner

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainPlannerTest {
    private val content = SimTestSupport.content
    private val planner = ChainPlanner(content)

    private fun finished() = content.allGoods.filter { planner.recipeFor(it.id) != null }

    @Test fun unknownGoodsAndEmptyRatesGiveNoPlan() {
        assertNull(planner.plan(GoodId("no_such_good"), Quantity.units(10)))
        val any = finished().first().id
        assertNull(planner.plan(any, Quantity(0L)))
    }

    @Test fun everyMakeableGoodPlansWithoutError() {
        for (g in finished()) {
            val p = planner.plan(g.id, Quantity.units(10))
            assertTrue("plan for ${g.id}", p != null)
            assertTrue(p!!.depth >= 1)
            assertTrue(p.totals.facilityCount >= 1)
        }
    }

    @Test fun buyingTheProductMakesTheRootALeaf() {
        val g = finished().first().id
        val p = planner.plan(g, Quantity.units(10), buy = setOf(g))!!
        assertEquals(Sourcing.BUY, p.root.sourcing)
        assertEquals(1, p.depth)
        assertEquals(0, p.totals.facilityCount)
    }

    @Test fun doublingTheRateAtLeastKeepsFacilityCount() {
        for (g in finished().take(20)) {
            val a = planner.plan(g.id, Quantity.units(10))!!
            val b = planner.plan(g.id, Quantity.units(20))!!
            assertTrue(b.totals.facilityCount >= a.totals.facilityCount)
            assertTrue(b.totals.capital.raw >= a.totals.capital.raw)
        }
    }

    @Test fun depthLimitStopsTheChain() {
        val deep = finished().map { planner.plan(it.id, Quantity.units(10))!! }.maxByOrNull { it.depth }!!
        if (deep.depth > 1) {
            val cut = planner.plan(deep.product, Quantity.units(10), maxDepth = 1)!!
            assertTrue(cut.depth <= 2)
            assertTrue(cut.goodsBought.isNotEmpty())
        }
    }

    @Test fun planningIsDeterministic() {
        for (g in finished().take(15)) {
            assertEquals(planner.plan(g.id, Quantity.units(7)), planner.plan(g.id, Quantity.units(7)))
        }
    }

    @Test fun refusingEveryRecipeBuysEverything() {
        val none = ChainPlanner(content) { false }
        val g = finished().first().id
        val p = none.plan(g, Quantity.units(5))!!
        assertEquals(Sourcing.BUY, p.root.sourcing)
        assertNull(none.recipeFor(g))
    }

    @Test fun totalsAddUp() {
        for (g in finished().take(20)) {
            val t = planner.plan(g.id, Quantity.units(10))!!.totals
            assertEquals(t.materialsPerDay.raw + t.labourPerDay.raw + t.energyPerDay.raw + t.upkeepPerDay.raw + t.overheadPerDay.raw, t.operatingPerDay.raw)
            assertEquals(t.revenuePerDay.raw - t.operatingPerDay.raw, t.profitPerDay.raw)
            val pb = t.paybackDays
            if (t.profitPerDay.raw <= 0L) assertNull(pb) else assertTrue(pb!! > 0L)
        }
    }

    @Test fun purchasesAreTheBoughtLeaves() {
        for (g in finished().take(20)) {
            val p = planner.plan(g.id, Quantity.units(10))!!
            val bought = p.goodsBought.map { it.good }.toSet()
            assertEquals(bought, p.totals.purchases.map { it.good }.toSet())
        }
    }

    @Test fun staffCountsAreNotNegative() {
        for (g in finished().take(20)) {
            val t = planner.plan(g.id, Quantity.units(10))!!.totals
            assertTrue(t.staff.all { it.second > 0L })
            assertEquals(t.staff.sumOf { it.second }, t.headcount)
        }
    }
}
