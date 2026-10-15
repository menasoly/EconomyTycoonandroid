package app.many.economytycoon.session

import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleReportsTest {
    private val sim = SimTestSupport.sim(61L, 4).also { it.advance(70) }
    private val reports = PeopleReports(sim.economy)

    @Test fun workforceSummaryMatchesTheEngine() {
        for (c in sim.economy.companies.active()) {
            val s = reports.workforce(c.id)
            assertEquals(sim.economy.workforce.employedBy(c.id), s.headcount)
            assertTrue(s.averageMoralePpm in 0..1_000_000)
        }
    }

    @Test fun cohortRowsCoverEveryCohortOfACompany() {
        val c = sim.economy.companies.active().first { sim.economy.workforce.cohortsOf(it.id).isNotEmpty() }
        val rows = reports.cohorts(c.id)
        assertEquals(sim.economy.workforce.cohortsOf(c.id).size, rows.size)
        assertEquals(sim.economy.workforce.employedBy(c.id), rows.sumOf { it.headcount })
        assertTrue(rows.all { it.role.isNotBlank() && it.marketWage.raw >= 0 })
    }

    @Test fun theTechTreeClassifiesEveryTechnology() {
        val c = sim.economy.companies.active().first()
        val tree = reports.techTree(c.id)
        assertEquals(sim.economy.content.allTechnologies.size, tree.size)
        for (t in tree) {
            if (t.state == TechState.LOCKED) assertTrue(t.missing.isNotEmpty())
            if (t.state == TechState.AVAILABLE) assertTrue(t.missing.isEmpty() && t.progressPpm == 0L)
            if (t.state == TechState.UNLOCKED) assertEquals(1_000_000L, t.progressPpm)
        }
    }

    @Test fun researchSummaryCountsUnlockedTechnologies() {
        val c = sim.economy.companies.active().first()
        val s = reports.research(c.id)
        assertEquals(sim.economy.research.of(c.id).unlockedTechs().size, s.unlocked)
        assertEquals(sim.economy.content.allTechnologies.size, s.total)
        assertTrue(s.active.all { it.state == TechState.RESEARCHING })
    }

    @Test fun researchStartedByCommandShowsAsActive() {
        val c = sim.economy.companies.active().first()
        val available = reports.techTree(c.id).firstOrNull { it.state == TechState.AVAILABLE }
        if (available != null) {
            sim.economy.research.start(c.id, available.id, sim.tick)
            assertEquals(TechState.RESEARCHING, reports.row(c.id, available.id).state)
        }
    }
}
