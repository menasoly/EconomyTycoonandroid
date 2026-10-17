package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.rule
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegimeTest {
    private val step = RegimeStep(
        2010, listOf(IndustryFamily.ENERGY, IndustryFamily.METALS), cap = Tonnes.of(1_000_000), capCutPpm = 20_000L, freeSharePpm = 400_000L,
        rate = Money.units(10), rateEscalatorPpm = 100_000L, benchmarkGramsPerUnit = 5_000L, benchmarkCutPpm = 100_000L,
    )

    @Test fun theCapFallsEveryYearSinceTheStepBegan() {
        assertEquals(Tonnes.of(1_000_000), step.capIn(2010))
        assertEquals(Tonnes.of(980_000), step.capIn(2011))
        assertEquals(Tonnes.of(960_400), step.capIn(2012))
        assertEquals(Tonnes.of(1_000_000), step.capIn(2005))
    }

    @Test fun freeAndAuctionedPermitsAddUpToTheCap() {
        for (y in 2010..2020) assertEquals(step.capIn(y), step.freeIn(y) + step.auctionIn(y))
        assertEquals(Tonnes.of(392_000), step.freeIn(2011))
    }

    @Test fun theRateEscalatesAndTheBenchmarkTightens() {
        assertEquals(Money.units(10), step.rateIn(2010))
        assertEquals(Money.units(11), step.rateIn(2011))
        assertEquals(5_000L, step.benchmarkIn(2010))
        assertEquals(4_500L, step.benchmarkIn(2011))
        assertEquals(4_050L, step.benchmarkIn(2012))
    }

    @Test fun aStepCoversOnlyItsFamilies() {
        assertTrue(step.covers(IndustryFamily.ENERGY))
        assertFalse(step.covers(IndustryFamily.FOOD))
    }

    @Test fun theLatestStepInForceIsTheOneUsed() {
        val later = step.copy(fromYear = 2015, cap = Tonnes.of(900_000))
        val r = CarbonRegime(uk, RegimeKind.CAP_AND_TRADE, listOf(step, later))
        assertEquals(null, r.stepFor(2009))
        assertEquals(step, r.stepFor(2014))
        assertEquals(later, r.stepFor(2015))
        assertTrue(r.covers(IndustryFamily.METALS, 2012))
        assertFalse(r.covers(IndustryFamily.METALS, 2005))
    }

    @Test fun nothingIsRegulatedWithoutAKindOrSteps() {
        assertFalse(CarbonRegime.none(uk).isRegulated)
        assertFalse(CarbonRegime(uk, RegimeKind.CARBON_TAX).isRegulated)
        assertTrue(RegimePresets.carbonTax(uk, 2000, Money.units(10)).isRegulated)
    }

    @Test fun priceDependsOnTheKind() {
        val permit = Money.units(33)
        assertEquals(Money.units(10), RegimePresets.carbonTax(uk, 2000, Money.units(10)).priceIn(2000, permit))
        assertEquals(permit, RegimePresets.capAndTrade(uk, 2000, Tonnes.of(1_000)).priceIn(2000, permit))
        assertEquals(Money.units(40), RegimePresets.intensityStandard(uk, 2000, 4_000L).priceIn(2000, permit))
        assertEquals(Money.ZERO, CarbonRegime.none(uk).priceIn(2000, permit))
        assertEquals(Money.ZERO, RegimePresets.carbonTax(uk, 2005, Money.units(10)).priceIn(2000, permit))
    }

    @Test fun expectedPriceRisesWithTheCapCutAndStaysUnderTheCeiling() {
        val r = RegimePresets.capAndTrade(uk, 2000, Tonnes.of(1_000), capCutPpm = 30_000L, ceiling = Money.units(30))
        val now = Money.units(20)
        assertEquals(now, r.expectedPrice(2000, now, 0))
        assertTrue(r.expectedPrice(2000, now, 3) > now)
        assertEquals(Money.units(30), r.expectedPrice(2000, now, 30))
    }

    @Test fun aTaxExpectsItsEscalatedRate() {
        val r = RegimePresets.carbonTax(uk, 2000, Money.units(10), escalatorPpm = 100_000L)
        assertEquals(Money.units(11), r.expectedPrice(2000, Money.units(10), 1))
    }

    @Test fun validationAcceptsEveryPreset() {
        listOf(
            RegimePresets.carbonTax(uk, 2000, Money.units(10)),
            RegimePresets.capAndTrade(uk, 2000, Tonnes.of(1_000)),
            RegimePresets.phasedCapAndTrade(uk, 2000, Tonnes.of(1_000_000)),
            RegimePresets.intensityStandard(uk, 2000, 4_000L),
            CarbonRegime.none(uk),
        ).forEach { assertTrue(it.validate() is Outcome.Ok) }
    }

    @Test fun validationRefusesIncoherentRules() {
        val ok = RegimePresets.capAndTrade(uk, 2000, Tonnes.of(1_000))
        val s = ok.steps.single()
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(steps = emptyList()).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(steps = listOf(s, s)).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(steps = listOf(s.copy(cap = Tonnes.ZERO))).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(steps = listOf(s.copy(ceiling = Money.units(1), floor = Money.units(5)))).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(steps = listOf(s.copy(freeSharePpm = 2_000_000L))).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(steps = listOf(s.copy(coverage = listOf(IndustryFamily.METALS, IndustryFamily.ENERGY)))).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(vintageLifeYears = 0).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(surrenderWindowDays = 400).validate().rule())
        assertEquals(Rules.INVALID_ARGUMENT, ok.copy(greenFundSharePpm = 2_000_000L).validate().rule())
        val tax = RegimePresets.carbonTax(uk, 2000, Money.units(10))
        assertEquals(Rules.INVALID_ARGUMENT, tax.copy(steps = listOf(tax.steps.single().copy(rate = Money.ZERO))).validate().rule())
        val levy = RegimePresets.intensityStandard(uk, 2000, 4_000L)
        assertEquals(Rules.INVALID_ARGUMENT, levy.copy(steps = listOf(levy.steps.single().copy(benchmarkGramsPerUnit = 0L))).validate().rule())
    }

    @Test fun strictnessChoosesTheInstrument() {
        assertEquals(RegimeKind.NONE, RegimePresets.forStrictness(uk, 100_000L, 2000).kind)
        assertEquals(RegimeKind.CARBON_TAX, RegimePresets.forStrictness(uk, 300_000L, 2000).kind)
        assertEquals(RegimeKind.INTENSITY_STANDARD, RegimePresets.forStrictness(uk, 600_000L, 2000).kind)
        assertEquals(RegimeKind.CAP_AND_TRADE, RegimePresets.forStrictness(uk, 900_000L, 2000).kind)
    }

    @Test fun thePhasedPresetWidensAndTightensInThreeSteps() {
        val r = RegimePresets.phasedCapAndTrade(uk, 2000, Tonnes.of(1_000_000))
        assertEquals(listOf(2000, 2003, 2006), r.steps.map { it.fromYear })
        assertTrue(r.steps[2].coverage.size > r.steps[0].coverage.size)
        assertTrue(r.steps[0].freeSharePpm > r.steps[2].freeSharePpm)
        assertTrue(r.steps[2].coversIndirect)
    }

    @Test fun onlyCapAndTradeTradesPermits() {
        assertTrue(RegimePresets.capAndTrade(uk, 2000, Tonnes.of(1)).tradesPermits)
        assertFalse(RegimePresets.carbonTax(uk, 2000, Money.units(1)).tradesPermits)
    }
}
