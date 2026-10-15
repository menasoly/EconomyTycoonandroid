package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.forecast.EndReason
import app.many.economytycoon.forecast.Forecast
import app.many.economytycoon.forecast.OutlookInput
import app.many.economytycoon.forecast.Projector
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.visualization.DataPoint
import app.many.economytycoon.visualization.LineSeries

data class OutlookModel(
    val days: Int,
    val headline: String,
    val tone: Tone,
    val tiles: List<Fact>,
    val warnings: List<String>,
    val series: List<LineSeries>,
    val caveat: String,
)

/** Where the company is heading if nothing is changed, from a private run of the game. */
object OutlookPresenter {
    val horizons: List<Int> = listOf(30, 90, 180, 360)

    const val CAVEAT = "This plays the game forward as it stands, rivals and prices included. Anything you do will change it."

    fun horizonLabel(days: Int): String = when (days) {
        30 -> "1 month"
        90 -> "3 months"
        180 -> "6 months"
        360 -> "1 year"
        else -> "$days days"
    }

    private fun abs(m: Money) = Money(kotlin.math.abs(m.raw))

    fun headline(f: Forecast, s: GameSettings, days: Int): String {
        if (f.ended == EndReason.COMPANY_GONE) return "The company would not survive."
        if (f.ended == EndReason.NOT_ACTIVE) return "The company would stop trading before ${horizonLabel(days)} are up."
        val change = f.netWorthChange
        val span = horizonLabel(days)
        return when {
            change.raw > 0L -> "Net worth would rise by ${s.money(change)} over $span."
            change.raw < 0L -> "Net worth would fall by ${s.money(abs(change))} over $span."
            else -> "Net worth would stay about where it is over $span."
        }
    }

    fun tone(f: Forecast): Tone = when {
        f.ended != EndReason.COMPLETED -> Tone.BAD
        f.firstOverdrawn != null -> Tone.BAD
        f.netWorthChange.raw < 0L -> Tone.WARN
        f.netWorthChange.raw > 0L -> Tone.GOOD
        else -> Tone.NEUTRAL
    }

    fun warnings(f: Forecast, s: GameSettings): List<String> {
        val out = ArrayList<String>()
        f.firstOverdrawn?.let { out += "Cash runs out on day $it." }
        val low = f.lowestCash
        val first = f.first
        if (low != null && first != null && low.cash < first.cash && f.firstOverdrawn == null && low.day != first.day) {
            out += "Cash is lowest on day ${low.day}, at ${s.money(low.cash)}."
        }
        val l = f.last
        if (l != null && first != null && l.debt > first.debt) out += "Debt grows by ${s.money(l.debt - first.debt)}."
        if (f.ended == EndReason.NOT_ACTIVE) out += "The company is no longer trading by day ${l?.day}."
        return out
    }

    fun series(f: Forecast): List<LineSeries> = listOf(
        LineSeries("Net worth", f.points.map { DataPoint((it.day - f.startDay).toDouble(), it.netWorth.raw / Money.SCALE.toDouble()) }, 0),
        LineSeries("Cash", f.points.map { DataPoint((it.day - f.startDay).toDouble(), it.cash.raw / Money.SCALE.toDouble()) }, 1),
    )

    fun model(f: Forecast, s: GameSettings, days: Int): OutlookModel {
        val last = f.last
        val tiles = ArrayList<Fact>()
        if (last != null) {
            tiles += Fact("Cash then", s.money(last.cash))
            tiles += Fact("Net worth then", s.money(last.netWorth))
            tiles += Fact("Net worth per day", s.money(f.netWorthPerDay))
            tiles += Fact("Revenue", s.money(f.revenueOverRun))
            f.lowestCash?.let { tiles += Fact("Lowest cash", s.money(it.cash)) }
        }
        return OutlookModel(days, headline(f, s, days), tone(f), tiles, warnings(f, s), series(f), CAVEAT)
    }

    /** Runs the trial. This takes real time, so call it away from the screen's thread. */
    fun run(input: OutlookInput, days: Int, settings: GameSettings): OutlookModel =
        model(Projector.projectSaved(input.saved, input.content, input.company, days), settings, days)

    fun input(session: GameSession): OutlookInput? = session.outlookInput()
}
