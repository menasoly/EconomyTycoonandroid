package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** How money turns into people reached. All results are parts per million. */
object ReachModel {
    /** Media in a rich region costs more, so the same budget buys less. One million is the average price level. */
    fun costIndexPpm(audience: Audience, params: MarketingParams): Long {
        val gap = audience.incomeIndexPpm - Ppm.ONE
        return (Ppm.ONE + Ppm.mul(gap, params.incomeCostPassThroughPpm)).coerceAtLeast(400_000L)
    }

    /** Share of the targeted people a day's [spend] reaches, on the saturating reach curve of [spec]. */
    fun reachPpm(spec: ChannelSpec, spend: Money, audience: Audience, segment: TargetSegment, params: MarketingParams): Long {
        val size = audience.segmentSize(segment)
        if (size <= 0L || spend.raw <= 0L) return 0L
        val effective = mulDiv(spend.raw, Ppm.ONE, costIndexPpm(audience, params))
        val perThousand = mulDiv(effective, 1_000L, size)
        if (perThousand <= 0L) return 0L
        return mulDiv(spec.reachCeilingPpm, perThousand, perThousand + spec.halfSpend.raw)
    }

    /** The share of the whole region a day's spend adds exposure for: reach inside the audience, fit and coverage. */
    fun regionImpulsePpm(spec: ChannelSpec, spend: Money, audience: Audience, segment: TargetSegment, params: MarketingParams): Long {
        val reach = reachPpm(spec, spend, audience, segment, params)
        return Ppm.mul(Ppm.mul(reach, audience.fit(spec, segment)), audience.regionCoverage(segment)).coerceAtMost(Ppm.ONE)
    }

    /** Daily spend that reaches [reachPpm] of the audience, or null if the channel cannot get that far. */
    fun spendForReach(spec: ChannelSpec, reachPpm: Long, audience: Audience, segment: TargetSegment, params: MarketingParams): Money? {
        val size = audience.segmentSize(segment)
        if (size <= 0L || reachPpm <= 0L || reachPpm >= spec.reachCeilingPpm) return null
        val perThousand = mulDiv(spec.halfSpend.raw, reachPpm, spec.reachCeilingPpm - reachPpm)
        val effective = mulDiv(perThousand, size, 1_000L)
        return Money(mulDiv(effective, costIndexPpm(audience, params), Ppm.ONE))
    }

    /** Reach added by the next [step] of spend on top of [spend]: the slope of the curve, for comparing channels. */
    fun marginalReachPpm(spec: ChannelSpec, spend: Money, step: Money, audience: Audience, segment: TargetSegment, params: MarketingParams): Long =
        reachPpm(spec, spend + step, audience, segment, params) - reachPpm(spec, spend, audience, segment, params)

    /** Strength of the creative as a multiplier: the middle quality is one, the best and worst differ by the spread. */
    fun creativeMultiplierPpm(qualityPpm: Long, params: MarketingParams): Long =
        Ppm.ONE + Ppm.mul(Ppm.clamp(qualityPpm) - 500_000L, params.creativeSpreadPpm * 2L)

    /** Strength left in a creative that has been seen too often. */
    fun wearMultiplierPpm(freshnessPpm: Long, params: MarketingParams): Long =
        params.wornEffectFloorPpm + Ppm.mul(Ppm.ONE - params.wornEffectFloorPpm, Ppm.clamp(freshnessPpm))

    /** Share of full strength a campaign has reached [daysRun] days in. */
    fun rampPpm(spec: ChannelSpec, daysRun: Int): Long =
        if (daysRun >= spec.rampDays) Ppm.ONE else Ppm.ratio((daysRun + 1).toLong(), spec.rampDays.toLong() + 1L)
}
