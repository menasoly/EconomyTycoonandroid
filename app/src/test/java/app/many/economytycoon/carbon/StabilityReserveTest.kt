package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityReserveTest {
    private val rule = ReserveRule(upperPpm = 1_200_000L, lowerPpm = 400_000L, intakePpm = 240_000L, releasePpm = 100_000L, maxStockPpm = 500_000L)
    private val cap = Tonnes.of(1_000)

    @Test fun aBalancedMarketIsLeftAlone() {
        val d = StabilityReserve.review(rule, Tonnes.of(800), cap, Tonnes.ZERO, Tonnes.of(500))
        assertTrue(d.isIdle)
    }

    @Test fun tooManyPermitsInCirculationAreWithheldFromAuction() {
        val d = StabilityReserve.review(rule, Tonnes.of(1_500), cap, Tonnes.ZERO, Tonnes.of(500))
        assertEquals(Tonnes.of(360), d.withhold)
        assertEquals(Tonnes.ZERO, d.release)
    }

    @Test fun whatIsWithheldCannotExceedWhatIsLeftToAuction() {
        val d = StabilityReserve.review(rule, Tonnes.of(1_500), cap, Tonnes.ZERO, Tonnes.of(100))
        assertEquals(Tonnes.of(100), d.withhold)
    }

    @Test fun tooFewPermitsReleaseFromTheReserve() {
        val d = StabilityReserve.review(rule, Tonnes.of(300), cap, Tonnes.of(250), Tonnes.of(500))
        assertEquals(Tonnes.of(100), d.release)
        assertEquals(Tonnes.ZERO, d.withhold)
    }

    @Test fun aReleaseNeverExceedsTheStock() {
        val d = StabilityReserve.review(rule, Tonnes.of(300), cap, Tonnes.of(40), Tonnes.of(500))
        assertEquals(Tonnes.of(40), d.release)
    }

    @Test fun stockAboveTheMaximumIsCancelled() {
        val d = StabilityReserve.review(rule, Tonnes.of(1_500), cap, Tonnes.of(400), Tonnes.of(500))
        assertEquals(Tonnes.of(360), d.withhold)
        assertEquals(Tonnes.of(260), d.cancel)
    }

    @Test fun noCapMeansNoAction() {
        assertTrue(StabilityReserve.review(rule, Tonnes.of(1_500), Tonnes.ZERO, Tonnes.of(400), Tonnes.of(500)).isIdle)
    }

    private fun prices(vararg p: Long) = p.map { Money.units(it) }

    @Test fun aPriceSpikeReleasesPermitsOnceThereIsHistory() {
        val spike = StabilityReserve.priceRelease(rule, cap, Tonnes.of(100), prices(10, 10, 10, 10, 10, 25))
        assertEquals(Tonnes.of(40), spike)
    }

    @Test fun aPriceBelowTheTriggerReleasesNothing() {
        assertEquals(Tonnes.ZERO, StabilityReserve.priceRelease(rule, cap, Tonnes.of(100), prices(10, 10, 10, 10, 10, 15)))
    }

    @Test fun tooLittleHistoryOrAnEmptyReserveReleasesNothing() {
        assertEquals(Tonnes.ZERO, StabilityReserve.priceRelease(rule, cap, Tonnes.of(100), prices(10, 10, 30)))
        assertEquals(Tonnes.ZERO, StabilityReserve.priceRelease(rule, cap, Tonnes.ZERO, prices(10, 10, 10, 10, 10, 90)))
    }

    @Test fun priceReleaseIsLimitedByTheStock() {
        assertEquals(Tonnes.of(10), StabilityReserve.priceRelease(rule, cap, Tonnes.of(10), prices(10, 10, 10, 10, 10, 90)))
    }
}

class FreeAllocationTest {
    @Test fun thePoolIsSplitInProportionToBaselineEmissions() {
        val parts = FreeAllocation.distribute(Tonnes.of(100), mapOf(acme to 600L, borea to 300L, cyrus to 100L))
        assertEquals(Tonnes.of(60), parts[acme])
        assertEquals(Tonnes.of(30), parts[borea])
        assertEquals(Tonnes.of(10), parts[cyrus])
    }

    @Test fun thePartsAlwaysAddUpToThePool() {
        val pool = Tonnes.ofKg(10_001)
        val parts = FreeAllocation.distribute(pool, mapOf(acme to 1L, borea to 1L, cyrus to 1L))
        assertEquals(pool, Tonnes.sum(parts.values))
    }

    @Test fun companiesWithoutBaselineGetNothing() {
        val parts = FreeAllocation.distribute(Tonnes.of(100), mapOf(acme to 5L, borea to 0L, cyrus to -3L))
        assertEquals(setOf(acme), parts.keys)
        assertEquals(Tonnes.of(100), parts[acme])
    }

    @Test fun nobodyEligibleOrAnEmptyPoolGivesAnEmptyResult() {
        assertTrue(FreeAllocation.distribute(Tonnes.of(100), emptyMap()).isEmpty())
        assertTrue(FreeAllocation.distribute(Tonnes.ZERO, mapOf(acme to 5L)).isEmpty())
    }

    @Test fun theSplitDoesNotDependOnInsertionOrder() {
        val a = FreeAllocation.distribute(Tonnes.ofKg(7_777), linkedMapOf(acme to 3L, borea to 5L, cyrus to 7L))
        val b = FreeAllocation.distribute(Tonnes.ofKg(7_777), linkedMapOf(cyrus to 7L, borea to 5L, acme to 3L))
        assertEquals(a, b)
    }
}
