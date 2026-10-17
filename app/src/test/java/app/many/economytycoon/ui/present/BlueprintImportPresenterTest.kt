package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.BlueprintLibrary
import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.blueprint.BlueprintText
import app.many.economytycoon.blueprint.LibraryLimits
import app.many.economytycoon.blueprint.TextEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintImportPresenterTest {
    private val code = BlueprintText.encode(T.sample())

    private fun preview(text: String, lib: BlueprintLibrary = BlueprintLibrary()) = BlueprintImportPresenter.preview(text, lib, T.content)

    @Test fun emptyTextAsksForPaste() {
        val m = preview("  ")
        assertFalse(m.canImport)
        assertEquals(BlueprintImportPresenter.HINT, m.hint)
        assertEquals("Nothing pasted yet.", m.summary)
    }

    @Test fun textWithoutCodesSaysSo() {
        val m = preview("hello there")
        assertEquals("No blueprint code found.", m.summary)
        assertFalse(m.canImport)
    }

    @Test fun validCodeIsOfferedAndPreviewDoesNotSave() {
        val lib = BlueprintLibrary()
        val m = preview(code, lib)
        val item = m.items.single()
        assertEquals(ImportItemKind.NEW, item.kind)
        assertEquals("Corner bakery", item.title)
        assertEquals("Bakery · 3 lines", item.subtitle)
        assertEquals(Tone.GOOD, item.tone)
        assertTrue(m.canImport)
        assertEquals("1 new", m.summary)
        assertEquals(0, lib.size)
    }

    @Test fun alreadySavedAndRepeatedCodesAreDuplicates() {
        val lib = BlueprintLibrary().also { it.add(T.sample("Mine")) }
        val m = preview(code, lib)
        assertEquals(ImportItemKind.DUPLICATE, m.items.single().kind)
        assertTrue(m.items.single().message.contains("'Mine'"))
        assertFalse(m.canImport)
        val twice = preview("$code\n$code")
        assertEquals(listOf(ImportItemKind.NEW, ImportItemKind.DUPLICATE), twice.items.map { it.kind })
        assertEquals("1 new, 1 already saved", twice.summary)
    }

    @Test fun damagedCodeShowsPositionAndExcerpt() {
        val broken = code.substring(0, 20) + "#" + code.substring(21)
        val item = preview("my bakery:\n$broken").items.single()
        assertEquals(ImportItemKind.INVALID, item.kind)
        assertEquals(Tone.BAD, item.tone)
        assertEquals("line 2, column 21", item.where)
        assertTrue(item.excerpt!!.contains("⟦#⟧"))
        assertTrue(item.message.contains("'#'"))
    }

    @Test fun mixedBlocksAreSummarised() {
        val m = preview("$code\n\nETBP1-B32:12$:0000000\n\n${BlueprintText.encode(T.sample("Other").copy(spec = T.sampleSpec().copy(qualityPpm = 7L)), TextEncoding.BASE64)}")
        assertEquals(listOf(ImportItemKind.NEW, ImportItemKind.INVALID, ImportItemKind.NEW), m.items.map { it.kind })
        assertEquals("2 new, 1 damaged", m.summary)
        assertEquals(1, m.errorCount)
        assertEquals(2, m.newCount)
    }

    @Test fun fullLibraryLeavesNoRoom() {
        val lib = BlueprintLibrary(LibraryLimits(maxEntries = 1)).also { it.add(T.sample("Other").copy(spec = T.sampleSpec().copy(qualityPpm = 9L))) }
        val m = preview(code, lib)
        assertEquals(ImportItemKind.NO_ROOM, m.items.single().kind)
        assertFalse(m.canImport)
        assertTrue(m.summary.contains("no room"))
    }

    @Test fun roomIsCountedAcrossBlocks() {
        val lib = BlueprintLibrary(LibraryLimits(maxEntries = 1))
        val second = BlueprintText.encode(T.sample("Two").copy(spec = T.sampleSpec().copy(qualityPpm = 5L)))
        assertEquals(listOf(ImportItemKind.NEW, ImportItemKind.NO_ROOM), preview("$code\n$second", lib).items.map { it.kind })
    }

    @Test fun migrationNotesComeAlong() {
        val w = app.many.economytycoon.core.ByteWriter()
        w.writeVarInt(1); w.writeString("Legacy"); w.writeString(""); w.writeString(""); w.writeVarLong(0)
        w.writeString("bakery"); w.writeVarInt(0); w.writeVarLong(20)
        repeat(4) { w.writeVarInt(0) }
        val b = w.toByteArray()
        val m = preview("ETBP1-B32:${BlueprintText.base32(b)}:${BlueprintText.checksum(b)}")
        assertTrue(m.items.single().notes.isNotEmpty())
    }

    @Test fun excerptHandlesTheEdges() {
        assertEquals("⟦a⟧bc", BlueprintImportPresenter.excerpt("abc", 0))
        assertEquals("abc⟦end⟧", BlueprintImportPresenter.excerpt("abc", 3))
        assertEquals("a b⟦c⟧", BlueprintImportPresenter.excerpt("a\nb" + "c", 3))
        val long = "x".repeat(40) + "!" + "y".repeat(40)
        val ex = BlueprintImportPresenter.excerpt(long, 40)
        assertTrue(ex.startsWith("…") && ex.endsWith("…") && ex.contains("⟦!⟧"))
        assertNull(null)
    }
}
