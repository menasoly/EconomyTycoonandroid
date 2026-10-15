package app.many.economytycoon.visualization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AxesAndSeriesTest {
    private val eps = 1e-9

    @Test fun linearScaleMapsBothWays() {
        val s = LinearScale(0.0, 100.0, 0.0, 200.0)
        assertEquals(100.0, s.toPixel(50.0), eps)
        assertEquals(25.0, s.toValue(50.0), eps)
        val flipped = LinearScale(0.0, 10.0, 300.0, 100.0)
        assertEquals(300.0, flipped.toPixel(0.0), eps)
        assertEquals(100.0, flipped.toPixel(10.0), eps)
        assertEquals(100.0, flipped.clampedPixel(99.0), eps)
    }

    @Test fun aFlatDomainDrawsInTheMiddle() {
        val s = LinearScale(5.0, 5.0, 0.0, 100.0)
        assertEquals(50.0, s.toPixel(5.0), eps)
        assertEquals(5.0, LinearScale(5.0, 5.0, 10.0, 10.0).toValue(10.0), eps)
    }

    @Test fun niceAxesUseRoundSteps() {
        val a = NiceAxis.of(3.0, 97.0, 5)
        assertEquals(0.0, a.min, eps); assertEquals(100.0, a.max, eps); assertEquals(25.0, a.step, eps)
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), a.ticks)
        val b = NiceAxis.of(0.0, 1.0, 5)
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), b.ticks)
        val c = NiceAxis.of(1234.0, 5678.0, 5)
        assertTrue(c.min <= 1234.0 && c.max >= 5678.0)
        assertTrue(c.ticks.zipWithNext().all { (x, y) -> Math.abs((y - x) - c.step) < 1e-6 })
    }

    @Test fun flatAndEmptyDataStillGiveAnAxis() {
        val flat = NiceAxis.of(7.0, 7.0)
        assertTrue(flat.min < 7.0 && flat.max > 7.0)
        val zero = NiceAxis.of(0.0, 0.0)
        assertTrue(zero.min < 0.0 && zero.max > 0.0)
        val bad = NiceAxis.of(Double.NaN, 1.0)
        assertEquals(5, bad.ticks.size)
    }

    @Test fun includeZeroExtendsTheAxis() {
        val a = NiceAxis.of(900.0, 1000.0, 5, includeZero = true)
        assertEquals(0.0, a.min, eps)
    }

    @Test fun negativeRangesWork() {
        val a = NiceAxis.of(-80.0, -10.0, 5)
        assertTrue(a.min <= -80.0 && a.max >= -10.0)
    }

    @Test fun axisLabelsAreCompact() {
        assertEquals("0", AxisLabels.compact(0.0))
        assertEquals("950", AxisLabels.compact(950.0))
        assertEquals("1.5K", AxisLabels.compact(1500.0))
        assertEquals("-2M", AxisLabels.compact(-2_000_000.0))
        assertEquals("3.25B", AxisLabels.compact(3_250_000_000.0))
        assertEquals("0.5", AxisLabels.compact(0.5))
        assertEquals("12.5%", AxisLabels.percent(125_000.0))
    }

    @Test fun seriesReportExtentsAndChange() {
        val s = LineSeries("p", listOf(DataPoint(0.0, 10.0), DataPoint(1.0, 15.0), DataPoint(2.0, 12.0)))
        assertEquals(10.0, s.minY, eps); assertEquals(15.0, s.maxY, eps); assertEquals(2.0, s.maxX, eps)
        assertEquals(0.2, s.relativeChange()!!, eps)
        assertEquals(null, LineSeries("e", emptyList()).relativeChange())
        assertEquals(null, LineSeries("z", listOf(DataPoint(0.0, 0.0), DataPoint(1.0, 5.0))).relativeChange())
    }

    @Test fun boundsSpanAllSeries() {
        val a = LineSeries("a", listOf(DataPoint(0.0, 1.0), DataPoint(10.0, 2.0)))
        val b = LineSeries("b", listOf(DataPoint(5.0, 50.0), DataPoint(20.0, 60.0)))
        val bounds = ChartBounds.of(listOf(a, b, LineSeries("none", emptyList())))
        assertEquals(0.0, bounds.minX, eps); assertEquals(20.0, bounds.maxX, eps)
        assertTrue(bounds.axis.min <= 1.0 && bounds.axis.max >= 60.0)
        assertEquals(0.0, ChartBounds.of(emptyList()).minX, eps)
    }

    @Test fun lttbKeepsEndpointsAndTheSpike() {
        val pts = List(1000) { DataPoint(it.toDouble(), if (it == 500) 100.0 else Math.sin(it / 20.0)) }
        val small = Downsample.lttb(pts, 50)
        assertEquals(50, small.size)
        assertEquals(pts.first(), small.first()); assertEquals(pts.last(), small.last())
        assertTrue(small.any { it.y == 100.0 })
        assertEquals(small.sortedBy { it.x }, small)
    }

    @Test fun shortSeriesAreLeftAlone() {
        val pts = List(10) { DataPoint(it.toDouble(), it.toDouble()) }
        assertEquals(pts, Downsample.lttb(pts, 50))
        assertEquals(pts, Downsample.lttb(pts, 2))
        assertEquals(pts, Downsample.every(pts, 20))
    }

    @Test fun everyNthKeepsTheLastPoint() {
        val pts = List(101) { DataPoint(it.toDouble(), it.toDouble()) }
        val out = Downsample.every(pts, 10)
        assertEquals(pts.last(), out.last())
        assertTrue(out.size <= 12)
    }

    @Test fun sparklinesNormaliseAndFindTrends() {
        assertEquals(listOf(0.0, 0.5, 1.0), Sparkline.normalise(listOf(2.0, 3.0, 4.0)))
        assertEquals(listOf(0.5, 0.5), Sparkline.normalise(listOf(7.0, 7.0)))
        assertEquals(1, Sparkline.trend(listOf(1.0, 1.0, 1.0, 2.0, 2.0, 2.0)))
        assertEquals(-1, Sparkline.trend(listOf(5.0, 5.0, 4.0, 3.0, 2.0, 2.0)))
        assertEquals(0, Sparkline.trend(listOf(3.0, 3.0, 3.0, 3.0)))
        assertEquals(0, Sparkline.trend(listOf(1.0, 2.0)))
    }

    @Test fun movingAveragesSmooth() {
        assertEquals(listOf(1.0, 1.5, 2.0, 3.0), Smoothing.movingAverage(listOf(1.0, 2.0, 3.0, 4.0), 3).let { listOf(it[0], it[1], it[2], it[3]) })
        assertEquals(listOf(1.0, 2.0), Smoothing.movingAverage(listOf(1.0, 2.0), 1))
        val e = Smoothing.exponential(listOf(10.0, 20.0, 20.0, 20.0), 0.5)
        assertEquals(10.0, e[0], eps); assertEquals(15.0, e[1], eps); assertEquals(17.5, e[2], eps)
    }

    @Test fun exponentialSmoothingRejectsBadAlpha() {
        var refused = 0
        for (a in listOf(0.0, 1.5, -1.0)) try { Smoothing.exponential(listOf(1.0), a) } catch (e: IllegalArgumentException) { refused++ }
        assertEquals(3, refused)
    }
}
