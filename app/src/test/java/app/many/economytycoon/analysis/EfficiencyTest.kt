package app.many.economytycoon.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EfficiencyTest {
    private val f = AnalysisFixture

    @Test fun turnoverAnnualisesTheMonth() {
        // 100k a month is 1.2M a year over assets of 50+20+30+260 = 360k.
        val e = Efficiency.of(f.income(), f.balance(), 30L)
        assertEquals(3_333_333L, e.assetTurnoverPpm)
    }

    @Test fun fixedAssetTurnoverUsesNetPlant() {
        val e = Efficiency.of(f.income(), f.balance(), 30L)
        assertEquals(4_615_385L, e.fixedAssetTurnoverPpm)
    }

    @Test fun daysAreBalanceOverFlowTimesThePeriod() {
        val e = Efficiency.of(f.income(), f.balance(), 30L)
        assertEquals(22L, e.inventoryDays)   // 30k stock against 40k of materials bought in 30 days
        assertEquals(6L, e.receivableDays)   // 20k owed on 100k of sales
        assertEquals(11L, e.payableDays)     // 15k owed on 40k of materials
        assertEquals(22L + 6L - 11L, e.cashCycleDays)
    }

    @Test fun noSalesLeavesDaysUndefinedNotZero() {
        val e = Efficiency.of(f.income(sales = 0, cogs = 0), f.balance(), 30L)
        assertNull(e.receivableDays)
        assertNull(e.inventoryDays)
        assertNull(e.cashCycleDays)
        assertEquals(0L, e.assetTurnoverPpm ?: 0L)
    }

    @Test fun noPlantLeavesFixedTurnoverUndefined() {
        val e = Efficiency.of(f.income(), f.balance(fixedGross = 0, accumulated = 0), 30L)
        assertNull(e.fixedAssetTurnoverPpm)
    }

    @Test fun cashCycleCanGoNegativeWhenSuppliersFundTheStock() {
        val e = Efficiency.of(f.income(), f.balance(inventory = 5_000, payables = 40_000), 30L)
        assertEquals(true, (e.cashCycleDays ?: 0L) < 0L)
    }

    @Test fun aLongerPeriodScalesFlowsConsistently() {
        val month = Efficiency.of(f.income(), f.balance(), 30L)
        val doubled = Efficiency.of(f.income(sales = 200_000, cogs = 80_000), f.balance(), 60L)
        assertEquals(month.assetTurnoverPpm, doubled.assetTurnoverPpm)
        assertEquals(month.inventoryDays, doubled.inventoryDays)
    }
}
