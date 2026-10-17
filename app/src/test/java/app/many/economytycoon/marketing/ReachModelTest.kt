package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.MarketingTestSupport.context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachModelTest {
    private val params = MarketingParams()
    private val audience = Audience.even(MarketingTestSupport.north, 500_000L)
    private val spec = Channel.RADIO.spec

    private fun reach(spend: Long, a: Audience = audience, seg: TargetSegment = TargetSegment.EVERYONE) =
        ReachModel.reachPpm(spec, Money.units(spend), a, seg, params)

    @Test fun noSpendReachesNobody() {
        assertEquals(0L, reach(0))
    }

    @Test fun halfSpendReachesHalfTheCeiling() {
        // 500,000 people at 4 per thousand is 2,000 a day.
        assertEquals(spec.reachCeilingPpm / 2, reach(2_000))
    }

    @Test fun reachGrowsWithSpendButNeverPassesTheCeiling() {
        var last = 0L
        for (s in listOf(100L, 500L, 2_000L, 10_000L, 15_000L)) {
            val r = reach(s)
            assertTrue(r > last)
            assertTrue(r < spec.reachCeilingPpm)
            last = r
        }
    }

    @Test fun returnsDiminish() {
        val first = reach(1_000) - reach(0)
        val second = reach(2_000) - reach(1_000)
        val third = reach(3_000) - reach(2_000)
        assertTrue(first > second)
        assertTrue(second > third)
    }

    @Test fun biggerAudiencesCostMoreToReach() {
        val big = Audience.even(MarketingTestSupport.north, 2_000_000L)
        assertTrue(reach(1_000, big) < reach(1_000))
    }

    @Test fun richRegionsPayMoreForMedia() {
        val rich = Audience.even(MarketingTestSupport.north, 500_000L, incomeIndexPpm = 1_500_000L)
        val poor = Audience.even(MarketingTestSupport.north, 500_000L, incomeIndexPpm = 700_000L)
        assertTrue(ReachModel.costIndexPpm(rich, params) > ReachModel.costIndexPpm(poor, params))
        assertTrue(reach(1_000, rich) < reach(1_000, poor))
    }

    @Test fun costIndexHasAFloor() {
        val broke = Audience.even(MarketingTestSupport.north, 1_000L, incomeIndexPpm = 0L)
        assertEquals(600_000L, ReachModel.costIndexPpm(broke, params))
        val absurd = params.copy(incomeCostPassThroughPpm = 5_000_000L)
        assertEquals(400_000L, ReachModel.costIndexPpm(broke, absurd))
    }

    @Test fun targetingASegmentReachesMoreOfItForTheSameMoney() {
        val all = reach(1_000)
        val premium = reach(1_000, seg = TargetSegment.PREMIUM)
        assertTrue(premium > all)
    }

    @Test fun regionImpulseIsScaledByCoverage() {
        val all = ReachModel.regionImpulsePpm(spec, Money.units(1_000), audience, TargetSegment.EVERYONE, params)
        val premium = ReachModel.regionImpulsePpm(spec, Money.units(1_000), audience, TargetSegment.PREMIUM, params)
        assertTrue(premium < all)
    }

    @Test fun cityCampaignsOnlyMoveTheirShareOfTheRegion() {
        val city = Audience.even(MarketingTestSupport.north, 100_000L, city = MarketingTestSupport.harbour, regionInhabitants = 500_000L)
        val inCity = ReachModel.regionImpulsePpm(spec, Money.units(1_000), city, TargetSegment.EVERYONE, params)
        val wholeRegion = ReachModel.regionImpulsePpm(spec, Money.units(1_000), audience, TargetSegment.EVERYONE, params)
        assertTrue(inCity < wholeRegion)
        assertTrue(inCity > 0L)
    }

    @Test fun emptyAudienceGivesNothing() {
        val empty = Audience.even(MarketingTestSupport.north, 0L)
        assertEquals(0L, ReachModel.reachPpm(spec, Money.units(1_000), empty, TargetSegment.EVERYONE, params))
    }

    @Test fun spendForReachInvertsTheCurve() {
        val spend = ReachModel.spendForReach(spec, 200_000L, audience, TargetSegment.EVERYONE, params)
        assertNotNull(spend)
        val back = reach(spend!!.wholeUnits())
        assertTrue("got $back", Math.abs(back - 200_000L) < 2_000L)
    }

    @Test fun spendForReachIsImpossibleAtOrAboveTheCeiling() {
        assertNull(ReachModel.spendForReach(spec, spec.reachCeilingPpm, audience, TargetSegment.EVERYONE, params))
        assertNull(ReachModel.spendForReach(spec, 0L, audience, TargetSegment.EVERYONE, params))
    }

    @Test fun marginalReachFallsAsSpendRises() {
        val low = ReachModel.marginalReachPpm(spec, Money.units(500), Money.units(100), audience, TargetSegment.EVERYONE, params)
        val high = ReachModel.marginalReachPpm(spec, Money.units(8_000), Money.units(100), audience, TargetSegment.EVERYONE, params)
        assertTrue(low > high)
    }

    @Test fun creativeQualityMovesStrengthAroundOne() {
        assertEquals(1_000_000L, ReachModel.creativeMultiplierPpm(500_000L, params))
        assertEquals(1_500_000L, ReachModel.creativeMultiplierPpm(1_000_000L, params))
        assertEquals(500_000L, ReachModel.creativeMultiplierPpm(0L, params))
        assertEquals(1_500_000L, ReachModel.creativeMultiplierPpm(5_000_000L, params))
    }

    @Test fun wornCreativesKeepAFloor() {
        assertEquals(1_000_000L, ReachModel.wearMultiplierPpm(1_000_000L, params))
        assertEquals(params.wornEffectFloorPpm, ReachModel.wearMultiplierPpm(0L, params))
        assertTrue(ReachModel.wearMultiplierPpm(500_000L, params) in params.wornEffectFloorPpm..1_000_000L)
    }

    @Test fun rampStartsPartwayAndReachesOne() {
        val tv = Channel.TELEVISION.spec
        assertTrue(ReachModel.rampPpm(tv, 0) < ReachModel.rampPpm(tv, 3))
        assertEquals(1_000_000L, ReachModel.rampPpm(tv, tv.rampDays))
        assertEquals(1_000_000L, ReachModel.rampPpm(tv, 400))
        assertTrue(ReachModel.rampPpm(Channel.ONLINE.spec, 0) in 1..1_000_000L)
    }

    @Test fun contextHelperBuildsAnAudience() {
        assertEquals(500_000L, context().audience.inhabitants)
    }
}
