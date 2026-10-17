package app.many.economytycoon.marketing

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.mulDiv

/**
 * One product in one region, as the planners see it. [baseRevenue] is the daily revenue the product earns with no
 * advertising at all, [marginPpm] the share of each sale left after the cost of goods. [freshnessPpm] and [voicePpm]
 * stand for the typical wear of a creative over a campaign and the typical share of voice, so plans are judged
 * on how they will work, not on a brand-new creative in an empty market.
 */
data class MarketContext(
    val good: GoodId,
    val region: RegionId,
    val audience: Audience,
    val baseRevenue: Money,
    val marginPpm: Long,
    val segment: TargetSegment = TargetSegment.EVERYONE,
    val creativeQualityPpm: Long = 600_000L,
    val deliveredQualityPpm: Long = Ppm.ONE,
    val freshnessPpm: Long = 700_000L,
    val voicePpm: Long = 600_000L,
    val taintPpm: Long = 0L,
)

/** The daily outcome of a plan once exposure has settled. */
data class PlanValue(
    val spend: Money,
    val awarenessPpm: Long,
    val loyaltyPpm: Long,
    val appealPpm: Long,
    val incrementalRevenue: Money,
    val incrementalProfit: Money,
) {
    val netProfit: Money get() = incrementalProfit - spend

    /** Gross profit returned per unit of money spent, ppm; one million is break-even. */
    val returnPpm: Long get() = if (spend.raw <= 0L) 0L else mulDiv(incrementalProfit.raw, Ppm.ONE, spend.raw)
}

/** Forward-looking estimates of what a plan of daily spends would earn, from the steady state of the brand model. */
object ModelledLift {
    fun impulse(ctx: MarketContext, channel: Channel, spend: Money, params: MarketingParams): Long {
        if (spend.raw <= 0L) return 0L
        val spec = channel.spec
        var v = ReachModel.regionImpulsePpm(spec, spend, ctx.audience, ctx.segment, params)
        v = Ppm.mul(v, ReachModel.wearMultiplierPpm(ctx.freshnessPpm, params))
        v = Ppm.mul(v, ReachModel.creativeMultiplierPpm(ctx.creativeQualityPpm, params))
        return Ppm.clamp(Ppm.mul(v, Voice.multiplierPpm(ctx.voicePpm, params)))
    }

    /** What the plan is worth per day once awareness and loyalty have settled. */
    fun value(ctx: MarketContext, plan: Map<Channel, Money>, params: MarketingParams): PlanValue {
        val impulses = LongArray(Channel.values().size)
        var spend = Money.ZERO
        for ((ch, money) in plan) {
            if (money.raw <= 0L) continue
            impulses[ch.ordinal] = impulse(ctx, ch, money, params)
            spend = spend + money
        }
        val (a, l) = StockDynamics.equilibrium(impulses, ctx.deliveredQualityPpm, params, ctx.taintPpm)
        val appeal = MarketingEffects.appealPpm(a, l, params)
        val revenue = Money(mulDiv(ctx.baseRevenue.raw, appeal - Ppm.ONE, Ppm.ONE))
        val profit = Money(mulDiv(revenue.raw, ctx.marginPpm, Ppm.ONE))
        return PlanValue(spend, a, l, appeal, revenue, profit)
    }

    /** Gross profit per extra unit of money from adding [step] to [channel] on top of [plan], ppm. */
    fun marginalReturnPpm(ctx: MarketContext, plan: Map<Channel, Money>, channel: Channel, step: Money, params: MarketingParams): Long {
        if (step.raw <= 0L) return 0L
        val before = value(ctx, plan, params)
        val bigger = plan.toMutableMap()
        bigger[channel] = (plan[channel] ?: Money.ZERO) + step
        val after = value(ctx, bigger, params)
        return mulDiv((after.incrementalProfit - before.incrementalProfit).raw, Ppm.ONE, step.raw)
    }

    /** Returns by channel when each is run alone at [spend]: the table the comparison screen shows. */
    fun compareChannels(ctx: MarketContext, spend: Money, params: MarketingParams): List<ChannelReturn> =
        Channel.ordered.map { ch ->
            val spec = ch.spec
            val s = Money.min(Money.max(spend, spec.minDailySpend), spec.maxDailySpend)
            val v = value(ctx, mapOf(ch to s), params)
            val reach = ReachModel.reachPpm(spec, s, ctx.audience, ctx.segment, params)
            ChannelReturn(
                ch, s, reach, v, marginalReturnPpm(ctx, mapOf(ch to s), ch, Money.max(Money(s.raw / 10L), Money.cents(100)), params),
                ctx.audience.fit(spec, ctx.segment),
            )
        }
}

data class ChannelReturn(
    val channel: Channel,
    val dailySpend: Money,
    val reachPpm: Long,
    val value: PlanValue,
    val marginalReturnPpm: Long,
    val fitPpm: Long,
)
