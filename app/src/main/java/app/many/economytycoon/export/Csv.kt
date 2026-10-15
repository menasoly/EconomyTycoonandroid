package app.many.economytycoon.export

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import java.math.BigDecimal
import java.math.RoundingMode

/** Comma-separated text that spreadsheets open without fuss: quoted where needed, one record per line. */
object Csv {
    const val NEWLINE = "\r\n"

    /** A field, quoted when it holds a comma, a quote or a line break, and guarded against being read as a formula. */
    fun field(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_STARTS && !isNumber(value)) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun row(fields: List<String>): String = fields.joinToString(",") { field(it) }

    fun table(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append(row(header)).append(NEWLINE)
        for (r in rows) sb.append(row(r)).append(NEWLINE)
        return sb.toString()
    }

    private val FORMULA_STARTS = charArrayOf('=', '+', '@', '\t', '\r')

    private fun isNumber(s: String): Boolean = s.toDoubleOrNull() != null

    /** Splits text written by [table] back into records. Used to check that what is written can be read again. */
    fun parse(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        var any = false
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> { quoted = !quoted; any = true }
                !quoted && ch == ',' -> { row += cur.toString(); cur.setLength(0); any = true }
                !quoted && ch == '\r' -> { }
                !quoted && ch == '\n' -> { if (any || cur.isNotEmpty()) { row += cur.toString(); out += row }; row = ArrayList(); cur.setLength(0); any = false }
                else -> { cur.append(ch); any = true }
            }
            i++
        }
        if (any || cur.isNotEmpty()) { row += cur.toString(); out += row }
        return out
    }
}

/** Figures written for a spreadsheet: plain digits with a point, no symbols or grouping. */
object Plain {
    fun money(m: Money): String = BigDecimal(m.raw).movePointLeft(4).setScale(2, RoundingMode.HALF_UP).toPlainString()

    fun quantity(q: Quantity): String = BigDecimal(q.raw).movePointLeft(3).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()

    /** Parts per million as a percentage with at most two decimals. */
    fun percent(ppm: Long): String = BigDecimal(ppm).movePointLeft(4).setScale(2, RoundingMode.HALF_UP).toPlainString()

    fun optional(m: Money?): String = m?.let { money(it) } ?: ""
}
