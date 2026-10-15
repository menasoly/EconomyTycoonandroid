package app.many.economytycoon.reporting

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Economy

/** A company's place in the table for one year. */
data class YearStanding(val company: CompanyId, val name: String, val revenue: Money, val netIncome: Money, val netWorth: Money)

/**
 * Builds yearly reports straight from the ledger. Nothing is stored: the ledger can answer for any past day,
 * so a loaded game reports its history exactly as the original run would have.
 */
class AnnualReports(private val e: Economy) {
    private val yearDays = Calendar.DAYS_PER_YEAR.toLong()

    /** Whole years that have passed. */
    fun completedYears(): Int = (e.tick.value / yearDays).toInt()

    /** Index of the year now under way. */
    fun currentIndex(): Int = completedYears()

    fun yearStart(index: Int): Tick = Tick(index * yearDays)
    fun yearEnd(index: Int): Tick = Tick((index + 1) * yearDays)

    private fun build(id: CompanyId, index: Int, prior: AnnualReport?): AnnualReport? {
        val c = e.companies.company(id) ?: return null
        if (index < 0) return null
        val from = yearStart(index)
        val end = yearEnd(index)
        val partial = e.tick < end
        val to = if (partial) e.tick else end
        if (to < from) return null
        val st = e.finance.statements
        return AnnualReport(id, c.name, index, from, to, partial, st.income(c.owner, from, to), st.balance(c.owner, from), st.balance(c.owner, to), prior)
    }

    private fun active(r: AnnualReport): Boolean =
        r.revenue.raw != 0L || r.netIncome.raw != 0L || r.closing.totalAssets.raw != 0L || r.opening.totalAssets.raw != 0L

    /** One year of one company, linked to the year before when there is one. */
    fun report(id: CompanyId, index: Int): AnnualReport? {
        val prior = if (index > 0) report(id, index - 1)?.takeIf { active(it) } else null
        return build(id, index, prior)
    }

    /** Every year the company was around for, oldest first, ending with the year in progress. */
    fun history(id: CompanyId): List<AnnualReport> {
        e.companies.company(id) ?: return emptyList()
        val out = ArrayList<AnnualReport>()
        var prior: AnnualReport? = null
        for (i in 0..currentIndex()) {
            val r = build(id, i, prior) ?: continue
            if (!active(r)) { prior = null; continue }
            out += r
            prior = r
        }
        return out
    }

    fun latestComplete(id: CompanyId): AnnualReport? = history(id).lastOrNull { !it.partial }

    /** Companies ranked for one year by net worth at its close. */
    fun table(index: Int): List<YearStanding> {
        val from = yearStart(index)
        val to = if (e.tick < yearEnd(index)) e.tick else yearEnd(index)
        val st = e.finance.statements
        return e.companies.all().filter { it.kind != CompanyKind.STATE }.mapNotNull { c ->
            val income = st.income(c.owner, from, to)
            val worth = st.balance(c.owner, to).netWorth
            if (income.revenue.raw == 0L && worth.raw == 0L) null else YearStanding(c.id, c.name, income.revenue, income.netIncome, worth)
        }.sortedWith(compareByDescending<YearStanding> { it.netWorth.raw }.thenBy { it.company })
    }

    /** One-based place in the year's table, or null for a company that was not in it. */
    fun rank(id: CompanyId, index: Int): Int? = table(index).indexOfFirst { it.company == id }.takeIf { it >= 0 }?.plus(1)
}
