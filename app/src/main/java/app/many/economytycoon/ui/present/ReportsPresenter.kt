package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.reporting.AnnualReport
import app.many.economytycoon.reporting.AnnualReports
import app.many.economytycoon.reporting.Verdict
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.GameSettings

data class YearCard(
    val index: Int,
    val title: String,
    val verdict: String,
    val tone: Tone,
    val period: String,
    val tiles: List<Fact>,
    val changes: List<String>,
    val income: List<StatementSection>,
    val balance: List<StatementSection>,
    val partial: Boolean,
)

data class ReportsModel(val company: String, val years: List<YearCard>, val note: String)

/** Yearly results, newest first. */
object ReportsPresenter {
    fun verdictTone(v: Verdict): Tone = when (v) {
        Verdict.RECORD -> Tone.GOOD
        Verdict.GROWTH -> Tone.GOOD
        Verdict.STEADY -> Tone.INFO
        Verdict.SLIP -> Tone.WARN
        Verdict.LOSS -> Tone.BAD
        Verdict.QUIET -> Tone.MUTED
    }

    private fun signedPercent(ppm: Long): String = (if (ppm > 0L) "+" else "") + Formatting.percentPpm(ppm)

    fun changes(r: AnnualReport, s: GameSettings): List<String> {
        val out = ArrayList<String>()
        r.revenueGrowthPpm?.let { out += "Revenue ${signedPercent(it)} on the year before" }
        r.netIncomeChange?.let { out += "Net income ${if (it.raw >= 0L) "up" else "down"} ${s.money(app.many.economytycoon.core.Money(kotlin.math.abs(it.raw)))}" }
        if (r.debtChange.raw != 0L) out += "Debt ${if (r.debtChange.raw > 0L) "rose" else "fell"} by ${s.money(app.many.economytycoon.core.Money(kotlin.math.abs(r.debtChange.raw)))}"
        r.returnOnEquityPpm?.let { out += "Return on equity ${Formatting.percentPpm(it)}" }
        if (r.bestNetIncomeSoFar && r.prior != null) out += "The most profitable year so far"
        return out
    }

    fun card(r: AnnualReport, rank: Int?, s: GameSettings): YearCard {
        val v = r.verdict
        val tiles = ArrayList<Fact>()
        tiles += Fact("Revenue", s.money(r.revenue))
        tiles += Fact("Net income", s.money(r.netIncome))
        tiles += Fact("Net worth change", s.money(r.netWorthChange))
        tiles += Fact("Net margin", r.netMarginPpm?.let { Formatting.percentPpm(it) } ?: "n/a")
        if (rank != null) tiles += Fact("Rank by net worth", "#$rank")
        val period = "${Formatting.date(r.from)} to ${Formatting.date(r.to)}"
        return YearCard(
            index = r.index, title = if (r.partial) "${r.calendarYear} so far" else "${r.calendarYear}", verdict = v.label, tone = verdictTone(v),
            period = period, tiles = tiles, changes = changes(r, s),
            income = StatementsPresenter.income(r.income, s), balance = StatementsPresenter.balance(r.closing, s), partial = r.partial,
        )
    }

    fun model(name: String, history: List<AnnualReport>, rank: (Int) -> Int?, s: GameSettings): ReportsModel = ReportsModel(
        company = name,
        years = history.asReversed().map { card(it, rank(it.index), s) },
        note = "Each year runs from day 1 of its January to the end of its December. The year in progress shows what has happened so far.",
    )

    fun build(session: GameSession, company: CompanyId? = session.selectedCompany): ReportsModel? {
        company ?: return null
        val c = session.economy.companies.company(company) ?: return null
        val books: AnnualReports = session.annualReports()
        return model(c.name, books.history(company), { books.rank(company, it) }, session.settings)
    }
}
