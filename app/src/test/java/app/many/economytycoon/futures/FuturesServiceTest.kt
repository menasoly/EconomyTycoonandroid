package app.many.economytycoon.futures

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturesServiceTest {
    private val s = FuturesService(IdAllocators())
    private val me = CompanyId(1)
    private val wheat = GoodId("wheat")
    private val ten = Money.units(10)

    private fun position(side: Side = Side.LONG, contracts: Long = 2, entry: Money = ten, margin: Money = Money.units(300), tenor: Int = 30, at: Long = 0) =
        s.open(me, wheat, side, contracts, entry, margin, Money.ZERO, Tick(at), tenor)

    @Test fun theFuturesPriceAddsCarryForTheDaysLeft() {
        assertEquals(ten, s.futuresPrice(ten, 0))
        assertEquals(Money(ten.raw + ten.raw * 40_000L * 90 / (360L * 1_000_000L)), s.futuresPrice(ten, 90))
        assertTrue(s.futuresPrice(ten, 90) > s.futuresPrice(ten, 30))
    }

    @Test fun buyersPayMoreThanSellersGet() {
        val buy = s.entryPrice(ten, 60, Side.LONG); val sell = s.entryPrice(ten, 60, Side.SHORT); val fair = s.futuresPrice(ten, 60)
        assertTrue(buy > fair && sell < fair)
        assertEquals(buy.raw - fair.raw, fair.raw - sell.raw)
    }

    @Test fun aQuoteScalesWithContractsAndSplitsIntoMarginAndFee() {
        val one = s.quote(ten, 30, Side.LONG, 1); val three = s.quote(ten, 30, Side.LONG, 3)
        assertEquals(one.price, three.price)
        assertEquals(one.notional.raw * 3, three.notional.raw)
        assertEquals(s.initialMargin(one.notional), one.margin)
        assertEquals(one.margin + one.fee, one.cashNeeded)
        assertTrue(one.margin.raw * 1_000_000L / one.notional.raw in 149_000L..151_000L)
    }

    @Test fun aLongGainsWhenPricesRiseAndAShortWhenTheyFall() {
        val up = Money.units(12); val down = Money.units(8)
        val size = s.params.contractSize.raw / 1000L
        assertEquals(Money.units(2 * size * 2), s.pnl(Side.LONG, ten, up, 2))
        assertEquals(Money.units(-2 * size * 2), s.pnl(Side.LONG, ten, down, 2))
        assertEquals(Money.units(-2 * size * 2), s.pnl(Side.SHORT, ten, up, 2))
        assertEquals(Money.units(2 * size * 2), s.pnl(Side.SHORT, ten, down, 2))
        assertEquals(Money.ZERO, s.pnl(Side.SHORT, ten, ten, 5))
    }

    @Test fun equityIsMarginPlusTheOpenResult() {
        val p = position(); s.markTo(p, Money.units(11))
        assertEquals(s.unrealised(p), s.pnl(Side.LONG, ten, Money.units(11), 2))
        assertEquals(p.margin + s.unrealised(p), s.equity(p))
    }

    @Test fun aFallingLongFirstGetsACallThenIsLiquidated() {
        val p = position(margin = s.initialMargin(s.notional(ten, 2)))
        assertFalse(s.needsCall(p)); assertFalse(s.mustLiquidate(p))
        s.markTo(p, Money(ten.raw * 92 / 100)); // 8% down against 15% margin leaves 7% of value, under the 8% floor
        assertTrue(s.needsCall(p)); assertFalse(s.mustLiquidate(p))
        s.markTo(p, Money(ten.raw * 88 / 100))
        assertTrue(s.mustLiquidate(p))
    }

    @Test fun theShortfallIsWhatLiftsEquityBackToTheFloor() {
        val p = position(margin = s.initialMargin(s.notional(ten, 2)))
        s.markTo(p, Money(ten.raw * 92 / 100))
        val need = s.shortfall(p)
        assertTrue(need.raw > 0L)
        s.addMargin(p, need)
        assertFalse(s.needsCall(p))
        assertEquals(Money.ZERO, s.shortfall(p))
    }

    @Test fun addingMarginClearsAnOutstandingCall() {
        val p = position(margin = s.initialMargin(s.notional(ten, 2)))
        s.markTo(p, Money(ten.raw * 92 / 100)); s.call(p, Tick(5))
        assertNotNull(p.calledAt)
        s.addMargin(p, Money.units(500))
        assertNull(p.calledAt)
    }

    @Test fun aCallIsRaisedOnlyOnce() {
        val p = position()
        assertTrue(s.call(p, Tick(5))); assertFalse(s.call(p, Tick(6)))
        s.clearCall(p); assertTrue(s.call(p, Tick(7)))
    }

    @Test fun marginMustBePositiveAndTheCardOpen() {
        val p = position()
        assertEquals(Rules.INVALID_ARGUMENT, (s.addMargin(p, Money.ZERO) as Outcome.Rejected).rejection.rule)
        s.finish(p, PositionStatus.CLOSED, Tick(3), Money.ZERO)
        assertEquals(Rules.WRONG_STATE, (s.addMargin(p, Money.units(5)) as Outcome.Rejected).rejection.rule)
    }

    @Test fun openingChecksTenorSizeAndCount() {
        assertNull(s.checkOpen(me, 30, 5))
        assertEquals(Rules.INVALID_ARGUMENT, s.checkOpen(me, 45, 5)!!.rule)
        assertEquals(Rules.INVALID_ARGUMENT, s.checkOpen(me, 30, 0)!!.rule)
        assertEquals(Rules.LIMIT_EXCEEDED, s.checkOpen(me, 30, s.params.maxContracts + 1)!!.rule)
        repeat(s.params.maxOpenPerCompany) { position() }
        assertEquals(Rules.LIMIT_EXCEEDED, s.checkOpen(me, 30, 1)!!.rule)
        assertNull(s.checkOpen(CompanyId(2), 30, 1))
    }

    @Test fun finishingFreesTheSlotAndZeroesTheMargin() {
        val p = position()
        s.finish(p, PositionStatus.EXPIRED, Tick(30), Money.units(7))
        assertFalse(p.isOpen); assertEquals(Money.ZERO, p.margin); assertEquals(Money.units(7), p.realised)
        assertTrue(s.openOf(me).isEmpty()); assertEquals(listOf(p), s.historyOf(me))
    }

    @Test fun oldClosedPositionsAreDroppedButOpenOnesStay() {
        val old = position(); val open = position()
        s.finish(old, PositionStatus.CLOSED, Tick(10), Money.ZERO)
        s.prune(Tick(10 + s.params.historyDays.toLong() - 1)); assertNotNull(s.get(old.id))
        s.prune(Tick(10 + s.params.historyDays.toLong() + 1))
        assertNull(s.get(old.id)); assertNotNull(s.get(open.id))
    }

    @Test fun daysLeftNeverGoesNegative() {
        val p = position(tenor = 30, at = 0)
        assertEquals(30, s.daysLeft(p, Tick(0))); assertEquals(0, s.daysLeft(p, Tick(40)))
    }

    @Test fun theHashChangesWithEveryField() {
        fun h(f: FuturesService) = app.many.economytycoon.core.CanonicalHasher().also { f.hashInto(it) }.finish()
        val a = FuturesService(IdAllocators()); val b = FuturesService(IdAllocators())
        assertEquals(h(a), h(b))
        val pa = a.open(me, wheat, Side.LONG, 2, ten, Money.units(100), Money.ZERO, Tick(0), 30)
        b.open(me, wheat, Side.LONG, 2, ten, Money.units(100), Money.ZERO, Tick(0), 30)
        assertEquals(h(a), h(b))
        a.markTo(pa, Money.units(11))
        assertTrue(h(a) != h(b))
    }
}
