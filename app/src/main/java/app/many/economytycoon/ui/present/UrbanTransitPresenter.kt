package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.urban.CityView
import app.many.economytycoon.urban.LevelOfService
import app.many.economytycoon.urban.LineStatus
import app.many.economytycoon.urban.LineView
import app.many.economytycoon.urban.RoadView
import app.many.economytycoon.urban.TransitLineId
import app.many.economytycoon.urban.TransitNetwork
import app.many.economytycoon.urban.UrbanCommand

data class LineRow(
    val id: TransitLineId,
    val name: String,
    val mode: String,
    val route: String,
    val facts: List<Fact>,
    val status: String,
    val tone: Tone,
    val canSpeedUp: Boolean,
    val canSlowDown: Boolean,
    val canToggle: Boolean,
    val toggleLabel: String,
)

data class RoadRow(val name: String, val roadClass: String, val load: String, val service: String, val tone: Tone, val toll: String?)

data class UrbanTransitModel(val tiles: List<Tile>, val lines: List<LineRow>, val roads: List<RoadRow>, val note: String)

/** Roads, lines and how people get to work. */
object UrbanTransitPresenter {
    fun statusLabel(s: LineStatus): String = when (s) {
        LineStatus.PLANNED -> "Planned"
        LineStatus.UNDER_CONSTRUCTION -> "Being built"
        LineStatus.ACTIVE -> "Running"
        LineStatus.SUSPENDED -> "Suspended"
    }

    fun statusTone(s: LineStatus): Tone = when (s) {
        LineStatus.PLANNED -> Tone.MUTED
        LineStatus.UNDER_CONSTRUCTION -> Tone.INFO
        LineStatus.ACTIVE -> Tone.GOOD
        LineStatus.SUSPENDED -> Tone.WARN
    }

    fun serviceTone(l: LevelOfService): Tone = when (l) {
        LevelOfService.A, LevelOfService.B -> Tone.GOOD
        LevelOfService.C -> Tone.NEUTRAL
        LevelOfService.D -> Tone.WARN
        LevelOfService.E, LevelOfService.F -> Tone.BAD
    }

    fun headwayText(seconds: Int): String = if (seconds % 60 == 0) "every ${seconds / 60} min" else "every ${seconds / 60} min ${seconds % 60} s"

    fun lineRow(l: LineView, money: MoneyText): LineRow {
        val running = l.status == LineStatus.ACTIVE
        val facts = listOf(
            Fact("Service", headwayText(l.headwaySeconds)),
            Fact("Fare", money(l.fare)),
            Fact("Length", "${l.lengthMeters / 100 / 10}.${l.lengthMeters / 100 % 10} km"),
            Fact("Busiest stretch", Formatting.percentPpm(l.loadRatioPpm), if (l.loadRatioPpm > 900_000L) Tone.BAD else if (l.loadRatioPpm < 120_000L && running) Tone.MUTED else Tone.NEUTRAL),
            Fact("Riders a month", Formatting.grouped(l.monthlyRiders)),
            Fact("Cost a month", if (l.concession != null) "paid by the concessionaire" else money(l.monthlyCost)),
        )
        return LineRow(
            l.id, l.name, l.mode.label, l.stopNames.joinToString(" - "), facts, statusLabel(l.status), statusTone(l.status),
            canSpeedUp = running && l.headwaySeconds > TransitNetwork.MIN_HEADWAY,
            canSlowDown = running && l.headwaySeconds < TransitNetwork.MAX_HEADWAY,
            canToggle = running || l.status == LineStatus.SUSPENDED,
            toggleLabel = if (running) "Suspend" else "Resume",
        )
    }

    fun roadRow(r: RoadView, money: MoneyText) = RoadRow(
        "${r.fromName} - ${r.toName}", "${r.roadClass.label}, ${r.lanes} lanes", "${r.flowVph} of ${r.capacityVph} an hour",
        "${r.levelOfService.name}: ${r.levelOfService.label}", serviceTone(r.levelOfService), if (r.toll.isPositive) money(r.toll) else null,
    )

    fun build(c: CityView, money: MoneyText = { Formatting.compactMoney(it) }): UrbanTransitModel {
        val tiles = listOf(
            Tile("Average commute", "${c.averageCommuteSeconds / 60} min", if (c.averageCommuteSeconds > 2_400L) Tone.WARN else Tone.NEUTRAL, "${Formatting.grouped(c.commuters)} commuters"),
            Tile("By car", Formatting.percentPpm(c.carSharePpm)),
            Tile("By public transport", Formatting.percentPpm(c.transitSharePpm), if (c.transitSharePpm > 150_000L) Tone.GOOD else Tone.NEUTRAL),
            Tile("On foot", Formatting.percentPpm(c.walkSharePpm)),
            Tile("Road delay", Formatting.percentPpm(c.congestionPpm), if (c.congestionPpm > 300_000L) Tone.BAD else if (c.congestionPpm > 150_000L) Tone.WARN else Tone.GOOD),
        )
        val worstFirst = c.roads.sortedWith(compareByDescending<RoadView> { it.volumeCapacityPpm }.thenBy { it.id }).take(8)
        return UrbanTransitModel(
            tiles, c.lines.map { lineRow(it, money) }, worstFirst.map { roadRow(it, money) },
            "Most commuters drive unless public transport is faster. Faster, more frequent service moves people off the roads.",
        )
    }

    fun speedUp(city: CityId, l: LineView): UrbanCommand = UrbanCommand.SetLineHeadway(city, l.id, (l.headwaySeconds * 4 / 5).coerceAtLeast(TransitNetwork.MIN_HEADWAY))
    fun slowDown(city: CityId, l: LineView): UrbanCommand = UrbanCommand.SetLineHeadway(city, l.id, (l.headwaySeconds * 5 / 4).coerceAtMost(TransitNetwork.MAX_HEADWAY))
    fun toggle(city: CityId, l: LineView): UrbanCommand = UrbanCommand.ToggleLine(city, l.id)
    fun fare(city: CityId, l: LineView, fare: Money): UrbanCommand = UrbanCommand.SetLineFare(city, l.id, fare)
}
