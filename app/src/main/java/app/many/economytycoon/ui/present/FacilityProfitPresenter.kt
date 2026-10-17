package app.many.economytycoon.ui.present

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.facilityprofit.FacilityPerformance
import app.many.economytycoon.facilityprofit.LinePerformance
import app.many.economytycoon.facilityprofit.PortfolioPerformance
import app.many.economytycoon.facilityprofit.Switch
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class LineProfitRow(val title: String, val profit: String, val tone: Tone, val detail: String)

data class FacilityProfitRow(
    val id: FacilityId,
    val title: String,
    val subtitle: String,
    val net: String,
    val tone: Tone,
    val facts: List<Fact>,
    val lines: List<LineProfitRow>,
    val note: String?,
)

data class SwitchRow(val title: String, val gain: String, val detail: String)

data class FacilityProfitModel(val tiles: List<Tile>, val rows: List<FacilityProfitRow>, val switches: List<SwitchRow>, val empty: String)

/** Which facilities earn their keep, which lines drag, and what swapping a recipe could add. */
object FacilityProfitPresenter {
    const val EMPTY = "You have no facilities yet. Build one and this page will show what it earns."

    fun netTone(net: Money): Tone = when {
        net.raw > 0L -> Tone.GOOD
        net.raw < 0L -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    fun lineRow(l: LinePerformance, money: (Money) -> String): LineProfitRow {
        if (l.recipe == null) return LineProfitRow("Empty line", "-", Tone.MUTED, "No recipe is set.")
        val state = when {
            !l.enabled -> "Paused"
            l.utilisationPpm < 500_000L -> "Running ${Formatting.percentPpm(l.utilisationPpm)} of the time"
            else -> "Busy ${Formatting.percentPpm(l.utilisationPpm)} of the time"
        }
        val tone = when { !l.enabled -> Tone.MUTED; l.losing -> Tone.BAD; else -> Tone.GOOD }
        return LineProfitRow(l.recipeName ?: "Line", money(l.profitPerDay) + " a day", tone, "$state. Each batch makes ${money(l.profitPerBatch)} (${Formatting.percentPpm(l.marginPpm)} on cost).")
    }

    fun note(f: FacilityPerformance, money: (Money) -> String): String? = when {
        f.lines.isEmpty() -> "No production lines."
        f.idleLines == f.lines.size -> "Every line is idle."
        f.losingLines > 0 -> "${Formatting.plural(f.losingLines.toLong(), "line")} lose money on every batch at today's prices."
        f.unusedPerDay.raw > Money.units(1).raw -> "About ${money(f.unusedPerDay)} a day is lost to lines that are paused or waiting."
        else -> null
    }

    fun row(f: FacilityPerformance, money: (Money) -> String): FacilityProfitRow {
        val payback = f.paybackDays?.let { Formatting.duration(it) } ?: "Never"
        return FacilityProfitRow(
            id = f.facility, title = f.name, subtitle = "${f.typeName}, ${f.regionName}",
            net = money(f.netPerDay) + " a day", tone = netTone(f.netPerDay),
            facts = listOf(
                Fact("Lines earn", money(f.grossPerDay) + " a day"),
                Fact("Upkeep and staff", money(f.upkeepPerDay + f.overheadPerDay) + " a day"),
                Fact("Return on cost", Formatting.percentPpm(f.annualReturnPpm) + " a year", netTone(f.netPerDay)),
                Fact("Pays back in", payback),
            ),
            lines = f.lines.map { lineRow(it, money) },
            note = note(f, money),
        )
    }

    fun switchRow(s: Switch, facilityName: String, money: (Money) -> String): SwitchRow =
        SwitchRow("$facilityName: ${s.from ?: "empty line"} to ${s.toName}", "+" + money(s.gainPerDay) + " a day", "Uses the same building and staff roles; check inputs are in reach first.")

    fun model(p: PortfolioPerformance, money: (Money) -> String): FacilityProfitModel {
        val names = p.facilities.associate { it.facility to it.name }
        val best = p.ranked.firstOrNull()
        val tiles = listOf(
            Tile("Earning a day", money(p.netPerDay), netTone(p.netPerDay)),
            Tile("Return a year", Formatting.percentPpm(p.annualReturnPpm)),
            Tile("Best facility", best?.name ?: "-", note = best?.let { money(it.netPerDay) + " a day" }),
            Tile("Lost to idle lines", money(p.unusedPerDay), if (p.unusedPerDay.raw > 0L) Tone.WARN else Tone.NEUTRAL),
        )
        return FacilityProfitModel(
            tiles, p.ranked.map { row(it, money) },
            p.switches.take(10).map { switchRow(it, names[it.facility] ?: "Facility", money) }, EMPTY,
        )
    }

    fun build(session: GameSession): FacilityProfitModel? = session.facilityProfit()?.let { model(it, session.settings::money) }
}
