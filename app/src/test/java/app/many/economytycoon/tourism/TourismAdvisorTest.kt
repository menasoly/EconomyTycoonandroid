package app.many.economytycoon.tourism

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismAdvisorTest {
    private val advisor = TourismAdvisor()

    private fun facts(
        policy: RatePolicy = RatePolicy.DYNAMIC, overbook: Long = 0L, occ14: Long = 750_000L, occ30: Long = 750_000L, adrIndex: Long = 1_000_000L,
        rating: Int = 40, condition: Long = 900_000L, service: Long = 1_000_000L, walked: Int = 0, soldOut: Int = 0, falloff: Long = 100_000L,
        outlook: Long = 1_000_000L, staffNow: Int = 20, staffNext: Int = 20, commission: Long = 0L, listed: Int = 1, platforms: Int = 1,
        sellThrough: Long? = null, unmet: Long = 0L, shocks: Int = 0, tax: Long = 0L, stars: Int = 3, maxStars: Int = 5,
    ) = PropertyFacts(
        PropertyId(1), DestinationId(1), "Hotel Sol", policy, overbook, 100, occ14, occ30, adrIndex, 1_000_000L, rating, condition, service, walked, soldOut,
        falloff, outlook, staffNow, staffNext, commission, listed, platforms, sellThrough, unmet, shocks, tax, stars, maxStars,
    )

    private fun codes(f: PropertyFacts) = advisor.all(f).map { it.code }

    @Test fun aHealthyPropertyHasNothingUrgentToHear() {
        assertTrue(advisor.advise(facts()).isEmpty())
    }

    @Test fun aFullHotelBelowTheMarketRateShouldRaisePrices() {
        val i = advisor.all(facts(occ14 = 960_000L, adrIndex = 900_000L)).first { it.code == "price-too-low" }
        assertTrue(i.priority in 55..70)
        assertTrue(i.title.contains("Hotel Sol"))
    }

    @Test fun noPriceAdviceWhenAlreadyDearOrNotFull() {
        assertFalse("dear", codes(facts(occ14 = 960_000L, adrIndex = 1_200_000L)).contains("price-too-low"))
        assertFalse("not full", codes(facts(occ14 = 800_000L, adrIndex = 900_000L)).contains("price-too-low"))
    }

    @Test fun anEmptyDearHotelShouldCutPrices() {
        assertTrue(codes(facts(occ14 = 350_000L, adrIndex = 1_250_000L)).contains("price-too-high"))
        assertFalse(codes(facts(occ14 = 350_000L, adrIndex = 1_000_000L)).contains("price-too-high"))
        assertFalse(codes(facts(occ14 = 600_000L, adrIndex = 1_250_000L)).contains("price-too-high"))
    }

    @Test fun priceAdvicePriorityGrowsWithTheProblem() {
        val a = advisor.all(facts(occ14 = 930_000L, adrIndex = 900_000L)).first { it.code == "price-too-low" }.priority
        val b = advisor.all(facts(occ14 = 1_000_000L, adrIndex = 900_000L)).first { it.code == "price-too-low" }.priority
        assertTrue(b > a)
    }

    @Test fun fixedPricingAtTheExtremesSuggestsDynamicPricing() {
        assertTrue(codes(facts(policy = RatePolicy.FIXED, occ14 = 950_000L)).contains("use-dynamic-pricing"))
        assertTrue(codes(facts(policy = RatePolicy.FIXED, occ14 = 300_000L)).contains("use-dynamic-pricing"))
        assertFalse(codes(facts(policy = RatePolicy.FIXED, occ14 = 750_000L)).contains("use-dynamic-pricing"))
        assertFalse(codes(facts(policy = RatePolicy.DYNAMIC, occ14 = 950_000L)).contains("use-dynamic-pricing"))
    }

    @Test fun aBusySeasonAheadCallsForHiring() {
        val i = advisor.all(facts(outlook = 1_400_000L, staffNext = 31)).first { it.code == "seasonal-staffing" }
        assertTrue(i.title.startsWith("Hire"))
        assertTrue(i.detail.contains("31"))
        assertTrue(i.priority >= 48)
    }

    @Test fun aQuietSeasonAheadCallsForTrimming() {
        val i = advisor.all(facts(outlook = 700_000L, staffNext = 15)).first { it.code == "seasonal-staffing" }
        assertTrue(i.title.startsWith("Slow season"))
        assertEquals(32, i.priority)
    }

    @Test fun smallSwingsAreIgnoredAndSoIsAHotelWithoutStaff() {
        assertFalse(codes(facts(outlook = 1_100_000L)).contains("seasonal-staffing"))
        assertFalse(codes(facts(outlook = 1_800_000L, staffNow = 0)).contains("seasonal-staffing"))
    }

    @Test fun shortStaffingIsFlaggedAndScaledByHowShort() {
        val mild = advisor.all(facts(service = 800_000L)).first { it.code == "understaffed" }.priority
        val severe = advisor.all(facts(service = 300_000L)).first { it.code == "understaffed" }.priority
        assertTrue(severe > mild)
        assertFalse(codes(facts(service = 850_000L)).contains("understaffed"))
    }

    @Test fun walkedGuestsOnlyMatterWithOverbookingOn() {
        assertTrue(codes(facts(walked = 5, overbook = 100_000L)).contains("walked-guests"))
        assertFalse(codes(facts(walked = 5, overbook = 0L)).contains("walked-guests"))
        assertFalse(codes(facts(walked = 2, overbook = 100_000L)).contains("walked-guests"))
    }

    @Test fun soldOutHotelsWithFallThroughMayOverbook() {
        assertTrue(codes(facts(soldOut = 8, falloff = 150_000L)).contains("overbooking-chance"))
        assertFalse(codes(facts(soldOut = 8, falloff = 40_000L)).contains("overbooking-chance"))
        assertFalse(codes(facts(soldOut = 2, falloff = 150_000L)).contains("overbooking-chance"))
        assertFalse(codes(facts(soldOut = 8, falloff = 150_000L, overbook = 50_000L)).contains("overbooking-chance"))
    }

    @Test fun lowReviewsAndWornBuildingsAreFlagged() {
        assertTrue(codes(facts(rating = 28)).contains("low-reviews"))
        assertFalse(codes(facts(rating = 35)).contains("low-reviews"))
        assertTrue(codes(facts(condition = 300_000L)).contains("condition-low"))
        assertFalse(codes(facts(condition = 550_000L)).contains("condition-low"))
    }

    @Test fun unsoldAllotmentsAreFlaggedOnlyWhenThereIsAHistory() {
        assertTrue(codes(facts(sellThrough = 150_000L)).contains("allotment-unsold"))
        assertFalse(codes(facts(sellThrough = 600_000L)).contains("allotment-unsold"))
        assertFalse(codes(facts(sellThrough = null)).contains("allotment-unsold"))
    }

    @Test fun heavyCommissionIsFlagged() {
        val i = advisor.all(facts(commission = 260_000L)).first { it.code == "commission-heavy" }
        assertTrue(i.title.contains("26%"))
        assertFalse(codes(facts(commission = 150_000L)).contains("commission-heavy"))
    }

    @Test fun anEmptyHotelOffPlatformsShouldList() {
        assertTrue(codes(facts(listed = 0, platforms = 2, occ30 = 400_000L)).contains("not-listed"))
        assertFalse(codes(facts(listed = 0, platforms = 0, occ30 = 400_000L)).contains("not-listed"))
        assertFalse(codes(facts(listed = 0, platforms = 2, occ30 = 800_000L)).contains("not-listed"))
        assertFalse(codes(facts(listed = 1, platforms = 2, occ30 = 400_000L)).contains("not-listed"))
    }

    @Test fun aFullTownWithUnmetDemandShouldGrowOrRaisePrices() {
        assertTrue(codes(facts(unmet = 300_000L, occ30 = 900_000L)).contains("destination-full"))
        assertFalse(codes(facts(unmet = 300_000L, occ30 = 500_000L)).contains("destination-full"))
        assertFalse(codes(facts(unmet = 100_000L, occ30 = 900_000L)).contains("destination-full"))
    }

    @Test fun adverseEventsAreTheTopPriority() {
        val all = advisor.all(facts(shocks = 2, tax = 90_000L, rating = 30))
        assertEquals("shock-active", all.first().code)
        assertEquals(66, all.first().priority)
    }

    @Test fun aHeavyLevyIsMentionedButLow() {
        val i = advisor.all(facts(tax = 120_000L)).first { it.code == "tax-heavy" }
        assertEquals(26, i.priority)
        assertNull(advisor.all(facts(tax = 50_000L)).firstOrNull { it.code == "tax-heavy" })
    }

    @Test fun aStarClimbIsSuggestedForAFullWellLikedHouse() {
        assertTrue(codes(facts(rating = 44, occ30 = 880_000L, adrIndex = 1_100_000L)).contains("upgrade-chance"))
        assertFalse(codes(facts(rating = 44, occ30 = 880_000L, adrIndex = 1_100_000L, stars = 5)).contains("upgrade-chance"))
        assertFalse(codes(facts(rating = 40, occ30 = 880_000L, adrIndex = 1_100_000L)).contains("upgrade-chance"))
    }

    @Test fun adviceComesSortedByPriorityThenCode() {
        val all = advisor.all(facts(occ14 = 960_000L, adrIndex = 800_000L, service = 400_000L, condition = 300_000L, rating = 28, shocks = 1, tax = 100_000L))
        assertTrue(all.size >= 5)
        assertEquals(all.sortedWith(compareByDescending<TourismInsight> { it.priority }.thenBy { it.code }), all)
    }

    @Test fun theMinimumPriorityFiltersTheList() {
        val f = facts(tax = 120_000L, shocks = 1)
        assertEquals(2, advisor.advise(f, 0).size)
        assertEquals(1, advisor.advise(f, 30).size)
        assertTrue(advisor.advise(f, 90).isEmpty())
    }

    @Test fun everyCodeIsUniqueAndPrioritiesAreInRange() {
        val worst = facts(
            policy = RatePolicy.FIXED, overbook = 80_000L, occ14 = 990_000L, occ30 = 990_000L, adrIndex = 800_000L, rating = 25, condition = 100_000L, service = 100_000L,
            walked = 20, outlook = 1_500_000L, commission = 300_000L, listed = 0, platforms = 2, sellThrough = 100_000L, unmet = 400_000L, shocks = 3, tax = 200_000L,
        )
        val all = advisor.all(worst)
        assertEquals(all.map { it.code }.toSet().size, all.size)
        assertTrue(all.all { it.priority in 0..100 })
    }

    @Test fun customRulesReplaceTheDefaults() {
        val only = TourismAdvisor(listOf { f -> TourismInsight("x", f.property, f.destination, 10, "t", "d") })
        assertEquals(listOf("x"), only.all(facts()).map { it.code })
        assertTrue(only.advise(facts(), 20).isEmpty())
    }

    @Test fun insightsRejectBadPriorities() {
        try { TourismInsight("x", null, DestinationId(1), 101, "t", "d"); throw AssertionError("accepted") } catch (e: IllegalArgumentException) { /* expected */ }
        assertNotNull(TourismInsight("x", null, DestinationId(1), 100, "t", "d"))
    }

    @Test fun factsDrawnFromARealPropertyFeedTheRules() {
        val w = TourismWorld(homeOutbound = 700L, abroadOutbound = 900L)
        val p = w.hotel(rooms = intArrayOf(30, 0, 0))
        w.days(45)
        val f = PropertyFacts.of(w.state, p, w.tick)
        assertEquals(p.id, f.property)
        assertTrue(f.occupancy14Ppm > 500_000L)
        assertTrue(f.staffNow > 0)
        assertTrue(advisor.all(f).isNotEmpty())
    }

    @Test fun anUnderstaffedRealHotelGetsTheStaffingAdvice() {
        val w = TourismWorld()
        val p = w.hotel()
        w.days(30, DayInputs(staff = ShareStaffSupply(500_000L)))
        val f = PropertyFacts.of(w.state, p, w.tick)
        assertTrue(f.serviceLevel7Ppm < TourismAdvisor.UNDERSTAFFED_PPM)
        assertTrue(advisor.all(f).any { it.code == "understaffed" })
    }

    @Test fun aRealHotelHitByAStrikeIsWarned() {
        val w = TourismWorld()
        val p = w.hotel()
        w.service.triggerShock(ShockKind.STRIKE, w.dest.id, null, tickOf(w), 10, 800_000L, "Walkout").getOrThrow()
        w.days(4)
        assertTrue(advisor.all(PropertyFacts.of(w.state, p, w.tick)).any { it.code == "shock-active" })
    }

    private fun tickOf(w: TourismWorld) = app.many.economytycoon.core.Tick(w.tick)
}
