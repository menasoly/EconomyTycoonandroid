package app.many.economytycoon.visualization

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapModelTest {
    private val economy by lazy { SimTestSupport.sim().economy }

    @Test fun everyRegionAndCityBecomesAMarker() {
        val m = WorldMapModel.build(economy, null)
        assertEquals(economy.world.regions().size, m.of(MarkerKind.REGION).size)
        assertEquals(economy.world.cities().size, m.of(MarkerKind.CITY).size)
        assertTrue(m.size > 0.0)
    }

    @Test fun markerWeightsAndHeatStayInRange() {
        val m = WorldMapModel.build(economy, null)
        for (k in m.markers) {
            assertTrue(k.name, k.weight in 0.0..1.0)
            val h = k.heat
            if (h != null) assertTrue(k.name, h in 0.0..1.0)
        }
    }

    @Test fun withoutAViewerNothingIsOwned() {
        val m = WorldMapModel.build(economy, null)
        assertTrue(m.of(MarkerKind.FACILITY).isEmpty())
        assertTrue(m.of(MarkerKind.WAREHOUSE).isEmpty())
        assertTrue(m.markers.none { it.owned })
    }

    @Test fun onlySurveyedDepositsAreRevealed() {
        val m = WorldMapModel.build(economy, null)
        val revealed = economy.world.deposits.all().count { !it.exhausted && it.surveyed }
        assertEquals(revealed, m.of(MarkerKind.DEPOSIT).size)
    }

    @Test fun zoomRevealsCitiesAndHidesRegions() {
        val m = WorldMapModel.build(economy, null)
        val layers = MapLayers()
        val far = m.visibleAt(1.0, layers)
        val near = m.visibleAt(5.0, layers)
        assertTrue(far.any { it.kind == MarkerKind.REGION })
        assertTrue(near.none { it.kind == MarkerKind.REGION })
        assertTrue(near.count { it.kind == MarkerKind.CITY } >= far.count { it.kind == MarkerKind.CITY })
        assertEquals(economy.world.cities().size, near.count { it.kind == MarkerKind.CITY })
    }

    @Test fun switchedOffLayersAreHidden() {
        val m = WorldMapModel.build(economy, null)
        val none = m.visibleAt(5.0, MapLayers(regions = false, cities = false, facilities = false, deposits = false, warehouses = false))
        assertTrue(none.isEmpty())
    }

    @Test fun routeLayerAndModeFiltersApply() {
        val m = WorldMapModel.build(economy, null)
        assertFalse(m.routes.isEmpty())
        assertEquals(m.routes.size, m.routesFor(MapLayers()).size)
        assertTrue(m.routesFor(MapLayers(routes = false)).isEmpty())
        val road = m.routesFor(MapLayers(modes = setOf(TransportMode.ROAD)))
        assertTrue(road.all { it.mode == TransportMode.ROAD })
        assertTrue(road.size <= m.routes.size)
        assertTrue(m.routes.all { it.loadPpm in 0L..1_000_000L })
    }

    @Test fun aCompanyViewerSeesItsOwnFacilities() {
        val sim = SimTestSupport.sim()
        val ai = sim.economy.companies.all().first { it.kind == app.many.economytycoon.companies.CompanyKind.AI }
        repeat(30) { sim.advance() }
        val m = WorldMapModel.build(sim.economy, ai.id)
        val own = sim.economy.production.facilitiesOf(ai.id).size
        assertEquals(own, m.of(MarkerKind.FACILITY).size)
        assertTrue(m.of(MarkerKind.FACILITY).all { it.owned })
    }
}
