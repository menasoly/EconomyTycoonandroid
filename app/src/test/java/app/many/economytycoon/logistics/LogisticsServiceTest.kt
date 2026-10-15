package app.many.economytycoon.logistics

import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.logistics.LogisticsTestSupport.Rig
import app.many.economytycoon.logistics.LogisticsTestSupport.alice
import app.many.economytycoon.logistics.LogisticsTestSupport.bob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogisticsServiceTest {
    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule

    private fun setup(r: Rig, units: Long = 100, good: app.many.economytycoon.core.GoodId = r.durable): Triple<app.many.economytycoon.production.Facility, Warehouse, ShipRequest> {
        val (a, b) = r.domesticPair()
        val f = r.facilityIn(alice, a)
        r.stock(f, good, units)
        val w = r.logistics.leaseWarehouse(alice, b, Quantity.units(100_000), Tick(0)).getOrThrow()
        r.charges.clear()
        return Triple(f, w, ShipRequest(alice, alice, StockRef.facility(f.id), StockRef.warehouse(w.id), good, Quantity.units(units)))
    }

    @Test fun quotesGrowWithQuantityAndDistance() {
        val r = Rig()
        val (a, b) = r.domesticPair()
        val small = r.logistics.quote(a, b, r.durable, Quantity.units(10), Tick(0))!!
        val big = r.logistics.quote(a, b, r.durable, Quantity.units(100), Tick(0))!!
        assertTrue(big.freight.raw > small.freight.raw * 9)
        assertTrue(small.ticks >= 1 && small.distance > 0)
        assertEquals(Money.ZERO, small.tariff)
    }

    @Test fun shipmentArrivesAndMovesStockAndValue() {
        val r = Rig(); val (f, w, req) = setup(r)
        val s = r.logistics.ship(req, Tick(0)).getOrThrow()
        assertEquals(Quantity.ZERO, f.inventory.quantity(r.durable))
        assertEquals(Money.units(1000), s.costValue)
        assertEquals(Money.units(1000), r.logistics.inTransitValue(alice))
        assertTrue(r.charges.any { it.memo == LogisticsMemo.FREIGHT && it.company == alice })
        var delivered = false
        for (i in 1..200) { val rep = r.step(); if (rep.deliveries.isNotEmpty()) { delivered = true; break } }
        assertTrue(delivered)
        assertEquals(ShipmentStatus.DELIVERED, s.status)
        assertTrue(w.inventory.quantity(r.durable).raw > 0)
        assertEquals(Money.ZERO, r.logistics.inTransitValue(alice))
        assertTrue(w.inventory.totalValue().raw > 0)
    }

    @Test fun invoiceValueReplacesCarryingCostAtDelivery() {
        val r = Rig(); val (a, b) = r.domesticPair()
        val f = r.facilityIn(alice, a); r.stock(f, r.durable, 10, 10)
        val g = r.facilityIn(bob, b)
        val req = ShipRequest(alice, bob, StockRef.facility(f.id), StockRef.facility(g.id), r.durable, Quantity.units(10), invoiceValue = Money.units(500))
        val s = r.logistics.ship(req, Tick(0)).getOrThrow()
        for (i in 1..200) { if (r.step().deliveries.isNotEmpty()) break }
        assertEquals(ShipmentStatus.DELIVERED, s.status)
        assertEquals(Money.units(500), g.inventory.totalValue())
    }

    @Test fun validationCatchesBadRequests() {
        val r = Rig(); val (f, w, req) = setup(r)
        assertEquals(Rules.NOT_OWNER, rule(r.logistics.ship(ShipRequest(bob, alice, req.from, req.to, req.good, req.quantity), Tick(0))))
        assertEquals(Rules.NOT_OWNER, rule(r.logistics.ship(ShipRequest(alice, bob, req.from, req.to, req.good, req.quantity), Tick(0))))
        assertEquals(Rules.INSUFFICIENT_STOCK, rule(r.logistics.ship(ShipRequest(alice, alice, req.from, req.to, req.good, Quantity.units(1000)), Tick(0))))
        assertEquals(Rules.INVALID_QUANTITY, rule(r.logistics.ship(ShipRequest(alice, alice, req.from, req.to, req.good, Quantity.ZERO), Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.logistics.ship(ShipRequest(alice, alice, req.from, req.from, req.good, req.quantity), Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.logistics.ship(ShipRequest(alice, alice, req.from, StockRef.warehouse(app.many.economytycoon.core.WarehouseId(999)), req.good, req.quantity), Tick(0))))
        assertEquals(Rules.NO_ROUTE, rule(r.logistics.ship(ShipRequest(alice, alice, req.from, req.to, req.good, req.quantity, modes = emptySet()), Tick(0))))
        assertEquals(Quantity.units(100), f.inventory.quantity(r.durable))
        assertEquals(Quantity.ZERO, w.inventory.quantity(r.durable))
    }

    @Test fun unaffordableFreightLeavesStockAlone() {
        val r = Rig(); val (f, _, req) = setup(r)
        r.funds = false
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(r.logistics.ship(req, Tick(0))))
        assertEquals(Quantity.units(100), f.inventory.quantity(r.durable))
        assertTrue(r.logistics.shipments().isEmpty())
    }

    @Test fun crossingBordersAddsTariffAndCustoms() {
        val r = Rig()
        val pair = r.foreignPair() ?: return
        val (a, b) = pair
        val f = r.facilityIn(alice, a); r.stock(f, r.durable, 100, 50)
        val w = r.logistics.leaseWarehouse(alice, b, Quantity.units(100_000), Tick(0)).getOrThrow()
        r.charges.clear()
        val q = r.logistics.quote(a, b, r.durable, Quantity.units(100), Tick(0), goodsValue = Money.units(5_000))!!
        assertTrue(q.crossesBorder)
        val expectedTariff = Money.units(5_000).raw * r.world.country(r.world.countryOfCity(b).id).tariffPpm / 1_000_000L
        assertTrue(Math.abs(expectedTariff - q.tariff.raw) <= 1L)
        r.logistics.ship(ShipRequest(alice, alice, StockRef.facility(f.id), StockRef.warehouse(w.id), r.durable, Quantity.units(100)), Tick(0)).getOrThrow()
        if (expectedTariff > 0) assertTrue(r.charges.any { it.memo.startsWith(LogisticsMemo.TARIFF + ":") })
    }

    @Test fun ownFleetShipsSingleModeRoutesAndBecomesBusy() {
        val r = Rig(); val (a, b) = r.domesticPair()
        val f = r.facilityIn(alice, a); r.stock(f, r.durable, 100)
        val w = r.logistics.leaseWarehouse(alice, b, Quantity.units(100_000), Tick(0)).getOrThrow()
        val route = r.world.network.route(a, b, 0, setOf(TransportMode.ROAD))
        if (route == null) return
        val truck = r.logistics.buyVehicle(alice, VehicleTypeId("truck"), a, Tick(0)).getOrThrow()
        fun req(units: Long) = ShipRequest(alice, alice, StockRef.facility(f.id), StockRef.warehouse(w.id), r.durable, Quantity.units(units), modes = setOf(TransportMode.ROAD), useFleet = true)
        val first = r.logistics.ship(req(30), Tick(0)).getOrThrow()
        assertEquals(truck.id, first.vehicle)
        assertEquals(VehicleStatus.ON_TRIP, truck.status)
        assertEquals(Rules.WRONG_STATE, rule(r.logistics.ship(req(30), Tick(0))))
        for (i in 1..300) { r.step(); if (truck.status == VehicleStatus.IDLE) break }
        assertEquals(VehicleStatus.IDLE, truck.status)
        assertNotNull(r.logistics.ship(req(30), Tick(r.tick)).getOrNull())
    }

    @Test fun vehiclesNeedCapacityForTheLoad() {
        val r = Rig(); val (a, b) = r.domesticPair()
        val f = r.facilityIn(alice, a); r.stock(f, r.durable, 100)
        val w = r.logistics.leaseWarehouse(alice, b, Quantity.units(100_000), Tick(0)).getOrThrow()
        r.logistics.buyVehicle(alice, VehicleTypeId("van"), a, Tick(0)).getOrThrow()
        val over = ShipRequest(alice, alice, StockRef.facility(f.id), StockRef.warehouse(w.id), r.durable, Quantity.units(100), modes = setOf(TransportMode.ROAD), useFleet = true)
        assertTrue(rule(r.logistics.ship(over, Tick(0))) in setOf(Rules.WRONG_STATE, Rules.NO_ROUTE))
    }

    @Test fun sellingAVehicleReturnsConditionAdjustedValue() {
        val r = Rig(); val a = r.city(0)
        val v = r.logistics.buyVehicle(alice, VehicleTypeId("truck"), a, Tick(0)).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(r.logistics.sellVehicle(bob, v.id)))
        val back = r.logistics.sellVehicle(alice, v.id).getOrThrow()
        assertEquals(Money.units(54_000), back)
        assertEquals(Rules.WRONG_STATE, rule(r.logistics.sellVehicle(alice, v.id)))
        assertTrue(r.logistics.vehiclesOf(alice).isEmpty())
    }

    @Test fun perishableCargoSpoilsOnTheRoad() {
        val r = Rig(); val (f, w, req0) = setup(r, 1000, r.perishable)
        val s = r.logistics.ship(req0, Tick(0)).getOrThrow()
        var spoiled = Money.ZERO
        for (i in 1..200) { val rep = r.step(); spoiled += rep.spoiledValue; if (!s.isActive) break }
        assertTrue(spoiled.raw > 0 || s.status == ShipmentStatus.LOST)
        if (s.status == ShipmentStatus.DELIVERED) assertTrue(w.inventory.quantity(r.perishable) < Quantity.units(1000))
        assertEquals(Quantity.ZERO, f.inventory.quantity(r.perishable))
    }

    @Test fun lostShipmentsAreWrittenOff() {
        val r = Rig(params = LogisticsParams(lossPpmRoad = 1_000_000, lossPpmRail = 1_000_000, lossPpmSea = 1_000_000, lossPpmAir = 1_000_000))
        val (_, _, req) = setup(r)
        val s = r.logistics.ship(req, Tick(0)).getOrThrow()
        val rep = r.step()
        assertEquals(ShipmentStatus.LOST, s.status)
        assertEquals(Money.units(1000), rep.lostValue)
        assertEquals(Money.units(1000), rep.writeOff)
        assertEquals(Money.ZERO, r.logistics.inTransitValue(alice))
    }

    @Test fun fullDestinationsHoldThenReturnTheGoods() {
        val r = Rig(); val (f, w, req) = setup(r, 100)
        w.inventory.capacity = Quantity.units(10)
        val s = r.logistics.ship(req, Tick(0)).getOrThrow()
        var returned = false
        for (i in 1..400) { val rep = r.step(); if (rep.returned.isNotEmpty()) { returned = true; break } }
        assertTrue(returned || s.status == ShipmentStatus.LOST)
        if (returned) {
            assertEquals(ShipmentStatus.RETURNED, s.status)
            assertEquals(Quantity.units(10), w.inventory.quantity(r.durable))
            assertEquals(Quantity.units(90), f.inventory.quantity(r.durable))
        }
    }

    @Test fun localMovesAreInstantAndFree() {
        val r = Rig(); val a = r.city(0)
        val f = r.facilityIn(alice, a); r.stock(f, r.durable, 50)
        val w = r.logistics.leaseWarehouse(alice, a, Quantity.units(1000), Tick(0)).getOrThrow()
        val moved = r.logistics.moveLocal(alice, StockRef.facility(f.id), StockRef.warehouse(w.id), r.durable, Quantity.units(20)).getOrThrow()
        assertEquals(Quantity.units(20), moved)
        assertEquals(Quantity.units(30), f.inventory.quantity(r.durable))
        assertEquals(Money.units(200), w.inventory.totalValue())
        val other = r.facilityIn(alice, r.city(5))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.logistics.moveLocal(alice, StockRef.facility(f.id), StockRef.facility(other.id), r.durable, Quantity.units(1))))
    }

    @Test fun warehouseRentIsChargedEveryTick() {
        val r = Rig(); val w = r.logistics.leaseWarehouse(alice, r.city(0), Quantity.units(1000), Tick(0)).getOrThrow()
        r.charges.clear()
        val rep = r.run(3)
        assertEquals(3, r.charges.count { it.memo == LogisticsMemo.RENT })
        assertEquals(w.rentPerTick, rep[0].rentCharged)
    }

    @Test fun unpaidRentEvictsAndWritesOffStock() {
        val r = Rig(); val a = r.city(0)
        val w = r.logistics.leaseWarehouse(alice, a, Quantity.units(1000), Tick(0)).getOrThrow()
        w.inventory.add(r.durable, Quantity.units(10), Money.units(100))
        r.funds = false
        val reports = r.run(r.logistics.params.maxUnpaidRentTicks)
        assertEquals(WarehouseStatus.EVICTED, w.status)
        assertEquals(Money.units(100), reports.last().evictedValue)
        assertNull(r.logistics.inventory(StockRef.warehouse(w.id)))
    }

    @Test fun payingRentResetsTheUnpaidCounter() {
        val r = Rig(); val w = r.logistics.leaseWarehouse(alice, r.city(0), Quantity.units(1000), Tick(0)).getOrThrow()
        r.funds = false; r.run(2)
        assertEquals(2, w.unpaidTicks)
        r.funds = true; r.run(1)
        assertEquals(0, w.unpaidTicks)
    }

    @Test fun closingNeedsAnEmptyWarehouse() {
        val r = Rig(); val w = r.logistics.leaseWarehouse(alice, r.city(0), Quantity.units(1000), Tick(0)).getOrThrow()
        w.inventory.add(r.durable, Quantity.units(1), Money.units(10))
        assertEquals(Rules.WRONG_STATE, rule(r.logistics.closeWarehouse(alice, w.id)))
        w.inventory.clear(r.durable)
        assertEquals(Rules.NOT_OWNER, rule(r.logistics.closeWarehouse(bob, w.id)))
        r.logistics.closeWarehouse(alice, w.id).getOrThrow()
        assertTrue(r.logistics.warehousesOf(alice).isEmpty())
    }

    @Test fun leaseRangeIsChecked() {
        val r = Rig()
        assertEquals(Rules.INVALID_QUANTITY, rule(r.logistics.leaseWarehouse(alice, r.city(0), Quantity.ZERO, Tick(0))))
        assertEquals(Rules.INVALID_QUANTITY, rule(r.logistics.leaseWarehouse(alice, r.city(0), Quantity.units(10_000_000), Tick(0))))
        r.funds = false
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(r.logistics.leaseWarehouse(alice, r.city(0), Quantity.units(100), Tick(0))))
    }

    @Test fun savedStateContinuesIdentically() {
        val r = Rig(); val (f, w, req) = setup(r, 200)
        r.stock(f, r.perishable, 100)
        r.logistics.ship(req, Tick(0)).getOrThrow()
        r.logistics.buyVehicle(alice, VehicleTypeId("truck"), r.city(0), Tick(0)).getOrThrow()
        r.run(2)
        val out = ByteWriter(); LogisticsCodec.write(r.logistics, out)
        val b = ByteWriter(); app.many.economytycoon.production.ProductionCodec.write(r.production, b)

        val copy = Rig()
        // the copy needs the same production state for facility lookups
        app.many.economytycoon.production.ProductionCodec.read(ByteReader(b.toByteArray()), copy.production)
        LogisticsCodec.read(ByteReader(out.toByteArray()), copy.logistics)
        assertEquals(r.hash(), copy.hash())
        assertEquals(w.id, copy.logistics.warehouses().first().id)
        copy.random.restore(r.random.snapshot()); copy.tick = r.tick
        for (i in 1..15) { r.logistics.step(Tick(r.tick + i), r.random); copy.logistics.step(Tick(copy.tick + i), copy.random) }
        assertEquals(r.hash(), copy.hash())
    }

    @Test fun corruptVersionIsRejected() {
        val w = ByteWriter(); w.writeVarInt(7)
        var failed = false
        try { LogisticsCodec.read(ByteReader(w.toByteArray()), Rig().logistics) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
    }
}
