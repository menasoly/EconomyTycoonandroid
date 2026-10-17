package app.many.economytycoon.cashflow

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** How hard to lean on the recent run rate. */
enum class Scenario(val label: String, val earningsPpm: Long, val costsPpm: Long) {
    CAUTIOUS("Cautious", 800_000L, 1_150_000L),
    EXPECTED("Expected", 1_000_000L, 1_000_000L),
    HOPEFUL("Hopeful", 1_150_000L, 900_000L),
}

/** A known sum on a known day, such as a loan payment. */
data class Lump(val day: Long, val label: String, val amount: Money)

/** The balance at the end of one day, and what moved it. */
data class CashPoint(val day: Long, val balance: Money, val change: Money, val lumps: List<Lump>)

/**
 * The company's cash, day by day, from today's balance, the recent daily run rate and the sums already due. Earning
 * days are scaled one way and spending days the other by the [Scenario].
 */
data class CashProjection(val start: Money, val startDay: Long, val points: List<CashPoint>, val scenario: Scenario) {
    val end: Money get() = points.lastOrNull()?.balance ?: start
    val lowest: CashPoint? get() = points.minByOrNull { it.balance.raw }
    val highest: CashPoint? get() = points.maxByOrNull { it.balance.raw }

    /** The first day the balance is below zero, or null if it never is. */
    val firstShortfall: CashPoint? get() = points.firstOrNull { it.balance.raw < 0L }

    val totalIn: Money get() = Money(points.sumOf { p -> p.change.raw.coerceAtLeast(0L) })
    val totalOut: Money get() = Money(points.sumOf { p -> p.change.raw.coerceAtMost(0L) })
    val lumpOut: Money get() = Money(points.sumOf { p -> p.lumps.sumOf { it.amount.raw.coerceAtMost(0L) } })
    val lumpIn: Money get() = Money(points.sumOf { p -> p.lumps.sumOf { it.amount.raw.coerceAtLeast(0L) } })

    /** The most that has to be found to stay at or above zero, or zero if cash never runs short. */
    val gapToCover: Money get() = lowest?.balance?.takeIf { it.raw < 0L }?.let { Money(-it.raw) } ?: Money.ZERO

    fun balanceOn(day: Long): Money = points.firstOrNull { it.day == day }?.balance ?: if (day <= startDay) start else end

    companion object {
        /**
         * [perDay] is the signed run rate before any one-off sums. A positive rate is scaled by the scenario's earnings
         * factor, a negative one by its costs factor, so a cautious view shrinks gains and grows losses alike.
         */
        fun build(start: Money, startDay: Long, perDay: Money, lumps: List<Lump>, horizon: Int, scenario: Scenario = Scenario.EXPECTED): CashProjection {
            require(horizon in 1..720) { "horizon out of range" }
            val factor = if (perDay.raw >= 0L) scenario.earningsPpm else scenario.costsPpm
            val daily = Money(mulDiv(perDay.raw, factor, 1_000_000L))
            val byDay = lumps.groupBy { it.day }
            var balance = start
            val points = ArrayList<CashPoint>(horizon)
            for (i in 1..horizon) {
                val day = startDay + i
                val today = byDay[day].orEmpty().sortedBy { it.label }
                val change = daily + Money(today.sumOf { it.amount.raw })
                balance += change
                points += CashPoint(day, balance, change, today)
            }
            return CashProjection(start, startDay, points, scenario)
        }
    }
}

/** The three scenarios side by side. */
data class CashOutlook(val cautious: CashProjection, val expected: CashProjection, val hopeful: CashProjection) {
    fun of(s: Scenario): CashProjection = when (s) { Scenario.CAUTIOUS -> cautious; Scenario.EXPECTED -> expected; Scenario.HOPEFUL -> hopeful }
    val all: List<CashProjection> get() = listOf(cautious, expected, hopeful)

    /** True when even the hopeful case runs short. */
    val certainShortfall: Boolean get() = hopeful.firstShortfall != null

    /** True when only the cautious case runs short. */
    val atRisk: Boolean get() = cautious.firstShortfall != null && expected.firstShortfall == null

    companion object {
        fun build(start: Money, startDay: Long, perDay: Money, lumps: List<Lump>, horizon: Int): CashOutlook =
            CashOutlook(
                CashProjection.build(start, startDay, perDay, lumps, horizon, Scenario.CAUTIOUS),
                CashProjection.build(start, startDay, perDay, lumps, horizon, Scenario.EXPECTED),
                CashProjection.build(start, startDay, perDay, lumps, horizon, Scenario.HOPEFUL),
            )
    }
}
