package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.Channel
import app.many.economytycoon.marketing.MarketingParams
import app.many.economytycoon.marketing.MarketingTestSupport
import app.many.economytycoon.marketing.ModelledLift
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelComparisonPresenterTest {
    private val money: (Money) -> String = { Formatting.money(it) }
    private val table = ModelledLift.compareChannels(MarketingTestSupport.context(revenue = 60_000), Money.units(1_500), MarketingParams())
    private val model = ChannelComparisonPresenter.build("Beer in Region 1", table, money)

    @Test fun everyChannelGetsARow() {
        assertEquals(Channel.values().size, model.rows.size)
        assertEquals(Channel.values().map { it.label }.toSet(), model.rows.map { it.label }.toSet())
        assertEquals("Beer in Region 1", model.title)
    }

    @Test fun rowsAreRankedByReturn() {
        val returns = model.rows.map { r -> table.first { it.channel.label == r.label }.value.returnPpm }
        assertEquals(returns.sortedDescending(), returns)
    }

    @Test fun exactlyTheTopRowIsMarkedBest() {
        assertTrue(model.rows.first().best)
        assertEquals(1, model.rows.count { it.best })
    }

    @Test fun eachRowShowsTheSameFacts() {
        val labels = model.rows.first().facts.map { it.label }
        for (r in model.rows) assertEquals(labels, r.facts.map { it.label })
        assertTrue(labels.contains("Reach") && labels.contains("Return"))
    }

    @Test fun verdictsFollowTheReturns() {
        assertEquals("Brings in nothing at this size", ChannelComparisonPresenter.verdict(0L, 0L))
        assertTrue(ChannelComparisonPresenter.verdict(3_000_000L, 2_000_000L).startsWith("Strong"))
        assertEquals("Pays for itself", ChannelComparisonPresenter.verdict(2_000_000L, 1_200_000L))
        assertTrue(ChannelComparisonPresenter.verdict(1_500_000L, 400_000L).contains("saturated"))
        assertTrue(ChannelComparisonPresenter.verdict(400_000L, 300_000L).contains("more than it brings"))
    }

    @Test fun tonesFollowTheReturns() {
        assertEquals(Tone.MUTED, ChannelComparisonPresenter.tone(0L, 0L))
        assertEquals(Tone.GOOD, ChannelComparisonPresenter.tone(2_000_000L, 1_100_000L))
        assertEquals(Tone.WARN, ChannelComparisonPresenter.tone(1_500_000L, 500_000L))
        assertEquals(Tone.BAD, ChannelComparisonPresenter.tone(500_000L, 400_000L))
    }

    @Test fun anEmptyTableSaysSo() {
        val m = ChannelComparisonPresenter.build("x", emptyList(), money)
        assertTrue(m.rows.isEmpty())
        assertEquals("Nothing to compare.", m.note)
    }

    @Test fun aHopelessMarketHasNoBestChannel() {
        val none = ModelledLift.compareChannels(MarketingTestSupport.context(revenue = 0), Money.units(500), MarketingParams())
        val m = ChannelComparisonPresenter.build("x", none, money)
        assertTrue(m.rows.none { it.best })
    }
}
