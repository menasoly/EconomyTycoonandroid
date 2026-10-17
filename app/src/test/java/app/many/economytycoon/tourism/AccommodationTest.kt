package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccommodationTest {
    private val w = TourismWorld()

    @Test fun starFactorsRiseAndClamp() {
        val f = (1..5).map { AccommodationRules.starFactorPpm(it) }
        assertEquals(f.sorted(), f)
        assertEquals(f.first(), AccommodationRules.starFactorPpm(-4))
        assertEquals(f.last(), AccommodationRules.starFactorPpm(9))
        assertEquals(1_000_000L, AccommodationRules.starFactorPpm(3))
    }

    @Test fun kindsAllowTheirStarRangeOnly() {
        assertTrue(PropertyKind.HOSTEL.allowsStars(2))
        assertFalse(PropertyKind.HOSTEL.allowsStars(3))
        assertFalse(PropertyKind.RESORT.allowsStars(2))
        assertTrue(PropertyKind.HOTEL.allowsStars(1) && PropertyKind.HOTEL.allowsStars(5))
    }

    @Test fun roomClassesAreOrderedByRate() {
        assertTrue(RoomClass.STANDARD.rateMultiplierPpm < RoomClass.SUPERIOR.rateMultiplierPpm)
        assertTrue(RoomClass.SUPERIOR.rateMultiplierPpm < RoomClass.SUITE.rateMultiplierPpm)
        assertEquals(3, RoomClass.COUNT)
    }

    @Test fun referenceRackScalesWithStarsAndKind() {
        assertTrue(AccommodationRules.referenceRack(PropertyKind.HOTEL, 5) > AccommodationRules.referenceRack(PropertyKind.HOTEL, 3))
        assertTrue(AccommodationRules.referenceRack(PropertyKind.RESORT, 4) > AccommodationRules.referenceRack(PropertyKind.HOTEL, 4))
        assertTrue(AccommodationRules.referenceRack(PropertyKind.HOSTEL, 2) < AccommodationRules.referenceRack(PropertyKind.HOTEL, 2))
        assertEquals(Money.units(105), AccommodationRules.referenceRack(PropertyKind.HOTEL, 3))
    }

    @Test fun buildCostScalesWithRoomsAndStars() {
        val a = AccommodationRules.buildCost(PropertyKind.HOTEL, 3, 100)
        assertEquals(Money.units(6_000_000), a)
        assertEquals(a * 2L, AccommodationRules.buildCost(PropertyKind.HOTEL, 3, 200))
        assertTrue(AccommodationRules.buildCost(PropertyKind.HOTEL, 4, 100) > a)
    }

    @Test fun upgradeCostIsAShareOfTheDifferenceAndNeverNegative() {
        val up = AccommodationRules.upgradeCost(PropertyKind.HOTEL, 3, 4, 100)
        val diff = AccommodationRules.buildCost(PropertyKind.HOTEL, 4, 100) - AccommodationRules.buildCost(PropertyKind.HOTEL, 3, 100)
        assertEquals(diff.raw * 6 / 10, up.raw)
        assertEquals(Money.ZERO, AccommodationRules.upgradeCost(PropertyKind.HOTEL, 4, 2, 100))
    }

    @Test fun dailyCostsScaleWithSizeAndOccupancy() {
        assertTrue(AccommodationRules.fixedCostPerDay(PropertyKind.HOTEL, 3, 200) > AccommodationRules.fixedCostPerDay(PropertyKind.HOTEL, 3, 100))
        assertEquals(Money.ZERO, AccommodationRules.variableCost(PropertyKind.HOTEL, 3, 0))
        assertTrue(AccommodationRules.variableCost(PropertyKind.RESORT, 3, 50) > AccommodationRules.variableCost(PropertyKind.HOTEL, 3, 50))
        assertTrue(AccommodationRules.ancillaryPerGuest(PropertyKind.RESORT, 5) > AccommodationRules.ancillaryPerGuest(PropertyKind.HOSTEL, 2))
    }

    @Test fun bookValueFallsWithWear() {
        val p = w.hotel()
        val fresh = AccommodationRules.bookValue(p)
        p.conditionPpm = 400_000L
        assertTrue(AccommodationRules.bookValue(p) < fresh)
        assertEquals(AccommodationRules.buildCost(p.kind, p.stars, p.totalRooms), fresh)
    }

    @Test fun roomsOutOfOrderStartBelowSixtyPercentCondition() {
        val p = w.hotel(rooms = intArrayOf(100, 0, 0))
        assertEquals(0L, p.outOfOrderPpm())
        assertEquals(100, p.sellable(RoomClass.STANDARD))
        p.conditionPpm = 600_000L
        assertEquals(0L, p.outOfOrderPpm())
        p.conditionPpm = 300_000L
        assertEquals(225_000L, p.outOfOrderPpm())
        assertEquals(77, p.sellable(RoomClass.STANDARD))
        p.conditionPpm = 0L
        assertEquals(450_000L, p.outOfOrderPpm())
        assertEquals(55, p.sellableTotal())
    }

    @Test fun overbookingRaisesTheSellLimit() {
        val p = w.hotel(rooms = intArrayOf(100, 0, 0))
        assertEquals(100, p.sellLimit(RoomClass.STANDARD))
        p.overbookPpm = 80_000L
        assertEquals(108, p.sellLimit(RoomClass.STANDARD))
    }

    @Test fun rackRatesScaleByRoomClass() {
        val p = w.hotel(rack = Money.units(100))
        assertEquals(Money.units(100), p.rackRate(RoomClass.STANDARD))
        assertEquals(Money.units(135), p.rackRate(RoomClass.SUPERIOR))
        assertEquals(Money.units(210), p.rackRate(RoomClass.SUITE))
    }

    @Test fun rateSettingsValidateTheirBand() {
        val ok = RateSettings(RatePolicy.DYNAMIC, Money.units(100))
        assertEquals(RatePolicy.DYNAMIC, ok.policy)
        for (bad in listOf<() -> RateSettings>(
            { RateSettings(RatePolicy.FIXED, Money.ZERO) },
            { RateSettings(RatePolicy.FIXED, Money.units(100), floorPpm = 2_000_000L, ceilingPpm = 1_000_000L) },
            { RateSettings(RatePolicy.FIXED, Money.units(100), aggressionPpm = 3_000_000L) },
            { RateSettings(RatePolicy.FIXED, Money.units(100), targetOccupancyPpm = 50_000L) },
        )) {
            try { bad(); throw AssertionError("expected a rejection") } catch (e: IllegalArgumentException) { /* expected */ }
        }
    }

    @Test fun cancellationPoliciesTradeDiscountForCertainty() {
        val p = CancellationPolicy.values().toList()
        assertTrue(p[0].cancelFactorPpm > p[1].cancelFactorPpm && p[1].cancelFactorPpm > p[2].cancelFactorPpm)
        assertTrue(p[0].discountPpm < p[1].discountPpm && p[1].discountPpm < p[2].discountPpm)
        assertTrue(CancellationPolicy.NON_REFUNDABLE.chargesNoShow && !CancellationPolicy.FLEXIBLE.chargesNoShow)
    }

    @Test fun nightBookCountsAndTotalsCommission() {
        val b = NightBook()
        b.booked[0] = 3; b.booked[1] = 2
        b.revenueRaw[0] = 300; b.revenueRaw[1] = 400
        assertEquals(5, b.total)
        assertEquals(Money(700), b.revenue())
        b.addCommission(IntermediaryId(1), 40); b.addCommission(IntermediaryId(2), 10); b.addCommission(IntermediaryId(1), 5); b.addCommission(IntermediaryId(3), 0)
        assertEquals(Money(55), b.commission())
        assertEquals(2, b.commissionRaw.size)
        b.addVia(IntermediaryId(1), 2); b.addVia(IntermediaryId(1), 1)
        assertEquals(3, b.roomsVia[1L])
    }

    @Test fun nightBookHashChangesWithEveryField() {
        fun h(b: NightBook) = CanonicalHasher().also { b.hashInto(it) }.finish()
        val base = NightBook()
        val hashes = HashSet<Any>()
        hashes += h(base)
        for (change in listOf<(NightBook) -> Unit>(
            { it.booked[1] = 1 }, { it.revenueRaw[2] = 5 }, { it.bySegment[3] = 1 }, { it.byChannel[1] = 1 }, { it.operatorRooms[0] = 1 },
            { it.operatorRevenueRaw[0] = 1 }, { it.addCommission(IntermediaryId(2), 3) }, { it.addVia(IntermediaryId(2), 1) },
        )) {
            val b = NightBook(); change(b)
            assertTrue("distinct", hashes.add(h(b)))
        }
    }

    @Test fun stayDayDerivedFiguresAreConsistent() {
        val d = StayDay(
            1, 100, 80, 3, 1, 0, Money.units(8_000), Money.units(1_000), Money.units(600), Money.ZERO, Money.units(2_000), Money.units(1_500), Money.ZERO, 150, 800_000L, 20, 20,
        )
        assertEquals(800_000L, d.occupancyPpm)
        assertEquals(Money.units(100), d.adr)
        assertEquals(Money.units(80), d.revPar)
        assertEquals(Money.units(9_000), d.totalRevenue)
        assertEquals(Money.units(9_000 - 600 - 2_000 - 1_500), d.gop)
        assertEquals(Money.ZERO, StayDay(1, 0, 0, 0, 0, 0, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0, 0L, 0, 0).adr)
    }

    @Test fun propertyHashChangesWithTheSettingsThatMatter() {
        fun h(p: Property) = CanonicalHasher().also { p.hashInto(it) }.finish()
        val p = w.hotel()
        val before = h(p)
        assertEquals(before, h(p))
        p.overbookPpm = 10_000L
        assertNotEquals(before, h(p))
        val mid = h(p)
        p.conditionPpm = 500_000L
        assertNotEquals(mid, h(p))
    }

    @Test fun dayHistoryIsBounded() {
        val p = w.hotel()
        repeat(Property.HISTORY_DAYS + 20) {
            p.pushDay(StayDay(it.toLong(), 100, 50, 0, 0, 0, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0, 0L, 0, 0))
        }
        assertEquals(Property.HISTORY_DAYS, p.days.size)
        assertEquals(Property.HISTORY_DAYS + 19L, p.lastDay?.tick)
    }
}
