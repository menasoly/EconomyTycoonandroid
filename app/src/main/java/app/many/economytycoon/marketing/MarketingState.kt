package app.many.economytycoon.marketing

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.StateHash

/**
 * Everything marketing remembers: the campaigns booked, what people think of each brand, promotions, split tests and
 * the damage log. Services in this package change it; everyone else reads. Iteration is always in key order.
 */
class MarketingState : Hashable {
    val ids = MarketingIds()
    internal val campaignMap = java.util.TreeMap<CampaignId, Campaign>()
    internal val stockMap = java.util.TreeMap<BrandKey, BrandStock>()
    internal val promotionMap = java.util.TreeMap<PromotionId, Promotion>()
    internal val testMap = java.util.TreeMap<AbTestId, AbTest>()
    internal val damageMap = java.util.TreeMap<DamageId, DamageRecord>()

    fun campaigns(): List<Campaign> = campaignMap.values.toList()
    fun campaign(id: CampaignId): Campaign? = campaignMap[id]
    fun campaignsOf(company: CompanyId): List<Campaign> = campaignMap.values.filter { it.company == company }
    fun liveCampaignsOf(company: CompanyId): List<Campaign> = campaignMap.values.filter { it.company == company && it.isLive }
    fun runningCampaigns(): List<Campaign> = campaignMap.values.filter { it.isRunning }
    fun campaignsFor(key: BrandKey): List<Campaign> = campaignMap.values.filter { it.key == key && it.isLive }
    fun campaignsIn(good: GoodId, region: RegionId): List<Campaign> =
        campaignMap.values.filter { it.good == good && it.region == region && it.isRunning }

    fun stock(key: BrandKey): BrandStock? = stockMap[key]
    fun stocks(): List<Pair<BrandKey, BrandStock>> = stockMap.entries.map { it.key to it.value }
    fun stocksOf(company: CompanyId): List<Pair<BrandKey, BrandStock>> = stockMap.entries.filter { it.key.company == company }.map { it.key to it.value }
    fun stocksIn(good: GoodId, region: RegionId): List<Pair<BrandKey, BrandStock>> =
        stockMap.entries.filter { it.key.good == good && it.key.region == region }.map { it.key to it.value }
    internal fun stockFor(key: BrandKey): BrandStock = stockMap.getOrPut(key) { BrandStock() }

    fun promotions(): List<Promotion> = promotionMap.values.toList()
    fun promotion(id: PromotionId): Promotion? = promotionMap[id]
    fun promotionsFor(company: CompanyId, good: GoodId, region: RegionId): List<Promotion> =
        promotionMap.values.filter { it.company == company && it.good == good && it.region == region }

    fun tests(): List<AbTest> = testMap.values.toList()
    fun test(id: AbTestId): AbTest? = testMap[id]
    fun testFor(campaign: CampaignId): AbTest? = testMap.values.lastOrNull { it.campaign == campaign }

    fun damages(): List<DamageRecord> = damageMap.values.toList()
    fun damagesOf(company: CompanyId): List<DamageRecord> = damageMap.values.filter { it.company == company }

    fun isEmpty(): Boolean = campaignMap.isEmpty() && stockMap.isEmpty() && promotionMap.isEmpty() && testMap.isEmpty() && damageMap.isEmpty()

    internal fun clear() {
        campaignMap.clear(); stockMap.clear(); promotionMap.clear(); testMap.clear(); damageMap.clear()
    }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("marketing")
        for (n in ids.snapshot()) hasher.putLong(n)
        hasher.putInt(campaignMap.size)
        for (c in campaignMap.values) c.hashInto(hasher)
        hasher.putInt(stockMap.size)
        for ((k, s) in stockMap) {
            hasher.putLong(k.company.value).putString(k.good.key).putLong(k.region.value)
            s.hashInto(hasher)
        }
        hasher.putInt(promotionMap.size)
        for (p in promotionMap.values) p.hashInto(hasher)
        hasher.putInt(testMap.size)
        for (t in testMap.values) t.hashInto(hasher)
        hasher.putInt(damageMap.size)
        for (d in damageMap.values) d.hashInto(hasher)
    }

    /** The canonical hash of this partition alone, for golden checks and parity tests. */
    fun stateHash(): StateHash = CanonicalHasher().also { hashInto(it) }.finish()
}
