package app.many.economytycoon.visualization

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** One wedge of a pie or ring chart. Angles are degrees clockwise from 12 o'clock. */
data class Slice(val label: String, val value: Double, val startDegrees: Double, val sweepDegrees: Double, val share: Double, val colorSlot: Int)

object PieLayout {
    /**
     * Splits the circle in proportion to the values. Zero and negative values get no wedge. Slices below
     * [minShare] are merged into a final "Other" wedge so thin slivers do not clutter the chart.
     */
    fun slices(items: List<Pair<String, Double>>, minShare: Double = 0.03, otherLabel: String = "Other"): List<Slice> {
        val positive = items.filter { it.second > 0.0 }.sortedByDescending { it.second }
        val total = positive.sumOf { it.second }
        if (total <= 0.0) return emptyList()
        val big = positive.filter { it.second / total >= minShare }
        val small = positive.filter { it.second / total < minShare }
        val shown = if (small.size > 1) big + (otherLabel to small.sumOf { it.second }) else positive
        val out = ArrayList<Slice>()
        var angle = 0.0
        for ((i, item) in shown.withIndex()) {
            val share = item.second / total
            val sweep = share * 360.0
            out += Slice(item.first, item.second, angle, sweep, share, if (item.first == otherLabel && small.size > 1) Palette.categorical.size - 1 else i)
            angle += sweep
        }
        return out
    }

    /** Which slice a touch at ([dx], [dy]) from the centre falls on, or null for the hole or outside the ring. */
    fun hit(slices: List<Slice>, dx: Double, dy: Double, innerRadius: Double, outerRadius: Double): Slice? {
        val r = hypot(dx, dy)
        if (r < innerRadius || r > outerRadius) return null
        var deg = Math.toDegrees(Math.atan2(dx, -dy))
        if (deg < 0) deg += 360.0
        return slices.firstOrNull { deg >= it.startDegrees && deg < it.startDegrees + it.sweepDegrees } ?: slices.lastOrNull()
    }
}

data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val area: Double get() = width * height
    fun contains(px: Double, py: Double) = px >= x && px < right && py >= y && py < bottom
}

data class TreemapCell(val label: String, val value: Double, val rect: Rect, val colorSlot: Int)

/** Squarified treemap: rectangles whose areas follow the values and whose shapes stay close to square. */
object Treemap {
    fun layout(items: List<Pair<String, Double>>, bounds: Rect): List<TreemapCell> {
        val sorted = items.filter { it.second > 0.0 }.sortedByDescending { it.second }
        val total = sorted.sumOf { it.second }
        if (sorted.isEmpty() || bounds.area <= 0.0) return emptyList()
        val scale = bounds.area / total
        val out = ArrayList<TreemapCell>()
        var free = bounds
        var row = ArrayList<Pair<Int, Double>>()
        var i = 0
        fun worst(r: List<Pair<Int, Double>>, side: Double): Double {
            val s = r.sumOf { it.second * scale }
            val longest = r.maxOf { it.second * scale }; val shortest = r.minOf { it.second * scale }
            return max(side * side * longest / (s * s), s * s / (side * side * shortest))
        }
        fun flush() {
            if (row.isEmpty()) return
            val sum = row.sumOf { it.second * scale }
            val horizontal = free.width >= free.height
            val side = if (horizontal) free.height else free.width
            val thickness = if (side > 0.0) sum / side else 0.0
            var offset = 0.0
            for ((idx, v) in row) {
                val length = if (thickness > 0.0) v * scale / thickness else 0.0
                val rect = if (horizontal) Rect(free.x, free.y + offset, thickness, length) else Rect(free.x + offset, free.y, length, thickness)
                out += TreemapCell(sorted[idx].first, sorted[idx].second, rect, idx)
                offset += length
            }
            free = if (horizontal) Rect(free.x + thickness, free.y, free.width - thickness, free.height) else Rect(free.x, free.y + thickness, free.width, free.height - thickness)
            row = ArrayList()
        }
        while (i < sorted.size) {
            val side = min(free.width, free.height)
            val candidate = row + (i to sorted[i].second)
            if (row.isEmpty() || side <= 0.0 || worst(candidate, side) <= worst(row, side)) { row = ArrayList(candidate); i++ } else flush()
        }
        flush()
        return out
    }
}

/** A point in the pixel space of a canvas. */
data class Pixel(val x: Double, val y: Double)

/**
 * Zoom and pan over a square world of [worldSize] units drawn in a canvas of [width] by [height] pixels.
 * Immutable: every gesture returns a new viewport.
 */
class MapViewport private constructor(
    val worldSize: Double,
    val width: Double,
    val height: Double,
    val zoom: Double,
    val centerX: Double,
    val centerY: Double,
) {
    /** Pixels per world unit when the whole world just fits. */
    val fitScale: Double get() = min(width, height) / worldSize
    val scale: Double get() = fitScale * zoom

    fun toPixel(wx: Double, wy: Double): Pixel = Pixel(width / 2.0 + (wx - centerX) * scale, height / 2.0 + (wy - centerY) * scale)
    fun toWorld(px: Double, py: Double): Pixel = Pixel(centerX + (px - width / 2.0) / scale, centerY + (py - height / 2.0) / scale)

    fun pan(dxPixels: Double, dyPixels: Double): MapViewport = with(centerX - dxPixels / scale, centerY - dyPixels / scale, zoom)

    /** Zooms by [factor] keeping the world point under ([px], [py]) where it is on screen. */
    fun zoomAt(px: Double, py: Double, factor: Double): MapViewport {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val before = toWorld(px, py)
        val newScale = fitScale * newZoom
        val cx = before.x - (px - width / 2.0) / newScale
        val cy = before.y - (py - height / 2.0) / newScale
        return with(cx, cy, newZoom)
    }

    fun centeredOn(wx: Double, wy: Double, atZoom: Double = zoom): MapViewport = with(wx, wy, atZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    fun resized(newWidth: Double, newHeight: Double): MapViewport = MapViewport(worldSize, max(1.0, newWidth), max(1.0, newHeight), zoom, centerX, centerY).clamped()

    /** Keeps the view over the world: the centre may not leave the map, so you cannot lose it. */
    private fun clamped(): MapViewport {
        val cx = centerX.coerceIn(0.0, worldSize); val cy = centerY.coerceIn(0.0, worldSize)
        return if (cx == centerX && cy == centerY) this else MapViewport(worldSize, width, height, zoom, cx, cy)
    }

    private fun with(cx: Double, cy: Double, z: Double) = MapViewport(worldSize, width, height, z, cx, cy).clamped()

    fun isVisible(wx: Double, wy: Double, margin: Double = 0.0): Boolean {
        val p = toPixel(wx, wy)
        return p.x >= -margin && p.x <= width + margin && p.y >= -margin && p.y <= height + margin
    }

    override fun equals(other: Any?): Boolean = other is MapViewport && other.worldSize == worldSize && other.width == width &&
        other.height == height && other.zoom == zoom && other.centerX == centerX && other.centerY == centerY
    override fun hashCode(): Int = listOf(worldSize, width, height, zoom, centerX, centerY).hashCode()

    companion object {
        const val MIN_ZOOM = 1.0
        const val MAX_ZOOM = 12.0

        fun fitting(worldSize: Double, width: Double, height: Double): MapViewport =
            MapViewport(worldSize, max(1.0, width), max(1.0, height), 1.0, worldSize / 2.0, worldSize / 2.0)
    }
}

/** Finds the nearest of several world-space markers to a touch, within a pixel tolerance. */
object HitTest {
    fun <T> nearest(viewport: MapViewport, markers: List<T>, position: (T) -> Pair<Double, Double>, px: Double, py: Double, tolerancePixels: Double): T? {
        var best: T? = null; var bestDistance = tolerancePixels
        for (m in markers) {
            val (wx, wy) = position(m)
            val p = viewport.toPixel(wx, wy)
            val d = hypot(p.x - px, p.y - py)
            if (d <= bestDistance) { bestDistance = d; best = m }
        }
        return best
    }
}

/** Angle helpers for gauges. */
object Gauge {
    const val START_DEGREES = 135.0
    const val SWEEP_DEGREES = 270.0

    fun sweepFor(fraction: Double): Double = fraction.coerceIn(0.0, 1.0) * SWEEP_DEGREES

    fun needleRadians(fraction: Double): Double = Math.toRadians(START_DEGREES + sweepFor(fraction))

    fun fractionOf(value: Double, min: Double, max: Double): Double = if (max == min) 0.0 else ((value - min) / (max - min)).coerceIn(0.0, 1.0)

    val fullCircleRadians: Double get() = 2 * PI
    fun absDiff(a: Double, b: Double) = abs(a - b)
}
