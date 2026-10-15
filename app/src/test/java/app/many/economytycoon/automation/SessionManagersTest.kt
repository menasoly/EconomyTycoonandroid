package app.many.economytycoon.automation

import app.many.economytycoon.ui.present.PresentSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagersTest {
    @Test fun managersStartOff() {
        val s = PresentSupport.session("Managed Works", 3L)
        assertFalse(s.managers.anyOn)
        s.step()
        assertTrue(s.managerLog().isEmpty())
    }

    @Test fun applyingSettingsNotifiesTheSink() {
        val s = PresentSupport.session("Managed Works", 3L)
        var saved = 0
        s.managerSink = { saved++ }
        s.applyManagers(ManagerSettings().with(ManagerKind.SELLING, true))
        assertEquals(1, saved)
        assertTrue(s.managers.isOn(ManagerKind.SELLING))
    }

    @Test fun restoringDoesNotCallTheSink() {
        val s = PresentSupport.session("Managed Works", 3L)
        var saved = 0
        s.managerSink = { saved++ }
        s.restoreManagers(ManagerSettings().with(ManagerKind.WASTE, true))
        assertEquals(0, saved)
        assertTrue(s.managers.isOn(ManagerKind.WASTE))
    }

    @Test fun enabledManagersActAsDaysPass() {
        val s = PresentSupport.session("Managed Works", 3L)
        s.applyManagers(ManagerSettings(enabled = ManagerKind.values().toSet(), reserve = app.many.economytycoon.core.Money.ZERO))
        repeat(40) { s.step() }
        val log = s.managerLog()
        assertTrue(log.isEmpty() || log.zipWithNext().all { (a, b) -> a.day >= b.day })
    }

    @Test fun theLogIsBounded() {
        val s = PresentSupport.session("Managed Works", 3L)
        s.applyManagers(ManagerSettings(enabled = ManagerKind.values().toSet(), reserve = app.many.economytycoon.core.Money.ZERO))
        repeat(60) { s.step() }
        assertTrue(s.managerLog().size <= 100)
    }
}
