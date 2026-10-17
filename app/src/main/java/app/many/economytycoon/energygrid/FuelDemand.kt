package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** Heat burned of one fuel, in kilowatt-hours, and what it cost at the prices given. */
data class FuelUse(val fuel: FuelKind, val heatKwh: Long, val cost: Money)

/**
 * How much fuel the plants burn. The grids price fuel inside the pool but do not buy it; the wiring uses these figures
 * to ask the markets for coal, gas and nuclear fuel, so the goods that go in match the energy that came out.
 */
object FuelDemand {
    /** Heat needed to make [generationKwh] at [efficiencyPpm]; zero for plants that burn nothing. */
    fun heatKwh(tech: Technology, generationKwh: Long, efficiencyPpm: Long = tech.profile.efficiencyPpm): Long {
        if (!tech.isThermal || generationKwh <= 0L || efficiencyPpm <= 0L) return 0L
        return mulDiv(generationKwh, Rate.ONE_PPM, efficiencyPpm, RoundingMode.UP)
    }

    private fun usesOf(generationKwh: List<Long>, prices: FuelPrices): List<FuelUse> {
        require(generationKwh.size == Technology.values().size) { "one total per technology" }
        return FuelKind.values().filter { it != FuelKind.NONE }.map { fuel ->
            val heat = Technology.values().filter { it.profile.fuel == fuel }.sumOf { heatKwh(it, generationKwh[it.ordinal]) }
            FuelUse(fuel, heat, GridUnits.cost(prices.of(fuel), heat))
        }
    }

    fun ofDay(day: DaySummary, prices: FuelPrices): List<FuelUse> = usesOf(day.generationKwh, prices)

    fun ofWindow(history: List<DaySummary>, days: Int, prices: FuelPrices): List<FuelUse> {
        val total = LongArray(Technology.values().size)
        for (d in history.takeLast(days)) for (i in total.indices) total[i] += d.generationKwh[i]
        return usesOf(total.toList(), prices)
    }

    /** Fuel cost for each megawatt-hour served over the window, a first measure of how costly the grid's mix is. */
    fun costPerMwhServed(history: List<DaySummary>, days: Int, prices: FuelPrices): Money {
        val served = history.takeLast(days).sumOf { it.servedKwh }
        if (served <= 0L) return Money.ZERO
        val cost = Money.sum(ofWindow(history, days, prices).map { it.cost })
        return Money(mulDiv(cost.raw, GridUnits.KW_PER_MW, served, RoundingMode.HALF_EVEN))
    }

    /** What the window's emissions cost at [perTonne]. */
    fun carbonBill(history: List<DaySummary>, days: Int, perTonne: Money): Money {
        val kg = history.takeLast(days).sumOf { it.carbonKg }
        return Money(mulDiv(perTonne.raw, kg, 1_000L, RoundingMode.HALF_EVEN))
    }
}
