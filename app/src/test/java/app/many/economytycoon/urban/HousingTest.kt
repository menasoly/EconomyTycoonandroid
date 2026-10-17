package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HousingTest {
    private val p = UrbanParams()

    @Test fun `allocation shares in proportion to weight`() {
        val a = Housing.allocate(1000L, longArrayOf(1, 1, 2), longArrayOf(10_000, 10_000, 10_000))
        assertEquals(listOf(250L, 250L, 500L), a.placed.toList())
        assertEquals(0L, a.unplaced)
    }

    @Test fun `allocation respects caps and passes the overflow on`() {
        val a = Housing.allocate(1000L, longArrayOf(1, 1, 1), longArrayOf(100, 5_000, 5_000))
        assertEquals(100L, a.placed[0])
        assertEquals(1000L, a.placed.sum())
        assertEquals(450L, a.placed[1])
    }

    @Test fun `allocation reports what does not fit`() {
        val a = Housing.allocate(1000L, longArrayOf(1, 1), longArrayOf(100, 200))
        assertEquals(listOf(100L, 200L), a.placed.toList())
        assertEquals(700L, a.unplaced)
    }

    @Test fun `zero weight or zero cap places nobody`() {
        val a = Housing.allocate(500L, longArrayOf(0, 3, 3), longArrayOf(100, 0, 1000))
        assertEquals(listOf(0L, 0L, 500L), a.placed.toList())
        assertEquals(Allocation(longArrayOf(0, 0), 7L), Housing.allocate(7L, longArrayOf(0, 0), longArrayOf(5, 5)))
        assertEquals(0L, Housing.allocate(-5L, longArrayOf(1), longArrayOf(5)).placed[0])
    }

    @Test fun `allocation is exact for awkward numbers`() {
        for (total in listOf(1L, 7L, 999L, 123_457L)) {
            val a = Housing.allocate(total, longArrayOf(3, 5, 7, 11), longArrayOf(40_000, 50_000, 1_000, 200_000))
            assertEquals(total, a.placed.sum() + a.unplaced)
            assertTrue(a.placed[2] <= 1_000L)
        }
    }

    @Test fun `allocation needs one cap per weight`() {
        try { Housing.allocate(1L, longArrayOf(1, 2), longArrayOf(1)); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `allocation is deterministic and independent of call order`() {
        val w = longArrayOf(13, 17, 19)
        val c = longArrayOf(500, 500, 500)
        assertEquals(Housing.allocate(777L, w, c), Housing.allocate(777L, w, c))
    }

    @Test fun `better located districts are more attractive and dear ones less so`() {
        val good = DistrictFactors(800, 700, 50, 50, 50)
        val bad = DistrictFactors(300, 200, 600, 500, 500)
        assertTrue(Housing.attractiveness(good, Scale.PPM) > Housing.attractiveness(bad, Scale.PPM))
        assertTrue(Housing.attractiveness(good, 2_000_000L) < Housing.attractiveness(good, Scale.PPM))
        assertTrue(Housing.attractiveness(bad, 9_000_000L) >= 1L)
    }

    @Test fun `anchor rent comes from wages`() {
        val a = Housing.anchorRent(Money.units(90), p)
        assertEquals(Money.units(90).raw * 30 * 480_000 / 1_000_000 * 300_000 / 1_000_000 / 35, a.raw)
        assertTrue(Housing.anchorRent(Money.units(180), p) > a)
        assertEquals(Money.ZERO, Housing.anchorRent(Money.ZERO, p))
    }

    @Test fun `rent targets rise when homes are scarce and fall when they stand empty`() {
        val anchor = Money.units(10)
        val flat = Housing.targetRent(anchor, Scale.PPM, p.targetVacancyPpm, p)
        assertEquals(anchor, flat)
        assertTrue(Housing.targetRent(anchor, Scale.PPM, 0L, p) > flat)
        assertTrue(Housing.targetRent(anchor, Scale.PPM, 300_000L, p) < flat)
        assertTrue(Housing.targetRent(anchor, 1_500_000L, p.targetVacancyPpm, p) > flat)
    }

    @Test fun `rent targets are bounded`() {
        val anchor = Money.units(10)
        assertTrue(Housing.targetRent(anchor, 99_000_000L, 0L, p).raw <= anchor.raw * 28 / 10 * 25 / 10)
        assertTrue(Housing.targetRent(anchor, 0L, Scale.PPM, p).raw >= anchor.raw * 4 / 10 * 4 / 10)
    }

    @Test fun `rent moves part of the way to its target`() {
        val next = Housing.stepRent(Money.units(10), Money.units(20), p)
        assertEquals(Money.units(10).raw + Money.units(10).raw * 150_000 / 1_000_000, next.raw)
        assertEquals(Money.units(7), Housing.stepRent(Money.ZERO, Money.units(7), p))
        assertEquals(1L, Housing.stepRent(Money(1), Money(1), p).raw)
        assertEquals(0L, Housing.stepRent(Money(10), Money.ZERO, p.copy(rentAdjustPpm = Scale.PPM)).raw)
    }

    @Test fun `repeated steps converge on the target`() {
        var r = Money.units(5)
        repeat(60) { r = Housing.stepRent(r, Money.units(9), p) }
        assertTrue(Money.units(9).raw - r.raw < 50L)
    }

    @Test fun `vacancy is the share of capacity left`() {
        assertEquals(250_000L, Housing.vacancy(1000L, 750L))
        assertEquals(0L, Housing.vacancy(1000L, 1200L))
        assertEquals(0L, Housing.vacancy(0L, 0L))
    }

    @Test fun `rent burden compares rent for a home with income`() {
        val b = Housing.burdenPpm(Money.units(10), Money.units(1000), p)
        assertEquals(10L * 35L * 1_000_000L / 1000L, b)
        assertEquals(Scale.PPM, Housing.burdenPpm(Money.units(1), Money.ZERO, p))
    }

    @Test fun `the report adds up capacity, residents and unhoused people`() {
        val ds = UrbanTestSupport.districts(floorArea = 70_000L)
        ds[0].residents = 1_000L
        ds[1].residents = 500L
        val r = Housing.report(ds, 2_000L, Money.units(90), p)
        assertEquals(ds.sumOf { Zoning.capacity(it.zone, it.floorArea, p) }, r.capacity)
        assertEquals(1_500L, r.residents)
        assertEquals(500L, r.unhoused)
        assertTrue(r.pressurePpm > 0L)
        assertTrue(r.lowIncomeBurdenPpm >= r.rentBurdenPpm)
        assertEquals(Housing.vacancy(r.capacity, r.residents), r.vacancyPpm)
    }

    @Test fun `a city without housing reports maximum pressure`() {
        val ds = UrbanTestSupport.districts(zone = ZoneUse.INDUSTRIAL)
        val r = Housing.report(ds, 100L, Money.units(90), p)
        assertEquals(0L, r.capacity)
        assertEquals(2 * Scale.PPM, r.pressurePpm)
        assertEquals(Money.ZERO, r.meanRentPerSqm)
    }
}
