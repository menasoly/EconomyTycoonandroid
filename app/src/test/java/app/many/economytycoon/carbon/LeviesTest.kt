package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeviesTest {
    private val tax = RegimePresets.carbonTax(uk, 2000, Money.units(10), escalatorPpm = 0L, threshold = Tonnes.of(100))
    private val step = tax.steps.single()
    private val levy = RegimePresets.intensityStandard(uk, 2000, benchmarkGramsPerUnit = 10_000L, levy = Money.units(40), cutPpm = 0L, threshold = Tonnes.of(100))
    private val lstep = levy.steps.single()

    @Test fun belowTheThresholdNothingIsDue() {
        assertEquals(Money.ZERO, Levies.taxDue(step, 2000, Tonnes.of(99), Money.ZERO))
    }

    @Test fun crossingTheThresholdTaxesEverythingAtOnce() {
        assertEquals(Money.units(1_000), Levies.taxDue(step, 2000, Tonnes.of(100), Money.ZERO))
    }

    @Test fun whatWasAssessedIsNettedOff() {
        assertEquals(Money.units(500), Levies.taxDue(step, 2000, Tonnes.of(150), Money.units(1_000)))
        assertEquals(Money.ZERO, Levies.taxDue(step, 2000, Tonnes.of(150), Money.units(5_000)))
    }

    @Test fun theMonthlyAssessmentsSumToTheYearsTax() {
        var assessed = Money.ZERO
        var covered = Tonnes.ZERO
        repeat(12) {
            covered += Tonnes.of(40)
            assessed += Levies.taxDue(step, 2000, covered, assessed)
        }
        assertEquals(Tonnes.of(480).cost(Money.units(10)), assessed)
    }

    @Test fun theBenchmarkAllowsEmissionsInProportionToOutput() {
        assertEquals(Tonnes.of(100), Levies.allowed(lstep, 2000, Money.units(10_000)))
        assertEquals(Tonnes.ZERO, Levies.allowed(lstep, 2000, Money.ZERO))
    }

    @Test fun onlyTheExcessOverTheBenchmarkIsLevied() {
        val a = Levies.assess(lstep, 2000, Tonnes.of(160), Money.units(10_000))
        assertEquals(Tonnes.of(60), a.excess)
        assertEquals(Money.units(2_400), a.levy)
        assertFalse(a.withinBenchmark)
        val within = Levies.assess(lstep, 2000, Tonnes.of(100), Money.units(10_000))
        assertTrue(within.withinBenchmark)
        assertEquals(Tonnes.ZERO, within.headroom)
        assertEquals(Money.ZERO, within.levy)
    }

    @Test fun headroomIsTheRoomLeftUnderTheBenchmark() {
        assertEquals(Tonnes.of(40), Levies.assess(lstep, 2000, Tonnes.of(160), Money.units(20_000)).headroom)
    }

    @Test fun companiesBelowTheThresholdPayNoLevyEvenAboveTheBenchmark() {
        val a = Levies.assess(lstep, 2000, Tonnes.of(50), Money.units(100))
        assertEquals(Money.ZERO, a.levy)
    }

    @Test fun thePenaltyGrowsWithEachRepeatedViolationUpToAFiveStepLimit() {
        val r = RegimePresets.capAndTrade(uk, 2000, Tonnes.of(1_000), penalty = Money.units(100))
        val s = r.steps.single()
        val one = Levies.penalty(s, r, Tonnes.of(10), 0)
        val second = Levies.penalty(s, r, Tonnes.of(10), 1)
        val capped = Levies.penalty(s, r, Tonnes.of(10), 50)
        assertEquals(Money.units(1_000), one)
        assertEquals(Money.units(1_250), second)
        assertEquals(Money.units(2_250), capped)
        assertEquals(Money.ZERO, Levies.penalty(s, r, Tonnes.ZERO, 3))
    }
}
