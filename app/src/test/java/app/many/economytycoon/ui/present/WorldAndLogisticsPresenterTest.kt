package app.many.economytycoon.ui.present

import app.many.economytycoon.core.RegionId
import app.many.economytycoon.logistics.ShipmentStatus
import app.many.economytycoon.logistics.VehicleStatus
import app.many.economytycoon.macro.CyclePhase
import app.many.economytycoon.content.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldAndLogisticsPresenterTest {
    private val session by lazy { PresentSupport.session("Atlas Works").also { s -> repeat(35) { s.step() } } }

    @Test fun theWorldListsEveryCountryAndRegion() {
        val m = WorldPresenter.build(session)
        assertEquals(session.economy.world.countries().size, m.countries.size)
        assertEquals(session.economy.world.regions().size, m.regions.size)
        assertTrue(m.countries.all { it.facts.size == 12 })
        assertTrue(m.regions.all { it.happiness in 0.0..1.0 })
    }

    @Test fun regionDetailBreaksDownPeopleAndCities() {
        val r = session.economy.world.regions().first().id
        val d = WorldPresenter.region(session, r)!!
        assertEquals(6, d.facts.size)
        assertTrue(d.cities.isNotEmpty())
        assertEquals(3, d.ageBands.size)
        assertEquals(d.cities.sortedByDescending { it.population.replace(",", "").toLong() }.map { it.name }, d.cities.map { it.name })
    }

    @Test fun unknownRegionsHaveNoDetail() {
        assertNull(WorldPresenter.region(session, RegionId(9999)))
    }

    @Test fun macroSeriesFollowTheHistory() {
        val c = session.economy.world.countries().first().id
        val m = WorldPresenter.macro(session, c)
        assertEquals(session.reports.world.macroHistory(c).size, m.gdp.points.size)
        assertEquals(m.inflation.points.size, m.unemployment.points.size)
    }

    @Test fun phasesHaveLabels() {
        for (p in CyclePhase.values()) assertTrue(WorldPresenter.phaseLabel(p).first.isNotBlank())
        assertEquals("Not yet measured", WorldPresenter.phaseLabel(null).first)
    }

    @Test fun anEmptyFleetShowsChoicesAndZeroTiles() {
        val m = LogisticsPresenter.build(session)!!
        assertTrue(m.fleet.isEmpty() && m.warehouses.isEmpty() && m.shipments.isEmpty())
        assertEquals(session.economy.content.allVehicleTypes.size, m.choices.size)
        assertEquals(4, m.tiles.size)
        assertEquals(m.choices.map { it.locked }.sorted(), m.choices.map { it.locked })
    }

    @Test fun buyingAVehicleAddsItToTheFleet() {
        val s = PresentSupport.session("Haulage Works", 8L)
        val city = s.economy.world.cities().first().id
        val option = s.reports.logistics.vehicleOptions(s.selectedCompany!!).first { !it.locked }
        val r = s.issueNow(app.many.economytycoon.simulation.Command.BuyVehicle(s.selectedCompany!!, option.type, city))
        assertTrue(r.toString(), r.isOk)
        val m = LogisticsPresenter.build(s)!!
        assertEquals(1, m.fleet.size)
        assertEquals("Idle", m.fleet.single().status)
        assertTrue(m.fleet.single().condition in 0.0..1.0)
    }

    @Test fun labelsCoverEveryEnumValue() {
        for (m in TransportMode.values()) assertTrue(LogisticsPresenter.modeLabel(m).isNotBlank())
        for (st in ShipmentStatus.values()) assertTrue(LogisticsPresenter.shipmentStatus(st).first.isNotBlank())
        for (st in VehicleStatus.values()) assertTrue(LogisticsPresenter.vehicleStatus(st).first.isNotBlank())
    }
}
