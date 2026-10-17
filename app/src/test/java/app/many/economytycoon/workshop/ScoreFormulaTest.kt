package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.scenarios.Medal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScoreFormulaTest {
    private val d = Samples.cheap() // gold 300, silver 500, deadline 720
    private val goals2 = 2

    private fun run(end: List<Long>, outcome: RunEnd = RunEnd.HORIZON, wonOn: Int? = null, netWorth: Long = 1_000L, softlock: Int? = null, bankrupt: Int? = null) =
        Samples.run(d, "greedy", 1, 100, end = end, outcome = outcome, wonOn = wonOn, netWorth = netWorth, softlock = softlock, bankrupt = bankrupt)

    private fun total(m: RunMetrics, rating: DifficultyRating? = null) = ScoreFormula.score(d, m, rating).total

    @Test fun progressIsWorthTenThousandPointsAtMost() {
        val half = ScoreFormula.score(d, run(listOf(500_000L, 500_000L), netWorth = 1))
        assertEquals(5_000L, half.lines.first { it.label == "progress on goals" }.points)
        val all = ScoreFormula.score(d, run(listOf(1_000_000L, 1_000_000L), netWorth = 1))
        assertEquals(10_000L, all.lines.first { it.label == "progress on goals" }.points)
    }

    @Test fun progressRespectsGoalWeights() {
        // weights 2 and 1: (2 * 1.0 + 1 * 0) / 3
        val b = ScoreFormula.score(d, run(listOf(1_000_000L, 0L), netWorth = 1))
        assertEquals(6_666L, b.lines.first { it.label == "progress on goals" }.points)
    }

    @Test fun aWinAddsThePrizeTheMedalAndSpeed() {
        val gold = ScoreFormula.score(d, run(listOf(1_000_000L, 1_000_000L), RunEnd.WON, wonOn = 200, netWorth = 1))
        assertEquals(5_000L, gold.lines.first { it.label == "won" }.points)
        assertEquals(4_000L, gold.lines.first { it.label == "gold medal" }.points)
        assertEquals((720 - 200) * 2_000L / 720, gold.lines.first { it.label == "speed" }.points)
        assertTrue(total(run(listOf(1_000_000L, 1_000_000L), RunEnd.WON, wonOn = 200)) > total(run(listOf(1_000_000L, 1_000_000L), RunEnd.WON, wonOn = 650)))
    }

    @Test fun medalsFollowTheDraftsTimes() {
        assertEquals(Medal.GOLD, ScoreFormula.medalFor(d, 300))
        assertEquals(Medal.SILVER, ScoreFormula.medalFor(d, 301))
        assertEquals(Medal.SILVER, ScoreFormula.medalFor(d, 500))
        assertEquals(Medal.BRONZE, ScoreFormula.medalFor(d, 720))
        assertEquals(Medal.NONE, ScoreFormula.medalFor(d, 721))
    }

    @Test fun bonusGoalsAddPointsInProportion() {
        val withBonus = d.copy(goals = d.goals + DraftGoal(app.many.economytycoon.scenarios.GoalKind.CASH, 500_000, weight = 2, bonus = true))
        val m = Samples.run(withBonus, "greedy", end = listOf(0L, 0L, 500_000L), netWorth = 1)
        val line = ScoreFormula.score(withBonus, m).lines.first { it.label == "bonus goal 3" }
        assertEquals(500L, line.points)
    }

    @Test fun netWorthEarnsALogarithmicBonusWithACap() {
        fun pts(nw: Long) = ScoreFormula.score(d, run(listOf(0L, 0L), netWorth = nw)).lines.firstOrNull { it.label == "net worth" }?.points ?: 0L
        assertEquals(0L, pts(1))
        assertEquals(1_000L, pts(1_024))
        assertEquals(ScoreFormula.WORTH_POINT_CAP, pts(Long.MAX_VALUE / 2))
        assertTrue(pts(2_000_000) > pts(2_000))
    }

    @Test fun penaltiesApply() {
        val clean = total(run(listOf(500_000L, 500_000L)))
        assertEquals(clean - ScoreFormula.BANKRUPT_PENALTY, total(run(listOf(500_000L, 500_000L), bankrupt = 30)))
        assertEquals(clean - ScoreFormula.STUCK_PENALTY, total(run(listOf(500_000L, 500_000L), softlock = 30)))
        assertEquals(clean - ScoreFormula.LOSS_PENALTY, total(run(listOf(500_000L, 500_000L), RunEnd.LOST)))
    }

    @Test fun aScoreNeverGoesBelowZero() {
        assertEquals(0L, total(run(listOf(0L, 0L), RunEnd.LOST, netWorth = 0, bankrupt = 5, softlock = 5)))
    }

    @Test fun harderScenariosMultiplyTheScore() {
        val m = run(listOf(500_000L, 500_000L))
        val easy = DifficultyRating(0, DifficultyBand.TRIVIAL, "greedy", 10, 0)
        val hard = DifficultyRating(100, DifficultyBand.BRUTAL, "greedy", 700, 900_000)
        assertEquals(80, ScoreFormula.multiplier(easy))
        assertEquals(180, ScoreFormula.multiplier(hard))
        assertEquals(100, ScoreFormula.multiplier(null))
        assertTrue(total(m, hard) > total(m, null))
        assertTrue(total(m, null) > total(m, easy))
        assertEquals(total(m, null) * 180 / 100, total(m, hard))
    }

    @Test fun theBreakdownListsOnlyLinesThatMatter() {
        val b = ScoreFormula.score(d, run(listOf(0L, 0L), netWorth = 1))
        assertTrue(b.lines.all { it.points != 0L })
        assertTrue(b.describe().last().startsWith("total: "))
        assertTrue(b.describe().contains("difficulty multiplier: 100%"))
    }

    @Test fun scoringIsDeterministic() {
        assertEquals(ScoreFormula.score(d, run(listOf(400_000L, 300_000L))).total, ScoreFormula.score(d, run(listOf(400_000L, 300_000L))).total)
    }

    // ---- submissions and the board ---------------------------------------------------------------------

    private fun sub(player: String = "Ada", score: Long = 5_000, days: Int = 400, seed: Long = 1L) = ScoreSubmission("hash", "cheap-run", player, seed, days, true, score)

    @Test fun submissionsRoundTripAndKeepTheirChecksum() {
        val s = sub()
        assertEquals(s, ScoreSubmission.fromBytes(s.toBytes()))
        assertEquals(s.checksum, ScoreSubmission.fromBytes(s.toBytes()).checksum)
    }

    @Test fun editingASubmissionBreaksItsChecksum() {
        val bytes = sub(score = 5_000).toBytes()
        val other = sub(score = 9_000).toBytes()
        assertFalse(bytes.contentEquals(other))
        // Swap the body of one for the checksum of the other.
        val forged = other.copyOf()
        System.arraycopy(bytes, bytes.size - 8, forged, forged.size - 8, 8)
        try { ScoreSubmission.fromBytes(forged); fail("expected refusal") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
    }

    @Test fun submissionsFromARunCarryTheDraftHashAndDays() {
        val m = run(listOf(1_000_000L, 1_000_000L), RunEnd.WON, wonOn = 250)
        val s = ScoreSubmission.of(d, "Ada", m, ScoreFormula.score(d, m))
        assertEquals(d.stateHash(), s.draftHash)
        assertEquals(250, s.days)
        assertTrue(s.won)
    }

    @Test fun playerNamesAreLimited() {
        for (bad in listOf("", "x".repeat(25))) {
            try { sub(player = bad); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
        }
    }

    @Test fun theBoardOrdersByScoreThenSpeedThenSeedThenName() {
        val ranked = Leaderboard.rank(listOf(sub("Cy", 100, 10), sub("Bo", 500, 300), sub("Al", 500, 200), sub("Di", 500, 200, seed = 0)))
        assertEquals(listOf("Di", "Al", "Bo", "Cy"), ranked.map { it.submission.player })
    }

    @Test fun equalScoresInEqualTimeShareAPosition() {
        val ranked = Leaderboard.rank(listOf(sub("A", 500, 200, 1), sub("B", 500, 200, 2), sub("C", 400, 100)))
        assertEquals(listOf(1, 1, 3), ranked.map { it.position })
    }

    @Test fun anEmptyBoardIsEmpty() {
        assertTrue(Leaderboard.rank(emptyList()).isEmpty())
    }

    @Test fun eachPlayerKeepsOnlyTheirBestScore() {
        val best = Leaderboard.bestPerPlayer(listOf(sub("A", 100), sub("A", 300), sub("B", 200), sub("A", 300, days = 100)))
        assertEquals(2, best.size)
        assertEquals(300L, best.first { it.player == "A" }.score)
        assertEquals(100, best.first { it.player == "A" }.days)
    }

    @Test fun theGoalCountMatchesTheFixture() {
        assertEquals(goals2, d.goals.size)
    }
}
