package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** How boldly a computer-controlled utility invests. Every ratio is in ppm. */
data class UtilityStance(
    /** Appetite for building beyond what the grid strictly needs. */
    val expansionPpm: Long,
    /** Willingness to build with a thin cash cushion and to sell without a fixed-price cover. */
    val riskPpm: Long,
    /** Longest payback it accepts for a new unit, in years. */
    val maxPaybackYears: Int,
) {
    init { require(maxPaybackYears > 0) { "payback must be positive" } }

    companion object {
        val CAUTIOUS = UtilityStance(150_000L, 200_000L, 8)
        val BALANCED = UtilityStance(400_000L, 500_000L, 12)
        val BOLD = UtilityStance(800_000L, 800_000L, 18)
    }
}

/** A unit of the utility, with what it is expected to earn per kilowatt in a year after fixed upkeep. */
data class UtilityAsset(
    val unit: UnitId,
    val tech: Technology,
    val capacityKw: Long,
    val ageTicks: Long,
    val status: UnitStatus,
    val netPerKwYear: Money,
    val hasPpa: Boolean = false,
) {
    val worn: Boolean get() = ageTicks * 100L >= tech.profile.lifetimeTicks * 95L
}

/** What the utility sees of one grid when it plans. */
data class UtilityView(
    val company: CompanyId,
    val grid: GridId,
    val tick: Long,
    val cash: Money,
    val stance: UtilityStance,
    val reservePpm: Long,
    val targetReservePpm: Long,
    /** Recent average pool price. */
    val averagePrice: Money,
    val prices: FuelPrices,
    val assets: List<UtilityAsset>,
    val buildable: Set<Technology>,
    val firmShortfallKw: Long,
    /** Units the utility already has on order here. */
    val ordersOpen: Int,
    val maxUnitKw: Long = 300_000L,
) {
    val short: Boolean get() = reservePpm < targetReservePpm
}

sealed class UtilityMove {
    data class Build(val tech: Technology, val capacityKw: Long) : UtilityMove()
    data class Retire(val unit: UnitId) : UtilityMove()
    data class Mothball(val unit: UnitId) : UtilityMove()
    data class Restart(val unit: UnitId) : UtilityMove()
    /** A wish to sell [volumeKw] of a unit at a fixed price; the market side finds the buyer. */
    data class SeekPpa(val unit: UnitId, val volumeKw: Long, val strike: Money, val days: Int) : UtilityMove()
}

/** A move and what the service said about it; [outcome] is null for moves that need a counterparty. */
data class MoveResult(val move: UtilityMove, val outcome: Outcome<*>?)

/**
 * The build, retire and contract rule for utilities that compete with the player. It is pure: the view goes in and
 * a short list of moves comes out, always in the same order for the same view.
 */
object UtilityAi {
    private const val HOURS_PER_YEAR = Calendar.DAYS_PER_YEAR.toLong() * GridUnits.HOURS_PER_DAY

    /** Price a new unit would be paid on average: the recent price, lifted when the grid is short of reserve. */
    fun expectedPrice(v: UtilityView): Money {
        if (!v.short) return v.averagePrice
        val gap = (v.targetReservePpm - v.reservePpm).coerceIn(0L, Rate.ONE_PPM)
        val lift = (gap * 2L).coerceAtMost(Rate.ONE_PPM)
        return Money(v.averagePrice.raw + mulDiv(v.averagePrice.raw, lift, Rate.ONE_PPM, RoundingMode.HALF_EVEN))
    }

    /** Earnings of a kilowatt of [tech] in a year at [price], before capital and fixed upkeep. */
    fun grossPerKwYear(tech: Technology, price: Money, prices: FuelPrices): Money {
        val perMwh = (price - tech.marginalCost(prices)).coerceAtLeast(Money.ZERO)
        val perKwAtFullLoad = mulDiv(perMwh.raw, HOURS_PER_YEAR, GridUnits.KW_PER_MW, RoundingMode.HALF_EVEN)
        return Money(mulDiv(perKwAtFullLoad, InvestmentPlanner.expectedLoadFactorPpm(tech), Rate.ONE_PPM, RoundingMode.HALF_EVEN))
    }

    /** Net yearly earnings per kilowatt, after fixed upkeep. */
    fun netPerKwYear(tech: Technology, price: Money, prices: FuelPrices): Money =
        grossPerKwYear(tech, price, prices) - tech.profile.fixedOmPerKwYear

    /** Years to earn back the capital of a new kilowatt, or null when it never would. */
    fun paybackYears(tech: Technology, price: Money, prices: FuelPrices): Long? {
        val net = netPerKwYear(tech, price, prices)
        if (net.raw <= 0L) return null
        return mulDiv(tech.profile.capexPerKw.raw, 1L, net.raw, RoundingMode.UP)
    }

    /** Cash the stance wants to keep for every unit of spending: more cautious utilities keep more. */
    fun cashCoverPpm(stance: UtilityStance): Long = Rate.ONE_PPM + (Rate.ONE_PPM - stance.riskPpm).coerceIn(0L, Rate.ONE_PPM)

    fun decide(v: UtilityView): List<UtilityMove> {
        val moves = ArrayList<UtilityMove>()
        val price = expectedPrice(v)
        // old plant out first, when the grid can spare it
        val spare = !v.short && v.firmShortfallKw <= 0L
        val retired = HashSet<UnitId>()
        if (spare) {
            val worn = v.assets.filter { it.status != UnitStatus.RETIRED && it.status != UnitStatus.BUILDING && it.worn && it.netPerKwYear.raw < 0L }
                .sortedWith(compareBy<UtilityAsset> { it.netPerKwYear.raw }.thenBy { it.unit })
            worn.firstOrNull()?.let { moves += UtilityMove.Retire(it.unit); retired += it.unit }
        }
        // unprofitable but young plant waits, profitable idle plant comes back
        for (a in v.assets.sortedBy { it.unit }) {
            if (a.unit in retired) continue
            when {
                a.status == UnitStatus.MOTHBALLED && (v.short || netPerKwYear(a.tech, price, v.prices).raw > 0L) -> moves += UtilityMove.Restart(a.unit)
                a.status == UnitStatus.AVAILABLE && spare && !a.tech.isStorage && a.tech != Technology.NUCLEAR && !a.worn &&
                    netPerKwYear(a.tech, price, v.prices).raw < 0L && a.netPerKwYear.raw < 0L -> moves += UtilityMove.Mothball(a.unit)
            }
        }
        build(v, price)?.let { moves += it }
        // cautious utilities cover part of a thermal unit's output with a fixed price
        if (v.stance.riskPpm < 500_000L) {
            val candidates = v.assets.filter { it.status == UnitStatus.AVAILABLE && !it.hasPpa && it.unit !in retired && it.tech.profile.fuel != FuelKind.NONE && !it.tech.isStorage }
            candidates.minByOrNull { it.unit }?.let { a ->
                val volume = mulDiv(a.capacityKw, 400_000L, Rate.ONE_PPM, RoundingMode.DOWN)
                val floor = Money(mulDiv(a.tech.marginalCost(v.prices).raw, 1_150_000L, Rate.ONE_PPM, RoundingMode.UP))
                val strike = Money.max(floor, Money(mulDiv(v.averagePrice.raw, 900_000L, Rate.ONE_PPM, RoundingMode.UP)))
                if (volume > 0L) moves += UtilityMove.SeekPpa(a.unit, volume, strike, Calendar.DAYS_PER_YEAR)
            }
        }
        return moves
    }

    private fun build(v: UtilityView, price: Money): UtilityMove.Build? {
        val parallel = 1 + (v.stance.expansionPpm / 400_000L).toInt()
        if (v.ordersOpen >= parallel) return null
        val wanted = v.short || v.firmShortfallKw > 0L || v.stance.expansionPpm >= 500_000L
        if (!wanted) return null
        val options = Technology.values().filter { it in v.buildable && !it.isStorage }.mapNotNull { t ->
            val years = paybackYears(t, price, v.prices) ?: return@mapNotNull null
            if (years > v.stance.maxPaybackYears) null else Triple(t, years, netPerKwYear(t, price, v.prices))
        }
        // best net earnings per kilowatt first; payback and technology order settle ties
        val best = options.sortedWith(compareByDescending<Triple<Technology, Long, Money>> { it.third.raw }.thenBy { it.second }.thenBy { it.first.ordinal }).firstOrNull() ?: return null
        val tech = best.first
        val credit = tech.profile.capacityCreditPpm.coerceAtLeast(1L)
        val need = if (v.firmShortfallKw > 0L) mulDiv(v.firmShortfallKw, Rate.ONE_PPM, credit, RoundingMode.UP) else 0L
        val ambition = mulDiv(v.maxUnitKw, v.stance.expansionPpm, Rate.ONE_PPM, RoundingMode.DOWN)
        val size = minOf(v.maxUnitKw, maxOf(need, ambition))
        val perKw = Money(mulDiv(tech.profile.capexPerKw.raw, cashCoverPpm(v.stance), Rate.ONE_PPM, RoundingMode.UP))
        if (perKw.raw <= 0L) return null
        val affordable = v.cash.raw / perKw.raw
        val kw = minOf(size, affordable)
        return if (kw >= MIN_BUILD_KW) UtilityMove.Build(tech, kw) else null
    }

    const val MIN_BUILD_KW = 5_000L

    /** Carries out the moves a utility chose; a move the rules refuse is reported, not hidden. */
    fun execute(service: EnergyGridService, view: UtilityView, moves: List<UtilityMove>): List<MoveResult> = moves.map { m ->
        val outcome: Outcome<*>? = when (m) {
            is UtilityMove.Build -> service.orderUnit(view.grid, m.tech, m.capacityKw, view.company, Tick(view.tick))
            is UtilityMove.Retire -> service.retireUnit(m.unit)
            is UtilityMove.Mothball -> service.mothball(m.unit)
            is UtilityMove.Restart -> service.restart(m.unit, Tick(view.tick))
            is UtilityMove.SeekPpa -> null
        }
        MoveResult(m, outcome)
    }

    /** Builds the view of a company's position on a grid from the live state. */
    fun viewOf(
        state: EnergyGridState, grid: Grid, company: CompanyId, tick: Long, cash: Money, stance: UtilityStance, prices: FuelPrices, peakKw: Long,
    ): UtilityView {
        val avg = GridAnalytics.windowAveragePrice(grid.history, 30)
        val mine = grid.units.filter { it.owner == company && it.status != UnitStatus.RETIRED }
        val ppaUnits = state.ppasOfCompany(company).filter { it.status.live && it.seller == company }.mapNotNull { it.unit }.toSet()
        val assets = mine.filter { it.status != UnitStatus.BUILDING }.map { u ->
            UtilityAsset(u.id, u.tech, u.capacityKw, (tick - u.commissioned).coerceAtLeast(0L), u.status, netPerKwYear(u.tech, avg, prices), u.id in ppaUnits)
        }
        val forecast = peakKw + mulDiv(peakKw, grid.params.growthPpm, Rate.ONE_PPM, RoundingMode.UP)
        val required = CapacityMarket.requirementKw(forecast, grid.params.targetReservePpm)
        val shortfall = (required - grid.firmKw() - grid.firmUnderConstructionKw()).coerceAtLeast(0L)
        return UtilityView(
            company, grid.id, tick, cash, stance, GridAnalytics.reserveMarginPpm(grid, peakKw), grid.params.targetReservePpm, avg, prices,
            assets, grid.params.buildable(), shortfall, mine.count { it.status == UnitStatus.BUILDING },
        )
    }
}
