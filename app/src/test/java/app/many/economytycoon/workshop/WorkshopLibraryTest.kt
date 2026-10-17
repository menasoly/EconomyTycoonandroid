package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopLibraryTest {
    private fun draft(id: String, title: String = id.replaceFirstChar { it.uppercase() }) = Samples.minimal(id).copy(title = title)
    private fun ok(o: Outcome<Long>): Long = (o as Outcome.Ok).value
    private fun <T> code(o: Outcome<T>) = (o as Outcome.Rejected).rejection.rule

    @Test fun addedDraftsGetAscendingIds() {
        val lib = WorkshopLibrary()
        assertEquals(1L, ok(lib.add(draft("a"))))
        assertEquals(2L, ok(lib.add(draft("b"))))
        assertEquals(2, lib.size)
        assertEquals("a", lib.get(1)?.draft?.id)
    }

    @Test fun twoDraftsCannotShareAScenarioId() {
        val lib = WorkshopLibrary().also { it.add(draft("a")) }
        assertEquals(Rules.DUPLICATE, code(lib.add(draft("a"))))
        assertEquals(1, lib.size)
    }

    @Test fun uniqueIdsCountUpward() {
        val lib = WorkshopLibrary().also { it.add(draft("a")); it.add(draft("a-2")) }
        assertEquals("fresh", lib.uniqueId("fresh"))
        assertEquals("a-3", lib.uniqueId("a"))
    }

    @Test fun theCapacityIsEnforced() {
        val lib = WorkshopLibrary(capacity = 2)
        lib.add(draft("a")); lib.add(draft("b"))
        assertEquals(Rules.LIMIT_EXCEEDED, code(lib.add(draft("c"))))
    }

    @Test fun foldersMustBeWellFormed() {
        val lib = WorkshopLibrary()
        for (bad in listOf("/x", "x/", "a//b", "tab\tx", "x".repeat(WorkshopLibrary.MAX_FOLDER + 1))) {
            assertEquals(bad, Rules.INVALID_ARGUMENT, code(lib.add(draft("a"), folder = bad)))
        }
        assertEquals(0, lib.size)
        assertTrue(lib.add(draft("a"), folder = "tests/farm") is Outcome.Ok)
    }

    @Test fun tagsAreNormalisedSortedAndDistinct() {
        val lib = WorkshopLibrary()
        val id = ok(lib.add(draft("a"), tags = listOf("Zoo", "apple", "ZOO")))
        assertEquals(listOf("apple", "zoo"), lib.get(id)?.tags)
        assertEquals(Rules.INVALID_ARGUMENT, code(lib.add(draft("b"), tags = listOf("not a tag"))))
        assertEquals(Rules.LIMIT_EXCEEDED, code(lib.add(draft("c"), tags = List(9) { "t$it" })))
    }

    @Test fun tagAndUntagWork() {
        val lib = WorkshopLibrary()
        val id = ok(lib.add(draft("a")))
        lib.tag(id, " Farm ")
        lib.tag(id, "farm")
        assertEquals(listOf("farm"), lib.get(id)?.tags)
        assertEquals(Rules.INVALID_ARGUMENT, code(lib.tag(id, "no good")))
        lib.untag(id, "FARM")
        assertTrue(lib.get(id)?.tags?.isEmpty() == true)
        assertTrue(lib.untag(id, "nothing") is Outcome.Ok)
    }

    @Test fun tagLimitHolds() {
        val lib = WorkshopLibrary()
        val id = ok(lib.add(draft("a"), tags = List(WorkshopLibrary.MAX_TAGS) { "t$it" }))
        assertEquals(Rules.LIMIT_EXCEEDED, code(lib.tag(id, "extra")))
    }

    @Test fun updatingKeepsTheFilingAndBumpsTheClock() {
        val lib = WorkshopLibrary()
        val id = ok(lib.add(draft("a"), folder = "f", tags = listOf("x"), note = "n"))
        val before = requireNotNull(lib.get(id))
        val e = (lib.update(id, draft("a", "New title")) as Outcome.Ok).value
        assertEquals("New title", e.draft.title)
        assertEquals("f", e.folder)
        assertEquals(listOf("x"), e.tags)
        assertTrue(e.updated > before.updated)
        assertEquals(before.created, e.created)
    }

    @Test fun updatingCannotStealAnotherEntrysId() {
        val lib = WorkshopLibrary()
        lib.add(draft("a")); val b = ok(lib.add(draft("b")))
        assertEquals(Rules.DUPLICATE, code(lib.update(b, draft("a"))))
        assertEquals("b", lib.get(b)?.draft?.id)
    }

    @Test fun unknownEntriesAreRejectedEverywhere() {
        val lib = WorkshopLibrary()
        assertEquals(Rules.UNKNOWN_ENTITY, code(lib.update(9, draft("a"))))
        assertEquals(Rules.UNKNOWN_ENTITY, code(lib.move(9, "x")))
        assertEquals(Rules.UNKNOWN_ENTITY, code(lib.setNote(9, "x")))
        assertEquals(Rules.UNKNOWN_ENTITY, code(lib.star(9, true)))
        assertEquals(Rules.UNKNOWN_ENTITY, code(lib.tag(9, "x")))
        assertEquals(Rules.UNKNOWN_ENTITY, code(lib.duplicate(9)))
        assertEquals(Rules.UNKNOWN_ENTITY, code(lib.exportShare(9)))
        assertFalse(lib.remove(9))
    }

    @Test fun removingFreesTheScenarioIdButNotTheEntryId() {
        val lib = WorkshopLibrary()
        val a = ok(lib.add(draft("a")))
        assertTrue(lib.remove(a))
        assertNull(lib.byScenarioId("a"))
        assertEquals(a + 1, ok(lib.add(draft("a"))))
    }

    @Test fun notesAreLimited() {
        val lib = WorkshopLibrary()
        val id = ok(lib.add(draft("a")))
        assertEquals(Rules.INVALID_ARGUMENT, code(lib.setNote(id, "x".repeat(WorkshopLibrary.MAX_NOTE + 1))))
        assertEquals("fine", (lib.setNote(id, "fine") as Outcome.Ok).value.note)
        assertEquals(Rules.INVALID_ARGUMENT, code(lib.add(draft("b"), note = "x".repeat(WorkshopLibrary.MAX_NOTE + 1))))
    }

    @Test fun starringIsIdempotent() {
        val lib = WorkshopLibrary()
        val id = ok(lib.add(draft("a")))
        val t = (lib.star(id, true) as Outcome.Ok).value.updated
        assertEquals(t, (lib.star(id, true) as Outcome.Ok).value.updated)
        assertFalse((lib.star(id, false) as Outcome.Ok).value.starred)
    }

    @Test fun duplicatesGetAFreshIdAndMarkedTitle() {
        val lib = WorkshopLibrary()
        val a = ok(lib.add(draft("farm", "Farm"), folder = "f", tags = listOf("x")))
        val c1 = requireNotNull(lib.get(ok(lib.duplicate(a))))
        val c2 = requireNotNull(lib.get(ok(lib.duplicate(a))))
        assertEquals("farm-copy", c1.draft.id)
        assertEquals("farm-copy-2", c2.draft.id)
        assertEquals("Farm copy", c1.draft.title)
        assertEquals("f", c1.folder)
        assertEquals(listOf("x"), c1.tags)
    }

    @Test fun movingAndRenamingFolders() {
        val lib = WorkshopLibrary()
        val a = ok(lib.add(draft("a"), folder = "old"))
        val b = ok(lib.add(draft("b"), folder = "old/deep"))
        val c = ok(lib.add(draft("c"), folder = "oldish"))
        assertEquals(2, (lib.renameFolder("old", "new") as Outcome.Ok).value)
        assertEquals("new", lib.get(a)?.folder)
        assertEquals("new/deep", lib.get(b)?.folder)
        assertEquals("oldish", lib.get(c)?.folder)
        assertEquals(Rules.INVALID_ARGUMENT, code(lib.renameFolder("", "x")))
        assertEquals(Rules.INVALID_ARGUMENT, code(lib.renameFolder("new", "/bad")))
        lib.move(c, "")
        assertEquals("", lib.get(c)?.folder)
    }

    @Test fun foldersAndTagsAreCounted() {
        val lib = WorkshopLibrary()
        lib.add(draft("a"), folder = "x", tags = listOf("t1", "t2")); lib.add(draft("b"), folder = "x", tags = listOf("t1")); lib.add(draft("c"))
        assertEquals(mapOf("" to 1, "x" to 2), lib.folders())
        assertEquals(mapOf("t1" to 2, "t2" to 1), lib.tags())
    }

    private fun shelf(): WorkshopLibrary {
        val lib = WorkshopLibrary()
        lib.add(draft("alpha", "Alpha Farm").copy(tagline = "Grow wheat", tags = listOf(DifficultyTag.GENTLE)), folder = "farms", tags = listOf("wheat"))
        lib.add(draft("bravo", "Bravo Mill").copy(tags = listOf(DifficultyTag.HARSH)), folder = "farms/mills", note = "needs work")
        lib.add(draft("charlie", "Charlie Port").copy(tags = listOf(DifficultyTag.STANDARD)), folder = "ports")
        lib.add(draft("delta", "Delta"))
        return lib
    }

    private fun names(l: List<LibraryEntry>) = l.map { it.draft.id }

    @Test fun searchMatchesWordsAcrossFields() {
        val lib = shelf()
        assertEquals(listOf("alpha"), names(lib.search(LibraryQuery(text = "wheat"))))
        assertEquals(listOf("bravo"), names(lib.search(LibraryQuery(text = "NEEDS work"))))
        assertEquals(listOf("alpha"), names(lib.search(LibraryQuery(text = "alpha gentle"))))
        assertTrue(lib.search(LibraryQuery(text = "zzz")).isEmpty())
    }

    @Test fun folderFiltersIncludeSubfolders() {
        val lib = shelf()
        assertEquals(setOf("alpha", "bravo"), names(lib.search(LibraryQuery(folder = "farms"))).toSet())
        assertEquals(listOf("bravo"), names(lib.search(LibraryQuery(folder = "farms/mills"))))
        assertEquals(listOf("delta"), names(lib.search(LibraryQuery(folder = ""))).filter { it == "delta" })
    }

    @Test fun tagDifficultyAndStarFilters() {
        val lib = shelf()
        assertEquals(listOf("alpha"), names(lib.search(LibraryQuery(tag = "WHEAT"))))
        assertEquals(listOf("bravo"), names(lib.search(LibraryQuery(difficulty = DifficultyTag.HARSH))))
        lib.star(requireNotNull(lib.byScenarioId("delta")).id, true)
        assertEquals(listOf("delta"), names(lib.search(LibraryQuery(starredOnly = true))))
    }

    @Test fun sortOrders() {
        val lib = shelf()
        assertEquals(listOf("alpha", "bravo", "charlie", "delta"), names(lib.search(LibraryQuery(sort = LibrarySort.TITLE))))
        assertEquals(listOf("alpha", "bravo", "charlie", "delta"), names(lib.search(LibraryQuery(sort = LibrarySort.OLDEST))))
        assertEquals(listOf("delta", "charlie", "bravo", "alpha"), names(lib.search(LibraryQuery(sort = LibrarySort.RECENT))))
        assertEquals(listOf("alpha", "charlie", "delta", "bravo"), names(lib.search(LibraryQuery(sort = LibrarySort.DIFFICULTY))))
    }

    @Test fun changingAnEntryMovesItToTheTopOfRecent() {
        val lib = shelf()
        lib.setNote(requireNotNull(lib.byScenarioId("alpha")).id, "touched")
        assertEquals("alpha", names(lib.search()).first())
    }

    @Test fun sharingRoundTripsThroughAnotherLibrary() {
        val lib = shelf()
        val text = (lib.exportShare(requireNotNull(lib.byScenarioId("alpha")).id) as Outcome.Ok).value
        val other = WorkshopLibrary()
        val id = ok(other.importShare(text, folder = "inbox"))
        assertEquals(requireNotNull(lib.byScenarioId("alpha")).draft, other.get(id)?.draft)
        assertEquals("inbox", other.get(id)?.folder)
    }

    @Test fun importingAnExistingIdFilesItUnderAFreeOne() {
        val lib = shelf()
        val text = (lib.exportShare(requireNotNull(lib.byScenarioId("alpha")).id) as Outcome.Ok).value
        val id = ok(lib.importShare(text))
        assertEquals("alpha-2", lib.get(id)?.draft?.id)
        assertEquals(5, lib.size)
    }

    @Test fun importingGarbageIsRejected() {
        val lib = WorkshopLibrary()
        assertEquals(Rules.INVALID_ARGUMENT, code(lib.importShare("this is not a code")))
        assertEquals(0, lib.size)
    }

    @Test fun tagNormalisation() {
        assertEquals("a-1", WorkshopLibrary.normaliseTag(" A-1 "))
        assertNull(WorkshopLibrary.normaliseTag(""))
        assertNull(WorkshopLibrary.normaliseTag("x".repeat(21)))
        assertNotNull(WorkshopLibrary.normaliseTag("x".repeat(20)))
    }
}
