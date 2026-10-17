package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemandEffectsTest {
    private val params = MarketingParams()
    private val s = MarketingTestSupport.system()
    private val key = BrandKey(acme, beer, north)

    @Test fun anUnknownBrandHasNoPull() {
        assertEquals(1_000_000L, MarketingEffects.appealPpm(0L, 0L, params))
        assertEquals(1_000_000L, MarketingEffects.appealPpm(null, params))
    }

    @Test fun awarenessAndLoyaltyBothLiftDemand() {
        val a = MarketingEffects.appealPpm(500_000L, 0L, params)
        val b = MarketingEffects.appealPpm(500_000L, 800_000L, params)
        assertEquals(1_150_000L, a)
        assertTrue(b > a)
    }

    @Test fun loyaltyCountsOnlyAmongTheAware() {
        assertEquals(MarketingEffects.appealPpm(0L, 900_000L, params), 1_000_000L)
    }

    @Test fun theLiftHasACeiling() {
        assertEquals(1_000_000L + params.awarenessDemandLiftPpm + params.loyaltyDemandLiftPpm, MarketingEffects.appealPpm(1_000_000L, 1_000_000L, params))
        assertEquals(MarketingEffects.appealPpm(1_000_000L, 1_000_000L, params), MarketingEffects.appealPpm(9_000_000L, 9_000_000L, params))
    }

    @Test fun categoryGrowsWhenMoreOfTheMarketKnowsABrand() {
        assertEquals(1_000_000L, MarketingEffects.categoryShiftPpm(emptyList(), params))
        val one = MarketingEffects.categoryShiftPpm(listOf(400_000L), params)
        val two = MarketingEffects.categoryShiftPpm(listOf(400_000L, 400_000L), params)
        assertTrue(one > 1_000_000L)
        assertTrue(two > one)
        assertTrue(two <= 1_000_000L + params.categoryExpansionPpm)
    }

    @Test fun relativeAppealComparesWithTheAverageSeller() {
        assertEquals(1_000_000L, MarketingEffects.relativeAppealPpm(1_200_000L, emptyList()))
        assertEquals(1_000_000L, MarketingEffects.relativeAppealPpm(1_200_000L, listOf(1_200_000L, 1_200_000L)))
        assertEquals(1_200_000L * 1_000_000L / 1_100_000L, MarketingEffects.relativeAppealPpm(1_200_000L, listOf(1_200_000L, 1_000_000L)))
    }

    @Test fun sharesSplitAMarketByAppealAndSumToOne() {
        val w = MarketingEffects.shareWeights(listOf(1_300_000L, 1_000_000L, 1_000_000L))
        assertEquals(1_000_000L, w.sum())
        assertTrue(w[0] > w[1])
        assertEquals(w[1], w[2])
        assertEquals(emptyList<Long>(), MarketingEffects.shareWeights(emptyList()))
        assertEquals(1_000_000L, MarketingEffects.shareWeights(listOf(0L, 0L, 0L)).sum())
        assertEquals(listOf(1_000_000L), MarketingEffects.shareWeights(listOf(7L)))
    }

    @Test fun shareWeightsAreExactWhenThingsDoNotDivideEvenly() {
        assertEquals(1_000_000L, MarketingEffects.shareWeights(listOf(1L, 1L, 1L)).sum())
    }

    // --- over live state ---

    @Test fun advertisingRaisesTheBrandMultiplier() {
        assertEquals(1_000_000L, s.demand.forBrand(key, Tick(0)))
        book(s, request(daily = 2_000, tier = CreativeTier.BASIC))
        run(s, 0, 40)
        assertTrue(s.demand.forBrand(key, Tick(40)) > 1_000_000L)
        assertTrue(s.demand.appeal(key) > 1_000_000L)
    }

    @Test fun aSaleLiftsTheBrandMultiplierFurther() {
        book(s, request(daily = 2_000, tier = CreativeTier.BASIC))
        run(s, 0, 40)
        val plain = s.demand.forBrand(key, Tick(40))
        s.promotions.launch(PromotionRequest(acme, beer, north, null, 7, 100_000L, Money.units(100), Money.units(50), 4_000_000L), Tick(40)).getOrThrow()
        assertTrue(s.demand.forBrand(key, Tick(41)) > plain)
    }

    @Test fun categoryShiftOnlyCountsBrandsInThatMarket() {
        book(s, request(daily = 2_000, tier = CreativeTier.BASIC))
        run(s, 0, 40)
        assertTrue(s.demand.categoryShift(beer, north) > 1_000_000L)
        assertEquals(1_000_000L, s.demand.categoryShift(bread, north))
    }

    @Test fun theBetterKnownSellerIsMoreAttractive() {
        book(s, request(daily = 3_000, tier = CreativeTier.BASIC))
        book(s, request(company = rival, daily = 300, tier = CreativeTier.BASIC))
        run(s, 0, 60)
        assertTrue(s.demand.relative(acme, beer, north, listOf(acme, rival)) > 1_000_000L)
        assertTrue(s.demand.relative(rival, beer, north, listOf(acme, rival)) < 1_000_000L)
        val shares = s.demand.shares(beer, north, listOf(rival, acme, acme))
        assertEquals(1_000_000L, shares.values.sum())
        assertTrue(shares.getValue(acme) > shares.getValue(rival))
        assertEquals(listOf(acme, rival), shares.keys.toList())
    }

    @Test fun sellersWithoutAnyAdvertisingSplitEvenly() {
        val shares = s.demand.shares(beer, north, listOf(acme, rival, CompanyId(3), CompanyId(4)))
        assertEquals(setOf(250_000L), shares.values.toSet())
    }

    @Test fun pricingPowerNeedsBothAwarenessAndLoyalty() {
        assertEquals(0L, s.demand.pricingPower(key))
        book(s, request(daily = 2_500, channel = Channel.SPONSORSHIP, tier = CreativeTier.BASIC))
        book(s, request(daily = 2_500, tier = CreativeTier.BASIC))
        run(s, 0, 120)
        val p = s.demand.pricingPower(key)
        assertTrue(p > 0L)
        assertTrue(p <= 150_000L)
    }
}
