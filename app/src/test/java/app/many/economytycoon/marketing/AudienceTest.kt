package app.many.economytycoon.marketing

import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.marketing.MarketingTestSupport.harbour
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.south
import app.many.economytycoon.world.WorldTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudienceTest {
    @Test fun segmentSharesSumTheirBands() {
        val a = Audience.even(north, 1_000_000L)
        assertEquals(1_000_000L, a.segmentShare(TargetSegment.EVERYONE))
        assertEquals(450_000L, a.segmentShare(TargetSegment.BUDGET))
        assertEquals(120_000L, a.segmentShare(TargetSegment.PREMIUM))
        assertEquals(120_000L, a.segmentSize(TargetSegment.PREMIUM))
    }

    @Test fun regionCoverageOfACityIsItsShare() {
        val city = Audience.even(north, 100_000L, city = harbour, regionInhabitants = 500_000L)
        assertEquals(200_000L, city.regionCoverage(TargetSegment.EVERYONE))
        assertEquals(24_000L, city.regionCoverage(TargetSegment.PREMIUM))
    }

    @Test fun fitWeightsBandsByTheirSize() {
        val a = Audience.even(north, 1_000_000L)
        val everyone = a.fit(Channel.IN_STORE.spec, TargetSegment.EVERYONE)
        val low = a.fit(Channel.IN_STORE.spec, TargetSegment.BUDGET)
        assertEquals(Channel.IN_STORE.spec.fit(IncomeBand.LOW), low)
        assertTrue(everyone < low)
    }

    @Test fun fitOfAnEmptySegmentIsZero() {
        val a = Audience(north, null, 1_000L, 1_000L, listOf(1_000_000L, 0L, 0L))
        assertEquals(0L, a.fit(Channel.RADIO.spec, TargetSegment.PREMIUM))
    }

    @Test(expected = IllegalArgumentException::class) fun anAudienceCannotExceedItsRegion() {
        Audience(north, null, 10L, 5L, listOf(1L, 1L, 1L))
    }

    @Test(expected = IllegalArgumentException::class) fun anAudienceNeedsThreeBands() {
        Audience(north, null, 10L, 10L, listOf(500_000L, 500_000L))
    }

    @Test fun fixedSourceLooksUpRegionsAndCities() {
        val src = MarketingTestSupport.audiences()
        assertEquals(listOf(north, south), src.regions())
        assertEquals(500_000L, src.audience(north)?.inhabitants)
        assertEquals(100_000L, src.audience(north, harbour)?.inhabitants)
        assertNull(src.audience(south, harbour))
        assertNull(src.audience(app.many.economytycoon.core.RegionId(99)))
    }

    @Test fun worldSourceReadsPopulationAndIncome() {
        val (world, _) = WorldTestSupport.generate(11L)
        val src = WorldAudienceSource(world)
        val regions = src.regions()
        assertTrue(regions.isNotEmpty())
        var total = 0L
        for (r in regions) {
            val a = src.audience(r)
            assertNotNull(a)
            assertEquals(1_000_000L, a!!.bandPpm.sum())
            assertEquals(a.inhabitants, a.regionInhabitants)
            assertTrue(a.incomeIndexPpm > 0L)
            total += a.inhabitants
        }
        assertEquals(world.cities().sumOf { world.population(it.id).total }, total)
    }

    @Test fun worldSourceNarrowsToACity() {
        val (world, _) = WorldTestSupport.generate(11L)
        val src = WorldAudienceSource(world)
        val city = world.cities().first()
        val a = src.audience(city.region, city.id)
        assertNotNull(a)
        assertEquals(world.population(city.id).total, a!!.inhabitants)
        assertTrue(a.regionInhabitants >= a.inhabitants)
        assertNull(src.audience(app.many.economytycoon.core.RegionId(-5)))
    }

    @Test fun worldSourceRefusesACityOfAnotherRegion() {
        val (world, _) = WorldTestSupport.generate(11L)
        val src = WorldAudienceSource(world)
        val city = world.cities().first()
        val other = world.regions().first { it.id != city.region }
        assertNull(src.audience(other.id, city.id))
    }
}
