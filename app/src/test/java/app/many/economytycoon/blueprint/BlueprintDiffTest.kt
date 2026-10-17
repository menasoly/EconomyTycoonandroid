package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.production.SchedulePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintDiffTest {
    private val differ = BlueprintDiffer(T.content)
    private val a = T.sampleSpec()

    @Test fun equalSpecsHaveNoDifferences() {
        val d = differ.diff(a, a)
        assertTrue(d.isEmpty)
        assertEquals("No differences.", d.summary)
    }

    @Test fun recipeSwapIsOneChange() {
        val b = a.copy(lines = a.lines.map { if (it.recipe == T.makeBread) it.copy(recipe = T.makeBreadPro) else it })
        val d = differ.diff(a, b)
        assertEquals(1, d.entries.size)
        assertEquals("line 2 recipe: Bake bread -> Bake bread (pro)", d.entries.single().text)
        assertEquals("1 difference: 1 changed", d.summary)
    }

    @Test fun machinesAddedAndRemoved() {
        val b = a.copy(lines = a.lines.map { if (it.recipe == T.makeBread) it.copy(machines = listOf(T.proOven)) else it })
        val d = differ.diff(a, b)
        assertEquals(1, d.count(DiffKind.ADDED))
        assertEquals(1, d.count(DiffKind.REMOVED))
        assertEquals(listOf("Machines"), d.sections)
        assertTrue(d.entries.any { it.text == "line 2 machine: was Oven" })
    }

    @Test fun lineCountDifferences() {
        val b = a.copy(lines = a.lines.take(2))
        val d = differ.diff(a, b)
        assertEquals(DiffKind.REMOVED, d.entries.single().kind)
        assertEquals(DiffKind.ADDED, differ.diff(b, a).entries.single().kind)
    }

    @Test fun scalarSettings() {
        val b = a.copy(policy = SchedulePolicy.PRIORITY, qualityPpm = 500_000L, storageExpansions = 3)
        val d = differ.diff(a, b)
        assertEquals(setOf("Schedule", "Storage"), d.sections.toSet())
        assertTrue(d.entries.any { it.text == "quality assurance: 30% -> 50%" })
        assertTrue(d.entries.any { it.text == "expansions: 1 -> 3" })
    }

    @Test fun staffAndRules() {
        val b = a.copy(
            staff = a.staff.map { if (it.role == T.baker) it.copy(target = 8) else it }.filter { it.role != T.clerk },
            sells = emptyList(),
            buys = a.buys + a.buys[0].copy(good = GoodId("flour"), target = Quantity.units(5)),
        )
        val d = differ.diff(a, b)
        assertTrue(d.entries.any { it.section == "Staffing" && it.kind == DiffKind.CHANGED && "6" in it.text && "8" in it.text })
        assertTrue(d.entries.any { it.section == "Staffing" && it.kind == DiffKind.REMOVED })
        assertTrue(d.entries.any { it.section == "Selling" && it.kind == DiffKind.REMOVED })
        assertTrue(d.entries.any { it.section == "Buying" && it.kind == DiffKind.ADDED && it.path == "buy Flour" })
    }

    @Test fun diffIsSymmetricInSizeAndNormalizesFirst() {
        val b = a.copy(qualityPpm = 0L, lines = a.lines.map { it.copy(priority = it.priority * 5) })
        assertEquals(differ.diff(a, b).entries.size, differ.diff(b, a).entries.size)
        assertTrue(BlueprintDiffer(null).diff(a, a.copy(lines = a.lines.map { it.copy(priority = it.priority * 5) })).isEmpty)
    }

    @Test fun withoutContentIdsAreShown() {
        val b = a.copy(lines = a.lines.map { if (it.recipe == T.makeBread) it.copy(recipe = T.makeBreadPro) else it })
        assertEquals("line 2 recipe: make_bread -> make_bread_pro", BlueprintDiffer(null).diff(a, b).entries.single().text)
    }

    @Test fun facilityTypeChangeIsReported() {
        val d = differ.diff(a, a.copy(facilityType = app.many.economytycoon.core.FacilityTypeId("farm")))
        assertEquals("type: Bakery -> Farm", d.inSection("Facility").single().text)
    }
}
