package app.many.economytycoon.content.audit

import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentAuditTest {
    private val report = ContentAudit.run(AuditFixture.content)

    private fun subjects(code: String) = report.withCode(code).map { it.subject }.toSet()

    @Test fun aRecipeThatLosesMoneyIsAnError() {
        val f = report.withCode("recipe.loss").single { it.subject.endsWith("press_junk") }
        assertEquals(AuditSeverity.ERROR, f.severity)
    }

    @Test fun aRecipeThatPrintsMoneyIsWarnedAbout() {
        assertTrue(subjects("recipe.windfall").any { it.endsWith("cut_gem") })
    }

    @Test fun aFacilityThatNeverPaysBackIsFlagged() {
        assertTrue(subjects("facility.neverpays").any { it.endsWith("smelter") })
    }

    @Test fun aFacilityThatPaysBackAtOnceIsFlagged() {
        assertTrue(subjects("facility.fast").any { it.endsWith("mine") })
    }

    @Test fun goodsNothingMakesAreListed() {
        val unmade = subjects("good.unmade")
        assertTrue(unmade.any { it.endsWith("mystery") })
        assertTrue(unmade.any { it.endsWith("electricity") })
    }

    @Test fun goodsInAClosedLoopAreUnreachable() {
        val loop = subjects("good.unreachable")
        assertTrue(loop.any { it.endsWith("loop_a") })
        assertTrue(loop.any { it.endsWith("loop_b") })
    }

    @Test fun madeButUnusedGoodsAreNotes() {
        assertTrue(report.withCode("good.unused").all { it.severity == AuditSeverity.INFO })
        assertTrue(subjects("good.unused").any { it.endsWith("orphan") || it.endsWith("junk") })
    }

    @Test fun aHallWithoutRecipesIsANote() {
        assertTrue(subjects("facility.norecipe").any { it.endsWith("empty_hall") })
    }

    @Test fun looserTuningFindsFewerProblems() {
        val loose = ContentAudit.run(AuditFixture.content, AuditTuning(lossMarginPpm = -990_000L, windfallMarginPpm = 90_000_000L))
        assertTrue(loose.withCode("recipe.loss").isEmpty())
        assertTrue(loose.withCode("recipe.windfall").isEmpty())
    }

    @Test fun shippedContentHasNoHardErrorsApartFromSlowPayback() {
        val shipped = ContentAudit.run(SimTestSupport.content)
        val hard = shipped.errors.filter { it.code != "facility.neverpays" }
        assertTrue(hard.joinToString("\n"), hard.isEmpty())
    }

    @Test fun shippedPaybackProblemsDoNotGrow() {
        val shipped = ContentAudit.run(SimTestSupport.content)
        val never = shipped.withCode("facility.neverpays").size
        assertTrue("$never facility types never pay back", never <= 60)
    }
}
