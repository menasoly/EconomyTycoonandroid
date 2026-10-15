package app.many.economytycoon.valuation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/**
 * What the valuation methods need to know about a company. Flows (revenue, earnings, cash flow) are annualised
 * so every method reads them on the same footing.
 */
data class ValuationInput(
    val revenue: Money,
    val ebitda: Money,
    val netIncome: Money,
    val freeCashFlow: Money,
    val cash: Money,
    val debt: Money,
    val receivables: Money,
    val inventory: Money,
    val fixedAssets: Money,
    val intangibles: Money,
    val investments: Money,
    val loansReceivable: Money,
    val liabilities: Money,
    val bookEquity: Money,
    val shares: Long,
    /** Latest traded price per share, when the company is listed. */
    val marketPrice: Money? = null,
    /** How many days of trading the flows are drawn from; short histories deserve less trust. */
    val historyDays: Long = 30L,
) {
    val netDebt: Money get() = debt - cash
    val marketCap: Money? get() = marketPrice?.let { it * shares }
}

/** The judgement calls behind a valuation, all adjustable. */
data class ValuationAssumptions(
    val earningsMultiple: Rate = Rate(12_000_000),
    val ebitdaMultiple: Rate = Rate(8_000_000),
    val revenueMultiple: Rate = Rate(1_000_000),
    val discount: Rate = Rate(120_000),
    val growth: Rate = Rate(30_000),
    val terminalGrowth: Rate = Rate(20_000),
    val years: Int = 5,
    /** Share of book value a quick sale would fetch, by kind of asset, in ppm. */
    val receivablesRecoveryPpm: Long = 800_000L,
    val inventoryRecoveryPpm: Long = 600_000L,
    val fixedAssetRecoveryPpm: Long = 500_000L,
    val investmentRecoveryPpm: Long = 900_000L,
    val loanRecoveryPpm: Long = 700_000L,
) {
    init {
        require(years in 1..30) { "years out of range" }
        require(discount > terminalGrowth) { "discount rate must exceed terminal growth" }
    }

    companion object {
        val DEFAULT = ValuationAssumptions()

        /** A more cautious set: lower multiples, higher discount, no growth beyond inflation. */
        val CAUTIOUS = ValuationAssumptions(
            earningsMultiple = Rate(9_000_000), ebitdaMultiple = Rate(6_000_000), revenueMultiple = Rate(700_000),
            discount = Rate(150_000), growth = Rate(10_000), terminalGrowth = Rate(10_000),
        )

        /** A more hopeful set: richer multiples and faster growth. */
        val OPTIMISTIC = ValuationAssumptions(
            earningsMultiple = Rate(16_000_000), ebitdaMultiple = Rate(11_000_000), revenueMultiple = Rate(1_500_000),
            discount = Rate(100_000), growth = Rate(60_000), terminalGrowth = Rate(30_000),
        )
    }
}
