package app.many.economytycoon.marketing

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** The limits a plan has to respect. */
data class OptimiserConstraints(
    val totalDaily: Money,
    /** The size of each increment the greedy search adds. */
    val step: Money = Money.units(50),
    /** The most of the total any single channel may take, ppm. */
    val maxChannelSharePpm: Long = 700_000L,
    /** The most of the total any single region may take, ppm. */
    val maxRegionSharePpm: Long = Ppm.ONE,
    /** Stop adding money once the next step returns less gross profit than this per unit spent, ppm. */
    val minReturnPpm: Long = Ppm.ONE,
    /** Spend the whole budget as long as every step still returns something, ignoring [minReturnPpm]. */
    val spendAll: Boolean = false,
    val allowedChannels: Set<Channel> = Channel.values().toSet(),
    val maxSteps: Int = 4_000,
) {
    init {
        require(step.raw > 0L) { "step must be positive" }
        require(totalDaily.raw >= 0L) { "budget cannot be negative" }
    }
}

data class Allocation(
    val market: MarketKey,
    val channel: Channel,
    val dailyBudget: Money,
    val reachPpm: Long,
    /** Gross profit per unit of the last step added here, ppm. */
    val lastReturnPpm: Long,
)

enum class StopReason(val label: String) {
    BUDGET_USED("The whole budget is allocated"),
    DIMINISHING("Further spending returns less than it costs"),
    CONSTRAINED("Every remaining option hits a limit"),
    NO_OPTIONS("There is nothing to advertise"),
}

data class OptimiserResult(
    val allocations: List<Allocation>,
    val budget: Money,
    val spend: Money,
    val expectedRevenue: Money,
    val expectedProfit: Money,
    val stopReason: StopReason,
    val steps: Int,
    val valueByMarket: Map<MarketKey, PlanValue>,
) {
    val leftover: Money get() = budget - spend
    val netProfit: Money get() = expectedProfit - spend
    val returnPpm: Long get() = if (spend.raw <= 0L) 0L else mulDiv(expectedProfit.raw, Ppm.ONE, spend.raw)
    fun forMarket(m: MarketKey): List<Allocation> = allocations.filter { it.market == m }
    fun byChannel(): Map<Channel, Money> {
        val out = java.util.EnumMap<Channel, Money>(Channel::class.java)
        for (a in allocations) out[a.channel] = (out[a.channel] ?: Money.ZERO) + a.dailyBudget
        return Channel.ordered.filter { out.containsKey(it) }.associateWith { out.getValue(it) }
    }
}

/**
 * Spreads a daily budget over channels and markets by always adding the next step where it earns the most gross
 * profit per unit, until the budget is gone, the returns fall below the threshold, or limits stop it. Because reach
 * curves flatten, the greedy choice is also a good one: the first money goes to the cheap, high-reach options.
 */
class BudgetOptimiser(private val params: MarketingParams = MarketingParams()) {

    fun optimise(contexts: List<MarketContext>, c: OptimiserConstraints): OptimiserResult {
        val markets = contexts.sortedWith(compareBy({ it.good }, { it.region }))
        if (markets.isEmpty() || c.totalDaily.raw <= 0L) {
            return OptimiserResult(emptyList(), c.totalDaily, Money.ZERO, Money.ZERO, Money.ZERO, StopReason.NO_OPTIONS, 0, emptyMap())
        }
        val plans = java.util.TreeMap<MarketKey, MutableMap<Channel, Money>>()
        val keys = markets.map { MarketKey(it.good, it.region) }
        for (k in keys) plans[k] = java.util.EnumMap(Channel::class.java)
        val current = java.util.TreeMap<MarketKey, PlanValue>()
        for ((i, k) in keys.withIndex()) current[k] = ModelledLift.value(markets[i], emptyMap(), params)
        val lastReturn = java.util.TreeMap<Pair<MarketKey, Channel>, Long>(compareBy<Pair<MarketKey, Channel>>({ it.first }, { it.second }))

        var spent = Money.ZERO
        var steps = 0
        var stop = StopReason.BUDGET_USED
        while (true) {
            if (steps >= c.maxSteps) { stop = StopReason.CONSTRAINED; break }
            val remaining = c.totalDaily - spent
            var bestRet = Long.MIN_VALUE
            var bestMarket = -1
            var bestChannel: Channel? = null
            var bestInc = Money.ZERO
            for ((i, ctx) in markets.withIndex()) {
                val k = keys[i]
                val plan = plans.getValue(k)
                for (ch in Channel.ordered) {
                    if (ch !in c.allowedChannels) continue
                    val have = plan[ch] ?: Money.ZERO
                    val inc = increment(ch, have, c.step)
                    if (inc.raw <= 0L || inc > remaining) continue
                    if (have + inc > ch.spec.maxDailySpend) continue
                    if (!withinShares(c, plans, k, ch, inc)) continue
                    val trial = copyOf(plan); trial[ch] = have + inc
                    val after = ModelledLift.value(ctx, trial, params)
                    val ret = mulDiv((after.incrementalProfit - current.getValue(k).incrementalProfit).raw, Ppm.ONE, inc.raw)
                    if (ret > bestRet) { bestRet = ret; bestMarket = i; bestChannel = ch; bestInc = inc }
                }
            }
            val ch = bestChannel
            if (ch == null) { stop = if (remaining.raw < c.step.raw) StopReason.BUDGET_USED else StopReason.CONSTRAINED; break }
            val worthIt = if (c.spendAll) bestRet > 0L else bestRet >= c.minReturnPpm
            if (!worthIt) { stop = StopReason.DIMINISHING; break }
            val k = keys[bestMarket]
            val plan = plans.getValue(k)
            plan[ch] = (plan[ch] ?: Money.ZERO) + bestInc
            current[k] = ModelledLift.value(markets[bestMarket], plan, params)
            lastReturn[k to ch] = bestRet
            spent = spent + bestInc
            steps++
        }
        prune(markets, keys, plans, current, c)
        return assemble(markets, keys, plans, current, lastReturn, c, stop, steps)
    }

    private fun copyOf(plan: Map<Channel, Money>): MutableMap<Channel, Money> =
        java.util.EnumMap<Channel, Money>(Channel::class.java).also { it.putAll(plan) }

    /** The first money on a channel has to cover its minimum booking; after that it grows by one step. */
    private fun increment(ch: Channel, have: Money, step: Money): Money =
        if (have.raw <= 0L) Money.max(step, ch.spec.minDailySpend) else step

    private fun withinShares(c: OptimiserConstraints, plans: Map<MarketKey, Map<Channel, Money>>, market: MarketKey, ch: Channel, inc: Money): Boolean {
        var channelTotal = inc
        var regionTotal = inc
        for ((k, plan) in plans) {
            for ((cc, m) in plan) {
                if (cc == ch) channelTotal = channelTotal + m
                if (k.region == market.region) regionTotal = regionTotal + m
            }
        }
        val channelCap = Money(mulDiv(c.totalDaily.raw, c.maxChannelSharePpm, Ppm.ONE))
        val regionCap = Money(mulDiv(c.totalDaily.raw, c.maxRegionSharePpm, Ppm.ONE))
        return channelTotal <= channelCap.coerceAtLeast(c.step) && regionTotal <= regionCap.coerceAtLeast(c.step)
    }

    /** Drops any allocation whose removal improves net profit: a booking that does not earn back its own cost. */
    private fun prune(
        markets: List<MarketContext>, keys: List<MarketKey>, plans: Map<MarketKey, MutableMap<Channel, Money>>,
        current: MutableMap<MarketKey, PlanValue>, c: OptimiserConstraints,
    ) {
        if (c.spendAll) return
        for ((i, k) in keys.withIndex()) {
            val plan = plans.getValue(k)
            for (ch in Channel.ordered) {
                val have = plan[ch] ?: continue
                val without = copyOf(plan); without.remove(ch)
                val a = ModelledLift.value(markets[i], plan, params)
                val b = ModelledLift.value(markets[i], without, params)
                if (b.netProfit > a.netProfit && have.raw > 0L) {
                    plan.remove(ch)
                }
            }
            current[k] = ModelledLift.value(markets[i], plan, params)
        }
    }

    private fun assemble(
        markets: List<MarketContext>, keys: List<MarketKey>, plans: Map<MarketKey, Map<Channel, Money>>, current: Map<MarketKey, PlanValue>,
        lastReturn: Map<Pair<MarketKey, Channel>, Long>, c: OptimiserConstraints, stop: StopReason, steps: Int,
    ): OptimiserResult {
        val allocations = ArrayList<Allocation>()
        var spend = Money.ZERO
        var revenue = Money.ZERO
        var profit = Money.ZERO
        val values = java.util.TreeMap<MarketKey, PlanValue>()
        for ((i, k) in keys.withIndex()) {
            val plan = plans.getValue(k)
            for (ch in Channel.ordered) {
                val m = plan[ch] ?: continue
                allocations += Allocation(k, ch, m, ReachModel.reachPpm(ch.spec, m, markets[i].audience, markets[i].segment, params), lastReturn[k to ch] ?: 0L)
            }
            val v = current.getValue(k)
            values[k] = v
            spend = spend + v.spend
            revenue = revenue + v.incrementalRevenue
            profit = profit + v.incrementalProfit
        }
        return OptimiserResult(allocations, c.totalDaily, spend, revenue, profit, if (allocations.isEmpty() && stop == StopReason.BUDGET_USED) StopReason.DIMINISHING else stop, steps, values)
    }
}
