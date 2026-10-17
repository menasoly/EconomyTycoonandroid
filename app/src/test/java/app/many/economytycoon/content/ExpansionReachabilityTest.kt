package app.many.economytycoon.content

import app.many.economytycoon.core.GoodId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionReachabilityTest {
    private val flows get() = ExpansionFixture.flows
    private val repo get() = ExpansionFixture.repo

    @Test fun everyExpansionGoodCanBeMadeFromRawResources() {
        val makeable = flows.makeable()
        val stuck = ExpansionFixture.allExpansionGoods.filter { GoodId(it) !in makeable }
        assertTrue("not makeable: $stuck", stuck.isEmpty())
    }

    @Test fun nothingInTheCombinedContentIsUnreachable() {
        assertEquals(emptyList<String>(), flows.unreachable().map { it.id.key })
    }

    @Test fun everyExpansionGoodHasAProducer() {
        val unmade = ExpansionFixture.allExpansionGoods.filter { flows.producersOf(GoodId(it)).isEmpty() }
        assertTrue("unmade: $unmade", unmade.isEmpty())
    }

    @Test fun inputsNobodyMakesAreFewAndKnown() {
        val inputs = ExpansionFixture.expansion.flatMap { p -> p.recipes.flatMap { r -> r.inputs.map { it.good.key } } }.toSet()
        val bought = inputs.filter { flows.producersOf(GoodId(it)).isEmpty() }.toSet()
        assertTrue("unexpected raw inputs: ${bought - ExpansionFixture.allowedExogenous}", ExpansionFixture.allowedExogenous.containsAll(bought))
    }

    @Test fun extractedGoodsComeFromMines() {
        for (k in listOf("sulfur_ore", "potash_ore", "gypsum", "titanium_ore")) assertTrue(k, flows.isExtracted(GoodId(k)))
    }

    @Test fun everyRecipeInputIsMakeable() {
        val makeable = flows.makeable()
        for (p in ExpansionFixture.expansion) for (r in p.recipes) for (i in r.inputs) {
            val ok = i.good in makeable || flows.producersOf(i.good).isEmpty()
            assertTrue("${r.id.key} needs ${i.good.key}", ok)
        }
    }

    @Test fun everyExpansionRecipeOutputIsMakeable() {
        val makeable = flows.makeable()
        for (p in ExpansionFixture.expansion) for (r in p.recipes) for (o in r.outputs) assertTrue("${r.id.key} -> ${o.good.key}", o.good in makeable)
    }

    @Test fun rawResourcesOfTheExpansionNeedNoInputsFromTheExpansion() {
        for (k in listOf("sulfur_ore", "potash_ore", "gypsum", "titanium_ore")) {
            val makers = repo.recipesProducing(GoodId(k))
            assertTrue(k, makers.isNotEmpty())
            for (r in makers) assertTrue(r.id.key, r.inputs.none { it.good.key in ExpansionFixture.allExpansionGoods })
        }
    }

    @Test fun existingUnmadeGoodsBecomeMakeable() {
        for (k in listOf("server_hardware", "network_equipment", "medical_equipment", "medical_equipment_kit")) {
            assertTrue(k, flows.producersOf(GoodId(k)).isNotEmpty())
        }
    }

    @Test fun theImportDeskGoodsAreTheSameAsBeforeMinusTheNewlyMade() {
        val before = app.many.economytycoon.content.audit.GoodFlows(ContentRepository.of(ExpandedContent.basePacks)).unmade().map { it.id.key }.toSet()
        val after = flows.unmade().map { it.id.key }.toSet()
        assertTrue(after.size < before.size)
        assertTrue(after.all { it in before })
    }

    @Test fun everyChainEndsInRawGoods() {
        for (g in ExpansionFixture.allExpansionGoods) {
            val seen = HashSet<GoodId>()
            val queue = ArrayDeque<GoodId>(); queue.add(GoodId(g))
            var reachedRaw = false
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                if (!seen.add(cur)) continue
                val makers = repo.recipesProducing(cur)
                if (makers.isEmpty() || flows.isExtracted(cur)) { reachedRaw = true; continue }
                makers.forEach { r -> r.inputs.forEach { queue.add(it.good) } }
            }
            assertTrue(g, reachedRaw)
        }
    }
}
