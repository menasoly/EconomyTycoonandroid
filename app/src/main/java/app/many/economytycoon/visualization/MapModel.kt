package app.many.economytycoon.visualization

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Economy
import app.many.economytycoon.world.WorldMap

enum class MarkerKind { REGION, CITY, FACILITY, DEPOSIT, WAREHOUSE }

/** Something drawn on the map. [id] identifies it within its [kind]; [weight] scales its symbol. */
data class MapMarker(
    val kind: MarkerKind,
    val id: Long,
    val name: String,
    val x: Double,
    val y: Double,
    val weight: Double,
    /** 0..1 for colouring (load, happiness, grade...), or null when the marker has a fixed colour. */
    val heat: Double? = null,
    val detail: String = "",
    val owned: Boolean = false,
)

data class MapRoute(val fromX: Double, val fromY: Double, val toX: Double, val toY: Double, val mode: TransportMode, val loadPpm: Long, val open: Boolean)

/** What to show: layers can be switched off on the map screen. */
data class MapLayers(
    val regions: Boolean = true,
    val cities: Boolean = true,
    val facilities: Boolean = true,
    val deposits: Boolean = false,
    val warehouses: Boolean = true,
    val routes: Boolean = true,
    val modes: Set<TransportMode> = TransportMode.values().toSet(),
)

class WorldMapModel(val size: Double, val markers: List<MapMarker>, val routes: List<MapRoute>) {

    fun of(kind: MarkerKind): List<MapMarker> = markers.filter { it.kind == kind }

    /** Markers worth drawing at the given zoom: cities appear as you zoom in, so the overview stays readable. */
    fun visibleAt(zoom: Double, layers: MapLayers): List<MapMarker> = markers.filter { m ->
        when (m.kind) {
            MarkerKind.REGION -> layers.regions && zoom < 4.0
            MarkerKind.CITY -> layers.cities && (zoom >= 1.5 || m.weight >= 0.5)
            MarkerKind.FACILITY -> layers.facilities && m.owned
            MarkerKind.DEPOSIT -> layers.deposits && zoom >= 2.0
            MarkerKind.WAREHOUSE -> layers.warehouses && m.owned
        }
    }

    fun routesFor(layers: MapLayers): List<MapRoute> = if (!layers.routes) emptyList() else routes.filter { it.mode in layers.modes }

    companion object {
        fun build(e: Economy, viewer: CompanyId?): WorldMapModel {
            val markers = ArrayList<MapMarker>()
            val maxArea = e.world.regions().maxOfOrNull { it.area }?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
            for (r in e.world.regions().sortedBy { it.id }) {
                val pop = e.world.citiesIn(r.id).sumOf { e.world.population(it.id).total }
                markers += MapMarker(MarkerKind.REGION, r.id.value, r.name, r.center.x.toDouble(), r.center.y.toDouble(), r.area / maxArea, e.grid.loadPpm(r.id) / 1_000_000.0, "${e.world.country(r.country).name} · $pop people")
            }
            val maxPop = e.world.populations().maxOfOrNull { it.total }?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
            for (c in e.world.cities().sortedBy { it.id }) {
                val p = e.world.population(c.id)
                markers += MapMarker(MarkerKind.CITY, c.id.value, c.name, c.location.x.toDouble(), c.location.y.toDouble(), p.total / maxPop, p.happinessPpm / 1_000_000.0, "${p.total} people")
            }
            if (viewer != null) {
                for (f in e.production.facilitiesOf(viewer)) {
                    val city = f.city?.let { e.world.cityOrNull(it) }
                    val at = city?.location ?: e.world.region(f.region).center
                    markers += MapMarker(MarkerKind.FACILITY, f.id.value, f.name.ifBlank { f.type.key }, at.x.toDouble(), at.y.toDouble(), 0.5, f.conditionPpm / 1_000_000.0, f.type.key, owned = true)
                }
                for (w in e.logistics.warehousesOf(viewer)) {
                    val at = e.world.city(w.city).location
                    markers += MapMarker(MarkerKind.WAREHOUSE, w.id.value, "Warehouse", at.x.toDouble(), at.y.toDouble(), 0.4, null, "", owned = true)
                }
            }
            for (d in e.world.deposits.all()) {
                val mine = viewer != null && d.owner == viewer
                if (d.exhausted || !(d.surveyed || mine)) continue
                markers += MapMarker(MarkerKind.DEPOSIT, d.id.value, d.good.key, d.location.x.toDouble(), d.location.y.toDouble(), 0.3, (d.gradePpm / 1_400_000.0).coerceIn(0.0, 1.0), d.good.key, owned = mine)
            }
            val routes = e.world.network.all().map { s ->
                val a = e.world.city(s.a).location; val b = e.world.city(s.b).location
                MapRoute(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(), s.mode, loadPpm(s), s.isOpen(e.tick.value))
            }
            return WorldMapModel(WorldMap.SIZE.toDouble(), markers, routes)
        }

        private fun loadPpm(s: app.many.economytycoon.world.Segment): Long {
            val cap = s.effectiveCapacity.raw
            return if (cap <= 0L) 1_000_000L else (s.load.raw * 1_000_000L / cap).coerceIn(0L, 1_000_000L)
        }
    }
}
