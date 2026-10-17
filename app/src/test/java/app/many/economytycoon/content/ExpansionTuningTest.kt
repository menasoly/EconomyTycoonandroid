package app.many.economytycoon.content

import app.many.economytycoon.content.audit.AuditSeverity
import app.many.economytycoon.content.audit.AuditTuning
import app.many.economytycoon.content.audit.ContentAudit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The expansion has to pass the audit with thresholds much tighter than the defaults. */
class ExpansionTuningTest {
    private val tight = AuditTuning(lossMarginPpm = 0L, windfallMarginPpm = 600_000L, slowPaybackDays = 1_500L, fastPaybackDays = 300L, chainLossPpm = 1_000_000L, chainWindfallPpm = 3_500_000L)
    private val report by lazy { ContentAudit.run(ExpansionFixture.repo, tight) }

    private fun mine(subject: String): Boolean {
        val own = ExpansionFixture.allExpansionGoods + ExpansionFixture.allExpansionRecipes + ExpansionFixture.allExpansionFacilities
        return subject in own || ExpansionFixture.allExpansionRecipes.any { subject.endsWith(it) }
    }

    @Test fun noExpansionRecipeLosesMoney() {
        assertEquals(emptyList<String>(), report.withCode("recipe.loss").filter { mine(it.subject) }.map { it.subject })
    }

    @Test fun noExpansionRecipeIsAWindfall() {
        assertEquals(emptyList<String>(), report.withCode("recipe.windfall").filter { mine(it.subject) }.map { it.subject })
    }

    @Test fun noExpansionFacilityIsSlowOrTooEasy() {
        assertEquals(emptyList<String>(), report.withCode("facility.slow").filter { mine(it.subject) }.map { it.subject })
        assertEquals(emptyList<String>(), report.withCode("facility.fast").filter { mine(it.subject) }.map { it.subject })
    }

    @Test fun noExpansionFacilityNeverPaysBack() {
        assertEquals(emptyList<String>(), report.withCode("facility.neverpays").filter { mine(it.subject) }.map { it.subject })
    }

    @Test fun noExpansionGoodIsPricedBelowItsChainCost() {
        assertEquals(emptyList<String>(), report.withCode("chain.loss").filter { mine(it.subject) }.map { it.subject })
    }

    @Test fun noExpansionGoodIsAMoneyMachine() {
        assertEquals(emptyList<String>(), report.withCode("chain.windfall").filter { mine(it.subject) }.map { it.subject })
    }

    @Test fun noErrorsAtAllInTheExpansion() {
        assertTrue(report.errors.none { mine(it.subject) })
    }

    @Test fun noWarningsAtAllInTheExpansion() {
        assertTrue(report.bySeverity(AuditSeverity.WARN).none { mine(it.subject) })
    }

    @Test fun theDefaultAuditReportsNothingAboutTheExpansion() {
        val newOnes = ExpansionFixture.audit.findings.filter { f -> mine(f.subject) }
        assertTrue(newOnes.isEmpty())
    }

    @Test fun everyExpansionRecipeAppearsInTheAuditEconomics() {
        val audited = ExpansionFixture.audit.recipes.map { it.recipe.id.key }.toSet()
        assertTrue(audited.containsAll(ExpansionFixture.allExpansionRecipes))
    }

    @Test fun everyExpansionFacilityAppearsInTheAuditPaybacks() {
        val audited = ExpansionFixture.audit.paybacks.map { it.facility.id.key }.toSet()
        assertTrue(audited.containsAll(ExpansionFixture.allExpansionFacilities))
    }
}
