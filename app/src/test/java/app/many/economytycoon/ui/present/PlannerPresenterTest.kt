package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerPresenterTest {
    private val session by lazy { PresentSupport.session("Planner Works", 5L) }
    private val goods by lazy { session.plannableGoods() }

    @Test fun plannableGoodsAreSortedByName() {
        val names = goods.map { it.name }
        assertTrue(names.isNotEmpty())
        assertEquals(names.sorted(), names)
    }

    @Test fun noPlanGivesAnEmptyModelWithAPrompt() {
        val m = PlannerPresenter.build(session, null)
        assertTrue(m.tiles.isEmpty() && m.tree.isEmpty())
        assertEquals(PlannerPresenter.NOTHING, m.verdict)
    }

    @Test fun aPlanFillsEverySection() {
        val p = PlannerPresenter.plan(session, goods.first().id, 10L, emptySet())!!
        val m = PlannerPresenter.build(session, p)
        assertEquals(4, m.tiles.size)
        assertEquals(6, m.costs.size)
        assertEquals(p.root.walk().size, m.tree.size)
        assertEquals(0, m.tree.first().depth)
        assertTrue(m.buildings.size == p.totals.facilities.size)
    }

    @Test fun treeDepthsNeverJumpByMoreThanOne() {
        for (g in goods.take(25)) {
            val m = PlannerPresenter.build(session, PlannerPresenter.plan(session, g.id, 10L, emptySet()))
            m.tree.zipWithNext().forEach { (a, b) -> assertTrue(b.depth <= a.depth + 1) }
        }
    }

    @Test fun buyingTheProductSaysNothingToBuild() {
        val g = goods.first().id
        val p = PlannerPresenter.plan(session, g, 10L, setOf(g))!!
        assertTrue(PlannerPresenter.verdict(p).first.startsWith("Nothing to build"))
    }

    @Test fun verdictTonesFollowProfit() {
        for (g in goods.take(40)) {
            val p = PlannerPresenter.plan(session, g.id, 10L, emptySet())!!
            val (_, tone) = PlannerPresenter.verdict(p)
            if (p.totals.facilityCount > 0 && p.totals.profitPerDay.raw <= 0L) assertEquals(Tone.BAD, tone)
        }
    }

    @Test fun rateOptionsAreAscending() = assertEquals(PlannerPresenter.rates.sorted(), PlannerPresenter.rates)

    @Test fun unknownRateIsRejected() {
        assertEquals(null, session.planChain(goods.first().id, Quantity(0L)))
    }
}
