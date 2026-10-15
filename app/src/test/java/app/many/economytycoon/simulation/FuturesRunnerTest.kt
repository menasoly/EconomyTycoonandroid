package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.futures.FuturesEvent
import app.many.economytycoon.futures.Position
import app.many.economytycoon.futures.PositionStatus
import app.many.economytycoon.futures.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturesRunnerTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val run = e.futuresRunner
        val country = e.world.countries().first()
        val wheat = GoodId("wheat")
        val ref = e.content.good(wheat).referencePrice.perUnit
        val me = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Hedge Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val owner = e.companies.company(me)!!.owner
        val ledger = e.finance.ledger
        val tick get() = Tick(e.tick.value)
        fun cash() = ledger.cash(owner)
        fun invested() = ledger.balance(owner, Chart.INVESTMENTS)
        fun spot(m: Money) = FuturesTestSupport.setSpot(e, wheat, m)
        fun open(side: Side = Side.LONG, contracts: Long = 5, tenor: Int = 30): Position {
            spot(ref)
            return (run.open(me, wheat, side, contracts, tenor, tick) as Outcome.Ok).value
        }
        fun at(day: Long) { e.tick = Tick(day) }
        fun drain(leave: Money) {
            val gov = app.many.economytycoon.finance.LedgerOwner.government(country.id)
            val take = cash() - leave
            e.finance.ledger.post(app.many.economytycoon.finance.TransactionDraft("drain:${e.tick.value}", e.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER, listOf(
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(owner, Chart.CASH), -take),
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, Chart.CASH), take)), "drain"))
        }
    }

    @Test fun openingLocksMarginInInvestmentsAndPaysTheFee() {
        val r = Rig(); val before = r.cash(); val q = r.run.quote(r.wheat, Side.LONG, 5, 30)
        val p = r.open()
        assertEquals(q.price, p.entry); assertEquals(q.margin, p.margin); assertEquals(q.fee, p.feePaid)
        assertEquals(before - q.cashNeeded, r.cash()); assertEquals(q.margin, r.invested())
        assertEquals(q.fee, r.ledger.balance(r.run.clearing, Chart.REVENUE_OTHER).let { Money(-it.raw) })
        assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun theClearingHouseIsFundedOnceOnFirstUse() {
        val r = Rig(); assertFalse(r.e.futures.clearingFunded)
        r.open(); assertTrue(r.e.futures.clearingFunded)
        val cash = r.ledger.cash(r.run.clearing)
        r.open(); assertTrue(r.ledger.cash(r.run.clearing) >= cash)
        assertTrue(r.ledger.cash(r.run.clearing) < r.e.futures.params.clearingReserve * 2)
    }

    @Test fun anUnaffordablePositionIsRejectedWithoutSideEffects() {
        val r = Rig(); r.drain(Money.units(10))
        val res = r.run.open(r.me, r.wheat, Side.LONG, 20, 30, r.tick)
        assertEquals(Rules.INSUFFICIENT_FUNDS, (res as Outcome.Rejected).rejection.rule)
        assertTrue(r.e.futures.all().isEmpty()); assertEquals(Money.units(10), r.cash())
    }

    @Test fun unknownGoodsAndBadTenorsAreRejected() {
        val r = Rig()
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.open(r.me, GoodId("nothing"), Side.LONG, 1, 30, r.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_ARGUMENT, (r.run.open(r.me, r.wheat, Side.LONG, 1, 45, r.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.open(CompanyId(999), r.wheat, Side.LONG, 1, 30, r.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun aLongThatClosesAfterARiseKeepsTheGain() {
        val r = Rig(); val p = r.open(Side.LONG, 5); val cash = r.cash()
        r.spot(Money(r.ref.raw * 11 / 10))
        val result = (r.run.close(r.me, p.id, r.tick) as Outcome.Ok).value
        assertTrue(result.raw > 0L)
        assertEquals(cash + marginOf(p, r) + result, r.cash())
        assertEquals(PositionStatus.CLOSED, p.status); assertEquals(result, p.realised); assertEquals(Money.ZERO, r.invested())
        assertEquals(0L, r.ledger.trialBalance())
    }

    private fun marginOf(p: Position, r: Rig) = r.e.futures.initialMargin(r.e.futures.notional(p.entry, p.contracts))

    @Test fun aShortThatClosesAfterARiseTakesTheLoss() {
        val r = Rig(); val p = r.open(Side.SHORT, 5); val cash = r.cash(); val margin = p.margin
        r.spot(Money(r.ref.raw * 105 / 100))
        val result = (r.run.close(r.me, p.id, r.tick) as Outcome.Ok).value
        assertTrue(result.raw < 0L)
        assertEquals(cash + margin + result, r.cash())
        assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun closingAtTheSamePriceCostsOnlyTheSpread() {
        val r = Rig(); val p = r.open(Side.LONG, 5)
        val result = (r.run.close(r.me, p.id, r.tick) as Outcome.Ok).value
        assertTrue(result.raw < 0L)
        val cost = r.e.futures.notional(p.entry, 5).raw
        assertTrue(-result.raw < cost * 2 * (r.e.futures.params.spreadPpm + 5_000L) / 1_000_000L)
    }

    @Test fun onlyTheOwnerCanCloseAndOnlyOnce() {
        val r = Rig(); val p = r.open()
        val other = CompanyId((r.sim.execute(Actor.Player(2), Command.FoundCompany("Other", r.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        assertEquals(Rules.NOT_OWNER, (r.run.close(other, p.id, r.tick) as Outcome.Rejected).rejection.rule)
        assertTrue(r.run.close(r.me, p.id, r.tick) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, (r.run.close(r.me, p.id, r.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun addingMarginMovesCashIntoTheInvestment() {
        val r = Rig(); val p = r.open(); val cash = r.cash(); val inv = r.invested()
        assertEquals(p.margin + Money.units(1_000), (r.run.addMargin(r.me, p.id, Money.units(1_000), r.tick) as Outcome.Ok).value)
        assertEquals(cash - Money.units(1_000), r.cash()); assertEquals(inv + Money.units(1_000), r.invested())
    }

    @Test fun addingMoreMarginThanYouHaveFails() {
        val r = Rig(); val p = r.open()
        assertEquals(Rules.INSUFFICIENT_FUNDS, (r.run.addMargin(r.me, p.id, Money.units(50_000_000), r.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun aFallingPriceBringsOneMarginCallThenAClosingOut() {
        val r = Rig(); val p = r.open(Side.LONG, 5)
        r.at(1); r.spot(Money(r.ref.raw * 92 / 100))
        val first = r.run.run(Tick(1))
        assertTrue(first.any { it is FuturesEvent.MarginCall && it.position == p.id }); assertNotNull(p.calledAt)
        r.at(2); assertTrue(r.run.run(Tick(2)).none { it is FuturesEvent.MarginCall })
        r.at(3); r.spot(Money(r.ref.raw * 85 / 100))
        val last = r.run.run(Tick(3))
        val liq = last.filterIsInstance<FuturesEvent.Liquidated>().single()
        assertEquals(p.id, liq.position); assertEquals(PositionStatus.LIQUIDATED, p.status)
        assertTrue(liq.loss.raw > 0L); assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun aClosingOutNeverTakesMoreThanTheMargin() {
        val r = Rig(); val p = r.open(Side.LONG, 5); val cash = r.cash(); val margin = p.margin
        r.at(1); r.spot(Money(r.ref.raw * 30 / 100))
        r.run.run(Tick(1))
        assertEquals(PositionStatus.LIQUIDATED, p.status)
        assertEquals(-margin.raw, p.realised.raw)
        assertEquals(cash, r.cash())
    }

    @Test fun toppingUpInTimeAvoidsTheClosingOut() {
        val r = Rig(); val p = r.open(Side.LONG, 5)
        r.at(1); r.spot(Money(r.ref.raw * 92 / 100)); r.run.run(Tick(1))
        r.run.addMargin(r.me, p.id, r.e.futures.shortfall(p) + Money.units(500), r.tick)
        assertNull(p.calledAt)
        r.at(2); r.run.run(Tick(2))
        assertTrue(p.isOpen)
    }

    @Test fun aPositionSettlesInCashAtExpiryAtTheSpotPrice() {
        val r = Rig(); val p = r.open(Side.SHORT, 5, 30); val margin = p.margin; val cash = r.cash()
        r.at(30); r.spot(Money(r.ref.raw * 94 / 100))
        val events = r.run.run(Tick(30))
        val ev = events.filterIsInstance<FuturesEvent.Expired>().single()
        assertEquals(PositionStatus.EXPIRED, p.status); assertEquals(ev.realised, p.realised)
        assertEquals(r.e.futures.pnl(p, Money(r.ref.raw * 94 / 100)), p.realised)
        assertEquals(cash + margin + p.realised, r.cash())
        assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun theMarkFollowsSpotPlusCarryAndConvergesToSpot() {
        val r = Rig(); val p = r.open(Side.LONG, 2, 60)
        r.at(1); r.spot(r.ref); r.run.run(Tick(1))
        assertEquals(r.e.futures.futuresPrice(r.ref, 59), p.mark)
        r.at(59); r.run.run(Tick(59))
        assertEquals(r.e.futures.futuresPrice(r.ref, 1), p.mark)
    }

    @Test fun theClearingHouseTopsItselfUpToPayABigWinner() {
        val r = Rig(); val p = r.open(Side.SHORT, 40, 30)
        r.drain(Money.ZERO)
        val clearing = r.run.clearing
        val gov = app.many.economytycoon.finance.LedgerOwner.government(r.country.id)
        val held = r.ledger.cash(clearing)
        r.ledger.post(app.many.economytycoon.finance.TransactionDraft("drain:clearing", r.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER, listOf(
            app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(clearing, Chart.CASH), -held),
            app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(gov, Chart.CASH), held)), "drain"))
        assertEquals(Money.ZERO, r.ledger.cash(clearing))
        val supply = r.ledger.moneySupply()
        r.spot(Money(r.ref.raw / 4))
        val result = (r.run.close(r.me, p.id, r.tick) as Outcome.Ok).value
        assertTrue(result.raw > 0L)
        assertTrue(r.ledger.cash(clearing).raw >= 0L)
        assertEquals(p.realised, result)
        assertEquals(0L, r.ledger.trialBalance())
        assertTrue(r.ledger.moneySupply() >= supply)
    }

    @Test fun retiringACompanyClosesItsPositionsAndReleasesMargin() {
        val r = Rig(); val a = r.open(Side.LONG, 3); val b = r.open(Side.SHORT, 3)
        r.run.forget(r.me, r.tick)
        assertFalse(a.isOpen || b.isOpen); assertEquals(Money.ZERO, r.invested())
        assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun worthIsTheSumOfEquity() {
        val r = Rig(); val a = r.open(Side.LONG, 3); val b = r.open(Side.SHORT, 2)
        r.at(1); r.spot(Money(r.ref.raw * 103 / 100)); r.run.run(Tick(1))
        assertEquals(r.e.futures.equity(a) + r.e.futures.equity(b), r.run.worth(r.me))
        assertEquals(Money.ZERO, r.run.worth(CompanyId(999)))
    }

    @Test fun spotFallsBackToTheReferencePriceBeforeAnyTrade() {
        val r = Rig()
        assertTrue(r.run.spot(r.wheat).raw > 0L)
    }

    @Test fun theCommandPathWorksEndToEnd() {
        val r = Rig(); r.spot(r.ref)
        val opened = r.sim.execute(Actor.Player(1), Command.OpenFuture(r.me, r.wheat, Side.SHORT, 4, 60)) as Outcome.Ok
        val id = opened.value.created!!
        assertTrue(r.sim.execute(Actor.Player(1), Command.AddMargin(r.me, id, Money.units(2_000))) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(1), Command.CloseFuture(r.me, id)) is Outcome.Ok)
        assertFalse(r.sim.execute(Actor.Player(2), Command.CloseFuture(r.me, id)) is Outcome.Ok)
    }
}
