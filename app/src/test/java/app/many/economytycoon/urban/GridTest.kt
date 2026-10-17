package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GridTest {
    @Test fun `a new grid is filled and indexed row by row`() {
        val g = Grid(4, 3, 7L)
        assertEquals(12, g.size)
        assertEquals(7L, g[3, 2])
        g[1, 2] = 9L
        assertEquals(9L, g.at(2 * 4 + 1))
        assertEquals(Cell(1, 2), g.cellAt(9))
    }

    @Test fun `out of range access is rejected`() {
        val g = Grid(3, 3)
        try { g[3, 0]; fail("expected a failure") } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("outside")) }
        assertEquals(-5L, g.getOrDefault(-1, 0, -5L))
        assertFalse(g.inBounds(0, 3))
    }

    @Test fun `empty grids and mismatched data are refused`() {
        try { Grid(0, 3); fail() } catch (e: IllegalArgumentException) { }
        try { Grid(2, 2, longArrayOf(1, 2, 3)); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `sum min max and mean`() {
        val g = Grid(2, 2, longArrayOf(1, 2, 3, 10))
        assertEquals(16L, g.sum())
        assertEquals(1L, g.min())
        assertEquals(10L, g.max())
        assertEquals(4L, g.mean())
        assertEquals(-1L, Grid(2, 1, longArrayOf(-1, -1)).mean())
        assertEquals(-2L, Grid(2, 1, longArrayOf(-1, -2)).mean())
    }

    @Test fun `copy is independent`() {
        val a = Grid(2, 2, 1L)
        val b = a.copy()
        b[0, 0] = 50L
        assertEquals(1L, a[0, 0])
        assertEquals(a, Grid(2, 2, 1L))
        assertTrue(a != b)
    }

    @Test fun `combine applies the operation cell by cell and checks sizes`() {
        val a = Grid(2, 2, longArrayOf(1, 2, 3, 4))
        val b = Grid(2, 2, longArrayOf(10, 20, 30, 40))
        assertEquals(longArrayOf(11, 22, 33, 44).toList(), a.combine(b) { x, y -> x + y }.toLongArray().toList())
        try { a.combine(Grid(3, 3)) { x, _ -> x }; fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `normalised grids span zero to the top`() {
        val g = Grid(3, 1, longArrayOf(5, 10, 15)).normalized(1000L)
        assertEquals(listOf(0L, 500L, 1000L), g.toLongArray().toList())
        assertEquals(listOf(0L, 0L), Grid(2, 1, 8L).normalized().toLongArray().toList())
    }

    @Test fun `coerce clamps values`() {
        assertEquals(listOf(0L, 5L, 10L), Grid(3, 1, longArrayOf(-4, 5, 99)).coerceIn(0L, 10L).toLongArray().toList())
    }

    @Test fun `spread of a single source follows the kernel by distance`() {
        val g = Grid(5, 5)
        g[2, 2] = 1000L
        val k = Grid.decayKernel(500_000L, 2)
        assertEquals(listOf(1_000_000L, 500_000L, 250_000L), k.toList())
        val s = g.spread(k)
        assertEquals(1000L, s[2, 2])
        assertEquals(500L, s[3, 2])
        assertEquals(500L, s[2, 1])
        assertEquals(250L, s[3, 3])
        assertEquals(250L, s[0, 2])
        assertEquals(0L, s[0, 0])
    }

    @Test fun `spread conserves mass away from the edges when the kernel sums to the divisor`() {
        val g = Grid(9, 9)
        g[4, 4] = 1_000_000L
        val s = g.spread(longArrayOf(500_000L, 125_000L, 0L))
        assertEquals(500_000L + 4 * 125_000L, s.sum() - 0L)
    }

    @Test fun `spread rejects empty kernels and zero divisors`() {
        try { Grid(2, 2).spread(LongArray(0)); fail() } catch (e: IllegalArgumentException) { }
        try { Grid(2, 2).spread(longArrayOf(1), 0L); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `smoothing averages with the neighbours that exist`() {
        val g = Grid(3, 1, longArrayOf(0, 90, 0)).smoothed()
        assertEquals(listOf(45L, 30L, 45L), g.toLongArray().toList())
    }

    @Test fun `sumRect covers half open rectangles`() {
        val g = Grid(4, 4, 1L)
        assertEquals(4L, g.sumRect(0, 0, 2, 2))
        assertEquals(0L, g.sumRect(2, 2, 2, 4))
        assertEquals(16L, g.sumRect(0, 0, 4, 4))
    }

    @Test fun `cells order by row then column and measure manhattan distance`() {
        assertTrue(Cell(5, 0) < Cell(0, 1))
        assertEquals(7, Cell(1, 1).manhattan(Cell(4, 5)))
        assertEquals(listOf(Cell(0, 0), Cell(3, 0), Cell(1, 1)), listOf(Cell(1, 1), Cell(3, 0), Cell(0, 0)).sorted())
    }

    @Test fun `equal grids hash the same and different grids differ`() {
        fun h(g: Grid) = CanonicalHasher().also { g.hashInto(it) }.finish()
        val a = Grid(3, 3, 4L)
        assertEquals(h(a), h(a.copy()))
        val b = a.copy(); b[1, 1] = 5L
        assertTrue(h(a) != h(b))
        assertTrue(h(Grid(3, 2, 4L)) != h(Grid(2, 3, 4L)))
    }

    @Test fun `decay kernel of zero reach is a single point`() {
        assertEquals(listOf(1_000_000L), Grid.decayKernel(900_000L, 0).toList())
        try { Grid.decayKernel(1L, -1); fail() } catch (e: IllegalArgumentException) { }
    }
}
