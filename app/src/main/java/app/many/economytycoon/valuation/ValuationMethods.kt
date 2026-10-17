package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Valuation

enum class Method(val label: String, val weight: Int) {
    DCF("Discounted cash flow", 30),
    EARNINGS("Earnings multiple", 25),
    EBITDA("EBITDA multiple", 20),
    BOOK("Book value", 15),
    LIQUIDATION("Break-up value", 5),
    REVENUE("Revenue multiple", 5),
}

/** What one method says the whole equity is worth. */
data class MethodValue(val method: Method, val applicable: Boolean, val equity: Money, val shares: Long, val note: String) {
    val perShare: Money get() = Valuation.perShare(equity, shares)
}

object ValuationMethods {
    private const val PPM = 1_000_000L

    fun value(method: Method, i: ValuationInput, a: ValuationAssumptions = ValuationAssumptions.DEFAULT): MethodValue = when (method) {
        Method.BOOK -> book(i)
        Method.EARNINGS -> earnings(i, a)
        Method.EBITDA -> ebitda(i, a)
        Method.DCF -> dcf(i, a)
        Method.LIQUIDATION -> liquidation(i, a)
        Method.REVENUE -> revenue(i, a)
    }

    fun all(i: ValuationInput, a: ValuationAssumptions = ValuationAssumptions.DEFAULT): List<MethodValue> = Method.values().map { value(it, i, a) }

    private fun floor(m: Money) = Money.max(m, Money.ZERO)

    fun book(i: ValuationInput) = MethodValue(Method.BOOK, true, floor(i.bookEquity), i.shares, "What the books say the owners hold.")

    fun earnings(i: ValuationInput, a: ValuationAssumptions): MethodValue =
        if (i.netIncome.raw <= 0L) MethodValue(Method.EARNINGS, false, Money.ZERO, i.shares, "Not applicable: the company is not making a profit.")
        else MethodValue(Method.EARNINGS, true, floor(i.netIncome.times(a.earningsMultiple)), i.shares, "Annual profit times ${a.earningsMultiple.ppm / PPM} (rounded).")

    fun ebitda(i: ValuationInput, a: ValuationAssumptions): MethodValue =
        if (i.ebitda.raw <= 0L) MethodValue(Method.EBITDA, false, Money.ZERO, i.shares, "Not applicable: operations earn nothing before interest and tax.")
        else MethodValue(Method.EBITDA, true, floor(i.ebitda.times(a.ebitdaMultiple) - i.netDebt), i.shares, "Operating earnings times a multiple, less net debt.")

    fun revenue(i: ValuationInput, a: ValuationAssumptions): MethodValue =
        if (i.revenue.raw <= 0L) MethodValue(Method.REVENUE, false, Money.ZERO, i.shares, "Not applicable: no sales yet.")
        else MethodValue(Method.REVENUE, true, floor(i.revenue.times(a.revenueMultiple) - i.netDebt), i.shares, "Sales times a multiple, less net debt.")

    fun dcf(i: ValuationInput, a: ValuationAssumptions): MethodValue =
        if (i.freeCashFlow.raw <= 0L) MethodValue(Method.DCF, false, Money.ZERO, i.shares, "Not applicable: the company does not generate spare cash.")
        else MethodValue(Method.DCF, true, floor(dcfEquity(i, a)), i.shares, "Cash flow projected ${a.years} years and discounted back.")

    fun dcfEquity(i: ValuationInput, a: ValuationAssumptions): Money =
        Valuation.discountedCashFlow(i.freeCashFlow, a.growth, a.discount, a.terminalGrowth, a.years, i.netDebt)

    fun liquidation(i: ValuationInput, a: ValuationAssumptions): MethodValue {
        fun part(m: Money, ppm: Long) = Money(mulDiv(Money.max(m, Money.ZERO).raw, ppm, PPM))
        val proceeds = Money.max(i.cash, Money.ZERO) + part(i.receivables, a.receivablesRecoveryPpm) + part(i.inventory, a.inventoryRecoveryPpm) +
            part(i.fixedAssets, a.fixedAssetRecoveryPpm) + part(i.investments, a.investmentRecoveryPpm) + part(i.loansReceivable, a.loanRecoveryPpm)
        return MethodValue(Method.LIQUIDATION, true, floor(proceeds - i.liabilities), i.shares, "What is left for owners if everything is sold off and debts are paid.")
    }
}
