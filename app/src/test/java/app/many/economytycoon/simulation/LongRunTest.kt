package app.many.economytycoon.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongRunTest {
    @Test fun threeHundredTicksStayConsistentThroughClosesAndInsolvency() {
        val sim = SimTestSupport.sim(31, ai = 6)
        sim.pipeline.checkInvariants = true
        val problems = ArrayList<String>()
        var closes = 0
        for (i in 1..300) {
            val s = sim.advance()
            if (s.closedBooks) closes++
            problems += s.problems.map { "t${s.tick.value}: $it" }
            if (problems.size > 10) break
        }
        assertEquals(emptyList<String>(), problems.take(10))
        assertTrue("books never closed", closes > 0)
    }

    @Test fun rivalsGrowTheirBusinessesOverTime() {
        val sim = SimTestSupport.sim(41, ai = 6)
        sim.advance(240)
        val e = sim.economy
        val ai = e.companies.ofKind(app.many.economytycoon.companies.CompanyKind.AI)
        val facilities = ai.sumOf { e.production.facilitiesOf(it.id).size }
        assertTrue("rivals never expanded (facilities=$facilities)", facilities > ai.size)
    }

    @Test fun moneyIsNotCreatedOutOfThinAir() {
        val sim = SimTestSupport.sim(51)
        val e = sim.economy
        sim.advance(60)
        val cash = e.finance.ledger.cashHeldBy(app.many.economytycoon.finance.OwnerKind.HOUSEHOLDS)
        assertTrue(cash.raw > 0)
        assertEquals(emptyList<String>(), e.finance.invariants())
    }
}
