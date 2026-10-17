package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AutoTunerTest {
    private val d = Samples.cheap()

    private fun rating(score: Int) = DifficultyRating(score, DifficultyBand.forScore(score), "greedy", null, 0)

    /** A scenario whose difficulty is a function of the starting capital. */
    private class Calls(val f: (Long) -> Int) : DifficultyProbe {
        val seen = ArrayList<Long>()
        override fun rate(draft: ScenarioDraft): Outcome<DifficultyRating> {
            seen += draft.company.capital
            return Outcome.ok(DifficultyRating(f(draft.company.capital), DifficultyBand.forScore(f(draft.company.capital)), "greedy", null, 0))
        }
    }

    private fun spec(target: DifficultyBand, low: Long = 0, high: Long = 1_000, probes: Int = 10) = TuneSpec(SweepParam.CAPITAL, low, high, target, probes)

    @Test fun bandsPartitionTheScores() {
        for (s in 0..100) {
            val b = DifficultyBand.forScore(s)
            assertTrue("$s in $b", s in b.scores)
        }
        assertEquals(DifficultyBand.values().toList(), (0..100).map { DifficultyBand.forScore(it) }.distinct())
    }

    @Test fun bandsAgreeWithTheAnalyst() {
        val d2 = Samples.cheap()
        val runs = listOf(Samples.run(d2, "idle", 1, 100, end = listOf(0L, 0L), netWorth = 1L), Samples.run(d2, "greedy", 1, 100, end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 50))
        val r = BalanceAnalyst.analyse(d2, runs).rating
        assertTrue(r.score in r.band.scores)
    }

    @Test fun aScenarioAlreadyInTheBandIsLeftAlone() {
        val calls = Calls { 40 }
        val r = AutoTuner(calls).tune(d, spec(DifficultyBand.MODERATE))
        assertEquals(TuneOutcome.ALREADY_THERE, r.outcome)
        assertEquals(d.company.capital, r.value)
        assertEquals(1, calls.seen.size)
    }

    @Test fun anEasierScenarioWithMoreCapitalIsFoundByBisection() {
        // More capital makes it easier: 100 at 0, 0 at 1000.
        val calls = Calls { 100 - (it / 10).toInt() }
        val r = AutoTuner(calls).tune(d.copy(company = d.company.copy(capital = 900)), spec(DifficultyBand.HARD))
        assertEquals(TuneOutcome.FOUND, r.outcome)
        val v = requireNotNull(r.value)
        assertTrue(100 - (v / 10).toInt() in DifficultyBand.HARD.scores)
        assertEquals(v, r.tuned?.company?.capital)
        assertTrue(r.probes <= 10)
    }

    @Test fun aHarderWithMoreNumberDirectionWorksToo() {
        val calls = Calls { (it / 10).toInt() }
        val r = AutoTuner(calls).tune(d, spec(DifficultyBand.EASY))
        assertEquals(TuneOutcome.FOUND, r.outcome)
        assertTrue((requireNotNull(r.value) / 10).toInt() in DifficultyBand.EASY.scores)
    }

    @Test fun anEndOfTheRangeCanBeTheAnswer() {
        val r = AutoTuner(Calls { (it / 10).toInt() }).tune(d.copy(company = d.company.copy(capital = 500)), spec(DifficultyBand.TRIVIAL, low = 0, high = 1_000))
        assertEquals(TuneOutcome.FOUND, r.outcome)
        assertTrue(requireNotNull(r.value) in 0..119)
    }

    @Test fun eachValueIsRatedOnce() {
        val calls = Calls { (it / 10).toInt() }
        AutoTuner(calls).tune(d.copy(company = d.company.copy(capital = 0)), spec(DifficultyBand.BRUTAL))
        assertEquals(calls.seen.size, calls.seen.distinct().size)
    }

    @Test fun aNumberThatDoesNotMoveTheRatingIsUnreachable() {
        val r = AutoTuner(Calls { 5 }).tune(d, spec(DifficultyBand.HARD))
        assertEquals(TuneOutcome.UNREACHABLE, r.outcome)
        assertNull(r.tuned)
        assertTrue(r.probes in 3..4)
    }

    @Test fun aBandOutsideTheRangeIsUnreachableAndNamesTheNearestEnd() {
        val r = AutoTuner(Calls { 10 + (it / 100).toInt() }).tune(d, spec(DifficultyBand.BRUTAL))
        assertEquals(TuneOutcome.UNREACHABLE, r.outcome)
        assertEquals(1_000L, r.value)
        assertTrue(r.message.contains("rates between 10 and 20"))
    }

    @Test fun runningOutOfProbesGivesTheNearestTried() {
        // A cliff: nothing between 49 and 51 is in the MODERATE band 35..59 only at exactly one value.
        val calls = Calls { if (it < 500) 20 else 70 }
        val r = AutoTuner(calls).tune(d, spec(DifficultyBand.MODERATE, probes = 5))
        assertEquals(TuneOutcome.NEAREST, r.outcome)
        assertTrue(r.probes <= 5)
        assertNotNull(r.tuned)
    }

    @Test fun anUnplayableValueStopsTheSearchWithTheReason() {
        val probe = DifficultyProbe { draft ->
            if (draft.company.capital == 0L) Outcome.reject(Rules.INVALID_ARGUMENT, "no capital") else Outcome.ok(rating(draft.company.capital.toInt() / 10))
        }
        val r = AutoTuner(probe).tune(d, spec(DifficultyBand.HARD))
        assertEquals(TuneOutcome.FAILED, r.outcome)
        assertTrue(r.message.contains("no capital"))
        assertNull(r.value)
    }

    @Test fun theLogListsEveryProbe() {
        val r = AutoTuner(Calls { (it / 10).toInt() }).tune(d, spec(DifficultyBand.EASY))
        assertEquals(r.probes + 1, r.lines().size)
        assertTrue(r.lines()[1].startsWith("tried "))
    }

    @Test fun tuningIsDeterministic() {
        val a = AutoTuner(Calls { (it / 7).toInt() }).tune(d, spec(DifficultyBand.MODERATE))
        val b = AutoTuner(Calls { (it / 7).toInt() }).tune(d, spec(DifficultyBand.MODERATE))
        assertEquals(a.steps, b.steps)
        assertEquals(a.value, b.value)
    }

    @Test fun badSpecsAreRefused() {
        for (make in listOf({ spec(DifficultyBand.EASY, low = 5, high = 5) }, { spec(DifficultyBand.EASY, probes = 2) }, { spec(DifficultyBand.IMPOSSIBLE) })) {
            try { make(); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
        }
    }

    @Test fun theStudyProbeRatesARealDraft() {
        val probe = StudyProbe(BalanceStudy(WorkshopHarness(Samples.content)), StudySpec(listOf(PolicyKind.IDLE), listOf(1L), HarnessSettings(days = 10, sampleEvery = 10, checkEvery = 5, stallDays = 5)))
        val r = (probe.rate(d) as Outcome.Ok).value
        assertTrue(r.score in 0..100)
    }
}
