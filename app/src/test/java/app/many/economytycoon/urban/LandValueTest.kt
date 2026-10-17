package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandValueTest {
    private val p = UrbanParams()
    private val w = LandValueWeights()
    private val g = UrbanTestSupport.geometry()

    private fun surface(districts: List<District> = UrbanTestSupport.districts(), accessibility: Grid = Grid(12, 12, 500L), price: Long = Scale.PPM): LandValueSurface {
        val s = Externalities.compute(g, districts)
        return LandValueModel.compute(g, districts, s, accessibility, price, p)
    }

    @Test fun `neutral factors give the base multiplier plus centrality`() {
        assertEquals(Scale.PPM, LandValueModel.multiplierPpm(w, 500, 500, 0, 0, 0, 0, 0))
        assertTrue(LandValueModel.multiplierPpm(w, 500, 500, 0, 0, 0, 0, 1000) > Scale.PPM)
    }

    @Test fun `each factor moves the multiplier the right way`() {
        val base = LandValueModel.multiplierPpm(w, 500, 500, 100, 100, 100, 0, 500)
        assertTrue(LandValueModel.multiplierPpm(w, 800, 500, 100, 100, 100, 0, 500) > base)
        assertTrue(LandValueModel.multiplierPpm(w, 500, 800, 100, 100, 100, 0, 500) > base)
        assertTrue(LandValueModel.multiplierPpm(w, 500, 500, 400, 100, 100, 0, 500) < base)
        assertTrue(LandValueModel.multiplierPpm(w, 500, 500, 100, 400, 100, 0, 500) < base)
        assertTrue(LandValueModel.multiplierPpm(w, 500, 500, 100, 100, 400, 0, 500) < base)
        assertTrue(LandValueModel.multiplierPpm(w, 500, 500, 100, 100, 100, 300, 500) > base)
    }

    @Test fun `the multiplier is clamped on both sides`() {
        assertEquals(w.minMultiplierPpm, LandValueModel.multiplierPpm(w, 0, 0, 1000, 1000, 1000, -500, 0))
        val tight = w.copy(maxMultiplierPpm = 1_200_000L)
        assertEquals(1_200_000L, LandValueModel.multiplierPpm(tight, 1000, 1000, 0, 0, 0, 500, 1000))
    }

    @Test fun `buildable floor area raises the value of land`() {
        assertEquals(600_000L, LandValueModel.farFactorPpm(0))
        assertEquals(1_000_000L, LandValueModel.farFactorPpm(2000))
        assertEquals(3_000_000L, LandValueModel.farFactorPpm(50_000))
        assertTrue(LandValueModel.farFactorPpm(5000) > LandValueModel.farFactorPpm(2400))
    }

    @Test fun `richer cities are dearer but only half as much`() {
        assertEquals(Scale.PPM, LandValueModel.priceLevelPpm(p.referenceWage, p))
        assertEquals(1_500_000L, LandValueModel.priceLevelPpm(Money(p.referenceWage.raw * 2), p))
        assertEquals(650_000L, LandValueModel.priceLevelPpm(Money(p.referenceWage.raw * 3 / 10), p))
        assertEquals(Scale.PPM, LandValueModel.priceLevelPpm(Money(500), p.copy(referenceWage = Money.ZERO)))
    }

    @Test fun `commercial land is worth more than industrial land of the same position`() {
        val s = surface(UrbanTestSupport.districts(zones = mapOf(1 to ZoneUse.COMMERCIAL, 7 to ZoneUse.INDUSTRIAL)))
        assertTrue(s.districtAverage(g, 1) > s.districtAverage(g, 7))
    }

    @Test fun `the centre costs more than the edge`() {
        val s = surface()
        assertTrue(s.districtAverage(g, 4) > s.districtAverage(g, 0))
        assertTrue(s.spreadPpm() > 0L)
    }

    @Test fun `a factory next to homes cuts their value`() {
        val calm = surface()
        val dirty = surface(UrbanTestSupport.districts(zones = mapOf(5 to ZoneUse.INDUSTRIAL)))
        assertTrue(dirty.districtAverage(g, 4) < calm.districtAverage(g, 4))
    }

    @Test fun `transit access raises the value of cells near stations`() {
        val districts = UrbanTestSupport.districts()
        val none = LandValueModel.accessibilityGrid(g, districts, emptyList())
        val near = LandValueModel.accessibilityGrid(g, districts, listOf(Cell(5, 5)))
        assertEquals(none[0, 0], near[0, 0])
        assertTrue(near[5, 5] > none[5, 5])
        assertTrue(near[5, 5] >= near[6, 5] && near[6, 5] >= near[7, 5])
        assertTrue(near.max() <= 1000L)
    }

    @Test fun `price level scales every value`() {
        val one = surface(price = Scale.PPM)
        val two = surface(price = 2 * Scale.PPM)
        for (c in listOf(Cell(0, 0), Cell(6, 6), Cell(11, 11))) assertTrue(two.valueAt(c).raw in (2 * one.valueAt(c).raw - 2)..(2 * one.valueAt(c).raw + 2))
    }

    @Test fun `the explanation matches the value and names the zone`() {
        val s = surface(UrbanTestSupport.districts(zones = mapOf(4 to ZoneUse.MIXED)))
        val e = s.explain(Cell(5, 5))
        assertEquals(ZoneUse.MIXED, e.zone)
        assertEquals(s.valueAt(Cell(5, 5)), e.valuePerSqm)
        assertTrue(e.multiplierPpm in w.minMultiplierPpm..w.maxMultiplierPpm)
    }

    @Test fun `ranking lists the dearest cell first and breaks ties by position`() {
        val s = surface()
        val ranked = s.ranked()
        assertEquals(144, ranked.size)
        assertTrue(s.valueAt(ranked.first()) >= s.valueAt(ranked.last()))
        for (i in 1 until ranked.size) {
            val a = s.valueAt(ranked[i - 1]); val b = s.valueAt(ranked[i])
            assertTrue(a > b || (a == b && ranked[i - 1] < ranked[i]))
        }
    }

    @Test fun `a district average is the mean of its cells`() {
        val s = surface()
        val cells = g.cellsOf(2)
        assertEquals(cells.sumOf { s.valueAt(it).raw } / cells.size, s.districtAverage(g, 2).raw)
    }
}
