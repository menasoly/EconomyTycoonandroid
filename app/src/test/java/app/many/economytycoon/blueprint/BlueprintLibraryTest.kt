package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintLibraryTest {
    private fun variant(name: String, quality: Long) = Blueprint.of(T.sampleSpec().copy(qualityPpm = quality), name)
    private fun added(r: AddResult) = (r as AddResult.Added).entry

    private fun lib(n: Int = 3): BlueprintLibrary {
        val l = BlueprintLibrary()
        for (i in 1..n) l.add(variant("Plan $i", i * 10_000L), day = i.toLong())
        return l
    }

    @Test fun addAssignsIdsAndStoresEntries() {
        val l = BlueprintLibrary()
        val e = added(l.add(T.sample(), "ovens", listOf("Bread", "starter"), true, 5))
        assertEquals(BlueprintId(1), e.id)
        assertEquals("ovens", e.folder)
        assertEquals(listOf("bread", "starter"), e.tags)
        assertTrue(e.favourite)
        assertEquals(5L, e.addedDay)
        assertEquals(1, l.size)
        assertEquals(e, l.get(e.id))
    }

    @Test fun sameConfigurationIsNotSavedTwice() {
        val l = BlueprintLibrary()
        val first = added(l.add(T.sample("First")))
        val again = l.add(T.sample("Renamed copy")) as AddResult.Duplicate
        assertEquals(first.id, again.existing.id)
        assertEquals(1, l.size)
        assertEquals(2, (added(l.add(T.sample("Forced"), allowDuplicate = true))).id.value)
    }

    @Test fun namesAreMadeUniqueWithinAFolder() {
        val l = BlueprintLibrary()
        added(l.add(variant("Bakery", 1)))
        assertEquals("Bakery (2)", added(l.add(variant("Bakery", 2))).name)
        assertEquals("bakery (3)", added(l.add(variant("bakery", 3))).name)
        assertEquals("Bakery", added(l.add(variant("Bakery", 4), "other")).name)
    }

    @Test fun duplicateKeepsADeliberateCopy() {
        val l = lib(1)
        val src = l.all().first()
        val copy = l.duplicate(src.id, 9).getOrThrow()
        assertEquals("Plan 1 (copy)", copy.name)
        assertEquals(src.id, copy.copyOf)
        assertEquals(src.hash, copy.hash)
        assertFalse(copy.favourite)
        assertEquals(src.id, l.duplicate(copy.id).getOrThrow().copyOf)
        assertEquals(src.id, l.findByHash(src.hash)!!.id)
        assertTrue(l.duplicate(BlueprintId(99)).isRejected)
    }

    @Test fun removingAnOriginalPromotesItsCopy() {
        val l = lib(1)
        val src = l.all().first()
        val c1 = l.duplicate(src.id).getOrThrow()
        val c2 = l.duplicate(src.id).getOrThrow()
        l.remove(src.id).getOrThrow()
        assertNull(l.get(c1.id)!!.copyOf)
        assertEquals(c1.id, l.get(c2.id)!!.copyOf)
        assertTrue(l.add(T.sample("again").copy(spec = src.blueprint.spec)) is AddResult.Duplicate)
    }

    @Test fun uniqueHashAmongOriginalsIsAnInvariant() {
        val l = lib(4)
        l.duplicate(l.all()[1].id); l.duplicate(l.all()[1].id)
        l.add(l.all()[0].blueprint)
        val roots = l.all().filter { it.copyOf == null }
        assertEquals(roots.size, roots.map { it.hash }.distinct().size)
    }

    @Test fun renameRejectsBlankLongAndTaken() {
        val l = lib(2)
        val (a, b) = l.all()
        assertEquals("Fresh", l.rename(a.id, "  Fresh ").getOrThrow().name)
        assertEquals(Rules.INVALID_ARGUMENT, l.rename(a.id, " ").rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, l.rename(a.id, "x".repeat(49)).rejectionOrNull()?.rule)
        assertEquals(Rules.NAME_TAKEN, l.rename(b.id, "fresh").rejectionOrNull()?.rule)
        assertTrue(l.rename(a.id, "FRESH").isOk)
        assertEquals(Rules.UNKNOWN_ENTITY, l.rename(BlueprintId(50), "x").rejectionOrNull()?.rule)
    }

    @Test fun tagsAndFavourites() {
        val l = lib(1)
        val id = l.all().first().id
        assertEquals(listOf("a b", "x"), l.addTag(id, " X ").flatMap { l.addTag(id, "a b") }.getOrThrow().tags)
        assertEquals(listOf("a b", "x"), l.addTag(id, "x").getOrThrow().tags)
        assertEquals(listOf("x"), l.removeTag(id, "A B").getOrThrow().tags)
        assertTrue(l.addTag(id, "bad!").isRejected)
        assertTrue(l.addTag(id, "").isRejected)
        for (i in 1..7) l.addTag(id, "t$i")
        assertEquals(Rules.LIMIT_EXCEEDED, l.addTag(id, "overflow").rejectionOrNull()?.rule)
        assertTrue(l.toggleFavourite(id).getOrThrow().favourite)
        assertFalse(l.toggleFavourite(id).getOrThrow().favourite)
        assertTrue(l.setFavourite(id, true).getOrThrow().favourite)
        assertEquals(l.tags(), l.tags().sorted())
    }

    @Test fun notesAreLimited() {
        val l = lib(1)
        val id = l.all().first().id
        assertEquals("hi", l.setNote(id, " hi ").getOrThrow().blueprint.note)
        assertTrue(l.setNote(id, "n".repeat(241)).isRejected)
    }

    @Test fun foldersAreNormalizedAndAncestorsAppear() {
        val l = BlueprintLibrary()
        added(l.add(variant("A", 1), " food \\ bread/ "))
        assertEquals(listOf("food", "food/bread"), l.folders())
        assertEquals("food/bread", l.all().single().folder)
        assertEquals(AddResult.Refused::class, l.add(variant("B", 2), "bad*name")::class)
        assertEquals(AddResult.Refused::class, l.add(variant("B", 2), "a/b/c/d")::class)
        assertEquals("a/b", BlueprintLibrary.normalizeFolder("//a//b/"))
        assertEquals("", BlueprintLibrary.normalizeFolder("  "))
        assertNull(BlueprintLibrary.normalizeFolder("x".repeat(25)))
    }

    @Test fun createRenameAndDeleteFolders() {
        val l = BlueprintLibrary()
        assertEquals("empty", l.createFolder("empty").getOrThrow())
        assertEquals(Rules.DUPLICATE, l.createFolder("empty").rejectionOrNull()?.rule)
        val e = added(l.add(variant("A", 1), "old/sub"))
        assertEquals(1, l.renameFolder("old", "new").getOrThrow())
        assertEquals("new/sub", l.get(e.id)!!.folder)
        assertEquals(listOf("empty", "new", "new/sub"), l.folders())
        assertEquals(Rules.INVALID_ARGUMENT, l.renameFolder("new", "new/inside").rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, l.renameFolder("missing", "x").rejectionOrNull()?.rule)
        assertEquals(1, l.deleteFolder("new", "empty").getOrThrow())
        assertEquals("empty", l.get(e.id)!!.folder)
        assertEquals(listOf("empty"), l.folders())
        assertEquals(Rules.UNKNOWN_ENTITY, l.deleteFolder("new").rejectionOrNull()?.rule)
    }

    @Test fun movingKeepsNamesUniqueInTheNewFolder() {
        val l = BlueprintLibrary()
        val a = added(l.add(variant("Same", 1), "x"))
        added(l.add(variant("Same", 2), "y"))
        assertEquals("Same (2)", l.move(a.id, "y").getOrThrow().name)
        assertEquals("y", l.get(a.id)!!.folder)
        assertTrue(l.move(a.id, "no/way/too/deep").isRejected)
    }

    @Test fun capacityLimitsApply() {
        val l = BlueprintLibrary(LibraryLimits(maxEntries = 2, maxFolders = 2))
        added(l.add(variant("A", 1)))
        added(l.add(variant("B", 2), "f1"))
        val full = l.add(variant("C", 3)) as AddResult.Refused
        assertEquals(Rules.LIMIT_EXCEEDED, full.rule)
        assertTrue(l.isFull)
        l.remove(l.all().first().id)
        assertEquals(Rules.LIMIT_EXCEEDED, (l.add(variant("D", 4), "f2/f3") as AddResult.Refused).rule)
        assertTrue(l.add(variant("D", 4), "f2") is AddResult.Added)
        assertTrue(l.createFolder("f9").isRejected)
    }

    @Test fun totalSizeIsCapped() {
        val one = BlueprintCodec.encode(variant("A", 1)).size
        val l = BlueprintLibrary(LibraryLimits(maxTotalBytes = one + one / 2))
        added(l.add(variant("A", 1)))
        assertTrue((l.add(variant("B", 2)) as AddResult.Refused).message.contains("space"))
    }

    @Test fun searchMatchesWordsAcrossFields() {
        val l = BlueprintLibrary()
        added(l.add(Blueprint.of(T.sampleSpec(), "Sunrise loaf", "morning rush"), "food", listOf("bread")))
        added(l.add(Blueprint.of(BlueprintSpec(FacilityTypeId("farm"), listOf(T.line(app.many.economytycoon.core.RecipeId("grow_wheat")))), "Wheat acre"), "farm"))
        assertEquals(1, l.find(BlueprintQuery(text = "sunrise")).size)
        assertEquals(1, l.find(BlueprintQuery(text = "MORNING loaf")).size)
        assertEquals(1, l.find(BlueprintQuery(text = "grow_wheat")).size)
        assertEquals(1, l.find(BlueprintQuery(text = "farm")).size)
        assertEquals(0, l.find(BlueprintQuery(text = "loaf wheat")).size)
        assertEquals(2, l.find(BlueprintQuery(text = "  ")).size)
        assertEquals(1, l.find(BlueprintQuery(tags = setOf("Bread"))).size)
        assertEquals(1, l.find(BlueprintQuery(facilityType = T.bakery)).size)
        assertEquals(1, l.find(BlueprintQuery(folder = "food")).size)
        assertEquals(2, l.find(BlueprintQuery(folder = "")).size)
        assertEquals(0, l.find(BlueprintQuery(favouritesOnly = true)).size)
    }

    @Test fun folderSearchIncludesSubfolders() {
        val l = BlueprintLibrary()
        added(l.add(variant("A", 1), "a"))
        added(l.add(variant("B", 2), "a/b"))
        added(l.add(variant("C", 3), "ab"))
        assertEquals(listOf("A", "B"), l.find(BlueprintQuery(folder = "a")).map { it.name })
    }

    @Test fun sortingIsStableAndDeterministic() {
        val l = BlueprintLibrary()
        added(l.add(variant("b", 1), day = 3))
        added(l.add(variant("A", 2), day = 9))
        val c = added(l.add(variant("c", 3), day = 9))
        l.setFavourite(c.id, true)
        assertEquals(listOf("A", "b", "c"), l.find(BlueprintQuery(sort = SortKey.NAME)).map { it.name })
        assertEquals(listOf("c", "A", "b"), l.find(BlueprintQuery(sort = SortKey.NEWEST)).map { it.name })
        assertEquals(listOf("c", "A", "b"), l.find(BlueprintQuery(sort = SortKey.FAVOURITES)).map { it.name })
        assertEquals(l.find(BlueprintQuery(sort = SortKey.LINES)), l.find(BlueprintQuery(sort = SortKey.LINES)))
        assertEquals(3, l.find(BlueprintQuery(sort = SortKey.TYPE)).size)
    }

    @Test fun replaceBumpsRevisionAndGuardsAgainstClashes() {
        val l = lib(2)
        val (a, b) = l.all()
        val updated = l.replace(a.id, variant("Plan 1 v2", 777_000L)).getOrThrow()
        assertEquals(2, updated.revision)
        assertEquals("Plan 1 v2", updated.name)
        assertEquals(Rules.DUPLICATE, l.replace(a.id, b.blueprint).rejectionOrNull()?.rule)
        assertEquals(b.hash, l.get(b.id)!!.hash)
    }

    @Test fun exportAndImportMany() {
        val src = lib(3)
        val text = src.exportAll()
        val dst = BlueprintLibrary()
        val report = dst.import(text, folder = "shared", day = 4, tags = listOf("friend"))
        assertEquals(3, report.imported)
        assertEquals("3 added.", report.summary)
        assertEquals(src.all().map { it.hash }.sorted(), dst.all().map { it.hash }.sorted())
        assertTrue(dst.all().all { it.folder == "shared" && it.tags == listOf("friend") && it.addedDay == 4L })
        val again = dst.import(text)
        assertEquals(3, again.count(ImportStatus.DUPLICATE))
        assertEquals("0 added, 3 already saved.", again.summary)
        assertEquals(3, dst.size)
    }

    @Test fun importReportsEachBlockSeparately() {
        val good = BlueprintText.encode(T.sample())
        val bad = good.dropLast(4)
        val report = BlueprintLibrary().import("$good\n\n$bad\n\nETBP1-B32:12$:0000000")
        assertEquals(listOf(ImportStatus.IMPORTED, ImportStatus.INVALID, ImportStatus.INVALID), report.rows.map { it.status })
        assertTrue(report.rows[1].message.contains("line"))
        assertEquals(ParseErrorCode.BAD_CHARACTER, report.rows[2].error!!.code)
        assertEquals("1 added, 2 not added.", report.summary)
        assertEquals("No blueprint codes found.", BlueprintLibrary().import("nothing here").summary)
    }

    @Test fun importStopsAtCapacityButKeepsReporting() {
        val l = BlueprintLibrary(LibraryLimits(maxEntries = 1))
        val text = BlueprintLibrary().also { it.add(variant("A", 1)); it.add(variant("B", 2)) }.exportAll()
        val r = l.import(text)
        assertEquals(listOf(ImportStatus.IMPORTED, ImportStatus.REFUSED), r.rows.map { it.status })
    }

    @Test fun importMigrationNotesAreKept() {
        val w = app.many.economytycoon.core.ByteWriter()
        w.writeVarInt(1); w.writeString("Legacy"); w.writeString(""); w.writeString(""); w.writeVarLong(0)
        w.writeString("bakery"); w.writeVarInt(0); w.writeVarLong(20)
        repeat(4) { w.writeVarInt(0) }
        val b = w.toByteArray()
        val r = BlueprintLibrary().import("ETBP1-B32:${BlueprintText.base32(b)}:${BlueprintText.checksum(b)}")
        assertEquals(ImportStatus.IMPORTED, r.rows.single().status)
        assertTrue(r.rows.single().notes.isNotEmpty())
    }

    @Test fun exportSkipsUnknownIdsAndOrdersById() {
        val l = lib(3)
        val ids = l.all().map { it.id }
        val text = l.export(listOf(ids[2], BlueprintId(99), ids[0], ids[0]))
        val names = BlueprintText.parseAll(text).map { (it as ParseResult.Success).blueprint.name }
        assertEquals(listOf("Plan 1", "Plan 3"), names)
    }

    @Test fun stateHashTracksEveryChange() {
        val l = lib(2)
        val h0 = l.stateHash()
        assertEquals(h0, l.stateHash())
        val id = l.all().first().id
        val seen = mutableSetOf(h0)
        for (change in listOf<() -> Unit>({ l.toggleFavourite(id) }, { l.addTag(id, "t") }, { l.rename(id, "New") }, { l.createFolder("f") }, { l.move(id, "f") })) {
            change()
            assertTrue(seen.add(l.stateHash()))
        }
    }

    @Test fun twoLibrariesWithTheSameHistoryHashAlike() {
        fun build() = lib(3).also { it.toggleFavourite(BlueprintId(2)); it.duplicate(BlueprintId(1), 7); it.createFolder("z") }
        assertEquals(build().stateHash(), build().stateHash())
    }
}
