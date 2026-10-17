package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checks every expansion pack must pass. Each pack has a subclass that names it and adds what is particular
 * to that industry, so a failure points at one pack.
 */
abstract class ExpansionPackContract(private val packId: String) {
    protected val pack: ContentPack get() = ExpansionFixture.pack(packId)
    protected val repo: ContentRepository get() = ExpansionFixture.repo

    @Test fun packCarriesItsOwnIdAndVersionOne() {
        assertEquals(packId, pack.version.packId)
        assertEquals(1, pack.version.major)
        assertEquals(0, pack.version.minor)
    }

    @Test fun packHasEveryKindOfContent() {
        assertTrue(pack.industries.size >= 3)
        assertTrue(pack.goods.size >= 20)
        assertTrue(pack.recipes.size >= 20)
        assertTrue(pack.facilityTypes.size >= 6)
        assertTrue(pack.machineTypes.size >= 3)
        assertTrue(pack.technologies.size >= 6)
        assertTrue(pack.demandProfiles.size >= 6)
    }

    @Test fun everyGoodHasAPositivePriceAndPhysicalSize() {
        for (g in pack.goods) {
            assertTrue("${g.id.key} price", g.referencePrice.perUnit.raw > 0L)
            assertTrue("${g.id.key} weight", g.weight > 0L)
            assertTrue("${g.id.key} volume", g.volume > 0L)
        }
    }

    @Test fun everyGoodIsMadeByARecipe() {
        val unmade = pack.goods.filter { ExpansionFixture.flows.producersOf(it.id).isEmpty() }.map { it.id.key }
        assertTrue("goods nothing makes: $unmade", unmade.isEmpty())
    }

    @Test fun everyGoodIsUsedOrWanted() {
        val idle = pack.goods.filter { ExpansionFixture.flows.consumersOf(it.id).isEmpty() && repo.demandProfile(it.id) == null }.map { it.id.key }
        assertTrue("goods nobody uses or buys: $idle", idle.isEmpty())
    }

    @Test fun everyConsumerGoodHasADemandProfile() {
        val missing = pack.goods.filter { it.category == GoodCategory.CONSUMER && repo.demandProfile(it.id) == null }.map { it.id.key }
        assertTrue("consumer goods without demand: $missing", missing.isEmpty())
    }

    @Test fun everyDemandProfileBuysSomethingAndNamesAGoodOfTheRepository() {
        for (d in pack.demandProfiles) {
            assertNotNull(d.good.key, repo.goodOrNull(d.good))
            assertTrue("${d.good.key} bands", d.perCapitaMilli.isNotEmpty())
            assertTrue("${d.good.key} per capita", d.perCapitaMilli.values.all { it >= 0L } && d.perCapitaMilli.values.any { it >= 1L })
            assertTrue("${d.good.key} elasticity", d.elasticityPpm > 0L)
        }
    }

    @Test fun everyIndustryOwnsRecipes() {
        for (i in pack.industries) assertTrue("${i.id.key} has no recipes", repo.allRecipes.any { it.industry == i.id })
    }

    @Test fun everyRecipeRunsInAFacilityOfTheRepository() {
        for (r in pack.recipes) assertNotNull("${r.id.key} facility", repo.facilityTypeOrNull(r.facilityType))
    }

    @Test fun everyFacilityRunsAtLeastOneRecipeOrExtractsADeposit() {
        for (f in pack.facilityTypes) {
            val runs = repo.recipesForFacility(f.id).isNotEmpty()
            assertTrue("${f.id.key} does nothing", runs || f.extracts != null)
        }
    }

    @Test fun everyMachineIsUsedByARecipeOfThePack() {
        val used = pack.recipes.flatMap { it.machines }.toSet()
        for (m in pack.machineTypes) assertTrue("${m.id.key} unused", m.id in used)
    }

    @Test fun everyMachineIsUnlockedByATechnologyOfThePack() {
        for (m in pack.machineTypes) {
            assertNotNull("${m.id.key} needs a tech", m.requiredTech)
            assertTrue("${m.id.key} not unlocked", pack.technologies.any { m.id in it.unlocksMachines })
        }
    }

    @Test fun everyRecipeNeedingATechnologyIsUnlockedByIt() {
        for (r in pack.recipes) {
            val t = r.requiredTech ?: continue
            assertTrue("${r.id.key} is not unlocked by ${t.key}", r.id in repo.technology(t).unlocksRecipes)
        }
    }

    @Test fun everyRecipeMarginIsPositiveAndPlausible() {
        for (e in ExpansionFixture.economicsOf(pack)) {
            assertTrue("${e.recipe.id.key} margin ${e.marginPpm}", e.marginPpm in 100_000L..450_000L)
        }
    }

    @Test fun everyRecipeCoversItsOwnInputs() {
        for (e in ExpansionFixture.economicsOf(pack)) assertTrue(e.recipe.id.key, e.outputValue > e.inputCost)
    }

    @Test fun everyFacilityPaysBackInAPlausibleTime() {
        val paybacks = ExpansionFixture.paybacksOf(pack)
        assertTrue(paybacks.isNotEmpty())
        for (p in paybacks) {
            val days = p.paybackDays
            assertNotNull("${p.facility.id.key} never pays back", days)
            if (days != null) assertTrue("${p.facility.id.key} pays back in $days days", days in 300L..1_600L)
        }
    }

    @Test fun theAuditFindsNothingToReportAboutThePack() {
        val findings = ExpansionFixture.findingsOf(pack)
        assertTrue("findings: $findings", findings.isEmpty())
    }

    @Test fun theValidatorHasNoIssuesForTheCombinedContent() {
        assertTrue(app.many.economytycoon.content.ContentValidator.validate(ExpandedContent.packs).isEmpty())
    }

    @Test fun packOnlyUsesInputsItsDependenciesProvide() {
        val own = ExpansionFixture.goodKeys(pack)
        val earlier = ExpandedContent.expansionPackIds.takeWhile { it != packId }.flatMap { ExpansionFixture.goodKeys(ExpansionFixture.pack(it)) }.toSet()
        val later = ExpandedContent.expansionPackIds.dropWhile { it != packId }.drop(1).flatMap { ExpansionFixture.goodKeys(ExpansionFixture.pack(it)) }.toSet()
        for (r in pack.recipes) for (i in r.inputs) {
            assertFalse("${r.id.key} uses ${i.good.key} from a later pack", i.good.key in later && i.good.key !in own && i.good.key !in earlier)
        }
    }

    @Test fun recipeStaffIsDefined() {
        for (r in pack.recipes) for (l in r.labor) assertNotNull("${r.id.key} role ${l.role.key}", repo.allRoles.firstOrNull { it.id == l.role })
    }

    @Test fun nothingMakesMoreValueThanItTakesAcrossTheWholeChain() {
        for (g in pack.goods) {
            if (repo.recipesProducing(g.id).isEmpty()) continue
            val cost = ExpansionFixture.chain.perUnit(g.id)
            assertTrue("${g.id.key} chain cost", cost in 1L..g.referencePrice.perUnit.raw)
        }
    }

    @Test fun packNamesAreNotBlank() {
        assertTrue(pack.industries.all { it.name.isNotBlank() })
        assertTrue(pack.goods.all { it.name.isNotBlank() })
        assertTrue(pack.recipes.all { it.name.isNotBlank() })
        assertTrue(pack.facilityTypes.all { it.name.isNotBlank() })
        assertTrue(pack.technologies.all { it.name.isNotBlank() })
    }
}
