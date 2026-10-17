package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridAnalyticsTest {
    private fun gen(vararg pairs: Pair<Technology, Long>): List<Long> {
        val a = LongArray(Technology.values().size)
        for ((t, v) in pairs) a[t.ordinal] = v
        return a.toList()
    }

    private fun day(
        tick: Long, avg: Long, demand: Long = 1_000_000L, unserved: Long = 0L, shed: Int = 0,
        peakPrice: Long = avg, generation: List<Long> = gen(Technology.GAS to demand),
    ) = DaySummary(
        tick, Money.units(avg), Money.units(peakPrice), Money.units(avg), demand, unserved, 0L, 80_000L, generation, 0L, 0L,
        shed, 0, 0L, 300_000L, Money.units(unserved),
    )

    // ----- load -----

    @Test fun theLoadDurationCurveRunsFromThePeakDown() {
        assertEquals(listOf(9L, 7L, 5L, 3L, 1L), GridAnalytics.loadDuration(listOf(5L, 9L, 1L, 7L, 3L)))
    }

    @Test fun curvePointsSitAtEvenShares() {
        val points = GridAnalytics.loadDurationPoints(listOf(100L, 80L, 60L, 40L, 20L), points = 5)
        assertEquals(listOf(0L, 250_000L, 500_000L, 750_000L, 1_000_000L), points.map { it.sharePpm })
        assertEquals(listOf(100L, 80L, 60L, 40L, 20L), points.map { it.demandKw })
    }

    @Test fun anEmptyCurveHasNoPoints() {
        assertTrue(GridAnalytics.loadDurationPoints(emptyList()).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class) fun aCurveNeedsTwoPoints() {
        GridAnalytics.loadDurationPoints(listOf(1L), points = 1)
    }

    @Test fun aFlatLoadHasALoadFactorOfOne() {
        assertEquals(Rate.ONE_PPM, GridAnalytics.loadFactorPpm(List(24) { 500L }))
    }

    @Test fun theLoadFactorIsAverageOverPeak() {
        assertEquals(750_000L, GridAnalytics.loadFactorPpm(listOf(100L, 50L, 100L, 50L)))
        assertEquals(0L, GridAnalytics.loadFactorPpm(emptyList()))
        assertEquals(0L, GridAnalytics.loadFactorPpm(listOf(0L, 0L)))
    }

    @Test fun peakHoursCountTheHoursNearThePeak() {
        val d = listOf(100L, 95L, 91L, 90L, 89L, 50L)
        assertEquals(4, GridAnalytics.peakHours(d))
        assertEquals(1, GridAnalytics.peakHours(d, bandPpm = 0L))
        assertEquals(0, GridAnalytics.peakHours(emptyList()))
    }

    // ----- carbon and mix -----

    @Test fun carbonIsEnergyTimesTheTechnologyFactor() {
        val kwh = 2_000_000L
        val expected = kwh / 1_000L * Technology.COAL.profile.carbonKgPerMwh
        assertEquals(expected, GridAnalytics.carbonKg(gen(Technology.COAL to kwh)))
        val clean = (Technology.WIND.profile.carbonKgPerMwh + Technology.NUCLEAR.profile.carbonKgPerMwh) * (kwh / 1_000L)
        assertEquals(clean, GridAnalytics.carbonKg(gen(Technology.WIND to kwh, Technology.NUCLEAR to kwh)))
    }

    @Test fun intensityIsThePerUnitAverage() {
        val coal = Technology.COAL.profile.carbonKgPerMwh
        val gas = Technology.GAS.profile.carbonKgPerMwh
        assertEquals(coal, GridAnalytics.carbonIntensity(gen(Technology.COAL to 5_000L)))
        assertEquals((coal + gas) / 2, GridAnalytics.carbonIntensity(gen(Technology.COAL to 5_000L, Technology.GAS to 5_000L)))
        assertEquals((coal + Technology.WIND.profile.carbonKgPerMwh) / 2, GridAnalytics.carbonIntensity(gen(Technology.COAL to 5_000L, Technology.WIND to 5_000L)))
        assertEquals(0L, GridAnalytics.carbonIntensity(gen()))
    }

    @Test fun intensityOverAHistoryWindowUsesOnlyTheLatestDays() {
        val h = listOf(day(1, 50, generation = gen(Technology.COAL to 10_000L)), day(2, 50, generation = gen(Technology.WIND to 10_000L)))
        assertEquals(Technology.WIND.profile.carbonKgPerMwh, GridAnalytics.carbonIntensity(h, 1))
        assertEquals((Technology.COAL.profile.carbonKgPerMwh + Technology.WIND.profile.carbonKgPerMwh) / 2, GridAnalytics.carbonIntensity(h, 2))
    }

    @Test fun renewableShareCountsWaterWindAndSun() {
        val g = gen(Technology.HYDRO to 100L, Technology.WIND to 100L, Technology.SOLAR to 100L, Technology.COAL to 300L, Technology.NUCLEAR to 100L)
        assertEquals(428_571L, GridAnalytics.renewableShare(g))
        assertEquals(0L, GridAnalytics.renewableShare(gen()))
    }

    @Test fun theFleetMixListsTheBiggestFirst() {
        val w = GridWorld()
        w.unit(Technology.GAS, 50_000L); w.unit(Technology.COAL, 150_000L); w.unit(Technology.GAS, 50_000L); w.unit(Technology.WIND, 100_000L)
        val mix = GridAnalytics.fleetMix(w.grid)
        assertEquals(listOf(Technology.COAL, Technology.GAS, Technology.WIND), mix.map { it.tech })
        assertEquals(listOf(150_000L, 100_000L, 100_000L), mix.map { it.installedKw })
        assertEquals(listOf(1, 2, 1), mix.map { it.units })
        assertTrue(Math.abs(Rate.ONE_PPM - mix.sumOf { it.sharePpm }) <= 3L)
        assertEquals(428_571L, mix.first().sharePpm)
    }

    @Test fun mothballedUnitsLeaveTheMix() {
        val w = GridWorld()
        val gas = w.unit(Technology.GAS, 50_000L); w.unit(Technology.COAL, 50_000L)
        w.service.mothball(gas.id).getOrThrow()
        assertEquals(listOf(Technology.COAL), GridAnalytics.fleetMix(w.grid).map { it.tech })
    }

    @Test fun capacityFactorIsOutputOverTheMaximum() {
        val h = listOf(day(1, 50, generation = gen(Technology.WIND to 12_000L)), day(2, 50, generation = gen(Technology.WIND to 24_000L)))
        // 2 days of 1 MW = 48 MWh possible, 36 MWh made
        assertEquals(750_000L, GridAnalytics.capacityFactorPpm(h, 2, Technology.WIND, 1_000L))
        assertEquals(1_000_000L, GridAnalytics.capacityFactorPpm(h, 1, Technology.WIND, 1_000L))
        assertEquals(0L, GridAnalytics.capacityFactorPpm(h, 2, Technology.WIND, 0L))
        assertEquals(0L, GridAnalytics.capacityFactorPpm(emptyList(), 2, Technology.WIND, 1_000L))
    }

    // ----- prices -----

    @Test fun theWindowAverageWeightsDaysByEnergyServed() {
        val h = listOf(day(1, 100, demand = 1_000_000L), day(2, 40, demand = 3_000_000L))
        assertEquals(Money.units(55), GridAnalytics.windowAveragePrice(h, 2))
        assertEquals(Money.units(40), GridAnalytics.windowAveragePrice(h, 1))
        assertEquals(Money.ZERO, GridAnalytics.windowAveragePrice(emptyList(), 5))
    }

    @Test fun statsOfNothingAreNull() {
        assertNull(GridAnalytics.priceStats(emptyList(), 30, Money.units(500)))
    }

    @Test fun statsDescribeTheSpread() {
        val h = listOf(10L, 20L, 30L, 40L, 50L).mapIndexed { i, p -> day(i.toLong(), p) }
        val s = GridAnalytics.priceStats(h, 30, Money.units(500)) ?: error("stats")
        assertEquals(5, s.days)
        assertEquals(Money.units(30), s.mean)
        assertEquals(Money.units(10), s.lowest)
        assertEquals(Money.units(50), s.highest)
        assertEquals(Money.units(30), s.median)
        assertEquals(Money(141_421L), s.standardDeviation)
        assertEquals(471_403L, s.volatilityPpm)
        assertEquals(Money.units(10), s.p10)
        assertEquals(Money.units(40), s.p90)
    }

    @Test fun spikeDaysReachHalfTheCap() {
        val h = listOf(day(1, 50, peakPrice = 50), day(2, 50, peakPrice = 250), day(3, 50, peakPrice = 400))
        assertEquals(2, GridAnalytics.priceStats(h, 3, Money.units(500))?.spikeDays)
    }

    @Test fun theTrendCompareTheHalvesOfTheWindow() {
        val rising = listOf(10L, 10L, 20L, 20L).mapIndexed { i, p -> day(i.toLong(), p) }
        assertEquals(1_000_000L, GridAnalytics.priceStats(rising, 4, Money.units(500))?.trendPpm)
        val falling = listOf(20L, 20L, 10L, 10L).mapIndexed { i, p -> day(i.toLong(), p) }
        assertEquals(-500_000L, GridAnalytics.priceStats(falling, 4, Money.units(500))?.trendPpm)
    }

    @Test fun aFlatMarketHasNoVolatility() {
        val h = List(6) { day(it.toLong(), 33) }
        val s = GridAnalytics.priceStats(h, 6, Money.units(500)) ?: error("stats")
        assertEquals(Money.ZERO, s.standardDeviation)
        assertEquals(0L, s.volatilityPpm)
        assertEquals(0L, s.trendPpm)
    }

    // ----- reliability -----

    @Test fun reliabilityCountsHoursAndMissingEnergy() {
        val h = listOf(
            day(1, 50, demand = 1_000_000L),
            day(2, 90, demand = 1_000_000L, unserved = 20_000L, shed = 3),
            day(3, 90, demand = 1_000_000L, unserved = 60_000L, shed = 5),
        )
        val r = GridAnalytics.reliability(h, 3)
        assertEquals(3, r.days)
        assertEquals(8, r.lossOfLoadHours)
        assertEquals(2, r.lossOfLoadDays)
        assertEquals(80_000L, r.energyNotServedKwh)
        assertEquals(60_000L, r.worstDayUnservedKwh)
        assertEquals(973_333L, r.availabilityPpm)
        assertEquals(Money.units(80_000), r.lostLoadValue)
        assertEquals(5, GridAnalytics.reliability(h, 1).lossOfLoadHours)
    }

    @Test fun nothingDemandedIsFullyAvailable() {
        assertEquals(Rate.ONE_PPM, GridAnalytics.reliability(emptyList(), 30).availabilityPpm)
    }

    @Test fun reserveMarginIsFirmCapacityOverThePeak() {
        val w = GridWorld()
        w.unit(Technology.GAS, 120_000L)
        val firm = w.grid.firmKw()
        assertEquals((firm - 100_000L) * Rate.ONE_PPM / 100_000L, GridAnalytics.reserveMarginPpm(w.grid, 100_000L))
        assertTrue(GridAnalytics.reserveMarginPpm(w.grid, 200_000L) < 0L)
        assertEquals(Rate.ONE_PPM, GridAnalytics.reserveMarginPpm(w.grid, 0L))
    }

    @Test fun aRealRunProducesConsistentNumbers() {
        val w = richWorld(20)
        val g = w.grid
        val r = GridAnalytics.reliability(g.history, 20)
        assertTrue(r.availabilityPpm in 0L..Rate.ONE_PPM)
        val stats = GridAnalytics.priceStats(g.history, 20, g.params.priceCap) ?: error("stats")
        assertTrue(stats.lowest <= stats.p10 && stats.p10 <= stats.median && stats.median <= stats.p90 && stats.p90 <= stats.highest)
        assertTrue(Math.abs(Rate.ONE_PPM - GridAnalytics.fleetMix(g).sumOf { it.sharePpm }) <= 4L)
        val intensity = GridAnalytics.carbonIntensity(g.history, 20)
        assertTrue(intensity in 0L..Technology.values().maxOf { it.profile.carbonKgPerMwh })
    }
}
