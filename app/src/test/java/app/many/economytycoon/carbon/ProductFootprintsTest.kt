package app.many.economytycoon.carbon

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.world.WorldTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFootprintsTest {
    private val content = WorldTestSupport.content
    private val footprints = ProductFootprints(content, EmissionCalculator(StandardFactors.table, content))

    @Test fun stepsOfTheSteelChainAccumulateCarbon() {
        val iron = footprints.gramsPerUnit(GoodId("pig_iron"))
        val steel = footprints.gramsPerUnit(GoodId("steel"))
        val stainless = footprints.gramsPerUnit(GoodId("stainless_steel"))
        assertTrue(iron > 0L)
        assertTrue(steel > 0L)
        assertTrue(stainless > steel)
    }

    @Test fun aGoodNothingMakesCarriesNothing() {
        assertEquals(0L, footprints.gramsPerUnit(GoodId("no_such_good")))
    }

    @Test fun theSameQuestionGivesTheSameAnswer() {
        val a = footprints.gramsPerUnit(GoodId("cement"))
        val b = ProductFootprints(content, EmissionCalculator(StandardFactors.table, content)).gramsPerUnit(GoodId("cement"))
        assertEquals(a, b)
        assertEquals(a, footprints.gramsPerUnit(GoodId("cement")))
    }

    @Test fun embeddedMassScalesWithQuantity() {
        val one = footprints.embedded(GoodId("steel"), Quantity.units(1))
        val thousand = footprints.embedded(GoodId("steel"), Quantity.units(1_000))
        assertEquals(Tonnes(footprints.gramsPerUnit(GoodId("steel"))), thousand)
        assertTrue(one.isPositive)
    }

    @Test fun theRankingIsSortedFromTheDirtiestDown() {
        val ranking = footprints.ranking()
        assertTrue(ranking.isNotEmpty())
        assertEquals(ranking.map { it.second }.sortedDescending(), ranking.map { it.second })
        assertTrue(ranking.all { it.second > 0L })
        assertEquals(3, footprints.ranking(3).size)
    }

    @Test fun aRecipeBatchOfCementCarriesItsCalcination() {
        val calc = EmissionCalculator(StandardFactors.table, content)
        val recipe = content.recipeOrNull(app.many.economytycoon.core.RecipeId("make_cement"))!!
        val lines = calc.recipeBatch(recipe)
        assertTrue(lines.any { it.kind == SourceKind.PROCESS && it.amount == Tonnes.of(30) })
        assertTrue(lines.any { it.kind == SourceKind.FUEL && it.key == "coal" })
        assertTrue(calc.batchTotal(recipe) > Tonnes.of(30))
    }

    @Test fun everyGoodHasAFiniteFootprintSoNoRecipeLoopHangsTheWalk() {
        for (g in content.allGoods) assertTrue(footprints.gramsPerUnit(g.id) >= 0L)
    }
}
