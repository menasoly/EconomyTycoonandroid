package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport.bakery
import app.many.economytycoon.blueprint.BlueprintTestSupport.line
import app.many.economytycoon.blueprint.BlueprintTestSupport.makeBread
import app.many.economytycoon.blueprint.BlueprintTestSupport.makeFlour
import app.many.economytycoon.blueprint.BlueprintTestSupport.oven
import app.many.economytycoon.blueprint.BlueprintTestSupport.mixer
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BlueprintModelTest {
    private fun rejects(block: () -> Unit) {
        try { block(); fail("expected IllegalArgumentException") } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test fun normalizationRenumbersPrioritiesAndSortsMachines() {
        val spec = BlueprintSpec(bakery, listOf(line(makeBread, listOf(oven, mixer), priority = 9), line(makeFlour, priority = 3))).normalized()
        assertEquals(listOf(makeFlour, makeBread), spec.lines.map { it.recipe })
        assertEquals(listOf(0, 1), spec.lines.map { it.priority })
        assertEquals(listOf(mixer, oven), spec.lines[1].machines)
    }

    @Test fun equalSetupsHashAlikeRegardlessOfOrderOrPriorityNumbers() {
        val a = BlueprintSpec(bakery, listOf(line(makeFlour, priority = 0), line(makeBread, listOf(oven, mixer), priority = 1)))
        val b = BlueprintSpec(bakery, listOf(line(makeBread, listOf(mixer, oven), priority = 40), line(makeFlour, priority = 5)).reversed().sortedBy { it.priority })
        assertEquals(a.canonicalHash(), b.canonicalHash())
    }

    @Test fun differentSetupsHashDifferently() {
        val a = BlueprintTestSupport.sampleSpec()
        assertNotEquals(a.canonicalHash(), a.copy(qualityPpm = 400_000L).canonicalHash())
        assertNotEquals(a.canonicalHash(), a.copy(storageExpansions = 2).canonicalHash())
        assertNotEquals(a.canonicalHash(), a.copy(lines = a.lines.map { it.copy(priority = 2 - it.priority) }).canonicalHash())
        assertNotEquals(a.canonicalHash(), a.copy(staff = a.staff.map { it.copy(wage = null) }).canonicalHash())
    }

    @Test fun hashIsStableAcrossRuns() {
        // A golden value: changing the hash layout invalidates every shared blueprint and must bump the format.
        val h = BlueprintTestSupport.sampleSpec().canonicalHash()
        assertEquals("7dd5aa26a6154880ec737482568dc071ea20e03b912f2573e96172a8e94e2e32", h)
        assertEquals(h, BlueprintTestSupport.sampleSpec().canonicalHash())
    }

    @Test fun nameAndNoteDoNotAffectTheHash() {
        val a = BlueprintTestSupport.sample("One")
        val b = a.renamed("Two").withNote("other")
        assertEquals(a.hash, b.hash)
        assertEquals(a.hash.substring(0, 8), a.shortHash)
    }

    @Test fun duplicateStaffAndRulesCollapse() {
        val s = BlueprintTestSupport.sampleSpec()
        val doubled = s.copy(staff = s.staff + s.staff, sells = s.sells + s.sells).normalized()
        assertEquals(s.staff.size, doubled.staff.size)
        assertEquals(1, doubled.sells.size)
    }

    @Test fun structuralLimitsAreEnforced() {
        rejects { BlueprintSpec(bakery, List(BlueprintLimits.MAX_LINES + 1) { line(null) }) }
        rejects { BlueprintSpec(bakery, emptyList(), qualityPpm = 1_000_001L) }
        rejects { BlueprintSpec(bakery, emptyList(), storageExpansions = 9) }
        rejects { StaffSpec(BlueprintTestSupport.baker, -1) }
        rejects { StaffSpec(BlueprintTestSupport.baker, 3, Money.ZERO) }
        rejects { StaffSpec(BlueprintTestSupport.baker, 3, null, 600_000L) }
        rejects { LineSpec(0, true, null, List(9) { oven }) }
        rejects { BuySpec(BlueprintTestSupport.wheat, Quantity.units(1), 50L, true) }
        rejects { SellSpec(BlueprintTestSupport.bread, app.many.economytycoon.companies.PriceStrategy.MARKET, 9_000_000L, Quantity.ZERO, null, 0L, true) }
    }

    @Test fun blueprintNeedsReasonableName() {
        rejects { Blueprint.of(BlueprintTestSupport.sampleSpec(), "   ") }
        rejects { Blueprint.of(BlueprintTestSupport.sampleSpec(), "x".repeat(BlueprintLimits.MAX_NAME + 1)) }
        rejects { Blueprint.of(BlueprintTestSupport.sampleSpec(), "ok", "n".repeat(BlueprintLimits.MAX_NOTE + 1)) }
        assertEquals("Trim me", Blueprint.of(BlueprintTestSupport.sampleSpec(), "  Trim me  ").name)
    }

    @Test fun rulesRoundTripThroughSpecs() {
        val spec = BlueprintTestSupport.sampleSpec()
        val facility = app.many.economytycoon.core.FacilityId(5)
        assertEquals(spec.sells[0], SellSpec.of(spec.sells[0].toRule(facility)))
        assertEquals(spec.buys[0], BuySpec.of(spec.buys[0].toRule(facility)))
        assertTrue(spec.machineCount == 3)
        assertEquals(3, spec.recipes.size)
    }
}
