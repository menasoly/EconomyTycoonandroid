package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.IncomeStatement

/** One line of a company's costs and what share of revenue it takes. */
data class CostLine(val label: String, val amount: Money, val sharePpm: Long, val variable: Boolean)

/**
 * Where the money goes, and how much of it stops when sales stop. Materials, energy, transport and waste
 * move with output; wages, rent, upkeep, marketing, research and interest are owed whatever is sold. The split
 * gives the break-even point and the operating leverage (how hard a fall in sales hits profit).
 */
data class CostStructure(
    val revenue: Money,
    val lines: List<CostLine>,
    val variable: Money,
    val fixed: Money,
) {
    val total: Money get() = variable + fixed

    /** What each unit of revenue contributes after variable costs, in ppm. */
    val contributionMarginPpm: Long get() = if (revenue.raw <= 0L) 0L else mulDiv(revenue.raw - variable.raw, PPM, revenue.raw)

    /** Revenue at which profit is zero for the period; null when sales never cover the variable costs. */
    val breakEvenRevenue: Money? get() {
        val cm = contributionMarginPpm
        if (cm <= 0L) return null
        return Money(mulDiv(fixed.raw, PPM, cm))
    }

    /** How far sales can fall before the period turns loss-making, in ppm of revenue; negative when already below. */
    val marginOfSafetyPpm: Long? get() {
        val be = breakEvenRevenue ?: return null
        if (revenue.raw <= 0L) return null
        return mulDiv(revenue.raw - be.raw, PPM, revenue.raw)
    }

    /** Percentage change in profit for a one percent change in sales, in ppm (2,000,000 means a 1% dip costs 2%). */
    val operatingLeveragePpm: Long? get() {
        val profit = revenue.raw - variable.raw - fixed.raw
        if (profit <= 0L) return null
        return mulDiv(revenue.raw - variable.raw, PPM, profit)
    }

    fun line(label: String): CostLine? = lines.firstOrNull { it.label == label }

    companion object {
        private const val PPM = 1_000_000L

        fun of(i: IncomeStatement): CostStructure {
            val rev = i.revenue
            fun line(label: String, m: Money, variable: Boolean) =
                CostLine(label, m, if (rev.raw <= 0L) 0L else mulDiv(m.raw, PPM, rev.raw), variable)
            val lines = listOf(
                line("Materials", i.cogs, true),
                line("Energy", i.energy, true),
                line("Transport", i.transport, true),
                line("Waste", i.waste, true),
                line("Wages", i.wages, false),
                line("Rent", i.rent, false),
                line("Maintenance", i.maintenance, false),
                line("Marketing", i.marketing, false),
                line("Research", i.research, false),
                line("Other", i.otherExpenses, false),
                line("Depreciation", i.depreciation, false),
                line("Interest", i.interestExpense, false),
            ).filter { it.amount.raw != 0L }
            val v = Money.sum(lines.filter { it.variable }.map { it.amount })
            val f = Money.sum(lines.filter { !it.variable }.map { it.amount })
            return CostStructure(rev, lines.sortedByDescending { it.amount.raw }, v, f)
        }
    }
}
