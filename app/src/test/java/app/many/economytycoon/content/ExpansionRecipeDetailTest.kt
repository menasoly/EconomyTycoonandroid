package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionRecipeDetailTest {
    private val repo get() = ExpansionFixture.repo
    private val recipes get() = ExpansionFixture.expansion.flatMap { it.recipes }

    @Test fun durationsAreBetweenOneAndSixtyDays() {
        for (r in recipes) assertTrue("${r.id.key} ${r.durationTicks}", r.durationTicks in 1..60)
    }

    @Test fun everyRecipeNeedsStaff() {
        for (r in recipes) assertTrue(r.id.key, r.labor.isNotEmpty() && r.labor.all { it.headcount > 0 })
    }

    @Test fun everyRecipeHasInputsExceptAtMostMines() {
        for (r in recipes) {
            val mine = repo.facilityType(r.facilityType).extracts != null
            assertTrue(r.id.key, r.inputs.isNotEmpty() || mine)
        }
    }

    @Test fun everyRecipeUsesEnergy() {
        for (r in recipes) assertTrue(r.id.key, r.energyPerBatch.raw > 0L)
    }

    @Test fun inputsAndOutputsAreNeverRepeatedInOneRecipe() {
        for (r in recipes) {
            assertEquals(r.id.key, r.inputs.size, r.inputs.map { it.good }.toSet().size)
            assertEquals(r.id.key, r.outputs.size, r.outputs.map { it.good }.toSet().size)
        }
    }

    @Test fun defectRatesAreBelowTenPercent() {
        for (r in recipes) assertTrue(r.id.key, r.baseDefectPpm in 0L..100_000L)
    }

    @Test fun wasteGoodsAreWasteOrExistingGoods() {
        for (r in recipes) for (w in r.waste) assertTrue("${r.id.key} ${w.good.key}", repo.goodOrNull(w.good) != null)
    }

    @Test fun mostRecipesMakeOneProduct() {
        assertTrue(recipes.count { it.outputs.size == 1 } * 10 >= recipes.size * 9)
    }

    @Test fun recipeIdsAreUnique() {
        assertEquals(recipes.size, recipes.map { it.id }.toSet().size)
    }

    @Test fun recipeInputsAreCheaperPerBatchThanOutputs() {
        for (r in recipes) {
            val e = ExpansionFixture.economyById.getValue(r.id.key)
            assertTrue(r.id.key, e.inputCost < e.outputValue)
        }
    }

    @Test fun laborIsALimitedShareOfCost() {
        for (r in recipes) {
            val e = ExpansionFixture.economyById.getValue(r.id.key)
            assertTrue("${r.id.key} labor ${e.laborCost} of ${e.totalCost}", e.laborCost * 100L <= e.totalCost * 80L)
        }
    }

    @Test fun energyIsALimitedShareOfCost() {
        for (r in recipes) {
            val e = ExpansionFixture.economyById.getValue(r.id.key)
            assertTrue("${r.id.key} energy ${e.energyCost} of ${e.totalCost}", e.energyCost * 100L <= e.totalCost * 35L)
        }
    }

    @Test fun materialShareIsPositiveForEveryRecipeThatBuysInputs() {
        for (r in recipes.filter { it.inputs.isNotEmpty() }) assertTrue(r.id.key, ExpansionFixture.economyById.getValue(r.id.key).materialSharePpm > 0L)
    }

    @Test fun profitPerLineDayIsPositive() {
        for (r in recipes) assertTrue(r.id.key, ExpansionFixture.economyById.getValue(r.id.key).profitPerLineDay > 0L)
    }

    @Test fun recipesUseOnlyKnownRoles() {
        val roles = repo.allRoles.map { it.id }.toSet()
        for (r in recipes) assertTrue(r.id.key, r.labor.all { it.role in roles })
    }

    @Test fun quantitiesHaveNoMoreThanThreeDecimals() {
        for (r in recipes) for (a in r.inputs + r.outputs) assertTrue("${r.id.key} ${a.good.key}", a.quantity.raw > 0L)
    }

    @Test fun eachFacilityTypeRunsRecipesOfOneIndustryFamily() {
        for (f in ExpansionFixture.expansion.flatMap { it.facilityTypes }) {
            val families = repo.recipesForFacility(f.id).map { repo.industry(it.industry).family }.toSet()
            assertTrue("${f.id.key} $families", families.size <= 2)
        }
    }

    @Test fun recipeNamesStartWithACapital() {
        assertTrue(recipes.all { it.name.first().isUpperCase() })
    }

    @Test fun everyRecipeIsReachableByItsFacilityBeingBuildable() {
        for (r in recipes) {
            val f = repo.facilityType(r.facilityType)
            assertTrue(r.id.key, f.maxLines > 0 || f.extracts != null)
        }
    }
}
