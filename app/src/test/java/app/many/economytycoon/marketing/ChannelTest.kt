package app.many.economytycoon.marketing

import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelTest {
    @Test fun everyChannelHasConsistentLimits() {
        for (c in Channel.values()) {
            val s = c.spec
            assertTrue(c.label, s.minDailySpend <= s.maxDailySpend)
            assertTrue(c.label, s.reachCeilingPpm in 1..1_000_000L)
            assertTrue(c.label, s.awarenessWeightPpm + s.loyaltyWeightPpm <= 1_100_000L)
            assertEquals(IncomeBand.values().size, s.bandFitPpm.size)
        }
    }

    @Test fun televisionReachesMoreButCostsMoreThanPress() {
        assertTrue(Channel.TELEVISION.spec.reachCeilingPpm > Channel.LOCAL_PRESS.spec.reachCeilingPpm)
        assertTrue(Channel.TELEVISION.spec.halfSpend > Channel.LOCAL_PRESS.spec.halfSpend)
    }

    @Test fun loyaltyProgrammeBuildsLoyaltyNotAwareness() {
        val s = Channel.LOYALTY_PROGRAMME.spec
        assertTrue(s.loyaltyWeightPpm > 5 * s.awarenessWeightPpm)
    }

    @Test fun bookedAheadChannelsAskForDeposits() {
        assertTrue(Channel.TELEVISION.spec.depositDays > 0)
        assertTrue(Channel.SPONSORSHIP.spec.depositDays > Channel.TELEVISION.spec.depositDays)
        assertEquals(0, Channel.ONLINE.spec.depositDays)
        assertEquals(0L, Channel.ONLINE.spec.cancelFeePpm)
    }

    @Test fun fitFollowsTheIncomeBand() {
        assertTrue(Channel.IN_STORE.spec.fit(IncomeBand.LOW) > Channel.IN_STORE.spec.fit(IncomeBand.HIGH))
        assertTrue(Channel.SPONSORSHIP.spec.fit(IncomeBand.HIGH) > Channel.SPONSORSHIP.spec.fit(IncomeBand.LOW))
    }

    @Test fun orderedListMatchesOrdinals() {
        assertEquals(Channel.values().toList(), Channel.ordered)
        assertEquals(Channel.ONLINE, Channel.byOrdinal(Channel.ONLINE.ordinal))
        assertNull(Channel.byOrdinal(99))
    }

    @Test fun segmentsCoverTheirBands() {
        assertEquals(3, TargetSegment.EVERYONE.bands.size)
        assertEquals(listOf(IncomeBand.LOW), TargetSegment.BUDGET.bands)
        assertEquals(listOf(IncomeBand.HIGH), TargetSegment.PREMIUM.bands)
        assertNull(TargetSegment.byOrdinal(-1))
    }

    @Test(expected = IllegalArgumentException::class) fun specRejectsInvertedSpendLimits() {
        ChannelSpec(500_000, Money.units(1), 1, 1, 1, 1, Money.units(10), Money.units(5), 0, 0, 1, listOf(1, 1, 1))
    }

    @Test(expected = IllegalArgumentException::class) fun specNeedsOneFitPerBand() {
        ChannelSpec(500_000, Money.units(1), 1, 1, 1, 1, Money.units(1), Money.units(5), 0, 0, 1, listOf(1, 1))
    }
}
