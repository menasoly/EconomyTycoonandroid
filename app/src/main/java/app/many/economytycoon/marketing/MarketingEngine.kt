package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementResult
import app.many.economytycoon.finance.SettlementStatus

/** Delivered quality of a company's product in a region against what buyers expect; one million is as expected. */
fun interface QualitySource {
    fun quality(key: BrandKey): Long

    companion object {
        val NEUTRAL = QualitySource { Ppm.ONE }
    }
}

/**
 * Runs marketing forward one day at a time. Each day it bills running campaigns, works out the exposure they bought,
 * moves every brand's awareness and loyalty, wears creatives down, plays out promotions and split tests, and hands
 * back the payments and events for the rest of the game. Run it once per tick, after sales, before the next day's demand.
 */
class MarketingEngine(
    val service: CampaignService,
    private val worldSeed: Long = 0L,
) {
    private val state: MarketingState get() = service.state
    private val params: MarketingParams get() = service.params

    private class Exposure(val campaign: Campaign, val impulse: Long, val reach: Long, val impressions: Long)

    fun advance(tick: Tick, audiences: AudienceSource, quality: QualitySource = QualitySource.NEUTRAL): DayReport {
        val intents = ArrayList<SettlementIntent>()
        val events = ArrayList<MarketingEvent>()
        val spend = java.util.TreeMap<CompanyId, Long>()

        startScheduled(tick, events)
        agePaused(tick, intents, events)
        val exposures = billAndMeasure(tick, audiences, intents, events, spend)
        val impulses = combine(exposures)
        for ((key, stock) in state.stockMap.entries.toList()) {
            StockDynamics.step(stock, impulses[key] ?: LongArray(Channel.values().size), quality.quality(key), params)
        }
        applyPromotionFatigue(tick)
        wearAndFinish(tick, exposures, intents, events)
        runPromotions(tick, events)
        runTests(tick, exposures, events)
        prune(tick)
        return DayReport(tick, intents, events, spend.mapValues { Money(it.value) })
    }

    private fun startScheduled(tick: Tick, events: MutableList<MarketingEvent>) {
        for (c in state.campaignMap.values) {
            if (c.status == CampaignStatus.SCHEDULED && c.startTick <= tick) {
                c.status = CampaignStatus.ACTIVE
                events += MarketingEvent.CampaignStarted(c.id, c.company, tick)
            }
        }
    }

    private fun agePaused(tick: Tick, intents: MutableList<SettlementIntent>, events: MutableList<MarketingEvent>) {
        for (c in state.campaignMap.values.toList()) {
            if (c.status != CampaignStatus.PAUSED) continue
            c.pausedDays += 1
            if (c.pausedDays > params.maxPauseDays) {
                val (refund, fee) = service.cancellationTerms(c)
                service.close(c, CampaignStatus.CANCELLED, tick, refund, fee)
                service.ledger.refund(c, refund, tick)?.let { intents += it }
                events += MarketingEvent.CampaignAutoEnded(c.id, c.company, refund, tick)
            }
        }
    }

    private fun billAndMeasure(
        tick: Tick, audiences: AudienceSource, intents: MutableList<SettlementIntent>, events: MutableList<MarketingEvent>,
        spend: MutableMap<CompanyId, Long>,
    ): List<Exposure> {
        val out = ArrayList<Exposure>()
        for (c in state.campaignMap.values) {
            if (c.status != CampaignStatus.ACTIVE) continue
            val bill = service.billFor(c)
            val fromDeposit = Money.min(c.prepaid, bill)
            val due = bill - fromDeposit
            c.prepaid = c.prepaid - fromDeposit
            if (due.raw > 0L) {
                service.ledger.daily(c, due, tick)?.let { intents += it }
                c.spentTotal = c.spentTotal + due
                spend.merge(c.company, due.raw, Long::plus)
            }
            val audience = audiences.audience(c.region, c.city)
            val test = state.testFor(c.id)?.takeIf { it.isRunning }
            val qualityNow = test?.blendedQuality ?: c.creative.qualityPpm
            val spec = c.channel.spec
            val reach = if (audience == null) 0L else ReachModel.reachPpm(spec, c.dailyBudget, audience, c.segment, params)
            val impulse = if (audience == null) 0L else {
                val base = ReachModel.regionImpulsePpm(spec, c.dailyBudget, audience, c.segment, params)
                var v = Ppm.mul(base, ReachModel.rampPpm(spec, c.daysRun))
                v = Ppm.mul(v, ReachModel.wearMultiplierPpm(c.creative.freshnessPpm, params))
                Ppm.clamp(Ppm.mul(v, ReachModel.creativeMultiplierPpm(qualityNow, params)))
            }
            val people = if (audience == null) 0L else mulDiv(audience.segmentSize(c.segment), reach, Ppm.ONE)
            out += Exposure(c, impulse, reach, people)
        }
        return out
    }

    /** Merges campaigns into one exposure per channel per brand, then applies share of voice within each market. */
    private fun combine(exposures: List<Exposure>): Map<BrandKey, LongArray> {
        val channelCount = Channel.values().size
        val grouped = java.util.TreeMap<BrandKey, Array<MutableList<Long>>>()
        for (e in exposures) {
            val lists = grouped.getOrPut(e.campaign.key) { Array(channelCount) { ArrayList<Long>() } }
            lists[e.campaign.channel.ordinal].add(e.impulse)
        }
        val merged = java.util.TreeMap<BrandKey, LongArray>()
        for ((key, lists) in grouped) merged[key] = LongArray(channelCount) { Ppm.union(lists[it].toLongArray()) }
        val pressure = java.util.TreeMap<MarketKey, MutableMap<BrandKey, Long>>()
        for ((key, arr) in merged) pressure.getOrPut(MarketKey(key.good, key.region)) { java.util.TreeMap() }[key] = Ppm.union(arr)
        for (byBrand in pressure.values) {
            val total = byBrand.values.sum()
            for ((key, p) in byBrand) {
                val share = if (total <= 0L) Ppm.ONE else Ppm.ratio(p, total)
                val voice = Voice.multiplierPpm(share, params)
                val arr = merged.getValue(key)
                for (i in arr.indices) arr[i] = Ppm.clamp(Ppm.mul(arr[i], voice))
            }
        }
        return merged
    }

    private fun applyPromotionFatigue(tick: Tick) {
        for (p in state.promotionMap.values) {
            if (!p.runsOn(tick)) continue
            val stock = state.stockMap[BrandKey(p.company, p.good, p.region)] ?: continue
            val loss = Ppm.mul(Ppm.mul(stock.loyaltyPpm, params.promotionFatiguePpm), p.discountPpm)
            stock.set(stock.awarenessPpm, stock.loyaltyPpm - loss)
        }
    }

    private fun wearAndFinish(tick: Tick, exposures: List<Exposure>, intents: MutableList<SettlementIntent>, events: MutableList<MarketingEvent>) {
        for (e in exposures) {
            val c = e.campaign
            val before = c.creative.freshnessPpm
            c.creative.freshnessPpm = before - Ppm.mul(c.channel.spec.wearPerDayPpm, e.reach)
            if (before >= WORN_LINE && c.creative.freshnessPpm < WORN_LINE) {
                events += MarketingEvent.CreativeWornOut(c.id, c.company, c.creative.freshnessPpm, tick)
            }
            c.daysRun += 1
            c.daysRemaining -= 1
            c.impressions += e.impressions
            c.lastReachPpm = e.reach
            if (c.daysRemaining <= 0) {
                val leftover = c.prepaid
                service.close(c, CampaignStatus.COMPLETED, tick, leftover, Money.ZERO)
                service.ledger.refund(c, leftover, tick)?.let { intents += it }
                events += MarketingEvent.CampaignFinished(c.id, c.company, c.netSpend, tick)
            }
        }
    }

    private fun runPromotions(tick: Tick, events: MutableList<MarketingEvent>) {
        for (p in state.promotionMap.values) {
            when (p.status) {
                PromotionStatus.SCHEDULED -> if (p.startTick <= tick) {
                    p.status = PromotionStatus.RUNNING
                    events += MarketingEvent.PromotionStarted(p.id, p.company, tick)
                }
                PromotionStatus.RUNNING -> if (tick + 1L >= p.endTick) {
                    p.status = PromotionStatus.ENDED
                    p.endedAt = tick
                    events += MarketingEvent.PromotionEnded(p.id, p.company, tick)
                }
                else -> {}
            }
        }
    }

    private fun runTests(tick: Tick, exposures: List<Exposure>, events: MutableList<MarketingEvent>) {
        val byCampaign = exposures.associateBy { it.campaign.id }
        for (t in state.testMap.values) {
            if (!t.isRunning) continue
            val c = state.campaign(t.campaign)
            if (c == null || !c.isLive) {
                t.outcome = AbOutcome.CANCELLED
                t.decidedAt = tick
                continue
            }
            t.impressions += byCampaign[t.campaign]?.impressions ?: 0L
            if (tick + 1L >= t.endTick) {
                val result = AbTesting.resolve(t, worldSeed, params)
                t.decidedAt = tick
                if (result == AbOutcome.B_WINS) {
                    c.creative.qualityPpm = t.qualityB
                    c.creative.freshnessPpm = Ppm.ONE
                    c.creative.version += 1
                    c.creative.lastRefreshed = tick
                } else {
                    c.creative.qualityPpm = t.qualityA
                }
                events += MarketingEvent.TestDecided(t.id, c.id, c.company, result, tick)
            }
        }
    }

    private fun prune(tick: Tick) {
        val oldCampaign = tick.value - params.keepEndedDays
        state.campaignMap.values.removeAll { it.isEnded() && (it.endedAt?.value ?: 0L) < oldCampaign }
        val live = state.campaignMap.keys
        state.testMap.values.removeAll { !it.isRunning && (it.decidedAt?.value ?: 0L) < oldCampaign && it.campaign !in live }
        state.promotionMap.values.removeAll { (it.status == PromotionStatus.ENDED || it.status == PromotionStatus.CANCELLED) && (it.endedAt?.value ?: 0L) < oldCampaign }
        val oldDamage = tick.value - params.keepEventsDays
        state.damageMap.values.removeAll { it.tick.value < oldDamage }
        state.stockMap.values.removeAll { it.isDormant }
    }

    private fun Campaign.isEnded() = status.isEnded

    /**
     * Tells marketing how the day's payments went. A day the company could not pay for counts as missed and is not
     * charged; enough missed days in a row suspends the campaign. Returns the events this caused.
     */
    fun reportPayments(tick: Tick, results: List<SettlementResult>): List<MarketingEvent> {
        val events = ArrayList<MarketingEvent>()
        for (r in results) {
            val id = MarketingLedger.dailyCampaign(r.intent.key) ?: continue
            val c = state.campaign(id) ?: continue
            when (r.status) {
                SettlementStatus.PAID, SettlementStatus.DEFERRED -> { c.missedPayments = 0; c.lastPaidTick = tick }
                SettlementStatus.UNPAID, SettlementStatus.INVALID -> {
                    c.missedPayments += 1
                    c.spentTotal = (c.spentTotal - r.intent.total).coerceAtLeast(Money.ZERO)
                    if (c.missedPayments >= params.missedPaymentsToSuspend && c.isLive) {
                        service.close(c, CampaignStatus.SUSPENDED, tick, Money.ZERO, Money.ZERO)
                        events += MarketingEvent.CampaignSuspended(c.id, c.company, tick)
                    }
                }
                SettlementStatus.DUPLICATE -> {}
            }
        }
        return events
    }

    companion object {
        /** A creative this stale is worth replacing. */
        const val WORN_LINE = 350_000L
    }
}

/** Share of voice: how much of the advertising pressure in a market a company has, and what that does to its effect. */
object Voice {
    /** Strength multiplier for a company holding [sharePpm] of the pressure: crowded markets blunt every message. */
    fun multiplierPpm(sharePpm: Long, params: MarketingParams): Long =
        (params.voiceFloorPpm + Ppm.mul(params.voiceSlopePpm, Ppm.clamp(sharePpm))).coerceIn(0L, 1_300_000L)
}
