package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.energygrid.BlackoutRecord
import app.many.economytycoon.energygrid.EnergyGridState
import app.many.economytycoon.energygrid.Grid
import app.many.economytycoon.energygrid.GridAdvisor
import app.many.economytycoon.energygrid.GridAnalytics
import app.many.economytycoon.energygrid.GridFacts
import app.many.economytycoon.energygrid.GridInsight
import app.many.economytycoon.energygrid.GridUnits
import app.many.economytycoon.energygrid.HourProfile
import app.many.economytycoon.energygrid.OutageEvent
import app.many.economytycoon.energygrid.Ppa
import app.many.economytycoon.energygrid.PpaStatus
import app.many.economytycoon.energygrid.Technology
import app.many.economytycoon.energygrid.UnitStatus
import app.many.economytycoon.session.Formatting

data class GridSummaryRow(val id: Long, val name: String, val price: String, val reserve: String, val tone: Tone, val note: String)

data class GridListModel(val rows: List<GridSummaryRow>, val empty: String)

data class MixLine(val tech: String, val capacity: String, val share: String, val units: Int)

data class GridOverviewModel(
    val title: String,
    val tiles: List<Tile>,
    val facts: List<Fact>,
    val mix: List<MixLine>,
    val insights: List<InsightLine>,
)

data class InsightLine(val code: String, val title: String, val detail: String, val tone: Tone)

data class StackLayer(val tech: String, val power: String, val sharePpm: Long)

data class StackHour(val hour: Int, val demand: String, val price: String, val unserved: String?, val setter: String, val layers: List<StackLayer>, val tone: Tone)

data class DispatchStackModel(val hours: List<StackHour>, val peakHour: Int?, val empty: String)

data class PpaRow(val id: Long, val parties: String, val terms: String, val progress: String, val status: String, val tone: Tone)

data class PpaListModel(val rows: List<PpaRow>, val tiles: List<Tile>, val empty: String)

data class OutageRow(val what: String, val kind: String, val period: String, val tone: Tone)

data class BlackoutRow(val id: Long, val when_: String, val length: String, val shed: String, val damage: String, val loads: Int, val open: Boolean)

data class OutageReportModel(val tiles: List<Tile>, val outages: List<OutageRow>, val blackouts: List<BlackoutRow>, val empty: String)

data class PriceHistoryModel(val tiles: List<Tile>, val series: List<Long>, val labels: List<String>, val duration: List<Pair<String, String>>, val empty: String)

/** Read-only views of the electricity grids. Money is formatted by the caller's function so settings stay in one place. */
object EnergyGridPresenter {
    const val NO_GRIDS = "There are no electricity grids in this game yet."
    const val NO_DISPATCH = "Nothing has been dispatched yet. The first day will fill this in."
    const val NO_PPAS = "No power purchase agreements on this grid."
    const val NO_OUTAGES = "No units are out and there have been no blackouts."
    const val NO_PRICES = "Prices appear once the grid has run for a day."

    fun power(kw: Long): String = when {
        kw >= 1_000_000L -> Formatting.grouped(kw / 1_000_000L) + "." + ((kw % 1_000_000L) / 100_000L) + " GW"
        kw >= 1_000L -> Formatting.grouped(kw / 1_000L) + " MW"
        else -> Formatting.grouped(kw) + " kW"
    }

    fun energy(kwh: Long): String = when {
        kwh >= 1_000_000L -> Formatting.grouped(kwh / 1_000_000L) + " GWh"
        kwh >= 1_000L -> Formatting.grouped(kwh / 1_000L) + " MWh"
        else -> Formatting.grouped(kwh) + " kWh"
    }

    fun perMwh(m: Money, money: (Money) -> String): String = money(m) + "/MWh"

    fun reserveTone(reservePpm: Long, targetPpm: Long): Tone = when {
        reservePpm < 0L -> Tone.BAD
        reservePpm < targetPpm -> Tone.WARN
        else -> Tone.GOOD
    }

    fun priceTone(price: Money, cap: Money): Tone = when {
        price.raw * 2 >= cap.raw -> Tone.BAD
        price.raw * 4 >= cap.raw -> Tone.WARN
        else -> Tone.NEUTRAL
    }

    fun insightTone(priority: Int): Tone = when {
        priority >= 70 -> Tone.BAD
        priority >= 45 -> Tone.WARN
        priority >= 30 -> Tone.INFO
        else -> Tone.NEUTRAL
    }

    fun insight(i: GridInsight): InsightLine = InsightLine(i.code, i.title, i.detail, insightTone(i.priority))

    private fun peakKw(grid: Grid): Long = grid.history.takeLast(90).maxOfOrNull { it.peakDemandKw } ?: 0L

    // ----- all grids -----

    fun list(state: EnergyGridState, money: (Money) -> String): GridListModel {
        val rows = state.grids.map { g ->
            val last = g.history.lastOrNull()
            val reserve = GridAnalytics.reserveMarginPpm(g, peakKw(g))
            GridSummaryRow(
                g.id.value, g.name, last?.let { perMwh(it.averagePrice, money) } ?: "No price yet", Formatting.signedPercentPpm(reserve),
                reserveTone(reserve, g.params.targetReservePpm),
                "${g.units.count { it.isLive }} units, ${power(g.connectedBaseKw())} connected",
            )
        }
        return GridListModel(rows, NO_GRIDS)
    }

    // ----- one grid -----

    fun overview(state: EnergyGridState, grid: Grid, tick: Long, money: (Money) -> String): GridOverviewModel {
        val peak = peakKw(grid)
        val reserve = GridAnalytics.reserveMarginPpm(grid, peak)
        val month = grid.history.takeLast(30)
        val last = grid.history.lastOrNull()
        val rel = GridAnalytics.reliability(grid.history, 30)
        val gen = LongArray(Technology.values().size).also { a -> for (d in month) for (i in a.indices) a[i] += d.generationKwh[i] }.toList()
        val tiles = listOf(
            Tile("Price", last?.let { perMwh(it.averagePrice, money) } ?: "None yet", last?.let { priceTone(it.peakPrice, grid.params.priceCap) } ?: Tone.MUTED, "Yesterday's average"),
            Tile("Reserve", Formatting.signedPercentPpm(reserve), reserveTone(reserve, grid.params.targetReservePpm), "Target ${Formatting.percentPpm(grid.params.targetReservePpm)}"),
            Tile("Peak demand", power(peak), note = "Last 90 days"),
            Tile("Clean energy", Formatting.percentPpm(GridAnalytics.renewableShare(gen)), note = "Last 30 days"),
            Tile("Carbon", "${GridAnalytics.carbonIntensity(month, 30)} g/kWh", Tone.NEUTRAL, "Last 30 days"),
            Tile("Supply met", Formatting.percentPpm(rel.availabilityPpm), Tones.health(rel.availabilityPpm), "${rel.lossOfLoadHours} dark hours"),
        )
        val stats = GridAnalytics.priceStats(month, 30, grid.params.priceCap)
        val facts = buildList {
            add(Fact("Region", grid.name))
            add(Fact("Firm capacity", power(grid.firmKw())))
            add(Fact("Under construction", power(grid.firmUnderConstructionKw())))
            add(Fact("Connected load", power(grid.connectedBaseKw())))
            add(Fact("Price cap", perMwh(grid.params.priceCap, money)))
            if (stats != null) {
                add(Fact("30-day average", perMwh(stats.mean, money)))
                add(Fact("Scarcity days", stats.spikeDays.toString(), if (stats.spikeDays > 0) Tone.WARN else Tone.NEUTRAL))
            }
            add(Fact("Capacity payment", grid.capacityRound?.let { perMwh(it.clearingPrice, money) + " per MW a day" } ?: "No auction yet"))
            add(Fact("Households", grid.tariff.householdCapPerMwh?.let { "Capped at ${perMwh(it, money)}" } ?: "No price cap"))
        }
        val mix = GridAnalytics.fleetMix(grid).map { MixLine(it.tech.label, power(it.installedKw), Formatting.percentPpm(it.sharePpm), it.units) }
        val advisor = GridAdvisor()
        val insights = advisor.advise(GridFacts.of(state, grid, tick, peak)).take(6).map { insight(it) }
        return GridOverviewModel(grid.name, tiles, facts, mix, insights)
    }

    // ----- dispatch stack -----

    fun stackHour(h: HourProfile, grid: Grid, money: (Money) -> String): StackHour {
        val total = h.generationKw.coerceAtLeast(1L)
        val layers = Technology.values().filter { h.outputKw[it.ordinal] > 0L }.map {
            StackLayer(it.label, power(h.outputKw[it.ordinal]), mulDiv(h.outputKw[it.ordinal], Rate.ONE_PPM, total, RoundingMode.HALF_EVEN))
        }
        val setter = grid.units.firstOrNull { it.id.value == h.setter }?.let { it.tech.label + " " + it.name } ?: if (h.unservedKw > 0L) "Scarcity" else "None"
        val tone = when {
            h.unservedKw > 0L -> Tone.BAD
            priceTone(h.price, grid.params.priceCap) != Tone.NEUTRAL -> Tone.WARN
            else -> Tone.NEUTRAL
        }
        return StackHour(h.hour, power(h.demandKw), perMwh(h.price, money), h.unservedKw.takeIf { it > 0L }?.let { power(it) + " unserved" }, setter, layers, tone)
    }

    fun dispatchStack(grid: Grid, money: (Money) -> String): DispatchStackModel {
        val day = grid.lastDay
        if (day.isEmpty()) return DispatchStackModel(emptyList(), null, NO_DISPATCH)
        return DispatchStackModel(day.map { stackHour(it, grid, money) }, day.maxByOrNull { it.demandKw }?.hour, NO_DISPATCH)
    }

    // ----- agreements -----

    fun ppaRow(p: Ppa, tick: Long, money: (Money) -> String): PpaRow {
        val volume = if (p.sharePpm > 0L) "${Formatting.percentPpm(p.sharePpm)} of the unit" else power(p.volumeKw)
        val tone = when {
            p.status == PpaStatus.ACTIVE && p.shortfallKwh > 0L -> Tone.WARN
            p.status == PpaStatus.ACTIVE -> Tone.GOOD
            p.status == PpaStatus.OFFERED -> Tone.INFO
            else -> Tone.MUTED
        }
        val progress = when (p.status) {
            PpaStatus.ACTIVE -> "${p.remainingDays(tick)} days left, ${energy(p.totalDeliveredKwh)} delivered" + if (p.totalShortfallKwh > 0L) ", ${energy(p.totalShortfallKwh)} short" else ""
            PpaStatus.OFFERED -> "Starts on day ${p.startsAt}"
            else -> "Ended on day ${p.closedAt}"
        }
        return PpaRow(p.id.value, "${p.profile.label}: seller ${p.seller.value}, buyer ${p.buyer.value}", "$volume at ${perMwh(p.strikePerMwh, money)} until day ${p.endsAt}", progress, p.status.label, tone)
    }

    fun ppaList(state: EnergyGridState, grid: Grid, tick: Long, money: (Money) -> String): PpaListModel {
        val all = state.ppasOf(grid.id)
        val live = all.filter { it.status == PpaStatus.ACTIVE }
        val order = all.sortedWith(compareBy<Ppa> { if (it.status == PpaStatus.ACTIVE) 0 else if (it.status == PpaStatus.OFFERED) 1 else 2 }.thenBy { it.id })
        val tiles = listOf(
            Tile("Active", live.size.toString()),
            Tile("Contracted", power(live.sumOf { if (it.profile == app.many.economytycoon.energygrid.PpaProfile.BASELOAD) it.volumeKw else 0L }), note = "Baseload"),
            Tile("Short", live.count { it.shortfallKwh > 0L || it.totalShortfallKwh > 0L }.toString(), if (live.any { it.shortfallKwh > 0L }) Tone.WARN else Tone.NEUTRAL),
        )
        return PpaListModel(order.map { ppaRow(it, tick, money) }, tiles, NO_PPAS)
    }

    // ----- outages and blackouts -----

    fun outageRow(o: OutageEvent, grid: Grid): OutageRow {
        val unit = grid.units.firstOrNull { it.id == o.unit }
        val name = unit?.let { it.tech.label + " " + it.name } ?: "Unit ${o.unit.value}"
        val tone = when (o.kind) {
            app.many.economytycoon.energygrid.OutageKind.FORCED -> Tone.BAD
            app.many.economytycoon.energygrid.OutageKind.DEFERRED -> Tone.INFO
            else -> Tone.WARN
        }
        return OutageRow(name, o.kind.label, "Day ${o.start} to day ${o.until}", tone)
    }

    fun blackoutRow(b: BlackoutRecord, money: (Money) -> String): BlackoutRow = BlackoutRow(
        b.id.value, "Day ${b.startTick}, hour ${b.startHour}", "${b.hours} hours", "${energy(b.shedKwh)} cut, peak ${power(b.peakShedKw)}", money(b.damage), b.loadsAffected, b.open,
    )

    fun outageReport(grid: Grid, money: (Money) -> String): OutageReportModel {
        val recent = grid.outages.takeLast(20).reversed()
        val blackouts = grid.blackouts.takeLast(10).reversed()
        val down = grid.units.count { it.status == UnitStatus.FORCED_OUTAGE }
        val maint = grid.units.count { it.status == UnitStatus.MAINTENANCE }
        val rel = GridAnalytics.reliability(grid.history, 90)
        val tiles = listOf(
            Tile("Broken", down.toString(), if (down > 0) Tone.BAD else Tone.NEUTRAL),
            Tile("In maintenance", maint.toString()),
            Tile("Dark hours", rel.lossOfLoadHours.toString(), if (rel.lossOfLoadHours > 0) Tone.BAD else Tone.GOOD, "Last 90 days"),
            Tile("Energy missed", energy(rel.energyNotServedKwh), Tone.NEUTRAL, "Last 90 days"),
        )
        return OutageReportModel(tiles, recent.map { outageRow(it, grid) }, blackouts.map { blackoutRow(it, money) }, NO_OUTAGES)
    }

    // ----- prices -----

    fun priceHistory(grid: Grid, days: Int, money: (Money) -> String): PriceHistoryModel {
        val window = grid.history.takeLast(days)
        val stats = GridAnalytics.priceStats(grid.history, days, grid.params.priceCap) ?: return PriceHistoryModel(emptyList(), emptyList(), emptyList(), emptyList(), NO_PRICES)
        val tiles = listOf(
            Tile("Average", perMwh(stats.mean, money)),
            Tile("Lowest", perMwh(stats.lowest, money)),
            Tile("Highest", perMwh(stats.highest, money), priceTone(stats.highest, grid.params.priceCap)),
            Tile("Swing", Formatting.percentPpm(stats.volatilityPpm), note = "Spread over average"),
            Tile("Trend", Formatting.signedPercentPpm(stats.trendPpm), if (stats.trendPpm > 100_000L) Tone.WARN else Tone.NEUTRAL),
            Tile("Scarcity days", stats.spikeDays.toString(), if (stats.spikeDays > 0) Tone.WARN else Tone.NEUTRAL),
        )
        val curve = GridAnalytics.loadDurationPoints(grid.lastDay.map { it.demandKw }, 5).map { Formatting.percentPpm(it.sharePpm) + " of hours" to power(it.demandKw) + " or more" }
        return PriceHistoryModel(tiles, window.map { it.averagePrice.wholeUnits() }, window.map { "Day ${it.tick}" }, curve, NO_PRICES)
    }

}
