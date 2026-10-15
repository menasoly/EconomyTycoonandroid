package app.many.economytycoon.session

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.GameDate
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick

/**
 * Turns engine values into the text screens show. Everything here is locale independent on purpose: the
 * same save must read the same on every device, and tests must not depend on the machine's locale.
 */
object Formatting {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
    )

    /** Whole units with thousands separators, e.g. `-1,234,567`. */
    fun grouped(whole: Long): String {
        val negative = whole < 0
        val digits = if (whole == Long.MIN_VALUE) "9223372036854775808" else Math.abs(whole).toString()
        val sb = StringBuilder()
        for ((i, ch) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        return if (negative) "-$sb" else sb.toString()
    }

    /** `1,234.56` with exactly two decimals; the third decimal rounds half away from zero. */
    fun money(m: Money, symbol: String = ""): String {
        val cents = roundedCents(m)
        val negative = cents < 0
        val abs = Math.abs(cents)
        val body = grouped(abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + symbol + body
    }

    private fun roundedCents(m: Money): Long {
        val perCent = Money.SCALE / 100
        val half = perCent / 2
        return if (m.raw >= 0) (m.raw + half) / perCent else -((-m.raw + half) / perCent)
    }

    /** Short form for tight spaces: `950`, `12.3K`, `4.56M`, `7.8B`, `1.2T`. */
    fun compactMoney(m: Money, symbol: String = ""): String {
        val whole = m.raw / Money.SCALE
        val negative = m.raw < 0
        val abs = Math.abs(whole)
        val text = when {
            abs < 1_000 -> abs.toString()
            abs < 1_000_000 -> scaled(abs, 1_000L, "K")
            abs < 1_000_000_000 -> scaled(abs, 1_000_000L, "M")
            abs < 1_000_000_000_000 -> scaled(abs, 1_000_000_000L, "B")
            else -> scaled(abs, 1_000_000_000_000L, "T")
        }
        return (if (negative) "-" else "") + symbol + text
    }

    private fun scaled(value: Long, unit: Long, suffix: String): String {
        // Three significant digits: 1.23K, 12.3K, 123K.
        val tenths = value * 100 / unit
        return when {
            tenths < 1_000 -> "${tenths / 100}.${(tenths % 100).toString().padStart(2, '0')}$suffix"
            tenths < 10_000 -> "${tenths / 100}.${(tenths % 100 / 10)}$suffix"
            else -> "${tenths / 100}$suffix"
        }
    }

    /** Quantities show up to two decimals, trimmed: `12`, `12.5`, `0.25`. */
    fun quantity(q: Quantity, unit: String = ""): String {
        val negative = q.raw < 0
        val abs = Math.abs(q.raw)
        val whole = abs / Quantity.SCALE
        val hundredths = (abs % Quantity.SCALE) * 100 / Quantity.SCALE
        val fraction = if (hundredths == 0L) "" else "." + hundredths.toString().padStart(2, '0').trimEnd('0')
        val text = grouped(whole) + fraction
        return (if (negative) "-" else "") + text + (if (unit.isBlank()) "" else " $unit")
    }

    /** `12.3%` from a rate; one decimal. */
    fun percent(r: Rate): String = percentPpm(r.ppm)

    fun percentPpm(ppm: Long): String {
        val negative = ppm < 0
        val abs = Math.abs(ppm)
        val tenths = (abs + 500) / 1_000
        return (if (negative && tenths != 0L) "-" else "") + (tenths / 10) + "." + (tenths % 10) + "%"
    }

    /** `+3.2%` or `-1.0%`; zero shows as `0.0%`. */
    fun signedPercentPpm(ppm: Long): String {
        val text = percentPpm(ppm)
        return if (ppm > 0 && !text.startsWith("0.0")) "+$text" else text
    }

    /** A ratio such as `1.85x`; two decimals, rounded half away from zero. */
    fun ratioTimes(ppm: Long): String {
        val negative = ppm < 0
        val hundredths = (Math.abs(ppm) + 5_000) / 10_000
        return (if (negative && hundredths != 0L) "-" else "") + (hundredths / 100) + "." + (hundredths % 100).toString().padStart(2, '0') + "x"
    }

    fun signedMoney(m: Money, symbol: String = ""): String = (if (m.raw > 0) "+" else "") + money(m, symbol)

    /** `12 Mar 2003`. */
    fun date(tick: Tick): String = date(Calendar.date(tick))
    fun date(d: GameDate): String = "${d.day} ${MONTHS[d.month - 1]} ${d.year}"
    fun monthYear(tick: Tick): String = Calendar.date(tick).let { "${MONTH_NAMES[it.month - 1]} ${it.year}" }

    /** `2 years 3 months`, `5 days`; the two largest non-zero units. */
    fun duration(ticks: Long): String {
        if (ticks <= 0) return "0 days"
        val years = ticks / Calendar.DAYS_PER_YEAR
        val months = ticks % Calendar.DAYS_PER_YEAR / Calendar.DAYS_PER_MONTH
        val days = ticks % Calendar.DAYS_PER_MONTH
        val parts = ArrayList<String>()
        if (years > 0) parts += plural(years, "year")
        if (months > 0) parts += plural(months, "month")
        if (days > 0 && years == 0L) parts += plural(days, "day")
        return parts.take(2).joinToString(" ")
    }

    fun plural(n: Long, noun: String): String {
        if (n == 1L) return "$n $noun"
        val vowelBefore = noun.length > 1 && noun[noun.length - 2] in "aeiou"
        return when {
            noun.endsWith("y") && !vowelBefore -> "$n ${noun.dropLast(1)}ies"
            noun.endsWith("s") || noun.endsWith("x") || noun.endsWith("ch") || noun.endsWith("sh") -> "${n} ${noun}es"
            else -> "$n ${noun}s"
        }
    }

    /** A rule's value as people read it: rates as percentages, the minimum wage as money per day. */
    fun leverValue(lever: app.many.economytycoon.regulation.Lever, value: Long): String =
        if (lever.unit == app.many.economytycoon.regulation.Lever.Unit.WAGE) money(Money.units(value)) + " a day" else percentPpm(value)

    /** One line describing a law, e.g. "a cut to the corporate tax from 20% to 18%". */
    fun lawSummary(law: app.many.economytycoon.regulation.Law): String =
        "a ${if (law.raises) "rise" else "cut"} to the ${law.lever.label.lowercase()} from ${leverValue(law.lever, law.from)} to ${leverValue(law.lever, law.to)}"

    /** Display name from a content key: `spare_parts` becomes `Spare parts`. */
    fun title(key: String): String {
        val s = key.replace('_', ' ').replace('-', ' ').trim()
        return if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
    }

    /** Whole-number population style: `1.2M inhabitants` is made by [compactCount]. */
    fun compactCount(n: Long): String = compactMoney(Money.units(n))
}
