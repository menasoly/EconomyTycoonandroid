package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocator
import app.many.economytycoon.core.RegionId

@JvmInline value class CampaignId(val value: Long) : Comparable<CampaignId> {
    override fun compareTo(other: CampaignId) = value.compareTo(other.value)
    override fun toString() = "Campaign#$value"
}

@JvmInline value class PromotionId(val value: Long) : Comparable<PromotionId> {
    override fun compareTo(other: PromotionId) = value.compareTo(other.value)
    override fun toString() = "Promotion#$value"
}

@JvmInline value class AbTestId(val value: Long) : Comparable<AbTestId> {
    override fun compareTo(other: AbTestId) = value.compareTo(other.value)
    override fun toString() = "AbTest#$value"
}

@JvmInline value class DamageId(val value: Long) : Comparable<DamageId> {
    override fun compareTo(other: DamageId) = value.compareTo(other.value)
    override fun toString() = "Damage#$value"
}

/** The unit awareness and loyalty are tracked for: one company's brand for one good in one region. */
data class BrandKey(val company: CompanyId, val good: GoodId, val region: RegionId) : Comparable<BrandKey> {
    override fun compareTo(other: BrandKey): Int {
        val c = company.compareTo(other.company)
        if (c != 0) return c
        val g = good.compareTo(other.good)
        return if (g != 0) g else region.compareTo(other.region)
    }

    override fun toString() = "$company/${good.key}/$region"
}

/** One market a brand competes in, regardless of who sells there. */
data class MarketKey(val good: GoodId, val region: RegionId) : Comparable<MarketKey> {
    override fun compareTo(other: MarketKey): Int {
        val g = good.compareTo(other.good)
        return if (g != 0) g else region.compareTo(other.region)
    }
}

/** Counters for the ids this module hands out; saved with the state so ids are never reused. */
class MarketingIds {
    private val campaigns = IdAllocator()
    private val promotions = IdAllocator()
    private val tests = IdAllocator()
    private val damages = IdAllocator()

    fun campaign() = CampaignId(campaigns.allocate())
    fun promotion() = PromotionId(promotions.allocate())
    fun test() = AbTestId(tests.allocate())
    fun damage() = DamageId(damages.allocate())

    fun snapshot(): LongArray = longArrayOf(campaigns.peek(), promotions.peek(), tests.peek(), damages.peek())

    fun restore(next: LongArray) {
        require(next.size == 4) { "expected four id counters" }
        campaigns.restore(next[0]); promotions.restore(next[1]); tests.restore(next[2]); damages.restore(next[3])
    }
}
