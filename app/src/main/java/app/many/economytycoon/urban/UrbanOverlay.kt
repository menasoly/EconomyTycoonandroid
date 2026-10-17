package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What a map overlay of a city shows. */
enum class OverlayKind(val label: String, val unit: String, val categorical: Boolean, val badWhenHigh: Boolean) {
    ZONING("Zoning", "", true, false),
    LAND_VALUE("Land value", "per sqm", false, false),
    RENT("Rent", "per sqm a month", false, true),
    VACANCY("Vacancy", "% of homes", false, false),
    ACCESSIBILITY("Accessibility", "of 100", false, false),
    POLLUTION("Pollution", "of 100", false, true),
    NOISE("Noise", "of 100", false, true),
    CRIME("Crime", "of 100", false, true),
    AMENITY("Amenities", "of 100", false, false),
    CONGESTION("Congestion", "% of capacity", false, true),
    BUILDING_AGE("Building age", "years", false, true),
    DENSITY("Density", "floor-area ratio", false, false),
    GENTRIFICATION("Displacement pressure", "of 100", false, true),
}

/** A stop on a colour scale: values at or above [value] take this colour until the next stop. Colours are 0xAARRGGBB. */
data class LegendStop(val label: String, val color: Int, val value: Long)

/** Fixed colour scales. Colours are plain ARGB integers so the drawing layer needs no conversion. */
object Ramps {
    /** Pale to deep blue for quantities where more is simply more. */
    val SEQUENTIAL: IntArray = intArrayOf(0xFFEFF3FF.toInt(), 0xFFBDD7E7.toInt(), 0xFF6BAED6.toInt(), 0xFF3182BD.toInt(), 0xFF08519C.toInt())

    /** Green through yellow to red for things that are bad when high. */
    val WARNING: IntArray = intArrayOf(0xFF1A9641.toInt(), 0xFFA6D96A.toInt(), 0xFFFFFFBF.toInt(), 0xFFFDAE61.toInt(), 0xFFD7191C.toInt())

    /** Zone colours in [ZoneUse] order. */
    val ZONES: IntArray = intArrayOf(
        0xFFF2D16B.toInt(), // residential
        0xFFD9534F.toInt(), // commercial
        0xFF8E8E93.toInt(), // industrial
        0xFFE69F66.toInt(), // mixed use
        0xFF5CB85C.toInt(), // green
        0xFF5B7DB1.toInt(), // civic
    )

    private fun channel(c: Int, shift: Int) = (c ushr shift) and 0xFF

    /** Integer interpolation between two colours; [tPpm] is 0..1,000,000. */
    fun mix(a: Int, b: Int, tPpm: Long): Int {
        val t = tPpm.coerceIn(0L, Scale.PPM)
        var out = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            val ca = channel(a, shift).toLong()
            val cb = channel(b, shift).toLong()
            out = out or ((ca + (cb - ca) * t / Scale.PPM).toInt() shl shift)
        }
        return out
    }

    /** Colour of [value] on a ramp spread evenly between [lo] and [hi]. */
    fun colorFor(value: Long, lo: Long, hi: Long, ramp: IntArray): Int {
        if (hi <= lo) return ramp[0]
        val pos = mulDiv((value - lo).coerceIn(0L, hi - lo), (ramp.size - 1).toLong() * Scale.PPM, hi - lo)
        val i = (pos / Scale.PPM).toInt().coerceAtMost(ramp.size - 2)
        return mix(ramp[i], ramp[i + 1], pos - i * Scale.PPM)
    }
}

/**
 * A grid of values with the colour scale that goes with it. For categorical overlays (zoning) each value is a category
 * index; otherwise values run from [min] to [max] and the colour of a cell comes from the ramp.
 */
class HeatGrid(
    val kind: OverlayKind,
    val width: Int,
    val height: Int,
    private val values: LongArray,
    val min: Long,
    val max: Long,
    val legend: List<LegendStop>,
    private val ramp: IntArray,
) {
    init { require(values.size == width * height) { "heat grid data does not match its size" } }

    fun valueAt(x: Int, y: Int): Long = values[y * width + x]

    fun colorAt(x: Int, y: Int): Int {
        val v = valueAt(x, y)
        return if (kind.categorical) ramp[v.toInt().coerceIn(0, ramp.size - 1)] else Ramps.colorFor(v, min, max, ramp)
    }

    /** Index of the legend entry that [valueAt] falls into. */
    fun legendIndexAt(x: Int, y: Int): Int {
        val v = valueAt(x, y)
        if (kind.categorical) return v.toInt().coerceIn(0, legend.size - 1)
        var idx = 0
        for ((i, stop) in legend.withIndex()) if (v >= stop.value) idx = i
        return idx
    }

    fun flat(): LongArray = values.copyOf()
}

enum class OverlayLineKind { LOCAL_ROAD, ARTERIAL, HIGHWAY, BUS, METRO, RAIL }

/** A road or transit line segment in cell coordinates scaled by 1000 (a cell centre is at x*1000+500). */
data class OverlayLine(
    val fromX: Int, val fromY: Int, val toX: Int, val toY: Int, val kind: OverlayLineKind, val loadPpm: Long, val active: Boolean, val label: String,
)

enum class OverlayMarkerKind { STATION, PETITION, PROJECT, EVENT }

data class OverlayMarker(val x: Int, val y: Int, val kind: OverlayMarkerKind, val label: String)

/** Everything the drawing layer needs for one overlay of one city: no drawing code lives here. */
class UrbanOverlay(
    val city: CityId,
    val kind: OverlayKind,
    val heat: HeatGrid,
    val lines: List<OverlayLine>,
    val markers: List<OverlayMarker>,
    /** Cells on a side of a district, to draw district borders. */
    val blockSize: Int,
    val districtNames: List<String>,
)

object UrbanOverlays {
    private fun scaled(c: Cell) = Cell(c.x * 1000 + 500, c.y * 1000 + 500)

    fun build(plan: CityPlan, analysis: CityAnalysis, kind: OverlayKind, params: UrbanParams, now: Tick): UrbanOverlay {
        val g = plan.geometry
        val n = g.gridSize
        val data = LongArray(n * n)
        val ext = analysis.externalities
        for (y in 0 until n) for (x in 0 until n) {
            val d = plan.districts[g.districtOfCell(x, y)]
            val c = Cell(x, y)
            data[y * n + x] = when (kind) {
                OverlayKind.ZONING -> d.zone.ordinal.toLong()
                OverlayKind.LAND_VALUE -> analysis.landValue.values[c] / 10_000L
                OverlayKind.RENT -> d.rentPerSqm.raw / 100L
                OverlayKind.VACANCY -> d.vacancyPpm / 10_000L
                OverlayKind.ACCESSIBILITY -> analysis.accessibility[c] / 10L
                OverlayKind.POLLUTION -> ext.pollution[c] / 10L
                OverlayKind.NOISE -> ext.noise[c] / 10L
                OverlayKind.CRIME -> ext.crime[c] / 10L
                OverlayKind.AMENITY -> ext.amenity[c] / 10L
                OverlayKind.CONGESTION -> d.trafficLoadPermille / 10L
                OverlayKind.BUILDING_AGE -> d.stock.averageAgeYears.toLong()
                OverlayKind.DENSITY -> d.farMilli.toLong()
                OverlayKind.GENTRIFICATION -> (Gentrification.pressurePpm(d) / 2_000L).coerceAtMost(100L)
            }
        }
        return UrbanOverlay(plan.city, kind, heat(kind, n, data), roadsAndLines(plan), markers(plan, now), g.blockSize, plan.districts.map { it.name })
    }

    /** One overlay per kind, in enum order. */
    fun buildAll(plan: CityPlan, analysis: CityAnalysis, params: UrbanParams, now: Tick): List<UrbanOverlay> =
        OverlayKind.values().map { build(plan, analysis, it, params, now) }

    private fun heat(kind: OverlayKind, n: Int, data: LongArray): HeatGrid {
        if (kind.categorical) {
            val legend = ZoneUse.values().map { LegendStop(it.label, Ramps.ZONES[it.ordinal], it.ordinal.toLong()) }
            return HeatGrid(kind, n, n, data, 0L, (ZoneUse.values().size - 1).toLong(), legend, Ramps.ZONES)
        }
        val lo = data.min()
        val hi = data.max()
        val ramp = if (kind.badWhenHigh) Ramps.WARNING else Ramps.SEQUENTIAL
        val labels = listOf("Lowest", "Low", "Middle", "High", "Highest")
        val legend = labels.mapIndexed { i, label ->
            val v = lo + (hi - lo) * i / (labels.size - 1)
            LegendStop("$label (${v}${if (kind.unit.isEmpty()) "" else " ${kind.unit}"})", ramp[i], v)
        }
        return HeatGrid(kind, n, n, data, lo, hi, legend, ramp)
    }

    private fun roadsAndLines(plan: CityPlan): List<OverlayLine> {
        val out = ArrayList<OverlayLine>()
        for (r in plan.network.roads()) {
            val a = scaled(plan.geometry.centre(r.a))
            val b = scaled(plan.geometry.centre(r.b))
            val kind = when (r.roadClass) {
                RoadClass.LOCAL -> OverlayLineKind.LOCAL_ROAD
                RoadClass.ARTERIAL -> OverlayLineKind.ARTERIAL
                RoadClass.HIGHWAY -> OverlayLineKind.HIGHWAY
            }
            out += OverlayLine(a.x, a.y, b.x, b.y, kind, r.volumeCapacityPpm.coerceAtMost(Scale.PPM), true, plan.districts[r.a].name + " - " + plan.districts[r.b].name)
        }
        for (l in plan.network.lines()) {
            val kind = when (l.mode) { TransitMode.BUS -> OverlayLineKind.BUS; TransitMode.METRO -> OverlayLineKind.METRO; TransitMode.RAIL -> OverlayLineKind.RAIL }
            for ((x, y) in l.stops.zipWithNext()) {
                val a = scaled(plan.geometry.centre(x))
                val b = scaled(plan.geometry.centre(y))
                out += OverlayLine(a.x, a.y, b.x, b.y, kind, l.loadRatioPpm.coerceAtMost(Scale.PPM), l.isActive, l.name)
            }
        }
        return out
    }

    private fun markers(plan: CityPlan, now: Tick): List<OverlayMarker> {
        val out = ArrayList<OverlayMarker>()
        val seen = HashSet<Long>()
        for (l in plan.network.activeLines()) {
            if (l.mode == TransitMode.BUS) continue
            for (s in l.stops) if (seen.add(l.id.value * 64L + s)) {
                val c = scaled(plan.geometry.centre(s))
                out += OverlayMarker(c.x, c.y, OverlayMarkerKind.STATION, "${l.name}: ${plan.districts[s].name}")
            }
        }
        for (p in plan.petitions.open()) {
            val c = scaled(plan.geometry.centre(p.district))
            out += OverlayMarker(c.x - 250, c.y - 250, OverlayMarkerKind.PETITION, "${p.kind.label}: ${plan.districts[p.district].name}")
        }
        for (p in plan.projects.running()) {
            val d = p.district ?: continue
            val c = scaled(plan.geometry.centre(d))
            out += OverlayMarker(c.x + 250, c.y - 250, OverlayMarkerKind.PROJECT, p.name)
        }
        for (e in plan.events.active(now)) {
            if (e.district < 0) continue
            val c = scaled(plan.geometry.centre(e.district))
            out += OverlayMarker(c.x + 250, c.y + 250, OverlayMarkerKind.EVENT, e.kind.label)
        }
        return out
    }
}
