package app.many.economytycoon.ui.present

import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.CashFlowStatement
import app.many.economytycoon.finance.IncomeStatement
import app.many.economytycoon.finance.LoanStatus
import app.many.economytycoon.finance.Ratios
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.visualization.DataPoint
import app.many.economytycoon.visualization.LineSeries

/** One line of a statement. [level] 0 is a total, 1 an ordinary line, which the screen indents. */
data class StatementLine(val label: String, val value: String, val raw: Long, val level: Int = 1, val tone: Tone = Tone.NEUTRAL)

data class StatementSection(val title: String, val lines: List<StatementLine>)

data class LoanItem(
    val id: LoanId,
    val title: String,
    val detail: String,
    val outstanding: String,
    val status: String,
    val tone: Tone,
    val repaid: Double,
    val active: Boolean,
)

data class FinanceModel(
    val tiles: List<Tile>,
    val standing: String,
    val standingTone: Tone,
    val netWorthSeries: LineSeries,
    val cashSeries: LineSeries,
    val costBreakdown: List<Pair<String, Double>>,
    val ratios: List<Fact>,
    val loans: List<LoanItem>,
    val creditLine: String?,
)

object FinancePresenter {
    fun build(session: GameSession): FinanceModel? {
        val company = session.selectedCompany ?: return null
        val pack = session.reports.company.finance(company) ?: return null
        val settings = session.settings
        val money = settings::money
        val stats = session.stats.of(company)
        val netWorth = LineSeries("Net worth", stats.map { DataPoint(it.tick.toDouble(), it.netWorth.raw / 10_000.0) }, 0)
        val cash = LineSeries("Cash", stats.map { DataPoint(it.tick.toDouble(), it.cash.raw / 10_000.0) }, 1)
        val m = pack.month
        val tiles = listOf(
            Tile("Revenue", money(m.revenue), note = "last 30 days"),
            Tile("Operating cost", money(m.cogs + m.operatingExpenses), note = "last 30 days"),
            Tile("Net income", money(m.netIncome), Tones.bySign(m.netIncome.raw), "after tax"),
            Tile("Cash flow", money(pack.cashFlow.netChange), Tones.bySign(pack.cashFlow.netChange.raw), "last 30 days"),
        )
        val credit = if (pack.creditLimit != null && pack.borrowRate != null) {
            "You can borrow up to ${money(pack.creditLimit)} at ${Formatting.percent(pack.borrowRate)} a year."
        } else null
        return FinanceModel(
            tiles = tiles, standing = OverviewPresenter.standing(pack.rating, pack.distress), standingTone = OverviewPresenter.distressTone(pack.distress),
            netWorthSeries = netWorth, cashSeries = cash, costBreakdown = costBreakdown(m), ratios = ratios(pack.ratios), loans = loans(session, pack.loans),
            creditLine = credit,
        )
    }

    fun costBreakdown(s: IncomeStatement): List<Pair<String, Double>> = listOf(
        "Materials" to s.cogs, "Wages" to s.wages, "Energy" to s.energy, "Rent" to s.rent, "Maintenance" to s.maintenance, "Transport" to s.transport,
        "Marketing" to s.marketing, "Research" to s.research, "Waste" to s.waste, "Interest" to s.interestExpense, "Tax" to s.incomeTax, "Other" to s.otherExpenses,
    ).map { (name, money) -> name to money.raw.toDouble() / Money.SCALE }.filter { it.second > 0.0 }

    fun ratios(r: Ratios): List<Fact> {
        fun pct(rate: app.many.economytycoon.core.Rate) = if (rate == Ratios.UNBOUNDED) "∞" else Formatting.percent(rate)
        fun times(rate: app.many.economytycoon.core.Rate) = if (rate == Ratios.UNBOUNDED) "∞" else Formatting.ratioTimes(rate.ppm)
        return listOf(
            Fact("Current ratio", times(r.currentRatio), if (r.currentRatio.ppm < 1_000_000L) Tone.WARN else Tone.NEUTRAL, "Short-term assets against short-term debts"),
            Fact("Cash ratio", times(r.cashRatio)),
            Fact("Debt to equity", times(r.debtToEquity), if (r.debtToEquity.ppm > 3_000_000L) Tone.WARN else Tone.NEUTRAL),
            Fact("Interest cover", times(r.interestCoverage), if (r.interestCoverage.ppm < 1_500_000L) Tone.WARN else Tone.NEUTRAL, "Operating profit against interest"),
            Fact("Gross margin", pct(r.grossMargin)),
            Fact("Net margin", pct(r.netMargin), Tones.bySign(r.netMargin.ppm)),
            Fact("Return on equity", pct(r.returnOnEquity), Tones.bySign(r.returnOnEquity.ppm)),
            Fact("Return on assets", pct(r.returnOnAssets), Tones.bySign(r.returnOnAssets.ppm)),
        )
    }

    fun loans(session: GameSession, rows: List<app.many.economytycoon.session.LoanRow>): List<LoanItem> {
        val money = session.settings::money
        return rows.map { l ->
            val repaid = if (l.principal.raw <= 0) 1.0 else (1.0 - l.outstanding.raw.toDouble() / l.principal.raw).coerceIn(0.0, 1.0)
            val tone = when {
                l.status == LoanStatus.DEFAULTED -> Tone.BAD
                l.missedPayments > 0 -> Tone.WARN
                else -> Tone.GOOD
            }
            val status = when {
                l.status == LoanStatus.DEFAULTED -> "In default"
                l.missedPayments > 0 -> "${l.missedPayments} missed"
                l.status == LoanStatus.PAID_OFF -> "Repaid"
                else -> "Current"
            }
            LoanItem(
                id = l.id, title = "Loan ${l.id.value} · ${Formatting.percent(l.annualRate)}", detail = "Next payment ${money(l.nextPayment)} · due ${Formatting.date(l.maturity)}",
                outstanding = money(l.outstanding), status = status, tone = tone, repaid = repaid, active = l.status == LoanStatus.ACTIVE,
            )
        }
    }
}

/** The three statements, ready to draw. */
object StatementsPresenter {
    private fun line(label: String, m: Money, s: GameSettings, level: Int = 1, signed: Boolean = false) =
        StatementLine(label, if (signed) s.money(m) else s.money(m), m.raw, level, if (signed) Tones.bySign(m.raw) else Tone.NEUTRAL)

    fun income(i: IncomeStatement, s: GameSettings): List<StatementSection> = listOf(
        StatementSection("Revenue", listOf(line("Sales", i.salesRevenue, s), line("Services", i.serviceRevenue, s), line("Total revenue", i.revenue, s, 0))),
        StatementSection("Costs", listOf(
            line("Cost of goods", i.cogs, s), line("Gross profit", i.grossProfit, s, 0, true),
            line("Wages", i.wages, s), line("Energy", i.energy, s), line("Rent", i.rent, s), line("Maintenance", i.maintenance, s), line("Transport", i.transport, s),
            line("Marketing", i.marketing, s), line("Research", i.research, s), line("Waste disposal", i.waste, s), line("Other", i.otherExpenses, s),
            line("Operating expenses", i.operatingExpenses, s, 0),
        )),
        StatementSection("Profit", listOf(
            line("EBITDA", i.ebitda, s, 0, true), line("Depreciation", i.depreciation, s), line("Operating profit", i.ebit, s, 0, true),
            line("Interest paid", i.interestExpense, s), line("Interest earned", i.interestIncome, s), line("Profit before tax", i.profitBeforeTax, s, 0, true),
            line("Income tax", i.incomeTax, s), line("Net income", i.netIncome, s, 0, true),
        )),
    )

    fun balance(b: BalanceSheet, s: GameSettings): List<StatementSection> = listOf(
        StatementSection("Assets", listOf(
            line("Cash", b.cash, s), line("Receivables", b.receivables, s), line("Inventory", b.inventory, s), line("Prepaid", b.prepaid, s),
            line("Current assets", b.currentAssets, s, 0), line("Fixed assets, net", b.fixedAssetsNet, s), line("Intangibles", b.intangibles, s),
            line("Investments", b.investments, s), line("Loans made", b.loansReceivable, s), line("Total assets", b.totalAssets, s, 0),
        )),
        StatementSection("Liabilities", listOf(
            line("Payables", b.payables, s), line("Wages owed", b.wagesPayable, s), line("Tax owed", b.taxPayable, s), line("Accrued interest", b.accruedInterest, s),
            line("Deferred revenue", b.deferredRevenue, s), line("Short-term debt", b.shortDebt, s), line("Deposits", b.deposits, s),
            line("Current liabilities", b.currentLiabilities, s, 0), line("Long-term debt", b.longDebt, s), line("Total liabilities", b.totalLiabilities, s, 0),
        )),
        StatementSection("Equity", listOf(
            line("Share capital", b.shareCapital, s), line("Retained earnings", b.retainedEarnings, s), line("Earnings to date", b.earningsToDate, s),
            line("Dividends", b.dividends, s), line("Treasury shares", b.treasuryShares, s), line("Total equity", b.totalEquity, s, 0, true),
        )),
    )

    fun cashFlow(c: CashFlowStatement, s: GameSettings): List<StatementSection> = listOf(
        StatementSection("Operating", listOf(
            line("Net income", c.netIncome, s), line("Non-cash charges", c.nonCashCharges, s), line("Working capital", c.workingCapitalChange, s), line("Operating cash flow", c.operating, s, 0, true),
        )),
        StatementSection("Investing and financing", listOf(line("Investing", c.investing, s, 1, true), line("Financing", c.financing, s, 1, true), line("Net change", c.netChange, s, 0, true))),
        StatementSection("Cash", listOf(line("Opening", c.openingCash, s), line("Closing", c.closingCash, s, 0))),
    )
}
