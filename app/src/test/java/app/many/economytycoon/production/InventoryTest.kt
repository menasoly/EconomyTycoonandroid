package app.many.economytycoon.production

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {
    private val a = GoodId("a")
    private val b = GoodId("b")
    private fun inv(cap: Long = 1000, vol: (GoodId) -> Long = { 1_000L }) = Inventory(Quantity.units(cap), vol)

    @Test fun averageCostFollowsPurchases() {
        val i = inv()
        i.add(a, Quantity.units(10), Money.units(100))
        i.add(a, Quantity.units(10), Money.units(300))
        assertEquals(Money.units(20), i.unitCost(a))
        assertEquals(Money.units(400), i.totalValue())
    }

    @Test fun removingTakesProportionalValue() {
        val i = inv()
        i.add(a, Quantity.units(10), Money.units(100))
        assertEquals(Money.units(30), i.remove(a, Quantity.units(3)).getOrThrow())
        assertEquals(Money.units(70), i.totalValue())
        assertEquals(Money.units(70), i.remove(a, Quantity.units(7)).getOrThrow())
        assertEquals(Money.ZERO, i.totalValue())
    }

    @Test fun cannotRemoveMoreThanAvailable() {
        val i = inv()
        i.add(a, Quantity.units(5), Money.units(50))
        assertEquals(Rules.INSUFFICIENT_STOCK, i.remove(a, Quantity.units(6)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_QUANTITY, i.remove(a, Quantity.ZERO).rejectionOrNull()?.rule)
    }

    @Test fun reservationsHideStockFromOtherUses() {
        val i = inv()
        i.add(a, Quantity.units(10), Money.units(100))
        i.reserve(a, Quantity.units(8)).getOrThrow()
        assertEquals(Quantity.units(2), i.available(a))
        assertEquals(Rules.INSUFFICIENT_STOCK, i.remove(a, Quantity.units(3)).rejectionOrNull()?.rule)
        assertEquals(Money.units(80), i.removeReserved(a, Quantity.units(8)).getOrThrow())
        assertEquals(Quantity.ZERO, i.reserved(a))
        assertEquals(Quantity.units(2), i.quantity(a))
    }

    @Test fun releaseNeverGoesBelowZero() {
        val i = inv()
        i.add(a, Quantity.units(10), Money.units(100))
        i.reserve(a, Quantity.units(2)).getOrThrow()
        i.release(a, Quantity.units(5))
        assertEquals(Quantity.ZERO, i.reserved(a))
    }

    @Test fun capacityLimitsWhatIsStored() {
        val i = inv(cap = 10)
        val stored = i.add(a, Quantity.units(15), Money.units(150))
        assertEquals(Quantity.units(10), stored)
        assertEquals(Money.units(100), i.totalValue())
        assertEquals(Quantity.ZERO, i.add(a, Quantity.units(1), Money.units(10)))
    }

    @Test fun bulkyGoodsTakeMoreRoom() {
        val i = inv(cap = 10) { g -> if (g == b) 2_000L else 1_000L }
        assertEquals(Quantity.units(5), i.room(b))
        i.add(b, Quantity.units(4), Money.units(40))
        assertEquals(Quantity.units(8), i.used())
        assertEquals(Quantity.units(2), i.room(a))
        assertTrue(i.fits(a, Quantity.units(2)))
        assertTrue(!i.fits(a, Quantity.units(3)))
    }

    @Test fun overflowAllowedForWaste() {
        val i = inv(cap = 1)
        i.add(a, Quantity.units(5), Money.ZERO, allowOverflow = true)
        assertEquals(Quantity.units(5), i.quantity(a))
        assertEquals(Quantity.ZERO, i.free())
    }

    @Test fun spoilageCarriesFractions() {
        val i = inv()
        i.add(a, Quantity.ofRaw(10), Money.units(1))
        var lost = 0L
        repeat(100) { lost += i.spoil(a, 10_000).raw }
        // 1% of 10 milli-units per tick is 0.1 milli-unit; over 100 ticks about 10 milli-units decay in whole steps
        assertTrue("lost $lost", lost in 1..10)
        assertEquals(10L - lost, i.quantity(a).raw)
    }

    @Test fun spoilageNeverTouchesReservedStock() {
        val i = inv()
        i.add(a, Quantity.units(10), Money.units(100))
        i.reserve(a, Quantity.units(10)).getOrThrow()
        assertEquals(Quantity.ZERO, i.spoil(a, 500_000))
    }

    @Test fun clearSkipsReservedStock() {
        val i = inv()
        i.add(a, Quantity.units(10), Money.units(100))
        i.reserve(a, Quantity.units(4)).getOrThrow()
        val (q, v) = i.clear(a)
        assertEquals(Quantity.units(6), q)
        assertEquals(Money.units(60), v)
        assertEquals(Quantity.units(4), i.quantity(a))
    }
}
