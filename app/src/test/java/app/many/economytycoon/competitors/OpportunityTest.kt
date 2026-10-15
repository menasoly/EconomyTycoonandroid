package app.many.economytycoon.competitors

import app.many.economytycoon.competitors.CompetitorTestSupport.bread
import app.many.economytycoon.competitors.CompetitorTestSupport.content
import app.many.economytycoon.competitors.CompetitorTestSupport.region
import app.many.economytycoon.competitors.CompetitorTestSupport.region2
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityTest {
    private val market = CompetitorTestSupport.Outlook()
    private val scanner = OpportunityScanner(content, market)
    private val flour = content.recipe(RecipeId("make_flour"))
    private val breadRecipe = content.recipe(RecipeId("make_bread"))

    @Test fun batchMarginIsRevenueLessInputsLaborAndEnergy() {
        assertEquals(Money.units(15), scanner.batchMargin(flour, region))
        assertEquals(Money.units(105), scanner.batchMargin(breadRecipe, region))
    }

    @Test fun marginFollowsMarketPrices() {
        market.multiplier[bread] = 500_000L
        assertEquals(Money.units(25), scanner.batchMargin(breadRecipe, region))
        market.multiplier[bread] = 200_000L
        assertTrue(scanner.batchMargin(breadRecipe, region).raw < 0)
        market.multiplier.clear()
    }

    @Test fun evaluationDerivesProfitPerTickAndPayback() {
        val o = scanner.evaluate(breadRecipe, region)
        assertEquals(Money.units(103), o.profitPerTick)
        assertEquals(Money.units(50_000), o.capital)
        assertEquals(486L, o.paybackTicks)
        assertTrue(o.profitable)
    }

    @Test fun scanRanksByPaybackAndDropsLosers() {
        val all = scanner.scan(listOf(region, region2), { true }, setOf(CompetitorTestSupport.bakery))
        assertEquals(RecipeId("make_bread"), all.first().recipe.id)
        assertTrue(all.zipWithNext().all { (a, b) -> a.paybackTicks <= b.paybackTicks + 100 })
        market.multiplier[bread] = 100_000L
        val none = scanner.scan(listOf(region), { it.id == RecipeId("make_bread") }, setOf(CompetitorTestSupport.bakery))
        assertTrue(none.isEmpty())
        market.multiplier.clear()
    }

    @Test fun scanRespectsAllowedRecipesAndTypes() {
        val onlyFlour = scanner.scan(listOf(region), { it.id == RecipeId("make_flour") }, setOf(CompetitorTestSupport.bakery))
        assertTrue(onlyFlour.all { it.recipe.id == RecipeId("make_flour") })
        assertTrue(scanner.scan(listOf(region), { true }, emptySet()).isEmpty())
    }

    @Test fun scarcityBreaksTiesInFavourOfShortGoods() {
        market.scarcity[bread] = 3_000_000L
        val o = scanner.scan(listOf(region), { true }, setOf(CompetitorTestSupport.bakery))
        assertEquals(RecipeId("make_bread"), o.first().recipe.id)
        market.scarcity.clear()
    }

    @Test fun bestRecipePicksTheMostProfitablePerTick() {
        assertEquals(RecipeId("make_bread"), scanner.bestRecipe(CompetitorTestSupport.bakery, region) { true }!!.id)
        assertEquals(RecipeId("make_flour"), scanner.bestRecipe(CompetitorTestSupport.bakery, region) { it.id == RecipeId("make_flour") }!!.id)
        assertNull(scanner.bestRecipe(CompetitorTestSupport.bakery, region) { false })
        assertNotNull(scanner.bestRecipe(CompetitorTestSupport.bakery, region2) { true })
    }
}
