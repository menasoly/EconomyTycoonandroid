package app.many.economytycoon.ui.present

import app.many.economytycoon.consolidation.GroupStatements
import app.many.economytycoon.consolidation.MemberStatements
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.GameSettings

data class GroupMemberRow(
    val company: CompanyId,
    val name: String,
    val interest: String,
    val revenue: String,
    val netIncome: String,
    val netTone: Tone,
    val assets: String,
    val shareOfGroup: String,
    val isRoot: Boolean,
)

data class GroupModel(
    val title: String,
    val tiles: List<Fact>,
    val members: List<GroupMemberRow>,
    val income: List<StatementSection>,
    val balance: List<StatementSection>,
    val notes: List<String>,
)

/** A parent company and the businesses it owns, shown as one. */
object GroupPresenter {
    const val METHOD = "Each business counts in proportion to the share the group owns of it."
    const val INTRAGROUP = "Sales between members are not netted out, so they appear on both sides."

    fun interest(ppm: Long): String = if (ppm >= 1_000_000L) "100%" else Formatting.percentPpm(ppm)

    fun row(g: GroupStatements, m: MemberStatements, s: GameSettings): GroupMemberRow = GroupMemberRow(
        company = m.company, name = m.name, interest = interest(m.interestPpm),
        revenue = s.money(m.shareOfRevenue), netIncome = s.money(m.shareOfNetIncome), netTone = Tones.bySign(m.shareOfNetIncome.raw),
        assets = s.money(m.shareOfAssets), shareOfGroup = Formatting.percentPpm(g.revenueShare(m.company)), isRoot = m.company == g.root,
    )

    fun notes(g: GroupStatements): List<String> {
        val out = ArrayList<String>()
        out += METHOD
        out += INTRAGROUP
        g.dominantMember()?.let { out += "${it.name} brings in most of the group's revenue; the group stands or falls with it." }
        val losing = g.subsidiaries.filter { it.shareOfNetIncome.raw < 0L }
        if (losing.isNotEmpty()) out += "${losing.joinToString { it.name }} ${if (losing.size == 1) "is" else "are"} losing money and pulling the group down."
        return out
    }

    fun model(g: GroupStatements, name: String, s: GameSettings): GroupModel = GroupModel(
        title = "$name group",
        tiles = listOf(
            Fact("Businesses", g.size.toString()),
            Fact("Revenue", s.money(g.income.revenue)),
            Fact("Net income", s.money(g.income.netIncome)),
            Fact("Assets", s.money(g.balance.totalAssets)),
            Fact("Equity", s.money(g.balance.totalEquity)),
            Fact("From subsidiaries", s.money(g.subsidiaryNetIncome)),
        ),
        members = g.members.map { row(g, it, s) },
        income = StatementsPresenter.income(g.income, s),
        balance = StatementsPresenter.balance(g.balance, s),
        notes = notes(g),
    )

    /** Null when the selected company owns no other business. */
    fun build(session: GameSession): GroupModel? {
        val g = session.group() ?: return null
        val name = session.economy.companies.company(g.root)?.name ?: return null
        return model(g, name, session.settings)
    }
}
