package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.AddResult
import app.many.economytycoon.blueprint.Blueprint
import app.many.economytycoon.blueprint.BlueprintController
import app.many.economytycoon.blueprint.BlueprintQuery
import app.many.economytycoon.blueprint.BlueprintSpec
import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.blueprint.Compatibility
import app.many.economytycoon.blueprint.FakeHost
import app.many.economytycoon.blueprint.LibraryEntry
import app.many.economytycoon.blueprint.BlueprintLibrary
import app.many.economytycoon.blueprint.LibraryLimits
import app.many.economytycoon.blueprint.SortKey
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.persistence.MemorySaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintLibraryPresenterTest {
    private fun lib(): BlueprintLibrary {
        val l = BlueprintLibrary()
        l.add(Blueprint.of(T.sampleSpec(), "Corner bakery"), "food/bread", listOf("bread"), true, 1)
        l.add(Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour))), "Mill"), "food", emptyList(), false, 2)
        l.add(Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(null))), "Blank"), "", emptyList(), false, 3)
        return l
    }

    private fun model(l: BlueprintLibrary, q: BlueprintQuery = BlueprintQuery(), compat: (LibraryEntry) -> Compatibility = { Compatibility.COMPATIBLE }) =
        BlueprintLibraryPresenter.model(l.all(), l.folders(), l.tags(), l.find(q), q, T.content, compat, l.isFull)

    @Test fun rowsDescribeEachBlueprint() {
        val m = model(lib())
        val r = m.rows.first { it.name == "Corner bakery" }
        assertEquals("Bakery · 3 lines · 3 machines", r.subtitle)
        assertEquals("Mill flour, Bake bread, Bake sourdough", r.recipes)
        assertTrue(r.favourite)
        assertEquals(listOf("bread"), r.tags)
        assertEquals("Up to date", r.status)
        assertEquals(Tone.GOOD, r.statusTone)
        assertEquals("Bakery · 1 line", m.rows.first { it.name == "Mill" }.subtitle)
        assertEquals("", m.rows.first { it.name == "Blank" }.recipes)
    }

    @Test fun tilesCountSavedFoldersAndOutOfDate() {
        val l = lib()
        val m = model(l) { if (it.name == "Mill") Compatibility.PARTIAL else Compatibility.COMPATIBLE }
        assertEquals("3", m.tiles[0].value)
        assertEquals("1 favourites", m.tiles[0].note)
        assertEquals("2", m.tiles[1].value)
        assertEquals("1", m.tiles[2].value)
        assertEquals(Tone.WARN, m.tiles[2].tone)
        assertEquals(Tone.WARN, m.rows.first { it.name == "Mill" }.statusTone)
    }

    @Test fun foldersListAllFirstWithCountsIncludingSubfolders() {
        val l = lib()
        val rows = model(l).folders
        assertEquals(listOf("", "food", "food/bread"), rows.map { it.path })
        assertEquals(listOf(3, 2, 1), rows.map { it.count })
        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
        assertEquals("bread", rows[2].label)
        assertTrue(rows[0].selected)
        assertTrue(model(l, BlueprintQuery(folder = "food")).folders[1].selected)
    }

    @Test fun emptyStatesAreDistinguished() {
        assertEquals(BlueprintLibraryPresenter.EMPTY, model(BlueprintLibrary()).empty)
        assertEquals(BlueprintLibraryPresenter.NO_MATCH, model(lib(), BlueprintQuery(text = "zzz")).empty)
        assertNull(model(lib()).empty)
    }

    @Test fun searchAndSortReachTheRows() {
        val l = lib()
        assertEquals(listOf("Corner bakery"), model(l, BlueprintQuery(text = "sourdough")).rows.map { it.name })
        assertEquals(listOf("Blank", "Corner bakery", "Mill"), model(l).rows.map { it.name })
        assertEquals(listOf("Blank", "Mill", "Corner bakery"), model(l, BlueprintQuery(sort = SortKey.NEWEST)).rows.map { it.name })
        assertEquals(SortKey.values().toList(), model(l).sorts)
    }

    @Test fun copiesAreMarkedAndFullIsReported() {
        val l = BlueprintLibrary(LibraryLimits(maxEntries = 2))
        val e = (l.add(T.sample()) as AddResult.Added).entry
        l.duplicate(e.id)
        val m = model(l)
        assertEquals(listOf(false, true), m.rows.sortedBy { it.name }.map { it.isCopy })
        assertTrue(m.full)
    }

    @Test fun buildNeedsAGame() {
        val c = BlueprintController.open(MemorySaveStore()) { null }
        assertNull(BlueprintLibraryPresenter.build(c))
    }

    @Test fun buildUsesTheHostContentAndReportsCompatibility() {
        val host = FakeHost().also { it.put(T.view(id = 7)) }
        val c = BlueprintController.open(MemorySaveStore()) { host }
        c.capture(FacilityId(7)).getOrThrow()
        c.edit { it.add(T.sample("Old").copy(contentTag = "test@1.0", spec = T.sampleSpec().copy(qualityPpm = 1L))) }
        val m = BlueprintLibraryPresenter.build(c)!!
        assertEquals(2, m.rows.size)
        assertEquals(setOf("Up to date", "Needs updating"), m.rows.map { it.status }.toSet())
        assertTrue(m.warnings.isEmpty())
    }

    @Test fun droppedEntriesProduceAWarning() {
        val store = MemorySaveStore()
        store.put("blueprints.library", ByteArray(5) { 1 })
        val c = BlueprintController.open(store) { FakeHost() }
        val m = BlueprintLibraryPresenter.build(c)!!
        assertFalse(m.warnings.isEmpty())
        assertTrue(m.warnings.single().contains("unreadable"))
    }
}
