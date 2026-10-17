package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.urban.LegendStop
import app.many.economytycoon.urban.OverlayKind
import app.many.economytycoon.urban.OverlayLineKind
import app.many.economytycoon.urban.OverlayMarkerKind
import app.many.economytycoon.urban.UrbanOverlay

data class OverlayChoice(val kind: OverlayKind, val label: String, val selected: Boolean)

data class LegendRow(val label: String, val color: Int)

data class CellReadout(val x: Int, val y: Int, val district: String, val title: String, val value: String, val legendIndex: Int)

data class MapMarkerRow(val label: String, val kind: String, val x: Int, val y: Int)

data class UrbanMapModel(
    val title: String,
    val size: Int,
    val colors: List<Int>,
    val legend: List<LegendRow>,
    val choices: List<OverlayChoice>,
    val markers: List<MapMarkerRow>,
    val lineSummary: List<Fact>,
    val summary: String,
)

/** Turns an overlay into what the map screen draws and says; the colours are already decided by the overlay. */
object UrbanMapPresenter {
    fun choices(selected: OverlayKind): List<OverlayChoice> = OverlayKind.values().map { OverlayChoice(it, it.label, it == selected) }

    fun legend(stops: List<LegendStop>): List<LegendRow> = stops.map { LegendRow(it.label, it.color) }

    fun markerKind(k: OverlayMarkerKind): String = when (k) {
        OverlayMarkerKind.STATION -> "Station"
        OverlayMarkerKind.PETITION -> "Petition"
        OverlayMarkerKind.PROJECT -> "Works"
        OverlayMarkerKind.EVENT -> "Event"
    }

    fun readout(o: UrbanOverlay, x: Int, y: Int): CellReadout? {
        val n = o.heat.width
        if (x !in 0 until n || y !in 0 until o.heat.height) return null
        val district = o.districtNames.getOrNull((y / o.blockSize) * (n / o.blockSize) + x / o.blockSize) ?: "Unknown"
        val v = o.heat.valueAt(x, y)
        val idx = o.heat.legendIndexAt(x, y)
        val value = if (o.kind.categorical) o.heat.legend[idx].label else "$v ${o.kind.unit}".trim()
        return CellReadout(x, y, district, o.kind.label, value, idx)
    }

    fun build(o: UrbanOverlay, cityName: String): UrbanMapModel {
        val n = o.heat.width
        val colors = ArrayList<Int>(n * o.heat.height)
        for (y in 0 until o.heat.height) for (x in 0 until n) colors += o.heat.colorAt(x, y)
        val byKind = o.lines.groupBy { it.kind }
        val lineSummary = OverlayLineKind.values().filter { byKind.containsKey(it) }.map { k ->
            val list = byKind.getValue(k)
            val busy = list.count { it.loadPpm >= 900_000L }
            Fact(k.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, "${list.size} links", if (busy > 0) Tone.WARN else Tone.NEUTRAL, if (busy > 0) "$busy nearly full" else null)
        }
        val summary = if (o.kind.categorical) "Each colour is a zone." else "Ranges from ${o.heat.min} to ${o.heat.max} ${o.kind.unit}.".replace("  ", " ")
        return UrbanMapModel(
            "$cityName: ${o.kind.label}", n, colors, legend(o.heat.legend), choices(o.kind),
            o.markers.map { MapMarkerRow(it.label, markerKind(it.kind), it.x, it.y) }, lineSummary, summary,
        )
    }

    fun nextKind(current: OverlayKind, forward: Boolean): OverlayKind {
        val all = OverlayKind.values()
        return all[(current.ordinal + (if (forward) 1 else all.size - 1)) % all.size]
    }
}
