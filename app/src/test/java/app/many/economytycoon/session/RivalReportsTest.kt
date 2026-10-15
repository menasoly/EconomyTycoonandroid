package app.many.economytycoon.session

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RivalReportsTest {
    private val sim = SimTestSupport.sim(81L, 5).also { it.advance(80) }
    private val rivals = RivalReports(sim.economy)

    @Test fun rivalsListAiCompaniesRichestFirst() {
        val rows = rivals.rivals(null)
        assertTrue(rows.isNotEmpty())
        assertEquals(rows.map { it.headline.netWorth.raw }.sortedDescending(), rows.map { it.headline.netWorth.raw })
        assertTrue(rows.filter { it.headline.kind == app.many.economytycoon.companies.CompanyKind.AI }.all { it.archetype != null })
    }

    @Test fun theViewerIsLeftOutOfTheirOwnRivalList() {
        val me = sim.economy.companies.active().first().id
        assertTrue(rivals.rivals(me).none { it.headline.id == me })
    }

    @Test fun producersOfAGoodAreCountedByFacility() {
        val good = sim.economy.content.allGoods.first { sim.economy.content.recipesProducing(it.id).isNotEmpty() }
        val rows = rivals.producersOf(good.id)
        assertEquals(rows.map { it.second }.sortedDescending(), rows.map { it.second })
        assertTrue(rivals.producersOf(GoodId("no_such_good")).isEmpty())
    }

    @Test fun offersAreListedForBothSides() {
        val ids = sim.economy.companies.active().map { it.id }
        val target = ids[1]
        val price = sim.economy.acquisitions.fairPricePerShare(target, sim.tick)
        val made = sim.economy.acquisitions.makeOffer(ids[0], target, 5, price, sim.tick)
        if (made.isOk) {
            assertEquals(1, rivals.offersFor(target).count { it.acquirerId == ids[0] })
            assertEquals(1, rivals.offersBy(ids[0]).count { it.targetId == target })
        }
    }
}
