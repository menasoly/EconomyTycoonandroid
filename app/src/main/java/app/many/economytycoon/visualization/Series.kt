package app.many.economytycoon.visualization

import kotlin.math.abs

data class DataPoint(val x: Double, val y: Double)

/** A named line of points, drawn in one colour slot of the palette. */
class LineSeries(val name: String, val points: List<DataPoint>, val colorSlot: Int = 0) {
    val isEmpty: Boolean get() = points.isEmpty()
    val minY: Double get() = points.minOfOrNull { it.y } ?: 0.0
    val maxY: Double get() = points.maxOfOrNull { it.y } ?: 0.0
    val minX: Double get() = points.minOfOrNull { it.x } ?: 0.0
    val maxX: Double get() = points.maxOfOrNull { it.x } ?: 0.0
    val last: DataPoint? get() = points.lastOrNull()

    /** Change from the first to the last point as a fraction of the first, or null when it has no base. */
    fun relativeChange(): Double? {
        if (points.size < 2) return null
        val first = points.first().y
        if (first == 0.0) return null
        return (points.last().y - first) / abs(first)
    }
}

/** Shrinks a long series to at most [limit] points while keeping its visual shape. */
object Downsample {
    /** Largest-Triangle-Three-Buckets: keeps the points that matter most for how the line looks. */
    fun lttb(points: List<DataPoint>, limit: Int): List<DataPoint> {
        if (limit < 3 || points.size <= limit) return points
        val out = ArrayList<DataPoint>(limit)
        out += points.first()
        val bucketSize = (points.size - 2).toDouble() / (limit - 2)
        var a = 0
        for (i in 0 until limit - 2) {
            val start = (i * bucketSize).toInt() + 1
            val end = (((i + 1) * bucketSize).toInt() + 1).coerceAtMost(points.size - 1)
            val nextStart = end
            val nextEnd = (((i + 2) * bucketSize).toInt() + 1).coerceAtMost(points.size)
            var avgX = 0.0; var avgY = 0.0
            val nextCount = (nextEnd - nextStart).coerceAtLeast(1)
            for (j in nextStart until nextEnd.coerceAtLeast(nextStart + 1).coerceAtMost(points.size)) { avgX += points[j].x; avgY += points[j].y }
            avgX /= nextCount; avgY /= nextCount
            var best = -1.0; var pick = start
            val pa = points[a]
            for (j in start until end) {
                val area = abs((pa.x - avgX) * (points[j].y - pa.y) - (pa.x - points[j].x) * (avgY - pa.y)) * 0.5
                if (area > best) { best = area; pick = j }
            }
            out += points[pick]
            a = pick
        }
        out += points.last()
        return out
    }

    /** Takes every n-th point plus the last; cheap and predictable for bar charts. */
    fun every(points: List<DataPoint>, limit: Int): List<DataPoint> {
        if (limit < 2 || points.size <= limit) return points
        val step = Math.ceil(points.size.toDouble() / limit).toInt()
        val out = ArrayList<DataPoint>()
        var i = 0
        while (i < points.size) { out += points[i]; i += step }
        if (out.last() != points.last()) out += points.last()
        return out
    }
}

/** Bounds shared by several series so they can be drawn on one chart. */
class ChartBounds(val minX: Double, val maxX: Double, val axis: NiceAxis) {
    companion object {
        fun of(series: List<LineSeries>, includeZero: Boolean = false, targetTicks: Int = 5): ChartBounds {
            val all = series.filterNot { it.isEmpty }
            if (all.isEmpty()) return ChartBounds(0.0, 1.0, NiceAxis.of(0.0, 1.0, targetTicks))
            return ChartBounds(all.minOf { it.minX }, all.maxOf { it.maxX }, NiceAxis.of(all.minOf { it.minY }, all.maxOf { it.maxY }, targetTicks, includeZero))
        }
    }
}

/** Scales a series to 0..1 for sparklines, where only the shape matters. */
object Sparkline {
    fun normalise(values: List<Double>): List<Double> {
        if (values.isEmpty()) return emptyList()
        val lo = values.min(); val hi = values.max()
        if (hi == lo) return values.map { 0.5 }
        return values.map { (it - lo) / (hi - lo) }
    }

    /** +1 rising, -1 falling, 0 flat, comparing the means of the first and last thirds. */
    fun trend(values: List<Double>, tolerance: Double = 0.01): Int {
        if (values.size < 3) return 0
        val third = (values.size / 3).coerceAtLeast(1)
        val early = values.take(third).average(); val late = values.takeLast(third).average()
        val base = abs(early).coerceAtLeast(1e-9)
        val change = (late - early) / base
        return when { change > tolerance -> 1; change < -tolerance -> -1; else -> 0 }
    }
}

/** Moving averages for smoothing noisy price series. */
object Smoothing {
    fun movingAverage(values: List<Double>, window: Int): List<Double> {
        if (window <= 1 || values.isEmpty()) return values
        val out = ArrayList<Double>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= window) sum -= values[i - window]
            out += sum / minOf(i + 1, window)
        }
        return out
    }

    fun exponential(values: List<Double>, alpha: Double): List<Double> {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1]" }
        if (values.isEmpty()) return values
        val out = ArrayList<Double>(values.size)
        var prev = values[0]
        for (v in values) { prev += alpha * (v - prev); out += prev }
        return out
    }
}
