package app.many.economytycoon.content.audit

import app.many.economytycoon.core.GoodId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodFlowsTest {
    private val flows = GoodFlows(AuditFixture.content)

    @Test fun producersIncludeRecipesAndExtractors() {
        assertEquals(listOf("dig_ore", "mine"), flows.producersOf(GoodId("ore")).sorted())
        assertEquals(listOf("smelt"), flows.producersOf(GoodId("bar")))
        assertTrue(flows.producersOf(GoodId("mystery")).isEmpty())
    }

    @Test fun consumersListRecipesThatUseAGood() {
        assertEquals(listOf("make_orphan", "press_junk", "smelt"), flows.consumersOf(GoodId("ore")).sorted())
        assertTrue(flows.consumersOf(GoodId("gem")).isEmpty())
    }

    @Test fun extractedGoodsAreMarked() {
        assertTrue(flows.isExtracted(GoodId("ore"))); assertFalse(flows.isExtracted(GoodId("bar")))
    }

    @Test fun goodsNothingMakesAreFound() {
        assertEquals(setOf("electricity", "mystery"), flows.unmade().map { it.id.key }.toSet())
    }

    @Test fun madeGoodsNobodyUsesAreFound() {
        val unused = flows.unused().map { it.id.key }.toSet()
        assertTrue("gem" in unused && "junk" in unused && "orphan" in unused && "bar" in unused)
        assertFalse("ore" in unused)
    }

    @Test fun importedGoodsStartAChain() {
        assertTrue(GoodId("gem") in flows.makeable())
        assertTrue(GoodId("bar") in flows.makeable())
    }

    @Test fun aCycleWithNoWayInCannotBeReached() {
        val stuck = flows.unreachable().map { it.id.key }.toSet()
        assertEquals(setOf("loop_a", "loop_b"), stuck)
    }

    @Test fun aCatalystMayFeedItself() {
        val shipped = GoodFlows(app.many.economytycoon.simulation.SimTestSupport.content)
        assertTrue(GoodId("seeds") in shipped.makeable())
    }
}
