package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.core.splitLargestRemainder

/**
 * How demand moves through the day, the week and the seasons, in parts per million of the average draw. Each daily
 * curve is normalised so its 24 hours add up to exactly 24 million: a load's base draw really is its daily average.
 */
object LoadShape {
    private const val DAY_TOTAL = 24_000_000L

    private fun normalised(raw: IntArray): LongArray =
        splitLargestRemainder(DAY_TOTAL, LongArray(raw.size) { raw[it].toLong() })

    private val HOUSEHOLD = normalised(intArrayOf(
        60, 55, 52, 50, 52, 60, 85, 100, 95, 85, 78, 75, 74, 74, 76, 82, 95, 125, 150, 155, 140, 115, 90, 70,
    ))
    private val COMMERCIAL = normalised(intArrayOf(
        30, 30, 30, 30, 30, 35, 45, 70, 100, 125, 130, 132, 130, 128, 130, 132, 128, 115, 90, 70, 55, 45, 38, 32,
    ))
    private val INDUSTRIAL = normalised(intArrayOf(
        88, 88, 88, 88, 88, 88, 95, 105, 112, 112, 112, 112, 112, 112, 112, 112, 112, 110, 105, 100, 96, 92, 90, 88,
    ))

    /** Demand in the given hour as a share of the daily average, in ppm. */
    fun hourPpm(customer: CustomerClass, hour: Int): Long {
        require(hour in 0 until GridUnits.HOURS_PER_DAY) { "hour out of range" }
        return when (customer) {
            CustomerClass.HOUSEHOLD -> HOUSEHOLD[hour]
            CustomerClass.COMMERCIAL -> COMMERCIAL[hour]
            CustomerClass.INDUSTRIAL, CustomerClass.INTERRUPTIBLE -> INDUSTRIAL[hour]
        }
    }

    /** The full curve of a class, 24 values. */
    fun curve(customer: CustomerClass): List<Long> = List(GridUnits.HOURS_PER_DAY) { hourPpm(customer, it) }

    fun isWeekend(tick: Tick): Boolean = Calendar.dayOfWeek(tick) >= 5

    /** Weekend effect on the day's total draw, in ppm. */
    fun weekdayPpm(customer: CustomerClass, tick: Tick): Long {
        if (!isWeekend(tick)) return Rate.ONE_PPM
        return when (customer) {
            CustomerClass.HOUSEHOLD -> 1_050_000L
            CustomerClass.COMMERCIAL -> 700_000L
            CustomerClass.INDUSTRIAL -> 850_000L
            CustomerClass.INTERRUPTIBLE -> 850_000L
        }
    }

    /** Effect of heating and cooling needs on a class, in ppm: only its thermal share follows the weather. */
    fun thermalPpm(customer: CustomerClass, weather: GridWeather): Long {
        val share = customer.thermalSharePpm
        return (Rate.ONE_PPM - share) + mulDiv(share, weather.thermalIndexPpm, Rate.ONE_PPM, RoundingMode.HALF_EVEN)
    }

    /** Draw in kilowatts of a [baseKw] load of this class in one hour of one day. */
    fun demandKw(baseKw: Long, customer: CustomerClass, tick: Tick, hour: Int, weather: GridWeather): Long {
        if (baseKw <= 0L) return 0L
        val shaped = mulDiv(baseKw, hourPpm(customer, hour), Rate.ONE_PPM, RoundingMode.HALF_EVEN)
        val modifier = mulDiv(weekdayPpm(customer, tick), thermalPpm(customer, weather), Rate.ONE_PPM, RoundingMode.HALF_EVEN)
        return mulDiv(shaped, modifier, Rate.ONE_PPM, RoundingMode.HALF_EVEN)
    }

    /** The 24 hourly demands of one load. */
    fun dayKw(baseKw: Long, customer: CustomerClass, tick: Tick, weather: GridWeather): LongArray =
        LongArray(GridUnits.HOURS_PER_DAY) { demandKw(baseKw, customer, tick, it, weather) }
}
