package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintVariantsTest {
    private val variants = BlueprintVariants(T.content)
    private val bp = T.sample()

    @Test fun mirrorReversesLineOrder() {
        val m = variants.mirror(bp).getOrThrow()
        assertEquals(bp.spec.lines.map { it.recipe }.reversed(), m.spec.lines.map { it.recipe })
        assertEquals(listOf(0, 1, 2), m.spec.lines.map { it.priority })
        assertEquals("Corner bakery (mirrored)", m.name)
        assertNotEquals(bp.hash, m.hash)
    }

    @Test fun mirrorTwiceIsTheIdentityOnTheSpec() {
        val twice = variants.mirror(variants.mirror(bp).getOrThrow()).getOrThrow()
        assertEquals(bp.spec, twice.spec)
        assertEquals(bp.hash, twice.hash)
        assertEquals("Corner bakery (mirrored)", twice.name)
    }

    @Test fun mirrorNeedsTwoLines() {
        val single = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour))), "One")
        assertEquals(Rules.INVALID_ARGUMENT, variants.mirror(single).rejectionOrNull()?.rule)
    }

    @Test fun alternativesShareOutputsAndSortByProfit() {
        val alts = variants.alternatives(T.makeBread).map { it.id }
        assertTrue(T.makeBreadPro in alts && T.makeSourdough in alts)
        assertFalse(T.makeBread in alts)
        assertFalse(T.makeFlour in alts)
        assertEquals(emptyList<Any>(), variants.alternatives(RecipeId("unknown")))
    }

    @Test fun swapRecipesMovesMachinesWithTheRecipe() {
        val swapped = variants.swapRecipes(bp, mapOf(T.makeBread to T.makeBreadPro)).getOrThrow()
        val line = swapped.spec.lines[1]
        assertEquals(T.makeBreadPro, line.recipe)
        assertEquals(listOf(T.proOven), line.machines)
        assertEquals(bp.spec.lines[0], swapped.spec.lines[0])
        assertTrue(BlueprintValidator(T.content).validate(swapped.spec, ResearchView.ALL).errors.isEmpty())
    }

    @Test fun swapRejectsBadMappings() {
        assertTrue(variants.swapRecipes(bp, emptyMap()).isRejected)
        assertTrue(variants.swapRecipes(bp, mapOf(T.makeBread to RecipeId("nope"))).isRejected)
        assertTrue(variants.swapRecipes(bp, mapOf(T.makeBread to RecipeId("dig_ore"))).isRejected)
        assertTrue(variants.swapRecipes(bp, mapOf(RecipeId("grow_wheat") to T.makeBreadPro)).isRejected)
        assertTrue(variants.swapRecipes(bp, mapOf(T.makeBread to T.makeBread)).isRejected)
    }

    @Test fun upgradeTierPicksFasterMachineRecipeWhenResearched() {
        val spec = BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven))))
        val src = Blueprint.of(spec, "Plain")
        val up = variants.upgradeTier(src).getOrThrow()
        assertEquals(T.makeBreadPro, up.spec.lines[0].recipe)
        assertEquals(listOf(T.proOven), up.spec.lines[0].machines)
        assertEquals("Plain (upgraded)", up.name)
    }

    @Test fun upgradeTierRespectsResearch() {
        val src = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread, listOf(T.oven)))), "Plain")
        val out = variants.upgradeTier(src, T.known("mixing"))
        assertTrue(out.isRejected)
        assertTrue(variants.upgradeTier(src, T.known("mixing", "automation")).isOk)
    }

    @Test fun upgradeTierLeavesLinesWithoutBetterRecipes() {
        val src = Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour), T.line(T.makeBread, listOf(T.oven)))), "Mixed")
        val up = variants.upgradeTier(src).getOrThrow()
        assertEquals(src.spec.lines[0], up.spec.lines[0])
        assertEquals(T.makeBreadPro, up.spec.lines[1].recipe)
    }

    @Test fun scaleUpCopiesTheBestLineAndScalesVolumes() {
        val two = Blueprint.of(
            BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour), T.line(T.makeBread, listOf(T.oven))), staff = listOf(StaffSpec(T.baker, 3)),
                buys = listOf(BuySpec(T.wheat, Quantity.units(10), 1_100_000L, true))),
            "Two",
        )
        val three = variants.scaleLines(two, 3).getOrThrow()
        assertEquals(3, three.spec.lines.size)
        assertEquals(two.spec.lines[1].recipe, three.spec.lines[2].recipe)
        assertEquals(listOf(0, 1, 2), three.spec.lines.map { it.priority })
        assertEquals(5L, three.spec.staff.single().target)
        assertEquals(Quantity(15_000L), three.spec.buys.single().target)
    }

    @Test fun scaleDownDropsTrailingLines() {
        val one = variants.scaleLines(bp, 1).getOrThrow()
        assertEquals(listOf(T.makeFlour), one.spec.lines.map { it.recipe })
        assertEquals(2L, one.spec.staff.first { it.role == T.baker }.target)
    }

    @Test fun scaleRespectsContentLimits() {
        assertEquals(Rules.LIMIT_EXCEEDED, variants.scaleLines(bp, 4).rejectionOrNull()?.rule)
        assertEquals(Rules.LIMIT_EXCEEDED, variants.scaleLines(bp, 0).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, variants.scaleLines(bp, 3).rejectionOrNull()?.rule)
        val gone = Blueprint.of(BlueprintSpec(FacilityTypeId("gone"), listOf(T.line(null))), "Gone")
        assertEquals(Rules.UNKNOWN_ENTITY, variants.scaleLines(gone, 2).rejectionOrNull()?.rule)
    }

    @Test fun scaleStaffRoundsUpAndChecksRange() {
        val up = variants.scaleStaff(bp, 1_500_000L).getOrThrow()
        assertEquals(9L, up.spec.staff.first { it.role == T.baker }.target)
        assertEquals(2L, up.spec.staff.first { it.role == T.clerk }.target)
        assertTrue(variants.scaleStaff(bp, 50_000L).isRejected)
        assertTrue(variants.scaleStaff(bp, 1_000_000L).isRejected)
        assertTrue(variants.scaleStaff(Blueprint.of(BlueprintSpec(T.bakery, listOf(T.line(null))), "None"), 2_000_000L).isRejected)
    }

    @Test fun variantNamesNeverExceedTheLimit() {
        val long = bp.copy(name = "n".repeat(BlueprintLimits.MAX_NAME))
        val m = variants.mirror(long).getOrThrow()
        assertEquals(BlueprintLimits.MAX_NAME, m.name.length)
        assertTrue(m.name.endsWith(" (mirrored)"))
    }

    @Test fun variantsKeepMetadataAndLeaveSourceAlone() {
        val before = bp.hash
        val m = variants.mirror(bp).getOrThrow()
        assertEquals(bp.contentTag, m.contentTag)
        assertEquals(bp.createdDay, m.createdDay)
        assertEquals(bp.note, m.note)
        assertEquals(before, bp.hash)
    }
}
