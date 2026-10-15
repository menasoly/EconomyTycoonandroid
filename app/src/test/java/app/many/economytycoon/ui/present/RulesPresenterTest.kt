package app.many.economytycoon.ui.present

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesPresenterTest {
    private fun withFacility() = PresentSupport.session("Rule Works", 5L).also { s ->
        val c = s.selectedCompany!!
        val region = s.economy.world.regions().first().id
        val option = s.reports.planner.options(c, region).filter { it.canBuild && !it.needsDeposit }.minByOrNull { it.cost.raw }!!
        s.issueNow(s.reports.planner.buildCommand(c, option, region, "Rule plant")!!)
        repeat(120) { s.step() }
    }

    @Test fun noRulesAtFirst() {
        val m = RulesPresenter.build(PresentSupport.session())!!
        assertTrue(m.sells.isEmpty() && m.buys.isEmpty())
    }

    @Test fun rulesAreListedWithReadableSummaries() {
        val s = withFacility()
        val c = s.selectedCompany!!
        val f = s.economy.production.facilitiesOf(c).single()
        val good = s.economy.content.allGoods.first { it.tradable }.id
        assertTrue(s.issueNow(Command.SetSellRule(c, SellRule(f.id, good, PriceStrategy.UNDERCUT, 50_000, Quantity.units(5), Quantity.units(20), 100_000, true))).isOk)
        assertTrue(s.issueNow(Command.SetBuyRule(c, BuyRule(f.id, good, Quantity.units(30), 1_200_000, false))).isOk)
        val m = RulesPresenter.build(s)!!
        val sell = m.sells.single()
        assertTrue(sell.title.startsWith("Sell ") && sell.title.endsWith("from Rule plant"))
        assertTrue(sell.summary.contains("Undercut by 5.0%"))
        assertTrue(sell.summary.contains("keep 5") && sell.summary.contains("at most 20 per order") && sell.summary.contains("margin at least 10.0%"))
        val buy = m.buys.single()
        assertEquals("Keep 30 in stock · pay up to 120.0% of market", buy.summary.substringAfter(" · ").let { "Keep 30 in stock · $it" })
        assertFalse(buy.enabled)
        assertTrue(sell.enabled)
    }

    @Test fun strategyLabelsAreDistinct() {
        assertEquals(PriceStrategy.values().size, PriceStrategy.values().map { RulesPresenter.strategyLabel(it) }.toSet().size)
        assertTrue(GoodId("x").key == "x")
    }
}
