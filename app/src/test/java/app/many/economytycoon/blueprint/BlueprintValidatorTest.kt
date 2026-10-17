package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Point
import app.many.economytycoon.world.Region
import app.many.economytycoon.world.Terrain
import app.many.economytycoon.core.CountryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintValidatorTest {
    private val v = BlueprintValidator(T.content)
    private val all = ResearchView.ALL

    private fun region(fertility: Long = 800_000L, coastal: Boolean = false) =
        Region(RegionId(1), CountryId(1), "Vale", Point(1, 1), Terrain.PLAINS, ClimateZone.TEMPERATE, 100, fertility, coastal)

    @Test fun sampleIsCleanWhenEverythingIsResearched() {
        val r = v.validate(T.sampleSpec(), all)
        assertTrue(r.isClean)
        assertEquals(0, r.count(IssueSeverity.ERROR))
    }

    @Test fun unknownFacilityStopsEarly() {
        val r = v.validate(BlueprintSpec(FacilityTypeId("gone"), emptyList()))
        assertEquals(listOf(IssueCodes.UNKNOWN_FACILITY), r.issues.map { it.code })
    }

    @Test fun removedRecipeMachineRoleAndGoodAreErrors() {
        val s = T.sampleSpec()
        val spec = s.copy(
            lines = s.lines + T.line(RecipeId("old_recipe"), listOf(MachineTypeId("old_machine"))),
            staff = s.staff + StaffSpec(RoleId("old_role"), 2),
            sells = s.sells + s.sells[0].copy(good = GoodId("old_good")),
            buys = s.buys + s.buys[0].copy(good = GoodId("old_good2")),
        ).normalized()
        val r = v.validate(spec, all)
        assertTrue(r.has(IssueCodes.UNKNOWN_RECIPE))
        assertTrue(r.has(IssueCodes.UNKNOWN_MACHINE))
        assertTrue(r.has(IssueCodes.UNKNOWN_ROLE))
        assertEquals(2, r.issues.count { it.code == IssueCodes.UNKNOWN_GOOD })
        assertTrue(r.has(IssueCodes.TOO_MANY_LINES))
        assertFalse(r.isClean)
    }

    @Test fun unresearchedTechIsAnErrorWithTheChain() {
        val r = v.validate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBreadPro, listOf(T.proOven)))), T.known())
        val tech = r.errors.filter { it.code == IssueCodes.TECH_MISSING }
        assertEquals(2, tech.size)
        assertTrue(tech.all { "Mixing > Automation" in it.message })
        assertTrue(tech.map { it.path }.containsAll(listOf("lines[0].recipe", "lines[0].machines[0]")))
    }

    @Test fun partlyResearchedChainShowsOnlyWhatIsLeft() {
        val r = v.validate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBreadPro, listOf(T.proOven)))), T.known("mixing"))
        assertTrue(r.errors.all { it.message.endsWith("Automation.") })
        assertEquals(emptyList<TechId>(), v.missingTechChain(TechId("automation"), T.known("mixing", "automation")))
        assertEquals(listOf(TechId("mixing"), TechId("automation")), v.missingTechChain(TechId("automation"), T.known()))
    }

    @Test fun facilityTechIsChecked() {
        val r = v.validate(BlueprintSpec(FacilityTypeId("lab_bakery"), emptyList()), T.known())
        assertTrue(r.has(IssueCodes.TECH_MISSING))
    }

    @Test fun recipeOfAnotherFacilityIsRejected() {
        val r = v.validate(BlueprintSpec(T.bakery, listOf(T.line(RecipeId("dig_ore")))), all)
        assertTrue(r.has(IssueCodes.RECIPE_WRONG_FACILITY))
    }

    @Test fun recipeWithoutItsMachineWarns() {
        val r = v.validate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread))), all)
        assertTrue(r.isClean)
        assertTrue(r.has(IssueCodes.MACHINE_MISSING_FOR_RECIPE))
    }

    @Test fun tooManyMachinesOnALine() {
        val ms = listOf("a", "b", "c", "d", "e").map { MachineTypeId(it) }
        val r = v.validate(BlueprintSpec(T.bakery, listOf(LineSpec(0, true, null, ms))), all)
        assertTrue(r.has(IssueCodes.TOO_MANY_MACHINES))
    }

    @Test fun staffShortfallAndUnusedRoles() {
        val spec = BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour)), staff = listOf(StaffSpec(T.baker, 1), StaffSpec(RoleId("miner"), 3)))
        val r = v.validate(spec, all)
        assertTrue(r.has(IssueCodes.STAFF_SHORT))
        assertTrue(r.has(IssueCodes.STAFF_UNUSED))
        // Without any staffing targets nothing is claimed about staffing.
        assertFalse(v.validate(spec.copy(staff = emptyList()), all).has(IssueCodes.STAFF_SHORT))
    }

    @Test fun overheadStaffCountsTowardsNeed() {
        val spec = BlueprintSpec(T.bakery, listOf(T.line(null)), staff = listOf(StaffSpec(T.baker, 0)))
        val r = v.validate(spec, all)
        assertTrue(r.warnings.any { it.code == IssueCodes.STAFF_SHORT && "Clerk" in it.message })
    }

    @Test fun tradingRuleWarnings() {
        val s = T.sampleSpec()
        val r = v.validate(s.copy(sells = listOf(s.sells[0].copy(good = GoodId("ore"))), buys = listOf(s.buys[0].copy(good = GoodId("ore")))), all)
        assertTrue(r.has(IssueCodes.SELL_NOT_PRODUCED))
        assertTrue(r.has(IssueCodes.BUY_NOT_USED))
        assertTrue(v.validate(s.copy(sells = listOf(s.sells[0].copy(good = GoodId("ash")))), all).has(IssueCodes.NOT_STORABLE))
    }

    @Test fun idleBlueprintsAreFlagged() {
        assertTrue(v.validate(BlueprintSpec(T.bakery, listOf(T.line(null))), all).has(IssueCodes.NO_RECIPES))
        val off = v.validate(BlueprintSpec(T.bakery, listOf(T.line(T.makeFlour, enabled = false))), all)
        assertTrue(off.has(IssueCodes.ALL_DISABLED))
    }

    @Test fun siteRules() {
        val mine = BlueprintSpec(FacilityTypeId("mine"), listOf(T.line(RecipeId("dig_ore"))))
        assertTrue(v.validate(mine, all, SiteInfo(region())).has(IssueCodes.DEPOSIT_REQUIRED))
        assertFalse(v.validate(mine, all, SiteInfo(region(), setOf(GoodId("ore")))).has(IssueCodes.DEPOSIT_REQUIRED))
        val farm = BlueprintSpec(FacilityTypeId("farm"), listOf(T.line(RecipeId("grow_wheat"))))
        assertTrue(v.validate(farm, all, SiteInfo(region(fertility = 100_000L))).has(IssueCodes.POOR_SOIL))
        assertFalse(v.validate(farm, all, SiteInfo(region())).has(IssueCodes.POOR_SOIL))
        val port = BlueprintSpec(FacilityTypeId("harbor"), emptyList())
        assertTrue(v.validate(port, all, SiteInfo(region())).has(IssueCodes.NOT_COASTAL))
        assertFalse(v.validate(port, all, SiteInfo(region(coastal = true))).has(IssueCodes.NOT_COASTAL))
    }

    @Test fun missingSiteIsOnlyAnInfo() {
        val r = v.validate(T.sampleSpec(), all, null)
        assertEquals(IssueSeverity.INFO, r.issues.first { it.code == IssueCodes.SITE_UNCHECKED }.severity)
        assertTrue(r.isClean)
    }

    @Test fun issuesAreOrderedBySeverityThenPath() {
        val r = v.validate(BlueprintSpec(T.bakery, listOf(T.line(T.makeBread), T.line(RecipeId("nope")))), all, null)
        assertEquals(r.issues.sortedBy { it.severity.ordinal }.map { it.severity }, r.issues.map { it.severity })
        assertEquals(IssueSeverity.ERROR, r.issues.first().severity)
    }

    @Test fun validationIsDeterministic() {
        val spec = T.sampleSpec().copy(sells = emptyList())
        assertEquals(v.validate(spec, T.known("mixing")), v.validate(spec, T.known("mixing")))
    }
}
