package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShareBundleTest {
    private val a = Samples.full()
    private val b = Samples.cheap("second")
    private val c = Samples.minimal("third")

    @Test fun aBundleRoundTrips() {
        val r = ShareBundle.decode(ShareBundle.encode(listOf(a, b, c)))
        assertTrue(r.complete)
        assertEquals(listOf(a, b, c).map { it.normalised() }, r.drafts)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun theHeaderCountsScenarios() {
        assertTrue(ShareBundle.encode(listOf(a)).startsWith("Economy Tycoon scenario bundle: 1 scenario\n"))
        assertTrue(ShareBundle.encode(listOf(a, b)).startsWith("Economy Tycoon scenario bundle: 2 scenarios\n"))
    }

    @Test fun aSingleCodeIsABundleOfOne() {
        val r = ShareBundle.decode(ShareCodec.encode(a))
        assertEquals(listOf(a.normalised()), r.drafts)
    }

    @Test fun textAroundTheCodesIsIgnored() {
        val text = "Hi! Try these two.\n\n" + ShareCodec.encode(a) + "\n\nthanks, and\nthis one too:\n\n" + ShareCodec.encode(b)
        assertEquals(2, ShareBundle.decode(text).drafts.size)
    }

    @Test fun aDamagedScenarioCostsOnlyItself() {
        val bad = ShareCodec.encode(b).replaceFirst("ETW1-", "ETW1-ZZZZZ-")
        val r = ShareBundle.decode(listOf(ShareCodec.encode(a), bad, ShareCodec.encode(c)).joinToString("\n\n"))
        assertEquals(listOf(a.normalised(), c.normalised()), r.drafts)
        assertEquals(listOf(2), r.failures.map { it.index })
        assertFalse(r.complete)
    }

    @Test fun failuresPointAtTheRightLineOfTheWholeText() {
        val first = ShareCodec.encode(a)
        val bad = ShareCodec.encode(b).replaceFirst("ETW1-", "ETW1-ZZZZZ-")
        val r = ShareBundle.decode(first + "\n\n" + bad)
        val firstLines = first.lines().size
        assertTrue(r.failures.single().error.at.line > firstLines)
    }

    @Test fun repeatedIdsAreSkippedWithAWarning() {
        val r = ShareBundle.decode(ShareCodec.encode(a) + "\n\n" + ShareCodec.encode(a))
        assertEquals(1, r.drafts.size)
        assertTrue(r.warnings.single().contains("repeats the id"))
    }

    @Test fun emptyTextIsAnEmptyBundle() {
        val r = ShareBundle.decode("   \n\n  ")
        assertTrue(r.drafts.isEmpty() && r.complete)
    }

    @Test fun bundlesRefuseDuplicateIdsAndTooMany() {
        try { ShareBundle.encode(listOf(a, a)); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
        val many = (1..26).map { Samples.minimal("s$it") }
        try { ShareBundle.encode(many); fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun theLibraryExportsAndImportsBundles() {
        val lib = WorkshopLibrary()
        val ids = listOf(a, b, c).map { (lib.add(it) as Outcome.Ok).value }
        val text = (lib.exportBundle(ids.take(2)) as Outcome.Ok).value
        val other = WorkshopLibrary()
        val r = other.importBundle(text, folder = "inbox")
        assertEquals(2, r.added.size)
        assertTrue(r.problems.isEmpty())
        assertEquals(listOf("inbox", "inbox"), r.added.map { other.get(it)?.folder })
    }

    @Test fun importingABundleTwiceGivesFreshIds() {
        val lib = WorkshopLibrary()
        val id = (lib.add(a) as Outcome.Ok).value
        val text = (lib.exportBundle(listOf(id)) as Outcome.Ok).value
        val r = lib.importBundle(text)
        assertEquals("${a.id}-2", lib.get(r.added.single())?.draft?.id)
    }

    @Test fun importReportsProblemsWithoutStopping() {
        val lib = WorkshopLibrary(capacity = 1)
        val text = ShareBundle.encode(listOf(a, b))
        val r = lib.importBundle(text)
        assertEquals(1, r.added.size)
        assertEquals(1, r.problems.size)
        assertTrue(r.problems.single().contains("at most 1"))
    }

    @Test fun exportingNothingIsRejected() {
        val lib = WorkshopLibrary()
        assertEquals(Rules.INVALID_ARGUMENT, ((lib.exportBundle(listOf(7L)) as Outcome.Rejected).rejection.rule))
    }
}
