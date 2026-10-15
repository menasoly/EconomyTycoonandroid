package app.many.economytycoon.ui.present

import app.many.economytycoon.pricestats.PriceMath
import app.many.economytycoon.pricestats.RangeZone
import app.many.economytycoon.pricestats.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceStatsPresenterTest {
    private val session by lazy { PresentSupport.session("Price Works", 6L).also { s -> repeat(50) { s.step() } } }

    @Test fun wordsCoverEveryCase() {
        assertEquals("rising", PriceStatsPresenter.trendWord(Trend.RISING))
        assertEquals("falling", PriceStatsPresenter.trendWord(Trend.FALLING))
        assertEquals("steady", PriceStatsPresenter.trendWord(Trend.FLAT))
        for (z in RangeZone.values()) assertTrue(PriceStatsPresenter.zoneWord(z).isNotEmpty())
    }

    @Test fun volatilityBands() {
        assertEquals("very calm", PriceStatsPresenter.volatilityWord(5_000))
        assertEquals("calm", PriceStatsPresenter.volatilityWord(20_000))
        assertEquals("lively", PriceStatsPresenter.volatilityWord(50_000))
        assertEquals("wild", PriceStatsPresenter.volatilityWord(200_000))
    }

    @Test fun strengthBands() {
        assertEquals("mostly climbing", PriceStatsPresenter.strengthWord(80))
        assertEquals("mostly sliding", PriceStatsPresenter.strengthWord(10))
        assertEquals("balanced", PriceStatsPresenter.strengthWord(50))
    }

    @Test fun headlineMentionsTrendAndRange() {
        val st = PriceMath.stats(longArrayOf(10, 20, 30, 40, 50))!!
        val h = PriceStatsPresenter.headline(st)
        assertTrue(h, h.contains("rising") && h.contains("top"))
    }

    @Test fun notesFlagDeepFallsAndStrongRuns() {
        val closes = longArrayOf(1000, 900, 800, 700, 600, 500)
        val st = PriceMath.stats(closes)!!
        val notes = PriceStatsPresenter.notes(st, closes)
        assertTrue(notes.toString(), notes.any { it.contains("from its peak") })
        assertTrue(notes.toString(), notes.any { it.contains("holding stock back") })
        assertTrue(notes.toString(), notes.any { it.contains("sliding") })
    }

    @Test fun calmSeriesHasNoNotes() {
        val closes = LongArray(12) { 1000L + (it % 2) }
        val st = PriceMath.stats(closes)!!
        assertEquals(emptyList<String>(), PriceStatsPresenter.notes(st, closes))
    }

    @Test fun aLiveGoodHasFacts() {
        val good = session.reports.market.board().first { session.reports.market.averageHistory(it.good).size >= PriceStatsPresenter.MIN_DAYS }.good
        val m = PriceStatsPresenter.build(session, good)
        assertTrue("model", m != null)
        val labels = m!!.facts.map { it.label }
        assertTrue(labels.toString(), "Lowest" in labels && "Highest" in labels && "Worst fall" in labels)
        assertTrue(m.headline.startsWith("Price is"))
    }

    @Test fun unknownGoodsHaveNoModel() {
        assertNull(PriceStatsPresenter.build(session, app.many.economytycoon.core.GoodId("no_such_good")))
    }

    @Test fun moversAreSortedByMovement() {
        val rows = PriceStatsPresenter.movers(session, 20)
        assertTrue(rows.isNotEmpty())
        val sizes = rows.map { kotlin.math.abs(it.change.trimEnd('%').replace("+", "").toDouble()) }
        assertEquals(sizes.sortedDescending(), sizes)
    }

    @Test fun moversRespectTheLimit() {
        assertTrue(PriceStatsPresenter.movers(session, 3).size <= 3)
    }
}
