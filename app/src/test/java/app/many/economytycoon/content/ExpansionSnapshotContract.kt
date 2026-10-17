package app.many.economytycoon.content

import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.TechId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A pack's structure written out in full: which goods it has and of what category, which facility each recipe runs
 * in, and the tier of each technology. A change to a spec that moves any of these fails here and has to be accepted
 * by updating the snapshot, so a generator run cannot change the content unnoticed.
 */
abstract class ExpansionSnapshotContract(
    private val packId: String,
    private val goods: Map<String, GoodCategory>,
    private val recipes: Map<String, String>,
    private val facilities: Map<String, FacilityClass>,
    private val techs: Map<String, Int>,
) {
    private val pack: ContentPack get() = ExpansionFixture.pack(packId)

    @Test fun goodIdsMatch() {
        assertEquals(goods.keys, pack.goods.map { it.id.key }.toSet())
    }

    @Test fun goodCategoriesMatch() {
        for ((k, c) in goods) assertEquals(k, c, ExpansionFixture.repo.good(GoodId(k)).category)
    }

    @Test fun recipeIdsMatch() {
        assertEquals(recipes.keys, pack.recipes.map { it.id.key }.toSet())
    }

    @Test fun recipeFacilitiesMatch() {
        for ((k, f) in recipes) assertEquals(k, f, ExpansionFixture.repo.recipe(RecipeId(k)).facilityType.key)
    }

    @Test fun facilityIdsMatch() {
        assertEquals(facilities.keys, pack.facilityTypes.map { it.id.key }.toSet())
    }

    @Test fun facilityClassesMatch() {
        for ((k, c) in facilities) assertEquals(k, c, ExpansionFixture.repo.facilityType(FacilityTypeId(k)).facilityClass)
    }

    @Test fun technologyIdsMatch() {
        assertEquals(techs.keys, pack.technologies.map { it.id.key }.toSet())
    }

    @Test fun technologyTiersMatch() {
        for ((k, t) in techs) assertEquals(k, t, ExpansionFixture.repo.technology(TechId(k)).tier)
    }

    @Test fun everyRecipeRunsInAFacilityOfThePackOrAShippedOne() {
        for (f in recipes.values) assertEquals(f, true, ExpansionFixture.repo.facilityTypeOrNull(FacilityTypeId(f)) != null)
    }
}
