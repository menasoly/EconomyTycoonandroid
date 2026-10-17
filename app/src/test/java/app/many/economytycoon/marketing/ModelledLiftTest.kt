package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.MarketingTestSupport.context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelledLiftTest {
    private val params = MarketingParams()
    private val ctx = context()

    @Test fun noSpendEarnsNothing() {
        val v = ModelLiftHelper.value(ctx, emptyMap())
        assertEquals(Money.ZERO, v.spend)
        assertEquals(Money.ZERO, v.incrementalRevenue)
        assertEquals(1_000_000L, v.appealPpm)
        assertEquals(0L, v.returnPpm)
    }

    @Test fun spendingAddsRevenueAndProfitInProportionToMargin() {
        val v = ModelLiftHelper.value(ctx, mapOf(Channel.RADIO to Money.units(2_000)))
        assertTrue(v.incrementalRevenue.raw > 0L)
        assertEquals(v.incrementalRevenue.raw * ctx.marginPpm / 1_000_000L, v.incrementalProfit.raw)
        assertEquals(v.incrementalProfit - v.spend, v.netProfit)
        assertTrue(v.awarenessPpm > 0L)
    }

    @Test fun moreSpendNeverLowersRevenue() {
        var last = Money.ZERO
        for (s in listOf(100L, 400L, 1_000L, 3_000L, 9_000L)) {
            val v = ModelLiftHelper.value(ctx, mapOf(Channel.RADIO to Money.units(s)))
            assertTrue(v.incrementalRevenue >= last)
            last = v.incrementalRevenue
        }
    }

    @Test fun marginalReturnFallsAsSpendRises() {
        val low = ModelledLift.marginalReturnPpm(ctx, mapOf(Channel.RADIO to Money.units(200)), Channel.RADIO, Money.units(100), params)
        val high = ModelledLift.marginalReturnPpm(ctx, mapOf(Channel.RADIO to Money.units(8_000)), Channel.RADIO, Money.units(100), params)
        assertTrue("low $low high $high", low > high)
    }

    @Test fun aZeroStepHasNoReturn() {
        assertEquals(0L, ModelledLift.marginalReturnPpm(ctx, emptyMap(), Channel.RADIO, Money.ZERO, params))
    }

    @Test fun combiningChannelsReachesMoreThanOneAlone() {
        val one = ModelLiftHelper.value(ctx, mapOf(Channel.RADIO to Money.units(6_000)))
        val two = ModelLiftHelper.value(ctx, mapOf(Channel.RADIO to Money.units(3_000), Channel.ONLINE to Money.units(3_000)))
        assertTrue(two.incrementalRevenue > one.incrementalRevenue)
    }

    @Test fun biggerMarketsEarnMoreFromTheSameSpend() {
        val small = context(revenue = 5_000)
        val big = context(revenue = 50_000)
        val plan = mapOf(Channel.ONLINE to Money.units(1_500))
        assertTrue(ModelLiftHelper.value(big, plan).incrementalRevenue > ModelLiftHelper.value(small, plan).incrementalRevenue)
    }

    @Test fun wornCreativesAndCrowdedMarketsEarnLess() {
        val plan = mapOf(Channel.RADIO to Money.units(2_000))
        val fresh = ModelLiftHelper.value(ctx.copy(freshnessPpm = 1_000_000L), plan)
        val worn = ModelLiftHelper.value(ctx.copy(freshnessPpm = 100_000L), plan)
        val crowded = ModelLiftHelper.value(ctx.copy(voicePpm = 50_000L), plan)
        assertTrue(fresh.incrementalRevenue > worn.incrementalRevenue)
        assertTrue(fresh.incrementalRevenue > crowded.incrementalRevenue)
    }

    @Test fun aTaintedBrandEarnsLess() {
        val plan = mapOf(Channel.RADIO to Money.units(2_000))
        assertTrue(ModelLiftHelper.value(ctx, plan).incrementalRevenue > ModelLiftHelper.value(ctx.copy(taintPpm = 800_000L), plan).incrementalRevenue)
    }

    @Test fun anyoneCanSeeTheChannelComparison() {
        val table = ModelledLift.compareChannels(ctx, Money.units(1_000), params)
        assertEquals(Channel.ordered, table.map { it.channel })
        for (row in table) {
            assertTrue(row.dailySpend >= row.channel.spec.minDailySpend)
            assertTrue(row.dailySpend <= row.channel.spec.maxDailySpend)
            assertTrue(row.reachPpm in 0..row.channel.spec.reachCeilingPpm)
        }
        val tv = table.first { it.channel == Channel.TELEVISION }
        val press = table.first { it.channel == Channel.LOCAL_PRESS }
        assertTrue(tv.dailySpend >= Money.units(400))
        assertTrue(press.reachPpm > 0L)
    }

    @Test fun premiumChannelsFavourRichAudiences() {
        val rich = ctx.copy(segment = TargetSegment.PREMIUM)
        val plan = mapOf(Channel.SPONSORSHIP to Money.units(1_000))
        val everyone = ModelLiftHelper.value(ctx, plan)
        val premium = ModelLiftHelper.value(rich, plan)
        assertTrue(premium.awarenessPpm != everyone.awarenessPpm)
    }

    @Test fun planValueReturnIsProfitPerUnitSpent() {
        val v = ModelLiftHelper.value(ctx, mapOf(Channel.RADIO to Money.units(500)))
        assertEquals(v.incrementalProfit.raw * 1_000_000L / v.spend.raw, v.returnPpm)
    }

    private object ModelLiftHelper {
        fun value(c: MarketContext, plan: Map<Channel, Money>) = ModelledLift.value(c, plan, MarketingParams())
    }
}
