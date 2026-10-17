package app.many.economytycoon.workshop

/** Order statistics of a set of whole numbers. All arithmetic is integer, so the same runs always summarise the same way. */
data class Summary(val count: Int, val min: Long, val max: Long, val mean: Long, val median: Long, val p10: Long, val p90: Long, val stdDev: Long) {
    /** Standard deviation as a share of the absolute mean, in ppm; zero when the mean is zero. */
    val variationPpm: Long get() = if (mean == 0L) 0L else stdDev * 1_000_000L / Math.abs(mean)
    val spread: Long get() = max - min

    fun describe(unit: String = ""): String =
        if (count == 0) "no data" else "median $median$unit (mean $mean, range $min to $max, spread +-$stdDev)"

    companion object {
        val EMPTY = Summary(0, 0, 0, 0, 0, 0, 0, 0)
    }
}

object WorkshopStats {
    fun summarize(values: List<Long>): Summary {
        if (values.isEmpty()) return Summary.EMPTY
        val sorted = values.sorted()
        val n = sorted.size
        val mean = floorDiv(sorted.sum(), n.toLong())
        // Squared deviations of large money values overflow a Long, so the variance is summed as a double.
        // IEEE arithmetic is exact and identical on every JVM, so the result is repeatable.
        var acc = 0.0
        for (v in sorted) { val dv = (v - mean).toDouble(); acc += dv * dv }
        val stdDev = Math.sqrt(acc / n).toLong()
        return Summary(n, sorted.first(), sorted.last(), mean, median(sorted), percentile(sorted, 10), percentile(sorted, 90), stdDev)
    }

    private fun floorDiv(a: Long, b: Long) = Math.floorDiv(a, b)

    /** Middle value; for an even count the mean of the two middle values, rounded down. */
    fun median(sorted: List<Long>): Long {
        if (sorted.isEmpty()) return 0L
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else floorDiv(sorted[n / 2 - 1] + sorted[n / 2], 2L)
    }

    /** Nearest-rank percentile of an already sorted list. */
    fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0L
        val rank = ((p * sorted.size + 99) / 100).coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** Integer square root, rounded down. */
    fun isqrt(v: Long): Long {
        if (v <= 0L) return 0L
        var x = Math.sqrt(v.toDouble()).toLong()
        while (x * x > v) x--
        while ((x + 1) * (x + 1) <= v) x++
        return x
    }

    /**
     * Direction in which [ys] moves as [xs] grows, from the signs of consecutive differences: [Trend.RISING] when
     * every step is up or flat, [Trend.FALLING] when every step is down or flat, [Trend.FLAT] when nothing moves
     * by more than [tolerance], otherwise [Trend.MIXED].
     */
    fun trend(ys: List<Long>, tolerance: Long = 0L): Trend {
        if (ys.size < 2) return Trend.FLAT
        var up = false
        var down = false
        for (i in 1 until ys.size) {
            val dy = ys[i] - ys[i - 1]
            if (dy > tolerance) up = true else if (dy < -tolerance) down = true
        }
        return when {
            up && down -> Trend.MIXED
            up -> Trend.RISING
            down -> Trend.FALLING
            else -> Trend.FLAT
        }
    }

    /** [value] as a share of [whole], in ppm; zero when [whole] is zero. */
    fun share(value: Long, whole: Long): Long = if (whole == 0L) 0L else value * 1_000_000L / whole
}

enum class Trend(val label: String) { RISING("rising"), FALLING("falling"), FLAT("flat"), MIXED("mixed") }
