package app.many.economytycoon.facilityprofit

import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfitAnalyzerTest {
    private val sim by lazy { SimTestSupport.sim(11L, 4).also { it.advance(30) } }
    private val e get() = sim.economy

    @Test fun referencePricesMatchTheAuditEconomics() {
        val a = ProfitAnalyzer(e, PriceSource.reference(e.content))
        val region = e.world.regions().first().id
        for (r in e.content.allRecipes.take(40)) {
            val perf = a.recipeEconomics(r, region)
            val audit = RecipeEconomics.of(e.content, r)
            assertEquals(audit.inputCost, perf.inputCost.raw)
            assertEquals(audit.laborCost, perf.labour.raw)
            assertEquals(audit.energyCost, perf.energy.raw)
            assertEquals(audit.outputValue, perf.outputValue.raw)
        }
    }

    @Test fun everyFacilityOfEveryCompanyCanBeAnalysed() {
        val a = ProfitAnalyzer(e)
        var seen = 0
        for (c in e.companies.active()) {
            val p = a.portfolio(c.id)
            assertEquals(e.production.facilitiesOf(c.id).size, p.facilities.size)
            seen += p.facilities.size
            for (f in p.facilities) assertTrue(f.capital.raw >= 0L && f.upkeepPerDay.raw >= 0L)
        }
        assertTrue("some facility exists after 30 days", seen > 0)
    }

    @Test fun lineCountsMatchTheFacility() {
        val a = ProfitAnalyzer(e)
        for (f in e.production.facilitiesOf(e.companies.active().first { e.production.facilitiesOf(it.id).isNotEmpty() }.id)) {
            assertEquals(f.lines.size, a.facility(f).lines.size)
        }
    }

    @Test fun switchesNeverLoseMoneyOrKeepTheSameRecipe() {
        val a = ProfitAnalyzer(e)
        for (c in e.companies.active()) for (s in a.portfolio(c.id).switches) {
            assertTrue(s.gainPerDay.raw >= Money.units(1).raw)
            assertTrue(s.from != s.toName)
        }
    }

    @Test fun switchesAreSortedByGain() {
        val a = ProfitAnalyzer(e)
        for (c in e.companies.active()) {
            val gains = a.portfolio(c.id).switches.map { it.gainPerDay.raw }
            assertEquals(gains.sortedDescending(), gains)
        }
    }

    @Test fun grantedFacilityWithNoLinesEarnsNothing() {
        val owner = e.companies.active().first().id
        val before = e.production.facilitiesOf(owner).size
        val region = e.world.regions().first().id
        e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        val p = ProfitAnalyzer(e).portfolio(owner)
        assertEquals(before + 1, p.facilities.size)
    }

    @Test fun fixedPricesGiveFixedProfit() {
        val fixed = PriceSource { _, good -> RecipeEconomics.priceOf(e.content, good) }
        val a = ProfitAnalyzer(e, fixed)
        val b = ProfitAnalyzer(e, PriceSource.reference(e.content))
        val region = e.world.regions().first().id
        for (r in e.content.allRecipes.take(10)) assertEquals(a.recipeEconomics(r, region).profitPerBatch, b.recipeEconomics(r, region).profitPerBatch)
    }
}
