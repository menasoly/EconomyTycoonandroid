package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv
import java.math.BigInteger

/** One point of a load duration curve: demand that is exceeded for [sharePpm] of the hours. */
data class LoadDurationPoint(val sharePpm: Long, val demandKw: Long)

/** Daily price statistics over a window. Prices are per megawatt-hour; ratios are ppm. */
data class PriceStats(
    val days: Int,
    val mean: Money,
    val lowest: Money,
    val highest: Money,
    val median: Money,
    val p10: Money,
    val p90: Money,
    val standardDeviation: Money,
    /** Standard deviation over the mean. */
    val volatilityPpm: Long,
    /** Days whose peak price reached at least half the cap. */
    val spikeDays: Int,
    /** Change of the mean from the first half of the window to the second, in ppm of the first. */
    val trendPpm: Long,
)

/**
 * Reliability indices over a window. Loss-of-load hours counts the hours in which some demand went unserved;
 * energy not served is the missing energy; availability is the share of demanded energy that was supplied.
 */
data class Reliability(
    val days: Int,
    val lossOfLoadHours: Int,
    val lossOfLoadDays: Int,
    val energyNotServedKwh: Long,
    val availabilityPpm: Long,
    val worstDayUnservedKwh: Long,
    val lostLoadValue: Money,
)

/** Installed capacity of one technology and its place in the fleet. */
data class MixRow(val tech: Technology, val installedKw: Long, val sharePpm: Long, val units: Int)

/** Pure read-only calculations over a grid's history and fleet. Nothing here changes state. */
object GridAnalytics {
    // ----- load -----

    /** Hourly demands sorted from the highest to the lowest. */
    fun loadDuration(demandKw: List<Long>): List<Long> = demandKw.sortedDescending()

    /** [points] evenly spaced points of the load duration curve, from the peak (0%) to the minimum (100%). */
    fun loadDurationPoints(demandKw: List<Long>, points: Int = 11): List<LoadDurationPoint> {
        require(points >= 2) { "a curve needs at least two points" }
        if (demandKw.isEmpty()) return emptyList()
        val sorted = loadDuration(demandKw)
        return List(points) { i ->
            val share = mulDiv(i.toLong(), Rate.ONE_PPM, (points - 1).toLong(), RoundingMode.HALF_EVEN)
            val index = mulDiv(share, (sorted.size - 1).toLong(), Rate.ONE_PPM, RoundingMode.HALF_EVEN).toInt()
            LoadDurationPoint(share, sorted[index])
        }
    }

    /** Average demand over peak demand, in ppm; 1,000,000 for a perfectly flat load. */
    fun loadFactorPpm(demandKw: List<Long>): Long {
        if (demandKw.isEmpty()) return 0L
        val peak = demandKw.max()
        if (peak <= 0L) return 0L
        return mulDiv(demandKw.sum() / demandKw.size, Rate.ONE_PPM, peak, RoundingMode.HALF_EVEN)
    }

    /** Hours of the window in which demand was within [bandPpm] of the peak. */
    fun peakHours(demandKw: List<Long>, bandPpm: Long = 100_000L): Int {
        if (demandKw.isEmpty()) return 0
        val peak = demandKw.max()
        val line = peak - mulDiv(peak, bandPpm, Rate.ONE_PPM, RoundingMode.UP)
        return demandKw.count { it >= line }
    }

    // ----- carbon and mix -----

    /** Kilograms of carbon dioxide emitted making [generationKwh] by technology (in [Technology.values] order). */
    fun carbonKg(generationKwh: List<Long>): Long =
        Technology.values().sumOf { mulDiv(generationKwh[it.ordinal], it.profile.carbonKgPerMwh, GridUnits.KW_PER_MW, RoundingMode.HALF_EVEN) }

    /**
     * Carbon intensity of the energy made, in kilograms per megawatt-hour. That is also grams per kilowatt-hour, the
     * unit grids are usually compared in. Zero when nothing was made.
     */
    fun carbonIntensity(generationKwh: List<Long>): Long {
        val total = generationKwh.sum()
        if (total <= 0L) return 0L
        val weighted = Technology.values().sumOf { mulDiv(generationKwh[it.ordinal], it.profile.carbonKgPerMwh, 1L, RoundingMode.DOWN) }
        return mulDiv(weighted, 1L, total, RoundingMode.HALF_EVEN)
    }

    /** Carbon intensity over the last [days] days of a grid's history. */
    fun carbonIntensity(history: List<DaySummary>, days: Int): Long {
        val gen = LongArray(Technology.values().size)
        for (d in history.takeLast(days)) for (i in gen.indices) gen[i] += d.generationKwh[i]
        return carbonIntensity(gen.toList())
    }

    /** Share of the energy made by water, wind and sun, in ppm. */
    fun renewableShare(generationKwh: List<Long>): Long {
        val total = generationKwh.sum()
        if (total <= 0L) return 0L
        val clean = Technology.values().filter { it.isRenewable }.sumOf { generationKwh[it.ordinal] }
        return mulDiv(clean, Rate.ONE_PPM, total, RoundingMode.HALF_EVEN)
    }

    /** Installed capacity by technology, largest first, counting units in service or temporarily out. */
    fun fleetMix(grid: Grid): List<MixRow> {
        val live = grid.unitMap.values.filter { it.isLive && it.status != UnitStatus.MOTHBALLED }
        val total = live.sumOf { it.capacityKw }
        return Technology.values().mapNotNull { t ->
            val of = live.filter { it.tech == t }
            if (of.isEmpty()) null else {
                val kw = of.sumOf { it.capacityKw }
                MixRow(t, kw, if (total > 0L) mulDiv(kw, Rate.ONE_PPM, total, RoundingMode.HALF_EVEN) else 0L, of.size)
            }
        }.sortedWith(compareByDescending<MixRow> { it.installedKw }.thenBy { it.tech.ordinal })
    }

    /** Output a technology produced as a share of its installed capacity over the window, in ppm. */
    fun capacityFactorPpm(history: List<DaySummary>, days: Int, tech: Technology, installedKw: Long): Long {
        val window = history.takeLast(days)
        if (window.isEmpty() || installedKw <= 0L) return 0L
        val possible = installedKw * GridUnits.HOURS_PER_DAY * window.size
        return mulDiv(window.sumOf { it.generationKwh[tech.ordinal] }, Rate.ONE_PPM, possible, RoundingMode.HALF_EVEN)
    }

    // ----- prices -----

    /** Average price over the last [days] days, weighted by the energy served each day. */
    fun windowAveragePrice(history: List<DaySummary>, days: Int): Money {
        val window = history.takeLast(days)
        if (window.isEmpty()) return Money.ZERO
        val served = window.sumOf { it.servedKwh }
        if (served <= 0L) return Money(window.sumOf { it.averagePrice.raw } / window.size)
        val value = window.fold(0L) { acc, d -> Math.addExact(acc, mulDiv(d.averagePrice.raw, d.servedKwh, GridUnits.KW_PER_MW)) }
        return Money(mulDiv(value, GridUnits.KW_PER_MW, served, RoundingMode.HALF_EVEN))
    }

    private fun percentile(sorted: List<Long>, percent: Int): Long = sorted[((sorted.size - 1) * percent) / 100]

    fun priceStats(history: List<DaySummary>, days: Int, priceCap: Money): PriceStats? {
        val window = history.takeLast(days)
        if (window.isEmpty()) return null
        val avg = window.map { it.averagePrice.raw }
        val sorted = avg.sorted()
        val mean = avg.sum() / avg.size
        val variance = avg.fold(BigInteger.ZERO) { acc, v -> acc + BigInteger.valueOf(v - mean).pow(2) }.divide(BigInteger.valueOf(avg.size.toLong()))
        val sd = variance.sqrt().toLong()
        val half = window.size / 2
        val first = if (half > 0) avg.take(half).sum() / half else mean
        val second = if (half > 0) avg.drop(window.size - half).sum() / half else mean
        val trend = if (first != 0L) mulDiv(second - first, Rate.ONE_PPM, first, RoundingMode.HALF_EVEN) else 0L
        return PriceStats(
            window.size, Money(mean), window.minOf { it.lowPrice }, window.maxOf { it.peakPrice }, Money(percentile(sorted, 50)),
            Money(percentile(sorted, 10)), Money(percentile(sorted, 90)), Money(sd),
            if (mean > 0L) mulDiv(sd, Rate.ONE_PPM, mean, RoundingMode.HALF_EVEN) else 0L,
            window.count { it.peakPrice.raw * 2 >= priceCap.raw }, trend,
        )
    }

    // ----- reliability -----

    fun reliability(history: List<DaySummary>, days: Int): Reliability {
        val window = history.takeLast(days)
        val demand = window.sumOf { it.demandKwh }
        val unserved = window.sumOf { it.unservedKwh }
        val availability = if (demand <= 0L) Rate.ONE_PPM else Rate.ONE_PPM - mulDiv(unserved, Rate.ONE_PPM, demand, RoundingMode.HALF_EVEN)
        return Reliability(
            window.size, window.sumOf { it.shedHours }, window.count { it.shedHours > 0 }, unserved, availability,
            window.maxOfOrNull { it.unservedKwh } ?: 0L, Money.sum(window.map { it.lostLoadValue }),
        )
    }

    /** Reserve margin right now: firm capacity over the recent peak, minus one, in ppm; negative when short. */
    fun reserveMarginPpm(grid: Grid, peakKw: Long): Long {
        if (peakKw <= 0L) return Rate.ONE_PPM
        return mulDiv(grid.firmKw() - peakKw, Rate.ONE_PPM, peakKw, RoundingMode.FLOOR)
    }
}
