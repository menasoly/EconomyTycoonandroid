package app.many.economytycoon.content.audit

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodDef
import app.many.economytycoon.core.GoodId

/**
 * Who makes and who uses each good. Built once from the content so the audit can ask which goods nothing
 * produces, which nothing consumes, and which can be made at all starting from the ground.
 */
class GoodFlows(private val content: ContentRepository) {
    private val producers = HashMap<GoodId, MutableList<String>>()
    private val consumers = HashMap<GoodId, MutableList<String>>()
    private val extracted = HashSet<GoodId>()

    init {
        for (r in content.allRecipes) {
            for (o in r.outputs) producers.getOrPut(o.good) { ArrayList() } += r.id.key
            for (i in r.inputs) consumers.getOrPut(i.good) { ArrayList() } += r.id.key
        }
        for (f in content.allFacilityTypes) {
            f.extracts?.let { extracted += it; producers.getOrPut(it) { ArrayList() } += f.id.key }
            for (b in f.buildInputs) consumers.getOrPut(b.good) { ArrayList() } += f.id.key
        }
    }

    fun producersOf(good: GoodId): List<String> = producers[good].orEmpty()
    fun consumersOf(good: GoodId): List<String> = consumers[good].orEmpty()
    fun isExtracted(good: GoodId): Boolean = good in extracted

    /** Goods nothing makes. Consumer demand does not count as making. */
    fun unmade(): List<GoodDef> = content.allGoods.filter { producers[it.id].isNullOrEmpty() }

    /** Goods nothing uses as an input, and that no consumer demand profile buys. */
    fun unused(): List<GoodDef> = content.allGoods.filter { g -> consumers[g.id].isNullOrEmpty() && content.demandProfile(g.id) == null }

    /**
     * Goods that can be produced by some chain that starts from what the world supplies: extracted goods, and
     * goods nothing makes (the state's import desk stocks those). Technology locks are ignored, and a catalyst
     * a recipe gives back does not have to come from elsewhere. A good outside this set can never appear.
     */
    fun makeable(): Set<GoodId> {
        val have = HashSet<GoodId>()
        have += extracted
        have += unmade().map { it.id }
        var changed = true
        while (changed) {
            changed = false
            for (r in content.allRecipes) {
                val needed = r.inputs.filter { inp -> !(r.catalytic && r.outputs.any { it.good == inp.good }) }
                if (needed.all { it.good in have }) for (o in r.outputs) if (have.add(o.good)) changed = true
            }
        }
        return have
    }

    /** Goods that exist in the definitions but cannot be reached from the ground. */
    fun unreachable(): List<GoodDef> {
        val ok = makeable()
        return content.allGoods.filter { it.id !in ok }
    }
}
