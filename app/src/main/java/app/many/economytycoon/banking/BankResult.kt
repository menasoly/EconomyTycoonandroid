package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** One month's income statement of a bank. Expenses are stored as positive amounts. */
data class MonthlyResult(
    /** The tick the month closed on. */
    val tick: Long,
    val loanInterest: Money,
    val securityIncome: Money,
    val interbankIncome: Money,
    val depositInterest: Money,
    val wholesaleInterest: Money,
    val centralBankInterest: Money,
    val interbankCost: Money,
    val subordinatedInterest: Money,
    val fees: Money,
    /** Gains and losses on securities sold, signed. */
    val tradingResult: Money,
    val operatingCost: Money,
    /** Charge for expected credit losses, signed: a release is negative. */
    val provisionCharge: Money,
    val tax: Money,
    val averageEarningAssets: Money,
    val averageLoans: Money,
    val averageEquity: Money,
    val averageDeposits: Money,
) {
    val interestIncome: Money get() = loanInterest + securityIncome + interbankIncome
    val interestExpense: Money get() = depositInterest + wholesaleInterest + centralBankInterest + interbankCost + subordinatedInterest
    val netInterestIncome: Money get() = interestIncome - interestExpense

    /** Total income before costs. */
    val grossIncome: Money get() = netInterestIncome + fees + tradingResult
    val preProvisionProfit: Money get() = grossIncome - operatingCost
    val preTaxProfit: Money get() = preProvisionProfit - provisionCharge
    val netIncome: Money get() = preTaxProfit - tax

    /** Net interest income over average earning assets, annualised. */
    val netInterestMargin: Rate get() = annualised(netInterestIncome, averageEarningAssets)

    /** Net income over average equity, annualised. */
    val returnOnEquity: Rate get() = annualised(netIncome, averageEquity)

    val returnOnAssets: Rate get() = annualised(netIncome, averageEarningAssets)

    /** Operating cost over gross income. Zero when there is no income. */
    val costToIncome: Rate get() = if (grossIncome.raw <= 0L) Rate.ZERO else Rate.ratio(operatingCost.raw, grossIncome.raw)

    /** Cost of credit risk over average loans, annualised. */
    val costOfRisk: Rate get() = annualised(provisionCharge, averageLoans)

    private fun annualised(amount: Money, base: Money): Rate =
        if (base.raw <= 0L) Rate.ZERO else Rate(app.many.economytycoon.core.mulDiv(amount.raw, 12L * Rate.ONE_PPM, base.raw))

    companion object {
        fun empty(tick: Long) = MonthlyResult(
            tick, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
            Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
        )
    }
}
