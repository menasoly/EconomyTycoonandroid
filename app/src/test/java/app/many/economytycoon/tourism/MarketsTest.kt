package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketsTest {
    private fun market(id: Long = 1, country: Long? = 1, at: Point = Point(0, 0), outbound: Long = 10_000L, mix: LongArray = longArrayOf(400_000L, 300_000L, 200_000L, 100_000L)) =
        OriginMarket(OriginId(id), "M$id", country?.let { CountryId(it) }, at).also { it.configure(outbound, 1_000_000L, mix) }

    private fun candidate(id: Long, country: Long = 1, at: Point = Point(0, 0), value: Long = 700_000L, arrivals: Long = 1_000_000L) =
        FlowCandidate(DestinationId(id), CountryId(country), at, LongArray(4) { value }, LongArray(4) { arrivals })

    @Test fun distanceDecayIsOneAtZeroAndAHalfAtTolerance() {
        assertEquals(1_000_000L, VisitorFlows.distanceFactorPpm(0, 300))
        assertEquals(500_000L, VisitorFlows.distanceFactorPpm(300, 300))
        assertTrue(VisitorFlows.distanceFactorPpm(900, 300) < 300_000L)
        assertEquals(1_000_000L, VisitorFlows.distanceFactorPpm(-5, 300))
    }

    @Test fun poolFollowsTheMixSeasonAndWeekday() {
        val m = market()
        val summer = VisitorFlows.pool(m, VisitorSegment.FAMILY, Tick(7 * 30 * 1 + 14))
        val winter = VisitorFlows.pool(m, VisitorSegment.FAMILY, Tick(14))
        assertTrue(summer > winter)
        assertEquals(0L, VisitorFlows.pool(m, VisitorSegment.FAMILY, Tick(14), originShockPpm = 0L))
        assertEquals(0L, VisitorFlows.pool(m, VisitorSegment.FAMILY, Tick(14), originShockPpm = -4L))
    }

    @Test fun anEmptyMarketSendsNobody() {
        val out = VisitorFlows.allocate(listOf(market(outbound = 0L)), listOf(candidate(1)), Tick(200))
        assertEquals(0L, VisitorFlows.total(out.getValue(DestinationId(1))))
    }

    @Test fun noCandidatesNoFlows() {
        assertTrue(VisitorFlows.allocate(listOf(market()), emptyList(), Tick(200)).isEmpty())
    }

    @Test fun tripsAreNeverCreated() {
        val m = market()
        val cands = listOf(candidate(1), candidate(2, value = 500_000L), candidate(3, value = 900_000L))
        val out = VisitorFlows.allocate(listOf(m), cands, Tick(200))
        val sent = out.values.sumOf { VisitorFlows.total(it) }
        val pool = VisitorSegment.values().sumOf { VisitorFlows.pool(m, it, Tick(200)) }
        assertTrue(sent <= pool)
        assertTrue(sent > 0L)
    }

    @Test fun aMoreAppealingDestinationGetsMore() {
        val out = VisitorFlows.allocate(listOf(market()), listOf(candidate(1, value = 400_000L), candidate(2, value = 900_000L)), Tick(200))
        assertTrue(VisitorFlows.total(out.getValue(DestinationId(2))) > VisitorFlows.total(out.getValue(DestinationId(1))))
    }

    @Test fun aNearbyDestinationBeatsAFarOneOfEqualAppeal() {
        val out = VisitorFlows.allocate(
            listOf(market(at = Point(0, 0), country = null)), listOf(candidate(1, at = Point(50, 0)), candidate(2, at = Point(900, 900))), Tick(200),
        )
        assertTrue(VisitorFlows.total(out.getValue(DestinationId(1))) > VisitorFlows.total(out.getValue(DestinationId(2))))
    }

    @Test fun homeCountryDestinationsGetADomesticBonus() {
        val out = VisitorFlows.allocate(listOf(market(country = 1)), listOf(candidate(1, country = 1), candidate(2, country = 2)), Tick(200))
        assertTrue(VisitorFlows.total(out.getValue(DestinationId(1))) > VisitorFlows.total(out.getValue(DestinationId(2))))
    }

    @Test fun arrivalMultipliersChangeArrivalsButNotTheShareOfRivals() {
        val base = VisitorFlows.allocate(listOf(market()), listOf(candidate(1), candidate(2)), Tick(200))
        val boosted = VisitorFlows.allocate(listOf(market()), listOf(candidate(1, arrivals = 1_500_000L), candidate(2)), Tick(200))
        assertEquals(VisitorFlows.total(base.getValue(DestinationId(2))), VisitorFlows.total(boosted.getValue(DestinationId(2))))
        assertTrue(VisitorFlows.total(boosted.getValue(DestinationId(1))) > VisitorFlows.total(base.getValue(DestinationId(1))))
    }

    @Test fun originShocksScaleTheWholeMarket() {
        val full = VisitorFlows.allocate(listOf(market()), listOf(candidate(1)), Tick(200))
        val half = VisitorFlows.allocate(listOf(market()), listOf(candidate(1)), Tick(200)) { 500_000L }
        val a = VisitorFlows.total(full.getValue(DestinationId(1)))
        val b = VisitorFlows.total(half.getValue(DestinationId(1)))
        assertTrue(Math.abs(b * 2 - a) < 20L)
    }

    @Test fun allocationIsDeterministic() {
        val m = listOf(market(1), market(2, country = 2, at = Point(300, 300)))
        val c = listOf(candidate(1), candidate(2, country = 2, at = Point(100, 100)))
        assertEquals(VisitorFlows.allocate(m, c, Tick(321)), VisitorFlows.allocate(m, c, Tick(321)))
    }

    @Test fun cellsComeInMarketThenSegmentOrder() {
        val out = VisitorFlows.allocate(listOf(market(1), market(2)), listOf(candidate(1)), Tick(200))
        val cells = out.getValue(DestinationId(1))
        assertEquals(cells.sortedWith(compareBy({ it.origin }, { it.segment })), cells)
    }

    @Test fun personsFiltersBySegment() {
        val cells = listOf(FlowCell(OriginId(1), VisitorSegment.BUDGET, 5), FlowCell(OriginId(2), VisitorSegment.BUDGET, 7), FlowCell(OriginId(1), VisitorSegment.LUXURY, 1))
        assertEquals(12L, VisitorFlows.persons(cells, VisitorSegment.BUDGET))
        assertEquals(13L, VisitorFlows.total(cells))
    }

    @Test(expected = IllegalArgumentException::class) fun aMixThatDoesNotSumToOneIsRejected() {
        market(mix = longArrayOf(500_000L, 500_000L, 500_000L, 0L))
    }

    @Test(expected = IllegalArgumentException::class) fun negativeOutboundIsRejected() {
        market(outbound = -1L)
    }

    @Test fun marketHashChangesWithItsSettings() {
        fun h(m: OriginMarket) = CanonicalHasher().also { m.hashInto(it) }.finish()
        assertEquals(h(market()), h(market()))
        assertNotEquals(h(market()), h(market(outbound = 10_001L)))
        assertNotEquals(h(market()), h(market(mix = longArrayOf(300_000L, 300_000L, 300_000L, 100_000L))))
    }
}
