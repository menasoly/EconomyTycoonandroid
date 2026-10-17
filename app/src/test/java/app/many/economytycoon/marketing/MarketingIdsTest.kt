package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingIdsTest {
    @Test fun idsAreSequentialPerKind() {
        val ids = MarketingIds()
        assertEquals(CampaignId(1), ids.campaign())
        assertEquals(CampaignId(2), ids.campaign())
        assertEquals(PromotionId(1), ids.promotion())
        assertEquals(AbTestId(1), ids.test())
        assertEquals(DamageId(1), ids.damage())
    }

    @Test fun snapshotAndRestoreContinueTheSequence() {
        val ids = MarketingIds()
        repeat(3) { ids.campaign() }
        ids.promotion()
        val saved = ids.snapshot()
        val other = MarketingIds()
        other.restore(saved)
        assertEquals(CampaignId(4), other.campaign())
        assertEquals(PromotionId(2), other.promotion())
    }

    @Test(expected = IllegalArgumentException::class) fun restoreNeedsFourCounters() {
        MarketingIds().restore(longArrayOf(1, 2))
    }

    @Test fun brandKeysOrderByCompanyThenGoodThenRegion() {
        val a = BrandKey(CompanyId(1), GoodId("beer"), RegionId(2))
        val b = BrandKey(CompanyId(1), GoodId("bread"), RegionId(1))
        val c = BrandKey(CompanyId(2), GoodId("beer"), RegionId(1))
        assertTrue(a < b)
        assertTrue(b < c)
        assertEquals(listOf(a, b, c), listOf(c, b, a).sorted())
    }

    @Test fun marketKeysOrderByGoodThenRegion() {
        val a = MarketKey(GoodId("beer"), RegionId(9))
        val b = MarketKey(GoodId("bread"), RegionId(1))
        assertTrue(a < b)
    }
}
