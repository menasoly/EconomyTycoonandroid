package app.many.economytycoon.credit

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditInputs
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.CreditScoring
import app.many.economytycoon.finance.Ratios
import app.many.economytycoon.finance.ScoreFactor
import app.many.economytycoon.finance.ScoreItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditExplainerTest {
    private fun ratios(
        cover: Long = 2_000_000, current: Long = 1_500_000, debtToEquity: Long = 800_000, margin: Long = 100_000, equity: Long = 450_000,
    ) = Ratios(
        currentRatio = Rate(current), cashRatio = Rate(500_000), debtToEquity = Rate(debtToEquity), interestCoverage = Rate(cover),
        grossMargin = Rate(300_000), netMargin = Rate(margin), returnOnEquity = Rate(100_000), returnOnAssets = Rate(50_000),
        debtToEbitda = Rate(2_000_000), equityRatio = Rate(equity),
    )

    private fun inputs(r: Ratios = ratios(), age: Long = 360 * 2, missed: Int = 0, arrears: Long = 0, revenue: Long = 100_000, worth: Long = 50_000) =
        CreditInputs(r, age, missed, Money.units(arrears), Money.units(revenue), Money.units(worth))

    private val strong = ratios(cover = 7_000_000, current = 3_500_000, debtToEquity = 100_000, margin = 350_000, equity = 700_000)
    private val weak = ratios(cover = 500_000, current = 500_000, debtToEquity = 5_000_000, margin = -200_000, equity = -100_000)

    @Test fun theBreakdownAddsUpToTheScore() {
        for (i in listOf(inputs(), inputs(strong, age = 3_600), inputs(weak, missed = 1, arrears = 20_000, worth = -5))) {
            val sum = CreditScoring.BASE + CreditScoring.breakdown(i).sumOf { it.points }
            assertEquals(sum.coerceIn(0, 1000), CreditScoring.score(i))
        }
    }

    @Test fun everyFactorAppearsOnceAndHasAdvice() {
        val e = CreditExplainer.explain(inputs(), CreditRating.BBB)
        assertEquals(ScoreFactor.values().toList(), e.factors.map { it.factor })
        assertTrue(e.factors.all { it.advice.length > 20 })
        assertEquals(ScoreFactor.values().size, ScoreFactor.values().map { it.label }.toSet().size)
    }

    @Test fun pointsStayBetweenWorstAndBest() {
        for (i in listOf(inputs(), inputs(strong, age = 99_999), inputs(weak, missed = 5, arrears = 900_000, worth = -1))) {
            for (item in CreditScoring.breakdown(i)) assertTrue(item.factor.name + " " + item.points, item.points in item.worst..item.best)
        }
    }

    @Test fun aStrongCompanyIsAtOrNearTheTop() {
        val e = CreditExplainer.explain(inputs(strong, age = 3_600), CreditRating.AA)
        assertTrue(e.score >= 790)
        assertTrue(e.earned.ordinal <= CreditRating.AA.ordinal)
        assertTrue(e.factors.count { it.state == FactorState.STRONG } >= 5)
    }

    @Test fun aWeakCompanyIsToldWhatDragsIt() {
        val e = CreditExplainer.explain(inputs(weak, missed = 1, arrears = 30_000, worth = -10), CreditRating.B)
        assertTrue(e.earned.ordinal >= CreditRating.B.ordinal)
        val drags = e.drags()
        assertEquals(3, drags.size)
        assertTrue(drags[0].points <= drags[1].points && drags[1].points <= drags[2].points)
        assertTrue(drags.all { it.points < 0 })
    }

    @Test fun leversAreTheFactorsWithMostToGain() {
        val e = CreditExplainer.explain(inputs(weak), CreditRating.B)
        val levers = e.levers()
        assertTrue(levers.isNotEmpty() && levers.size <= 3)
        assertEquals(levers.map { it.headroom }.sortedDescending(), levers.map { it.headroom })
        assertTrue(levers.none { it.state == FactorState.STRONG })
    }

    @Test fun nextUpNamesTheNextRatingAndThePointsNeeded() {
        val e = CreditExplainer.explain(inputs(), CreditRating.BBB)
        val up = e.nextUp!!
        assertEquals(e.earned.ordinal - 1, up.rating.ordinal)
        assertEquals(up.score - e.score, up.gap)
        assertTrue(up.gap > 0)
    }

    @Test fun cushionIsHowFarTheScoreIsAboveItsFloor() {
        val e = CreditExplainer.explain(inputs(), CreditRating.BBB)
        val floor = CreditScoring.thresholds.first { it.second == e.earned }.first
        assertEquals(e.score - floor, e.cushion)
        assertTrue(e.cushion!! >= 0)
    }

    @Test fun theBottomRatingHasNoFloorToFallThrough() {
        val e = CreditExplainer.explain(inputs(weak, missed = 3, arrears = 900_000, worth = -1, age = 0), CreditRating.D)
        assertEquals(CreditRating.D, e.earned)
        assertNull(e.cushion)
    }

    @Test fun theTopRatingHasNothingAbove() {
        val maxed = inputs(strong, age = 360 * 10, revenue = 1_000_000, worth = 9_000_000)
        val e = CreditExplainer.explain(maxed, CreditRating.AAA)
        if (e.earned == CreditRating.AAA) { assertNull(e.nextUp); assertTrue(e.atCeiling) }
        else assertFalse(e.atCeiling)
    }

    @Test fun aHeldRatingBelowTheScoreWillRiseAndOneAboveWillFall() {
        val good = inputs(strong, age = 3_600)
        val rising = CreditExplainer.explain(good, CreditRating.BBB)
        assertTrue(rising.willRise)
        assertFalse(rising.willFall)
        val falling = CreditExplainer.explain(inputs(weak), CreditRating.AA)
        assertTrue(falling.willFall)
        assertFalse(falling.willRise)
        val steady = CreditExplainer.explain(inputs(), CreditScoring.target(CreditScoring.score(inputs())))
        assertFalse(steady.willRise || steady.willFall)
    }

    @Test fun targetsMatchTheThresholdTable() {
        for ((floor, rating) in CreditScoring.thresholds) {
            assertEquals(rating, CreditScoring.target(floor))
            assertTrue(CreditScoring.target(floor - 1).ordinal > rating.ordinal)
        }
        assertEquals(CreditRating.D, CreditScoring.target(0))
        assertEquals(CreditRating.AAA, CreditScoring.target(1000))
    }

    @Test fun fillShowsWhereAFactorSitsInItsRange() {
        val worst = FactorView(ScoreItem(ScoreFactor.MARGIN, -80, 60, -80), FactorState.WEAK, "")
        val best = FactorView(ScoreItem(ScoreFactor.MARGIN, 60, 60, -80), FactorState.STRONG, "")
        assertEquals(0L, worst.fillPpm)
        assertEquals(1_000_000L, best.fillPpm)
        assertEquals(1_000_000L, FactorView(ScoreItem(ScoreFactor.AGE, 0, 0, 0), FactorState.STRONG, "").fillPpm)
    }

    @Test fun statesFollowTheThirdsOfTheRange() {
        fun s(points: Int) = CreditExplainer.stateOf(ScoreItem(ScoreFactor.MARGIN, points, 60, -60))
        assertEquals(FactorState.WEAK, s(-60))
        assertEquals(FactorState.WEAK, s(-30))
        assertEquals(FactorState.FAIR, s(-10))
        assertEquals(FactorState.STRONG, s(30))
        assertEquals(FactorState.STRONG, s(60))
        assertEquals(FactorState.STRONG, CreditExplainer.stateOf(ScoreItem(ScoreFactor.PAYMENTS, 0, 0, -210)))
    }

    @Test fun maxPossibleGainSumsTheHeadroom() {
        val e = CreditExplainer.explain(inputs(), CreditRating.BBB)
        assertEquals(e.factors.sumOf { it.headroom }, e.maxPossibleGain)
        assertTrue(e.maxPossibleGain > 0)
    }
}
