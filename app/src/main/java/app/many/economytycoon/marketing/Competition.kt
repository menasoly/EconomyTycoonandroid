package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.mulDiv

/** One company's presence in a market's advertising. */
data class VoiceEntry(
    val company: CompanyId,
    /** How much exposure its channels are producing right now, ppm of the region. */
    val pressurePpm: Long,
    val sharePpm: Long,
    val dailySpend: Money,
    val awarenessPpm: Long,
    val loyaltyPpm: Long,
)

data class MarketVoice(val market: MarketKey, val entries: List<VoiceEntry>) {
    val totalSpend: Money get() = Money.sum(entries.map { it.dailySpend })
    fun of(company: CompanyId): VoiceEntry? = entries.firstOrNull { it.company == company }
    fun leader(): VoiceEntry? = entries.maxWithOrNull(compareBy<VoiceEntry> { it.sharePpm }.thenByDescending { it.company })
}

/** What a company could do in answer to rivals who are outshouting it. */
data class CounterProposal(
    val good: GoodId,
    val region: RegionId,
    val channel: Channel,
    val dailyBudget: Money,
    val rival: CompanyId,
    val gapPpm: Long,
    val reason: String,
)

/** Share of voice and the answers to it. */
object Competition {
    /** Everything advertising in a market: pressure from the smoothed exposure of each brand, spend from live campaigns. */
    fun voice(state: MarketingState, good: GoodId, region: RegionId): MarketVoice {
        val brands = state.stocksIn(good, region)
        val rows = ArrayList<Triple<CompanyId, Long, BrandStock>>()
        for ((key, stock) in brands) {
            val parts = LongArray(Channel.values().size) { stock.adstock(Channel.values()[it]) }
            rows += Triple(key.company, Ppm.union(parts), stock)
        }
        val total = rows.sumOf { it.second }
        val entries = rows.map { (company, pressure, stock) ->
            val spend = state.campaignsIn(good, region).filter { it.company == company }.fold(Money.ZERO) { a, c -> a + c.dailyBudget }
            VoiceEntry(company, pressure, if (total <= 0L) 0L else Ppm.ratio(pressure, total), spend, stock.awarenessPpm, stock.loyaltyPpm)
        }.sortedBy { it.company }
        return MarketVoice(MarketKey(good, region), entries)
    }

    /** Every market where at least one brand has anything to track. */
    fun markets(state: MarketingState): List<MarketKey> =
        state.stocks().map { MarketKey(it.first.good, it.first.region) }.distinct().sorted()

    /** The strongest rival of [company] in a market, if anyone is louder. */
    fun loudestRival(voice: MarketVoice, company: CompanyId): VoiceEntry? {
        val mine = voice.of(company)?.sharePpm ?: 0L
        return voice.entries.filter { it.company != company && it.sharePpm > mine }.maxWithOrNull(compareBy<VoiceEntry> { it.sharePpm }.thenByDescending { it.company })
    }

    /**
     * A counter-campaign: when a rival has more of the voice, answer with part of the gap in spending, on the channel
     * that buys the most reach for the money among those the company is not already using for that product.
     */
    fun counter(
        state: MarketingState, company: CompanyId, good: GoodId, region: RegionId, audience: Audience, params: MarketingParams,
        maxDaily: Money, responsePpm: Long = 700_000L,
    ): CounterProposal? {
        val voice = voice(state, good, region)
        val rival = loudestRival(voice, company) ?: return null
        val mySpend = voice.of(company)?.dailySpend ?: Money.ZERO
        val gap = rival.dailySpend - mySpend
        if (gap.raw <= 0L) return null
        val want = Money(mulDiv(gap.raw, responsePpm, Ppm.ONE))
        val taken = state.campaignsFor(BrandKey(company, good, region)).map { it.channel }.toSet()
        var best: Channel? = null
        var bestPerMoney = -1L
        var bestBudget = Money.ZERO
        for (ch in Channel.ordered) {
            if (ch in taken) continue
            val spec = ch.spec
            val budget = Money.min(Money.max(want, spec.minDailySpend), Money.min(spec.maxDailySpend, maxDaily))
            if (budget < spec.minDailySpend) continue
            val reach = ReachModel.regionImpulsePpm(spec, budget, audience, TargetSegment.EVERYONE, params) * (spec.awarenessWeightPpm + spec.loyaltyWeightPpm) / Ppm.ONE
            val perMoney = mulDiv(reach, 1_000_000L, budget.raw / 100L + 1L)
            if (perMoney > bestPerMoney) { best = ch; bestPerMoney = perMoney; bestBudget = budget }
        }
        val channel = best ?: return null
        val gapShare = rival.sharePpm - (voice.of(company)?.sharePpm ?: 0L)
        return CounterProposal(good, region, channel, bestBudget, rival.company, gapShare, "A rival holds ${gapShare / 10_000L} points more of the advertising voice here.")
    }
}
