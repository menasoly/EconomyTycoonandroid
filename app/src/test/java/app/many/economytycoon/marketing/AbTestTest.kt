package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.audiences
import app.many.economytycoon.marketing.MarketingTestSupport.bank
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.rejection
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbTestTest {
    private val params = MarketingParams()
    private val s = MarketingTestSupport.system(seed = 99L)

    private fun finished(qualityA: Long, qualityB: Long, impressions: Long, id: Long = 1L, seed: Long = 99L): AbTest {
        val t = AbTest(AbTestId(id), CampaignId(1), qualityA, qualityB, Tick(0), 10)
        t.impressions = impressions
        AbTesting.resolve(t, seed, params)
        return t
    }

    @Test fun betterQualityMeansAHigherResponseRate() {
        assertTrue(AbTesting.responseRatePpm(800_000L, params) > AbTesting.responseRatePpm(400_000L, params))
        assertEquals(AbTesting.BASE_RESPONSE_PPM, AbTesting.responseRatePpm(500_000L, params))
    }

    @Test fun aClearlyBetterCreativeWinsWithEnoughViewers() {
        val t = finished(450_000L, 750_000L, 2_000_000L)
        assertEquals(AbOutcome.B_WINS, t.outcome)
        assertTrue(t.zHundredths > 164L)
        assertTrue(t.responsesB > t.responsesA)
    }

    @Test fun aClearlyWorseChallengerLoses() {
        val t = finished(750_000L, 450_000L, 2_000_000L)
        assertEquals(AbOutcome.A_WINS, t.outcome)
        assertTrue(t.zHundredths < -164L)
    }

    @Test fun aTinyAudienceCannotSeparateTwoCreatives() {
        val t = finished(600_000L, 650_000L, 50L)
        assertEquals(AbOutcome.INCONCLUSIVE, t.outcome)
    }

    @Test fun tooFewResponsesNeverDecideAnything() {
        val strict = params.copy(abMinResponses = 1_000_000L)
        val t = AbTest(AbTestId(1), CampaignId(1), 300_000L, 900_000L, Tick(0), 10)
        t.impressions = 2_000_000L
        assertEquals(AbOutcome.INCONCLUSIVE, AbTesting.resolve(t, 99L, strict))
    }

    @Test fun noViewersMeansNoVerdict() {
        val t = finished(600_000L, 900_000L, 0L)
        assertEquals(AbOutcome.INCONCLUSIVE, t.outcome)
        assertEquals(0L, t.responsesA + t.responsesB)
    }

    @Test fun theVerdictIsReproducible() {
        val a = finished(500_000L, 560_000L, 300_000L)
        val b = finished(500_000L, 560_000L, 300_000L)
        assertEquals(a.responsesA, b.responsesA)
        assertEquals(a.responsesB, b.responsesB)
        assertEquals(a.outcome, b.outcome)
    }

    @Test fun differentSeedsDrawDifferentNoise() {
        val draws = (1L..8L).map { finished(500_000L, 500_000L, 5_000_000L, seed = it).responsesA }.toSet()
        assertTrue("draws were $draws", draws.size > 1)
    }

    @Test fun differentTestsOfTheSameGameDrawDifferentNoise() {
        val draws = (1L..8L).map { finished(500_000L, 500_000L, 5_000_000L, id = it).responsesA }.toSet()
        assertTrue(draws.size > 1)
    }

    @Test fun identicalCreativesAreMostlyInconclusive() {
        val verdicts = (1L..12L).map { finished(600_000L, 600_000L, 800_000L, id = it).outcome }
        assertTrue("verdicts $verdicts", verdicts.count { it == AbOutcome.INCONCLUSIVE } >= 9)
    }

    @Test fun zScoreIsZeroWithNoViewersAndSignedByTheBetterArm() {
        assertEquals(0L, AbTesting.zHundredths(0L, 5L, 9L))
        assertTrue(AbTesting.zHundredths(10_000L, 100L, 160L) > 0L)
        assertTrue(AbTesting.zHundredths(10_000L, 160L, 100L) < 0L)
        assertEquals(0L, AbTesting.zHundredths(10_000L, 120L, 120L))
    }

    @Test fun smallerGapsNeedMoreViewers() {
        val wide = AbTesting.impressionsNeeded(400_000L, 800_000L, params)
        val narrow = AbTesting.impressionsNeeded(500_000L, 550_000L, params)
        assertTrue(narrow > wide)
        assertEquals(Long.MAX_VALUE, AbTesting.impressionsNeeded(500_000L, 500_000L, params))
    }

    // --- service ---

    @Test fun startingATestCostsTheAlternativeCreative() {
        val c = book(s)
        val launch = s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), bank).getOrThrow()
        assertEquals(Money.units(500 * 6), launch.intents.single().total)
        assertEquals(c.creative.qualityPpm, launch.test.qualityA)
        assertEquals(CreativeTier.STUDIO.qualityPpm, launch.test.qualityB)
        assertEquals(launch.test, s.state.testFor(c.id))
    }

    @Test fun testsHaveBoundedLengthAndFitInsideTheCampaign() {
        val c = book(s, request(days = 8))
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.experiments.start(c.id, CreativeTier.STUDIO, 2, Tick(0), bank)))
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.experiments.start(c.id, CreativeTier.STUDIO, 50, Tick(0), bank)))
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.experiments.start(c.id, CreativeTier.STUDIO, 12, Tick(0), bank)))
    }

    @Test fun onlyOneTestAtATimeAndNeverTheSameCreative() {
        val c = book(s)
        s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), bank).getOrThrow()
        assertEquals(Rules.DUPLICATE, rejection(s.experiments.start(c.id, CreativeTier.BASIC, 10, Tick(0), bank)))
        val other = book(s, request(channel = Channel.ONLINE))
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.experiments.start(other.id, CreativeTier.PROFESSIONAL, 10, Tick(0), bank)))
    }

    @Test fun testsNeedCashAndARunningCampaign() {
        val c = book(s)
        assertEquals(Rules.INSUFFICIENT_FUNDS, rejection(s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), Money.units(5))))
        s.campaigns.pause(c.id)
        assertEquals(Rules.WRONG_STATE, rejection(s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), bank)))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(s.experiments.start(CampaignId(77), CreativeTier.STUDIO, 10, Tick(0), bank)))
    }

    @Test fun theEngineDecidesTheTestOnItsLastDay() {
        val c = book(s, request(daily = 4_000, channel = Channel.ONLINE, days = 40, tier = CreativeTier.BASIC))
        val t = s.experiments.start(c.id, CreativeTier.STUDIO, 12, Tick(0), bank).getOrThrow().test
        val reports = run(s, 0, 14)
        assertTrue(reports[10].events.none { it is MarketingEvent.TestDecided })
        assertTrue(reports[11].events.any { it is MarketingEvent.TestDecided })
        assertTrue(!t.isRunning)
        assertEquals(Tick(11), t.decidedAt)
        assertTrue(t.impressions > 0L)
    }

    @Test fun theWinnerBecomesTheCampaignCreative() {
        val c = book(s, request(daily = 4_000, channel = Channel.ONLINE, days = 40, tier = CreativeTier.BASIC))
        val t = s.experiments.start(c.id, CreativeTier.STUDIO, 12, Tick(0), bank).getOrThrow().test
        run(s, 0, 13)
        assertEquals(AbOutcome.B_WINS, t.outcome)
        assertEquals(CreativeTier.STUDIO.qualityPpm, c.creative.qualityPpm)
        assertEquals(2, c.creative.version)
        assertEquals(Tick(11), c.creative.lastRefreshed)
        assertTrue(c.creative.freshnessPpm > 900_000L)
    }

    @Test fun aLosingChallengerLeavesTheCreativeAlone() {
        val c = book(s, request(daily = 4_000, channel = Channel.ONLINE, days = 40, tier = CreativeTier.STUDIO))
        val t = s.experiments.start(c.id, CreativeTier.BASIC, 12, Tick(0), bank).getOrThrow().test
        run(s, 0, 13)
        assertEquals(AbOutcome.A_WINS, t.outcome)
        assertEquals(CreativeTier.STUDIO.qualityPpm, c.creative.qualityPpm)
        assertEquals(1, c.creative.version)
    }

    @Test fun sameSeedSameGameSameVerdict() {
        val results = (1..2).map {
            val sys = MarketingTestSupport.system(seed = 31L)
            val c = book(sys, request(daily = 600, channel = Channel.ONLINE, days = 40, tier = CreativeTier.BASIC))
            val t = sys.experiments.start(c.id, CreativeTier.PROFESSIONAL, 10, Tick(0), bank).getOrThrow().test
            run(sys, 0, 12)
            Triple(t.outcome, t.responsesA, t.responsesB)
        }
        assertEquals(results[0], results[1])
    }

    @Test fun cancellingATestStopsIt() {
        val c = book(s)
        val t = s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), bank).getOrThrow().test
        s.experiments.cancel(t.id, Tick(2)).getOrThrow()
        assertEquals(AbOutcome.CANCELLED, t.outcome)
        run(s, 3, 12)
        assertEquals(AbOutcome.CANCELLED, t.outcome)
        assertEquals(Rules.WRONG_STATE, rejection(s.experiments.cancel(t.id, Tick(3))))
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(s.experiments.cancel(AbTestId(55), Tick(3))))
    }

    @Test fun aTestOnACancelledCampaignIsClosed() {
        val c = book(s)
        val t = s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), bank).getOrThrow().test
        s.campaigns.cancel(c.id, Tick(1))
        run(s, 1, 1)
        assertEquals(AbOutcome.CANCELLED, t.outcome)
    }

    @Test fun daysNeededShrinksWithBiggerSpend() {
        val small = book(s, request(daily = 100, channel = Channel.ONLINE))
        val bigSys = MarketingTestSupport.system()
        val big = book(bigSys, request(daily = 20_000, channel = Channel.ONLINE))
        val smallDays = s.experiments.daysNeeded(small, CreativeTier.STUDIO, audiences())
        val bigDays = bigSys.experiments.daysNeeded(big, CreativeTier.STUDIO, audiences())
        assertNotNull(smallDays); assertNotNull(bigDays)
        assertTrue(smallDays!! > bigDays!!)
    }

    @Test fun daysNeededIsUnknownWithoutAnAudienceOrADifference() {
        val c = book(s)
        assertNull(s.experiments.daysNeeded(c, CreativeTier.STUDIO, FixedAudienceSource(emptyList())))
        val same = CreativeTier.values().firstOrNull { it.qualityPpm == c.creative.qualityPpm }
        assertNotNull(same)
        assertNull(s.experiments.daysNeeded(c, same!!, audiences()))
    }

    @Test fun successfulStartIsAnOk() {
        val c = book(s)
        assertTrue(s.experiments.start(c.id, CreativeTier.STUDIO, 10, Tick(0), bank) is Outcome.Ok)
    }
}
