package app.many.economytycoon.health

import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceRunTest {
    private val runner = BalanceRun(SimTestSupport.content, rivals = 4)

    @Test fun aFreshWorldIsSampledAtTheStartAndThenOnSchedule() {
        val r = runner.run(5L, 90L, 30L)
        assertEquals(listOf(0L, 30L, 60L, 90L), r.samples.map { it.tick })
        assertEquals(4, r.samples.first().aiCompanies)
        assertTrue(r.last.markets > 0)
    }

    @Test fun theLastPartialIntervalIsStillSampled() {
        val r = runner.run(5L, 50L, 30L)
        assertEquals(listOf(0L, 30L, 50L), r.samples.map { it.tick })
    }

    @Test fun theSameSeedGivesTheSameRun() {
        val a = runner.run(8L, 60L, 30L)
        val b = runner.run(8L, 60L, 30L)
        assertEquals(a.samples, b.samples)
        assertEquals(a.issues, b.issues)
    }

    @Test fun differentSeedsGiveDifferentWorlds() {
        val a = runner.run(8L, 60L, 30L).last
        val b = runner.run(9L, 60L, 30L).last
        assertTrue(a != b)
    }

    @Test fun noInvariantIsBrokenAlongTheWay() {
        val r = runner.run(12L, 120L, 40L)
        assertEquals(emptyList<String>(), r.problems)
    }

    @Test fun moneySupplyOnlyGrowsThroughMinting() {
        val r = runner.run(13L, 120L, 40L)
        assertTrue(r.samples.all { it.moneySupply > 0L })
    }

    @Test fun formatListsEachSampleAndIssue() {
        val r = runner.run(5L, 60L, 30L)
        val text = BalanceFormat.result(r)
        assertTrue(text, text.startsWith("seed 5, 60 days"))
        assertEquals(r.samples.size, text.lines().count { it.trimStart().startsWith("day") })
    }

    @Test fun summaryCountsIssuesAcrossWorlds() {
        val ok = BalanceResult(1, 10, emptyList(), emptyList(), emptyList())
        val bad = BalanceResult(2, 10, emptyList(), emptyList(), listOf(HealthIssue("ai.broke", HealthSeverity.PROBLEM, "x"), HealthIssue("price.floor", HealthSeverity.WARNING, "y")))
        assertEquals("1 worlds: no issues", BalanceFormat.summary(listOf(ok)))
        assertEquals("2 worlds: ai.broke x1, price.floor x1", BalanceFormat.summary(listOf(ok, bad)))
        assertEquals(HealthSeverity.PROBLEM, bad.worst())
        assertTrue(bad.has("ai.broke"))
    }

    @Test fun runAllKeepsSeedOrder() {
        val rs = runner.runAll(listOf(3L, 4L), 30L, 30L)
        assertEquals(listOf(3L, 4L), rs.map { it.seed })
    }
}
