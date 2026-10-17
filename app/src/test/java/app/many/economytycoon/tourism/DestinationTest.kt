package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationTest {
    private val w = TourismWorld()
    private val d get() = w.dest

    private fun day(tick: Long, sold: Long = 50L, avail: Long = 100L, revenue: Long = 5_000L) =
        DestinationDay(tick, 100L, 200L, sold, avail, 10L, 1L, Money.units(revenue), 150L, 30L, 700_000L)

    @Test fun dayRatiosAreDerivedFromTheCounts() {
        val x = day(1)
        assertEquals(500_000L, x.occupancyPpm)
        assertEquals(Money.units(100), x.adr)
        assertEquals(0L, day(1, sold = 0L, avail = 0L).occupancyPpm)
        assertEquals(Money.ZERO, day(1, sold = 0L).adr)
    }

    @Test fun monthRatiosAreDerivedFromTheCounts() {
        val m = DestinationMonth(3, 1_000L, 2_000L, 4_000L, 100L, Money.units(200_000))
        assertEquals(500_000L, m.occupancyPpm)
        assertEquals(Money.units(100), m.adr)
        assertEquals(Money.units(50), m.revPar)
        assertEquals(Money.ZERO, DestinationMonth(0, 0, 0, 0, 0, Money.ZERO).revPar)
    }

    @Test fun historiesAreBounded() {
        repeat(Destination.HISTORY_DAYS + 30) { d.pushDay(day(it.toLong())) }
        assertEquals(Destination.HISTORY_DAYS, d.days.size)
        assertEquals(Destination.HISTORY_DAYS + 29L, d.lastDay?.tick)
        repeat(Destination.HISTORY_MONTHS + 5) { d.pushMonth(DestinationMonth(it.toLong(), 1, 1, 1, 0, Money.ZERO)) }
        assertEquals(Destination.HISTORY_MONTHS, d.months.size)
    }

    @Test fun reputationIsClamped() {
        d.setReputation(5_000_000L)
        assertEquals(1_000_000L, d.reputationPpm)
        d.setReputation(-3L)
        assertEquals(0L, d.reputationPpm)
    }

    @Test fun driversCanBeReplacedThroughTheService() {
        val better = DestinationDrivers(900_000L, 900_000L, 900_000L, 900_000L, 900_000L)
        w.service.setDrivers(d.id, better).getOrThrow()
        assertEquals(better, d.drivers)
        assertEquals(Rules.UNKNOWN_ENTITY, w.service.setDrivers(DestinationId(77), better).rejectionOrNull()?.rule)
    }

    @Test(expected = IllegalArgumentException::class) fun driversAboveTheRangeCannotBeBuilt() {
        DestinationDrivers(infrastructurePpm = 1_000_001L)
    }

    @Test(expected = IllegalArgumentException::class) fun driversBelowTheRangeCannotBeBuilt() {
        DestinationDrivers(safetyPpm = -1L)
    }

    @Test fun festivalsAreRangeChecked() {
        assertTrue(w.service.setFestival(d.id, 7, 10).isOk)
        assertEquals(7, d.festivalMonth)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setFestival(d.id, 13, 10).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setFestival(d.id, 7, 0).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setFestival(d.id, 7, 31).rejectionOrNull()?.rule)
        assertTrue(w.service.setFestival(d.id, 0, 7).isOk)
    }

    @Test fun duplicateCitiesAreRefused() {
        val again = w.service.addDestination(
            app.many.economytycoon.core.CityId(1), app.many.economytycoon.core.RegionId(1), app.many.economytycoon.core.CountryId(1), "Again",
            app.many.economytycoon.world.ClimateZone.TEMPERATE, app.many.economytycoon.world.Point(1, 1), false, DestinationDrivers(),
        )
        assertEquals(Rules.DUPLICATE, again.rejectionOrNull()?.rule)
    }

    @Test fun blankNamesAreRefused() {
        val r = w.service.addDestination(
            app.many.economytycoon.core.CityId(5), app.many.economytycoon.core.RegionId(1), app.many.economytycoon.core.CountryId(1), " ",
            app.many.economytycoon.world.ClimateZone.TEMPERATE, app.many.economytycoon.world.Point(1, 1), false, DestinationDrivers(),
        )
        assertEquals(Rules.INVALID_ARGUMENT, r.rejectionOrNull()?.rule)
    }

    @Test fun cityLookupFindsTheDestination() {
        assertEquals(d, w.state.destinationOfCity(app.many.economytycoon.core.CityId(1)))
        assertEquals(null, w.state.destinationOfCity(app.many.economytycoon.core.CityId(42)))
    }

    @Test fun hashChangesWithEverythingThatMatters() {
        fun h() = CanonicalHasher().also { d.hashInto(it) }.finish()
        val seen = HashSet<Any>()
        seen += h()
        d.setReputation(123_000L); assertTrue(seen.add(h()))
        d.setTaxes(Money.units(1), 10_000L, 20_000L); assertTrue(seen.add(h()))
        d.setFestival(3, 5); assertTrue(seen.add(h()))
        d.setPriceLevel(900_000L, 950_000L); assertTrue(seen.add(h()))
        d.setDomesticShare(1_000L); assertTrue(seen.add(h()))
        d.setUnmet(2_000L); assertTrue(seen.add(h()))
        d.pushDay(day(1)); assertTrue(seen.add(h()))
        d.pushMonth(DestinationMonth(1, 1, 1, 1, 0, Money.ZERO)); assertTrue(seen.add(h()))
        d.marketingPpm[2] = 500L; assertTrue(seen.add(h()))
        assertEquals(h(), h())
    }

    @Test fun identicalDestinationsHashAlike() {
        val a = TourismWorld().dest
        val b = TourismWorld().dest
        fun h(x: Destination) = CanonicalHasher().also { x.hashInto(it) }.finish()
        assertEquals(h(a), h(b))
        assertNotEquals(h(a), h(TourismWorld(climate = app.many.economytycoon.world.ClimateZone.COLD).dest))
    }
}
