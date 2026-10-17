package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSpendingTest {
    private val perHead = Money.units(25)

    @Test fun categorySharesSumToOneForEverySegment() {
        for (s in VisitorSegment.values()) assertEquals(1_000_000L, LocalSpending.shares(s).sum())
    }

    @Test fun luxuryGuestsSpendMostAndShopMost() {
        assertTrue(LocalSpending.perGuestDay(VisitorSegment.LUXURY, 1_000_000L) > LocalSpending.perGuestDay(VisitorSegment.BUDGET, 1_000_000L))
        assertTrue(LocalSpending.shares(VisitorSegment.LUXURY)[SpendCategory.SHOPS.ordinal] > LocalSpending.shares(VisitorSegment.BUDGET)[SpendCategory.SHOPS.ordinal])
    }

    @Test fun priceLevelScalesSpendingAndNegativeLevelsAreZero() {
        assertEquals(VisitorSegment.FAMILY.profile.dailySpend, LocalSpending.perGuestDay(VisitorSegment.FAMILY, 1_000_000L))
        assertEquals(VisitorSegment.FAMILY.profile.dailySpend * 2L, LocalSpending.perGuestDay(VisitorSegment.FAMILY, 2_000_000L))
        assertEquals(Money.ZERO, LocalSpending.perGuestDay(VisitorSegment.FAMILY, -5L))
    }

    @Test fun breakdownAddsUpToTheSpendOfAllGuests() {
        val guests = longArrayOf(1_000L, 500L, 200L, 50L)
        val b = LocalSpending.breakdown(guests, 1_000_000L)
        val expected = VisitorSegment.values().sumOf { LocalSpending.perGuestDay(it, 1_000_000L).raw * guests[it.ordinal] }
        assertEquals(expected, b.total.raw)
    }

    @Test fun noGuestsNoSpending() {
        assertEquals(Money.ZERO, LocalSpending.breakdown(LongArray(4), 1_000_000L).total)
        assertEquals(Money.ZERO, LocalSpending.breakdown(longArrayOf(-5L, 0L), 1_000_000L).total)
    }

    @Test fun breakdownCategoriesAreNonNegativeAndRestaurantsLeadForBudget() {
        val b = LocalSpending.breakdown(longArrayOf(1_000L, 0L, 0L, 0L), 1_000_000L)
        assertTrue(SpendCategory.values().all { b[it].raw >= 0L })
        assertTrue(b[SpendCategory.RESTAURANTS] > b[SpendCategory.ENTERTAINMENT])
        assertEquals(SpendCategory.values().size, b.toMap().size)
    }

    @Test fun residentSpendUsesTheCategoryShare() {
        assertEquals(Money.units(25 * 10_000 * 3 / 10), LocalSpending.residentSpend(SpendCategory.RESTAURANTS, 10_000L, perHead))
        assertEquals(Money.ZERO, LocalSpending.residentSpend(SpendCategory.SHOPS, 0L, perHead))
    }

    @Test fun upliftIsTouristSpendOverResidentSpendAndCapped() {
        assertEquals(500_000L, LocalSpending.upliftPpm(Money.units(50), Money.units(100)))
        assertEquals(0L, LocalSpending.upliftPpm(Money.ZERO, Money.units(100)))
        assertEquals(LocalSpending.MAX_UPLIFT_PPM, LocalSpending.upliftPpm(Money.units(50), Money.ZERO))
        assertEquals(LocalSpending.MAX_UPLIFT_PPM, LocalSpending.upliftPpm(Money.units(5_000_000), Money.units(1)))
        assertEquals(0L, LocalSpending.upliftPpm(Money.ZERO, Money.ZERO))
    }

    @Test fun upliftsCoverEveryCategoryInOrder() {
        val spend = LocalSpending.breakdown(longArrayOf(2_000L, 1_000L, 500L, 100L), 1_000_000L)
        val ups = LocalSpending.uplifts(spend, 50_000L, perHead)
        assertEquals(SpendCategory.values().toList(), ups.map { it.category })
        for (u in ups) {
            assertEquals(spend[u.category], u.touristSpend)
            assertEquals(LocalSpending.upliftPpm(u.touristSpend, u.residentSpend), u.upliftPpm)
        }
    }

    @Test fun moreTouristsMeanMoreUplift() {
        val few = LocalSpending.uplifts(LocalSpending.breakdown(longArrayOf(100L, 0L, 0L, 0L), 1_000_000L), 50_000L, perHead)
        val many = LocalSpending.uplifts(LocalSpending.breakdown(longArrayOf(1_000L, 0L, 0L, 0L), 1_000_000L), 50_000L, perHead)
        for (i in few.indices) assertTrue(many[i].upliftPpm > few[i].upliftPpm)
    }

    @Test fun biggerTownsFeelTouristsLess() {
        val spend = LocalSpending.breakdown(longArrayOf(1_000L, 0L, 0L, 0L), 1_000_000L)
        val town = LocalSpending.uplifts(spend, 20_000L, perHead)
        val city = LocalSpending.uplifts(spend, 2_000_000L, perHead)
        for (i in town.indices) assertTrue(town[i].upliftPpm > city[i].upliftPpm)
    }

    @Test fun multiplierMatchesTheClosedForm() {
        // 1 / (1 - 0.45 * 0.65) = 1.4134...
        val m = LocalSpending.multiplierPpm(450_000L, 350_000L)
        assertTrue(m in 1_413_000L..1_414_500L)
        assertEquals(1_000_000L, LocalSpending.multiplierPpm(0L, 0L))
        assertEquals(1_000_000L, LocalSpending.multiplierPpm(800_000L, 1_000_000L))
    }

    @Test fun multiplierRisesWithRespendAndFallsWithLeakage() {
        assertTrue(LocalSpending.multiplierPpm(600_000L, 300_000L) > LocalSpending.multiplierPpm(300_000L, 300_000L))
        assertTrue(LocalSpending.multiplierPpm(500_000L, 600_000L) < LocalSpending.multiplierPpm(500_000L, 100_000L))
    }

    @Test fun multiplierIsFiniteForExtremeInputs() {
        val m = LocalSpending.multiplierPpm(5_000_000L, -1L)
        assertEquals(10_000_000L, m)
        assertTrue(LocalSpending.multiplierPpm(900_000L, 0L) <= 10_000_000L)
    }

    @Test fun economicImpactAppliesTheMultiplier() {
        assertEquals(Money.units(1_500), LocalSpending.economicImpact(Money.units(1_000), 1_500_000L))
        assertEquals(Money.units(1_000), LocalSpending.economicImpact(Money.units(1_000), 1_000_000L))
    }
}
