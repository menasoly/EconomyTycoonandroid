package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** What a storage unit does hour by hour for one day. Both arrays are kilowatts; discharge is what reaches the grid. */
class StoragePlan(
    val unit: UnitId,
    val chargeKw: LongArray,
    val dischargeKw: LongArray,
    /** Energy taken out of the store in each discharge hour, before the round trip loss. */
    val drawKw: LongArray,
    val expectedProfit: Money,
    val endLevel: Int,
) {
    val chargeHours: Int get() = chargeKw.count { it > 0L }
    val dischargeHours: Int get() = dischargeKw.count { it > 0L }
    val isIdle: Boolean get() = chargeHours == 0 && dischargeHours == 0

    companion object {
        fun idle(unit: UnitId): StoragePlan {
            val h = GridUnits.HOURS_PER_DAY
            return StoragePlan(unit, LongArray(h), LongArray(h), LongArray(h), Money.ZERO, 0)
        }
    }
}

/**
 * Plans a store's day to buy power when it is cheap and sell it when it is dear. The store is worked in whole steps
 * of full power for one hour, so its content is a level from empty to its hours of capacity. A dynamic programme over
 * the 24 hours and the levels finds the most profitable schedule for a price forecast; what is left in the store at
 * the end of the day is valued at the day's average price so the plan does not dump it at the cheapest hour.
 * Every choice is made by comparing integers; ties go to doing nothing, then discharging, then charging, and equal
 * prices are told apart by the hour so buying falls early and selling late. The profit reported is that of the
 * schedule at the forecast prices, leaving out the terminal value of what stays in the store.
 */
object StoragePlanner {
    /** The level a store holding [socKwh] is at, counted in steps of [powerKw] and capped at [hours]. */
    fun levelOf(socKwh: Long, powerKw: Long, hours: Int): Int =
        if (powerKw <= 0L) 0 else (socKwh / powerKw).coerceIn(0L, hours.toLong()).toInt()

    /**
     * Prices a plan can rely on. A single hour that sticks out of its neighbours is often an accident of the units'
     * start-up and ramp limits and would not survive the store's own charging, so the store buys at the higher of an
     * hour's price and the median of it and its neighbours, and sells at the lower.
     */
    fun cautious(forecast: List<Money>): Pair<List<Money>, List<Money>> {
        val n = forecast.size
        val median = List(n) { i ->
            val trio = listOf(forecast[maxOf(i - 1, 0)].raw, forecast[i].raw, forecast[minOf(i + 1, n - 1)].raw).sorted()
            Money(trio[1])
        }
        return List(n) { Money.max(forecast[it], median[it]) } to List(n) { Money.min(forecast[it], median[it]) }
    }

    fun plan(
        unit: UnitId, powerKw: Long, hours: Int, startLevel: Int, roundTripPpm: Long, runningCostPerMwh: Money, forecast: List<Money>,
        sellForecast: List<Money> = forecast,
    ): StoragePlan {
        val h = GridUnits.HOURS_PER_DAY
        require(forecast.size == h && sellForecast.size == h) { "forecast needs 24 prices" }
        val charge = LongArray(h)
        val discharge = LongArray(h)
        val draw = LongArray(h)
        if (powerKw <= 0L || hours <= 0) return StoragePlan(unit, charge, discharge, draw, Money.ZERO, startLevel)

        val delivered = mulDiv(powerKw, roundTripPpm, Rate.ONE_PPM, RoundingMode.DOWN)
        val levels = hours + 1
        val mean = Money(sellForecast.sumOf { it.raw } / h)
        val terminal = GridUnits.cost(mean, delivered) .raw
        val cost = LongArray(h) { GridUnits.cost(forecast[it], powerKw).raw }
        val gain = LongArray(h) { GridUnits.cost(sellForecast[it] - runningCostPerMwh, delivered).raw }
        // equal prices are split by one raw unit an hour, so the store buys early and sells late rather than the reverse
        val costBias = LongArray(h) { cost[it] + it }
        val gainBias = LongArray(h) { gain[it] + it }

        // best[t][l]: most profit from hour t to the end of the day when the store starts hour t at level l
        val best = Array(h + 1) { LongArray(levels) }
        val choice = Array(h) { IntArray(levels) }
        for (l in 0 until levels) best[h][l] = terminal * l
        for (t in h - 1 downTo 0) {
            for (l in 0 until levels) {
                var top = best[t + 1][l]
                var pick = 0
                if (l > 0) {
                    val v = gainBias[t] + best[t + 1][l - 1]
                    if (v > top) { top = v; pick = -1 }
                }
                if (l < levels - 1) {
                    val v = -costBias[t] + best[t + 1][l + 1]
                    if (v > top) { top = v; pick = 1 }
                }
                best[t][l] = top
                choice[t][l] = pick
            }
        }
        var l = startLevel.coerceIn(0, hours)
        for (t in 0 until h) {
            when (choice[t][l]) {
                -1 -> { discharge[t] = delivered; draw[t] = powerKw; l-- }
                1 -> { charge[t] = powerKw; l++ }
            }
        }
        var profit = 0L
        for (t in 0 until h) {
            if (discharge[t] > 0L) profit += gain[t]
            if (charge[t] > 0L) profit -= cost[t]
        }
        return StoragePlan(unit, charge, discharge, draw, Money(profit), l)
    }
}
