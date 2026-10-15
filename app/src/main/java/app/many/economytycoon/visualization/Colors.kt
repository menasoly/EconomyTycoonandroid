package app.many.economytycoon.visualization

import kotlin.math.pow

/** Colours as packed ARGB ints, so this code runs and is tested without any UI toolkit. */
object Argb {
    fun of(r: Int, g: Int, b: Int, a: Int = 255): Int = (a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)
    fun alpha(c: Int) = c ushr 24 and 0xFF
    fun red(c: Int) = c ushr 16 and 0xFF
    fun green(c: Int) = c ushr 8 and 0xFF
    fun blue(c: Int) = c and 0xFF

    fun withAlpha(c: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (c and 0x00FFFFFF)

    fun lerp(a: Int, b: Int, t: Double): Int {
        val u = t.coerceIn(0.0, 1.0)
        fun mix(x: Int, y: Int) = Math.round(x + (y - x) * u).toInt()
        return of(mix(red(a), red(b)), mix(green(a), green(b)), mix(blue(a), blue(b)), mix(alpha(a), alpha(b)))
    }

    /** Relative luminance per WCAG, 0 (black) to 1 (white). */
    fun luminance(c: Int): Double {
        fun channel(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel(red(c)) + 0.7152 * channel(green(c)) + 0.0722 * channel(blue(c))
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Black or white, whichever reads better on [background]. */
    fun onColor(background: Int): Int = if (contrast(background, 0xFF000000.toInt()) >= contrast(background, 0xFFFFFFFF.toInt())) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    fun hex(c: Int): String = "#" + "%08X".format(c)
}

/** Series colours that stay distinguishable for the common kinds of colour blindness (Okabe-Ito based). */
object Palette {
    val categorical: IntArray = intArrayOf(
        0xFF0072B2.toInt(), 0xFFE69F00.toInt(), 0xFF009E73.toInt(), 0xFFD55E00.toInt(),
        0xFFCC79A7.toInt(), 0xFF56B4E9.toInt(), 0xFFF0E442.toInt(), 0xFF7A7A7A.toInt(),
    )

    fun slot(i: Int): Int = categorical[Math.floorMod(i, categorical.size)]

    val gain: Int = 0xFF1B9E77.toInt()
    val loss: Int = 0xFFD95F02.toInt()
    val neutral: Int = 0xFF8D8D8D.toInt()
}

/** Smooth colour scale through a list of evenly spaced colours. */
class ColorRamp(private val stops: List<Int>) {
    init { require(stops.size >= 2) { "a ramp needs at least two colours" } }

    fun at(t: Double): Int {
        val u = t.coerceIn(0.0, 1.0) * (stops.size - 1)
        val i = u.toInt().coerceAtMost(stops.size - 2)
        return Argb.lerp(stops[i], stops[i + 1], u - i)
    }

    companion object {
        /** Cool to hot, for load, utilisation and density. */
        val heat = ColorRamp(listOf(0xFF2C7BB6.toInt(), 0xFFABD9E9.toInt(), 0xFFFFFFBF.toInt(), 0xFFFDAE61.toInt(), 0xFFD7191C.toInt()))

        /** Red through grey to green, for change and profit. */
        val diverging = ColorRamp(listOf(Palette.loss, 0xFFE8E8E8.toInt(), Palette.gain))

        /** Light to dark single hue, for population and wealth. */
        val sequential = ColorRamp(listOf(0xFFEFF3FF.toInt(), 0xFF6BAED6.toInt(), 0xFF08519C.toInt()))
    }
}

/** Maps a signed value to the diverging ramp, centred on zero and saturating at [limit]. */
fun divergingColor(value: Double, limit: Double): Int {
    if (limit <= 0.0) return ColorRamp.diverging.at(0.5)
    return ColorRamp.diverging.at(0.5 + (value / limit).coerceIn(-1.0, 1.0) / 2.0)
}
