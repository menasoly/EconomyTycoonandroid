package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick

data class IncomeStatement(
    val owner: LedgerOwner,
    val from: Tick,
    val to: Tick,
    val salesRevenue: Money,
    val serviceRevenue: Money,
    val otherIncome: Money,
    val cogs: Money,
    val wages: Money,
    val energy: Money,
    val rent: Money,
    val maintenance: Money,
    val transport: Money,
    val marketing: Money,
    val research: Money,
    val waste: Money,
    val otherExpenses: Money,
    val depreciation: Money,
    val interestExpense: Money,
    val interestIncome: Money,
    val incomeTax: Money,
) {
    val revenue: Money get() = salesRevenue + serviceRevenue
    val grossProfit: Money get() = revenue - cogs
    val operatingExpenses: Money get() = wages + energy + rent + maintenance + transport + marketing + research + waste + otherExpenses
    val ebitda: Money get() = grossProfit + otherIncome - operatingExpenses
    val ebit: Money get() = ebitda - depreciation
    val profitBeforeTax: Money get() = ebit - interestExpense + interestIncome
    val netIncome: Money get() = profitBeforeTax - incomeTax
}

data class BalanceSheet(
    val owner: LedgerOwner,
    val asOf: Tick,
    val cash: Money,
    val receivables: Money,
    val inventory: Money,
    val prepaid: Money,
    val fixedAssetsGross: Money,
    val accumulatedDepreciation: Money,
    val intangibles: Money,
    val investments: Money,
    val loansReceivable: Money,
    val payables: Money,
    val wagesPayable: Money,
    val taxPayable: Money,
    val accruedInterest: Money,
    val deferredRevenue: Money,
    val shortDebt: Money,
    val longDebt: Money,
    val deposits: Money,
    val shareCapital: Money,
    val retainedEarnings: Money,
    val dividends: Money,
    val treasuryShares: Money,
    /** Lifetime profit not yet closed into retained earnings. */
    val earningsToDate: Money,
) {
    val fixedAssetsNet: Money get() = fixedAssetsGross - accumulatedDepreciation
    val currentAssets: Money get() = cash + receivables + inventory + prepaid
    val totalAssets: Money get() = currentAssets + fixedAssetsNet + intangibles + investments + loansReceivable
    val currentLiabilities: Money get() = payables + wagesPayable + taxPayable + accruedInterest + deferredRevenue + shortDebt + deposits
    val totalLiabilities: Money get() = currentLiabilities + longDebt
    val totalDebt: Money get() = shortDebt + longDebt
    val totalEquity: Money get() = shareCapital + retainedEarnings + earningsToDate - dividends - treasuryShares
    val balances: Boolean get() = totalAssets == totalLiabilities + totalEquity
    val netWorth: Money get() = totalAssets - totalLiabilities
}

data class CashFlowStatement(
    val owner: LedgerOwner,
    val from: Tick,
    val to: Tick,
    val netIncome: Money,
    val nonCashCharges: Money,
    val workingCapitalChange: Money,
    val investing: Money,
    val financing: Money,
    val openingCash: Money,
    val closingCash: Money,
) {
    val operating: Money get() = netIncome + nonCashCharges + workingCapitalChange
    val netChange: Money get() = operating + investing + financing
    val reconciles: Boolean get() = openingCash + netChange == closingCash
    val freeCashFlow: Money get() = operating + investing
}

/** Key financial ratios used by credit rating, AI planning and the UI. */
data class Ratios(
    val currentRatio: Rate,
    val cashRatio: Rate,
    val debtToEquity: Rate,
    val interestCoverage: Rate,
    val grossMargin: Rate,
    val netMargin: Rate,
    val returnOnEquity: Rate,
    val returnOnAssets: Rate,
    val debtToEbitda: Rate,
    val equityRatio: Rate,
) {
    companion object {
        /** Sentinel for ratios with a zero denominator and a positive numerator. */
        val UNBOUNDED = Rate(1_000_000_000L)
        val NONE = Rate.ZERO

        fun of(i: IncomeStatement, b: BalanceSheet, periodsPerYear: Long = 1L): Ratios {
            fun div(n: Money, d: Money): Rate = when {
                d.raw == 0L -> if (n.raw > 0) UNBOUNDED else if (n.raw < 0) -UNBOUNDED else NONE
                else -> Rate.ratio(n.raw, d.raw).coerceIn(-UNBOUNDED, UNBOUNDED)
            }
            val annualNet = i.netIncome * periodsPerYear
            val annualEbitda = i.ebitda * periodsPerYear
            return Ratios(
                currentRatio = div(b.currentAssets, b.currentLiabilities),
                cashRatio = div(b.cash, b.currentLiabilities),
                debtToEquity = div(b.totalDebt, b.totalEquity),
                interestCoverage = div(i.ebit, i.interestExpense),
                grossMargin = div(i.grossProfit, i.revenue),
                netMargin = div(i.netIncome, i.revenue),
                returnOnEquity = div(annualNet, b.totalEquity),
                returnOnAssets = div(annualNet, b.totalAssets),
                debtToEbitda = div(b.totalDebt, annualEbitda),
                equityRatio = div(b.totalEquity, b.totalAssets),
            )
        }
    }
}

/** Projections over the ledger. Never authoritative: they can always be rebuilt. */
class StatementBuilder(private val ledger: Ledger) {

    private fun activity(owner: LedgerOwner, chart: Chart, from: Tick, to: Tick): Money =
        ledger.activity(AccountRef(owner, chart), from, to)

    private fun bal(owner: LedgerOwner, chart: Chart, at: Tick): Money = ledger.balanceAt(AccountRef(owner, chart), at)

    private fun expense(o: LedgerOwner, c: Chart, from: Tick, to: Tick) = activity(o, c, from, to)
    private fun revenue(o: LedgerOwner, c: Chart, from: Tick, to: Tick) = -activity(o, c, from, to)

    /** Income statement for ticks in (from, to]. */
    fun income(owner: LedgerOwner, from: Tick, to: Tick): IncomeStatement = IncomeStatement(
        owner, from, to,
        salesRevenue = revenue(owner, Chart.REVENUE_SALES, from, to),
        serviceRevenue = revenue(owner, Chart.REVENUE_SERVICES, from, to),
        otherIncome = revenue(owner, Chart.REVENUE_OTHER, from, to),
        cogs = expense(owner, Chart.COGS, from, to),
        wages = expense(owner, Chart.WAGES_EXPENSE, from, to),
        energy = expense(owner, Chart.ENERGY_EXPENSE, from, to),
        rent = expense(owner, Chart.RENT_EXPENSE, from, to),
        maintenance = expense(owner, Chart.MAINTENANCE_EXPENSE, from, to),
        transport = expense(owner, Chart.TRANSPORT_EXPENSE, from, to),
        marketing = expense(owner, Chart.MARKETING_EXPENSE, from, to),
        research = expense(owner, Chart.RESEARCH_EXPENSE, from, to),
        waste = expense(owner, Chart.WASTE_EXPENSE, from, to),
        otherExpenses = expense(owner, Chart.OTHER_EXPENSE, from, to),
        depreciation = expense(owner, Chart.DEPRECIATION_EXPENSE, from, to),
        interestExpense = expense(owner, Chart.INTEREST_EXPENSE, from, to),
        interestIncome = revenue(owner, Chart.INTEREST_INCOME, from, to),
        incomeTax = expense(owner, Chart.TAX_EXPENSE, from, to),
    )

    fun balance(owner: LedgerOwner, asOf: Tick): BalanceSheet {
        fun asset(c: Chart) = bal(owner, c, asOf)
        fun liability(c: Chart) = -bal(owner, c, asOf)
        var pl = 0L
        for (c in Chart.values()) {
            if (c.cls == AccountClass.REVENUE || c.cls == AccountClass.EXPENSE) pl += bal(owner, c, asOf).raw
        }
        return BalanceSheet(
            owner, asOf,
            cash = asset(Chart.CASH),
            receivables = asset(Chart.RECEIVABLES),
            inventory = asset(Chart.INVENTORY),
            prepaid = asset(Chart.PREPAID),
            fixedAssetsGross = asset(Chart.FIXED_ASSETS),
            accumulatedDepreciation = liability(Chart.ACCUMULATED_DEPRECIATION),
            intangibles = asset(Chart.INTANGIBLES),
            investments = asset(Chart.INVESTMENTS),
            loansReceivable = asset(Chart.LOANS_RECEIVABLE),
            payables = liability(Chart.PAYABLES),
            wagesPayable = liability(Chart.WAGES_PAYABLE),
            taxPayable = liability(Chart.TAX_PAYABLE),
            accruedInterest = liability(Chart.ACCRUED_INTEREST),
            deferredRevenue = liability(Chart.DEFERRED_REVENUE),
            shortDebt = liability(Chart.DEBT_SHORT),
            longDebt = liability(Chart.DEBT_LONG),
            deposits = liability(Chart.DEPOSITS),
            shareCapital = liability(Chart.SHARE_CAPITAL),
            retainedEarnings = liability(Chart.RETAINED_EARNINGS),
            dividends = asset(Chart.DIVIDENDS),
            treasuryShares = asset(Chart.TREASURY_SHARES),
            earningsToDate = Money(-pl),
        )
    }

    fun cashFlow(owner: LedgerOwner, from: Tick, to: Tick): CashFlowStatement {
        val inc = income(owner, from, to)
        val open = balance(owner, from)
        val close = balance(owner, to)
        val nonCash = close.accumulatedDepreciation - open.accumulatedDepreciation
        val wc = -(close.receivables - open.receivables) - (close.inventory - open.inventory) - (close.prepaid - open.prepaid) +
            (close.payables - open.payables) + (close.wagesPayable - open.wagesPayable) + (close.taxPayable - open.taxPayable) +
            (close.accruedInterest - open.accruedInterest) + (close.deferredRevenue - open.deferredRevenue) + (close.deposits - open.deposits)
        val investing = -(close.fixedAssetsGross - open.fixedAssetsGross) - (close.intangibles - open.intangibles) -
            (close.investments - open.investments) - (close.loansReceivable - open.loansReceivable)
        val financing = (close.totalDebt - open.totalDebt) + (close.shareCapital - open.shareCapital) +
            (close.retainedEarnings - open.retainedEarnings) - (close.dividends - open.dividends) - (close.treasuryShares - open.treasuryShares)
        return CashFlowStatement(owner, from, to, inc.netIncome, nonCash, wc, investing, financing, open.cash, close.cash)
    }

    fun ratios(owner: LedgerOwner, from: Tick, to: Tick, periodsPerYear: Long = 1L): Ratios =
        Ratios.of(income(owner, from, to), balance(owner, to), periodsPerYear)
}
