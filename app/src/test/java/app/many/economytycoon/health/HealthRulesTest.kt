package app.many.economytycoon.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRulesTest {
    private fun sample(
        tick: Long = 0, supply: Long = 1_000_000, households: Long = 500_000, ai: Int = 6, strained: Int = 0, broke: Int = 0,
        facilities: Int = 20, operating: Int = 18, markets: Int = 100, cheap: Int = 0, dear: Int = 0, median: Long = 1_000_000,
    ) = HealthSample(tick, supply, households, 300_000, 100_000, ai, strained, broke, facilities, operating, 1, markets, cheap, dear, median, null, null)

    private fun codes(vararg s: HealthSample, limits: HealthLimits = HealthLimits()) = HealthRules.check(s.toList(), limits).map { it.code }

    @Test fun aQuietHealthyWorldHasNothingToReport() {
        assertEquals(emptyList<String>(), codes(sample(), sample(tick = 360)))
    }

    @Test fun noSamplesNoIssues() {
        assertEquals(emptyList<HealthIssue>(), HealthRules.check(emptyList()))
    }

    @Test fun manyStrainedRivalsAreAWarningAndMostAreAProblem() {
        val warn = HealthRules.check(listOf(sample(strained = 3)))
        assertEquals(listOf("ai.strained"), warn.map { it.code })
        assertEquals(HealthSeverity.WARNING, warn.single().severity)
        val problem = HealthRules.check(listOf(sample(strained = 5)))
        assertEquals(listOf("ai.broke"), problem.map { it.code })
        assertEquals(HealthSeverity.PROBLEM, problem.single().severity)
    }

    @Test fun everyRivalOutOfCashIsReportedAsEmpty() {
        assertTrue(codes(sample(strained = 6, broke = 6)).contains("ai.empty"))
    }

    @Test fun idleRivalPlantsAreFlagged() {
        assertTrue(codes(sample(operating = 5)).contains("ai.idle"))
        assertFalse(codes(sample(facilities = 0, operating = 0)).contains("ai.idle"))
    }

    @Test fun marketsStuckAtTheFloorOrTheCeilingAreFlagged() {
        assertTrue(codes(sample(cheap = 40)).contains("price.floor"))
        assertTrue(codes(sample(dear = 40)).contains("price.runaway"))
        assertFalse(codes(sample(cheap = 10, dear = 10)).any { it.startsWith("price.") })
    }

    @Test fun priceDriftNeedsAYearlyPace() {
        assertFalse(codes(sample(), sample(tick = 60, median = 1_900_000)).contains("price.drift"))
        assertTrue(codes(sample(), sample(tick = 180, median = 2_000_000)).contains("price.drift"))
        assertTrue(codes(sample(), sample(tick = 180, median = 100_000)).contains("price.drift"))
    }

    @Test fun moneySupplyGrowthIsReportedOverALongEnoughSpan() {
        assertTrue(codes(sample(), sample(tick = 360, supply = 4_000_000)).contains("money.growth"))
        assertFalse(codes(sample(), sample(tick = 360, supply = 1_100_000)).contains("money.growth"))
        assertFalse(codes(sample(), sample(tick = 30, supply = 9_000_000)).contains("money.growth"))
    }

    @Test fun householdsWithNoCashAreAProblem() {
        val issues = HealthRules.check(listOf(sample(households = 0)))
        assertEquals(HealthSeverity.PROBLEM, issues.single { it.code == "households.broke" }.severity)
    }

    @Test fun limitsCanBeLoosened() {
        val loose = HealthLimits(strainedShareWarn = 900_000L, strainedShareProblem = 950_000L)
        assertEquals(emptyList<String>(), codes(sample(strained = 5), limits = loose))
    }

    @Test fun sharesHandleZeroDenominators() {
        val s = sample(ai = 0, facilities = 0, operating = 0, markets = 0)
        assertEquals(0L, s.aiStrainedShare)
        assertEquals(1_000_000L, s.aiOperatingShare)
        assertEquals(0L, s.cheapShare)
        assertEquals(0L, s.dearShare)
    }
}
