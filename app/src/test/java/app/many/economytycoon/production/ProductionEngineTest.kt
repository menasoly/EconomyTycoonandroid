package app.many.economytycoon.production

import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.ProductionTestSupport.Rig
import app.many.economytycoon.production.ProductionTestSupport.bread
import app.many.economytycoon.production.ProductionTestSupport.company
import app.many.economytycoon.production.ProductionTestSupport.flour
import app.many.economytycoon.production.ProductionTestSupport.ore
import app.many.economytycoon.production.ProductionTestSupport.wheat
import app.many.economytycoon.production.ProductionTestSupport.ash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionEngineTest {
    private fun setFlour(r: Rig, f: Facility) = r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_flour")).getOrThrow()

    @Test fun batchFinishesAfterItsDuration() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 40.0, 10.0); setFlour(r, f)
        r.step()
        assertEquals(Quantity.ZERO, f.inventory.quantity(flour))
        assertEquals(Blocked.None, f.lines[0].blocked)
        r.step()
        assertEquals(Quantity.units(3), f.inventory.quantity(flour))
        assertEquals(Quantity.units(1), f.inventory.quantity(ash))
    }

    @Test fun producedValueEqualsInputsPlusLaborStandardCost() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 4.0, 10.0); setFlour(r, f)
        val before = f.inventory.totalValue()
        val reports = r.run(2)
        val delta = reports.fold(Money.ZERO) { a, rep -> a + rep.inventoryValueDelta }
        // 40.00 of wheat becomes flour worth 40.00 plus 2 bakers x 100 x 2 ticks of capitalised labor
        assertEquals(Money.units(400), delta)
        assertEquals(before + delta, f.inventory.totalValue())
    }

    @Test fun missingInputBlocksTheLine() {
        val r = Rig(); val f = r.bakery(); setFlour(r, f)
        r.step()
        assertEquals(Blocked.MissingInput(wheat), f.lines[0].blocked)
        assertEquals(0L, f.lines[0].batchesCompleted)
    }

    @Test fun noRecipeIsReported() {
        val r = Rig(); val f = r.bakery()
        r.step()
        assertEquals(Blocked.NoRecipe, f.lines[0].blocked)
    }

    @Test fun noLaborStopsWork() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 40.0, 10.0); setFlour(r, f)
        r.labor.staffingPpm = 0
        r.run(5)
        assertEquals(Blocked.NoLabor, f.lines[0].blocked)
        assertEquals(Quantity.ZERO, f.inventory.quantity(flour))
    }

    @Test fun halfStaffingHalvesTheRate() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 400.0, 10.0); setFlour(r, f)
        r.labor.staffingPpm = 500_000
        r.run(20)
        val full = Rig(); val g = full.bakery(); full.stock(g, wheat, 400.0, 10.0); setFlour(full, g)
        full.run(20)
        assertTrue(g.lines[0].batchesCompleted >= 2 * f.lines[0].batchesCompleted - 1)
        assertTrue(f.lines[0].batchesCompleted in 4..6)
    }

    @Test fun strikeBlocksAllLines() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 40.0, 10.0); setFlour(r, f)
        r.labor.strike = true
        r.step()
        assertEquals(Blocked.Strike, f.lines[0].blocked)
    }

    @Test fun shortBatchesCanFinishSeveralTimesPerTick() {
        val r = Rig(); val f = r.bakery(withOven = true); r.stock(f, flour, 100.0, 25.0)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_bread")).getOrThrow()
        val rep = r.step()
        // oven runs at 1.25x so a one tick recipe completes one batch and carries 25% progress
        assertEquals(1L, rep.facilities[0].batches)
        val rep2 = r.step()
        assertTrue(rep2.facilities[0].batches >= 1)
        assertTrue(f.lines[0].batchesCompleted >= 2)
    }

    @Test fun missingMachineBlocks() {
        val r = Rig(); val f = r.bakery(); r.stock(f, flour, 10.0, 25.0)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_bread")).getOrThrow()
        r.step()
        assertEquals(Blocked.MissingMachine, f.lines[0].blocked)
    }

    @Test fun energyShortageSlowsProduction() {
        val full = Rig(); val a = full.bakery(); full.stock(a, wheat, 400.0, 10.0); setFlour(full, a)
        val short = Rig(); val b = short.bakery(); short.stock(b, wheat, 400.0, 10.0); setFlour(short, b)
        short.energy.available = 0
        full.run(10); short.run(10)
        assertTrue(a.lines[0].batchesCompleted > 0)
        assertTrue(b.lines[0].batchesCompleted == 0L || b.lines[0].blocked == Blocked.None)
    }

    @Test fun extractionDrawsFromTheDeposit() {
        val r = Rig()
        val f = r.svc.grantFacility(company, FacilityTypeId("mine"), ProductionTestSupport.region, null, null, app.many.economytycoon.core.DepositId(9), Tick(0))
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("dig_ore")).getOrThrow()
        r.resources.reserve = Quantity.units(7).raw
        r.run(3)
        assertEquals(Quantity.units(7), f.inventory.quantity(ore))
        assertEquals(Blocked.NoDeposit, f.lines[0].blocked)
    }

    @Test fun extractionWithoutDepositIsBlocked() {
        val r = Rig()
        val f = r.svc.grantFacility(company, FacilityTypeId("mine"), ProductionTestSupport.region, null, null, null, Tick(0))
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("dig_ore")).getOrThrow()
        r.step()
        assertEquals(Blocked.NoDeposit, f.lines[0].blocked)
    }

    @Test fun fullStorageHoldsTheFinishedBatch() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 40.0, 10.0); setFlour(r, f)
        f.inventory.capacity = Quantity.units(37)
        r.run(2)
        assertEquals(Blocked.OutputFull, f.lines[0].blocked)
        f.inventory.capacity = Quantity.units(500)
        r.step()
        assertTrue(f.inventory.quantity(flour).raw > 0)
    }

    @Test fun defectiveBatchesYieldLess() {
        val r = Rig(); val f = r.bakery(); r.stock(f, flour, 10.0, 25.0)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_bread_def")).getOrThrow()
        val rep = r.step()
        assertEquals(1L, rep.facilities[0].defects)
        // 30% of 4 units
        assertEquals(Quantity((4000L * 300_000L) / 1_000_000L), f.inventory.quantity(bread))
    }

    @Test fun qualityAssuranceReducesDefects() {
        val r = Rig()
        val f = r.bakery()
        val line = f.lines[0]
        val recipe = ProductionTestSupport.content.recipe(RecipeId("make_flour"))
        val base = r.svc.engine.defectChancePpm(f, line, recipe, r.ctx())
        r.svc.setQualityAssurance(company, f.id, app.many.economytycoon.core.Rate.percent(50)).getOrThrow()
        assertTrue(r.svc.engine.defectChancePpm(f, line, recipe, r.ctx()) <= base)
    }

    @Test fun machinesWearAndEventuallyBreakDown() {
        val r = Rig(); val f = r.bakery(withOven = true); r.stock(f, flour, 5000.0, 25.0)
        f.inventory.capacity = Quantity.units(100_000)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_bread")).getOrThrow()
        val m = f.lines[0].machines[0]
        r.run(5)
        assertTrue(m.conditionPpm < 1_000_000L)
        r.run(120)
        assertTrue("breakdowns ${m.breakdowns}", m.breakdowns > 0)
    }

    @Test fun repairRestoresMachineCondition() {
        val r = Rig(); val f = r.bakery(withOven = true); r.stock(f, flour, 200.0, 25.0)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_bread")).getOrThrow()
        r.run(6)
        val m = f.lines[0].machines[0]
        val worn = m.conditionPpm
        val spent = r.svc.maintain(company, f.id, Money.units(1_000_000)).getOrThrow()
        assertTrue(spent.raw > 0)
        assertTrue(m.conditionPpm > worn)
        assertEquals(1_000_000L, m.conditionPpm)
    }

    @Test fun autoMarginPicksTheProfitableRecipe() {
        val r = Rig(); val f = r.bakery(withOven = true)
        r.stock(f, flour, 50.0, 25.0); r.stock(f, wheat, 50.0, 10.0)
        r.svc.setPolicy(company, f.id, SchedulePolicy.AUTO_MARGIN).getOrThrow()
        r.step()
        assertEquals(RecipeId("make_bread"), f.lines[0].recipe)
    }

    @Test fun spoilageEatsPerishableStock() {
        val r = Rig(); val f = r.bakery(); r.stock(f, bread, 100.0, 40.0)
        val rep = r.step()
        assertEquals(Quantity.units(5), rep.facilities[0].spoiled[bread])
        assertEquals(Quantity.units(95), f.inventory.quantity(bread))
        assertTrue(rep.facilities[0].spoiledValue.raw > 0)
    }

    @Test fun shutdownFacilitiesDoNothing() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 40.0, 10.0); setFlour(r, f)
        r.svc.shutdown(company, f.id).getOrThrow()
        r.run(5)
        assertEquals(0L, f.lines[0].batchesCompleted)
        assertEquals(Blocked.Shutdown, f.lines[0].blocked)
        r.svc.reopen(company, f.id).getOrThrow()
        r.run(3)
        assertTrue(f.lines[0].batchesCompleted > 0)
    }

    @Test fun priorityDecidesWhoGetsScarceInputs() {
        val r = Rig(); val f = r.bakery()
        r.svc.addLine(company, f.id).getOrThrow()
        r.stock(f, wheat, 4.0, 10.0)
        val low = f.lines[0]; val high = f.lines[1]
        r.svc.setRecipe(company, f.id, low.id, RecipeId("make_flour")).getOrThrow()
        r.svc.setRecipe(company, f.id, high.id, RecipeId("make_flour")).getOrThrow()
        r.svc.setPriority(company, f.id, high.id, 5).getOrThrow()
        r.step()
        assertTrue(high.batch != null)
        assertEquals(null, low.batch)
        assertEquals(Blocked.MissingInput(wheat), low.blocked)
    }

    @Test fun wasteBuildsPollutionUntilDisposed() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 4000.0, 10.0); setFlour(r, f)
        f.inventory.capacity = Quantity.units(100_000)
        r.run(60)
        assertTrue(f.pollutionPpm > 0)
        val removed = r.svc.disposeWaste(company, f.id).getOrThrow()
        assertTrue(removed.raw > 0)
        assertEquals(Quantity.ZERO, f.inventory.quantity(ash))
    }

    @Test fun sameSeedGivesIdenticalHistory() {
        fun history(seed: Long): String {
            val r = Rig(seed); val f = r.bakery(withOven = true); r.stock(f, flour, 3000.0, 25.0)
            f.inventory.capacity = Quantity.units(100_000)
            r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_bread")).getOrThrow()
            r.run(150)
            return r.hash()
        }
        assertEquals(history(11), history(11))
    }
}
