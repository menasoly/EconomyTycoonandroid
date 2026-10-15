package app.many.economytycoon.content.audit

import org.junit.Assert.assertTrue
import org.junit.Test

class AuditFormatTest {
    private val report = ContentAudit.run(AuditFixture.content)

    @Test fun summaryCountsEachKindOfFinding() {
        val s = AuditFormat.summary(report)
        assertTrue(s, s.contains("${report.errors.size} errors"))
        assertTrue(s, s.contains("${report.warnings.size} warnings"))
    }

    @Test fun recipeTableListsWorstFirst() {
        val t = AuditFormat.recipeTable(report).lines()
        val junk = t.indexOfFirst { it.contains("press_junk") }
        val gem = t.indexOfFirst { it.contains("cut_gem") }
        assertTrue(junk in 1 until gem)
    }

    @Test fun paybackTableShowsNeverForUnprofitableFacilities() {
        val line = AuditFormat.paybackTable(report).lines().first { it.contains("smelter") }
        assertTrue(line, line.trim().endsWith("never"))
    }

    @Test fun findingsAreOnePerLine() {
        assertTrue(AuditFormat.findings(report).lines().size == report.findings.size)
    }
}
