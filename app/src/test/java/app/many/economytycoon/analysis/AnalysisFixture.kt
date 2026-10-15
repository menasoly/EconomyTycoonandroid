package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.CashFlowStatement
import app.many.economytycoon.finance.IncomeStatement
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind

/** Hand-made statements so ratio tests can state their inputs in whole units. */
object AnalysisFixture {
    val owner = LedgerOwner(OwnerKind.COMPANY, 1L)
    private fun m(n: Long) = Money.units(n)

    fun income(
        sales: Long = 100_000, services: Long = 0, other: Long = 0, cogs: Long = 40_000, wages: Long = 20_000, energy: Long = 2_000,
        rent: Long = 3_000, maintenance: Long = 1_000, transport: Long = 3_000, marketing: Long = 2_000, research: Long = 0,
        waste: Long = 0, otherExpenses: Long = 0, depreciation: Long = 4_000, interest: Long = 2_000, interestIncome: Long = 0, tax: Long = 4_000,
    ) = IncomeStatement(
        owner, Tick(0), Tick(30), m(sales), m(services), m(other), m(cogs), m(wages), m(energy), m(rent), m(maintenance), m(transport),
        m(marketing), m(research), m(waste), m(otherExpenses), m(depreciation), m(interest), m(interestIncome), m(tax),
    )

    fun balance(
        cash: Long = 50_000, receivables: Long = 20_000, inventory: Long = 30_000, prepaid: Long = 0, fixedGross: Long = 300_000,
        accumulated: Long = 40_000, intangibles: Long = 0, investments: Long = 0, loansReceivable: Long = 0,
        payables: Long = 15_000, wagesPayable: Long = 5_000, taxPayable: Long = 2_000, shortDebt: Long = 20_000, longDebt: Long = 100_000,
        shareCapital: Long = 200_000,
    ): BalanceSheet {
        val assets = cash + receivables + inventory + prepaid + (fixedGross - accumulated) + intangibles + investments + loansReceivable
        val liabilities = payables + wagesPayable + taxPayable + shortDebt + longDebt
        val retained = assets - liabilities - shareCapital
        return BalanceSheet(
            owner, Tick(30), m(cash), m(receivables), m(inventory), m(prepaid), m(fixedGross), m(accumulated), m(intangibles), m(investments),
            m(loansReceivable), m(payables), m(wagesPayable), m(taxPayable), Money.ZERO, Money.ZERO, m(shortDebt), m(longDebt), Money.ZERO,
            m(shareCapital), m(retained), Money.ZERO, Money.ZERO, Money.ZERO,
        )
    }

    fun cashFlow(opening: Long = 40_000, closing: Long = 50_000) = CashFlowStatement(
        owner, Tick(0), Tick(30), m(closing - opening), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, m(opening), m(closing),
    )

    fun input(
        income: IncomeStatement = income(), balance: BalanceSheet = balance(), cashFlow: CashFlowStatement = cashFlow(),
        history: List<Money> = emptyList(),
    ) = AnalysisInput(income, balance, cashFlow, periodDays = 30L, netWorthHistory = history, historyStepDays = 5L, asOfDay = 100L)
}
