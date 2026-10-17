package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankStressResult
import app.many.economytycoon.banking.StressReport
import app.many.economytycoon.banking.StressScenario
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting

data class StressScenarioRow(val id: String, val name: String, val description: String, val shocks: String)

data class StressBankRow(
    val name: String,
    val verdict: String,
    val tone: Tone,
    val start: String,
    val trough: String,
    val shortfall: String,
    val note: String,
)

data class StressView(
    val title: String,
    val tiles: List<Tile>,
    val rows: List<StressBankRow>,
    val facts: List<Fact>,
    val summary: String,
)

object BankStressPresenter {
    fun scenarios(): List<StressScenarioRow> = StressScenario.ALL.map {
        StressScenarioRow(it.id, it.name, it.description, shocks(it))
    }

    fun shocks(s: StressScenario): String {
        val parts = ArrayList<String>()
        if (s.rateShock.ppm != 0L) parts += "rates ${Formatting.signedPercentPpm(s.rateShock.ppm)}"
        if (s.defaultMultiplierPpm != 1_000_000L) parts += "defaults x${Formatting.ratioTimes(s.defaultMultiplierPpm).removeSuffix("x")}"
        if (s.collateralIndexPpm != 1_000_000L) parts += "collateral ${Formatting.percentPpm(s.collateralIndexPpm)} of today"
        if (s.depositOutflow.ppm > 0L) parts += "${Formatting.percent(s.depositOutflow)} of deposits leave"
        if (s.wholesaleFreeze.ppm > 0L) parts += "${Formatting.percent(s.wholesaleFreeze)} of wholesale funding lost"
        return if (parts.isEmpty()) "No shocks" else parts.joinToString(", ")
    }

    fun row(r: BankStressResult, money: (Money) -> String): StressBankRow = StressBankRow(
        name = r.name,
        verdict = if (r.passed) "Passes" else "Fails",
        tone = if (r.passed) Tone.GOOD else Tone.BAD,
        start = Formatting.percent(r.startCet1Ratio),
        trough = Formatting.percent(r.minCet1Ratio),
        shortfall = if (r.capitalShortfall.isPositive) money(r.capitalShortfall) else "None",
        note = when {
            !r.passed && r.stressedLcr < app.many.economytycoon.core.Rate.ONE -> "Capital and liquidity both fall short"
            !r.passed -> "Capital falls below the hurdle in month ${r.troughMonth}"
            r.stressedLcr < app.many.economytycoon.core.Rate.ONE -> "Capital holds but liquidity is thin"
            else -> "Capital falls by ${Formatting.percent(r.drawdown)} at the worst point"
        },
    )

    fun build(report: StressReport, money: (Money) -> String): StressView {
        val rows = report.results.sortedBy { it.minCet1Ratio.ppm }.map { row(it, money) }
        val fails = report.failures.size
        val worst = report.worst
        return StressView(
            title = report.scenario.name,
            tiles = listOf(
                Tile("Banks tested", report.results.size.toString()),
                Tile("Failing", fails.toString(), if (fails == 0) Tone.GOOD else Tone.BAD),
                Tile("Pass rate", Formatting.percent(report.passRate), Tones.health(report.passRate.ppm)),
                Tile("Capital needed", money(report.systemShortfall), if (report.systemShortfall.isPositive) Tone.BAD else Tone.GOOD),
            ),
            rows = rows,
            facts = listOf(
                Fact("Scenario", shocks(report.scenario)),
                Fact("Capital hurdle", Formatting.percent(report.hurdle)),
                Fact("Weakest bank", worst?.let { "${it.name}, ${Formatting.percent(it.minCet1Ratio)}" } ?: "None", if (worst != null && !worst.passed) Tone.BAD else Tone.NEUTRAL),
            ),
            summary = when {
                report.results.isEmpty() -> "There are no banks to test."
                fails == 0 -> "Every bank keeps enough capital in this scenario."
                else -> "${Formatting.plural(fails.toLong(), "bank")} would need more capital: ${money(report.systemShortfall)} in total."
            },
        )
    }
}
