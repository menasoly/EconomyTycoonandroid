package app.many.economytycoon.ui.present

import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.GoodId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPresenterTest {
    private val session by lazy { PresentSupport.session("Market Works").also { s -> repeat(60) { s.step() } } }

    @Test fun theBoardListsTradableGoodsInNameOrder() {
        val b = MarketPresenter.board(session)
        assertTrue(b.rows.isNotEmpty())
        assertEquals(b.rows.map { it.name }.sorted(), b.rows.map { it.name })
        assertEquals(GoodCategory.values().size + 1, b.categories.size)
        assertNull(b.categories.first())
    }

    @Test fun categoryFiltersNarrowTheBoard() {
        val all = MarketPresenter.board(session).rows.size
        val raw = MarketPresenter.board(session, GoodCategory.RAW).rows
        assertTrue(raw.isNotEmpty() && raw.size < all)
        assertTrue(raw.all { it.category == "Raw" })
    }

    @Test fun searchMatchesNamesIgnoringCase() {
        val first = MarketPresenter.board(session).rows.first().name
        val hit = MarketPresenter.board(session, query = first.uppercase().take(4)).rows
        assertTrue(hit.any { it.name == first })
        assertTrue(MarketPresenter.board(session, query = "qqqqq").rows.isEmpty())
    }

    @Test fun everySortKeepsAllRows() {
        val n = MarketPresenter.board(session).rows.size
        for (s in MarketSort.values()) assertEquals(n, MarketPresenter.board(session, sort = s).rows.size)
    }

    @Test fun priceSortPutsTheDearestFirst() {
        val rows = session.reports.market.board().sortedByDescending { it.price.raw }.map { it.name }
        assertEquals(rows, MarketPresenter.board(session, sort = MarketSort.PRICE).rows.map { it.name })
    }

    @Test fun goodDetailHasRegionsAndHistory() {
        val good = session.reports.market.board().first().good
        val d = GoodDetailPresenter.build(session, good)!!
        assertTrue(d.regions.isNotEmpty())
        assertTrue(d.history.points.isNotEmpty())
        assertEquals(6, d.facts.size)
        val region = session.economy.world.regions().first().id
        assertNotNull(GoodDetailPresenter.depth(session, region, good))
    }

    @Test fun unknownGoodsHaveNoDetail() {
        assertNull(GoodDetailPresenter.build(session, GoodId("not_a_good")))
    }

    @Test fun aNewCompanyHasNoOrders() {
        assertTrue(OrdersPresenter.build(session).isEmpty())
    }

    @Test fun tonesFollowTheSizeOfMoves() {
        assertEquals(Tone.GOOD, MarketPresenter.changeTone(50_000)); assertEquals(Tone.BAD, MarketPresenter.changeTone(-50_000)); assertEquals(Tone.NEUTRAL, MarketPresenter.changeTone(100))
        assertEquals(Tone.WARN, MarketPresenter.deviationTone(300_000)); assertEquals(Tone.NEUTRAL, MarketPresenter.deviationTone(-40_000))
    }
}
