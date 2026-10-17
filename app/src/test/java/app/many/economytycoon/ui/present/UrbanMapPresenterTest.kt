package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Tick
import app.many.economytycoon.urban.OverlayKind
import app.many.economytycoon.urban.UrbanAnalysis
import app.many.economytycoon.urban.UrbanOverlay
import app.many.economytycoon.urban.UrbanOverlays
import app.many.economytycoon.urban.UrbanTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanMapPresenterTest {
    private val T = UrbanTestSupport

    private fun overlay(kind: OverlayKind): UrbanOverlay {
        val svc = T.withCity()
        T.run(svc, 0, 60)
        val plan = T.plan(svc)
        return UrbanOverlays.build(plan, UrbanAnalysis.analyse(plan, T.params), kind, T.params, Tick(60))
    }

    @Test fun `choices mark the selected overlay`() {
        val c = UrbanMapPresenter.choices(OverlayKind.RENT)
        assertEquals(OverlayKind.values().size, c.size)
        assertEquals(listOf(OverlayKind.RENT), c.filter { it.selected }.map { it.kind })
        assertTrue(c.all { it.label.isNotBlank() })
    }

    @Test fun `stepping through overlays wraps around`() {
        val all = OverlayKind.values()
        assertEquals(all[1], UrbanMapPresenter.nextKind(all[0], true))
        assertEquals(all[0], UrbanMapPresenter.nextKind(all.last(), true))
        assertEquals(all.last(), UrbanMapPresenter.nextKind(all[0], false))
    }

    @Test fun `the model has a colour for every cell and a title`() {
        val m = UrbanMapPresenter.build(overlay(OverlayKind.LAND_VALUE), "Testville")
        assertEquals("Testville: Land value", m.title)
        assertEquals(12, m.size)
        assertEquals(144, m.colors.size)
        assertEquals(5, m.legend.size)
        assertTrue(m.summary.startsWith("Ranges from"))
    }

    @Test fun `zoning maps say each colour is a zone`() {
        val m = UrbanMapPresenter.build(overlay(OverlayKind.ZONING), "Testville")
        assertEquals("Each colour is a zone.", m.summary)
        assertEquals(6, m.legend.size)
    }

    @Test fun `line and marker summaries describe the network`() {
        val m = UrbanMapPresenter.build(overlay(OverlayKind.CONGESTION), "Testville")
        assertTrue(m.lineSummary.any { it.label == "Bus" })
        assertTrue(m.lineSummary.any { it.label.contains("road") || it.label == "Arterial" || it.label == "Local road" })
        assertTrue(m.markers.all { it.label.isNotBlank() })
    }

    @Test fun `readouts name the district and the value`() {
        val o = overlay(OverlayKind.RENT)
        val r = UrbanMapPresenter.readout(o, 5, 5)!!
        assertEquals(o.districtNames[4], r.district)
        assertEquals("Rent", r.title)
        assertTrue(r.value.contains("per sqm"))
        assertEquals(o.heat.legendIndexAt(5, 5), r.legendIndex)
    }

    @Test fun `zoning readouts name the zone`() {
        val o = overlay(OverlayKind.ZONING)
        val r = UrbanMapPresenter.readout(o, 0, 0)!!
        assertTrue(r.value in app.many.economytycoon.urban.ZoneUse.values().map { it.label })
    }

    @Test fun `readouts outside the map are empty`() {
        val o = overlay(OverlayKind.RENT)
        assertNull(UrbanMapPresenter.readout(o, -1, 0))
        assertNull(UrbanMapPresenter.readout(o, 12, 3))
        assertNull(UrbanMapPresenter.readout(o, 3, 99))
    }

    @Test fun `every overlay can be presented`() {
        for (k in OverlayKind.values()) {
            val m = UrbanMapPresenter.build(overlay(k), "City")
            assertEquals(144, m.colors.size)
            assertTrue(m.legend.isNotEmpty())
        }
    }
}
