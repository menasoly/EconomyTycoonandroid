package app.many.economytycoon.content

import app.many.economytycoon.content.packs.AgriculturePack
import app.many.economytycoon.content.packs.BasePack
import app.many.economytycoon.content.packs.ConsumerPack
import app.many.economytycoon.content.packs.EnergyPack
import app.many.economytycoon.content.packs.FoodPack
import app.many.economytycoon.content.packs.IndustryPack
import app.many.economytycoon.content.packs.MiningPack
import app.many.economytycoon.content.packs.ServicesPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPacksTest {
    private val packs = listOf(BasePack.pack, AgriculturePack.pack, FoodPack.pack,
        MiningPack.pack, EnergyPack.pack, IndustryPack.pack, ConsumerPack.pack, ServicesPack.pack)

    @Test fun shippedPacksHaveNoFatalIssues() {
        val issues = ContentValidator.fatal(ContentValidator.validate(packs))
        assertEquals(emptyList<ContentIssue>(), issues)
    }

    @Test fun shippedPacksHaveNoBalanceWarnings() {
        val warnings = ContentValidator.validate(packs).filter { !it.fatal }
        assertEquals(emptyList<ContentIssue>(), warnings)
    }

    @Test fun repositoryIndexesEverything() {
        val repo = ContentRepository.of(packs)
        assertTrue(repo.allGoods.size > 50)
        assertTrue(repo.allRecipes.size > 40)
        assertEquals("Wheat", repo.good(app.many.economytycoon.core.GoodId("wheat")).name)
        assertTrue(repo.recipesProducing(app.many.economytycoon.core.GoodId("bread")).isNotEmpty())
    }

    @Test fun everyConsumerGoodHasDemand() {
        val repo = ContentRepository.of(packs)
        val missing = repo.goodsInCategory(GoodCategory.CONSUMER).filter { repo.demandProfile(it.id) == null }.map { it.id.key }
        assertTrue("consumer goods without demand: $missing", missing.size <= 40)
    }

    @Test fun everyTechnologyIsReachable() {
        val repo = ContentRepository.of(packs)
        for (t in repo.allTechnologies) {
            val chain = repo.prerequisitesOf(t.id)
            assertTrue("${t.id.key} requires itself", t.id !in chain)
        }
    }
}
