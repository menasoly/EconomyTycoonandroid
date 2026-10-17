package app.many.economytycoon.i18n

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick

/**
 * Numbers, money, percentages and dates written the way a locale writes them. Like the base formatter this
 * works in integers only, so output never depends on the device.
 */
class LocaleFormat(val locale: GameLocale) {
    fun grouped(whole: Long): String {
        val negative = whole < 0
        val digits = if (whole == Long.MIN_VALUE) "9223372036854775808" else Math.abs(whole).toString()
        val sb = StringBuilder()
        for ((i, ch) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(locale.groupSeparator)
            sb.append(ch)
        }
        return if (negative) "-$sb" else sb.toString()
    }

    /** Two decimals, grouped, with the symbol where the locale puts it. */
    fun money(m: Money, symbol: String = ""): String {
        val perCent = Money.SCALE / 100
        val half = perCent / 2
        val cents = if (m.raw >= 0) (m.raw + half) / perCent else -((-m.raw + half) / perCent)
        val abs = Math.abs(cents)
        val body = grouped(abs / 100) + locale.decimalSeparator + (abs % 100).toString().padStart(2, '0')
        return withSymbol(body, symbol, cents < 0)
    }

    private fun withSymbol(body: String, symbol: String, negative: Boolean): String {
        val sign = if (negative) "-" else ""
        return when {
            symbol.isEmpty() -> sign + body
            locale.symbolAfter -> "$sign$body $symbol"
            else -> sign + symbol + body
        }
    }

    /** `950`, `12,3 mil`, `4,56 M` and so on, three significant digits. */
    fun compactMoney(m: Money, symbol: String = ""): String {
        val whole = Math.abs(m.raw / Money.SCALE)
        val negative = m.raw < 0
        val steps = longArrayOf(1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L)
        var index = -1
        for (i in steps.indices) if (whole >= steps[i]) index = i
        val body = if (index < 0) whole.toString() else scaled(whole, steps[index]) + spaceBefore(locale.compactSuffixes[index])
        return withSymbol(body, symbol, negative)
    }

    private fun spaceBefore(suffix: String): String = if (suffix.length > 1 || locale != GameLocale.EN) " $suffix" else suffix

    private fun scaled(value: Long, unit: Long): String {
        val tenths = value * 100 / unit
        val d = locale.decimalSeparator
        return when {
            tenths < 1_000 -> "${tenths / 100}$d${(tenths % 100).toString().padStart(2, '0')}"
            tenths < 10_000 -> "${tenths / 100}$d${tenths % 100 / 10}"
            else -> "${tenths / 100}"
        }
    }

    fun quantity(q: Quantity): String {
        val abs = Math.abs(q.raw)
        val whole = abs / Quantity.SCALE
        val hundredths = (abs % Quantity.SCALE) * 100 / Quantity.SCALE
        val fraction = if (hundredths == 0L) "" else locale.decimalSeparator + hundredths.toString().padStart(2, '0').trimEnd('0')
        return (if (q.raw < 0) "-" else "") + grouped(whole) + fraction
    }

    fun percentPpm(ppm: Long): String {
        val abs = Math.abs(ppm)
        val tenths = (abs + 500) / 1_000
        val sign = if (ppm < 0 && tenths != 0L) "-" else ""
        val gap = if (locale.spacedPercent) " " else ""
        return "$sign${tenths / 10}${locale.decimalSeparator}${tenths % 10}$gap%"
    }

    fun signedPercentPpm(ppm: Long): String {
        val text = percentPpm(ppm)
        return if (ppm > 0 && !text.startsWith("0${locale.decimalSeparator}0")) "+$text" else text
    }

    /** `12 mars 2003`, `12 Mar 2003`. */
    fun date(tick: Tick): String {
        val d = Calendar.date(tick)
        return when (locale) {
            GameLocale.EN -> "${d.day} ${locale.monthAbbreviations[d.month - 1]} ${d.year}"
            GameLocale.DE -> "${d.day}. ${locale.monthAbbreviations[d.month - 1]} ${d.year}"
            GameLocale.ES -> "${d.day} ${locale.monthAbbreviations[d.month - 1]} ${d.year}"
            GameLocale.FR -> "${d.day} ${locale.monthAbbreviations[d.month - 1]} ${d.year}"
        }
    }

    fun monthYear(tick: Tick): String {
        val d = Calendar.date(tick)
        val name = locale.monthNames[d.month - 1]
        return when (locale) {
            GameLocale.ES -> "$name de ${d.year}"
            else -> "$name ${d.year}"
        }
    }
}
