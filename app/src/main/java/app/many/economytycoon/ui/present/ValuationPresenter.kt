package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.valuation.Method
import app.many.economytycoon.valuation.Sensitivity
import app.many.economytycoon.valuation.ValuationAssumptions
import app.many.economytycoon.valuation.ValuationInput
import app.many.economytycoon.valuation.ValuationMethods
import app.many.economytycoon.valuation.ValuationSummary
import app.many.economytycoon.valuation.Verdict

enum class Outlook(val label: String, val assumptions: ValuationAssumptions) {
    CAUTIOUS("Cautious", ValuationAssumptions.CAUTIOUS),
    STANDARD("Standard", ValuationAssumptions.DEFAULT),
    OPTIMISTIC("Optimistic", ValuationAssumptions.OPTIMISTIC),
}

data class MethodRow(val label: String, val value: String, val perShare: String, val weight: String, val note: String, val applicable: Boolean)

data class GridModel(val corner: String, val columns: List<String>, val rows: List<Pair<String, List<String>>>)

data class ValuationModel(
    val company: String,
    val tiles: List<Tile>,
    val verdict: String,
    val tone: Tone,
    val methods: List<MethodRow>,
    val grid: GridModel?,
    val reverse: List<Fact>,
)

/** A company's worth from several angles, set against what the market pays. */
object ValuationPresenter {
    const val NOTHING = "Choose a company to value."

    fun verdictTone(v: Verdict): Tone = when (v) {
        Verdict.UNDERVALUED -> Tone.GOOD
        Verdict.FAIR -> Tone.INFO
        Verdict.OVERVALUED -> Tone.WARN
        Verdict.NOT_LISTED, Verdict.WORTHLESS -> Tone.NEUTRAL
    }

    fun verdictText(s: ValuationSummary, money: (Money) -> String): String {
        val p = s.premiumPpm
        return when (s.verdict()) {
            Verdict.NOT_LISTED -> "Not traded on the exchange, so there is no market price to compare with."
            Verdict.WORTHLESS -> "The estimate is too small to compare with the market price."
            Verdict.FAIR -> "The market price of ${money(s.marketPrice!!)} is close to the estimate."
            Verdict.UNDERVALUED -> "The market price of ${money(s.marketPrice!!)} is ${Formatting.percentPpm(-(p ?: 0L))} below the estimate."
            Verdict.OVERVALUED -> "The market price of ${money(s.marketPrice!!)} is ${Formatting.percentPpm(p ?: 0L)} above the estimate."
        }
    }

    fun methodRow(m: app.many.economytycoon.valuation.MethodValue, money: (Money) -> String): MethodRow = MethodRow(
        label = m.method.label,
        value = if (m.applicable) money(m.equity) else "n/a",
        perShare = if (m.applicable) money(m.perShare) else "-",
        weight = if (m.applicable) "${m.method.weight}%" else "-",
        note = m.note,
        applicable = m.applicable,
    )

    fun grid(input: ValuationInput, a: ValuationAssumptions, money: (Money) -> String): GridModel? {
        if (input.freeCashFlow.raw <= 0L) return null
        val cells = Sensitivity.dcfGrid(input, a)
        val columns = Sensitivity.DEFAULT_GROWTHS.map { "Growth ${Formatting.percentPpm(it.ppm)}" }
        val rows = Sensitivity.DEFAULT_DISCOUNTS.mapIndexed { r, d -> "Discount ${Formatting.percentPpm(d.ppm)}" to cells[r].map { it?.let(money) ?: "-" } }
        return GridModel("Cash-flow value", columns, rows)
    }

    fun reverse(input: ValuationInput, a: ValuationAssumptions): List<Fact> {
        val market = input.marketCap ?: return emptyList()
        val g = Sensitivity.impliedGrowth(input, a, market)
        val d = Sensitivity.breakEvenDiscount(input, a, market)
        return listOf(
            Fact("Growth the market expects", g?.let { Formatting.percentPpm(it.ppm) + " a year" } ?: "Out of reach", hint = "The yearly growth that makes the cash-flow value equal the market value."),
            Fact("Highest discount rate that supports the price", d?.let { Formatting.percentPpm(it.ppm) } ?: "None", hint = "Above this return requirement the price looks too high."),
        )
    }

    fun build(session: GameSession, outlook: Outlook = Outlook.STANDARD): ValuationModel? {
        val input = session.valuationInput() ?: return null
        val summary = ValuationSummary.of(input, outlook.assumptions)
        val name = session.selectedCompany?.let { id -> session.simulation.economy.companies.company(id)?.name } ?: "Company"
        return assemble(name, input, summary, outlook.assumptions, session.settings::money)
    }

    fun assemble(name: String, input: ValuationInput, s: ValuationSummary, a: ValuationAssumptions, money: (Money) -> String): ValuationModel {
        val v = s.verdict()
        val tiles = listOf(
            Tile("Estimated value", money(s.equity), note = "${money(s.low)} to ${money(s.high)}"),
            Tile("Per share", money(s.perShare)),
            Tile("Market price", s.marketPrice?.let(money) ?: "-", verdictTone(v), v.label),
            Tile("Confidence", Formatting.percentPpm(s.confidencePpm), if (s.confidencePpm >= 700_000L) Tone.GOOD else Tone.WARN),
        )
        return ValuationModel(name, tiles, verdictText(s, money), verdictTone(v), s.methods.map { methodRow(it, money) }, grid(input, a, money), reverse(input, a))
    }
}
