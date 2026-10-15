package app.many.economytycoon.content.audit

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEconomicsTest {
    private val c = AuditFixture.content
    private fun econ(id: String) = RecipeEconomics.of(c, c.recipe(RecipeId(id)))

    @Test fun aBatchIsCostedFromInputsLaborAndEnergy() {
        val e = econ("smelt")
        assertEquals(Money.units(20).raw, e.inputCost)
        assertEquals(Money.units(10).raw, e.laborCost)
        assertEquals(0L, e.energyCost)
        assertEquals(Money.units(40).raw, e.outputValue)
        assertEquals(Money.units(10).raw, e.profitPerBatch)
    }

    @Test fun marginIsProfitOverCost() {
        assertEquals(333_333L, econ("smelt").marginPpm)
        assertEquals(-950_000L, econ("press_junk").marginPpm)
    }

    @Test fun laborCostScalesWithDuration() {
        assertEquals(Money.units(20).raw, econ("dig_ore").laborCost)
    }

    @Test fun profitPerLineDayDividesByDuration() {
        val e = econ("dig_ore")
        assertEquals(Money.units(80).raw, e.profitPerLineDay)
    }

    @Test fun materialShareIsInputsOverCost() {
        assertEquals(666_666L, econ("smelt").materialSharePpm)
        assertEquals(0L, econ("dig_ore").materialSharePpm)
    }

    @Test fun aFreeBatchWithOutputHasTheLargestMargin() {
        val free = RecipeEconomy(c.recipe(RecipeId("dig_ore")), 0, 0, 0, 5)
        assertEquals(RecipeEconomy.MAX_MARGIN_PPM, free.marginPpm)
        val nothing = RecipeEconomy(c.recipe(RecipeId("dig_ore")), 0, 0, 0, 0)
        assertEquals(0L, nothing.marginPpm)
    }

    @Test fun marginIsCappedBothWays() {
        val huge = RecipeEconomy(c.recipe(RecipeId("dig_ore")), 1, 0, 0, 1_000_000_000)
        assertEquals(RecipeEconomy.MAX_MARGIN_PPM, huge.marginPpm)
        val ruin = RecipeEconomy(c.recipe(RecipeId("dig_ore")), 1_000_000, 0, 0, 0)
        assertEquals(-1_000_000L, ruin.marginPpm)
    }

    @Test fun allRecipesAreCovered() {
        assertEquals(c.allRecipes.size, RecipeEconomics.all(c).size)
    }

    @Test fun unknownGoodsCostNothing() {
        assertEquals(Money.ZERO, RecipeEconomics.priceOf(c, app.many.economytycoon.core.GoodId("nonexistent")))
    }

    @Test fun aCatalystIsNotCountedAsCost() {
        val shipped = app.many.economytycoon.simulation.SimTestSupport.content
        val seeds = shipped.allRecipes.firstOrNull { it.catalytic }
        if (seeds != null) {
            val withCatalyst = RecipeEconomics.of(shipped, seeds)
            val naive = seeds.inputs.sumOf { RecipeEconomics.valueOf(shipped, it) }
            assertTrue(withCatalyst.inputCost < naive)
        }
    }
}
