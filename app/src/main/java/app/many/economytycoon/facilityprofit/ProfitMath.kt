package app.many.economytycoon.facilityprofit

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** The arithmetic behind a line's profit, kept apart from the game so it can be tested on its own. */
object ProfitMath {
    private const val PPM = 1_000_000L

    /** Output worth after defects: defective batches sell for nothing. */
    fun goodOutput(outputValue: Money, defectPpm: Long): Money =
        Money(mulDiv(outputValue.raw, (PPM - defectPpm.coerceIn(0L, PPM)), PPM))

    fun profitPerBatch(outputValue: Money, defectPpm: Long, inputCost: Money, labour: Money, energy: Money): Money =
        goodOutput(outputValue, defectPpm) - inputCost - labour - energy

    /** What a line earns each day when it runs [utilisationPpm] of the time; a batch takes [durationDays] days. */
    fun perDay(profitPerBatch: Money, durationDays: Int, utilisationPpm: Long): Money {
        val full = profitPerBatch.raw / durationDays.coerceAtLeast(1)
        return Money(mulDiv(full, utilisationPpm.coerceIn(0L, PPM), PPM))
    }

    /** Return on capital over a year, in ppm of the capital; zero capital gives zero. */
    fun annualReturnPpm(netPerDay: Money, capital: Money, daysPerYear: Long = 360L): Long =
        if (capital.raw <= 0L) 0L else mulDiv(netPerDay.raw * daysPerYear, PPM, capital.raw)

    /** Days to earn back [capital]; null when the facility does not earn. */
    fun paybackDays(netPerDay: Money, capital: Money): Long? =
        if (netPerDay.raw <= 0L) null else (capital.raw + netPerDay.raw - 1L) / netPerDay.raw

    /** Margin on cost in ppm: profit over everything spent making the batch. */
    fun marginPpm(profit: Money, cost: Money): Long =
        if (cost.raw <= 0L) 0L else mulDiv(profit.raw, PPM, cost.raw)

    /** Share of [part] in [whole], in ppm; zero when there is no whole. */
    fun sharePpm(part: Money, whole: Money): Long =
        if (whole.raw <= 0L) 0L else mulDiv(part.raw.coerceAtLeast(0L), PPM, whole.raw).coerceAtMost(PPM)
}
