package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionTest {
    private val w = TourismWorld()

    @Test fun platformsAndAgenciesSitInTheirChannels() {
        assertEquals(Channel.ONLINE, w.platform().channel)
        assertEquals(Channel.AGENCY, w.platform(IntermediaryKind.AGENCY).channel)
    }

    @Test fun onlineRankingFollowsReviewsAndIgnoresCommission() {
        val cheap = w.platform(commission = 100_000L)
        val dear = w.platform(commission = 300_000L)
        assertTrue(cheap.rankingPpm(900_000L) > cheap.rankingPpm(400_000L))
        assertEquals(cheap.rankingPpm(700_000L), dear.rankingPpm(700_000L))
    }

    @Test fun agenciesFavourThoseWhoPayMore() {
        val low = w.platform(IntermediaryKind.AGENCY, 80_000L)
        val high = w.platform(IntermediaryKind.AGENCY, 250_000L)
        assertTrue(high.rankingPpm(700_000L) > low.rankingPpm(700_000L))
    }

    @Test fun rankingStaysPositiveAndClampsScores() {
        for (k in IntermediaryKind.values()) {
            val i = w.platform(k)
            assertTrue(i.rankingPpm(0L) > 0L)
            assertEquals(i.rankingPpm(1_000_000L), i.rankingPpm(5_000_000L))
            assertEquals(i.rankingPpm(0L), i.rankingPpm(-5L))
        }
    }

    @Test fun commissionIsBoundedWhenCreatedAndWhenChanged() {
        assertEquals(Rules.INVALID_ARGUMENT, w.service.addIntermediary(IntermediaryKind.AGENCY, null, "A", Intermediary.MAX_COMMISSION_PPM + 1).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.addIntermediary(IntermediaryKind.AGENCY, null, "A", -1L).rejectionOrNull()?.rule)
        val i = w.platform()
        w.service.setCommission(i.id, Intermediary.MAX_COMMISSION_PPM).getOrThrow()
        assertEquals(Intermediary.MAX_COMMISSION_PPM, i.commissionPpm)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setCommission(i.id, 400_000L).rejectionOrNull()?.rule)
    }

    @Test fun namesAndReachAreChecked() {
        assertEquals(Rules.INVALID_ARGUMENT, w.service.addIntermediary(IntermediaryKind.ONLINE_PLATFORM, null, " ", 100_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.addIntermediary(IntermediaryKind.ONLINE_PLATFORM, null, "P", 100_000L, reachPpm = 0L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.addIntermediary(IntermediaryKind.ONLINE_PLATFORM, null, "P", 100_000L, listingFee = Money(-1)).rejectionOrNull()?.rule)
        val i = w.platform()
        w.service.setReach(i.id, 2_000_000L).getOrThrow()
        assertEquals(2_000_000L, i.reachPpm)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setReach(i.id, 0L).rejectionOrNull()?.rule)
    }

    @Test fun unknownIntermediariesAreRefused() {
        assertEquals(Rules.UNKNOWN_ENTITY, w.service.setCommission(IntermediaryId(77), 100_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, w.service.setReach(IntermediaryId(77), 100_000L).rejectionOrNull()?.rule)
    }

    @Test fun listingAddsAndRemovesAPropertyFromAPlatform() {
        val p = w.hotel()
        val i = w.platform()
        w.service.listWith(p.id, i.id).getOrThrow()
        assertTrue(i.id in p.listedOn)
        assertTrue(w.service.listWith(p.id, i.id).isRejected || i.id in p.listedOn)
        w.service.delist(p.id, i.id).getOrThrow()
        assertTrue(i.id !in p.listedOn)
    }

    @Test fun intermediaryIdsAreAllocatedInOrder() {
        val a = w.platform()
        val b = w.platform()
        assertTrue(b.id.value > a.id.value)
    }

    @Test fun hashFollowsCommissionAndTotals() {
        val i = w.platform()
        fun h() = CanonicalHasher().also { i.hashInto(it) }.finish()
        val before = h()
        w.service.setCommission(i.id, 90_000L).getOrThrow()
        assertNotEquals(before, h())
        val mid = h()
        i.monthRooms = 4
        assertNotEquals(mid, h())
    }

    @Test fun listedPlatformsEarnCommissionOnTheirBookings() {
        val p = w.hotel()
        val platform = w.platform(commission = 180_000L)
        w.service.listWith(p.id, platform.id).getOrThrow()
        w.days(40)
        val k = TourismAnalytics.kpis(p, 20)
        assertTrue(k.commissions.raw > 0L)
        assertTrue(k.commissionShareOfRevenuePpm in 1..360_000L)
    }
}
