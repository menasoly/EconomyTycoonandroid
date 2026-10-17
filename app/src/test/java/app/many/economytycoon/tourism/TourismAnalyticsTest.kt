package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismAnalyticsTest {
    private fun world(): TourismWorld {
        val w = TourismWorld()
        w.hotel(3); w.hotel(4, intArrayOf(60, 30, 10), rack = Money.units(190)); w.hotel(2, intArrayOf(80, 0, 0))
        w.days(60)
        return w
    }

    @Test fun kpisOfAnEmptyPropertyAreZero() {
        val w = TourismWorld()
        val k = TourismAnalytics.kpis(w.hotel())
        assertEquals(0, k.nights)
        assertEquals(0L, k.occupancyPpm)
        assertEquals(Money.ZERO, k.adr)
        assertEquals(Money.ZERO, k.revPar)
        assertEquals(Money.ZERO, k.goppar)
        assertEquals(0L, k.commissionShareOfRevenuePpm)
    }

    @Test fun revParIsOccupancyTimesAdrWithinRounding() {
        val w = world()
        for (p in w.state.properties) {
            val k = TourismAnalytics.kpis(p, 30)
            val product = RevenueManagement.revPar(k.adr, k.occupancyPpm)
            assertTrue(Math.abs(product.raw - k.revPar.raw) <= k.roomsSold.coerceAtLeast(1L))
        }
    }

    @Test fun theWindowLimitsTheNightsCounted() {
        val w = world()
        val p = w.state.properties[0]
        assertEquals(7, TourismAnalytics.kpis(p, 7).nights)
        assertEquals(60, TourismAnalytics.kpis(p, 500).nights)
        assertTrue(TourismAnalytics.kpis(p, 7).roomsAvailable < TourismAnalytics.kpis(p, 30).roomsAvailable)
    }

    @Test fun setKpisAddUp() {
        val w = world()
        val all = TourismAnalytics.setKpis(w.state.properties)
        assertEquals(w.state.properties.sumOf { TourismAnalytics.kpis(it).roomsSold }, all.roomsSold)
        assertEquals(Money.sum(w.state.properties.map { TourismAnalytics.kpis(it).roomRevenue }), all.roomRevenue)
        assertEquals(0L, TourismAnalytics.setKpis(emptyList()).roomsSold)
    }

    @Test fun goppar() {
        val w = world()
        val k = TourismAnalytics.kpis(w.state.properties[0])
        assertEquals(Money(k.gop.raw / k.roomsAvailable), k.goppar)
    }

    @Test fun theMarketIndexIsParAgainstItself() {
        val w = world()
        val ps = w.state.properties
        val a = TourismAnalytics.marketIndex(ps[0], ps)
        assertNotNull(a)
    }

    @Test fun theMarketIndexRanksADearerHotelAboveParOnRate() {
        val w = world()
        val ps = w.state.properties
        val dear = requireNotNull(TourismAnalytics.marketIndex(ps[1], ps))
        val cheap = requireNotNull(TourismAnalytics.marketIndex(ps[2], ps))
        assertTrue(dear.adrPpm > cheap.adrPpm)
        assertTrue(Math.abs(dear.occupancyPpm * dear.adrPpm / 1_000_000L - dear.revParPpm) <= 1L)
    }

    @Test fun aLoneHotelHasNoMarketToCompareWith() {
        val w = TourismWorld()
        val p = w.hotel()
        w.days(20)
        assertNull(TourismAnalytics.marketIndex(p, listOf(p)))
    }

    @Test fun destinationFiguresMatchTheirDays() {
        val w = world()
        val d = w.dest
        val days = d.days.takeLast(30)
        assertEquals(days.sumOf { it.roomsSold } * 1_000_000L / days.sumOf { it.roomsAvailable }, TourismAnalytics.occupancyPpm(d))
        assertEquals(Money(Money.sum(days.map { it.roomRevenue }).raw / days.sumOf { it.roomsSold }), TourismAnalytics.adr(d))
        val demand = days.sumOf { it.roomNightDemand }
        assertEquals(if (demand == 0L) 0L else days.sumOf { it.deniedRoomNights } * 1_000_000L / demand, TourismAnalytics.denialRatePpm(d))
    }

    @Test fun anEmptyDestinationHasZeroFigures() {
        val d = TourismWorld().dest
        assertEquals(0L, TourismAnalytics.occupancyPpm(d))
        assertEquals(Money.ZERO, TourismAnalytics.adr(d))
        assertEquals(0L, TourismAnalytics.denialRatePpm(d))
    }

    @Test fun seasonalityOfAYoungDestinationIsEmpty() {
        val w = TourismWorld()
        w.hotel()
        assertTrue(TourismAnalytics.seasonality(w.dest).isEmpty())
        assertEquals(1_000_000L, TourismAnalytics.seasonalityRangePpm(emptyList()))
    }

    @Test fun seasonalityHasOnePointPerMonthSeenAndAveragesNearOne() {
        val w = TourismWorld(startTick = 0L)
        w.hotel(); w.hotel()
        w.days(330)
        val pts = TourismAnalytics.seasonality(w.dest)
        assertEquals((1..11).toList(), pts.map { it.month })
        val mean = pts.sumOf { it.indexPpm } / pts.size
        assertTrue(mean in 950_000L..1_050_000L)
        assertTrue(TourismAnalytics.seasonalityRangePpm(pts) > 1_000_000L)
    }

    @Test fun aMediterraneanCoastPeaksInSummerWhenMonthsAreSeen() {
        val w = TourismWorld(startTick = 0L, homeOutbound = 60L, abroadOutbound = 80L)
        w.hotel(rooms = intArrayOf(400, 0, 0))
        w.days(330)
        val peak = TourismAnalytics.seasonality(w.dest).filter { it.month > 1 }.maxByOrNull { it.indexPpm }
        assertTrue(peak != null && peak.month in 6..9)
    }

    @Test fun peakConcentrationIsBetweenZeroAndOne() {
        val w = world()
        for (p in w.state.properties) assertTrue(TourismAnalytics.peakConcentrationPpm(p) in 0..1_000_000L)
    }

    @Test fun appealBreakdownGivesAScoreForEverySegment() {
        val w = world()
        for (s in VisitorSegment.values()) {
            val a = TourismAnalytics.appealBreakdown(w.dest, s, w.tick, w.state)
            assertEquals(s, a.segment)
            assertTrue(a.parts.isNotEmpty() && a.valuePpm > 0L)
        }
    }

    @Test fun anAttractionLiftsTheCultureDriverInTheBreakdown() {
        val w = TourismWorld()
        w.hotel()
        val before = TourismAnalytics.appealBreakdown(w.dest, VisitorSegment.FAMILY, w.tick, w.state).parts.first { it.label == "Culture" }.valuePpm
        w.attraction(AttractionKind.MUSEUM)
        val after = TourismAnalytics.appealBreakdown(w.dest, VisitorSegment.FAMILY, w.tick, w.state).parts.first { it.label == "Culture" }.valuePpm
        assertTrue(after > before)
    }

    @Test fun marketMixSumsToOneAndFollowsTheMarkets() {
        val w = TourismWorld()
        val mix = TourismAnalytics.marketMix(w.state)
        assertEquals(1_000_000L, mix.sum())
        assertTrue(mix[VisitorSegment.BUDGET.ordinal] > mix[VisitorSegment.LUXURY.ordinal])
    }

    @Test fun marketMixWithNoMarketsIsEven() {
        val mix = TourismAnalytics.marketMix(TourismState())
        assertEquals(1_000_000L, mix.sum())
        assertTrue(mix.all { it in 250_000L..250_003L })
    }
}
