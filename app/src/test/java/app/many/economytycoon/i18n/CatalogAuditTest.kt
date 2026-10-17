package app.many.economytycoon.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAuditTest {
    @Test fun shippedCatalogsAreClean() {
        val issues = CatalogAudit.all()
        assertTrue(issues.joinToString("\n") { "${it.locale} ${it.key} ${it.kind} ${it.detail}" }, issues.isEmpty())
    }

    @Test fun everyLanguageIsFullyCovered() {
        for (l in GameLocale.values()) assertEquals(1_000_000L, CatalogAudit.coveragePpm(l))
    }

    @Test fun englishIsNeverUntranslated() = assertTrue(CatalogAudit.untranslated(GameLocale.EN).isEmpty())

    @Test fun translationsDifferFromEnglishForLongText() {
        for (l in GameLocale.values().filter { it != GameLocale.EN }) {
            val same = CatalogAudit.untranslated(l)
            assertTrue("$l: $same", same.isEmpty())
        }
    }

    @Test fun missingKeysAreFound() {
        val base = mapOf("a" to "A", "b" to "B")
        val issues = CatalogAudit.check(GameLocale.FR, mapOf("a" to "Un"), base)
        assertEquals(listOf(CatalogIssue.Kind.MISSING), issues.map { it.kind })
        assertEquals("b", issues.single().key)
    }

    @Test fun extraKeysAreFound() {
        val issues = CatalogAudit.check(GameLocale.FR, mapOf("a" to "Un", "z" to "Zed"), mapOf("a" to "A"))
        assertEquals(listOf(CatalogIssue.Kind.EXTRA), issues.map { it.kind })
    }

    @Test fun blankTextIsFound() {
        val issues = CatalogAudit.check(GameLocale.DE, mapOf("a" to "  "), mapOf("a" to "A"))
        assertEquals(CatalogIssue.Kind.BLANK, issues.single().kind)
    }

    @Test fun brokenSyntaxIsFound() {
        val issues = CatalogAudit.check(GameLocale.DE, mapOf("a" to "{oops"), mapOf("a" to "A"))
        assertEquals(CatalogIssue.Kind.BAD_SYNTAX, issues.single().kind)
    }

    @Test fun argumentMismatchIsFound() {
        val issues = CatalogAudit.check(GameLocale.ES, mapOf("a" to "Dia {dia}"), mapOf("a" to "Day {day}"))
        assertEquals(CatalogIssue.Kind.ARGUMENT_MISMATCH, issues.single().kind)
    }

    @Test fun matchingArgumentsPass() {
        assertTrue(CatalogAudit.check(GameLocale.ES, mapOf("a" to "Día {day}"), mapOf("a" to "Day {day}")).isEmpty())
    }

    @Test fun issuesComeSortedByKey() {
        val issues = CatalogAudit.check(GameLocale.FR, emptyMap(), mapOf("c" to "C", "a" to "A", "b" to "B"))
        assertEquals(listOf("a", "b", "c"), issues.map { it.key })
    }

    @Test fun keysAreDottedLowerCamel() {
        val shape = Regex("[a-z]+(\\.[a-z][A-Za-z]*)+")
        for (k in Catalogs.keys) assertTrue(k, shape.matches(k))
    }

    @Test fun everyLanguageHasTheSameKeys() {
        for (l in GameLocale.values()) assertEquals(Catalogs.keys, Catalogs.of(l).keys)
    }
}
