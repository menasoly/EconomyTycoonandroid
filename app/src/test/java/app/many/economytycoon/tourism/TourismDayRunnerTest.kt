package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.WeatherEvent
import app.many.economytycoon.world.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismDayRunnerTest {
    /** Stay dates need two weeks of bookings before a night is fully booked, so the first days of a run are a warm-up. */
    private val warmUp = Seasonality.HORIZON

    private fun tail(w: TourismWorld, p: Property, n: Int = 20) = p.days.takeLast(n)

    @Test fun anEmptyWorldHasAnEmptyDay() {
        val s = TourismService()
        val r = s.advanceDay(Tick(10), DayInputs(), app.many.economytycoon.core.RandomProvider(1L))
        assertTrue(r.destinations.isEmpty() && r.events.isEmpty() && r.routes.isEmpty() && r.spending.isEmpty())
        assertEquals(0L, r.totalArrivals)
    }

    @Test fun aDayReportsEveryDestination() {
        val w = TourismWorld()
        w.hotel()
        val r = w.day()
        assertEquals(1, r.destinations.size)
        assertNotNull(r.destination(w.dest.id))
        assertNotNull(r.spendingOf(w.dest.id))
        assertEquals(Tick(150), r.tick)
        assertTrue(r.totalArrivals > 0L)
    }

    @Test fun arrivalsAddUpAcrossSegmentsAndOrigins() {
        val w = TourismWorld()
        w.hotel()
        val r = w.day().destination(w.dest.id)!!
        assertEquals(r.day.arrivals, r.arrivalsBySegment.sum())
        assertEquals(r.day.arrivals, r.arrivalsByOrigin.values.sum())
        assertEquals(setOf(w.home.id, w.abroad.id), r.arrivalsByOrigin.keys)
    }

    @Test fun propertySalesAddUpToTheDestinationDay() {
        val w = TourismWorld()
        val a = w.hotel(); val b = w.hotel(4, intArrayOf(40, 20, 5))
        w.days(25)
        val d = w.dest.lastDay!!
        assertEquals(a.lastDay!!.roomsSold + b.lastDay!!.roomsSold.toLong(), d.roomsSold)
        assertEquals(a.lastDay!!.roomsAvailable + b.lastDay!!.roomsAvailable.toLong(), d.roomsAvailable)
        assertEquals(a.lastDay!!.roomRevenue + b.lastDay!!.roomRevenue, d.roomRevenue)
    }

    @Test fun occupancyBuildsUpAsBookingsAccumulate() {
        val w = TourismWorld()
        val p = w.hotel()
        w.days(30)
        val early = p.days.take(3).sumOf { it.roomsSold }
        val later = p.days.takeLast(3).sumOf { it.roomsSold }
        assertTrue("$early vs $later", later > early)
    }

    @Test fun anEstablishedHotelTradesAtAPlausibleOccupancy() {
        val w = TourismWorld()
        val p = w.hotel(); w.hotel(); w.hotel(2, intArrayOf(80, 0, 0))
        w.days(60)
        val k = TourismAnalytics.kpis(p, 30)
        assertTrue("occupancy ${k.occupancyPpm}", k.occupancyPpm in 250_000L..1_000_000L)
        assertTrue(k.adr.raw > 0L && k.revPar.raw > 0L)
        assertTrue(k.revPar <= k.adr)
    }

    @Test fun roomsSoldNeverExceedWhatTheHotelHas() {
        val w = TourismWorld(homeOutbound = 600L, abroadOutbound = 800L)
        val p = w.hotel(rooms = intArrayOf(20, 0, 0))
        w.days(40)
        for (d in p.days) assertTrue(d.roomsSold <= d.roomsAvailable)
        TourismInvariants.check(w.state)
    }

    @Test fun anUndersizedHotelFillsAndTurnsDemandAway() {
        val w = TourismWorld(homeOutbound = 600L, abroadOutbound = 800L)
        val p = w.hotel(rooms = intArrayOf(20, 0, 0))
        val events = w.days(40).flatMap { it.events }
        assertTrue(tail(w, p, 10).all { it.occupancyPpm >= 700_000L })
        assertTrue(events.any { it is TourismEvent.DemandTurnedAway })
        assertTrue(w.dest.days.takeLast(10).all { it.deniedRoomNights > 0L })
        assertTrue(w.dest.unmetPpm > 0L)
    }

    @Test fun anOversizedHotelStaysHalfEmpty() {
        val w = TourismWorld(homeOutbound = 30L, abroadOutbound = 30L)
        val p = w.hotel(rooms = intArrayOf(240, 45, 15))
        w.days(40)
        assertTrue(TourismAnalytics.kpis(p, 20).occupancyPpm < 600_000L)
        val last = w.dest.days.takeLast(10)
        assertTrue(last.sumOf { it.deniedRoomNights } * 20 < last.sumOf { it.roomNightDemand })
    }

    @Test fun propertiesUnderConstructionAndClosedOnesSellNothing() {
        val w = TourismWorld()
        val open = w.hotel()
        val closed = w.hotel()
        w.service.closeProperty(closed.id).getOrThrow()
        val building = w.service.addProperty(w.dest.id, w.owner, null, PropertyKind.HOTEL, 3, intArrayOf(50, 0, 0), Tick(w.tick), "Later").getOrThrow().property
        w.days(20)
        assertTrue(open.days.sumOf { it.roomsSold } > 0)
        assertTrue(closed.days.isEmpty())
        assertTrue(building.days.isEmpty())
    }

    @Test fun aPropertyThatOpensStartsTrading() {
        val w = TourismWorld()
        w.hotel()
        val p = w.service.addProperty(w.dest.id, w.owner, null, PropertyKind.HOSTEL, 2, intArrayOf(40, 0, 0), Tick(w.tick), "Bunks").getOrThrow().property
        w.days(PropertyKind.HOSTEL.buildDays + 25)
        assertEquals(PropertyStatus.OPEN, p.status)
        assertTrue(p.days.isNotEmpty())
        assertTrue(p.days.takeLast(5).sumOf { it.roomsSold } > 0)
    }

    @Test fun moreCompetitionSharesTheSameDemand() {
        val one = TourismWorld(seed = 3L)
        val a = one.hotel(); one.days(50)
        val two = TourismWorld(seed = 3L)
        val b = two.hotel(); two.hotel(); two.days(50)
        assertTrue(TourismAnalytics.kpis(a, 20).occupancyPpm >= TourismAnalytics.kpis(b, 20).occupancyPpm)
        assertTrue(two.dest.days.takeLast(20).sumOf { it.roomsSold } >= one.dest.days.takeLast(20).sumOf { it.roomsSold })
    }

    @Test fun cheaperRoomsWinGuestsFromDearerOnes() {
        val w = TourismWorld(homeOutbound = 220L, abroadOutbound = 300L)
        val cheap = w.hotel(rack = Money.units(70)); val dear = w.hotel(rack = Money.units(190))
        w.days(60)
        assertTrue(TourismAnalytics.kpis(cheap, 20).occupancyPpm > TourismAnalytics.kpis(dear, 20).occupancyPpm)
        assertTrue(TourismAnalytics.kpis(dear, 20).adr > TourismAnalytics.kpis(cheap, 20).adr)
    }

    @Test fun nonRefundableRatesCancelLessThanFlexibleOnes() {
        val w = TourismWorld()
        val flex = w.hotel(); val firm = w.hotel()
        w.service.setCancellation(firm.id, CancellationPolicy.NON_REFUNDABLE).getOrThrow()
        w.days(50)
        val cf = flex.days.drop(warmUp).sumOf { it.cancelled.toLong() * 1_000L / it.roomsSold.coerceAtLeast(1) }
        val cn = firm.days.drop(warmUp).sumOf { it.cancelled.toLong() * 1_000L / it.roomsSold.coerceAtLeast(1) }
        assertTrue("$cf vs $cn", cn < cf)
    }

    @Test fun overbookingSellsMoreRoomsThanTheHotelHasAndWalksSomeGuests() {
        val w = TourismWorld(homeOutbound = 600L, abroadOutbound = 800L)
        val p = w.hotel(rooms = intArrayOf(40, 0, 0))
        w.service.setOverbooking(p.id, 300_000L).getOrThrow()
        val events = w.days(50).flatMap { it.events }
        assertTrue(p.days.sumOf { it.walked } > 0)
        assertTrue(events.any { it is TourismEvent.GuestsWalked && it.property == p.id })
        assertTrue(p.lifetimeWalked > 0L)
        assertTrue(p.days.any { it.walkCost.raw > 0L })
        for (d in p.days) assertTrue(d.roomsSold <= d.roomsAvailable)
    }

    @Test fun withoutOverbookingNobodyIsWalked() {
        val w = TourismWorld(homeOutbound = 600L, abroadOutbound = 800L)
        val p = w.hotel(rooms = intArrayOf(40, 0, 0))
        w.days(50)
        assertEquals(0, p.days.sumOf { it.walked })
    }

    @Test fun wearBuildsUpWithUseAndLowersTheCondition() {
        val w = TourismWorld(homeOutbound = 600L, abroadOutbound = 800L)
        val p = w.hotel(rooms = intArrayOf(30, 0, 0))
        w.days(90)
        assertTrue(p.conditionPpm < 1_000_000L)
        assertTrue(p.conditionPpm > 0L)
    }

    @Test fun poorConditionLowersTheScoreOfTheStay() {
        val good = TourismWorld(seed = 2L); val worn = TourismWorld(seed = 2L)
        val a = good.hotel(); val b = worn.hotel()
        b.conditionPpm = 200_000L
        good.days(40); worn.days(40)
        assertTrue(b.reviews.scorePpm < a.reviews.scorePpm)
    }

    @Test fun missingStaffLowersTheServiceAndTheRating() {
        val full = TourismWorld(seed = 2L); val thin = TourismWorld(seed = 2L)
        val a = full.hotel(); val b = thin.hotel()
        full.days(60)
        thin.days(60, DayInputs(staff = ShareStaffSupply(400_000L)))
        assertTrue(b.serviceLevelPpm < a.serviceLevelPpm)
        assertTrue(b.reviews.scorePpm < a.reviews.scorePpm)
    }

    @Test fun staffCostsFollowHeadsAndWage() {
        val cheap = TourismWorld(seed = 2L); val dear = TourismWorld(seed = 2L)
        val a = cheap.hotel(); val b = dear.hotel()
        cheap.days(25, DayInputs(defaultWage = Money.units(40)))
        dear.days(25, DayInputs(defaultWage = Money.units(120)))
        assertTrue(b.days.last().wageCost > a.days.last().wageCost)
        assertEquals(Staffing.wageCost(a.days.last().staffHad, Money.units(40)), a.days.last().wageCost)
    }

    @Test fun marketingRaisesArrivalsAndThenFades() {
        val base = TourismWorld(seed = 4L); val pushed = TourismWorld(seed = 4L)
        base.hotel(); pushed.hotel()
        pushed.service.launchCampaign(pushed.dest.id, pushed.owner, null, Money.units(400_000), Tick(pushed.tick)).getOrThrow()
        val ra = base.days(5).sumOf { it.totalArrivals }
        val rb = pushed.days(5).sumOf { it.totalArrivals }
        assertTrue("$ra vs $rb", rb > ra)
        val early = pushed.dest.marketingPpm.sum()
        pushed.days(120)
        assertTrue(pushed.dest.marketingPpm.sum() < early)
    }

    @Test fun aStrikeCutsArrivalsForItsDuration() {
        val calm = TourismWorld(seed = 4L); val struck = TourismWorld(seed = 4L)
        calm.hotel(); struck.hotel()
        struck.service.triggerShock(ShockKind.STRIKE, struck.dest.id, null, Tick(struck.tick), 10, 1_000_000L, "Strike").getOrThrow()
        val a = calm.days(10).sumOf { it.totalArrivals }
        val b = struck.days(10).sumOf { it.totalArrivals }
        assertTrue("$a vs $b", b < a)
        val afterA = calm.days(5).sumOf { it.totalArrivals }
        val afterB = struck.days(5).sumOf { it.totalArrivals }
        assertTrue(Math.abs(afterA - afterB) * 20 < afterA)
    }

    @Test fun shocksAnnounceTheirStartAndEnd() {
        val w = TourismWorld()
        w.hotel()
        val s = w.service.triggerShock(ShockKind.DISEASE, w.dest.id, null, Tick(w.tick + 2), 3, 500_000L, "Fever").getOrThrow()
        val events = w.days(8).flatMap { it.events }
        assertEquals(1, events.count { it is TourismEvent.ShockStarted && it.shock == s.id })
        assertEquals(1, events.count { it is TourismEvent.ShockEnded && it.shock == s.id })
        val started = events.first { it is TourismEvent.ShockStarted && it.shock == s.id }.tick
        val ended = events.first { it is TourismEvent.ShockEnded && it.shock == s.id }.tick
        assertEquals(s.start.value, started)
        assertEquals(s.end.value, ended)
    }

    @Test fun aFestivalBringsMoreVisitorsAndAnAnnouncement() {
        val w = TourismWorld(startTick = 140L)
        w.hotel()
        w.service.setFestival(w.dest.id, 6, 7).getOrThrow()
        val reports = w.days(30)
        val began = reports.flatMap { it.events }.filterIsInstance<TourismEvent.FestivalBegan>()
        assertEquals(1, began.size)
        assertEquals(150L, began[0].tick)
        val during = reports.filter { it.tick.value in 150L..156L }.sumOf { it.totalArrivals }
        val before = reports.filter { it.tick.value in 143L..149L }.sumOf { it.totalArrivals }
        assertTrue("$during vs $before", during > before)
    }

    @Test fun transportLimitsCapArrivalsAndReportFullRoutes() {
        val w = TourismWorld()
        w.hotel()
        val limited = DayInputs(transport = FixedTransport(emptyMap(), default = 5L))
        val r = w.day(limited)
        assertTrue(r.totalArrivals <= 10L)
        assertTrue(r.routes.all { it.carried <= 5L })
        assertTrue(r.events.any { it is TourismEvent.RoutesFull })
        assertTrue(r.routes.any { it.turnedAway > 0L })
    }

    @Test fun badWeatherLowersAttractionVisits() {
        val dry = TourismWorld(seed = 6L); val wet = TourismWorld(seed = 6L)
        dry.hotel(); wet.hotel()
        val park = dry.attraction(AttractionKind.THEME_PARK); val park2 = wet.attraction(AttractionKind.THEME_PARK)
        val storm = DayInputs(weather = mapOf(wet.dest.id to listOf(WeatherEvent(RegionId(1), WeatherKind.STORM, Tick(wet.tick), 60, 1_000_000L))))
        dry.days(40); wet.days(40, storm)
        assertTrue(park2.days.takeLast(10).sumOf { it.visits } < park.days.takeLast(10).sumOf { it.visits })
    }

    @Test fun attractionsEarnTicketRevenueAndHaveRunningCosts() {
        val w = TourismWorld()
        w.hotel(); val a = w.attraction()
        w.days(40)
        assertTrue(a.lifetimeVisits > 0L)
        assertTrue(a.lifetimeRevenue.raw > 0L)
        assertTrue(a.monthCost.raw > 0L)
        assertEquals(a.lifetimeVisits, a.days.sumOf { it.visits })
    }

    @Test fun attractionsDrawVisitorsAlreadyInTown() {
        val w = TourismWorld()
        w.hotel(); w.attraction()
        w.days(40)
        assertTrue(w.dest.days.takeLast(10).all { it.attractionVisits > 0L })
    }

    @Test fun localSpendingIsReportedWithAMultiplier() {
        val w = TourismWorld()
        w.hotel()
        w.days(30)
        val r = w.day(DayInputs(residents = mapOf(w.dest.id to 80_000L))).spendingOf(w.dest.id)!!
        assertTrue(r.spend.total.raw > 0L)
        assertTrue(r.multiplierPpm > 1_000_000L)
        assertTrue(r.impact > r.spend.total)
        assertEquals(SpendCategory.values().size, r.uplifts.size)
        assertTrue(r.upliftOf(SpendCategory.RESTAURANTS) > 0L)
    }

    @Test fun moreGuestsMeanMoreRestaurantUplift() {
        val small = TourismWorld(homeOutbound = 40L, abroadOutbound = 40L); val big = TourismWorld(homeOutbound = 400L, abroadOutbound = 600L)
        small.hotel(rooms = intArrayOf(400, 0, 0)); big.hotel(rooms = intArrayOf(400, 0, 0))
        small.days(30); big.days(30)
        val inputs = DayInputs(residents = mapOf(small.dest.id to 80_000L))
        val a = small.day(inputs).spendingOf(small.dest.id)!!
        val b = big.day(inputs).spendingOf(big.dest.id)!!
        assertTrue(b.upliftOf(SpendCategory.RESTAURANTS) > a.upliftOf(SpendCategory.RESTAURANTS))
    }

    @Test fun reputationFollowsTheQualityOfStays() {
        val good = TourismWorld(seed = 8L); val bad = TourismWorld(seed = 8L)
        good.hotel(); bad.hotel()
        good.days(120)
        bad.days(120, DayInputs(staff = ShareStaffSupply(100_000L)))
        assertTrue(bad.dest.reputationPpm < good.dest.reputationPpm)
    }

    @Test fun domesticShareReflectsTheOriginsOfVisitors() {
        val w = TourismWorld()
        w.hotel()
        w.days(5)
        assertTrue(w.dest.domesticSharePpm in 1..999_999L)
    }

    @Test fun bookingsForPastNightsAreDroppedAndTheHorizonIsRespected() {
        val w = TourismWorld()
        val p = w.hotel()
        w.days(40)
        val now = w.tick
        assertTrue(p.bookedNights.all { it >= now })
        assertTrue(p.bookedNights.all { it < now + Seasonality.HORIZON })
    }

    @Test fun monthEndRollsUpTheMonth() {
        val w = TourismWorld(startTick = 120L)
        w.hotel()
        w.days(30)
        assertEquals(1, w.dest.months.size)
        val m = w.dest.months[0]
        assertEquals(4L, m.month)
        assertEquals(w.dest.days.sumOf { it.roomsSold }, m.roomsSold)
        assertEquals(w.dest.days.sumOf { it.arrivals }, m.arrivals)
    }

    @Test fun aScarceDestinationGetsDearerAndACheapOneDoesNot() {
        val tight = TourismWorld(homeOutbound = 800L, abroadOutbound = 900L)
        val t = tight.hotel(rooms = intArrayOf(30, 0, 0))
        val loose = TourismWorld(homeOutbound = 20L, abroadOutbound = 20L)
        val l = loose.hotel(rooms = intArrayOf(300, 0, 0))
        tight.service.setPricing(t.id, RatePolicy.DYNAMIC, t.settings.rackRate).getOrThrow()
        loose.service.setPricing(l.id, RatePolicy.DYNAMIC, l.settings.rackRate).getOrThrow()
        tight.days(150); loose.days(150)
        assertTrue("${tight.dest.priceLevelPpm} vs ${loose.dest.priceLevelPpm}", tight.dest.priceLevelPpm > loose.dest.priceLevelPpm)
    }

    @Test fun invariantsHoldOnADenseWorld() {
        val w = RichWorld.build(days = 60)
        TourismInvariants.check(w.state)
        assertFalse(w.state.properties.any { it.days.isEmpty() })
    }

    @Test fun anOperatorAllotmentFillsRoomsAndReleasesTheRest() {
        val w = TourismWorld()
        val p = w.hotel()
        val op = w.operator()
        val a = w.allotment(op, p, rooms = 10, release = 3, committed = 0L)
        w.days(40)
        assertTrue(a.lifetimeSold > 0L)
        assertTrue(p.days.drop(warmUp).any { it.roomRevenue.raw > 0L })
        for (d in a.soldDates) assertTrue(a.soldOn(d) <= 10)
    }

    @Test fun committedRoomsAreOwedEvenWhenUnsold() {
        val w = TourismWorld(homeOutbound = 5L, abroadOutbound = 5L)
        val p = w.hotel()
        val weak = w.operator(strength = 1L)
        val a = w.allotment(weak, p, rooms = 20, committed = 1_000_000L, netShare = 600_000L)
        w.days(45)
        assertTrue(a.monthShortfallRooms > 0L || a.lifetimeShortfall.raw > 0L)
        assertTrue(a.lifetimeShortfall.raw > 0L)
    }

    @Test fun anAllotmentEndsOnItsLastDay() {
        val w = TourismWorld()
        val p = w.hotel()
        val a = w.allotment(w.operator(), p, days = 12)
        val events = w.days(20).flatMap { it.events }
        assertEquals(AllotmentStatus.ENDED, a.status)
        assertEquals(1, events.count { it is TourismEvent.AllotmentEnded && it.allotment == a.id })
    }

    @Test fun platformsDeliverGuestsAndEarnCommissionOnTheirBookings() {
        val w = TourismWorld()
        val p = w.hotel()
        val i = w.platform(commission = 200_000L)
        w.service.listWith(p.id, i.id).getOrThrow()
        w.days(40)
        assertTrue(i.lifetimeRooms > 0L)
        assertTrue(i.lifetimeCommission.raw > 0L)
        val ratio = i.lifetimeCommission.raw * 1_000_000L / p.days.sumOf { it.roomRevenue.raw }.coerceAtLeast(1L)
        assertTrue("share $ratio", ratio in 1..200_000L)
    }

    @Test fun anUnlistedHotelPaysNoCommission() {
        val w = TourismWorld()
        val p = w.hotel()
        w.platform()
        w.days(40)
        assertEquals(Money.ZERO, Money.sum(p.days.map { it.commissions }))
    }

    @Test fun anUnlistedHotelStillFillsThroughDirectBookings() {
        val w = TourismWorld()
        val p = w.hotel()
        w.platform()
        w.days(40)
        assertTrue(p.days.takeLast(10).sumOf { it.roomsSold } > 0)
    }
}
