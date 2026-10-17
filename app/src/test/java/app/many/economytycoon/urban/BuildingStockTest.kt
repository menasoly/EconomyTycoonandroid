package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildingStockTest {
    private fun stock(vararg bands: Long): BuildingStock {
        val s = BuildingStock()
        s.restore(LongArray(5) { bands.getOrElse(it) { 0L } }, LongArray(5) { Scale.PPM }, LongArray(5))
        return s
    }

    @Test fun `new floor area enters the youngest band`() {
        val s = BuildingStock()
        s.addNew(10_000L)
        assertEquals(10_000L, s.areaOf(0))
        assertEquals(10_000L, s.totalArea)
        assertEquals(BuildingStock.NEW_CONDITION, s.conditionOf(0))
    }

    @Test fun `mixing new area blends the condition by area`() {
        val s = stock(10_000L)
        s.addNew(10_000L, 500_000L)
        assertEquals(20_000L, s.areaOf(0))
        assertEquals(750_000L, s.conditionOf(0))
    }

    @Test fun `negative additions are refused`() {
        try { BuildingStock().addNew(-1L); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `ageing keeps the total area and moves it upward`() {
        val s = stock(120_000L)
        val before = s.totalArea
        repeat(24) { s.ageOneMonth() }
        assertEquals(before, s.totalArea)
        assertTrue(s.areaOf(0) < 120_000L)
        assertTrue(s.areaOf(1) > 0L)
    }

    @Test fun `ageing wears every band down to a floor`() {
        val s = stock(1_000L, 1_000L, 1_000L, 1_000L, 1_000L)
        repeat(1200) { s.ageOneMonth() }
        for (i in 0 until 5) assertTrue(s.conditionOf(i) >= BuildingStock.MIN_CONDITION)
        assertEquals(BuildingStock.MIN_CONDITION, s.conditionOf(4))
        assertEquals(5_000L, s.totalArea)
    }

    @Test fun `the oldest band never leaves it`() {
        val s = stock(0L, 0L, 0L, 0L, 5_000L)
        repeat(100) { s.ageOneMonth() }
        assertEquals(5_000L, s.areaOf(4))
    }

    @Test fun `fractions of a square metre carry over rather than vanish`() {
        val s = stock(100L)
        repeat(120) { s.ageOneMonth() }
        assertEquals(100L, s.totalArea)
        assertTrue("some area aged within ten years", s.areaOf(1) > 0L)
    }

    @Test fun `maintenance restores a share of lost condition`() {
        val s = stock(1_000L)
        repeat(60) { s.ageOneMonth() }
        val worn = s.conditionOf(0)
        s.maintain(500_000L)
        assertTrue(s.conditionOf(0) > worn)
        assertTrue(s.conditionOf(0) <= Scale.PPM)
        val c = s.conditionOf(0)
        s.maintain(0L)
        assertEquals(c, s.conditionOf(0))
    }

    @Test fun `full maintenance makes a band as new`() {
        val s = stock(1_000L)
        repeat(60) { s.ageOneMonth() }
        s.maintain(Scale.PPM)
        assertEquals(Scale.PPM, s.conditionOf(0))
    }

    @Test fun `demolition takes the worst condition first`() {
        val s = BuildingStock()
        s.restore(longArrayOf(1_000L, 1_000L, 1_000L, 1_000L, 1_000L), longArrayOf(900_000L, 700_000L, 300_000L, 500_000L, 800_000L), LongArray(5))
        assertEquals(1_500L, s.demolish(1_500L, false))
        assertEquals(0L, s.areaOf(2))
        assertEquals(500L, s.areaOf(3))
        assertEquals(1_000L, s.areaOf(0))
    }

    @Test fun `heritage protection spares the oldest band`() {
        val s = BuildingStock()
        s.restore(longArrayOf(100L, 0L, 0L, 0L, 5_000L), longArrayOf(900_000L, 1_000_000L, 1_000_000L, 1_000_000L, 100_000L), LongArray(5))
        assertEquals(100L, s.demolish(4_000L, true))
        assertEquals(5_000L, s.areaOf(4))
    }

    @Test fun `demolition cannot remove more than exists or a negative amount`() {
        val s = stock(300L)
        assertEquals(300L, s.demolish(10_000L, false))
        assertEquals(0L, s.demolish(-5L, false))
        assertEquals(0L, s.totalArea)
    }

    @Test fun `damage hits poor stock harder and never exceeds the area`() {
        val good = stock(10_000L)
        val poor = BuildingStock()
        poor.restore(longArrayOf(10_000L, 0, 0, 0, 0), longArrayOf(200_000L, Scale.PPM, Scale.PPM, Scale.PPM, Scale.PPM), LongArray(5))
        val a = good.damage(100_000L)
        val b = poor.damage(100_000L)
        assertEquals(100_000L * 10_000L / Scale.PPM, a)
        assertTrue(b > a)
        assertEquals(10_000L - a, good.totalArea)
        val wiped = stock(10_000L)
        assertEquals(10_000L, wiped.damage(5_000_000L))
        assertEquals(0L, wiped.totalArea)
    }

    @Test fun `empty stock counts as new and young`() {
        val s = BuildingStock()
        assertEquals(Scale.PPM, s.averageConditionPpm)
        assertEquals(0, s.averageAgeYears)
        assertEquals(0L, s.oldShare)
    }

    @Test fun `average age and old share weight by area`() {
        val s = stock(1_000L, 0L, 0L, 0L, 1_000L)
        assertEquals((BuildingStock.MID_AGE[0] + BuildingStock.MID_AGE[4]) / 2, s.averageAgeYears)
        assertEquals(500_000L, s.oldShare)
    }

    @Test fun `replacement value scales with condition`() {
        val s = stock(1_000L)
        val full = s.replacementValue(Money.units(100))
        assertEquals(Money.units(100_000), full)
        s.restore(longArrayOf(1_000L, 0, 0, 0, 0), longArrayOf(500_000L, 0, 0, 0, 0), LongArray(5))
        assertEquals(Money.units(50_000), s.replacementValue(Money.units(100)))
    }

    @Test fun `restore copies and rejects wrong sizes`() {
        val s = BuildingStock()
        val areas = longArrayOf(1, 2, 3, 4, 5)
        s.restore(areas, LongArray(5) { 7L }, LongArray(5) { 1L })
        areas[0] = 99L
        assertEquals(1L, s.areaOf(0))
        assertEquals(listOf(1L, 1L, 1L, 1L, 1L), s.carries().toList())
        try { s.restore(LongArray(4), LongArray(5), LongArray(5)); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `identical stocks hash alike and a change shows`() {
        fun h(s: BuildingStock) = CanonicalHasher().also { s.hashInto(it) }.finish()
        val a = stock(500L, 500L)
        val b = stock(500L, 500L)
        assertEquals(h(a), h(b))
        b.ageOneMonth()
        assertTrue(h(a) != h(b))
    }
}
