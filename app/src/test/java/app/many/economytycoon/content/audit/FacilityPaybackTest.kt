package app.many.economytycoon.content.audit

import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilityPaybackTest {
    private val c = AuditFixture.content
    private val all = FacilityPayback.all(c, RecipeEconomics.all(c)).associateBy { it.facility.id.key }

    @Test fun everyFacilityTypeGetsARow() {
        assertEquals(c.allFacilityTypes.size, all.size)
    }

    @Test fun aGoodLineEarnsBackFast() {
        val p = all.getValue("mine")
        assertEquals("dig_ore", p.bestRecipe!!.recipe.id.key)
        assertEquals(Money.units(160).raw, p.grossPerDay)
        assertEquals(Money.units(150).raw, p.netPerDay)
        assertEquals(7L, p.paybackDays)
    }

    @Test fun costsThatOutrunProfitMeanNeverPayingBack() {
        val p = all.getValue("smelter")
        assertTrue(p.netPerDay < 0L)
        assertNull(p.paybackDays)
    }

    @Test fun theBestRecipeOfSeveralIsChosen() {
        val p = all.getValue("smelter")
        assertEquals("smelt", p.bestRecipe!!.recipe.id.key)
    }

    @Test fun aFacilityWithNoRecipeHasNoBestOne() {
        val p = all.getValue("empty_hall")
        assertNull(p.bestRecipe); assertEquals(0L, p.grossPerDay)
        assertNull(p.paybackDays)
    }

    @Test fun overheadStaffIsChargedAgainstProfit() {
        val shipped = app.many.economytycoon.simulation.SimTestSupport.content
        val type = shipped.allFacilityTypes.first { it.overheadStaff.isNotEmpty() }
        val p = FacilityPayback.of(shipped, type, RecipeEconomics.all(shipped).associateBy { it.recipe.id.key })
        assertTrue(p.overheadPerDay > 0L)
        assertEquals(p.grossPerDay - p.upkeepPerDay - p.overheadPerDay, p.netPerDay)
        assertNotNull(type.id)
        assertEquals(FacilityTypeId(type.id.key), type.id)
    }

    @Test fun paybackRoundsUp() {
        val p = Payback(c.allFacilityTypes.first(), null, 100, 0, 0, 250)
        assertEquals(3L, p.paybackDays)
    }
}
