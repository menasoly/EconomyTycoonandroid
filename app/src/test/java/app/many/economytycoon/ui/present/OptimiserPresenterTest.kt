package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.BudgetOptimiser
import app.many.economytycoon.marketing.MarketingParams
import app.many.economytycoon.marketing.MarketingTestSupport
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.south
import app.many.economytycoon.marketing.OptimiserConstraints
import app.many.economytycoon.marketing.PlainNames
import app.many.economytycoon.marketing.StopReason
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimiserPresenterTest {
    private val money: (Money) -> String = { Formatting.money(it) }
    private val contexts = listOf(
        MarketingTestSupport.context(beer, north, 500_000L, 60_000),
        MarketingTestSupport.context(bread, south, 200_000L, 40_000, income = 1_300_000L),
    )
    private val result = BudgetOptimiser(MarketingParams()).optimise(contexts, OptimiserConstraints(Money.units(6_000), step = Money.units(100)))
    private val model = OptimiserPresenter.build(result, PlainNames, money)

    @Test fun tilesSummariseTheBudget() {
        assertEquals(listOf("Budget", "Planned spend", "Left unspent", "Extra profit a day", "Return"), model.tiles.map { it.label })
        assertEquals(money(Money.units(6_000)), model.tiles[0].value)
        assertEquals(money(result.spend), model.tiles[1].value)
    }

    @Test fun oneRowPerMarketWithItsAllocations() {
        assertTrue(model.markets.isNotEmpty())
        assertEquals(result.allocations.map { it.market }.distinct().size, model.markets.size)
        assertEquals(result.allocations.size, model.markets.sumOf { it.allocations.size })
        assertTrue(model.markets.first().title.contains(" in Region "))
    }

    @Test fun channelTotalsMatchTheResult() {
        assertEquals(result.byChannel().size, model.byChannel.size)
        assertEquals(result.byChannel().keys.map { it.label }, model.byChannel.map { it.label })
    }

    @Test fun aPlanCanBeApplied() {
        assertTrue(model.canApply)
    }

    @Test fun anEmptyPlanCannotBeApplied() {
        val empty = BudgetOptimiser(MarketingParams()).optimise(emptyList(), OptimiserConstraints(Money.units(100)))
        val m = OptimiserPresenter.build(empty, PlainNames, money)
        assertFalse(m.canApply)
        assertTrue(m.markets.isEmpty())
        assertEquals(StopReason.NO_OPTIONS.label, m.stop)
        assertEquals(Tone.MUTED, m.tone)
    }

    @Test fun stopReasonsHaveTones() {
        assertEquals(Tone.GOOD, OptimiserPresenter.stopTone(StopReason.BUDGET_USED))
        assertEquals(Tone.INFO, OptimiserPresenter.stopTone(StopReason.DIMINISHING))
        assertEquals(Tone.WARN, OptimiserPresenter.stopTone(StopReason.CONSTRAINED))
        assertEquals(Tone.MUTED, OptimiserPresenter.stopTone(StopReason.NO_OPTIONS))
    }

    @Test fun leftoverMoneyIsHighlighted() {
        val big = BudgetOptimiser(MarketingParams()).optimise(contexts, OptimiserConstraints(Money.units(900_000), step = Money.units(1_000)))
        val m = OptimiserPresenter.build(big, PlainNames, money)
        assertEquals(Tone.INFO, m.tiles[2].tone)
        assertEquals(Tone.NEUTRAL, OptimiserPresenter.build(result.copy(budget = result.spend), PlainNames, money).tiles[2].tone)
    }

    @Test fun profitableMarketsAreGreen() {
        assertTrue(model.markets.all { it.tone == Tone.GOOD })
        assertEquals(Tone.GOOD, model.tiles[3].tone)
    }
}
