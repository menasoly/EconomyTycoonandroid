package app.many.economytycoon.macro

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiscalTest {
    private val country = app.many.economytycoon.core.CountryId(1)

    @Test fun deficitsAreBorrowedAndSurplusesRepay() {
        val f = FiscalBook(country)
        f.addRevenue(Money.units(100)); f.addSpending(Money.units(160))
        assertEquals(Money.units(-60), f.close(0, 12))
        assertEquals(Money.units(60), f.debt)
        f.addRevenue(Money.units(100)); f.addSpending(Money.units(70))
        assertEquals(Money.units(30), f.close(0, 12))
        assertEquals(Money.units(30), f.debt)
        assertEquals(Money.ZERO, f.openRevenue())
    }

    @Test fun debtCannotGoNegative() {
        val f = FiscalBook(country)
        f.addRevenue(Money.units(500))
        f.close(0, 12)
        assertEquals(Money.ZERO, f.debt)
    }

    @Test fun interestIsChargedOnDebt() {
        val f = FiscalBook(country)
        f.addSpending(Money.units(1_200)); f.close(0, 12)
        val balance = f.close(120_000L, 12)
        assertEquals(Money.units(-12), balance)
        assertEquals(Money.units(12), f.lastInterest)
        assertEquals(Money.units(1_212), f.debt)
    }

    @Test fun debtToGdp() {
        val f = FiscalBook(country)
        f.addSpending(Money.units(500)); f.close(0, 12)
        assertEquals(250_000L, f.debtToGdpPpm(Money.units(2_000)))
        assertEquals(0L, f.debtToGdpPpm(Money.ZERO))
    }

    @Test fun ruleRaisesTaxesWhenIndebted() {
        val p = FiscalRule.recommend(Money.units(-10), Money.units(10_000), 1_100_000L, 250_000L, 100_000L)
        assertEquals(255_000L, p.corporatePpm)
        assertEquals(102_500L, p.salesPpm)
        assertTrue(p.changed(250_000L, 100_000L))
    }

    @Test fun ruleRaisesTaxesOnBigDeficit() {
        val p = FiscalRule.recommend(Money.units(-100), Money.units(10_000), 100_000L, 250_000L, 100_000L)
        assertEquals(255_000L, p.corporatePpm)
    }

    @Test fun ruleCutsTaxesInSurplus() {
        val p = FiscalRule.recommend(Money.units(100), Money.units(10_000), 200_000L, 250_000L, 100_000L)
        assertEquals(247_500L, p.corporatePpm)
        assertEquals(98_750L, p.salesPpm)
    }

    @Test fun ruleLeavesRatesAloneInNormalTimesAndStaysInBounds() {
        val calm = FiscalRule.recommend(Money.units(10), Money.units(10_000), 600_000L, 250_000L, 100_000L)
        assertEquals(250_000L, calm.corporatePpm)
        val high = FiscalRule.recommend(Money.units(-500), Money.units(10_000), 2_000_000L, 450_000L, 300_000L)
        assertEquals(450_000L, high.corporatePpm)
        assertEquals(300_000L, high.salesPpm)
        val low = FiscalRule.recommend(Money.units(900), Money.units(10_000), 0L, 50_000L, 0L)
        assertEquals(50_000L, low.corporatePpm)
        assertEquals(0L, low.salesPpm)
        assertEquals(250_000L, FiscalRule.recommend(Money.units(1), Money.ZERO, 0L, 250_000L, 1L).corporatePpm)
    }

    @Test fun minimumWageFollowsInflationButNeverFalls() {
        assertEquals(1_050L, FiscalRule.indexWage(1_000L, 50_000L))
        assertEquals(1_000L, FiscalRule.indexWage(1_000L, -30_000L))
        assertEquals(1_000L, FiscalRule.indexWage(1_000L, 0L))
    }

    @Test fun serviceProposesTaxesFromItsLatestBudget() {
        val r = MacroRig()
        assertEquals(250_000L, r.service.proposeTaxes(r.country, 250_000L, 100_000L).corporatePpm)
        r.service.recordSpending(r.country, Money.units(900_000))
        r.run(1)
        val p = r.service.proposeTaxes(r.country, 250_000L, 100_000L)
        assertEquals(255_000L, p.corporatePpm)
    }
}
