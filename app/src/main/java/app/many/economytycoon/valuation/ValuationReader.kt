package app.many.economytycoon.valuation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.simulation.Economy

/** Builds a [ValuationInput] from the ledger and the exchange. */
class ValuationReader(private val e: Economy) {
    fun input(company: CompanyId, window: Long = WINDOW_DAYS): ValuationInput? {
        val c = e.companies.company(company) ?: return null
        val now = e.tick
        val from = Tick((now.value - window).coerceAtLeast(0L))
        val span = (now.value - from.value).coerceAtLeast(1L)
        val income = e.finance.statements.income(c.owner, from, now)
        val balance = e.finance.statements.balance(c.owner, now)
        val flow = e.finance.statements.cashFlow(c.owner, from, now)
        fun annual(m: Money) = Money(mulDiv(m.raw, DAYS_PER_YEAR, span))
        val shares = e.finance.equity.table(c.owner)?.totalOutstanding() ?: 0L
        return ValuationInput(
            revenue = annual(income.revenue),
            ebitda = annual(income.ebitda),
            netIncome = annual(income.netIncome - income.otherIncome),
            freeCashFlow = annual(flow.freeCashFlow),
            cash = balance.cash,
            debt = balance.totalDebt,
            receivables = balance.receivables,
            inventory = balance.inventory,
            fixedAssets = balance.fixedAssetsNet,
            intangibles = balance.intangibles,
            investments = balance.investments,
            loansReceivable = balance.loansReceivable,
            liabilities = balance.totalLiabilities,
            bookEquity = balance.totalEquity,
            shares = shares,
            marketPrice = e.exchange.listing(company)?.price,
            historyDays = (now.value - c.founded.value).coerceAtLeast(0L).coerceAtMost(span),
        )
    }

    fun summary(company: CompanyId, a: ValuationAssumptions = ValuationAssumptions.DEFAULT): ValuationSummary? = input(company)?.let { ValuationSummary.of(it, a) }

    companion object {
        const val WINDOW_DAYS = 30L
        const val DAYS_PER_YEAR = 360L
    }
}
