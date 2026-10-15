package app.many.economytycoon.ui.present

import app.many.economytycoon.agenda.Agenda
import app.many.economytycoon.agenda.AgendaItem
import app.many.economytycoon.agenda.AgendaKind
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class AgendaRow(val title: String, val detail: String, val cash: String?, val cashTone: Tone, val kind: String, val risky: Boolean)

data class AgendaDay(val label: String, val inDays: String, val rows: List<AgendaRow>)

data class AgendaModel(
    val tiles: List<Tile>,
    val days: List<AgendaDay>,
    val warning: String?,
    val empty: String,
)

/** What is coming up for the company, a day at a time, with how much cash the dated items will move. */
object AgendaPresenter {
    const val EMPTY = "Nothing is due. Loans, contracts, tenders and grants will be listed here."
    val windows = listOf(30, 60, 90)

    fun whenText(day: Long, now: Long): String {
        val d = day - now
        return when {
            d <= 0L -> "Today"
            d == 1L -> "Tomorrow"
            else -> "In $d days"
        }
    }

    fun row(item: AgendaItem, money: (Money) -> String): AgendaRow {
        val cash = item.cash
        val tone = when {
            cash == null -> Tone.NEUTRAL
            cash.raw < 0L -> Tone.WARN
            else -> Tone.GOOD
        }
        return AgendaRow(item.title, item.detail, cash?.let { if (it.raw > 0L) "+" + money(it) else money(it) }, tone, item.kind.label, item.risky)
    }

    fun warning(a: Agenda, cash: Money?, money: (Money) -> String): String? {
        cash ?: return null
        val out = a.outflow(30)
        if (out.raw >= 0L) return null
        val need = Money(-out.raw)
        return if (cash.raw < need.raw) "Payments in the next 30 days come to ${money(need)}, which is more than the ${money(cash)} you hold."
        else null
    }

    fun build(session: GameSession): AgendaModel? {
        val a = session.agenda() ?: return null
        val me = session.selectedCompany ?: return null
        val money = session.settings::money
        val cash = session.reports.company.headline(me)?.cash
        val now = session.tick.value
        val out30 = a.outflow(30); val in30 = a.inflow(30)
        val tiles = listOf(
            Tile("Due in 30 days", Formatting.plural(a.items.count { it.day < now + 30 }.toLong(), "item")),
            Tile("Money out", money(Money(-out30.raw)), if (out30.raw < 0L) Tone.WARN else Tone.NEUTRAL, "next 30 days"),
            Tile("Money in", money(in30), if (in30.raw > 0L) Tone.GOOD else Tone.NEUTRAL, "next 30 days"),
            Tile("Costly to miss", a.risky.size.toString(), if (a.risky.isEmpty()) Tone.NEUTRAL else Tone.INFO),
        )
        val days = a.byDay().map { (day, items) ->
            AgendaDay(Formatting.date(Tick(day)), whenText(day, now), items.map { row(it, money) })
        }
        return AgendaModel(tiles, days, warning(a, cash, money), EMPTY)
    }

    fun kinds(): List<String> = AgendaKind.values().map { it.label }
}
