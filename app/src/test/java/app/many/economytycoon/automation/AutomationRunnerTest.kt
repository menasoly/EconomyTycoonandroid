package app.many.economytycoon.automation

import app.many.economytycoon.core.CompanyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRunnerTest {
    @Test fun nothingRunsWhenAllManagersAreOff() {
        val r = AutomationRig(); r.farm().conditionPpm = 100_000L
        assertTrue(AutomationRunner(r.e).plan(r.company, ManagerSettings(), 1).isEmpty())
    }

    @Test fun unknownCompaniesGetNoPlans() {
        val r = AutomationRig()
        assertTrue(AutomationRunner(r.e).plan(CompanyId(9999), r.on(ManagerKind.MAINTENANCE), 1).isEmpty())
    }

    @Test fun aManagerRunsOnItsCadence() {
        val r = AutomationRig(); r.farm().conditionPpm = 300_000L
        val runner = AutomationRunner(r.e)
        val s = r.on(ManagerKind.MAINTENANCE)
        assertEquals(1, runner.plan(r.company, s, 10).size)
        assertTrue(runner.plan(r.company, s, 11).isEmpty())
        assertFalse(runner.due(ManagerKind.MAINTENANCE, r.company, 10L + ManagerKind.MAINTENANCE.everyDays - 1))
        assertTrue(runner.due(ManagerKind.MAINTENANCE, r.company, 10L + ManagerKind.MAINTENANCE.everyDays))
    }

    @Test fun forgettingMakesEveryManagerDueAgain() {
        val r = AutomationRig(); r.farm().conditionPpm = 300_000L
        val runner = AutomationRunner(r.e); val s = r.on(ManagerKind.MAINTENANCE)
        runner.plan(r.company, s, 1)
        runner.forget(r.company)
        assertTrue(runner.due(ManagerKind.MAINTENANCE, r.company, 1))
    }

    @Test fun logKeepsNewestFirstAndIsBounded() {
        val r = AutomationRig()
        val runner = AutomationRunner(r.e, logSize = 3)
        for (i in 1..5) runner.record(i.toLong(), Planned(ManagerKind.SELLING, app.many.economytycoon.simulation.Command.Rename(r.company, "x"), "n$i"))
        val log = runner.recent()
        assertEquals(listOf("n5", "n4", "n3"), log.map { it.note })
        assertEquals(5L, log.first().day)
    }

    @Test fun runningThePlanOverDaysKeepsACompanyTidy() {
        val r = AutomationRig(); val f = r.farm()
        f.conditionPpm = 350_000L
        val runner = AutomationRunner(r.e); val s = r.on(ManagerKind.MAINTENANCE)
        for (day in 1L..20L) {
            r.run(runner.plan(r.company, s, day))
            r.sim.advance(1)
        }
        assertTrue(f.conditionPpm > 350_000L)
    }
}
