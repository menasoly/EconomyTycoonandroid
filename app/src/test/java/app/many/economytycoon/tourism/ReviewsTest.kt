package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewsTest {
    private fun inputs(
        stars: Int = 3, condition: Long = 1_000_000L, service: Long = 1_000_000L, paid: Long = 100, fair: Long = 100, walked: Long = 0L, experience: Long = 700_000L,
    ) = SatisfactionInputs(stars, condition, service, Money.units(paid), Money.units(fair), walked, experience)

    @Test fun aNewBookStartsNeutralWithNoReviews() {
        val b = ReviewBook()
        assertEquals(0L, b.volume)
        assertEquals(700_000L, b.scorePpm)
        assertEquals(38, b.ratingTenths)
    }

    @Test fun ratingTenthsRunFromOneToFive() {
        val b = ReviewBook()
        b.restore(0L, 0L, 0L, emptyList())
        assertEquals(10, b.ratingTenths)
        b.restore(1_000_000L, 0L, 0L, emptyList())
        assertEquals(50, b.ratingTenths)
    }

    @Test fun happyGuestsPullTheScoreUpAndUnhappyOnesDown() {
        val up = ReviewBook()
        val down = ReviewBook()
        repeat(60) { up.record(950_000L, 100); down.record(100_000L, 100) }
        assertTrue(up.scorePpm > 700_000L)
        assertTrue(down.scorePpm < 700_000L)
    }

    @Test fun scoreInertiaGrowsWithVolume() {
        val fresh = ReviewBook()
        val old = ReviewBook()
        old.restore(700_000L, 400L, 700_000L, emptyList())
        fresh.record(100_000L, 1_000)
        old.record(100_000L, 1_000)
        assertTrue(fresh.scorePpm < old.scorePpm)
    }

    @Test fun volumeOnlyGrowsWhenGuestsStay() {
        val b = ReviewBook()
        b.record(800_000L, 0)
        assertEquals(0L, b.volume)
        assertEquals(1, b.recent.size)
        b.record(800_000L, 250)
        assertEquals(20L, b.volume)
    }

    @Test fun recentHistoryIsBounded() {
        val b = ReviewBook()
        repeat(ReviewBook.RECENT_DAYS + 25) { b.record(500_000L, 10) }
        assertEquals(ReviewBook.RECENT_DAYS, b.recent.size)
    }

    @Test fun recentAverageUsesTheTail() {
        val b = ReviewBook()
        repeat(20) { b.record(200_000L, 1) }
        repeat(10) { b.record(800_000L, 1) }
        assertEquals(800_000L, b.recentAverage(10))
        assertTrue(b.recentAverage(30) < 800_000L)
        assertEquals(700_000L, ReviewBook().recentAverage())
    }

    @Test fun satisfactionIsClampedToValidRange() {
        val b = ReviewBook()
        b.record(5_000_000L, 1_000)
        assertTrue(b.scorePpm <= 1_000_000L)
        b.record(-5L, 1_000)
        assertTrue(b.scorePpm >= 0L)
    }

    @Test fun reputationFollowsTheScoreSlowly() {
        val b = ReviewBook()
        b.restore(1_000_000L, 100L, 200_000L, emptyList())
        b.record(1_000_000L, 100)
        assertTrue(b.reputationPpm > 200_000L)
        assertTrue(b.reputationPpm < 400_000L)
    }

    @Test fun restoreReplacesEverything() {
        val b = ReviewBook()
        repeat(5) { b.record(100_000L, 100) }
        b.restore(123_000L, 9L, 456_000L, listOf(1L, 2L, 3L))
        assertEquals(123_000L, b.scorePpm)
        assertEquals(9L, b.volume)
        assertEquals(456_000L, b.reputationPpm)
        assertEquals(listOf(1L, 2L, 3L), b.recent)
    }

    @Test fun bookHashChangesWithContent() {
        fun h(b: ReviewBook) = CanonicalHasher().also { b.hashInto(it) }.finish()
        val a = ReviewBook()
        val b = ReviewBook()
        assertEquals(h(a), h(b))
        a.record(900_000L, 100)
        assertNotEquals(h(a), h(b))
    }

    @Test fun expectationsRiseWithStarsAndAreCapped() {
        assertEquals(200_000L, GuestSatisfaction.expectationPpm(1))
        assertEquals(1_000_000L, GuestSatisfaction.expectationPpm(5))
        assertEquals(1_000_000L, GuestSatisfaction.expectationPpm(9))
        assertEquals(200_000L, GuestSatisfaction.expectationPpm(-3))
    }

    @Test fun qualityIsHighestForAWellKeptWellStaffedProperty() {
        assertEquals(750_000L, GuestSatisfaction.qualityPpm(1_000_000L, 1_000_000L))
        assertTrue(GuestSatisfaction.qualityPpm(300_000L, 1_000_000L) < 750_000L)
        assertTrue(GuestSatisfaction.qualityPpm(1_000_000L, 300_000L) < 750_000L)
        assertTrue(GuestSatisfaction.qualityPpm(0L, 0L) > 0L)
    }

    @Test fun valueRewardsCheapRoomsAndPunishesDearOnes() {
        val fair = Money.units(100)
        assertEquals(500_000L, GuestSatisfaction.valuePpm(fair, fair))
        assertTrue(GuestSatisfaction.valuePpm(Money.units(60), fair) > 500_000L)
        assertTrue(GuestSatisfaction.valuePpm(Money.units(160), fair) < 500_000L)
        assertEquals(500_000L, GuestSatisfaction.valuePpm(Money.units(50), Money.ZERO))
        assertEquals(1_000_000L, GuestSatisfaction.valuePpm(Money.ZERO, fair))
        assertEquals(0L, GuestSatisfaction.valuePpm(Money.units(900), fair))
    }

    @Test fun scoreFallsWithPoorServiceConditionAndWalks() {
        val good = GuestSatisfaction.score(inputs())
        assertTrue(GuestSatisfaction.score(inputs(service = 400_000L)) < good)
        assertTrue(GuestSatisfaction.score(inputs(condition = 200_000L)) < good)
        assertTrue(GuestSatisfaction.score(inputs(walked = 100_000L)) < good)
        assertTrue(GuestSatisfaction.score(inputs(paid = 160)) < good)
        assertTrue(GuestSatisfaction.score(inputs(experience = 100_000L)) < good)
    }

    @Test fun scoreStaysInRangeForExtremeInputs() {
        for (cond in listOf(-1L, 0L, 1_000_000L, 9_000_000L)) for (svc in listOf(-1L, 0L, 1_000_000L, 9_000_000L)) {
            val s = GuestSatisfaction.score(inputs(condition = cond, service = svc, walked = cond.coerceAtLeast(0), experience = svc))
            assertTrue(s in 0..1_000_000L)
        }
    }

    @Test fun destinationReputationDriftsTowardTheWeightedAverageRating() {
        val r = GuestSatisfaction.destinationReputation(500_000L, listOf(900_000L to 100L, 900_000L to 300L))
        assertEquals(500_000L + 400_000L / 30L, r)
        val weighted = GuestSatisfaction.destinationReputation(500_000L, listOf(1_000_000L to 900L, 0L to 100L))
        assertTrue(weighted > 500_000L)
    }

    @Test fun withNothingSoldReputationDriftsBackTowardNeutral() {
        assertTrue(GuestSatisfaction.destinationReputation(1_000_000L, emptyList()) < 1_000_000L)
        assertTrue(GuestSatisfaction.destinationReputation(100_000L, emptyList()) > 100_000L)
        assertEquals(600_000L, GuestSatisfaction.destinationReputation(600_000L, emptyList()))
    }
}
