package app.many.economytycoon.tutorial

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTest {
    private val empty = TutorialFacts()

    @Test fun acknowledgeNeedsTheFlag() {
        assertFalse(Goal.Acknowledge.met(empty))
        assertTrue(Goal.Acknowledge.met(empty, acknowledged = true))
    }

    @Test fun visitNeedsTheScreenOpened() {
        val g = Goal.Visit("market")
        assertFalse(g.met(empty))
        assertTrue(g.met(empty.copy(visited = setOf("market"))))
        assertFalse(g.met(empty.copy(visited = setOf("trade"))))
    }

    @Test fun reachComparesTheGauge() {
        val g = Goal.Reach(Gauge.FACILITIES, 2)
        assertFalse(g.met(empty.copy(facilities = 1)))
        assertTrue(g.met(empty.copy(facilities = 2)))
        assertTrue(g.met(empty.copy(facilities = 9)))
    }

    @Test fun everyGaugeReadsItsFact() {
        val f = TutorialFacts(day = 5, cash = Money.units(900), facilities = 2, runningFacilities = 1, employees = 7, loans = 3, activeContracts = 4, vehicles = 6, techsUnlocked = 8, monthlyRevenue = Money.units(100), monthlyProfit = Money.units(-20))
        assertEquals(listOf(5L, 2L, 1L, 7L, 3L, 4L, 6L, 8L, 900L, 100L, -20L), listOf(Gauge.DAY, Gauge.FACILITIES, Gauge.RUNNING, Gauge.EMPLOYEES, Gauge.LOANS, Gauge.CONTRACTS, Gauge.VEHICLES, Gauge.TECHS, Gauge.CASH, Gauge.REVENUE, Gauge.PROFIT).map { it.read(f) })
    }

    @Test fun listedNeedsTheListing() {
        assertFalse(Goal.Listed.met(empty))
        assertTrue(Goal.Listed.met(empty.copy(listed = true)))
    }

    @Test fun allNeedsEveryPart() {
        val g = Goal.All(listOf(Goal.Reach(Gauge.EMPLOYEES, 1), Goal.Visit("people")))
        assertFalse(g.met(empty.copy(employees = 1)))
        assertFalse(g.met(empty.copy(visited = setOf("people"))))
        assertTrue(g.met(empty.copy(employees = 1, visited = setOf("people"))))
    }

    @Test fun emptyAllIsMet() = assertTrue(Goal.All(emptyList()).met(empty))

    @Test fun reachProgressIsProportionalAndCapped() {
        val g = Goal.Reach(Gauge.DAY, 90)
        assertEquals(0L, g.progressPpm(empty))
        assertEquals(500_000L, g.progressPpm(empty.copy(day = 45)))
        assertEquals(1_000_000L, g.progressPpm(empty.copy(day = 400)))
    }

    @Test fun negativeGaugeCountsAsNoProgress() {
        assertEquals(0L, Goal.Reach(Gauge.PROFIT, 100).progressPpm(empty.copy(monthlyProfit = Money.units(-50))))
    }

    @Test fun zeroTargetIsAlreadyComplete() = assertEquals(1_000_000L, Goal.Reach(Gauge.DAY, 0).progressPpm(empty))

    @Test fun allAveragesItsParts() {
        val g = Goal.All(listOf(Goal.Reach(Gauge.DAY, 10), Goal.Visit("x")))
        assertEquals(250_000L, g.progressPpm(empty.copy(day = 5)))
        assertEquals(500_000L, g.progressPpm(empty.copy(day = 10)))
        assertEquals(1_000_000L, g.progressPpm(empty.copy(day = 10, visited = setOf("x"))))
    }

    @Test fun simpleGoalsAreAllOrNothing() {
        assertEquals(0L, Goal.Listed.progressPpm(empty))
        assertEquals(1_000_000L, Goal.Listed.progressPpm(empty.copy(listed = true)))
    }

    @Test fun everyGoalDescribesItself() {
        for (g in listOf(Goal.Acknowledge, Goal.Visit("market"), Goal.Reach(Gauge.VEHICLES, 3), Goal.Listed, Goal.All(listOf(Goal.Listed, Goal.Visit("x"))))) assertTrue(g.describe().isNotBlank())
        assertEquals("Get 3 vehicles.", Goal.Reach(Gauge.VEHICLES, 3).describe())
    }
}
