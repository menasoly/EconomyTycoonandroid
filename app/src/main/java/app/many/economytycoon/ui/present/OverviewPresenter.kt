package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.DistressState
import app.many.economytycoon.session.Alert
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.ObjectiveProgress
import app.many.economytycoon.session.Objectives
import app.many.economytycoon.session.Severity

data class LeaderRow(val rank: Int, val company: CompanyId, val name: String, val netWorth: String, val player: Boolean, val tone: Tone)

data class OverviewModel(
    val companyName: String,
    val dateText: String,
    val tiles: List<Tile>,
    val standing: String,
    val standingTone: Tone,
    val netWorthTrend: List<Double>,
    val cashTrend: List<Double>,
    val alerts: List<Alert>,
    val criticalCount: Int,
    val nextGoals: List<ObjectiveProgress>,
    val leaders: List<LeaderRow>,
)

/** Everything the overview screen shows, worked out once so the screen only draws it. */
object OverviewPresenter {
    private const val LEADER_ROWS = 6

    fun build(session: GameSession): OverviewModel? {
        val id = session.selectedCompany ?: return null
        val reports = session.reports.company
        val head = reports.headline(id) ?: return null
        val money = session.settings::money
        val change = session.stats.netWorthChange(id)
        val tiles = listOf(
            Tile("Cash", money(head.cash), if (head.cash.raw < 0) Tone.BAD else Tone.NEUTRAL),
            Tile("Net worth", money(head.netWorth), Tone.NEUTRAL, change?.let { Formatting.signedMoney(it, session.settings.symbol) + " lately" }),
            Tile("Revenue", money(head.revenue), Tone.NEUTRAL, "last 30 days"),
            Tile("Profit", money(head.netIncome), Tones.bySign(head.netIncome.raw), "last 30 days"),
            Tile("Debt", money(head.debt), if (head.debt.raw > head.netWorth.raw && head.debt.raw > 0) Tone.WARN else Tone.NEUTRAL),
            Tile("Staff", Formatting.grouped(head.employees), Tone.NEUTRAL, if (head.facilities == 1) "1 facility" else "${head.facilities} facilities"),
        )
        val stats = session.stats.of(id)
        val board = reports.leaderboard()
        val rows = board.mapIndexed { i, h -> LeaderRow(i + 1, h.id, h.name, money(h.netWorth), h.id == id, if (h.id == id) Tone.INFO else Tone.NEUTRAL) }
        // Always show where the player stands, even when outside the top places.
        val leaders = rows.take(LEADER_ROWS).let { top -> if (top.any { it.player }) top else top.take(LEADER_ROWS - 1) + rows.filter { it.player } }
        val alerts = session.alerts()
        val goals = Objectives.evaluate(session.economy, id).filter { !it.done }.sortedByDescending { it.ppm }.take(3)
        return OverviewModel(
            companyName = head.name,
            dateText = Formatting.date(session.tick),
            tiles = tiles,
            standing = standing(head.rating, head.distress),
            standingTone = distressTone(head.distress),
            netWorthTrend = stats.map { it.netWorth.raw.toDouble() / 10_000.0 },
            cashTrend = stats.map { it.cash.raw.toDouble() / 10_000.0 },
            alerts = alerts.take(4),
            criticalCount = alerts.count { it.severity == Severity.CRITICAL },
            nextGoals = goals,
            leaders = leaders,
        )
    }

    fun standing(rating: CreditRating?, distress: DistressState?): String {
        val r = rating?.let { "Credit ${it.label}" }
        val d = when (distress) {
            null, DistressState.HEALTHY -> null
            DistressState.WATCH -> "On watch"
            DistressState.DISTRESSED -> "In distress"
            DistressState.INSOLVENT -> "Insolvent"
            DistressState.LIQUIDATING -> "Liquidating"
        }
        return listOfNotNull(r, d).joinToString(" · ").ifEmpty { "No credit history yet" }
    }

    fun distressTone(d: DistressState?): Tone = when (d) {
        null, DistressState.HEALTHY -> Tone.GOOD
        DistressState.WATCH -> Tone.WARN
        else -> Tone.BAD
    }

    fun severityTone(s: Severity): Tone = when (s) {
        Severity.INFO -> Tone.INFO
        Severity.WARNING -> Tone.WARN
        Severity.CRITICAL -> Tone.BAD
    }
}
