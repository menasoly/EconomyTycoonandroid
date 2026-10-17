package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermitHoldingsTest {
    private val h = PermitHoldings()

    @Test fun lotsAreKeptPerCompanyAndVintage() {
        h.add(acme, 2001, Tonnes.of(100), Money.units(1_000))
        h.add(acme, 2001, Tonnes.of(50), Money.units(600))
        h.add(acme, 2002, Tonnes.of(10), Money.units(100))
        assertEquals(Tonnes.of(160), h.total(acme))
        assertEquals(Money.units(1_700), h.totalCost(acme))
        assertEquals(listOf(2001, 2002), h.lotsOf(acme).map { it.vintage })
        assertEquals(Tonnes.of(150), h.lotsOf(acme).first().tonnes)
        assertEquals(Tonnes.ZERO, h.total(borea))
    }

    @Test fun takingUsesTheOldestVintageFirst() {
        h.add(acme, 2002, Tonnes.of(100), Money.units(2_000))
        h.add(acme, 2001, Tonnes.of(100), Money.units(1_000))
        val w = h.take(acme, Tonnes.of(150))
        assertEquals(Tonnes.of(150), w.taken)
        assertEquals(listOf(2001, 2002), w.parts.map { it.vintage })
        assertEquals(Money.units(2_000), w.cost)
        assertEquals(Tonnes.of(50), h.total(acme))
    }

    @Test fun aPartialTakeCarriesAProportionalCost() {
        h.add(acme, 2001, Tonnes.of(100), Money.units(1_000))
        val w = h.take(acme, Tonnes.of(25))
        assertEquals(Money.units(250), w.cost)
        assertEquals(Money.units(750), h.totalCost(acme))
    }

    @Test fun takingTheWholeLotTakesItsWholeCostWithoutRoundingLoss() {
        h.add(acme, 2001, Tonnes.ofKg(3), Money.ofRaw(10))
        h.take(acme, Tonnes.ofKg(1))
        h.take(acme, Tonnes.ofKg(1))
        val last = h.take(acme, Tonnes.ofKg(1))
        assertEquals(Money.ZERO, h.totalCost(acme))
        assertTrue(last.cost.raw >= 3L)
        assertEquals(emptyList<CompanyIdLike>().size, h.companies().size)
    }

    @Test fun takeCannotExceedWhatIsHeldAndNeverGoesNegative() {
        h.add(acme, 2001, Tonnes.of(10), Money.units(100))
        val w = h.take(acme, Tonnes.of(50))
        assertEquals(Tonnes.of(10), w.taken)
        assertEquals(Tonnes.ZERO, h.total(acme))
    }

    @Test fun takingFromAVintageRangeLeavesOthersUntouched() {
        h.add(acme, 2000, Tonnes.of(10), Money.units(100))
        h.add(acme, 2002, Tonnes.of(10), Money.units(100))
        val w = h.take(acme, Tonnes.of(20), minVintage = 2001, maxVintage = 2003)
        assertEquals(Tonnes.of(10), w.taken)
        assertEquals(Tonnes.of(10), h.held(acme, 2000, 2000))
    }

    @Test fun heldCountsOnlyTheRequestedVintages() {
        h.add(acme, 2000, Tonnes.of(10), Money.ZERO)
        h.add(acme, 2001, Tonnes.of(20), Money.ZERO)
        h.add(acme, 2002, Tonnes.of(40), Money.ZERO)
        assertEquals(Tonnes.of(60), h.held(acme, 2001, 2002))
        assertEquals(Tonnes.of(70), h.totalAll())
    }

    @Test fun expiryRemovesOldVintagesAndReportsThem() {
        h.add(acme, 1999, Tonnes.of(10), Money.units(50))
        h.add(acme, 2001, Tonnes.of(20), Money.units(60))
        h.add(borea, 2000, Tonnes.of(5), Money.units(5))
        val gone = h.expire(2001)
        assertEquals(listOf(acme to 1999, borea to 2000), gone.map { it.company to it.vintage })
        assertEquals(Tonnes.of(20), h.total(acme))
        assertEquals(Tonnes.ZERO, h.total(borea))
        assertEquals(listOf(acme), h.companies())
    }

    @Test fun emptyingACompanyRemovesItFromTheBook() {
        h.add(acme, 2001, Tonnes.of(10), Money.ZERO)
        h.take(acme, Tonnes.of(10))
        assertTrue(h.companies().isEmpty())
    }

    private class CompanyIdLike
}
