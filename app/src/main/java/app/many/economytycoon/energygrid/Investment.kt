package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** A unit the operator could retire, as the planner sees it. */
data class RetireCandidate(val unit: UnitId, val tech: Technology, val ageTicks: Long, val firmKw: Long, val marginalCost: Money)

data class BuildOrder(val tech: Technology, val capacityKw: Long)

/** What the planner looks at: the grid's peak and growth, its firm capacity by technology, and its fleet. */
data class PlanningView(
    val peakDemandKw: Long,
    /** Expected growth of the peak over the next year, in ppm of the peak. */
    val growthPpm: Long,
    val firmKw: Long,
    val firmUnderConstructionKw: Long,
    /** Firm capacity of each technology, live and under construction, in the order of [Technology.values]. */
    val firmByTech: List<Long>,
    val candidates: List<RetireCandidate>,
    val targetReservePpm: Long,
    val buildable: Set<Technology>,
    val prices: FuelPrices,
    /** Largest unit the operator will order of a kind, in kilowatts. */
    val maxUnitKw: Long = 300_000L,
    /** No technology may supply more than this share of firm capacity after a build. */
    val maxShareOfFirmPpm: Long = 600_000L,
    /** Spare firm capacity above the requirement before an old unit is retired. */
    val retireBandPpm: Long = 150_000L,
) {
    init { require(firmByTech.size == Technology.values().size) { "one firm figure per technology" } }
}

data class PlanResult(
    val requirementKw: Long,
    val shortfallKw: Long,
    val builds: List<BuildOrder>,
    val retire: List<UnitId>,
    /** Firm capacity over the forecast peak, in ppm; negative when the grid is short. */
    val reserveMarginPpm: Long,
)

/**
 * The grid operator's investment rule. It builds when the firm capacity it has, and has on order, falls short of the
 * forecast peak plus the target reserve, choosing the cheapest technology for a firm kilowatt over its life that would
 * not take too large a share of the fleet. It retires an old unit when the fleet is comfortably long.
 */
object InvestmentPlanner {
    /** Share of the year a unit of this kind is expected to run, used to price its fuel. */
    fun expectedLoadFactorPpm(tech: Technology): Long = when (tech) {
        Technology.COAL -> 600_000L
        Technology.GAS -> 350_000L
        Technology.NUCLEAR -> 900_000L
        Technology.HYDRO -> 450_000L
        Technology.WIND -> 300_000L
        Technology.SOLAR -> 200_000L
        Technology.STORAGE -> 100_000L
    }

    /** Levelised cost of a firm kilowatt for a year: capital, fixed upkeep and expected fuel, over the firm share. */
    fun costPerFirmKwYear(tech: Technology, prices: FuelPrices): Money {
        val p = tech.profile
        val capital = mulDiv(p.capexPerKw.raw, Calendar.DAYS_PER_YEAR.toLong(), p.lifetimeTicks.toLong(), RoundingMode.HALF_EVEN)
        val hours = Calendar.DAYS_PER_YEAR.toLong() * GridUnits.HOURS_PER_DAY
        val energy = mulDiv(mulDiv(tech.marginalCost(prices).raw, hours, GridUnits.KW_PER_MW), expectedLoadFactorPpm(tech), Rate.ONE_PPM, RoundingMode.HALF_EVEN)
        val total = capital + p.fixedOmPerKwYear.raw + energy
        return Money(mulDiv(total, Rate.ONE_PPM, p.capacityCreditPpm, RoundingMode.HALF_EVEN))
    }

    fun plan(v: PlanningView): PlanResult {
        val forecast = v.peakDemandKw + mulDiv(v.peakDemandKw, v.growthPpm, Rate.ONE_PPM, RoundingMode.UP)
        val required = CapacityMarket.requirementKw(forecast, v.targetReservePpm)
        val have = v.firmKw + v.firmUnderConstructionKw
        val margin = if (forecast <= 0L) Rate.ONE_PPM else mulDiv(have - forecast, Rate.ONE_PPM, forecast, RoundingMode.FLOOR)
        val shortfall = (required - have).coerceAtLeast(0L)
        val builds = ArrayList<BuildOrder>()
        if (shortfall > 0L) {
            var left = shortfall
            val byTech = v.firmByTech.toLongArray()
            var total = have
            val options = Technology.values().filter { it in v.buildable && !it.isStorage && it.profile.capacityCreditPpm > 0L }
                .sortedWith(compareBy({ costPerFirmKwYear(it, v.prices).raw }, { it.ordinal }))
            var guard = 0
            while (left > 0L && options.isNotEmpty() && guard++ < 8) {
                val pick = options.firstOrNull { t ->
                    val room = mulDiv(total + 1L, v.maxShareOfFirmPpm, Rate.ONE_PPM, RoundingMode.DOWN) - byTech[t.ordinal]
                    room > 0L
                } ?: options.first()
                val credit = pick.profile.capacityCreditPpm
                val wantKw = mulDiv(left, Rate.ONE_PPM, credit, RoundingMode.UP)
                val kw = minOf(wantKw, v.maxUnitKw)
                val firm = CapacityMarket.firmKw(pick, kw)
                if (firm <= 0L) break
                builds += BuildOrder(pick, kw)
                byTech[pick.ordinal] += firm
                total += firm
                left -= firm
            }
        }
        val retire = ArrayList<UnitId>()
        if (shortfall == 0L) {
            val band = mulDiv(required, Rate.ONE_PPM + v.retireBandPpm, Rate.ONE_PPM, RoundingMode.UP)
            val worn = v.candidates.filter { it.ageTicks * 10L >= it.tech.profile.lifetimeTicks * 9L }
                .sortedWith(compareByDescending<RetireCandidate> { it.ageTicks * Rate.ONE_PPM / it.tech.profile.lifetimeTicks }.thenByDescending { it.marginalCost.raw }.thenBy { it.unit })
            for (c in worn) {
                if (have - c.firmKw >= band) { retire += c.unit; break }
            }
        }
        return PlanResult(required, shortfall, builds, retire, margin)
    }
}
