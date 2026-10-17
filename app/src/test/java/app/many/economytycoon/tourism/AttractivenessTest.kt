package app.many.economytycoon.tourism

import app.many.economytycoon.core.Season
import app.many.economytycoon.world.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttractivenessTest {
    private val w = TourismWorld()
    private val d = w.dest

    private fun ctx(season: Season = Season.SUMMER, drivers: DestinationDrivers = d.drivers, event: Long = 0L, safetyDrop: Long = 0L, weather: Long = 0L, tax: Long = 0L) =
        AppealContext(season, drivers, event, safetyDrop, weather, tax)

    @Test fun priceFactorIsOneAtTheReferenceLevel() {
        assertEquals(1_000_000L, Attractiveness.priceFactorPpm(1_000_000L, 800_000L))
    }

    @Test fun dearerPlacesLoseMoreWithHighSensitivity() {
        val budget = Attractiveness.priceFactorPpm(1_500_000L, VisitorSegment.BUDGET.profile.priceSensitivityPpm)
        val luxury = Attractiveness.priceFactorPpm(1_500_000L, VisitorSegment.LUXURY.profile.priceSensitivityPpm)
        assertTrue(budget < luxury)
        assertTrue(luxury < 1_000_000L)
    }

    @Test fun cheapPlacesGainButWithinTheCeiling() {
        assertTrue(Attractiveness.priceFactorPpm(600_000L, 1_000_000L) > 1_000_000L)
        assertEquals(Attractiveness.MAX_PRICE_FACTOR_PPM, Attractiveness.priceFactorPpm(0L, 5_000_000L))
        assertEquals(Attractiveness.MIN_PRICE_FACTOR_PPM, Attractiveness.priceFactorPpm(9_000_000L, 5_000_000L))
    }

    @Test fun weatherPenaltiesAreOrderedBySeverity() {
        assertTrue(Attractiveness.weatherPenaltyPpm(WeatherKind.FLOOD) > Attractiveness.weatherPenaltyPpm(WeatherKind.HEATWAVE))
        assertTrue(Attractiveness.weatherPenaltyPpm(WeatherKind.STORM) > Attractiveness.weatherPenaltyPpm(WeatherKind.DROUGHT))
        for (k in WeatherKind.values()) assertTrue(Attractiveness.weatherPenaltyPpm(k) in 1..1_000_000L)
    }

    @Test fun appealIsWithinBoundsForEverySegment() {
        for (s in VisitorSegment.values()) {
            val a = Attractiveness.appeal(d, s, ctx())
            assertTrue(a.scorePpm in 0..1_200_000L)
            assertTrue(a.valuePpm >= 0L)
        }
    }

    @Test fun partsListEveryDriverIncludingMarketing() {
        val a = Attractiveness.appeal(d, VisitorSegment.FAMILY, ctx())
        assertEquals(listOf("Climate", "Infrastructure", "Safety", "Culture", "Nature", "Nightlife", "Events", "Reputation", "Marketing"), a.parts.map { it.label })
    }

    @Test fun betterDriversGiveAHigherScore() {
        val weak = DestinationDrivers(200_000L, 300_000L, 200_000L, 200_000L, 100_000L)
        val strong = DestinationDrivers(900_000L, 900_000L, 900_000L, 900_000L, 900_000L)
        for (s in VisitorSegment.values()) {
            assertTrue(Attractiveness.appeal(d, s, ctx(drivers = strong)).scorePpm > Attractiveness.appeal(d, s, ctx(drivers = weak)).scorePpm)
        }
    }

    @Test fun safetyScaresHurtFamiliesMoreThanBusiness() {
        fun loss(s: VisitorSegment) = Attractiveness.appeal(d, s, ctx()).scorePpm - Attractiveness.appeal(d, s, ctx(safetyDrop = 600_000L)).scorePpm
        assertTrue(loss(VisitorSegment.FAMILY) > 0L)
        assertTrue(loss(VisitorSegment.FAMILY) > loss(VisitorSegment.BUSINESS) / 2)
    }

    @Test fun badWeatherLowersTheScore() {
        val fine = Attractiveness.appeal(d, VisitorSegment.LUXURY, ctx()).scorePpm
        val storm = Attractiveness.appeal(d, VisitorSegment.LUXURY, ctx(weather = 500_000L)).scorePpm
        assertTrue(storm < fine)
    }

    @Test fun eventsRaiseTheScore() {
        val plain = Attractiveness.appeal(d, VisitorSegment.BUSINESS, ctx()).scorePpm
        val event = Attractiveness.appeal(d, VisitorSegment.BUSINESS, ctx(event = 800_000L)).scorePpm
        assertTrue(event > plain)
    }

    @Test fun marketingLiftsTheScoreByAtMostItsCeiling() {
        val base = Attractiveness.appeal(d, VisitorSegment.FAMILY, ctx()).scorePpm
        d.marketingPpm[VisitorSegment.FAMILY.ordinal] = 1_000_000L
        val lifted = Attractiveness.appeal(d, VisitorSegment.FAMILY, ctx()).scorePpm
        assertTrue(lifted > base)
        assertTrue(lifted <= base + base * Attractiveness.MARKETING_LIFT_PPM / 1_000_000L + 1L)
        d.marketingPpm[VisitorSegment.FAMILY.ordinal] = 0L
    }

    @Test fun marketingOnOneSegmentDoesNotLiftAnother() {
        val before = Attractiveness.appeal(d, VisitorSegment.BUDGET, ctx()).scorePpm
        d.marketingPpm[VisitorSegment.LUXURY.ordinal] = 1_000_000L
        assertEquals(before, Attractiveness.appeal(d, VisitorSegment.BUDGET, ctx()).scorePpm)
        d.marketingPpm[VisitorSegment.LUXURY.ordinal] = 0L
    }

    @Test fun taxesFeltByVisitorsLowerTheValueNotTheScore() {
        val plain = Attractiveness.appeal(d, VisitorSegment.BUDGET, ctx())
        val taxed = Attractiveness.appeal(d, VisitorSegment.BUDGET, ctx(tax = 150_000L))
        assertEquals(plain.scorePpm, taxed.scorePpm)
        assertTrue(taxed.valuePpm < plain.valuePpm)
    }

    @Test fun valueIsScoreTimesPriceFactor() {
        val a = Attractiveness.appeal(d, VisitorSegment.FAMILY, ctx())
        assertEquals(a.scorePpm * a.priceFactorPpm / 1_000_000L, a.valuePpm)
    }

    @Test fun summerBeatsWinterForLeisureOnAWarmCoast() {
        val summer = Attractiveness.appeal(d, VisitorSegment.FAMILY, ctx(Season.SUMMER)).scorePpm
        val winter = Attractiveness.appeal(d, VisitorSegment.FAMILY, ctx(Season.WINTER)).scorePpm
        assertTrue(summer > winter)
    }

    @Test fun meanValueIsBetweenTheSegmentExtremes() {
        val values = VisitorSegment.values().map { Attractiveness.appeal(d, it, ctx()).valuePpm }
        val mean = Attractiveness.meanValuePpm(d, ctx())
        assertTrue(mean >= values.min() && mean <= values.max())
    }

    @Test fun attractionsBoostADriverWithDiminishingReturns() {
        val one = Attractiveness.boosted(400_000L, listOf(300_000L))
        val two = Attractiveness.boosted(400_000L, listOf(300_000L, 300_000L))
        val many = Attractiveness.boosted(400_000L, List(50) { 300_000L })
        assertTrue(one > 400_000L)
        assertTrue(two > one)
        assertTrue(two - one < one - 400_000L)
        assertTrue(many <= 1_000_000L)
    }

    @Test fun noBoostsLeaveTheDriverAlone() {
        assertEquals(555_000L, Attractiveness.boosted(555_000L, emptyList()))
        assertEquals(1_000_000L, Attractiveness.boosted(1_000_000L, listOf(500_000L)))
        assertEquals(500_000L, Attractiveness.boosted(0L, listOf(500_000L)))
    }

    @Test fun anAppealBreakdownFromTheStateMatchesTheDirectCalculation() {
        val a = TourismAnalytics.appealBreakdown(d, VisitorSegment.FAMILY, w.tick, w.state)
        assertEquals(VisitorSegment.FAMILY, a.segment)
        assertTrue(a.scorePpm > 0L)
    }
}
