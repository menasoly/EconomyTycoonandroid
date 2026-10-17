package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.content.ContentVersion
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintCompatTest {
    private val checker = CompatibilityChecker(T.content)

    @Test fun currentBlueprintIsCompatibleAndUnchanged() {
        val bp = T.sample()
        val r = checker.check(bp)
        assertEquals(Compatibility.COMPATIBLE, r.status)
        assertSame(bp, r.migrated)
        assertTrue(r.notes.isEmpty())
    }

    @Test fun olderMinorVersionIsOnlyANote() {
        val bp = T.sample().copy(contentTag = "test@1.1")
        val r = checker.check(bp)
        assertEquals(Compatibility.MIGRATABLE, r.status)
        assertEquals("Made for test 1.1; the game now has 1.4.", r.notes.single().text)
        assertEquals("test@1.4", r.migrated!!.contentTag)
        assertEquals(bp.spec, r.migrated!!.spec)
    }

    @Test fun newerVersionAndUnknownPackAreExplained() {
        val r = checker.check(T.sample().copy(contentTag = "test@2.0,extra@1.0"))
        assertEquals(2, r.notes(NoteKind.VERSION).size)
        assertTrue(r.notes[0].text.contains("newer"))
        assertTrue(r.notes[1].text.contains("not loaded"))
    }

    @Test fun renamesAreFollowedAndExplained() {
        val old = T.sampleSpec().copy(
            lines = T.sampleSpec().lines.map { if (it.recipe == T.makeBread) it.copy(recipe = RecipeId("bake_loaf"), machines = listOf(MachineTypeId("old_oven"))) else it },
            staff = listOf(StaffSpec(RoleId("old_baker"), 4)),
            sells = listOf(T.sampleSpec().sells[0].copy(good = GoodId("loaf"))),
        )
        val table = RenameTable(
            recipes = mapOf("bake_loaf" to "make_bread"), machines = mapOf("old_oven" to "oven"),
            roles = mapOf("old_baker" to "baker"), goods = mapOf("loaf" to "bread"),
        )
        val r = CompatibilityChecker(T.content, table).check(Blueprint.of(old, "Old"))
        assertEquals(Compatibility.MIGRATABLE, r.status)
        assertEquals(4, r.notes(NoteKind.RENAMED).size)
        val spec = r.migrated!!.spec
        assertEquals(T.makeBread, spec.lines.first { it.machines.contains(T.oven) }.recipe)
        assertEquals(T.baker, spec.staff.single().role)
        assertEquals(T.bread, spec.sells.single().good)
        assertTrue(r.notes.any { it.text == "The recipe 'bake_loaf' is now called 'make_bread'." })
    }

    @Test fun chainedRenamesResolve() {
        val table = RenameTable(recipes = mapOf("a" to "b", "b" to "make_bread"))
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(RecipeId("a"), listOf(T.oven)))), "Chain")
        val r = CompatibilityChecker(T.content, table).check(bp)
        assertEquals(T.makeBread, r.migrated!!.spec.lines[0].recipe)
    }

    @Test fun renameLoopsDoNotHang() {
        val table = RenameTable(recipes = mapOf("a" to "b", "b" to "a"))
        val bp = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(RecipeId("a")))), "Loop")
        assertEquals(Compatibility.PARTIAL, CompatibilityChecker(T.content, table).check(bp).status)
    }

    @Test fun removedPartsAreDroppedWithNotes() {
        val s = T.sampleSpec()
        val spec = s.copy(
            lines = s.lines.map { if (it.recipe == T.makeBread) it.copy(recipe = RecipeId("gone"), machines = listOf(MachineTypeId("gone_m"))) else it },
            staff = s.staff + StaffSpec(RoleId("gone_r"), 1),
            sells = s.sells + s.sells[0].copy(good = GoodId("gone_g")),
            buys = s.buys + s.buys[0].copy(good = GoodId("gone_g2")),
        )
        val r = checker.check(Blueprint.of(spec, "Broken-ish"))
        assertEquals(Compatibility.PARTIAL, r.status)
        assertEquals(5, r.notes(NoteKind.REMOVED).size)
        val m = r.migrated!!.spec
        assertNull(m.lines[1].recipe)
        assertTrue(m.lines[1].machines.isEmpty())
        assertEquals(1, m.sells.size)
        assertTrue(BlueprintValidator(T.content).validate(m).isClean)
        assertTrue(r.notes.first { "recipe" in it.text }.text.startsWith("Line 2"))
    }

    @Test fun missingFacilityTypeIsBroken() {
        val r = checker.check(Blueprint.of(BlueprintSpec(FacilityTypeId("gone"), emptyList()), "Gone"))
        assertEquals(Compatibility.BROKEN, r.status)
        assertNull(r.migrated)
        assertTrue(!r.isUsable)
    }

    @Test fun renamedFacilityTypeIsFollowed() {
        val r = CompatibilityChecker(T.content, RenameTable(facilities = mapOf("old_bakery" to "bakery")))
            .check(Blueprint.of(BlueprintSpec(FacilityTypeId("old_bakery"), listOf(T.line(null))), "Old"))
        assertEquals(T.bakery, r.migrated!!.spec.facilityType)
        assertEquals(Compatibility.MIGRATABLE, r.status)
    }

    @Test fun tagsParseAndFormat() {
        assertEquals(listOf(ContentVersion("a", 1, 2), ContentVersion("b", 3, 0)), CompatibilityChecker.parseTag("a@1.2, b@3.0"))
        assertEquals(emptyList<ContentVersion>(), CompatibilityChecker.parseTag("garbage,@1.0,x@y.z"))
        assertEquals("test@1.4", CompatibilityChecker.tagOf(T.content))
        assertEquals("test@1.4", checker.currentTag())
    }

    @Test fun migrationIsIdempotent() {
        val bp = T.sample().copy(contentTag = "test@1.0")
        val once = checker.check(bp).migrated!!
        val twice = checker.check(once)
        assertEquals(Compatibility.COMPATIBLE, twice.status)
        assertEquals(once, twice.migrated)
    }
}
