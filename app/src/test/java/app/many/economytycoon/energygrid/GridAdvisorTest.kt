package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridAdvisorTest {
    private val advisor = GridAdvisor()
    private val g = GridId(1)
    private fun facts(f: GridFacts.() -> GridFacts = { this }) = GridFacts(g, "North").f()
    private fun codes(f: GridFacts) = advisor.all(f).map { it.code }

    @Test fun aHealthyGridGetsNoAdvice() {
        assertTrue(advisor.all(facts()).isEmpty())
    }

    @Test fun lowReserveIsFlaggedAndUrgentWhenNegative() {
        val low = advisor.all(facts { copy(reservePpm = 50_000L) }).single { it.code == "low-reserve" }
        val short = advisor.all(facts { copy(reservePpm = -20_000L) }).single { it.code == "low-reserve" }
        assertTrue(low.priority in 60..85)
        assertEquals(90, short.priority)
        assertTrue(short.title.contains("cannot cover"))
    }

    @Test fun reserveAtTargetIsFine() {
        assertNull(advisor.all(facts { copy(reservePpm = targetReservePpm) }).firstOrNull { it.code == "low-reserve" })
    }

    @Test fun blackoutsRankAboveOtherThings() {
        val f = facts { copy(lossOfLoadHours = 6, spikeDays = 5, carbonIntensity = 800L) }
        assertEquals("recent-blackout", advisor.all(f).first().code)
    }

    @Test fun spikesNeedAFewDays() {
        assertFalse("price-spikes" in codes(facts { copy(spikeDays = 2) }))
        assertTrue("price-spikes" in codes(facts { copy(spikeDays = 3) }))
    }

    @Test fun shortfallsInAgreementsAreReported() {
        val f = facts { copy(ppas = listOf(PpaFact(PpaId(1), 5_000L, 100L, true), PpaFact(PpaId(2), 0L, 100L, true))) }
        val i = advisor.all(f).single { it.code == "ppa-shortfall" }
        assertTrue(i.title.startsWith("1 "))
    }

    @Test fun agreementsEndingSoonAreRemembered() {
        assertTrue("ppa-expiry" in codes(facts { copy(ppas = listOf(PpaFact(PpaId(1), 0L, 20L, true))) }))
        assertFalse("ppa-expiry" in codes(facts { copy(ppas = listOf(PpaFact(PpaId(1), 0L, 200L, true))) }))
        assertFalse("ppa-expiry" in codes(facts { copy(ppas = listOf(PpaFact(PpaId(1), 0L, 20L, false))) }))
    }

    @Test fun heavyCarbonAndCurtailmentAreFlagged() {
        assertTrue("carbon-heavy" in codes(facts { copy(carbonIntensity = 700L) }))
        assertFalse("carbon-heavy" in codes(facts { copy(carbonIntensity = 300L) }))
        assertTrue("curtailment" in codes(facts { copy(curtailedSharePpm = 80_000L) }))
    }

    @Test fun storageIsSuggestedOnlyWhereTheSpreadIsWideAndNoneIsBuilt() {
        val wide = facts { copy(averagePrice = Money.units(40), dailySpread = Money.units(80)) }
        assertTrue("storage-opportunity" in codes(wide))
        assertFalse("storage-opportunity" in codes(wide.copy(storageKw = 10_000L)))
        assertFalse("storage-opportunity" in codes(wide.copy(dailySpread = Money.units(30))))
    }

    @Test fun congestionCapLossAndCapacityGapsAppear() {
        val c = codes(facts { copy(congestedLinkHours = 100, capShortfall = Money.units(1), capacityShortfallKw = 20_000L) })
        assertTrue(c.containsAll(listOf("congested-link", "cap-loss", "capacity-shortfall")))
    }

    @Test fun anAgingFleetNeedsAThirdOfItsUnitsOld() {
        assertFalse("aging-fleet" in codes(facts { copy(units = 10, agedUnits = 2) }))
        assertTrue("aging-fleet" in codes(facts { copy(units = 10, agedUnits = 3) }))
        assertFalse("aging-fleet" in codes(facts { copy(units = 0, agedUnits = 0) }))
    }

    @Test fun adviceIsSortedAndFiltered() {
        val f = facts { copy(reservePpm = -1L, spikeDays = 5, curtailedSharePpm = 90_000L, congestedLinkHours = 99) }
        val all = advisor.all(f)
        assertEquals(all.sortedByDescending { it.priority }.map { it.priority }, all.map { it.priority })
        assertTrue(advisor.advise(f, minPriority = 50).all { it.priority >= 50 })
        assertTrue(advisor.advise(f, minPriority = 50).size < all.size)
    }

    @Test fun equalPrioritiesFallBackToCodeOrder() {
        val a = GridAdvisor(listOf({ f -> GridInsight("b", f.grid, 50, "x", "y") }, { f -> GridInsight("a", f.grid, 50, "x", "y") }))
        assertEquals(listOf("a", "b"), a.all(facts()).map { it.code })
    }

    @Test fun priorityOutOfRangeIsRefused() {
        val ex = runCatching { GridInsight("x", g, 101, "t", "d") }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test fun factsAreReadFromARunningGrid() {
        val w = richWorld(20)
        val f = GridFacts.of(w.service.state, w.grid, 20L, w.service.peakDemandKw(w.grid.id))
        assertEquals(w.grid.id, f.grid)
        assertEquals(w.grid.units.count { it.isLive && it.status != UnitStatus.MOTHBALLED }, f.units)
        assertNotNull(advisor.all(f))
        assertTrue(f.carbonIntensity >= 0L)
        assertEquals(f, GridFacts.of(w.service.state, w.grid, 20L, w.service.peakDemandKw(w.grid.id)))
    }

    @Test fun anUnderbuiltGridIsToldSo() {
        val w = GridWorld()
        w.unit(Technology.GAS, 50_000L)
        w.households(60_000L)
        w.days(8)
        val f = GridFacts.of(w.service.state, w.grid, 8L, w.service.peakDemandKw(w.grid.id))
        val c = codes(f)
        assertTrue("low-reserve" in c)
        assertTrue("recent-blackout" in c)
    }
}
