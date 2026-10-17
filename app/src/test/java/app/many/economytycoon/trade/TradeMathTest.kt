package app.many.economytycoon.trade

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeMathTest {
    private fun leg(
        good: String = "wheat", from: Long = 1, to: Long = 2, buy: Long = 10, sell: Long = 14, qty: Long = 100,
        freight: Long = 120, tariff: Long = 30, days: Int = 4,
    ) = TradeLeg(
        GoodId(good), good.replaceFirstChar { it.uppercase() }, RegionId(from), "R$from", RegionId(to), "R$to",
        Money.units(buy), Money.units(sell), Quantity.units(qty), Money.units(freight), Money.units(tariff), days, false,
    )

    @Test fun profitIsRevenueLessEverythingSpent() {
        val l = leg()
        assertEquals(Money.units(1_000), l.purchase)
        assertEquals(Money.units(1_400), l.revenue)
        assertEquals(Money.units(150), l.carriage)
        assertEquals(Money.units(1_150), l.outlay)
        assertEquals(Money.units(250), l.netProfit)
        assertTrue(l.isProfitable)
    }

    @Test fun marginIsProfitOverOutlay() {
        assertEquals(217_391L, leg().marginPpm)
        assertEquals(0L, leg(buy = 0, sell = 0, freight = 0, tariff = 0).marginPpm)
    }

    @Test fun profitPerDayCountsAtLeastOneDay() {
        assertEquals(625_000L, leg().profitPerDay.raw)
        assertEquals(leg(days = 0).netProfit, leg(days = 0).profitPerDay)
    }

    @Test fun spreadAndCarriagePerUnit() {
        val l = leg()
        assertEquals(Money.units(4), l.spread)
        assertEquals(15_000L, l.carriagePerUnit.raw)
        assertEquals(Money.ZERO, leg(qty = 0).carriagePerUnit)
    }

    @Test fun aLossIsNotProfitable() {
        val l = leg(sell = 10)
        assertTrue(l.netProfit.raw < 0L)
        assertTrue(!l.isProfitable)
    }

    @Test fun affordableHoldsBackTheReserve() {
        val q = TradeMath.affordable(Money.units(1_000), Money.units(200), Money.units(10), Quantity.units(500))
        assertEquals(Quantity.units(80), q)
    }

    @Test fun affordableRespectsTheLimitAndEmptyPurses() {
        assertEquals(Quantity.units(30), TradeMath.affordable(Money.units(10_000), Money.ZERO, Money.units(10), Quantity.units(30)))
        assertEquals(Quantity.ZERO, TradeMath.affordable(Money.units(100), Money.units(500), Money.units(10), Quantity.units(30)))
        assertEquals(Quantity.units(30), TradeMath.affordable(Money.units(100), Money.ZERO, Money.ZERO, Quantity.units(30)))
    }

    @Test fun breakEvenFindsTheSizeThatCoversTheFixedCharge() {
        val q = TradeMath.breakEven(Money.units(4), Money.units(100), Money.units(1))!!
        assertEquals(33_334L, q.raw)
    }

    @Test fun breakEvenIsNullWhenTheSpreadIsEatenPerUnit() {
        assertNull(TradeMath.breakEven(Money.units(1), Money.units(10), Money.units(2)))
        assertEquals(Quantity.ZERO, TradeMath.breakEven(Money.units(3), Money.ZERO, Money.units(1)))
    }

    @Test fun sortingByEachKey() {
        val a = leg(good = "a", buy = 10, sell = 20, qty = 10, days = 10, freight = 0, tariff = 0)
        val b = leg(good = "b", buy = 10, sell = 13, qty = 1_000, days = 1, freight = 0, tariff = 0)
        val c = leg(good = "c", buy = 100, sell = 125, qty = 10, days = 2, freight = 0, tariff = 0)
        val all = listOf(a, b, c)
        assertEquals(listOf("b", "c", "a"), TradeMath.sorted(all, TradeSort.PROFIT).map { it.good.key })
        assertEquals(listOf("a", "b", "c"), TradeMath.sorted(all, TradeSort.MARGIN).map { it.good.key })
        assertEquals(listOf("b", "c", "a"), TradeMath.sorted(all, TradeSort.PER_DAY).map { it.good.key })
        assertEquals(listOf("c", "a", "b"), TradeMath.sorted(all, TradeSort.SPREAD).map { it.good.key })
    }

    @Test fun equalLegsOrderByNameThenRegions() {
        val x = leg(good = "z", from = 3, to = 4); val y = leg(good = "a", from = 5, to = 6); val w = leg(good = "a", from = 2, to = 9)
        assertEquals(listOf(w, y, x), TradeMath.sorted(listOf(x, y, w), TradeSort.PROFIT))
    }

    @Test fun bestPerGoodKeepsOneRoutePerCommodity() {
        val low = leg(good = "wheat", from = 1, to = 2, sell = 12)
        val high = leg(good = "wheat", from = 3, to = 4, sell = 18)
        val other = leg(good = "corn", sell = 13)
        val best = TradeMath.bestPerGood(listOf(low, high, other))
        assertEquals(2, best.size)
        assertEquals(high, best.first { it.good.key == "wheat" })
    }

    @Test fun resizingScalesCarriageInProportion() {
        val l = leg()
        val half = TradeMath.resized(l, Quantity.units(50))
        assertEquals(Money.units(60), half.freight)
        assertEquals(Money.units(15), half.tariff)
        assertEquals(Quantity.units(50), half.quantity)
        assertEquals(l, TradeMath.resized(l, l.quantity))
    }

    @Test fun resizingAZeroLegOnlyChangesTheQuantity() {
        val l = leg(qty = 0)
        assertEquals(Quantity.units(5), TradeMath.resized(l, Quantity.units(5)).quantity)
    }

    @Test fun everySortHasALabel() {
        for (s in TradeSort.values()) assertTrue(s.label.isNotBlank())
    }
}
