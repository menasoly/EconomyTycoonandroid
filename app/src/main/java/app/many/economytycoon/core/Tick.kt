package app.many.economytycoon.core

/** Simulation time in ticks. One tick equals one in-game day. */
@JvmInline
value class Tick(val value: Long) : Comparable<Tick> {
    operator fun plus(ticks: Long) = Tick(value + ticks)
    operator fun minus(ticks: Long) = Tick(value - ticks)
    operator fun minus(other: Tick): Long = value - other.value
    override operator fun compareTo(other: Tick) = value.compareTo(other.value)
    fun next() = Tick(value + 1)

    override fun toString() = "T$value"

    companion object {
        val ZERO = Tick(0)
    }
}

/** In-game calendar: 12 months of 30 days, so a year is 360 ticks. */
object Calendar {
    const val DAYS_PER_MONTH = 30
    const val MONTHS_PER_YEAR = 12
    const val DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR
    const val DAYS_PER_WEEK = 7

    /** Starting year of tick 0. */
    const val START_YEAR = 2000

    fun date(tick: Tick): GameDate {
        val t = tick.value
        require(t >= 0) { "negative tick" }
        val year = START_YEAR + (t / DAYS_PER_YEAR).toInt()
        val dayOfYear = (t % DAYS_PER_YEAR).toInt()
        return GameDate(year, dayOfYear / DAYS_PER_MONTH + 1, dayOfYear % DAYS_PER_MONTH + 1)
    }

    fun dayOfWeek(tick: Tick): Int = (tick.value % DAYS_PER_WEEK).toInt()

    fun season(tick: Tick): Season = date(tick).season()

    fun isMonthStart(tick: Tick) = tick.value % DAYS_PER_MONTH == 0L
    fun isQuarterStart(tick: Tick) = tick.value % (DAYS_PER_MONTH * 3) == 0L
    fun isYearStart(tick: Tick) = tick.value % DAYS_PER_YEAR == 0L
    fun isWeekStart(tick: Tick) = tick.value % DAYS_PER_WEEK == 0L

    fun tickOf(date: GameDate): Tick {
        require(date.year >= START_YEAR) { "date before calendar start" }
        val days = (date.year - START_YEAR).toLong() * DAYS_PER_YEAR +
            (date.month - 1).toLong() * DAYS_PER_MONTH + (date.day - 1)
        return Tick(days)
    }
}

data class GameDate(val year: Int, val month: Int, val day: Int) : Comparable<GameDate> {
    init {
        require(month in 1..Calendar.MONTHS_PER_YEAR) { "month out of range" }
        require(day in 1..Calendar.DAYS_PER_MONTH) { "day out of range" }
    }

    fun season(): Season = when (month) {
        12, 1, 2 -> Season.WINTER
        3, 4, 5 -> Season.SPRING
        6, 7, 8 -> Season.SUMMER
        else -> Season.AUTUMN
    }

    override fun compareTo(other: GameDate): Int =
        compareValuesBy(this, other, GameDate::year, GameDate::month, GameDate::day)

    override fun toString() = "%04d-%02d-%02d".format(year, month, day)
}

enum class Season { WINTER, SPRING, SUMMER, AUTUMN }
