package app.many.economytycoon.ui.present

import app.many.economytycoon.cashflow.CashOutlook
import app.many.economytycoon.cashflow.CashProjection
import app.many.economytycoon.cashflow.Scenario
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class CashWeekRow(val label: String, val balance: String, val change: String, val tone: Tone, val events: List<String>)

data class CashForecastModel(
    val tiles: List<Tile>,
    val verdict: String,
    val tone: Tone,
    val weeks: List<CashWeekRow>,
    val dueSoon: List<Fact>,
    val scenarios: List<Fact>,
)

/** The next three months of cash, week by week, under a cautious, expected or hopeful view. */
object CashForecastPresenter {
    const val NOTHING = "Choose a company to forecast."

    fun verdict(o: CashOutlook, p: CashProjection, money: (Money) -> String): Pair<String, Tone> {
        val short = p.firstShortfall
        return when {
            o.certainShortfall -> "Cash runs out around day ${o.hopeful.firstShortfall!!.day} even if things go well. Raise money or cut costs soon." to Tone.BAD
            short != null -> "On this view cash runs out around day ${short.day}; about ${money(p.gapToCover)} would cover the gap." to Tone.WARN
            o.atRisk -> "Fine on the expected view, but a cautious view runs short around day ${o.cautious.firstShortfall!!.day}." to Tone.WARN
            p.end.raw >= p.start.raw -> "Cash holds up and ends the period higher." to Tone.GOOD
            else -> "Cash falls over the period but stays above zero." to Tone.INFO
        }
    }

    /** One row for each week of the projection: where the balance ends, the week's net change and what lands in it. */
    fun weeks(p: CashProjection, money: (Money) -> String): List<CashWeekRow> =
        p.points.chunked(7).mapIndexed { i, week ->
            val change = Money(week.sumOf { it.change.raw })
            val last = week.last()
            CashWeekRow(
                label = "Week ${i + 1}, to day ${last.day}",
                balance = money(last.balance),
                change = (if (change.raw > 0L) "+" else "") + money(change),
                tone = when { last.balance.raw < 0L -> Tone.BAD; change.raw < 0L -> Tone.WARN; else -> Tone.GOOD },
                events = week.flatMap { d -> d.lumps.map { "Day ${d.day}: ${it.label}, ${money(it.amount)}" } },
            )
        }

    fun model(o: CashOutlook, scenario: Scenario, money: (Money) -> String): CashForecastModel {
        val p = o.of(scenario)
        val (text, tone) = verdict(o, p, money)
        val low = p.lowest
        val tiles = listOf(
            Tile("Cash now", money(p.start)),
            Tile("In ${p.points.size} days", money(p.end), if (p.end.raw < p.start.raw) Tone.WARN else Tone.GOOD),
            Tile("Lowest point", low?.let { money(it.balance) } ?: "-", if ((low?.balance?.raw ?: 0L) < 0L) Tone.BAD else Tone.NEUTRAL, low?.let { "Day ${it.day}" }),
            Tile("Payments due", money(p.lumpOut), note = "Known loan instalments"),
        )
        val due = p.points.flatMap { it.lumps }.take(8).map { Fact("Day ${it.day}", money(it.amount), Tone.NEUTRAL, it.label) }
        val scenarios = o.all.map { s ->
            Fact(s.scenario.label, money(s.end), if (s.firstShortfall != null) Tone.BAD else Tone.NEUTRAL, s.firstShortfall?.let { "Short on day ${it.day}" } ?: "Stays above zero")
        }
        return CashForecastModel(tiles, text, tone, weeks(p, money), due, scenarios)
    }

    fun build(session: GameSession, scenario: Scenario = Scenario.EXPECTED, horizon: Int = 84): CashForecastModel? =
        session.cashOutlook(horizon)?.let { model(it, scenario, session.settings::money) }

    fun span(days: Int) = Formatting.duration(days.toLong())
}
