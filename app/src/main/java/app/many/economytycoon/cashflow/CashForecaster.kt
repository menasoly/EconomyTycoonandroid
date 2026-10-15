package app.many.economytycoon.cashflow

import app.many.economytycoon.agenda.AgendaBuilder
import app.many.economytycoon.agenda.AgendaItem
import app.many.economytycoon.agenda.AgendaKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.simulation.Economy

/** What goes into a forecast besides today's balance. */
data class ForecastBasis(val cash: Money, val perDay: Money, val lumps: List<Lump>, val windowDays: Long)

/** Builds a [CashOutlook] for a company from its ledger and its agenda. */
class CashForecaster(private val e: Economy) {
    /**
     * The run rate is the average daily operating and investing cash flow over the last [window] days. Financing is left
     * out because loan instalments are projected separately as dated sums. Interest sits in both, so the forecast errs
     * a little on the cautious side.
     */
    fun basis(company: CompanyId, horizon: Int = DEFAULT_HORIZON, window: Long = WINDOW_DAYS): ForecastBasis? {
        val c = e.companies.company(company) ?: return null
        val now = e.tick
        val from = Tick((now.value - window).coerceAtLeast(0L))
        val span = (now.value - from.value).coerceAtLeast(1L)
        val flow = e.finance.statements.cashFlow(c.owner, from, now)
        val organic = flow.operating + flow.investing
        val perDay = Money(Math.floorDiv(organic.raw, span))
        val agenda = AgendaBuilder(e).build(company, now.value, horizon)
        val lumps = agenda.items.mapNotNull { lump(it) }
        return ForecastBasis(e.finance.statements.balance(c.owner, now).cash, perDay, lumps, span)
    }

    fun outlook(company: CompanyId, horizon: Int = DEFAULT_HORIZON): CashOutlook? {
        val b = basis(company, horizon) ?: return null
        return CashOutlook.build(b.cash, e.tick.value, b.perDay, b.lumps, horizon)
    }

    /** Known payments only: loan instalments. Deliveries are already part of the run rate, so counting them again would double up. */
    private fun lump(i: AgendaItem): Lump? =
        if (i.kind == AgendaKind.LOAN_PAYMENT && i.cash != null) Lump(i.day, i.title, i.cash) else null

    companion object {
        const val DEFAULT_HORIZON = 90
        const val WINDOW_DAYS = 30L
    }
}
