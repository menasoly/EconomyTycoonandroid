package app.many.economytycoon.session

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsAndFacilityTest {
    private val sim = SimTestSupport.sim(91L, 5).also { it.advance(90) }
    private val e = sim.economy
    private val logistics = LogisticsReports(e)
    private val company = CompanyReports(e)
    private val details = FacilityDetails(e, company, PeopleReports(e))

    @Test fun fleetRowsMatchOwnedVehicles() {
        for (c in e.companies.active()) {
            assertEquals(e.logistics.vehiclesOf(c.id).size, logistics.fleet(c.id).size)
            assertEquals(e.logistics.warehousesOf(c.id).size, logistics.warehouses(c.id).size)
        }
    }

    @Test fun vehicleOptionsListOpenOnesFirst() {
        val c = e.companies.active().first()
        val options = logistics.vehicleOptions(c.id)
        assertEquals(e.content.allVehicleTypes.size, options.size)
        assertEquals(options.map { it.locked }.sortedBy { it }, options.map { it.locked })
    }

    @Test fun stockPointsCoverFacilitiesAndWarehouses() {
        val c = e.companies.active().first { e.production.facilitiesOf(it.id).isNotEmpty() }
        val points = logistics.stockPoints(c.id)
        assertEquals(e.production.facilitiesOf(c.id).size + e.logistics.warehousesOf(c.id).size, points.size)
        assertEquals(points.map { it.ref }.sorted(), points.map { it.ref })
    }

    @Test fun shipmentRowsAreOnlyActiveByDefault() {
        for (c in e.companies.active()) {
            assertTrue(logistics.shipments(c.id).all { it.status == app.many.economytycoon.logistics.ShipmentStatus.IN_TRANSIT || it.status == app.many.economytycoon.logistics.ShipmentStatus.AT_DESTINATION })
            assertTrue(logistics.shipments(c.id, true).size >= logistics.shipments(c.id).size)
        }
    }

    @Test fun aQuoteBetweenTwoCitiesOfOneRegionExistsOrIsNull() {
        val cities = e.world.cities()
        val q = logistics.quote(cities[0].id, cities[1].id, e.content.allGoods.first().id, Quantity.units(10))
        if (q != null) assertTrue(q.total.raw >= 0 && q.distance >= 0)
    }

    @Test fun facilityDetailListsLinesStockAndStaff() {
        val owner = e.companies.active().first { e.production.facilitiesOf(it.id).isNotEmpty() }
        val f = e.production.facilitiesOf(owner.id).first()
        val d = details.detail(f.id)!!
        assertEquals(f.id, d.row.id)
        assertEquals(f.lines.size, d.lines.size)
        assertTrue(d.storageUsedPpm in 0..1_000_000)
        assertTrue(d.staff.all { it.facility == f.id })
        for (l in d.lines) if (l.recipe != null) assertNotNull(l.recipeName)
    }

    @Test fun unknownOrDemolishedFacilitiesHaveNoDetail() {
        assertNull(details.detail(FacilityId(999_999)))
    }
}
