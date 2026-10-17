package app.many.economytycoon.ui.present

import app.many.economytycoon.workshop.DifficultyTag
import app.many.economytycoon.workshop.LibraryQuery
import app.many.economytycoon.workshop.Samples
import app.many.economytycoon.workshop.WorkshopLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopLibraryPresenterTest {
    private fun shelf(): WorkshopLibrary {
        val lib = WorkshopLibrary()
        lib.add(Samples.minimal("a").copy(title = "Alpha", tags = listOf(DifficultyTag.GENTLE)), folder = "farms", tags = listOf("wheat"))
        lib.add(Samples.minimal("b").copy(title = "Bravo"), folder = "farms")
        lib.add(Samples.cheap("c").copy(title = "Charlie"))
        return lib
    }

    @Test fun anEmptyShelfInvitesTheFirstSave() {
        val m = WorkshopLibraryPresenter.model(WorkshopLibrary(), LibraryQuery())
        assertEquals(WorkshopLibraryPresenter.EMPTY, m.empty)
        assertTrue(m.rows.isEmpty())
    }

    @Test fun noMatchIsDifferentFromEmpty() {
        val m = WorkshopLibraryPresenter.model(shelf(), LibraryQuery(text = "zzz"))
        assertEquals(WorkshopLibraryPresenter.NO_MATCH, m.empty)
    }

    @Test fun rowsDescribeEachDraft() {
        val m = WorkshopLibraryPresenter.model(shelf(), LibraryQuery(sort = app.many.economytycoon.workshop.LibrarySort.TITLE))
        assertEquals(listOf("Alpha", "Bravo", "Charlie"), m.rows.map { it.title })
        assertTrue(m.rows[0].subtitle.endsWith("Gentle"))
        assertEquals(listOf("wheat"), m.rows[0].tags)
    }

    @Test fun tilesCountSavedShownAndStarred() {
        val lib = shelf(); lib.star(1, true)
        val m = WorkshopLibraryPresenter.model(lib, LibraryQuery(folder = "farms"))
        assertEquals(listOf("3", "2", "1"), m.tiles.map { it.value })
    }

    @Test fun folderChipsStartWithAllAndMarkTheChosenOne() {
        val m = WorkshopLibraryPresenter.model(shelf(), LibraryQuery(folder = "farms"))
        assertEquals(listOf("All", "Top level", "farms"), m.folders.map { it.label })
        assertEquals(listOf(3, 1, 2), m.folders.map { it.count })
        assertEquals("farms", m.folders.single { it.selected }.path)
        assertTrue(WorkshopLibraryPresenter.model(shelf(), LibraryQuery()).folders.first().selected)
    }

    @Test fun tappingAChosenFilterClearsIt() {
        val q = WorkshopLibraryPresenter.withFolder(LibraryQuery(), "farms")
        assertEquals("farms", q.folder)
        assertNull(WorkshopLibraryPresenter.withFolder(q, "farms").folder)
        assertEquals("wheat", WorkshopLibraryPresenter.withTag(LibraryQuery(), "wheat").tag)
        assertNull(WorkshopLibraryPresenter.withTag(LibraryQuery(tag = "wheat"), "wheat").tag)
        assertEquals(DifficultyTag.HARSH, WorkshopLibraryPresenter.withDifficulty(LibraryQuery(), DifficultyTag.HARSH).difficulty)
        assertNull(WorkshopLibraryPresenter.withDifficulty(LibraryQuery(difficulty = DifficultyTag.HARSH), DifficultyTag.HARSH).difficulty)
    }

    @Test fun filtersActiveDetectsAnyFilter() {
        assertFalse(WorkshopLibraryPresenter.filtersActive(LibraryQuery()))
        assertFalse(WorkshopLibraryPresenter.filtersActive(LibraryQuery(text = "  ")))
        assertTrue(WorkshopLibraryPresenter.filtersActive(LibraryQuery(text = "a")))
        assertTrue(WorkshopLibraryPresenter.filtersActive(LibraryQuery(starredOnly = true)))
    }

    @Test fun tagsOfTheShelfAreOffered() {
        assertEquals(listOf("wheat"), WorkshopLibraryPresenter.model(shelf(), LibraryQuery()).tags)
    }
}
