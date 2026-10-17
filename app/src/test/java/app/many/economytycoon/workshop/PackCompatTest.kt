package app.many.economytycoon.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackCompatTest {
    private val content = Samples.content
    private val loaded = PackCompat.loadedPacks(content)

    @Test fun loadedPacksAreWrittenAsIdMajorMinorAndSorted() {
        assertTrue(loaded.isNotEmpty())
        assertEquals(loaded.sorted(), loaded)
        assertTrue(loaded.all { Regex("[a-z0-9_-]+:\\d+\\.\\d+").matches(it) })
    }

    @Test fun stampingPinsTheDraftToTheLoadedContent() {
        val stamped = PackCompat.stamp(Samples.cheap(), content)
        assertEquals(loaded, stamped.packs)
        val rep = PackCompat.check(stamped, content)
        assertFalse(rep.has("pack.missing"))
        assertFalse(rep.has("pack.major"))
        assertFalse(rep.has("pack.unpinned"))
    }

    @Test fun anUnpinnedDraftGetsANote() {
        val rep = PackCompat.check(Samples.cheap().copy(packs = emptyList()), content)
        assertEquals(IssueSeverity.NOTE, rep.withCode("pack.unpinned").single().severity)
        assertTrue(rep.compatible)
    }

    @Test fun aMissingPackIsAnError() {
        val rep = PackCompat.check(Samples.cheap().copy(packs = listOf("nowhere:1.0")), content)
        val f = rep.withCode("pack.missing").single()
        assertEquals("packs[0]", f.path)
        assertFalse(rep.compatible)
    }

    @Test fun aBadlyWrittenPackIsAFormatError() {
        val rep = PackCompat.check(Samples.cheap().copy(packs = listOf("Base 1")), content)
        assertTrue(rep.has("pack.format"))
        assertFalse(rep.compatible)
    }

    private fun spec(major: Int, minor: Int): String {
        val first = loaded.first().substringBefore(':')
        return "$first:$major.$minor"
    }

    private fun versionOfFirst(): Pair<Int, Int> {
        val v = loaded.first().substringAfter(':').split('.')
        return v[0].toInt() to v[1].toInt()
    }

    @Test fun aDifferentMajorVersionIsAnError() {
        val (maj, min) = versionOfFirst()
        val rep = PackCompat.check(Samples.cheap().copy(packs = listOf(spec(maj + 1, min))), content)
        assertTrue(rep.has("pack.major"))
    }

    @Test fun aNewerMinorRequirementIsAnError() {
        val (maj, min) = versionOfFirst()
        val rep = PackCompat.check(Samples.cheap().copy(packs = listOf(spec(maj, min + 1))), content)
        assertTrue(rep.has("pack.minor_old"))
        assertFalse(rep.compatible)
    }

    @Test fun anOlderMinorRequirementIsOnlyANote() {
        val (maj, min) = versionOfFirst()
        if (min == 0) return
        val rep = PackCompat.check(Samples.cheap().copy(packs = listOf(spec(maj, min - 1))), content)
        assertEquals(IssueSeverity.NOTE, rep.withCode("pack.newer").single().severity)
        assertTrue(rep.compatible)
    }

    @Test fun anUnknownStarterRecipeIsAnError() {
        val d = Samples.cheap()
        val bad = d.copy(company = d.company.copy(starters = d.company.starters + StarterSite("no_such_recipe", 0)))
        val rep = PackCompat.check(bad, content)
        assertTrue(rep.errors.any { it.code == "starter.unknown" && it.path.startsWith("company.starters[") })
    }

    @Test fun anExpensiveStarterIsOnlyAWarning() {
        val d = Samples.cheap()
        val poor = d.copy(company = d.company.copy(capital = 1))
        val rep = PackCompat.check(poor, content)
        if (d.company.starters.isNotEmpty()) assertTrue(rep.has("starter.expensive"))
        assertTrue(rep.compatible)
    }

    @Test fun goalsBeyondTheContentAreErrors() {
        val d = Samples.cheap().copy(goals = listOf(DraftGoal(app.many.economytycoon.scenarios.GoalKind.TECHNOLOGIES, 10_000), DraftGoal(app.many.economytycoon.scenarios.GoalKind.INDUSTRIES, 10_000)))
        val rep = PackCompat.check(d, content)
        assertEquals(listOf("goals[0].target", "goals[1].target"), rep.errors.map { it.path }.filter { it.startsWith("goals") })
    }

    @Test fun findingsAreOrderedErrorsFirst() {
        val rep = PackCompat.check(Samples.cheap().copy(packs = listOf("nowhere:1.0", "base:x")), content)
        assertEquals(rep.findings.sortedBy { it.severity.ordinal }.map { it.severity }, rep.findings.map { it.severity })
    }

    @Test fun findingsPrintTheirPathAndCode() {
        val f = CompatFinding("pack.missing", IssueSeverity.ERROR, "packs[0]", "gone")
        assertEquals("ERROR   packs[0]  pack.missing: gone", f.toString())
    }

    @Test fun checkingIsRepeatable() {
        val d = Samples.cheap().copy(packs = listOf("nowhere:1.0"))
        assertEquals(PackCompat.check(d, content).findings, PackCompat.check(d, content).findings)
    }
}
