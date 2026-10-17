package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionDependencyTest {
    private val packs get() = ExpansionFixture.expansion

    /** Packs whose goods this pack's recipes take as inputs, other than itself and the base packs. */
    private fun needs(pack: ContentPack): Set<String> {
        val owner = HashMap<String, String>()
        for (p in packs) for (g in p.goods) owner[g.id.key] = p.version.packId
        return pack.recipes.flatMap { r -> r.inputs.mapNotNull { owner[it.good.key] } }.toSet() - pack.version.packId
    }

    private fun needsTech(pack: ContentPack): Set<String> {
        val owner = HashMap<String, String>()
        for (p in packs) for (t in p.technologies) owner[t.id.key] = p.version.packId
        return pack.technologies.flatMap { t -> t.prerequisites.mapNotNull { owner[it.key] } }.toSet() - pack.version.packId
    }

    private fun graph(): Map<String, Set<String>> = packs.associate { it.version.packId to (needs(it) + needsTech(it)) }

    @Test fun packDependenciesAreAcyclic() {
        val g = graph()
        val state = HashMap<String, Int>()
        fun visit(id: String) {
            check(state[id] != 1) { "cycle at $id" }
            if (state[id] == 2) return
            state[id] = 1
            g.getValue(id).forEach { visit(it) }
            state[id] = 2
        }
        g.keys.forEach { visit(it) }
    }

    @Test fun packsAreListedAfterEverythingTheyNeed() {
        val order = ExpandedContent.expansionPackIds
        for ((id, deps) in graph()) for (d in deps) assertTrue("$id before $d", order.indexOf(d) < order.indexOf(id))
    }

    @Test fun chemicalsNeedNoOtherExpansionPack() {
        assertEquals(emptySet<String>(), graph().getValue("chemicals"))
    }

    @Test fun medicalDevicesMayNeedElectronics() {
        assertTrue(graph().getValue("medical_devices").all { it in ExpandedContent.expansionPackIds })
    }


    @Test fun everyPackIsNeededByNoLaterCycleOrStandsAlone() {
        val g = graph()
        assertTrue(g.values.flatten().all { it in g.keys })
    }

    @Test fun theBaseTechnologiesTheExpansionNeedsAllExist() {
        val repo = ExpansionFixture.repo
        for (p in packs) for (t in p.technologies) for (q in t.prerequisites) assertTrue(q.key, repo.technologyOrNull(q) != null)
    }

    @Test fun dependencyGraphIsAsDocumented() {
        val g = graph()
        assertEquals(emptySet<String>(), g.getValue("chemicals"))
        assertEquals(setOf("chemicals"), g.getValue("pharmaceuticals"))
        assertEquals(setOf("chemicals"), g.getValue("textiles"))
        assertEquals(setOf("chemicals", "textiles"), g.getValue("construction_materials"))
        assertEquals(setOf("chemicals"), g.getValue("electronics"))
        assertEquals(setOf("chemicals", "textiles", "electronics", "construction_materials"), g.getValue("automotive"))
        assertEquals(setOf("textiles", "electronics"), g.getValue("aerospace"))
        assertEquals(setOf("chemicals", "textiles", "aerospace", "electronics", "pharmaceuticals", "construction_materials"), g.getValue("medical_devices"))
    }

    @Test fun noPackNeedsAutomotiveExceptNone() {
        assertTrue(graph().values.none { "automotive" in it })
    }
}
