package app.many.economytycoon.visualization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorsAndGeometryTest {
    private val eps = 1e-6

    @Test fun argbPacksAndUnpacks() {
        val c = Argb.of(10, 20, 30, 40)
        assertEquals(10, Argb.red(c)); assertEquals(20, Argb.green(c)); assertEquals(30, Argb.blue(c)); assertEquals(40, Argb.alpha(c))
        assertEquals(0x80, Argb.alpha(Argb.withAlpha(c, 0x80)))
        assertEquals(10, Argb.red(Argb.withAlpha(c, 0x80)))
        assertEquals("#FF0072B2", Argb.hex(Palette.categorical[0]))
    }

    @Test fun lerpBlendsAndClamps() {
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
        assertEquals(0xFF808080.toInt(), Argb.lerp(black, white, 0.5))
        assertEquals(black, Argb.lerp(black, white, -3.0))
        assertEquals(white, Argb.lerp(black, white, 9.0))
    }

    @Test fun contrastPicksReadableText() {
        assertEquals(0xFF000000.toInt(), Argb.onColor(0xFFFFFF00.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), Argb.onColor(0xFF001F3F.toInt()))
        assertEquals(21.0, Argb.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, Argb.contrast(0xFF123456.toInt(), 0xFF123456.toInt()), eps)
    }

    @Test fun paletteWrapsAndStaysDistinct() {
        assertEquals(Palette.slot(0), Palette.slot(Palette.categorical.size))
        assertEquals(Palette.slot(7), Palette.slot(-1))
        assertEquals(Palette.categorical.size, Palette.categorical.toSet().size)
    }

    @Test fun rampsHitTheirEndsAndMiddle() {
        val r = ColorRamp(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        assertEquals(0xFF000000.toInt(), r.at(0.0)); assertEquals(0xFFFFFFFF.toInt(), r.at(1.0)); assertEquals(0xFF808080.toInt(), r.at(0.5))
        assertEquals(r.at(0.0), r.at(-4.0))
        assertEquals(Palette.loss, divergingColor(-5.0, 5.0)); assertEquals(Palette.gain, divergingColor(9.0, 5.0))
        assertEquals(ColorRamp.diverging.at(0.5), divergingColor(3.0, 0.0))
    }

    @Test fun aRampNeedsTwoColours() {
        var refused = false
        try { ColorRamp(listOf(1)) } catch (e: IllegalArgumentException) { refused = true }
        assertTrue(refused)
    }

    @Test fun pieSlicesFollowTheValues() {
        val s = PieLayout.slices(listOf("a" to 50.0, "b" to 30.0, "c" to 20.0))
        assertEquals(listOf("a", "b", "c"), s.map { it.label })
        assertEquals(180.0, s[0].sweepDegrees, eps)
        assertEquals(180.0, s[1].startDegrees, eps)
        assertEquals(360.0, s.sumOf { it.sweepDegrees }, eps)
        assertEquals(1.0, s.sumOf { it.share }, eps)
    }

    @Test fun smallSlicesMergeIntoOther() {
        val s = PieLayout.slices(listOf("big" to 90.0, "x" to 1.0, "y" to 1.0, "z" to 1.0))
        assertEquals(listOf("big", "Other"), s.map { it.label })
        assertEquals(3.0, s[1].value, eps)
        val single = PieLayout.slices(listOf("big" to 98.0, "tiny" to 1.0))
        assertEquals(listOf("big", "tiny"), single.map { it.label })
    }

    @Test fun nothingPositiveGivesNoSlices() {
        assertTrue(PieLayout.slices(emptyList()).isEmpty())
        assertTrue(PieLayout.slices(listOf("a" to 0.0, "b" to -3.0)).isEmpty())
    }

    @Test fun pieHitTestingUsesAngleAndRing() {
        val s = PieLayout.slices(listOf("right" to 50.0, "left" to 50.0))
        assertEquals("right", PieLayout.hit(s, 10.0, 0.0, 5.0, 20.0)!!.label)
        assertEquals("left", PieLayout.hit(s, -10.0, 0.0, 5.0, 20.0)!!.label)
        assertNull(PieLayout.hit(s, 1.0, 0.0, 5.0, 20.0))
        assertNull(PieLayout.hit(s, 30.0, 0.0, 5.0, 20.0))
    }

    @Test fun treemapAreasFollowValuesAndFillTheBounds() {
        val bounds = Rect(0.0, 0.0, 200.0, 100.0)
        val cells = Treemap.layout(listOf("a" to 6.0, "b" to 3.0, "c" to 2.0, "d" to 1.0), bounds)
        assertEquals(4, cells.size)
        assertEquals(bounds.area, cells.sumOf { it.rect.area }, 1e-6)
        for (c in cells) assertEquals(bounds.area * c.value / 12.0, c.rect.area, 1e-6)
        for (c in cells) assertTrue(c.rect.x >= -1e-9 && c.rect.right <= bounds.right + 1e-9 && c.rect.y >= -1e-9 && c.rect.bottom <= bounds.bottom + 1e-9)
    }

    @Test fun treemapCellsDoNotOverlap() {
        val cells = Treemap.layout(List(12) { "i$it" to (it + 1).toDouble() }, Rect(0.0, 0.0, 300.0, 180.0))
        for (i in cells.indices) for (j in i + 1 until cells.size) {
            val a = cells[i].rect; val b = cells[j].rect
            val w = minOf(a.right, b.right) - maxOf(a.x, b.x); val h = minOf(a.bottom, b.bottom) - maxOf(a.y, b.y)
            assertTrue("overlap ${cells[i].label} ${cells[j].label}", w <= 1e-6 || h <= 1e-6)
        }
    }

    @Test fun emptyTreemapsAreEmpty() {
        assertTrue(Treemap.layout(emptyList(), Rect(0.0, 0.0, 10.0, 10.0)).isEmpty())
        assertTrue(Treemap.layout(listOf("a" to 1.0), Rect(0.0, 0.0, 0.0, 10.0)).isEmpty())
    }

    @Test fun viewportStartsCentredAndFitted() {
        val v = MapViewport.fitting(1000.0, 400.0, 300.0)
        assertEquals(0.3, v.scale, eps)
        val c = v.toPixel(500.0, 500.0)
        assertEquals(200.0, c.x, eps); assertEquals(150.0, c.y, eps)
    }

    @Test fun pixelAndWorldRoundTrip() {
        val v = MapViewport.fitting(1000.0, 500.0, 500.0).zoomAt(100.0, 100.0, 3.0).pan(20.0, -15.0)
        val w = v.toWorld(123.0, 321.0)
        val p = v.toPixel(w.x, w.y)
        assertEquals(123.0, p.x, 1e-6); assertEquals(321.0, p.y, 1e-6)
    }

    @Test fun zoomingKeepsTheFocalPointStill() {
        val v = MapViewport.fitting(1000.0, 500.0, 500.0)
        val before = v.toWorld(300.0, 200.0)
        val after = v.zoomAt(300.0, 200.0, 4.0)
        val moved = after.toPixel(before.x, before.y)
        assertEquals(300.0, moved.x, 1e-6); assertEquals(200.0, moved.y, 1e-6)
        assertEquals(4.0, after.zoom, eps)
    }

    @Test fun zoomIsLimited() {
        val v = MapViewport.fitting(1000.0, 500.0, 500.0)
        assertEquals(MapViewport.MAX_ZOOM, v.zoomAt(0.0, 0.0, 1000.0).zoom, eps)
        assertEquals(MapViewport.MIN_ZOOM, v.zoomAt(0.0, 0.0, 0.0001).zoom, eps)
    }

    @Test fun panningCannotLoseTheMap() {
        val v = MapViewport.fitting(1000.0, 500.0, 500.0).zoomAt(250.0, 250.0, 4.0).pan(1e9, 1e9)
        assertTrue(v.centerX in 0.0..1000.0 && v.centerY in 0.0..1000.0)
        assertTrue(v.resized(10.0, 10.0).centerX in 0.0..1000.0)
    }

    @Test fun visibilityFollowsTheViewport() {
        val v = MapViewport.fitting(1000.0, 500.0, 500.0).zoomAt(0.0, 0.0, 6.0).centeredOn(100.0, 100.0)
        assertTrue(v.isVisible(100.0, 100.0))
        assertTrue(!v.isVisible(900.0, 900.0))
        assertTrue(v.isVisible(900.0, 900.0, margin = 1e9))
    }

    @Test fun hitTestingPicksTheClosestWithinTolerance() {
        val v = MapViewport.fitting(100.0, 100.0, 100.0)
        val markers = listOf("a" to (10.0 to 10.0), "b" to (12.0 to 10.0), "c" to (80.0 to 80.0))
        assertEquals("b", HitTest.nearest(v, markers, { it.second }, 12.0, 10.0, 8.0)!!.first)
        assertNull(HitTest.nearest(v, markers, { it.second }, 50.0, 50.0, 8.0))
        assertNotNull(HitTest.nearest(v, markers, { it.second }, 79.0, 81.0, 8.0))
    }

    @Test fun gaugeAnglesCoverTheArc() {
        assertEquals(0.0, Gauge.sweepFor(-1.0), eps)
        assertEquals(270.0, Gauge.sweepFor(5.0), eps)
        assertEquals(135.0, Gauge.sweepFor(0.5), eps)
        assertEquals(0.5, Gauge.fractionOf(5.0, 0.0, 10.0), eps)
        assertEquals(0.0, Gauge.fractionOf(5.0, 3.0, 3.0), eps)
        assertEquals(Math.toRadians(135.0), Gauge.needleRadians(0.0), eps)
    }
}
