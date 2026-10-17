package app.many.economytycoon.content

import app.many.economytycoon.core.GoodId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionLoopTest {
    private val repo get() = ExpansionFixture.repo

    private fun edges(): Map<GoodId, Set<GoodId>> {
        val out = HashMap<GoodId, MutableSet<GoodId>>()
        for (r in repo.allRecipes) {
            val ins = if (r.catalytic) r.inputs.filter { i -> r.outputs.none { it.good == i.good } } else r.inputs
            for (i in ins) for (o in r.outputs) out.getOrPut(i.good) { HashSet() }.add(o.good)
        }
        return out
    }

    private fun hasCycleThrough(start: GoodId, g: Map<GoodId, Set<GoodId>>): Boolean {
        val seen = HashSet<GoodId>(); val stack = ArrayDeque<GoodId>()
        g[start].orEmpty().forEach { stack.add(it) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n == start) return true
            if (seen.add(n)) g[n].orEmpty().forEach { stack.add(it) }
        }
        return false
    }

    @Test fun noExpansionGoodSitsOnARecipeLoop() {
        val g = edges()
        val looped = ExpansionFixture.allExpansionGoods.filter { hasCycleThrough(GoodId(it), g) }
        assertTrue("loops through: $looped", looped.isEmpty())
    }

    @Test fun noGoodAtAllSitsOnALoop() {
        val g = edges()
        assertTrue(repo.allGoods.none { hasCycleThrough(it.id, g) })
    }

    @Test fun validatorReportsNoRecipeCycle() {
        assertTrue(ContentValidator.validate(ExpandedContent.packs).none { it.code.contains("cycle") })
    }

    @Test fun noRecipeOutputsItsOwnInput() {
        for (p in ExpansionFixture.expansion) for (r in p.recipes) assertTrue(r.id.key, r.outputs.none { o -> r.inputs.any { it.good == o.good } })
    }

    @Test fun noRecipeHasACatalyst() {
        assertTrue(ExpansionFixture.expansion.flatMap { it.recipes }.none { it.catalytic })
    }

    @Test fun everyRecipeLosesValueNowhereAndMintsNoneBeyondItsBand() {
        for (id in ExpansionFixture.allExpansionRecipes) {
            val e = ExpansionFixture.economyById.getValue(id)
            assertTrue("$id profit", e.profitPerBatch > 0L)
            assertTrue("$id margin ${e.marginPpm}", e.marginPpm < 600_000L)
        }
    }

    @Test fun chainCostNeverExceedsThePriceOfAnyMadeGood() {
        for (k in ExpansionFixture.allExpansionGoods) {
            val g = repo.good(GoodId(k))
            val cost = ExpansionFixture.chain.perUnit(g.id)
            assertTrue("$k cost $cost price ${g.referencePrice.perUnit.raw}", cost <= g.referencePrice.perUnit.raw)
        }
    }

    @Test fun chainCostStaysAboveAQuarterOfThePrice() {
        for (k in ExpansionFixture.allExpansionGoods) {
            val g = repo.good(GoodId(k))
            val cost = ExpansionFixture.chain.perUnit(g.id)
            assertTrue("$k", cost * 4L >= g.referencePrice.perUnit.raw)
        }
    }
}
