package app.many.economytycoon.visualization

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A straight-line mapping from a data range onto a pixel range; the pixel range may run backwards (y axes). */
class LinearScale(val domainMin: Double, val domainMax: Double, val rangeStart: Double, val rangeEnd: Double) {
    private val span = domainMax - domainMin

    fun toPixel(value: Double): Double =
        if (span == 0.0) (rangeStart + rangeEnd) / 2.0 else rangeStart + (value - domainMin) / span * (rangeEnd - rangeStart)

    fun toValue(pixel: Double): Double {
        val r = rangeEnd - rangeStart
        return if (r == 0.0) domainMin else domainMin + (pixel - rangeStart) / r * span
    }

    fun clampedPixel(value: Double): Double {
        val p = toPixel(value)
        return p.coerceIn(min(rangeStart, rangeEnd), max(rangeStart, rangeEnd))
    }
}

/** Tick positions that land on round numbers, so axis labels read as 0, 50, 100 rather than 0, 47, 94. */
class NiceAxis(val min: Double, val max: Double, val step: Double, val ticks: List<Double>) {
    companion object {
        /** Chooses a step of 1, 2, 2.5 or 5 times a power of ten that gives about [targetTicks] ticks. */
        fun of(dataMin: Double, dataMax: Double, targetTicks: Int = 5, includeZero: Boolean = false): NiceAxis {
            var lo = dataMin; var hi = dataMax
            if (includeZero) { lo = min(lo, 0.0); hi = max(hi, 0.0) }
            if (lo.isNaN() || hi.isNaN() || lo.isInfinite() || hi.isInfinite()) return NiceAxis(0.0, 1.0, 0.25, listOf(0.0, 0.25, 0.5, 0.75, 1.0))
            if (hi == lo) {
                val pad = if (lo == 0.0) 1.0 else abs(lo) * 0.1
                lo -= pad; hi += pad
            }
            val rough = (hi - lo) / max(1, targetTicks - 1)
            val magnitude = 10.0.pow(floor(log10(rough)))
            val residual = rough / magnitude
            val nice = when {
                residual <= 1.0 -> 1.0
                residual <= 2.0 -> 2.0
                residual <= 2.5 -> 2.5
                residual <= 5.0 -> 5.0
                else -> 10.0
            }
            val step = nice * magnitude
            val first = floor(lo / step) * step
            val last = ceil(hi / step) * step
            val count = Math.round((last - first) / step).toInt()
            val ticks = List(count + 1) { clean(first + it * step, step) }
            return NiceAxis(ticks.first(), ticks.last(), step, ticks)
        }

        private fun log10(x: Double) = ln(x) / ln(10.0)

        /** Removes floating-point dust such as 0.30000000000000004 from tick values. */
        private fun clean(v: Double, step: Double): Double {
            val decimals = if (step >= 1.0) 0 else (-floor(log10(step))).toInt() + 1
            val f = 10.0.pow(decimals)
            return Math.round(v * f) / f
        }
    }
}

/** Short axis labels for large numbers: 1.5K, 2M. */
object AxisLabels {
    fun compact(value: Double): String {
        val a = abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            a >= 1e12 -> sign + trim(a / 1e12) + "T"
            a >= 1e9 -> sign + trim(a / 1e9) + "B"
            a >= 1e6 -> sign + trim(a / 1e6) + "M"
            a >= 1e3 -> sign + trim(a / 1e3) + "K"
            a >= 10 || a == 0.0 -> sign + Math.round(a).toString()
            else -> sign + trim(a)
        }
    }

    private fun trim(v: Double): String {
        val rounded = Math.round(v * 100.0) / 100.0
        val s = if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }

    fun percent(ppm: Double): String {
        val p = ppm / 10_000.0
        return trim(p) + "%"
    }
}
