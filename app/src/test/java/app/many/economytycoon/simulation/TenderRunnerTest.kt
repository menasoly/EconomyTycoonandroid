package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.Facility
import app.many.economytycoon.tenders.Tender
import app.many.economytycoon.tenders.TenderEvent
import app.many.economytycoon.tenders.TenderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenderRunnerTest {
    private val wheat = GoodId("wheat")

    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val gov = LedgerOwner.government(country.id)
        init { e.finance.mint(e.tick, gov, Money.units(5_000_000), "test:treasury") }
        fun company(name: String, p: Long): CompanyId =
            CompanyId((sim.execute(Actor.Player(p), Command.FoundCompany(name, country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        fun farm(owner: CompanyId, stock: Long = 0): Facility = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick).also {
            if (stock > 0) it.inventory.add(GoodId("wheat"), Quantity.units(stock), Money.units(stock * 10))
        }
        val ref get() = e.content.good(GoodId("wheat")).referencePrice.perUnit
        fun tender(qty: Long = 50): Tender = (e.tenders.issue(country.id, GoodId("wheat"), Quantity.units(qty), Money(ref.raw * 13 / 10), e.tick) as Outcome.Ok).value
        fun cash(c: CompanyId) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun at(t: Long) { e.tick = Tick(t) }
    }

    @Test fun bidsNeedYourOwnLiveFacility() {
        val r = Rig(); val a = r.company("A Works", 1); val b = r.company("B Works", 2)
        val t = r.tender(); val mine = r.farm(a); val theirs = r.farm(b)
        val stolen = r.e.tenderRunner.bid(a, t.id, theirs.id, r.ref, r.e.tick) as Outcome.Rejected
        assertEquals(Rules.NOT_OWNER, stolen.rejection.rule)
        r.e.production.demolish(a, mine.id)
        assertEquals(Rules.WRONG_STATE, (r.e.tenderRunner.bid(a, t.id, mine.id, r.ref, r.e.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun theFloorIsAShareOfTheReferencePrice() {
        val r = Rig(); val t = r.tender()
        assertEquals(Money(r.ref.raw / 2), r.e.tenderRunner.floorFor(t))
        val a = r.company("A Works", 1); val f = r.farm(a)
        val low = r.e.tenderRunner.bid(a, t.id, f.id, Money(r.ref.raw / 4), r.e.tick) as Outcome.Rejected
        assertEquals(Rules.INVALID_PRICE, low.rejection.rule)
    }

    @Test fun theCheapestBidderWinsWhenBiddingCloses() {
        val r = Rig(); val a = r.company("A Works", 1); val b = r.company("B Works", 2)
        val t = r.tender(); val fa = r.farm(a, 100); val fb = r.farm(b, 100)
        r.e.tenderRunner.bid(a, t.id, fa.id, Money(r.ref.raw * 12 / 10), r.e.tick)
        r.e.tenderRunner.bid(b, t.id, fb.id, Money(r.ref.raw * 9 / 10), r.e.tick)
        r.at(t.closesAt.value)
        val events = r.e.tenderRunner.run(r.e.tick)
        assertEquals(b, (events.first { it is TenderEvent.Awarded } as TenderEvent.Awarded).winner)
    }

    @Test fun aBidderWhoLostTheirFacilityIsPassedOver() {
        val r = Rig(); val a = r.company("A Works", 1); val b = r.company("B Works", 2)
        val t = r.tender(); val fa = r.farm(a, 100); val fb = r.farm(b, 100)
        r.e.tenderRunner.bid(a, t.id, fa.id, Money(r.ref.raw * 8 / 10), r.e.tick)
        r.e.tenderRunner.bid(b, t.id, fb.id, Money(r.ref.raw * 11 / 10), r.e.tick)
        r.e.production.demolish(a, fa.id)
        r.at(t.closesAt.value)
        r.e.tenderRunner.run(r.e.tick)
        assertEquals(b, t.winner)
    }

    @Test fun deliveryMovesGoodsAndTheTreasuryPays() {
        val r = Rig(); val a = r.company("A Works", 1)
        val t = r.tender(50); val f = r.farm(a, 80)
        val price = Money(r.ref.raw * 9 / 10)
        r.e.tenderRunner.bid(a, t.id, f.id, price, r.e.tick)
        val cash = r.cash(a); val treasury = r.e.finance.ledger.cash(r.gov)
        r.at(t.closesAt.value)
        val events = r.e.tenderRunner.run(r.e.tick)
        val paid = (events.first { it is TenderEvent.Delivered } as TenderEvent.Delivered).paid
        assertEquals(t.awardedValue, paid)
        assertEquals(Quantity.units(30), f.inventory.quantity(wheat))
        assertEquals(cash + paid, r.cash(a))
        assertEquals(treasury - paid, r.e.finance.ledger.cash(r.gov))
        assertEquals(TenderStatus.DELIVERED, t.status)
        assertTrue(r.e.finance.invariants().toString(), r.e.finance.invariants().isEmpty())
    }

    @Test fun aWinnerWithoutStockHasUntilTheDeadline() {
        val r = Rig(); val a = r.company("A Works", 1)
        val t = r.tender(50); val f = r.farm(a, 10)
        r.e.tenderRunner.bid(a, t.id, f.id, Money(r.ref.raw), r.e.tick)
        r.at(t.closesAt.value)
        assertTrue(r.e.tenderRunner.run(r.e.tick).any { it is TenderEvent.Awarded })
        assertTrue(t.isAwarded)
        r.at(t.deliverBy.value - 1)
        assertTrue(r.e.tenderRunner.run(r.e.tick).isEmpty())
        f.inventory.add(wheat, Quantity.units(60), Money.units(600))
        r.at(t.deliverBy.value - 1)
        assertTrue(r.e.tenderRunner.run(r.e.tick).any { it is TenderEvent.Delivered })
    }

    @Test fun missingTheDeadlineCostsAPenaltyAndAMark() {
        val r = Rig(); val a = r.company("A Works", 1)
        val t = r.tender(50); val f = r.farm(a, 10)
        r.e.tenderRunner.bid(a, t.id, f.id, Money(r.ref.raw), r.e.tick)
        r.at(t.closesAt.value); r.e.tenderRunner.run(r.e.tick)
        val cash = r.cash(a); val treasury = r.e.finance.ledger.cash(r.gov)
        r.at(t.deliverBy.value)
        val failed = r.e.tenderRunner.run(r.e.tick).first { it is TenderEvent.Failed } as TenderEvent.Failed
        assertEquals(t.awardedValue.scale(r.e.tenders.params.penaltyPpm, 1_000_000L), failed.penalty)
        assertEquals(cash - failed.penalty, r.cash(a))
        assertEquals(treasury + failed.penalty, r.e.finance.ledger.cash(r.gov))
        assertEquals(1, r.e.tenders.failuresOf(a, r.e.tick))
        assertEquals(Quantity.units(10), f.inventory.quantity(wheat))
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun aShortTreasuryIsToppedUpSoTheWinnerIsAlwaysPaid() {
        val r = Rig(); val a = r.company("A Works", 1)
        val t = r.tender(50); val f = r.farm(a, 80)
        r.e.tenderRunner.bid(a, t.id, f.id, Money(r.ref.raw), r.e.tick)
        r.at(t.closesAt.value)
        val cash = r.e.finance.ledger.cash(r.gov)
        r.e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", r.e.tick, app.many.economytycoon.finance.ReasonCode.ADJUSTMENT,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.gov, app.many.economytycoon.finance.Chart.OTHER_EXPENSE), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.gov, app.many.economytycoon.finance.Chart.CASH), -cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.CASH), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -cash),
                ),
                "drain",
            ),
        )
        val before = r.cash(a)
        val events = r.e.tenderRunner.run(r.e.tick)
        val paid = (events.first { it is TenderEvent.Delivered } as TenderEvent.Delivered).paid
        assertEquals(before + paid, r.cash(a))
        assertEquals(Money.ZERO, r.e.finance.ledger.cash(r.gov))
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun noBidsMeansNoAward() {
        val r = Rig(); val t = r.tender()
        r.at(t.closesAt.value)
        assertTrue(r.e.tenderRunner.run(r.e.tick).any { it is TenderEvent.NoBids })
        assertEquals(TenderStatus.NO_BIDS, t.status)
    }

    @Test fun governmentsIssueTendersOnTheirOwnSchedule() {
        val r = Rig()
        var issued = 0
        for (day in 0 until 120) {
            r.at(day.toLong())
            issued += r.e.tenderRunner.run(r.e.tick).count { it is TenderEvent.Issued }
        }
        assertTrue("expected some tenders, got $issued", issued > 0)
        assertTrue(r.e.tenders.all().all { it.quantity.raw > 0 && it.ceiling.raw > 0 })
    }

    @Test fun theBudgetCapsWhatIsCommitted() {
        val r = Rig()
        val budget = r.e.tenderRunner.budgetFor(r.country.id)
        assertTrue(budget.raw > 0)
        // One tender that uses the whole budget leaves no room while it is open.
        val big = (r.e.tenders.issue(r.country.id, wheat, Quantity.units(1), budget, r.e.tick) as Outcome.Ok).value
        assertEquals(budget, r.e.tenders.committed(r.country.id))
        for (day in 0 until 7) {
            r.at(day.toLong())
            assertTrue(r.e.tenderRunner.run(r.e.tick).none { it is TenderEvent.Issued && it.country == r.country.id })
        }
        assertEquals(listOf(big), r.e.tenders.all().filter { it.country == r.country.id })
    }

    @Test fun deliveredTendersFreeTheirBudget() {
        val r = Rig(); val a = r.company("A Works", 1)
        val t = r.tender(50); val f = r.farm(a, 80)
        r.e.tenderRunner.bid(a, t.id, f.id, Money(r.ref.raw), r.e.tick)
        assertTrue(r.e.tenders.committed(r.country.id).raw > 0)
        r.at(t.closesAt.value); r.e.tenderRunner.run(r.e.tick)
        assertEquals(Money.ZERO, r.e.tenders.committed(r.country.id))
    }

    @Test fun commandsBidAndWithdrawThroughTheExecutor() {
        val r = Rig(); val a = r.company("A Works", 1)
        val t = r.tender(); val f = r.farm(a, 100)
        val placed = r.sim.execute(Actor.Player(1), Command.BidTender(a, t.id, f.id, Money(r.ref.raw)))
        assertTrue(placed.toString(), placed is Outcome.Ok)
        assertEquals(1, t.bids.size)
        assertTrue(r.sim.execute(Actor.Player(1), Command.WithdrawBid(a, t.id)) is Outcome.Ok)
        assertTrue(t.bids.isEmpty())
        assertFalse(r.sim.execute(Actor.Player(2), Command.BidTender(a, t.id, f.id, Money(r.ref.raw))) is Outcome.Ok)
    }

    @Test fun aLongGameRunsTendersToCompletionAndStaysBalanced() {
        fun play(): Pair<Int, String> {
            val r = Rig(); val a = r.company("A Works", 1)
            r.farm(a, 100_000)
            r.sim.advance(240)
            return r.e.tenders.all().size to r.sim.hash()
        }
        val x = play(); val y = play()
        assertEquals(x, y)
        assertTrue(x.first > 0)
    }
}
