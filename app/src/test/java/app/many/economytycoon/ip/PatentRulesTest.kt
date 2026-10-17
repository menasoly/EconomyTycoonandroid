package app.many.economytycoon.ip

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatentRulesTest {
    private val office = OfficeParams()
    private val balanced = RegimeKind.BALANCED.regime
    private val strict = RegimeKind.PATENT_SKEPTIC.regime
    private val def = IpTestSupport.content.technology(IpTestSupport.ovens)

    @Test fun applicationFeeIncludesTheFirstClaimsAndChargesForExtras() {
        val base = PatentRules.applicationFee(office, balanced, 10)
        assertEquals(Money.units(4_000 + 1_500 + 2_500), base)
        assertEquals(base + Money.units(150 * 5), PatentRules.applicationFee(office, balanced, 15))
        assertEquals(base, PatentRules.applicationFee(office, balanced, 1))
    }

    @Test fun regimeMultipliesFees() {
        val cheap = PatentRules.applicationFee(office, RegimeKind.DEVELOPING.regime, 10)
        val dear = PatentRules.applicationFee(office, RegimeKind.PRO_PATENT.regime, 10)
        assertTrue(cheap < PatentRules.applicationFee(office, balanced, 10))
        assertTrue(dear > PatentRules.applicationFee(office, balanced, 10))
        assertEquals(Money.units(8_000).times(app.many.economytycoon.core.Rate(500_000)), cheap)
    }

    @Test fun maintenanceFeesClimbEveryYear() {
        var last = Money.ZERO
        for (y in 1..10) {
            val fee = PatentRules.maintenanceFee(office, balanced, y)
            assertTrue(fee > last)
            last = fee
        }
        assertEquals(Money.units(800), PatentRules.maintenanceFee(office, balanced, 1))
        assertEquals(Money.units(800 + 800 * 180 / 1000), PatentRules.maintenanceFee(office, balanced, 2))
    }

    @Test fun lifetimeMaintenanceSumsTheYearsHeld() {
        val sum = (1..4).fold(Money.ZERO) { a, y -> a + PatentRules.maintenanceFee(office, balanced, y) }
        assertEquals(sum, PatentRules.lifetimeMaintenance(office, balanced, false, 4))
        assertEquals(PatentRules.lifetimeMaintenance(office, balanced, false, 19), PatentRules.lifetimeMaintenance(office, balanced, false, 40))
    }

    @Test fun pharmaceuticalPatentsGetAnExtension() {
        assertEquals(20, PatentRules.termYears(balanced, false))
        assertEquals(22, PatentRules.termYears(balanced, true))
        assertEquals(17, PatentRules.termYears(strict, true))
        assertEquals(22L * 360, PatentRules.termTicks(balanced, true))
    }

    @Test fun noveltyRisesWithTier() {
        val low = PatentRules.novelty(office, def.copy(tier = 1), 0, false, 0, 0)
        val high = PatentRules.novelty(office, def.copy(tier = 3), 0, false, 0, 0)
        assertTrue(high > low)
    }

    @Test fun crowdingAndRivalsAndPublishedPatentsLowerNovelty() {
        val clean = PatentRules.novelty(office, def, 0, false, 1, 0)
        assertTrue(PatentRules.novelty(office, def, 10_000_000, false, 1, 0) < clean)
        assertTrue(PatentRules.novelty(office, def, 0, false, 4, 0) < clean)
        assertTrue(PatentRules.novelty(office, def, 0, true, 1, 0) < clean)
    }

    @Test fun beingFirstEarnsABonus() {
        assertTrue(PatentRules.novelty(office, def, 0, false, 0, 0) > PatentRules.novelty(office, def, 0, false, 1, 0))
    }

    @Test fun noveltyIsClamped() {
        assertEquals(30_000L, PatentRules.novelty(office, def, 1_000_000_000, true, 50, -60_000))
        assertEquals(980_000L, PatentRules.novelty(office, def.copy(tier = 20), 0, false, 0, 60_000))
    }

    @Test fun jitterMovesNoveltyBothWays() {
        val mid = PatentRules.novelty(office, def, 0, false, 1, 0)
        assertEquals(mid + 30_000, PatentRules.novelty(office, def, 0, false, 1, 30_000))
        assertEquals(mid - 30_000, PatentRules.novelty(office, def, 0, false, 1, -30_000))
    }

    @Test fun broadClaimsAreHarderToGet() {
        val narrow = PatentRules.grantProbability(office, balanced, 600_000, BreadthChoice.NARROW.breadthPpm)
        val broad = PatentRules.grantProbability(office, balanced, 600_000, BreadthChoice.BROAD.breadthPpm)
        assertTrue(narrow > broad)
    }

    @Test fun novelInventionsAreEasierToGet() {
        assertTrue(PatentRules.grantProbability(office, balanced, 800_000, 550_000) > PatentRules.grantProbability(office, balanced, 300_000, 550_000))
    }

    @Test fun strictOfficesGrantLess() {
        assertTrue(PatentRules.grantProbability(office, strict, 600_000, 550_000) < PatentRules.grantProbability(office, balanced, 600_000, 550_000))
    }

    @Test fun grantProbabilityStaysWithinBounds() {
        assertEquals(office.maxGrantPpm, PatentRules.grantProbability(office, RegimeKind.PRO_PATENT.regime, 980_000, 0))
        assertEquals(office.minGrantPpm, PatentRules.grantProbability(office, strict, 30_000, 1_000_000))
    }

    @Test fun backlogLengthensExaminationButIsCapped() {
        val empty = PatentRules.examinationDays(office, balanced, 0, 0)
        val busy = PatentRules.examinationDays(office, balanced, 20, 0)
        val flooded = PatentRules.examinationDays(office, balanced, 10_000, 0)
        assertEquals(240, empty)
        assertEquals(280, busy)
        assertEquals(240 + 300, flooded)
    }

    @Test fun slowOfficesTakeLonger() {
        assertTrue(PatentRules.examinationDays(office, RegimeKind.DEVELOPING.regime, 0, 0) > PatentRules.examinationDays(office, balanced, 0, 0))
    }

    @Test fun rollsFallIntoThreeBands() {
        val p = PatentRules.grantProbability(office, balanced, 600_000, 550_000)
        assertTrue(PatentRules.examine(office, balanced, 600_000, 550_000, p - 1) is ExamResult.Granted)
        val narrowed = PatentRules.examine(office, balanced, 600_000, 550_000, p)
        assertTrue(narrowed is ExamResult.Narrowed)
        assertEquals(330_000L, (narrowed as ExamResult.Narrowed).breadthPpm)
        assertTrue(PatentRules.examine(office, balanced, 600_000, 550_000, p + office.amendBandPpm - 1) is ExamResult.Narrowed)
        assertTrue(PatentRules.examine(office, balanced, 600_000, 550_000, p + office.amendBandPpm) is ExamResult.Refused)
    }

    @Test fun fieldsFollowTechCategories() {
        assertEquals(TechField.COMPUTING, TechFields.of(TechCategory.DIGITAL))
        assertEquals(TechField.MANUFACTURING, TechFields.of(def.copy(category = TechCategory.PRODUCTION)))
        assertEquals(8, TechCategory.values().map { TechFields.of(it) }.toSet().size)
    }

    @Test fun affinityIsSymmetricAndMaximalOnTheDiagonal() {
        for (a in TechField.values()) {
            assertEquals(1_000_000L, TechFields.affinityPpm(a, a))
            for (b in TechField.values()) assertEquals(TechFields.affinityPpm(a, b), TechFields.affinityPpm(b, a))
        }
        assertEquals(0L, TechFields.affinityPpm(TechField.ENERGY_SYSTEMS, TechField.FINANCIAL_METHODS))
    }
}
