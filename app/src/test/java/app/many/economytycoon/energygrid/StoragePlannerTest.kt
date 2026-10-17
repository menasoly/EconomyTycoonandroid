package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePlannerTest {
    private val unit = UnitId(1)

    private fun prices(base: Long, vararg special: Pair<Int, Long>): List<Money> =
        List(24) { h -> Money.units(special.firstOrNull { it.first == h }?.second ?: base) }

    private fun plan(forecast: List<Money>, start: Int = 0, rt: Long = 800_000L, cost: Money = Money.ZERO, hours: Int = 2) =
        StoragePlanner.plan(unit, 10_000L, hours, start, rt, cost, forecast)

    @Test fun flatPricesLeaveTheStoreIdle() {
        val p = plan(prices(40))
        assertTrue(p.isIdle)
        assertEquals(Money.ZERO, p.expectedProfit)
        assertEquals(0, p.endLevel)
    }

    @Test fun buysTheCheapHourAndSellsTheDearOnes() {
        val p = plan(prices(40, 3 to 10L, 18 to 100L, 19 to 90L))
        assertEquals(2, p.chargeHours)
        assertEquals(2, p.dischargeHours)
        assertTrue(p.chargeKw[3] > 0L)
        assertTrue(p.dischargeKw[18] > 0L)
        assertTrue(p.dischargeKw[19] > 0L)
        assertEquals(0, p.endLevel)
        // -100 -400 +800 +720 (hand computed: 10 kW-hours of 10,000 kW at the quoted prices, 80% delivered)
        assertEquals(Money.units(1020), p.expectedProfit)
    }

    @Test fun dischargedEnergyIsReducedByTheRoundTripLoss() {
        val p = plan(prices(40, 3 to 10L, 18 to 100L))
        assertEquals(8_000L, p.dischargeKw[18])
        assertEquals(10_000L, p.drawKw[18])
        assertEquals(10_000L, p.chargeKw.sum())
    }

    @Test fun aSpreadThatDoesNotCoverTheLossIsNotWorthTrading() {
        // buying at 40 and selling at 45 delivers only 80% of 45 = 36
        val p = plan(prices(40, 18 to 45L))
        assertTrue(p.isIdle)
    }

    @Test fun runningCostRaisesTheBarForSelling() {
        val cheap = plan(prices(40, 3 to 10L, 18 to 60L), cost = Money.ZERO)
        val dear = plan(prices(40, 3 to 10L, 18 to 60L), cost = Money.units(50))
        assertTrue(cheap.dischargeHours > 0)
        assertEquals(0, dear.dischargeHours)
    }

    @Test fun aFullStoreSellsIntoThePeak() {
        val p = plan(prices(40, 18 to 120L), start = 2)
        assertTrue(p.dischargeKw[18] > 0L)
        assertEquals(0L, p.chargeKw[0])
    }

    @Test fun neverChargesAboveOrDischargesBelowTheContent() {
        val forecast = prices(40, 2 to 5L, 3 to 5L, 4 to 5L, 5 to 5L, 18 to 100L, 19 to 100L, 20 to 100L, 21 to 100L)
        val p = plan(forecast, hours = 2)
        var level = 0
        for (h in 0 until 24) {
            if (p.chargeKw[h] > 0L) level++
            if (p.dischargeKw[h] > 0L) level--
            assertTrue("level $level at hour $h", level in 0..2)
        }
    }

    @Test fun aBiggerStoreMovesMoreEnergy() {
        val forecast = prices(40, 2 to 5L, 3 to 5L, 4 to 5L, 5 to 5L, 18 to 100L, 19 to 100L, 20 to 100L, 21 to 100L)
        assertEquals(2, plan(forecast, hours = 2).dischargeHours)
        assertEquals(4, plan(forecast, hours = 4).dischargeHours)
    }

    @Test fun planningIsRepeatable() {
        val forecast = prices(40, 3 to 10L, 18 to 100L)
        val a = plan(forecast)
        val b = plan(forecast)
        assertEquals(a.chargeKw.toList(), b.chargeKw.toList())
        assertEquals(a.dischargeKw.toList(), b.dischargeKw.toList())
    }

    @Test fun anEmptyPowerRatingPlansNothing() {
        val p = StoragePlanner.plan(unit, 0L, 4, 0, 800_000L, Money.ZERO, prices(40, 3 to 1L, 18 to 500L))
        assertTrue(p.isIdle)
    }

    @Test fun levelIsCountedInWholeStepsAndCapped() {
        assertEquals(0, StoragePlanner.levelOf(9_999L, 10_000L, 4))
        assertEquals(2, StoragePlanner.levelOf(25_000L, 10_000L, 4))
        assertEquals(4, StoragePlanner.levelOf(900_000L, 10_000L, 4))
        assertEquals(0, StoragePlanner.levelOf(5_000L, 0L, 4))
    }
}
