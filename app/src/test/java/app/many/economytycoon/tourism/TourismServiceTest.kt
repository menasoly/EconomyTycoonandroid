package app.many.economytycoon.tourism

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismServiceTest {
    private val w = TourismWorld()
    private val svc get() = w.service

    private fun order(
        kind: PropertyKind = PropertyKind.HOTEL, stars: Int = 3, rooms: IntArray = intArrayOf(40, 0, 0), who: CompanyId? = w.owner, name: String = "Inn",
        instant: Boolean = false, rack: Money? = null,
    ) = svc.addProperty(w.dest.id, who, null, kind, stars, rooms, Tick(w.tick), name, rack, instant)

    // ----- building -----

    @Test fun buildingTakesTimeAndCostsMoney() {
        val o = order().getOrThrow()
        assertEquals(PropertyStatus.UNDER_CONSTRUCTION, o.property.status)
        assertEquals(w.tick + PropertyKind.HOTEL.buildDays, o.property.opensAt)
        assertEquals(AccommodationRules.buildCost(PropertyKind.HOTEL, 3, 40), o.property.invested)
        val pay = requireNotNull(o.payment)
        assertTrue(pay.key.startsWith("tour:capex:property:"))
        assertEquals(o.property.invested.raw, pay.lines.sumOf { it.amount.raw })
    }

    @Test fun instantPropertiesAreFreeAndOpen() {
        val o = order(instant = true).getOrThrow()
        assertEquals(PropertyStatus.OPEN, o.property.status)
        assertNull(o.payment)
        assertEquals(Money.ZERO, o.property.invested)
    }

    @Test fun propertiesWithoutAnOwnerPostNoPayment() {
        assertNull(order(who = null).getOrThrow().payment)
    }

    @Test fun aNewPropertyIsPricedFromTheReferenceAtThePriceLevel() {
        val o = order(stars = 3).getOrThrow()
        assertEquals(AccommodationRules.referenceRack(PropertyKind.HOTEL, 3), o.property.settings.rackRate)
        assertEquals(RatePolicy.FIXED, o.property.settings.policy)
        assertEquals(Money.units(250), order(rack = Money.units(250), name = "Priced").getOrThrow().property.settings.rackRate)
    }

    @Test fun badBuildOrdersAreRejected() {
        assertEquals(Rules.UNKNOWN_ENTITY, svc.addProperty(DestinationId(9), w.owner, null, PropertyKind.HOTEL, 3, intArrayOf(5, 0, 0), Tick(1), "X").rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, order(name = " ").rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, order(kind = PropertyKind.HOSTEL, stars = 4).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, order(kind = PropertyKind.RESORT, stars = 2).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, order(rooms = intArrayOf(0, 0, 0)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, order(rooms = intArrayOf(10, -1, 0)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, order(rooms = intArrayOf(10, 0)).rejectionOrNull()?.rule)
        assertEquals(Rules.LIMIT_EXCEEDED, order(rooms = intArrayOf(900, 0, 0)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_PRICE, order(rack = Money.ZERO).rejectionOrNull()?.rule)
    }

    @Test fun aCompanyCannotOwnUnlimitedProperties() {
        val small = TourismWorld()
        val limited = TourismService(small.state, TourismParams(maxPropertiesPerCompany = 2))
        repeat(2) { limited.addProperty(small.dest.id, small.owner, null, PropertyKind.HOSTEL, 1, intArrayOf(10, 0, 0), Tick(1), "H$it").getOrThrow() }
        assertEquals(Rules.LIMIT_EXCEEDED, limited.addProperty(small.dest.id, small.owner, null, PropertyKind.HOSTEL, 1, intArrayOf(10, 0, 0), Tick(1), "H3").rejectionOrNull()?.rule)
        assertTrue(limited.addProperty(small.dest.id, small.rival, null, PropertyKind.HOSTEL, 1, intArrayOf(10, 0, 0), Tick(1), "R").isOk)
    }

    @Test fun constructionFinishesOnTheOpeningDay() {
        val p = order(kind = PropertyKind.HOSTEL, stars = 2, rooms = intArrayOf(30, 0, 0)).getOrThrow().property
        val events = ArrayList<TourismEvent>()
        for (i in 0..PropertyKind.HOSTEL.buildDays + 2) events += w.day().events
        val opened = events.filterIsInstance<TourismEvent.PropertyOpened>().filter { it.property == p.id }
        assertEquals(1, opened.size)
        assertEquals(PropertyStatus.OPEN, p.status)
    }

    // ----- pricing and policies -----

    @Test fun ratesCanBeChanged() {
        val p = w.hotel()
        svc.setPricing(p.id, RatePolicy.DYNAMIC, Money.units(222)).getOrThrow()
        assertEquals(RatePolicy.DYNAMIC, p.settings.policy)
        assertEquals(Money.units(222), p.settings.rackRate)
        svc.setRackRate(p.id, Money.units(150)).getOrThrow()
        assertEquals(Money.units(150), p.settings.rackRate)
        assertEquals(RatePolicy.DYNAMIC, p.settings.policy)
        svc.setPolicy(p.id, RatePolicy.FIXED, 900_000L).getOrThrow()
        assertEquals(RatePolicy.FIXED, p.settings.policy)
        assertEquals(900_000L, p.settings.targetOccupancyPpm)
    }

    @Test fun badRatesAreRejected() {
        val p = w.hotel()
        assertEquals(Rules.INVALID_PRICE, svc.setPricing(p.id, RatePolicy.FIXED, Money.ZERO).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_PRICE, svc.setRackRate(p.id, Money(-4)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.setPolicy(p.id, RatePolicy.DYNAMIC, 50_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, svc.setRackRate(PropertyId(77), Money.units(1)).rejectionOrNull()?.rule)
    }

    @Test fun pricingBandIsValidated() {
        val p = w.hotel()
        assertTrue(svc.setPricingBand(p.id, 700_000L, 1_500_000L, 500_000L, 800_000L).isOk)
        assertEquals(700_000L, p.settings.floorPpm)
        assertEquals(Rules.INVALID_PRICE, svc.setPricingBand(p.id, 0L, 1_500_000L, 500_000L, 800_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_PRICE, svc.setPricingBand(p.id, 2_000_000L, 1_000_000L, 500_000L, 800_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_PRICE, svc.setPricingBand(p.id, 500_000L, 4_000_001L, 500_000L, 800_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.setPricingBand(p.id, 500_000L, 1_500_000L, 2_000_001L, 800_000L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.setPricingBand(p.id, 500_000L, 1_500_000L, 500_000L, 1_000_001L).rejectionOrNull()?.rule)
        assertEquals(700_000L, p.settings.floorPpm)
    }

    @Test fun overbookingIsLimited() {
        val p = w.hotel()
        assertTrue(svc.setOverbooking(p.id, 300_000L).isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, svc.setOverbooking(p.id, 300_001L).rejectionOrNull()?.rule)
        assertEquals(Rules.LIMIT_EXCEEDED, svc.setOverbooking(p.id, -1L).rejectionOrNull()?.rule)
        assertEquals(300_000L, p.overbookPpm)
    }

    @Test fun cancellationPolicyChanges() {
        val p = w.hotel()
        svc.setCancellation(p.id, CancellationPolicy.NON_REFUNDABLE).getOrThrow()
        assertEquals(CancellationPolicy.NON_REFUNDABLE, p.cancellation)
        assertEquals(Rules.UNKNOWN_ENTITY, svc.setCancellation(PropertyId(88), CancellationPolicy.MODERATE).rejectionOrNull()?.rule)
    }

    @Test fun listingTwiceIsADuplicateAndDelistingWhatIsNotListedFails() {
        val p = w.hotel()
        val i = w.platform()
        svc.listWith(p.id, i.id).getOrThrow()
        assertEquals(Rules.DUPLICATE, svc.listWith(p.id, i.id).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, svc.listWith(p.id, IntermediaryId(55)).rejectionOrNull()?.rule)
        svc.delist(p.id, i.id).getOrThrow()
        assertEquals(Rules.UNKNOWN_ENTITY, svc.delist(p.id, i.id).rejectionOrNull()?.rule)
    }

    // ----- capital works -----

    @Test fun expandingAddsRoomsAndCharges() {
        val p = w.hotel(rooms = intArrayOf(50, 0, 0))
        val c = svc.expandRooms(p.id, RoomClass.SUPERIOR, 20, Tick(w.tick)).getOrThrow()
        assertEquals(20, p.rooms[RoomClass.SUPERIOR.ordinal])
        assertEquals(70, p.totalRooms)
        assertEquals(Money(AccommodationRules.buildCost(p.kind, p.stars, 20).raw * 1_200_000L / 1_000_000L), c.cost)
        assertNotNull(c.payment)
    }

    @Test fun expansionLimitsApply() {
        val p = w.hotel(rooms = intArrayOf(750, 0, 0))
        assertEquals(Rules.INVALID_QUANTITY, svc.expandRooms(p.id, RoomClass.STANDARD, 0, Tick(w.tick)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, svc.expandRooms(p.id, RoomClass.STANDARD, 101, Tick(w.tick)).rejectionOrNull()?.rule)
        assertEquals(Rules.LIMIT_EXCEEDED, svc.expandRooms(p.id, RoomClass.STANDARD, 60, Tick(w.tick)).rejectionOrNull()?.rule)
        assertEquals(750, p.totalRooms)
    }

    @Test fun aClosedPropertyCannotBeExtendedOrUpgraded() {
        val p = w.hotel()
        svc.closeProperty(p.id).getOrThrow()
        assertEquals(Rules.WRONG_STATE, svc.expandRooms(p.id, RoomClass.STANDARD, 5, Tick(w.tick)).rejectionOrNull()?.rule)
        assertEquals(Rules.WRONG_STATE, svc.upgradeStars(p.id, 4, Tick(w.tick)).rejectionOrNull()?.rule)
    }

    @Test fun upgradingRaisesStarsRatesAndCondition() {
        val p = w.hotel(stars = 3, rack = Money.units(100))
        p.conditionPpm = 500_000L
        val c = svc.upgradeStars(p.id, 4, Tick(w.tick)).getOrThrow()
        assertEquals(4, p.stars)
        assertEquals(1_000_000L, p.conditionPpm)
        assertEquals(Money.units(160), p.settings.rackRate)
        assertTrue(c.cost.raw > 0L)
    }

    @Test fun upgradeMustBeAnUpgradeWithinTheKindsRange() {
        val p = w.hotel(stars = 3)
        assertEquals(Rules.WRONG_STATE, svc.upgradeStars(p.id, 3, Tick(w.tick)).rejectionOrNull()?.rule)
        assertEquals(Rules.WRONG_STATE, svc.upgradeStars(p.id, 2, Tick(w.tick)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.upgradeStars(p.id, 6, Tick(w.tick)).rejectionOrNull()?.rule)
        val hostel = w.hotel(kind = PropertyKind.HOSTEL, stars = 1, rooms = intArrayOf(20, 0, 0))
        assertEquals(Rules.INVALID_ARGUMENT, svc.upgradeStars(hostel.id, 3, Tick(w.tick)).rejectionOrNull()?.rule)
    }

    @Test fun renovationCostGrowsWithWear() {
        val a = w.hotel(); val b = w.hotel()
        a.conditionPpm = 800_000L; b.conditionPpm = 300_000L
        val ca = svc.renovate(a.id, Tick(w.tick)).getOrThrow()
        val cb = svc.renovate(b.id, Tick(w.tick)).getOrThrow()
        assertTrue(cb.cost > ca.cost)
        assertEquals(1_000_000L, a.conditionPpm)
        assertEquals(Rules.WRONG_STATE, svc.renovate(a.id, Tick(w.tick)).rejectionOrNull()?.rule)
    }

    @Test fun closingAndReopening() {
        val p = w.hotel()
        svc.closeProperty(p.id).getOrThrow()
        assertEquals(PropertyStatus.CLOSED, p.status)
        assertEquals(Rules.WRONG_STATE, svc.closeProperty(p.id).rejectionOrNull()?.rule)
        svc.reopenProperty(p.id, Tick(w.tick)).getOrThrow()
        assertEquals(PropertyStatus.OPEN, p.status)
        assertEquals(Rules.WRONG_STATE, svc.reopenProperty(p.id, Tick(w.tick)).rejectionOrNull()?.rule)
    }

    @Test fun aPropertyWithLiveContractsCannotClose() {
        val p = w.hotel()
        w.allotment(w.operator(), p)
        assertEquals(Rules.WRONG_STATE, svc.closeProperty(p.id).rejectionOrNull()?.rule)
    }

    @Test fun closingDropsFutureBookings() {
        val p = w.hotel()
        w.days(10)
        assertTrue(p.bookedNights.isNotEmpty())
        svc.closeProperty(p.id).getOrThrow()
        assertTrue(p.bookedNights.isEmpty())
    }

    // ----- attractions -----

    @Test fun attractionOrdersTakeTimeAndMoney() {
        val o = svc.addAttraction(w.dest.id, w.owner, null, AttractionKind.MUSEUM, "Louvre", Tick(w.tick)).getOrThrow()
        assertEquals(w.tick + 120, o.attraction.opensAt)
        assertEquals(AttractionKind.MUSEUM.buildCost, o.attraction.invested)
        assertNotNull(o.payment)
        val half = svc.addAttraction(w.dest.id, w.owner, null, AttractionKind.MUSEUM, "Small", Tick(w.tick), capacityPerDay = 750).getOrThrow()
        assertEquals(Money(AttractionKind.MUSEUM.buildCost.raw / 2), half.attraction.invested)
    }

    @Test fun badAttractionOrdersAreRejected() {
        assertEquals(Rules.UNKNOWN_ENTITY, svc.addAttraction(DestinationId(8), null, null, AttractionKind.MUSEUM, "X", Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.addAttraction(w.dest.id, null, null, AttractionKind.MUSEUM, "", Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, svc.addAttraction(w.dest.id, null, null, AttractionKind.MUSEUM, "X", Tick(1), capacityPerDay = 0).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_PRICE, svc.addAttraction(w.dest.id, null, null, AttractionKind.MUSEUM, "X", Tick(1), ticket = Money(-1)).rejectionOrNull()?.rule)
    }

    @Test fun attractionSettingsAreValidated() {
        val a = w.attraction()
        assertTrue(svc.setTicketPrice(a.id, Money.ZERO).isOk)
        assertEquals(Rules.INVALID_PRICE, svc.setTicketPrice(a.id, Money(-1)).rejectionOrNull()?.rule)
        assertTrue(svc.setAttractionCapacity(a.id, 6_000).isOk)
        assertEquals(Rules.INVALID_QUANTITY, svc.setAttractionCapacity(a.id, 6_001).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, svc.setAttractionCapacity(a.id, 0).rejectionOrNull()?.rule)
        assertTrue(svc.setAttractionOpen(a.id, false).isOk)
        assertFalse(a.open)
        assertEquals(Rules.UNKNOWN_ENTITY, svc.setAttractionOpen(AttractionId(99), true).rejectionOrNull()?.rule)
    }

    // ----- markets -----

    @Test fun originMarketsAreValidated() {
        assertEquals(Rules.INVALID_ARGUMENT, svc.addOrigin(" ", CountryId(1), Point(0, 0), 10).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, svc.addOrigin("A", CountryId(1), Point(0, 0), -1).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.addOrigin("A", CountryId(1), Point(0, 0), 10, mixPpm = longArrayOf(1L, 2L)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.addOrigin("A", CountryId(1), Point(0, 0), 10, mixPpm = longArrayOf(500_000L, 500_000L, 500_000L, -500_000L)).rejectionOrNull()?.rule)
        assertTrue(svc.setOutbound(w.home.id, 5L).isOk)
        assertEquals(5L, w.home.outboundPerDay)
        assertEquals(Rules.INVALID_QUANTITY, svc.setOutbound(w.home.id, -5L).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, svc.setOutbound(OriginId(50), 5L).rejectionOrNull()?.rule)
    }

    @Test fun destinationPriceLevelIsRangeChecked() {
        val r = svc.addDestination(
            app.many.economytycoon.core.CityId(7), app.many.economytycoon.core.RegionId(1), CountryId(1), "Pricey",
            app.many.economytycoon.world.ClimateZone.ARID, Point(1, 1), false, basePriceLevelPpm = 5_000_000L,
        )
        assertEquals(Rules.INVALID_PRICE, r.rejectionOrNull()?.rule)
    }

    // ----- marketing and shocks -----

    @Test fun campaignsLiftMarketingWithDiminishingReturns() {
        val small = svc.launchCampaign(w.dest.id, w.owner, VisitorSegment.LUXURY, Money.units(30_000), Tick(w.tick)).getOrThrow()
        val first = w.dest.marketingPpm[VisitorSegment.LUXURY.ordinal]
        svc.launchCampaign(w.dest.id, w.owner, VisitorSegment.LUXURY, Money.units(30_000), Tick(w.tick + 1)).getOrThrow()
        val second = w.dest.marketingPpm[VisitorSegment.LUXURY.ordinal] - first
        assertTrue(first > 0L && second in 1 until first)
        assertEquals(0L, w.dest.marketingPpm[VisitorSegment.BUDGET.ordinal])
        assertNotNull(small.payment)
    }

    @Test fun anUnfocusedCampaignSpreadsOverAllSegments() {
        svc.launchCampaign(w.dest.id, w.owner, null, Money.units(80_000), Tick(w.tick)).getOrThrow()
        val m = w.dest.marketingPpm
        assertTrue(m.all { it > 0L })
        assertEquals(1, m.toSet().size)
    }

    @Test fun campaignsHaveAMinimumAndNeedARealDestination() {
        assertEquals(Rules.INVALID_QUANTITY, svc.launchCampaign(w.dest.id, w.owner, null, Money.units(10), Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, svc.launchCampaign(DestinationId(55), w.owner, null, Money.units(5_000), Tick(1)).rejectionOrNull()?.rule)
    }

    @Test fun shocksAreValidatedAndCapped() {
        assertEquals(Rules.UNKNOWN_ENTITY, svc.triggerShock(ShockKind.STRIKE, DestinationId(9), null, Tick(1), 3, 500_000L, "x").rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, svc.triggerShock(ShockKind.STRIKE, null, OriginId(9), Tick(1), 3, 500_000L, "x").rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.triggerShock(ShockKind.STRIKE, null, null, Tick(1), 0, 500_000L, "x").rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.triggerShock(ShockKind.STRIKE, null, null, Tick(1), 721, 500_000L, "x").rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, svc.triggerShock(ShockKind.STRIKE, null, null, Tick(1), 3, 0L, "x").rejectionOrNull()?.rule)
        repeat(6) { svc.triggerShock(ShockKind.STRIKE, w.dest.id, null, Tick(w.tick), 5, 400_000L, "s$it").getOrThrow() }
        assertEquals(Rules.LIMIT_EXCEEDED, svc.triggerShock(ShockKind.STRIKE, w.dest.id, null, Tick(w.tick), 5, 400_000L, "one too many").rejectionOrNull()?.rule)
    }

    @Test fun aBlankShockLabelFallsBackToTheKind() {
        val s = svc.triggerShock(ShockKind.DISEASE, null, null, Tick(1), 5, 500_000L, " ").getOrThrow()
        assertEquals(ShockKind.DISEASE.label, s.label)
    }

    // ----- the clock -----

    @Test fun daysMustRunInOrder() {
        w.day()
        try {
            svc.advanceDay(Tick(w.tick - 1), DayInputs(), w.random)
            throw AssertionError("a repeated day was accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("already ran"))
        }
    }

    @Test fun aMonthCanOnlyBeSettledOnce() {
        w.hotel()
        w.days(30)
        w.settle()
        try {
            svc.settleMonth(Tick(w.tick - 1))
            throw AssertionError("settled twice")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("already settled"))
        }
    }

    @Test fun theClockAdvancesTheStateMarker() {
        assertEquals(-1L, w.state.lastTick)
        w.days(3)
        assertEquals(w.tick - 1, w.state.lastTick)
    }
}
