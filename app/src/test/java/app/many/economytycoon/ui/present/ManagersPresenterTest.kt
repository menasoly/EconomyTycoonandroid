package app.many.economytycoon.ui.present

import app.many.economytycoon.automation.ManagerKind
import app.many.economytycoon.content.TechCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagersPresenterTest {
    @Test fun startsWithEveryManagerOffAndAnEmptyLog() {
        val m = ManagersPresenter.build(PresentSupport.session())
        assertEquals(ManagerKind.values().size, m.rows.size)
        assertTrue(m.rows.none { it.on })
        assertTrue(m.log.isEmpty())
        assertEquals("0 of ${ManagerKind.values().size}", m.tiles[0].value)
    }

    @Test fun togglingFlipsOneManager() {
        val s = PresentSupport.session()
        ManagersPresenter.toggle(s, ManagerKind.SELLING)
        assertTrue(ManagersPresenter.build(s).rows.first { it.kind == ManagerKind.SELLING }.on)
        ManagersPresenter.toggle(s, ManagerKind.SELLING)
        assertTrue(ManagersPresenter.build(s).rows.none { it.on })
    }

    @Test fun allOnAndAllOff() {
        val s = PresentSupport.session()
        ManagersPresenter.setAll(s, true)
        assertTrue(ManagersPresenter.build(s).rows.all { it.on })
        ManagersPresenter.setAll(s, false)
        assertTrue(ManagersPresenter.build(s).rows.none { it.on })
    }

    @Test fun reserveAcceptsWholeNumbersOnly() {
        val s = PresentSupport.session()
        assertNull(ManagersPresenter.setReserve(s, " 12000 "))
        assertEquals("12000", ManagersPresenter.build(s).reserveText)
        assertTrue(ManagersPresenter.setReserve(s, "abc") != null)
        assertTrue(ManagersPresenter.setReserve(s, "-5") != null)
        assertEquals("12000", ManagersPresenter.build(s).reserveText)
    }

    @Test fun limitsAreValidated() {
        val s = PresentSupport.session()
        assertNull(ManagersPresenter.setRepairPercent(s, 90))
        assertEquals(90, ManagersPresenter.build(s).repairPercent)
        assertTrue(ManagersPresenter.setRepairPercent(s, 5) != null)
        assertNull(ManagersPresenter.setWasteUnits(s, 50))
        assertTrue(ManagersPresenter.setWasteUnits(s, 0) != null)
        assertNull(ManagersPresenter.setMarginPercent(s, -10))
        assertEquals(-10, ManagersPresenter.build(s).marginPercent)
        assertTrue(ManagersPresenter.setMarginPercent(s, 500) != null)
    }

    @Test fun researchFocusCanBeSetAndCleared() {
        val s = PresentSupport.session()
        val c = TechCategory.values().first()
        ManagersPresenter.setFocus(s, c)
        assertEquals(c, ManagersPresenter.build(s).focus)
        ManagersPresenter.setFocus(s, null)
        assertNull(ManagersPresenter.build(s).focus)
    }

    @Test fun cadenceReadsInWords() {
        assertEquals("Every 7 days", ManagersPresenter.cadence(ManagerKind.MAINTENANCE))
    }
}
