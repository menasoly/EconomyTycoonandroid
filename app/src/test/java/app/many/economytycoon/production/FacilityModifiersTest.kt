package app.many.economytycoon.production

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.production.ProductionTestSupport.Rig
import app.many.economytycoon.production.ProductionTestSupport.company
import app.many.economytycoon.production.ProductionTestSupport.wheat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Savings that belong to one facility rather than to its owner, as a retrofit gives. */
class FacilityModifiersTest {
    private fun usedWheat(r: Rig, f: Facility, ticks: Int): Long {
        r.stock(f, wheat, 400.0, 10.0)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_flour")).getOrThrow()
        val before = f.inventory.quantity(wheat)
        r.run(ticks)
        return before.raw - f.inventory.quantity(wheat).raw
    }

    @Test fun aFacilitySavingCutsOnlyThatFacilitysInput() {
        val plain = Rig(); val fa = plain.bakery()
        val saving = Rig()
        val fb = saving.bakery()
        saving.modifiers = object : ProductionModifiers {
            override fun facilityInputSavings(facility: FacilityId, good: GoodId) = if (facility == fb.id && good == wheat) Rate(250_000L) else Rate.ZERO
        }
        val used = usedWheat(plain, fa, 8)
        val usedLess = usedWheat(saving, fb, 8)
        assertTrue(used > 0L)
        assertEquals("a quarter less wheat for the same flour", used * 3 / 4, usedLess)
    }

    @Test fun aFacilitySavingForAnotherFacilityChangesNothing() {
        val plain = Rig(); val fa = plain.bakery()
        val other = Rig(); val fb = other.bakery()
        other.modifiers = object : ProductionModifiers {
            override fun facilityInputSavings(facility: FacilityId, good: GoodId) = if (facility.value == fb.id.value + 99) Rate(500_000L) else Rate.ZERO
            override fun facilityEnergySavings(facility: FacilityId) = if (facility.value == fb.id.value + 99) Rate(500_000L) else Rate.ZERO
        }
        assertEquals(usedWheat(plain, fa, 8), usedWheat(other, fb, 8))
        assertEquals(plain.hash(), other.hash())
    }

    @Test fun noModifiersAreStillTheDefault() {
        val m = NoModifiers
        assertEquals(Rate.ZERO, m.facilityEnergySavings(FacilityId(1)))
        assertEquals(Rate.ZERO, m.facilityInputSavings(FacilityId(1), wheat))
        assertEquals(Quantity.ZERO, Quantity.ZERO)
    }
}
