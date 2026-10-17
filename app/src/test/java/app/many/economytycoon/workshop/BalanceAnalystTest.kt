package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceAnalystTest {
    private val d = Samples.cheap() // goals: net worth (weight 2) and employees (weight 1); deadline 720 days

    private fun r(policy: String, seed: Long = 1L, end: List<Long>, days: Int = 60, outcome: RunEnd = RunEnd.HORIZON, netWorth: Long = 300_000L, wonOn: Int? = null,
                  softlock: Int? = null, regressed: List<Boolean>? = null, firstDay: List<Int?>? = null, bankrupt: Int? = null, invariants: List<String> = emptyList()) =
        Samples.run(d, policy, seed, days, end = end, outcome = outcome, netWorth = netWorth, wonOn = wonOn, softlock = softlock, regressed = regressed, firstDay = firstDay, bankrupt = bankrupt, invariants = invariants)

    private fun analyse(runs: List<RunMetrics>, draft: ScenarioDraft = d, tuning: AnalysisTuning = AnalysisTuning()) = BalanceAnalyst.analyse(draft, runs, tuning)

    private val flat = listOf(0L, 0L)

    // ---- difficulty -------------------------------------------------------------------------------

    @Test fun anEarlyWinRatesTrivial() {
        val rep = analyse(listOf(r("greedy", end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 60)))
        assertEquals(DifficultyBand.TRIVIAL, rep.rating.band)
        assertEquals("greedy", rep.rating.bestPolicy)
        assertEquals(60L, rep.rating.projectedDays)
    }

    @Test fun slowProgressRatesHard() {
        val rep = analyse(listOf(r("greedy", end = listOf(100_000L, 100_000L))))
        assertEquals(600L, rep.rating.projectedDays)
        assertEquals(DifficultyBand.HARD, rep.rating.band)
        assertTrue(rep.rating.score in 60..84)
    }

    @Test fun middlingProgressRatesModerate() {
        val rep = analyse(listOf(r("greedy", end = listOf(150_000L, 150_000L))))
        assertEquals(400L, rep.rating.projectedDays)
        assertEquals(DifficultyBand.MODERATE, rep.rating.band)
    }

    @Test fun fasterProgressRatesEasy() {
        val rep = analyse(listOf(r("greedy", end = listOf(250_000L, 250_000L))))
        assertEquals(DifficultyBand.EASY, rep.rating.band)
    }

    @Test fun noHeadwayAtAllIsImpossible() {
        val rep = analyse(listOf(r("greedy", end = flat), r("idle", end = flat)))
        assertEquals(DifficultyBand.IMPOSSIBLE, rep.rating.band)
        assertEquals(100, rep.rating.score)
        assertNull(rep.rating.bestPolicy)
        assertNull(rep.rating.projectedDays)
    }

    @Test fun aProjectionBeyondThreeDeadlinesIsImpossible() {
        val rep = analyse(listOf(r("greedy", end = listOf(20_000L, 20_000L))))
        assertEquals(DifficultyBand.IMPOSSIBLE, rep.rating.band)
        assertEquals(3_000L, rep.rating.projectedDays)
    }

    @Test fun lossesMakeAScenarioLookHarder() {
        val steady = analyse(listOf(r("greedy", 1, listOf(150_000L, 150_000L)), r("greedy", 2, listOf(150_000L, 150_000L))))
        val risky = analyse(listOf(r("greedy", 1, listOf(150_000L, 150_000L)), r("greedy", 2, listOf(0L, 0L), outcome = RunEnd.LOST)))
        assertTrue(risky.rating.score > steady.rating.score)
    }

    @Test fun theBestPolicyDecidesTheRating() {
        val rep = analyse(listOf(r("greedy", end = listOf(200_000L, 200_000L)), r("conservative", end = listOf(50_000L, 50_000L))))
        assertEquals("greedy", rep.rating.bestPolicy)
        assertEquals(300L, rep.rating.projectedDays)
    }

    @Test fun progressIsMeasuredFromWhereTheRunStarted() {
        val started = analyse(listOf(Samples.run(d, "greedy", end = listOf(500_000L, 500_000L), start = listOf(400_000L, 400_000L))))
        // gain 100k from a start of 400k: 60 * 600k / 100k = 360 days
        assertEquals(360L, started.rating.projectedDays)
    }

    @Test fun projectedDaysIsNullForLostRunsAndRunsGoingNowhere() {
        assertNull(BalanceAnalyst.projectedDays(d, r("g", end = listOf(500_000L, 500_000L), outcome = RunEnd.LOST)))
        assertNull(BalanceAnalyst.projectedDays(d, r("g", end = flat)))
        assertEquals(42L, BalanceAnalyst.projectedDays(d, r("g", end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 42)))
    }

    // ---- digests ----------------------------------------------------------------------------------

    @Test fun digestsSummariseEachPolicyAcrossItsSeeds() {
        val rep = analyse(listOf(
            r("greedy", 1, listOf(300_000L, 300_000L), netWorth = 400_000), r("greedy", 2, listOf(500_000L, 500_000L), netWorth = 600_000),
            r("idle", 1, flat, netWorth = 300_000),
        ))
        val g = requireNotNull(rep.digest("greedy"))
        assertEquals(2, g.runs)
        assertEquals(500_000L, g.netWorth.mean)
        assertEquals(400_000L, g.progress.median)
        assertEquals(2, rep.seeds.size)
        assertEquals(listOf("greedy", "idle"), rep.digests.map { it.policy })
        assertNull(rep.digest("nobody"))
    }

    @Test fun winsAndLossesAreCounted() {
        val rep = analyse(listOf(
            r("greedy", 1, listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 50),
            r("greedy", 2, listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 70),
            r("greedy", 3, flat, outcome = RunEnd.LOST, bankrupt = 40),
            r("greedy", 4, listOf(100_000L, 100_000L)),
        ))
        val g = requireNotNull(rep.digest("greedy"))
        assertEquals(2, g.wins)
        assertEquals(1, g.losses)
        assertEquals(1, g.bankruptRuns)
        assertEquals(500_000L, g.winRatePpm)
        assertEquals(60L, g.daysToWin?.mean)
    }

    @Test fun scoresRewardWinsAndPunishLosses() {
        val win = BalanceAnalyst.runScore(d, r("g", end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON))
        val ok = BalanceAnalyst.runScore(d, r("g", end = listOf(300_000L, 300_000L)))
        val lost = BalanceAnalyst.runScore(d, r("g", end = listOf(300_000L, 300_000L), outcome = RunEnd.LOST))
        assertEquals(2_000_000L, win)
        assertEquals(300_000L, ok)
        assertEquals(0L, lost)
    }

    // ---- dominance and idleness --------------------------------------------------------------------

    @Test fun oneApproachFarAheadOnEverySeedIsADominantStrategy() {
        val runs = (1L..2L).flatMap { s -> listOf(
            r("greedy", s, listOf(600_000L, 600_000L)), r("conservative", s, listOf(150_000L, 150_000L)), r("scripted", s, listOf(100_000L, 100_000L)), r("idle", s, flat),
        ) }
        val rep = analyse(runs)
        val f = rep.withCode("dominant.strategy").single()
        assertEquals(IssueSeverity.WARNING, f.severity)
        assertEquals("greedy", f.policy)
    }

    @Test fun closeContendersAreNotDominant() {
        val rep = analyse(listOf(r("greedy", end = listOf(300_000L, 300_000L)), r("conservative", end = listOf(250_000L, 250_000L)), r("idle", end = flat)))
        assertFalse(rep.has("dominant.strategy"))
    }

    @Test fun aLeadThatFlipsOnOneSeedIsNotDominant() {
        val runs = listOf(
            r("greedy", 1, listOf(600_000L, 600_000L)), r("conservative", 1, listOf(100_000L, 100_000L)),
            r("greedy", 2, listOf(100_000L, 100_000L)), r("conservative", 2, listOf(300_000L, 300_000L)),
        )
        assertFalse(analyse(runs).has("dominant.strategy"))
    }

    @Test fun anIdleWinIsAnError() {
        val rep = analyse(listOf(r("idle", end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 30), r("greedy", end = listOf(400_000L, 400_000L))))
        val f = rep.withCode("idle.wins").single()
        assertEquals(IssueSeverity.ERROR, f.severity)
        assertTrue(f.message, f.message.contains("day 30"))
        assertFalse(rep.healthy)
    }

    @Test fun idleBeatingEveryActivePolicyIsAnError() {
        val rep = analyse(listOf(r("idle", end = listOf(400_000L, 400_000L)), r("greedy", end = listOf(100_000L, 100_000L))))
        assertTrue(rep.has("idle.best"))
    }

    @Test fun idleNearlyAsGoodAsTheBestIsAWarning() {
        val rep = analyse(listOf(r("idle", end = listOf(270_000L, 270_000L)), r("greedy", end = listOf(300_000L, 300_000L))))
        assertTrue(rep.has("idle.viable"))
        assertFalse(rep.has("idle.best"))
    }

    @Test fun idleFarBehindIsFine() {
        val rep = analyse(listOf(r("idle", end = listOf(50_000L, 50_000L)), r("greedy", end = listOf(400_000L, 400_000L))))
        assertFalse(rep.has("idle.viable"))
        assertFalse(rep.has("idle.best"))
    }

    @Test fun whenNothingMakesHeadwayTheReportSaysSo() {
        val rep = analyse(listOf(r("greedy", end = listOf(10_000L, 10_000L)), r("idle", end = flat)))
        assertTrue(rep.has("none.progress"))
    }

    // ---- softlocks -----------------------------------------------------------------------------------

    @Test fun aStuckActiveRunIsAnError() {
        val rep = analyse(listOf(r("greedy", end = listOf(10_000L, 10_000L), softlock = 33)))
        val f = rep.withCode("softlock").single()
        assertEquals(IssueSeverity.ERROR, f.severity)
        assertTrue(f.message, f.message.contains("day 33"))
        assertEquals("greedy", f.policy)
        assertEquals(1L, f.seed)
    }

    @Test fun idleBeingStuckIsNotReported() {
        assertFalse(analyse(listOf(r("idle", end = flat, softlock = 10), r("greedy", end = listOf(300_000L, 300_000L)))).has("softlock"))
    }

    @Test fun manyStuckRunsAreSummarised() {
        val runs = (1L..8L).map { r("greedy", it, listOf(10_000L, 10_000L), softlock = 20) }
        val rep = analyse(runs)
        assertEquals(5, rep.withCode("softlock").size)
        assertEquals("3 more runs got stuck", rep.withCode("softlock.more").single().message)
    }

    // ---- goals ----------------------------------------------------------------------------------------

    @Test fun aGoalMetAndThenLostIsReportedOnce() {
        val runs = listOf(
            r("greedy", 1, listOf(300_000L, 300_000L), regressed = listOf(true, false)),
            r("greedy", 2, listOf(300_000L, 300_000L), regressed = listOf(true, false)),
        )
        val f = analyse(runs).withCode("goal.regressed").single()
        assertTrue(f.message, f.message.contains("net worth"))
        assertTrue(f.message, f.message.contains("2 of 2"))
    }

    @Test fun twoFluctuatingGoalsAreNoted() {
        val rep = analyse(listOf(r("greedy", end = listOf(300_000L, 300_000L))))
        assertEquals(IssueSeverity.NOTE, rep.withCode("goals.simultaneous").single().severity)
    }

    @Test fun aMonotoneGoalDoesNotCountAsFluctuating() {
        val draft = d.copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 400_000), DraftGoal(GoalKind.TECHNOLOGIES, 3)))
        val run = Samples.run(draft, "greedy", end = listOf(300_000L, 300_000L))
        assertFalse(BalanceAnalyst.analyse(draft, listOf(run)).has("goals.simultaneous"))
    }

    @Test fun goalsMetSeparatelyButNeverTogetherAreFlagged() {
        val rep = analyse(listOf(r("greedy", end = listOf(900_000L, 900_000L), firstDay = listOf(30, 50))))
        val f = rep.withCode("goal.never_together").single()
        assertEquals("greedy", f.policy)
    }

    @Test fun aWinWhereGoalsWereMetTogetherIsNotFlagged() {
        val rep = analyse(listOf(r("greedy", end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 60, firstDay = listOf(30, 60))))
        assertFalse(rep.has("goal.never_together"))
    }

    @Test fun monotonyKnowsWhichGoalsCanFall() {
        assertTrue(GoalMonotony.isMonotone(GoalKind.TECHNOLOGIES))
        assertTrue(GoalMonotony.isMonotone(GoalKind.INDUSTRIES))
        assertFalse(GoalMonotony.isMonotone(GoalKind.CASH))
        assertEquals(listOf(GoalKind.CASH), GoalMonotony.fluctuating(listOf(GoalKind.CASH, GoalKind.TECHNOLOGIES)))
    }

    // ---- variance and the rest ------------------------------------------------------------------------

    @Test fun widelyVaryingResultsAreFlagged() {
        val runs = listOf(
            r("greedy", 1, listOf(300_000L, 300_000L), netWorth = 100_000), r("greedy", 2, listOf(300_000L, 300_000L), netWorth = 900_000),
            r("greedy", 3, listOf(300_000L, 300_000L), netWorth = 500_000),
        )
        assertTrue(analyse(runs).has("variance.high"))
    }

    @Test fun steadyResultsAreNot() {
        val runs = listOf(r("greedy", 1, listOf(300_000L, 300_000L), netWorth = 500_000), r("greedy", 2, listOf(300_000L, 300_000L), netWorth = 520_000))
        assertFalse(analyse(runs).has("variance.high"))
    }

    @Test fun aSplitBetweenWinsAndNonWinsIsFlagged() {
        val runs = listOf(
            r("greedy", 1, listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 60), r("greedy", 2, listOf(300_000L, 300_000L)),
        )
        val f = analyse(runs).withCode("variance.split").single()
        assertTrue(f.message, f.message.contains("1 of 2"))
    }

    @Test fun oneSeedCannotShowVariance() {
        assertFalse(analyse(listOf(r("greedy", end = listOf(300_000L, 300_000L)))).has("variance.high"))
    }

    @Test fun brokenBooksAreAnError() {
        val rep = analyse(listOf(r("greedy", end = listOf(300_000L, 300_000L), invariants = listOf("ledger out of balance"))))
        assertTrue(rep.withCode("invariant").single().message.contains("ledger out of balance"))
    }

    @Test fun aTagThatDisagreesWithTheRatingIsFlagged() {
        val harsh = d.copy(tags = listOf(DifficultyTag.HARSH))
        val run = Samples.run(harsh, "greedy", end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 40)
        assertTrue(BalanceAnalyst.analyse(harsh, listOf(run)).has("rating.tag"))
        val gentle = d.copy(tags = listOf(DifficultyTag.GENTLE))
        val hard = Samples.run(gentle, "greedy", end = listOf(100_000L, 100_000L))
        assertTrue(BalanceAnalyst.analyse(gentle, listOf(hard)).has("rating.tag"))
        val fine = Samples.run(gentle, "greedy", end = listOf(1_000_000L, 1_000_000L), outcome = RunEnd.WON, wonOn = 40)
        assertFalse(BalanceAnalyst.analyse(gentle, listOf(fine)).has("rating.tag"))
    }

    @Test fun shortRunsAreNotedAsExtrapolation() {
        assertTrue(analyse(listOf(r("greedy", end = listOf(300_000L, 300_000L), days = 60))).has("horizon.short"))
        assertFalse(analyse(listOf(r("greedy", end = listOf(300_000L, 300_000L), days = 400))).has("horizon.short"))
    }

    @Test fun findingsAreOrderedBySeverity() {
        val rep = analyse(listOf(r("greedy", end = listOf(10_000L, 10_000L), softlock = 5), r("idle", end = flat)))
        val sev = rep.findings.map { it.severity.ordinal }
        assertEquals(sev.sorted(), sev)
        assertTrue(rep.problems.all { it.severity == IssueSeverity.ERROR })
    }

    @Test fun reportsCarryTheBasics() {
        val rep = analyse(listOf(r("greedy", end = listOf(300_000L, 300_000L), days = 77)))
        assertEquals("cheap-run", rep.draftId)
        assertEquals(720, rep.deadlineDays)
        assertEquals(77, rep.horizonDays)
        assertNotNull(rep.digest("greedy"))
    }

    @Test fun analysisIsDeterministic() {
        val runs = listOf(r("greedy", end = listOf(300_000L, 300_000L)), r("idle", end = flat))
        assertEquals(analyse(runs).findings, analyse(runs).findings)
        assertEquals(analyse(runs).rating, analyse(runs).rating)
    }

    @Test fun tuningChangesTheVerdicts() {
        val runs = listOf(r("idle", end = listOf(150_000L, 150_000L)), r("greedy", end = listOf(300_000L, 300_000L)))
        assertFalse(analyse(runs).has("idle.viable"))
        assertTrue(analyse(runs, tuning = AnalysisTuning(idleViablePpm = 400_000L)).has("idle.viable"))
    }

    @Test fun anEmptyRunListGivesAnImpossibleRatingAndNoFindings() {
        val rep = analyse(emptyList())
        assertEquals(DifficultyBand.IMPOSSIBLE, rep.rating.band)
        assertTrue(rep.digests.isEmpty())
        assertEquals(0, rep.horizonDays)
    }
}
