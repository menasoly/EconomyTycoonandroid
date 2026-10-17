package app.many.economytycoon.talent

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EducationDemandTest {
    private val p = EducationParams()

    @Test fun schoolingIgnoresWagePremia() {
        assertEquals(850_000L, EducationDemand.participationPpm(InstitutionKind.SCHOOL, 2_000_000L, p))
        assertEquals(850_000L, EducationDemand.participationPpm(InstitutionKind.SCHOOL, 500_000L, p))
    }

    @Test fun collegeEnrolmentFollowsThePremium() {
        val par = EducationDemand.participationPpm(InstitutionKind.UNIVERSITY, Ppm.ONE, p)
        val rich = EducationDemand.participationPpm(InstitutionKind.UNIVERSITY, 1_400_000L, p)
        val poor = EducationDemand.participationPpm(InstitutionKind.UNIVERSITY, 700_000L, p)
        assertEquals(260_000L, par)
        assertTrue(rich > par && par > poor)
    }

    @Test fun premiaAreClampedSoDemandStaysSane() {
        val huge = EducationDemand.participationPpm(InstitutionKind.VOCATIONAL, 50_000_000L, p)
        val tiny = EducationDemand.participationPpm(InstitutionKind.VOCATIONAL, 0L, p)
        assertEquals(Ppm.mul(220_000L, p.maxPremiumPpm), huge)
        assertEquals(Ppm.mul(220_000L, p.minPremiumPpm), tiny)
    }

    @Test fun freePlacesAreFullyAffordable() {
        assertEquals(Ppm.ONE, EducationDemand.affordabilityPpm(Money.ZERO, Money.units(90), p))
    }

    @Test fun feesBiteAsTheyApproachYearlyPay() {
        val wage = Money.units(100)
        val cheap = EducationDemand.affordabilityPpm(Money.units(1_000), wage, p)
        val dear = EducationDemand.affordabilityPpm(Money.units(20_000), wage, p)
        val absurd = EducationDemand.affordabilityPpm(Money.units(500_000), wage, p)
        assertTrue(cheap > dear)
        assertEquals(p.minAffordabilityPpm, absurd)
        assertEquals(p.minAffordabilityPpm, EducationDemand.affordabilityPpm(Money.units(10), Money.ZERO, p))
    }

    @Test fun allocationSharesByWeightWithinFreeSeats() {
        val offers = listOf(SeatOffer(InstitutionId(1), 300L, 1_000L), SeatOffer(InstitutionId(2), 100L, 1_000L))
        val (placed, unmet) = EducationDemand.allocate(400L, offers)
        assertEquals(300L, placed[InstitutionId(1)])
        assertEquals(100L, placed[InstitutionId(2)])
        assertEquals(0L, unmet)
    }

    @Test fun aFullPlaceSpillsOverToTheOthers() {
        val offers = listOf(SeatOffer(InstitutionId(1), 900L, 50L), SeatOffer(InstitutionId(2), 100L, 1_000L))
        val (placed, unmet) = EducationDemand.allocate(400L, offers)
        assertEquals(50L, placed[InstitutionId(1)])
        assertEquals(350L, placed[InstitutionId(2)])
        assertEquals(0L, unmet)
    }

    @Test fun whenEverythingIsFullSomeAreLeftOut() {
        val offers = listOf(SeatOffer(InstitutionId(1), 1L, 30L), SeatOffer(InstitutionId(2), 1L, 20L))
        val (placed, unmet) = EducationDemand.allocate(100L, offers)
        assertEquals(50L, placed.values.sum())
        assertEquals(50L, unmet)
    }

    @Test fun allocationNeverExceedsSeekersOrSeats() {
        for (seekers in listOf(0L, 1L, 7L, 99L, 1_000L)) {
            val offers = listOf(SeatOffer(InstitutionId(1), 5L, 40L), SeatOffer(InstitutionId(2), 3L, 0L), SeatOffer(InstitutionId(3), 9L, 25L))
            val (placed, unmet) = EducationDemand.allocate(seekers, offers)
            assertEquals(seekers, placed.values.sum() + unmet)
            assertTrue(placed.getValue(InstitutionId(1)) <= 40L && placed.getValue(InstitutionId(3)) <= 25L)
            assertEquals(0L, placed.getValue(InstitutionId(2)))
        }
    }

    @Test fun noOffersMeansEveryoneIsUnplaced() {
        val (placed, unmet) = EducationDemand.allocate(30L, emptyList())
        assertTrue(placed.isEmpty())
        assertEquals(30L, unmet)
    }

    @Test fun fieldsShareAnIntakeByTheirPremium() {
        val split = EducationDemand.splitByField(100L, listOf(DefaultTaxonomy.ENGINEERING, DefaultTaxonomy.DESIGN)) { if (it == DefaultTaxonomy.ENGINEERING) 1_500_000L else 500_000L }
        assertEquals(75L, split[DefaultTaxonomy.ENGINEERING])
        assertEquals(25L, split[DefaultTaxonomy.DESIGN])
        assertTrue(EducationDemand.splitByField(0L, listOf(DefaultTaxonomy.DESIGN)) { Ppm.ONE }.isEmpty())
        assertTrue(EducationDemand.splitByField(10L, emptyList()) { Ppm.ONE }.isEmpty())
    }

    @Test fun graduateLevelFollowsQuality() {
        val avg = EducationDemand.graduateLevelPpm(InstitutionKind.UNIVERSITY, 500_000L, p)
        val best = EducationDemand.graduateLevelPpm(InstitutionKind.UNIVERSITY, 1_000_000L, p)
        val worst = EducationDemand.graduateLevelPpm(InstitutionKind.UNIVERSITY, 0L, p)
        assertEquals(700_000L, avg)
        assertTrue(best > avg && avg > worst)
        assertTrue(EducationDemand.graduateLevelPpm(InstitutionKind.SCHOOL, 0L, p) >= 100_000L)
    }

    @Test fun completionFallsWhenCrowded() {
        val roomy = EducationDemand.completionPpm(InstitutionKind.UNIVERSITY, 500_000L, 700_000L, p)
        val packed = EducationDemand.completionPpm(InstitutionKind.UNIVERSITY, 500_000L, 1_000_000L, p)
        assertTrue(roomy > packed)
        assertTrue(EducationDemand.completionPpm(InstitutionKind.SCHOOL, 1_000_000L, 0L, p) <= p.maxCompletionPpm)
        assertTrue(EducationDemand.completionPpm(InstitutionKind.UNIVERSITY, 0L, 1_000_000L, p) >= 300_000L)
    }

    @Test fun qualityTargetSaturatesWithSpend() {
        val bench = Money.units(1_000)
        val none = EducationDemand.qualityTargetPpm(Money.ZERO, bench, 0L, p)
        val par = EducationDemand.qualityTargetPpm(bench, bench, 0L, p)
        val double = EducationDemand.qualityTargetPpm(Money.units(2_000), bench, 0L, p)
        val huge = EducationDemand.qualityTargetPpm(Money.units(100_000), bench, 0L, p)
        assertEquals(0L, none)
        assertEquals(500_000L, par)
        assertTrue(double in 600_000L..700_000L)
        assertTrue(huge < Ppm.ONE && huge > double)
    }

    @Test fun crowdingDragsQualityDown() {
        val bench = Money.units(1_000)
        assertTrue(EducationDemand.qualityTargetPpm(bench, bench, 1_000_000L, p) < EducationDemand.qualityTargetPpm(bench, bench, 800_000L, p))
    }

    @Test fun benchmarkScalesWithLocalPay() {
        val base = EducationDemand.benchmarkSpend(InstitutionKind.UNIVERSITY, Money.units(90), p)
        val rich = EducationDemand.benchmarkSpend(InstitutionKind.UNIVERSITY, Money.units(180), p)
        assertEquals(Money.units(4_500), base)
        assertEquals(Money.units(9_000), rich)
    }
}
