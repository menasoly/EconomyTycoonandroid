package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money

/** One hour of a grid as it was dispatched. Kept for the last day so the dispatch stack can be drawn. */
data class HourProfile(
    val hour: Int,
    val demandKw: Long,
    val price: Money,
    val unservedKw: Long,
    val curtailedKw: Long,
    val importKw: Long,
    val exportKw: Long,
    val storageChargeKw: Long,
    val storageDischargeKw: Long,
    val reservePpm: Long,
    /** Output of each technology in the order of [Technology.values]. */
    val outputKw: List<Long>,
    /** The unit that set the price, or -1 when none did. */
    val setter: Long,
) {
    init { require(outputKw.size == Technology.values().size) { "one output per technology" } }

    val generationKw: Long get() = outputKw.sum()

    fun hashInto(h: CanonicalHasher) {
        h.putInt(hour).putLong(demandKw).putMoney(price).putLong(unservedKw).putLong(curtailedKw).putLong(importKw).putLong(exportKw)
        h.putLong(storageChargeKw).putLong(storageDischargeKw).putLong(reservePpm).putLong(setter)
        h.putInt(outputKw.size); for (o in outputKw) h.putLong(o)
    }
}

/** A grid's day in numbers. Energy is kilowatt-hours; prices are per megawatt-hour. */
data class DaySummary(
    val tick: Long,
    /** Average price over the day weighted by the energy served each hour. */
    val averagePrice: Money,
    val peakPrice: Money,
    val lowPrice: Money,
    val demandKwh: Long,
    val unservedKwh: Long,
    val curtailedKwh: Long,
    val peakDemandKw: Long,
    val generationKwh: List<Long>,
    val importKwh: Long,
    val exportKwh: Long,
    /** Hours in which some load could not be served. */
    val shedHours: Int,
    /** Hours in which the reserve fell under the scarcity threshold, so the price was lifted. */
    val scarcityHours: Int,
    val carbonKg: Long,
    val lowestReservePpm: Long,
    /** Cost of the day's lost load at the grid's value of lost load. */
    val lostLoadValue: Money,
) {
    init { require(generationKwh.size == Technology.values().size) { "one total per technology" } }

    val servedKwh: Long get() = (demandKwh - unservedKwh).coerceAtLeast(0L)
    val totalGenerationKwh: Long get() = generationKwh.sum()

    fun hashInto(h: CanonicalHasher) {
        h.putLong(tick).putMoney(averagePrice).putMoney(peakPrice).putMoney(lowPrice).putLong(demandKwh).putLong(unservedKwh).putLong(curtailedKwh)
        h.putLong(peakDemandKw).putLong(importKwh).putLong(exportKwh).putInt(shedHours).putInt(scarcityHours).putLong(carbonKg).putLong(lowestReservePpm).putMoney(lostLoadValue)
        h.putInt(generationKwh.size); for (g in generationKwh) h.putLong(g)
    }

    companion object {
        /** Folds 24 dispatched hours into a summary. [valueOfLostLoad] prices each unserved megawatt-hour. */
        fun of(tick: Long, hours: List<HourProfile>, valueOfLostLoad: Money): DaySummary {
            require(hours.size == GridUnits.HOURS_PER_DAY) { "a day has 24 hours" }
            val demand = hours.sumOf { it.demandKw }
            val unserved = hours.sumOf { it.unservedKw }
            val served = hours.sumOf { (it.demandKw - it.unservedKw).coerceAtLeast(0L) }
            val weighted = hours.fold(0L) { acc, h -> Math.addExact(acc, app.many.economytycoon.core.mulDiv(h.price.raw, (h.demandKw - h.unservedKw).coerceAtLeast(0L), 1_000L)) }
            val average = if (served > 0L) Money(app.many.economytycoon.core.mulDiv(weighted, 1_000L, served)) else Money(hours.sumOf { it.price.raw } / hours.size)
            val byTech = LongArray(Technology.values().size)
            for (h in hours) for (i in byTech.indices) byTech[i] += h.outputKw[i]
            var carbon = 0L
            for (t in Technology.values()) carbon += app.many.economytycoon.core.mulDiv(byTech[t.ordinal], t.profile.carbonKgPerMwh, GridUnits.KW_PER_MW)
            return DaySummary(
                tick, average, hours.maxOf { it.price }, hours.minOf { it.price }, demand, unserved, hours.sumOf { it.curtailedKw }, hours.maxOf { it.demandKw },
                byTech.toList(), hours.sumOf { it.importKw }, hours.sumOf { it.exportKw }, hours.count { it.unservedKw > 0L },
                hours.count { it.reservePpm < SCARCITY_RESERVE_PPM }, carbon, hours.minOf { it.reservePpm },
                GridUnits.cost(valueOfLostLoad, unserved),
            )
        }

        /** Reserve under this share of load counts as a tight hour in reports. */
        const val SCARCITY_RESERVE_PPM = 100_000L
    }
}
