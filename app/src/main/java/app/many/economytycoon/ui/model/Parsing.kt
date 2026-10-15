package app.many.economytycoon.ui.model

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate

/** What typing in a field produced. */
sealed class Parsed<out T> {
    data class Value<T>(val value: T) : Parsed<T>()
    data class Invalid(val reason: String) : Parsed<Nothing>()
    object Empty : Parsed<Nothing>()

    val valueOrNull: T? get() = (this as? Value)?.value
    val error: String? get() = (this as? Invalid)?.reason
}

/**
 * Reads numbers the way people type them: thousands separators and spaces are ignored, `.` is the decimal
 * mark, and a leading sign is allowed where it makes sense. No locale is consulted, so the result never
 * depends on the phone's region settings.
 */
object Parsing {
    private const val MAX_DIGITS = 15

    /** Returns the text as a plain decimal like `-1234.5`, or null when it is not a number. */
    private fun clean(text: String, allowSign: Boolean): String? {
        var t = text.trim().replace(",", "").replace(" ", "").replace("_", "")
        if (t.isEmpty()) return ""
        var negative = false
        if (t.startsWith("-") || t.startsWith("+")) {
            if (!allowSign && t.startsWith("-")) return null
            negative = t.startsWith("-")
            t = t.substring(1)
        }
        if (t.isEmpty() || t.count { it == '.' } > 1) return null
        if (t.any { !(it.isDigit() || it == '.') }) return null
        if (t == ".") return null
        if (t.length > MAX_DIGITS + 1) return null
        return (if (negative) "-" else "") + t
    }

    /** Scaled fixed-point value: `12.345` with scale 1,000 gives 12,345. Extra decimals are refused. */
    private fun fixed(text: String, scale: Long, digits: Int, allowSign: Boolean): Parsed<Long> {
        val c = clean(text, allowSign) ?: return Parsed.Invalid("Enter a number.")
        if (c.isEmpty()) return Parsed.Empty
        val negative = c.startsWith("-")
        val body = c.removePrefix("-")
        val whole = body.substringBefore('.')
        val fraction = body.substringAfter('.', "")
        if (fraction.length > digits) return Parsed.Invalid("At most $digits decimal places.")
        val wholeValue = if (whole.isEmpty()) 0L else whole.toLongOrNull() ?: return Parsed.Invalid("Enter a number.")
        val fractionValue = if (fraction.isEmpty()) 0L else fraction.padEnd(digits, '0').toLong()
        val limit = Long.MAX_VALUE / scale
        if (wholeValue >= limit) return Parsed.Invalid("That number is too large.")
        val raw = wholeValue * scale + fractionValue * (scale / pow10(digits))
        return Parsed.Value(if (negative) -raw else raw)
    }

    private fun pow10(n: Int): Long { var r = 1L; repeat(n) { r *= 10 }; return r }

    fun money(text: String, allowNegative: Boolean = false): Parsed<Money> = when (val p = fixed(text, Money.SCALE, 4, allowNegative)) {
        is Parsed.Value -> Parsed.Value(Money(p.value))
        is Parsed.Invalid -> p
        Parsed.Empty -> Parsed.Empty
    }

    fun quantity(text: String): Parsed<Quantity> = when (val p = fixed(text, Quantity.SCALE, 3, false)) {
        is Parsed.Value -> Parsed.Value(Quantity(p.value))
        is Parsed.Invalid -> p
        Parsed.Empty -> Parsed.Empty
    }

    /** A percentage typed as `12.5` becomes the rate 12.5%. */
    fun percent(text: String, allowNegative: Boolean = false): Parsed<Rate> = when (val p = fixed(text, 10_000L, 2, allowNegative)) {
        is Parsed.Value -> Parsed.Value(Rate(p.value))
        is Parsed.Invalid -> p
        Parsed.Empty -> Parsed.Empty
    }

    fun wholeNumber(text: String, allowNegative: Boolean = false): Parsed<Long> {
        val c = clean(text, allowNegative) ?: return Parsed.Invalid("Enter a whole number.")
        if (c.isEmpty()) return Parsed.Empty
        if (c.contains('.')) return Parsed.Invalid("Enter a whole number.")
        return c.toLongOrNull()?.let { Parsed.Value(it) } ?: Parsed.Invalid("That number is too large.")
    }

    fun <T> required(p: Parsed<T>): Parsed<T> = if (p == Parsed.Empty) Parsed.Invalid("This field is required.") else p
}
