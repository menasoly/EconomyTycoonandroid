package app.many.economytycoon.consolidation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement
import app.many.economytycoon.finance.LedgerOwner

object GroupFixture {
    private val owner = LedgerOwner.government(app.many.economytycoon.core.CountryId(1))
    private fun m(n: Long) = Money.units(n)

    fun income(sales: Long = 10_000, cogs: Long = 4_000, wages: Long = 2_000, tax: Long = 500, interest: Long = 100) = IncomeStatement(
        owner = owner, from = Tick(0), to = Tick(30), salesRevenue = m(sales), serviceRevenue = m(0), otherIncome = m(0),
        cogs = m(cogs), wages = m(wages), energy = m(0), rent = m(0), maintenance = m(0), transport = m(0), marketing = m(0),
        research = m(0), waste = m(0), otherExpenses = m(0), depreciation = m(0), interestExpense = m(interest),
        interestIncome = m(0), incomeTax = m(tax),
    )

    /** A balance sheet that balances: assets are cash and fixed assets, funded by debt and equity. */
    fun balance(cash: Long = 5_000, fixed: Long = 20_000, debt: Long = 8_000, capital: Long = 17_000) = BalanceSheet(
        owner = owner, asOf = Tick(30), cash = m(cash), receivables = m(0), inventory = m(0), prepaid = m(0),
        fixedAssetsGross = m(fixed), accumulatedDepreciation = m(0), intangibles = m(0), investments = m(0), loansReceivable = m(0),
        payables = m(0), wagesPayable = m(0), taxPayable = m(0), accruedInterest = m(0), deferredRevenue = m(0),
        shortDebt = m(0), longDebt = m(debt), deposits = m(0), shareCapital = m(capital), retainedEarnings = m(0),
        dividends = m(0), treasuryShares = m(0), earningsToDate = m(0),
    )

    fun member(id: Long, name: String, interestPpm: Long, income: IncomeStatement = income(), balance: BalanceSheet = balance()) =
        MemberStatements(CompanyId(id), name, interestPpm, income, balance)
}
