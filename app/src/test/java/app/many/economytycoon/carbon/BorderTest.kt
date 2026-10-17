package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderTest {
    private val steel = GoodId("steel")
    private val policy = BorderPolicy(de, startYear = 2010, phaseInYears = 4, exportRebatePpm = 0L)
    private val flow = TradeFlow(steel, Quantity.units(1), uk, de, importer = acme, exporter = borea)

    @Test fun thePhaseInRisesInEqualStepsFromTheStartYear() {
        assertEquals(0L, policy.phasePpm(2009))
        assertEquals(250_000L, policy.phasePpm(2010))
        assertEquals(500_000L, policy.phasePpm(2011))
        assertEquals(1_000_000L, policy.phasePpm(2013))
        assertEquals(1_000_000L, policy.phasePpm(2030))
        assertEquals(0L, policy.copy(enabled = false).phasePpm(2020))
    }

    @Test fun anEmptyGoodListCoversAnythingDirtyEnough() {
        assertTrue(policy.covers(steel, 20_000L))
        assertFalse(policy.covers(steel, 19_999L))
        val listed = policy.copy(goods = listOf(GoodId("cement")))
        assertTrue(listed.covers(GoodId("cement"), 0L))
        assertFalse(listed.covers(steel, 999_999L))
    }

    @Test fun importsPayTheGapBetweenThePricesOnTheEmbeddedCarbon() {
        val a = BorderAdjuster.assess(flow, 2_000_000L, Money.units(5), Money.units(25), policy, null, 2013)
        assertEquals(Tonnes.of(2), a.embedded)
        assertEquals(Money.units(40), a.levy)
        assertEquals(Money.ZERO, a.rebate)
    }

    @Test fun thePhaseInScalesTheLevy() {
        val a = BorderAdjuster.assess(flow, 2_000_000L, Money.units(5), Money.units(25), policy, null, 2011)
        assertEquals(Money.units(20), a.levy)
        assertEquals(500_000L, a.phasePpm)
    }

    @Test fun noLevyWhenTheOriginPricesCarbonAtLeastAsHighly() {
        assertEquals(Money.ZERO, BorderAdjuster.assess(flow, 2_000_000L, Money.units(30), Money.units(25), policy, null, 2013).levy)
        assertEquals(Money.ZERO, BorderAdjuster.assess(flow, 2_000_000L, Money.units(25), Money.units(25), policy, null, 2013).levy)
    }

    @Test fun noLevyWithoutAPolicyOrBeforeItStarts() {
        assertEquals(Money.ZERO, BorderAdjuster.assess(flow, 2_000_000L, Money.ZERO, Money.units(25), null, null, 2013).levy)
        assertEquals(Money.ZERO, BorderAdjuster.assess(flow, 2_000_000L, Money.ZERO, Money.units(25), policy, null, 2005).levy)
    }

    @Test fun cleanGoodsBelowTheThresholdArePassedThrough() {
        assertEquals(Money.ZERO, BorderAdjuster.assess(flow, 5_000L, Money.ZERO, Money.units(25), policy, null, 2013).levy)
    }

    @Test fun tradeInsideACountryIsNeverAdjusted() {
        val domestic = flow.copy(from = de, to = de)
        val a = BorderAdjuster.assess(domestic, 2_000_000L, Money.ZERO, Money.units(25), policy, policy, 2013)
        assertEquals(Money.ZERO, a.levy)
        assertEquals(Money.ZERO, a.rebate)
    }

    @Test fun exportersFromAPricingCountryGetARebateWhenSellingToACheaperOne() {
        val exportPolicy = BorderPolicy(uk, startYear = 2010, phaseInYears = 1, exportRebatePpm = 500_000L)
        val reverse = flow.copy(from = uk, to = de)
        val a = BorderAdjuster.assess(reverse, 2_000_000L, Money.units(30), Money.units(10), null, exportPolicy, 2012)
        assertEquals(Money.units(20), a.rebate)
        assertEquals(Money.ZERO, a.levy)
    }

    @Test fun noRebateWhenTheRebateShareIsZero() {
        val exportPolicy = BorderPolicy(uk, startYear = 2010, phaseInYears = 1, exportRebatePpm = 0L)
        val a = BorderAdjuster.assess(flow.copy(from = uk, to = de), 2_000_000L, Money.units(30), Money.units(10), null, exportPolicy, 2012)
        assertEquals(Money.ZERO, a.rebate)
    }

    @Test fun aPolicyMustBeSane() {
        assertTrue(runCatching { BorderPolicy(de, startYear = 2000, phaseInYears = 0) }.isFailure)
        assertTrue(runCatching { BorderPolicy(de, startYear = 2000, exportRebatePpm = 2_000_000L) }.isFailure)
        assertTrue(runCatching { BorderPolicy(de, startYear = 2000, minIntensityGrams = -1L) }.isFailure)
    }

    @Test fun theBookTracksPoliciesAndTotals() {
        val book = BorderBook()
        book.set(policy)
        book.noteLevy(de, Money.units(10)); book.noteLevy(de, Money.units(5)); book.noteRebate(uk, Money.units(3))
        assertEquals(policy, book.policy(de))
        assertEquals(Money.units(15), book.levied(de))
        assertEquals(Money.units(3), book.rebated(uk))
        book.remove(de)
        assertEquals(null, book.policy(de))
    }
}
