package app.many.economytycoon.session

import app.many.economytycoon.activity.ActivityEntry
import app.many.economytycoon.export.Csv
import app.many.economytycoon.export.ExportFile
import app.many.economytycoon.export.ExportKind
import app.many.economytycoon.export.Plain
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.IncomeStatement
import app.many.economytycoon.intel.GoodSector
import app.many.economytycoon.reporting.AnnualReport

/** Builds spreadsheet files from the figures the screens already show. */
object ExportTables {
    class Table(val header: List<String>, val rows: List<List<String>>)

    private fun line(label: String, m: app.many.economytycoon.core.Money) = listOf(label, Plain.money(m))

    fun income(i: IncomeStatement): Table = Table(
        listOf("Line", "Amount"),
        listOf(
            line("Sales", i.salesRevenue), line("Services", i.serviceRevenue), line("Total revenue", i.revenue),
            line("Cost of goods", i.cogs), line("Gross profit", i.grossProfit),
            line("Wages", i.wages), line("Energy", i.energy), line("Rent", i.rent), line("Maintenance", i.maintenance),
            line("Transport", i.transport), line("Marketing", i.marketing), line("Research", i.research),
            line("Waste disposal", i.waste), line("Other expenses", i.otherExpenses), line("Operating expenses", i.operatingExpenses),
            line("EBITDA", i.ebitda), line("Depreciation", i.depreciation), line("Operating profit", i.ebit),
            line("Interest paid", i.interestExpense), line("Interest earned", i.interestIncome),
            line("Profit before tax", i.profitBeforeTax), line("Income tax", i.incomeTax), line("Net income", i.netIncome),
        ),
    )

    fun balance(b: BalanceSheet): Table = Table(
        listOf("Section", "Line", "Amount"),
        listOf(
            listOf("Assets") + line("Cash", b.cash), listOf("Assets") + line("Receivables", b.receivables),
            listOf("Assets") + line("Inventory", b.inventory), listOf("Assets") + line("Prepaid", b.prepaid),
            listOf("Assets") + line("Fixed assets, net", b.fixedAssetsNet), listOf("Assets") + line("Intangibles", b.intangibles),
            listOf("Assets") + line("Investments", b.investments), listOf("Assets") + line("Loans made", b.loansReceivable),
            listOf("Assets") + line("Total assets", b.totalAssets),
            listOf("Liabilities") + line("Payables", b.payables), listOf("Liabilities") + line("Wages owed", b.wagesPayable),
            listOf("Liabilities") + line("Tax owed", b.taxPayable), listOf("Liabilities") + line("Accrued interest", b.accruedInterest),
            listOf("Liabilities") + line("Deferred revenue", b.deferredRevenue), listOf("Liabilities") + line("Short-term debt", b.shortDebt),
            listOf("Liabilities") + line("Deposits", b.deposits), listOf("Liabilities") + line("Long-term debt", b.longDebt),
            listOf("Liabilities") + line("Total liabilities", b.totalLiabilities),
            listOf("Equity") + line("Share capital", b.shareCapital), listOf("Equity") + line("Retained earnings", b.retainedEarnings),
            listOf("Equity") + line("Earnings to date", b.earningsToDate), listOf("Equity") + line("Dividends", b.dividends),
            listOf("Equity") + line("Treasury shares", b.treasuryShares), listOf("Equity") + line("Total equity", b.totalEquity),
        ),
    )

    fun yearly(history: List<AnnualReport>): Table = Table(
        listOf("Year", "Part of year", "Revenue", "Net income", "Net worth change", "Net margin %", "Revenue growth %", "Verdict"),
        history.map { r ->
            listOf(
                r.calendarYear.toString(), if (r.partial) "partial" else "full", Plain.money(r.revenue), Plain.money(r.netIncome),
                Plain.money(r.netWorthChange), r.netMarginPpm?.let { Plain.percent(it) } ?: "", r.revenueGrowthPpm?.let { Plain.percent(it) } ?: "", r.verdict.label,
            )
        },
    )

    fun history(points: List<StatPoint>): Table = Table(
        listOf("Day", "Cash", "Net worth", "Revenue", "Net income", "Employees"),
        points.map { listOf(it.tick.toString(), Plain.money(it.cash), Plain.money(it.netWorth), Plain.money(it.revenue), Plain.money(it.netIncome), it.employees.toString()) },
    )

    fun activity(entries: List<ActivityEntry>): Table = Table(
        listOf("Day", "Area", "By", "What", "Moves money"),
        entries.map { listOf(it.tick.toString(), it.area.label, it.origin.label, it.text, if (it.movesMoney) "yes" else "no") },
    )

    fun sectors(list: List<GoodSector>, nameOf: (app.many.economytycoon.core.GoodId) -> String): Table = Table(
        listOf("Good", "Producers", "Biggest producer", "Biggest share %", "Capacity per day", "Made per day", "Used by industry per day", "Concentration", "Balance", "Price vs usual %"),
        list.map { s ->
            listOf(
                nameOf(s.good), s.producers.size.toString(), s.leader?.name ?: "", s.leader?.let { Plain.percent(it.sharePpm) } ?: "",
                Plain.quantity(app.many.economytycoon.core.Quantity(s.capacityPerDay)), Plain.quantity(app.many.economytycoon.core.Quantity(s.actualPerDay)),
                Plain.quantity(app.many.economytycoon.core.Quantity(s.industryUsePerDay)), s.concentration.label, s.balance.label, Plain.percent(s.priceRatioPpm - 1_000_000L),
            )
        },
    )

    fun prices(board: List<GoodQuote>): Table = Table(
        listOf("Good", "Category", "Price", "Reference price", "Change %", "From reference %", "Volume", "Unmet demand", "Unsold supply"),
        board.map { q ->
            listOf(
                q.name, q.category.name.lowercase().replace('_', ' '), Plain.money(q.price), Plain.money(q.reference), Plain.percent(q.changePpm),
                Plain.percent(q.deviationPpm), Plain.quantity(q.volume), Plain.quantity(q.unmet), Plain.quantity(q.unsold),
            )
        },
    )

    fun file(kind: ExportKind, company: String, day: Long, table: Table): ExportFile =
        ExportFile(kind, ExportFile.nameFor(company, kind, day), Csv.table(table.header, table.rows), table.rows.size)
}

/** Builds each kind of export for the selected company of a running game. */
class Exports(private val session: GameSession) {
    fun available(): List<ExportKind> = if (session.selectedCompany == null) emptyList() else ExportKind.values().toList()

    fun build(kind: ExportKind): ExportFile? {
        val id = session.selectedCompany ?: return null
        val e = session.economy
        val name = e.companies.company(id)?.name ?: return null
        val day = session.tick.value
        val pack by lazy { session.reports.company.finance(id) }
        val table: ExportTables.Table = when (kind) {
            ExportKind.INCOME_MONTH -> ExportTables.income((pack ?: return null).month)
            ExportKind.INCOME_YEAR -> ExportTables.income((pack ?: return null).year)
            ExportKind.BALANCE -> ExportTables.balance((pack ?: return null).balance)
            ExportKind.YEARLY -> ExportTables.yearly(session.annualReports().history(id))
            ExportKind.HISTORY -> ExportTables.history(session.stats.of(id))
            ExportKind.ACTIVITY -> ExportTables.activity(session.activity(MAX_ACTIVITY))
            ExportKind.SECTORS -> ExportTables.sectors(session.sectors().sectors()) { g -> e.content.goodOrNull(g)?.name ?: g.key }
            ExportKind.PRICES -> ExportTables.prices(session.reports.market.board())
        }
        return ExportTables.file(kind, name, day, table)
    }

    companion object { const val MAX_ACTIVITY = 1_000 }
}
