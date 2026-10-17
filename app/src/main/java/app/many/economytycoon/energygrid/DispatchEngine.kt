package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** Market settings of a grid. The cap is the highest price the pool may pay; the floor is the lowest. */
data class DispatchParams(
    val priceCap: Money = Money.units(500),
    val priceFloor: Money = Money.ZERO,
    /** When spare capacity falls under this share of demand, the price rises towards the cap. */
    val scarcityThresholdPpm: Long = 100_000L,
)

/** A unit as the dispatch sees it in one hour: what it could do, what it costs and how fast it can move. */
data class DispatchUnit(
    val id: UnitId,
    val tech: Technology,
    val availableKw: Long,
    val marginalCost: Money,
    val prevKw: Long,
    val rampUpKw: Long,
    val rampDownKw: Long,
    val minStableKw: Long,
) {
    init { require(availableKw >= 0L && prevKw >= 0L && rampUpKw >= 0L && rampDownKw >= 0L && minStableKw >= 0L) { "negative unit limit" } }
}

/** Everything the dispatch of one grid-hour needs. */
data class DispatchInput(
    val demandKw: Long,
    val units: List<DispatchUnit>,
    /** Storage discharging into the grid, which is taken before any other supply. */
    val storageDischargeKw: Long = 0L,
    /** Storage charging, an extra load. */
    val storageChargeKw: Long = 0L,
    /** Power arriving over interconnectors, after losses. */
    val importKw: Long = 0L,
    /** Power sent out over interconnectors, an extra load. */
    val exportKw: Long = 0L,
    val params: DispatchParams = DispatchParams(),
) {
    init {
        require(demandKw >= 0L && storageDischargeKw >= 0L && storageChargeKw >= 0L && importKw >= 0L && exportKw >= 0L) { "negative flow" }
    }

    /** Load the units have to cover. */
    val netDemandKw: Long get() = demandKw + storageChargeKw + exportKw - storageDischargeKw - importKw
}

/** What one unit does in one hour. [floorKw] and [ceilingKw] are the limits that ramping and availability allowed. */
data class UnitDispatch(
    val id: UnitId,
    val tech: Technology,
    val outputKw: Long,
    val availableKw: Long,
    val curtailedKw: Long,
    val marginalCost: Money,
    val floorKw: Long,
    val ceilingKw: Long,
)

/** The outcome of dispatching one grid-hour. */
class DispatchResult(
    val input: DispatchInput,
    /** Units in merit order, cheapest first. */
    val units: List<UnitDispatch>,
    val price: Money,
    /** Price before any scarcity uplift. */
    val basePrice: Money,
    val setter: UnitId?,
    val unservedKw: Long,
    val curtailedKw: Long,
    /** Must-run or forced output that exceeded demand and had nowhere to go. */
    val surplusKw: Long,
    /** Capacity the dispatchable units had left unused, whatever their ramp rates; variable units are not counted. */
    val reserveKw: Long,
) {
    val demandKw: Long get() = input.demandKw
    val netDemandKw: Long get() = input.netDemandKw
    val generationKw: Long get() = units.sumOf { it.outputKw }
    val servedKw: Long get() = (input.demandKw - unservedKw).coerceAtLeast(0L)

    /** Spare capacity as a share of the load the units had to cover, in ppm. One million when there is no load. */
    val reservePpm: Long
        get() = if (input.netDemandKw <= 0L) Rate.ONE_PPM else mulDiv(reserveKw, Rate.ONE_PPM, input.netDemandKw, RoundingMode.DOWN)

    val scarcity: Boolean get() = unservedKw > 0L || price > basePrice

    fun outputOf(id: UnitId): Long = units.firstOrNull { it.id == id }?.outputKw ?: 0L

    /** Output by technology, in the order of [Technology.values]. */
    fun outputByTech(): LongArray {
        val out = LongArray(Technology.values().size)
        for (u in units) out[u.tech.ordinal] += u.outputKw
        return out
    }

    /** Kilowatts that could be added by units costing no more than [maxCost]. */
    fun headroomUpTo(maxCost: Money): Long = units.filter { it.marginalCost <= maxCost }.sumOf { (it.ceilingKw - it.outputKw).coerceAtLeast(0L) }

    /** Kilowatts of current output from units costing more than [minCost]: what extra supply from elsewhere would displace. */
    fun outputAbove(minCost: Money): Long = units.filter { it.marginalCost > minCost }.sumOf { it.outputKw - it.floorKw.coerceAtMost(it.outputKw) }

    /** Cost of the next kilowatt this grid could add, or the cap when nothing is left. */
    fun nextCost(): Money = units.filter { it.ceilingKw > it.outputKw }.minOfOrNull { it.marginalCost } ?: input.params.priceCap
}

/**
 * The merit-order dispatch of one grid for one hour. It is a pure function: the same input always gives the same
 * result, with ties broken by unit id.
 *
 * Each unit gets a floor and a ceiling for the hour. A variable unit (wind, sun) may produce anything up to what the
 * weather allows. A dispatchable unit that ran in the previous hour may move only by its ramp rate, and may not sit
 * between zero and its minimum stable output. Floors are taken first, then the cheapest units fill what is left. A unit
 * that is off is started only when the remaining load is at least its minimum output; if load is still unmet after
 * every other unit has been tried, off units are started at their minimum anyway, and the overshoot is taken back from
 * variable units. The price is the running cost of the dearest unit that produced above its floor, lifted towards the
 * cap as spare capacity (the dispatchable capacity left unused) runs out, and the cap itself when load goes unserved.
 */
object DispatchEngine {
    private class Slot(val unit: DispatchUnit, val lo: Long, val hi: Long, val minStable: Long) {
        var out: Long = lo
        val variable: Boolean get() = !unit.tech.dispatchable
        val offable: Boolean get() = !variable && lo == 0L && minStable > 0L
    }

    private fun slotOf(u: DispatchUnit): Slot {
        if (!u.tech.dispatchable) return Slot(u, 0L, u.availableKw, 0L)
        val avail = u.availableKw
        if (avail <= 0L) return Slot(u, 0L, 0L, 0L)
        val minS = minOf(u.minStableKw, avail)
        if (u.prevKw > 0L) {
            var hi = minOf(avail, u.prevKw + u.rampUpKw)
            hi = maxOf(hi, minS)
            var lo = if (u.prevKw <= minS) 0L else maxOf(u.prevKw - u.rampDownKw, minS)
            if (lo > hi) lo = hi
            return Slot(u, lo, hi, minS)
        }
        // a start brings the unit to its minimum output within the hour and it can climb from there
        val hi = minOf(avail, minS + u.rampUpKw)
        return Slot(u, 0L, hi, minS)
    }

    fun dispatch(input: DispatchInput): DispatchResult {
        val params = input.params
        val slots = input.units.map(::slotOf).sortedWith(compareBy({ it.unit.marginalCost.raw }, { it.unit.id.value }))
        val residual = input.netDemandKw.coerceAtLeast(0L)
        var surplus = if (input.netDemandKw < 0L) -input.netDemandKw else 0L

        val floors = slots.sumOf { it.lo }
        var remaining = residual - floors
        if (remaining < 0L) { surplus += -remaining; remaining = 0L }

        for (s in slots) {
            if (remaining <= 0L) break
            val room = s.hi - s.out
            if (room <= 0L) continue
            if (s.offable && s.out == 0L && remaining < s.minStable) continue
            val add = minOf(room, remaining)
            s.out += add
            remaining -= add
        }
        if (remaining > 0L) {
            for (s in slots) {
                if (remaining <= 0L) break
                if (!s.offable || s.out != 0L || s.hi <= 0L) continue
                val add = minOf(s.hi, maxOf(s.minStable, remaining))
                s.out = add
                remaining -= add
            }
            if (remaining < 0L) {
                var over = -remaining
                remaining = 0L
                for (s in slots.asReversed()) {
                    if (over <= 0L) break
                    if (!s.variable) continue
                    val cut = minOf(s.out, over)
                    s.out -= cut
                    over -= cut
                }
                surplus += over
            }
        }

        val unserved = remaining
        val units = slots.map {
            UnitDispatch(
                it.unit.id, it.unit.tech, it.out, it.unit.availableKw,
                if (it.variable) it.unit.availableKw - it.out else 0L, it.unit.marginalCost, it.lo, it.hi,
            )
        }
        val curtailed = units.sumOf { it.curtailedKw }

        val setterSlot = slots.filter { it.out > it.lo }.maxWithOrNull(compareBy({ it.unit.marginalCost.raw }, { it.unit.id.value }))
        val base = when {
            unserved > 0L -> params.priceCap
            setterSlot != null -> setterSlot.unit.marginalCost
            curtailed > 0L || surplus > 0L || residual == 0L -> params.priceFloor
            else -> slots.filter { it.out > 0L }.maxOfOrNull { it.unit.marginalCost } ?: params.priceFloor
        }.let { Money.max(params.priceFloor, Money.min(params.priceCap, it)) }

        // spare capacity is what the firm units could still deliver, whatever their ramp rates would allow within the hour
        val reserve = slots.filter { !it.variable }.sumOf { (it.unit.availableKw - it.out).coerceAtLeast(0L) }
        var price = base
        if (unserved == 0L && residual > 0L) {
            val ratio = mulDiv(reserve, Rate.ONE_PPM, residual, RoundingMode.DOWN)
            if (ratio < params.scarcityThresholdPpm && params.scarcityThresholdPpm > 0L) {
                val gap = (params.priceCap - base).raw.coerceAtLeast(0L)
                price = base + Money(mulDiv(gap, params.scarcityThresholdPpm - ratio, params.scarcityThresholdPpm, RoundingMode.DOWN))
            }
        }
        return DispatchResult(input, units, price, base, setterSlot?.unit?.id, unserved, curtailed, surplus, reserve)
    }
}
