package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BalanceStudyTest {
    private val harness = WorkshopHarness(Samples.content)
    private val quick = HarnessSettings(days = 10, sampleEvery = 10, checkEvery = 5, stallDays = 5)
    private val d = Samples.cheap()

    private fun <T> ok(o: Outcome<T>): T = (o as? Outcome.Ok)?.value ?: fail("rejected: $o").let { throw IllegalStateException() }

    @Test fun aStudyPlaysEveryPolicyOnEverySeedInOrder() {
        val r = ok(BalanceStudy(harness).run(d, StudySpec(listOf(PolicyKind.IDLE, PolicyKind.GREEDY), listOf(3L, 1L), quick)))
        assertEquals(listOf("idle" to 3L, "idle" to 1L, "greedy" to 3L, "greedy" to 1L), r.runs.map { it.policy to it.seed })
        assertEquals(2, r.runsOf(PolicyKind.GREEDY).size)
        assertNotNull(r.run(PolicyKind.IDLE, 1L))
        assertEquals(null, r.run(PolicyKind.CONSERVATIVE, 1L))
    }

    @Test fun theReportCoversTheRunsPlayed() {
        val r = ok(BalanceStudy(harness).run(d, StudySpec(listOf(PolicyKind.IDLE), listOf(1L), quick)))
        assertEquals(d.id, r.report.draftId)
        assertEquals(listOf(1L), r.report.seeds)
        assertNotNull(r.report.digest("idle"))
    }

    @Test fun studiesAreDeterministic() {
        val spec = StudySpec(listOf(PolicyKind.GREEDY), listOf(5L), quick)
        val a = ok(BalanceStudy(harness).run(d, spec)).runs.single()
        val b = ok(BalanceStudy(harness).run(d, spec)).runs.single()
        assertEquals(a.stateHash, b.stateHash)
        assertEquals(a.last.netWorth, b.last.netWorth)
    }

    @Test fun anUnplayableDraftRejectsTheWholeStudy() {
        val broken = d.copy(company = d.company.copy(starters = listOf(StarterSite("no_such_recipe", 0))))
        assertTrue(BalanceStudy(harness).run(broken, StudySpec(listOf(PolicyKind.IDLE), listOf(1L), quick)) is Outcome.Rejected)
    }

    @Test fun specsRefuseEmptyOrRepeatedChoices() {
        for (make in listOf({ StudySpec(emptyList()) }, { StudySpec(seeds = emptyList()) }, { StudySpec(seeds = listOf(1L, 1L)) })) {
            try { make(); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
        }
    }

    // ---- sweeps ----------------------------------------------------------------------------------------

    @Test fun rangesAreEvenlySpacedAndDistinct() {
        assertEquals(listOf(0L, 50L, 100L), SweepRunner.range(0, 100, 3))
        assertEquals(listOf(7L), SweepRunner.range(7, 99, 1))
        assertEquals(listOf(0L, 1L), SweepRunner.range(0, 1, 5))
        try { SweepRunner.range(0, 1, 0); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun eachParameterReadsAndWritesItsOwnNumber() {
        for (p in SweepParam.values()) {
            val v = p.current(d)
            val changed = p.apply(d, v)
            assertEquals(p.name, v, p.current(changed))
        }
        assertEquals(777_000L, SweepParam.CAPITAL.current(SweepParam.CAPITAL.apply(d, 777_000L)))
        assertEquals(3L, SweepParam.RIVALS.current(SweepParam.RIVALS.apply(d, 3)))
        assertEquals(5L, SweepParam.YEARS.current(SweepParam.YEARS.apply(d, 5)))
    }

    @Test fun goalScaleScalesTargetsButKeepsSingleGoalsAtOne() {
        val withOne = d.copy(goals = listOf(DraftGoal(app.many.economytycoon.scenarios.GoalKind.CASH, 1_000), DraftGoal(app.many.economytycoon.scenarios.GoalKind.TECHNOLOGIES, 1)))
        val half = SweepParam.GOAL_SCALE.apply(withOne, 50)
        assertEquals(listOf(500L, 1L), half.goals.map { it.target })
        assertEquals(1L, SweepParam.GOAL_SCALE.apply(withOne, 0).goals[0].target)
    }

    @Test fun aSweepHasOneRowPerValue() {
        val rep = SweepRunner(harness).run(d, SweepSpec(SweepParam.CAPITAL, listOf(50_000L, 150_000L), PolicyKind.IDLE, listOf(1L), quick))
        assertEquals(listOf(50_000L, 150_000L), rep.rows.map { it.value })
        assertTrue(rep.rows.all { it.error == null && it.runs.size == 1 })
        assertEquals(2, rep.lines().size)
        assertNotNull(rep.row(50_000L))
    }

    @Test fun moreStartingCapitalDoesNotLowerAnIdleCompanysWorth() {
        val rep = SweepRunner(harness).run(d, SweepSpec(SweepParam.CAPITAL, listOf(50_000L, 100_000L, 200_000L), PolicyKind.IDLE, listOf(1L), quick))
        val worth = rep.rows.map { it.netWorth.median }
        assertEquals(worth.sorted(), worth)
        assertTrue(worth.first() < worth.last())
        assertTrue(rep.monotone)
    }

    @Test fun anUnplayableValueGivesAnErrorRowNotACrash() {
        val broken = d.copy(company = d.company.copy(starters = listOf(StarterSite("no_such_recipe", 0))))
        val rep = SweepRunner(harness).run(broken, SweepSpec(SweepParam.CAPITAL, listOf(100_000L), PolicyKind.IDLE, listOf(1L), quick))
        assertNotNull(rep.rows.single().error)
        assertTrue(rep.lines().single().contains("cannot play"))
        assertEquals(null, rep.bestValue)
    }

    @Test fun theBestValueIsTheOneWithTheMostProgress() {
        val rep = SweepRunner(harness).run(d, SweepSpec(SweepParam.CAPITAL, listOf(50_000L, 400_000L), PolicyKind.IDLE, listOf(1L), quick))
        val best = requireNotNull(rep.rows.maxByOrNull { it.progress.median })
        if (rep.rows[0].progress.median != rep.rows[1].progress.median) assertEquals(best.value, rep.bestValue)
        else assertEquals(50_000L, rep.bestValue)
    }

    @Test fun sweepSpecsRefuseEmptyInputs() {
        try { SweepSpec(SweepParam.CAPITAL, emptyList()); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
        try { SweepSpec(SweepParam.CAPITAL, listOf(1L), seeds = emptyList()); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
        assertFalse(SweepParam.values().isEmpty())
    }
}
