package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionUniquenessTest {
    private val base get() = ExpandedContent.basePacks
    private val expansion get() = ExpandedContent.expansionPacks

    private fun <T> clash(a: Collection<T>, b: Collection<T>) = a.toSet().intersect(b.toSet())

    private fun <T> duplicates(items: List<T>): List<T> = items.groupBy { it }.filter { it.value.size > 1 }.keys.toList()

    @Test fun goodIdsDoNotClashWithTheBasePacks() {
        assertEquals(emptySet<String>(), clash(base.flatMap { p -> p.goods.map { it.id.key } }, expansion.flatMap { p -> p.goods.map { it.id.key } }))
    }

    @Test fun recipeIdsDoNotClashWithTheBasePacks() {
        assertEquals(emptySet<String>(), clash(base.flatMap { p -> p.recipes.map { it.id.key } }, expansion.flatMap { p -> p.recipes.map { it.id.key } }))
    }

    @Test fun facilityIdsDoNotClashWithTheBasePacks() {
        assertEquals(emptySet<String>(), clash(base.flatMap { p -> p.facilityTypes.map { it.id.key } }, expansion.flatMap { p -> p.facilityTypes.map { it.id.key } }))
    }

    @Test fun techIdsDoNotClashWithTheBasePacks() {
        assertEquals(emptySet<String>(), clash(base.flatMap { p -> p.technologies.map { it.id.key } }, expansion.flatMap { p -> p.technologies.map { it.id.key } }))
    }

    @Test fun industryIdsDoNotClashWithTheBasePacks() {
        assertEquals(emptySet<String>(), clash(base.flatMap { p -> p.industries.map { it.id.key } }, expansion.flatMap { p -> p.industries.map { it.id.key } }))
    }

    @Test fun machineIdsDoNotClashWithTheBasePacks() {
        assertEquals(emptySet<String>(), clash(base.flatMap { p -> p.machineTypes.map { it.id.key } }, expansion.flatMap { p -> p.machineTypes.map { it.id.key } }))
    }

    @Test fun roleIdsDoNotClashWithTheBasePacks() {
        assertEquals(emptySet<String>(), clash(base.flatMap { p -> p.roles.map { it.id.key } }, expansion.flatMap { p -> p.roles.map { it.id.key } }))
    }

    @Test fun demandProfilesAreNotRepeatedForBaseGoods() {
        val baseDemand = base.flatMap { p -> p.demandProfiles.map { it.good.key } }
        assertEquals(emptySet<String>(), clash(baseDemand, expansion.flatMap { p -> p.demandProfiles.map { it.good.key } }))
    }

    @Test fun idsAreUniqueInsideTheExpansion() {
        assertEquals(emptyList<String>(), duplicates(expansion.flatMap { p -> p.goods.map { it.id.key } }))
        assertEquals(emptyList<String>(), duplicates(expansion.flatMap { p -> p.recipes.map { it.id.key } }))
        assertEquals(emptyList<String>(), duplicates(expansion.flatMap { p -> p.facilityTypes.map { it.id.key } }))
        assertEquals(emptyList<String>(), duplicates(expansion.flatMap { p -> p.technologies.map { it.id.key } }))
        assertEquals(emptyList<String>(), duplicates(expansion.flatMap { p -> p.industries.map { it.id.key } }))
        assertEquals(emptyList<String>(), duplicates(expansion.flatMap { p -> p.machineTypes.map { it.id.key } }))
    }

    @Test fun namesAreUniqueAmongGoods() {
        val names = ExpandedContent.packs.flatMap { p -> p.goods.map { it.name.lowercase() } }
        assertEquals(emptyList<String>(), duplicates(names))
    }

    @Test fun namesAreUniqueAmongFacilitiesAndTechnologies() {
        assertEquals(emptyList<String>(), duplicates(ExpandedContent.packs.flatMap { p -> p.facilityTypes.map { it.name.lowercase() } }))
        assertEquals(emptyList<String>(), duplicates(ExpandedContent.packs.flatMap { p -> p.technologies.map { it.name.lowercase() } }))
    }

    @Test fun namesAreUniqueAmongRecipesWithinTheExpansion() {
        assertEquals(emptyList<String>(), duplicates(expansion.flatMap { p -> p.recipes.map { it.name.lowercase() } }))
    }

    @Test fun idsAreLowerSnakeCase() {
        val ok = Regex("[a-z][a-z0-9_]*")
        val all = expansion.flatMap { p -> p.goods.map { it.id.key } + p.recipes.map { it.id.key } + p.facilityTypes.map { it.id.key } + p.technologies.map { it.id.key } + p.industries.map { it.id.key } + p.machineTypes.map { it.id.key } + p.roles.map { it.id.key } }
        assertTrue(all.filterNot { ok.matches(it) }.toString(), all.all { ok.matches(it) })
    }

    @Test fun packIdsAreUniqueAndLowerCase() {
        val ids = ExpandedContent.packs.map { it.version.packId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it == it.lowercase() })
    }

    @Test fun everyIndustryIsDefinedByExactlyOnePack() {
        for (i in ExpandedContent.repository.allIndustries) assertEquals(i.id.key, 1, ExpandedContent.packs.count { p -> p.industries.any { it.id == i.id } })
    }

    @Test fun everyExpansionRecipeBelongsToAnIndustryOfItsOwnPack() {
        for (p in expansion) {
            val own = p.industries.map { it.id }.toSet()
            for (r in p.recipes) assertTrue("${r.id.key} in ${p.version.packId}", r.industry in own)
        }
    }
}
