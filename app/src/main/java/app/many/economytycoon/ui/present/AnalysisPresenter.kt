package app.many.economytycoon.ui.present

import app.many.economytycoon.analysis.CompanyAnalysis
import app.many.economytycoon.analysis.Grade
import app.many.economytycoon.analysis.Measure
import app.many.economytycoon.analysis.RunwayStatus
import app.many.economytycoon.analysis.Standing
import app.many.economytycoon.session.AnalysisView
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class ReadingRow(val label: String, val value: String, val grade: String, val tone: Tone, val hint: String)

data class CostRow(val label: String, val amount: String, val share: String, val ratio: Double, val variable: Boolean)

data class StandingRow(val label: String, val you: String, val median: String, val best: String, val place: String, val tone: Tone)

data class AnalysisModel(
    val company: String,
    val headline: String,
    val tone: Tone,
    val readings: List<ReadingRow>,
    val worries: List<String>,
    val efficiency: List<Fact>,
    val returns: List<Fact>,
    val costs: List<CostRow>,
    val breakEven: List<Fact>,
    val runway: Fact,
    val outlook: List<Fact>,
    val peers: List<StandingRow>,
    val peerNote: String,
)

object AnalysisPresenter {
    fun gradeTone(g: Grade): Tone = when (g) { Grade.STRONG -> Tone.GOOD; Grade.SOUND -> Tone.INFO; Grade.WATCH -> Tone.WARN; Grade.WEAK -> Tone.BAD }

    /** A reading's number in the unit that suits the measure. */
    fun value(m: Measure, v: Long): String = when (m) {
        Measure.CASH_CYCLE -> Formatting.plural(v, "day")
        Measure.CURRENT, Measure.CASH, Measure.DEBT_TO_EQUITY, Measure.INTEREST_COVER, Measure.ASSET_TURNOVER -> Formatting.ratioTimes(v)
        else -> Formatting.percentPpm(v)
    }

    fun headline(overall: Grade?): String = when (overall) {
        null -> "There is not enough trading yet to judge the books."
        Grade.STRONG -> "The books look strong."
        Grade.SOUND -> "The books are sound, with room to improve."
        Grade.WATCH -> "Some figures need watching."
        Grade.WEAK -> "The books are in poor shape."
    }

    fun reading(r: app.many.economytycoon.analysis.Reading) = ReadingRow(r.label, value(r.measure, r.value), r.grade.label, gradeTone(r.grade), r.measure.hint)

    private fun days(n: Long?) = n?.let { Formatting.plural(it, "day") } ?: "n/a"

    fun efficiency(a: CompanyAnalysis): List<Fact> {
        val e = a.efficiency
        return listOf(
            Fact("Asset turnover", e.assetTurnoverPpm?.let { Formatting.ratioTimes(it) } ?: "n/a", Tone.NEUTRAL, "Yearly sales for each unit of assets"),
            Fact("Plant turnover", e.fixedAssetTurnoverPpm?.let { Formatting.ratioTimes(it) } ?: "n/a", Tone.NEUTRAL, "Yearly sales for each unit of buildings and machines"),
            Fact("Stock held", days(e.inventoryDays), Tone.NEUTRAL, "Days of materials on the shelf"),
            Fact("Customers pay in", days(e.receivableDays)),
            Fact("You pay suppliers in", days(e.payableDays)),
            Fact("Cash cycle", days(e.cashCycleDays), (e.cashCycleDays ?: 0L).let { if (it > 90L) Tone.WARN else Tone.NEUTRAL }, "Days between paying for goods and being paid"),
        )
    }

    fun returns(a: CompanyAnalysis): List<Fact> {
        val d = a.dupont ?: return listOf(Fact("Return on equity", "n/a", Tone.MUTED, "Needs sales, assets and positive equity"))
        val weak = d.weakest()
        return listOf(
            Fact("Profit margin", Formatting.percentPpm(d.marginPpm), Tones.bySign(d.marginPpm), "Share of each sale kept"),
            Fact("Asset turnover", Formatting.ratioTimes(d.turnoverPpm), Tone.NEUTRAL, "Sales made by each unit of assets"),
            Fact("Leverage", Formatting.ratioTimes(d.leveragePpm), Tone.NEUTRAL, "Assets for each unit of owners' money"),
            Fact("Return on equity", Formatting.percentPpm(d.returnOnEquityPpm), Tones.bySign(d.returnOnEquityPpm), "The three above, multiplied"),
            Fact("Hold-back", weak?.label?.replaceFirstChar { it.uppercase() } ?: "None", if (weak == null) Tone.GOOD else Tone.WARN, "The factor pulling the return down the most"),
        )
    }

    fun costs(a: CompanyAnalysis, money: (app.many.economytycoon.core.Money) -> String): List<CostRow> =
        a.costs.lines.map { CostRow(it.label, money(it.amount), Formatting.percentPpm(it.sharePpm), (it.sharePpm / 1_000_000.0).coerceIn(0.0, 1.0), it.variable) }

    fun breakEven(a: CompanyAnalysis, money: (app.many.economytycoon.core.Money) -> String): List<Fact> {
        val c = a.costs
        val be = c.breakEvenRevenue
        val mos = c.marginOfSafetyPpm
        val lev = c.operatingLeveragePpm
        return listOf(
            Fact("Break-even sales", be?.let(money) ?: "Never", if (be == null) Tone.BAD else Tone.NEUTRAL, "Monthly sales that cover every cost"),
            Fact("Room to fall", mos?.let { Formatting.percentPpm(it) } ?: "n/a", mos?.let { if (it < 0L) Tone.BAD else if (it < 150_000L) Tone.WARN else Tone.GOOD } ?: Tone.MUTED, "How far sales can drop before a loss"),
            Fact("Profit sensitivity", lev?.let { Formatting.ratioTimes(it) } ?: "n/a", Tone.NEUTRAL, "Percent change in profit for one percent change in sales"),
            Fact("Fixed costs", money(c.fixed), Tone.NEUTRAL, "Owed whatever is sold"),
            Fact("Variable costs", money(c.variable), Tone.NEUTRAL, "Move with output"),
        )
    }

    fun runway(a: CompanyAnalysis, money: (app.many.economytycoon.core.Money) -> String): Fact {
        val r = a.runway
        val tone = when (r.status()) {
            RunwayStatus.EMPTY, RunwayStatus.CRITICAL -> Tone.BAD
            RunwayStatus.SHORT -> Tone.WARN
            RunwayStatus.COMFORTABLE, RunwayStatus.GROWING -> Tone.GOOD
        }
        val value = when (r.status()) {
            RunwayStatus.GROWING -> "Cash is not shrinking"
            RunwayStatus.EMPTY -> "No cash"
            else -> Formatting.plural(r.days ?: 0L, "day") + " at ${money(r.burnPerDay)} a day"
        }
        return Fact("Cash runway", value, tone, r.status().label)
    }

    fun outlook(a: CompanyAnalysis, money: (app.many.economytycoon.core.Money) -> String): List<Fact> {
        val f = a.outlook ?: return listOf(Fact("Outlook", "Not enough history yet", Tone.MUTED, "Needs about a month of play"))
        val in30 = f.at(30L)
        val in90 = f.at(90L)
        val note = if (f.reliable) "The trend has been steady" else "The trend has been jumpy, so treat this loosely"
        return listOf(
            Fact("Net worth trend", money(f.trendPerDay) + " a day", Tones.bySign(f.trendPerDay.raw), note),
            Fact("In 30 days", money(in30.expected), Tone.NEUTRAL, "Likely between ${money(in30.low)} and ${money(in30.high)}"),
            Fact("In 90 days", money(in90.expected), Tone.NEUTRAL, "Likely between ${money(in90.low)} and ${money(in90.high)}"),
        )
    }

    fun place(s: Standing): String = when (s.quartile) {
        1 -> "Top quarter"
        2 -> "Above the middle"
        3 -> "Below the middle"
        else -> "Bottom quarter"
    }

    fun standing(s: Standing) = StandingRow(
        s.measure.label, value(s.measure, s.value), value(s.measure, s.peerMedian), value(s.measure, s.best), place(s),
        when (s.quartile) { 1 -> Tone.GOOD; 2 -> Tone.INFO; 3 -> Tone.WARN; else -> Tone.BAD },
    )

    fun model(view: AnalysisView, money: (app.many.economytycoon.core.Money) -> String): AnalysisModel {
        val a = view.analysis
        val worries = a.weakSpots.map { "${it.label} is ${value(it.measure, it.value)}. ${it.measure.hint}" }
        val peerNote = if (view.standings.isEmpty()) "Compared with ${view.peerCount} other trading companies; at least three are needed." else "Compared with ${view.peerCount} other trading companies."
        return AnalysisModel(
            company = view.name,
            headline = headline(a.overall),
            tone = a.overall?.let { gradeTone(it) } ?: Tone.MUTED,
            readings = a.readings.map { reading(it) },
            worries = worries,
            efficiency = efficiency(a),
            returns = returns(a),
            costs = costs(a, money),
            breakEven = breakEven(a, money),
            runway = runway(a, money),
            outlook = outlook(a, money),
            peers = view.standings.map { standing(it) },
            peerNote = peerNote,
        )
    }

    /** The selected company's analysis for a running game; null before one is chosen. */
    fun build(session: GameSession): AnalysisModel? = session.analysis()?.let { model(it, session.settings::money) }
}
