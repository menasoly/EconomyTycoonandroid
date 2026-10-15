package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.property.PropertyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyRunnerTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val run = e.propertyRunner
        val country = e.world.countries().first()
        val city = e.world.citiesOf(country.id).first()
        val ledger = e.finance.ledger
        fun found(name: String, n: Long = 1, capital: Long = 1_000_000): CompanyId =
            CompanyId((sim.execute(Actor.Player(n), Command.FoundCompany(name, country.id, null, Money.units(capital))) as Outcome.Ok).value.created!!)
        val seller = found("Land Co", 1)
        val buyer = found("Buyer Co", 2)
        fun owner(c: CompanyId) = e.companies.company(c)!!.owner
        fun cash(c: CompanyId) = ledger.cash(owner(c))
        fun bank(c: CompanyId) = ledger.balance(owner(c), Chart.INVESTMENTS)
        fun buyPlot(c: CompanyId = seller): ParcelId {
            val p = e.world.land.available(city.id).filter { run.appraise(it).raw > 0L }.minByOrNull { run.appraise(it).raw }!!
            assertTrue(sim.execute(Actor.Player(c.value), Command.BuyParcel(c, p.id)) is Outcome.Ok)
            return p.id
        }
        fun parcel(id: ParcelId) = e.world.land.get(id)!!
        fun ask(id: ParcelId, ppm: Long = 1_000_000L) = Money(run.appraise(parcel(id)).raw * ppm / 1_000_000L)
        fun day(d: Long) { e.tick = Tick(d) }
        fun lister(id: ParcelId, ppm: Long = 1_000_000L) = run.list(seller, id, ask(id, ppm), e.tick)
    }

    @Test fun buyingLandNowBooksItAsAnInvestmentNotAnExpense() {
        val r = Rig(); val before = r.cash(r.seller); val plot = r.buyPlot()
        assertEquals(r.parcel(plot).lastPrice, r.bank(r.seller))
        assertEquals(before - r.parcel(plot).lastPrice, r.cash(r.seller))
        assertEquals(r.e.tick, r.e.property.heldSince(plot))
        assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun appraisalsArePositiveForOpenLand() {
        val r = Rig(); val plot = r.buyPlot()
        assertTrue(r.run.appraise(r.parcel(plot)).raw > 0L)
    }

    @Test fun listingNeedsOwnershipAnOpenPlotAndASanePrice() {
        val r = Rig(); val plot = r.buyPlot()
        assertEquals(Rules.NOT_OWNER, (r.run.list(r.buyer, plot, r.ask(plot), r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.list(r.seller, ParcelId(999_999), Money.units(1), r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_PRICE, (r.run.list(r.seller, plot, Money(r.ask(plot).raw * 10), r.e.tick) as Outcome.Rejected).rejection.rule)
        assertTrue(r.lister(plot) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, (r.lister(plot) as Outcome.Rejected).rejection.rule)
    }

    @Test fun aBuiltOnPlotCannotBeListedOrLet() {
        val r = Rig(); val plot = r.buyPlot()
        r.e.production.grantFacility(r.seller, FacilityTypeId("grain_farm"), r.parcel(plot).region, r.parcel(plot).city, plot, null, r.e.tick)
        assertEquals(Rules.WRONG_STATE, (r.lister(plot) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.WRONG_STATE, (r.run.offerLease(r.seller, plot, 90, Money.units(100), r.e.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun aSaleMovesTheLandCashTaxAndTheLedgerBalances() {
        val r = Rig(); val plot = r.buyPlot(); val paid = r.parcel(plot).lastPrice
        val ask = r.ask(plot, 1_200_000L); r.run.list(r.seller, plot, ask, r.e.tick)
        val sellerCash = r.cash(r.seller); val buyerCash = r.cash(r.buyer)
        val gov = LedgerOwner.government(r.country.id); val govCash = r.ledger.cash(gov)
        val tax = r.e.property.transferTax(ask)
        val sale = (r.run.buy(r.buyer, plot, r.e.tick) as Outcome.Ok).value
        assertEquals(ask, sale.price)
        assertEquals(r.buyer, r.parcel(plot).owner); assertEquals(ask, r.parcel(plot).lastPrice)
        assertEquals(sellerCash + ask, r.cash(r.seller)); assertEquals(buyerCash - ask - tax, r.cash(r.buyer)); assertEquals(govCash + tax, r.ledger.cash(gov))
        assertEquals(Money.ZERO, r.bank(r.seller)); assertEquals(ask, r.bank(r.buyer))
        assertTrue(ask > paid)
        assertNull(r.e.property.listing(plot)); assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun aSaleAtALossBooksTheLossAndStillBalances() {
        val r = Rig(); val plot = r.buyPlot()
        r.run.list(r.seller, plot, r.ask(plot, 650_000L), r.e.tick)
        assertTrue(r.run.buy(r.buyer, plot, r.e.tick) is Outcome.Ok)
        assertEquals(0L, r.ledger.trialBalance()); assertEquals(Money.ZERO, r.bank(r.seller))
    }

    @Test fun buyingNeedsCashIncludingTheTaxAndAnotherSeller() {
        val r = Rig(); val plot = r.buyPlot(); r.lister(plot)
        assertEquals(Rules.INVALID_ARGUMENT, (r.run.buy(r.seller, plot, r.e.tick) as Outcome.Rejected).rejection.rule)
        val poor = r.found("Poor Co", 3, 100_000)
        val gov = LedgerOwner.government(r.country.id); val take = r.cash(poor) - Money.units(10)
        r.ledger.post(TransactionDraft("drain", r.e.tick, ReasonCode.TRANSFER, listOf(Entry(AccountRef(r.owner(poor), Chart.CASH), -take), Entry(AccountRef(gov, Chart.CASH), take)), "d"))
        assertEquals(Rules.INSUFFICIENT_FUNDS, (r.run.buy(poor, plot, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.buy(r.buyer, ParcelId(999_999), r.e.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun delistingIsForTheSellerOnly() {
        val r = Rig(); val plot = r.buyPlot(); r.lister(plot)
        assertEquals(Rules.NOT_OWNER, (r.run.delist(r.buyer, plot) as Outcome.Rejected).rejection.rule)
        assertTrue(r.run.delist(r.seller, plot) is Outcome.Ok)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.delist(r.seller, plot) as Outcome.Rejected).rejection.rule)
    }

    @Test fun aListingLapsesAfterItsTime() {
        val r = Rig(); val plot = r.buyPlot(); r.lister(plot)
        val expires = r.e.property.listing(plot)!!.expiresAt
        assertTrue(r.run.run(Tick(expires.value - 1)).none { it is PropertyEvent.ListingLapsed })
        assertTrue(r.run.run(expires).any { it is PropertyEvent.ListingLapsed && it.parcel == plot })
        assertNull(r.e.property.listing(plot)); assertEquals(r.seller, r.parcel(plot).owner)
    }

    @Test fun listedLandCannotBeBuiltOn() {
        val r = Rig(); val plot = r.buyPlot(); r.lister(plot)
        val res = r.sim.execute(Actor.Player(1), Command.Build(r.seller, FacilityTypeId("grain_farm"), r.parcel(plot).region, r.parcel(plot).city, plot, null, "x"))
        assertFalse(res is Outcome.Ok)
    }

    @Test fun anOfferFindsATenantAndRentFlowsFromHouseholds() {
        val r = Rig(); val plot = r.buyPlot()
        val rent = r.e.property.minRent(r.run.appraise(r.parcel(plot)))
        assertTrue(r.run.offerLease(r.seller, plot, 90, rent, r.e.tick) is Outcome.Ok)
        var started: PropertyEvent.LeaseStarted? = null
        var d = 0L
        while (started == null && d < 400) { d++; started = r.run.run(Tick(d)).filterIsInstance<PropertyEvent.LeaseStarted>().firstOrNull() }
        assertNotNull(started)
        val lease = r.e.property.leaseOf(plot)!!
        val before = r.cash(r.seller); val hh = r.ledger.cash(r.e.finance.households)
        r.run.run(lease.nextRent)
        assertEquals(before + rent, r.cash(r.seller)); assertEquals(hh - rent, r.ledger.cash(r.e.finance.households))
        assertEquals(rent, lease.collected); assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun anAbsurdRentNeverFindsATenantQuickly() {
        val r = Rig(); val plot = r.buyPlot()
        r.run.offerLease(r.seller, plot, 90, r.e.property.maxRent(r.run.appraise(r.parcel(plot))), r.e.tick)
        var started = 0
        for (d in 1L..r.e.property.params.offerDays.toLong()) started += r.run.run(Tick(d)).count { it is PropertyEvent.LeaseStarted }
        assertTrue(started <= 1)
    }

    @Test fun anUnansweredOfferLapses() {
        val r = Rig(); val plot = r.buyPlot()
        r.run.offerLease(r.seller, plot, 90, r.e.property.maxRent(r.run.appraise(r.parcel(plot))), r.e.tick)
        val until = r.e.property.offer(plot)!!.expiresAt
        r.run.run(Tick(until.value + 1))
        if (r.e.property.leaseOf(plot) == null) assertNull(r.e.property.offer(plot))
    }

    @Test fun aLeaseRunsOutAndFreesThePlot() {
        val r = Rig(); val plot = r.buyPlot()
        r.e.property.startLease(r.e.property.addOffer(plot, r.seller, 90, Money.units(300), r.e.tick), 2, r.e.tick)
        val lease = r.e.property.leaseOf(plot)!!
        val ev = r.run.run(lease.endsAt).filterIsInstance<PropertyEvent.LeaseEnded>().single()
        assertEquals(lease.id, ev.lease); assertNull(r.e.property.leaseOf(plot)); assertFalse(r.e.property.encumbered(plot))
    }

    @Test fun missedRentTwiceEndsTheLease() {
        val r = Rig(); val plot = r.buyPlot()
        val lease = r.e.property.startLease(r.e.property.addOffer(plot, r.seller, 360, Money.units(300), r.e.tick), 1, r.e.tick)
        val hh = r.e.finance.households; val gov = LedgerOwner.government(r.country.id)
        val take = r.ledger.cash(hh)
        r.ledger.post(TransactionDraft("drain-hh", r.e.tick, ReasonCode.TRANSFER, listOf(Entry(AccountRef(hh, Chart.CASH), -take), Entry(AccountRef(gov, Chart.CASH), take)), "d"))
        val first = r.run.run(lease.nextRent)
        assertTrue(first.any { it is PropertyEvent.RentMissed } && lease.isActive)
        val second = r.run.run(lease.nextRent)
        assertTrue(second.any { it is PropertyEvent.RentMissed } && second.any { it is PropertyEvent.LeaseEnded })
        assertFalse(lease.isActive)
    }

    @Test fun theLandlordCanBuyOutALeaseForTwoMonthsRent() {
        val r = Rig(); val plot = r.buyPlot()
        r.e.property.startLease(r.e.property.addOffer(plot, r.seller, 180, Money.units(300), r.e.tick), 1, r.e.tick)
        val cash = r.cash(r.seller)
        assertEquals(Rules.NOT_OWNER, (r.run.endLease(r.buyer, plot, r.e.tick) as Outcome.Rejected).rejection.rule)
        val cost = (r.run.endLease(r.seller, plot, r.e.tick) as Outcome.Ok).value
        assertEquals(Money.units(600), cost); assertEquals(cash - cost, r.cash(r.seller))
        assertNull(r.e.property.leaseOf(plot)); assertEquals(0L, r.ledger.trialBalance())
    }

    @Test fun withdrawingAnOfferIsForTheLandlordOnly() {
        val r = Rig(); val plot = r.buyPlot()
        r.run.offerLease(r.seller, plot, 90, r.e.property.fairRent(r.run.appraise(r.parcel(plot))), r.e.tick)
        assertEquals(Rules.NOT_OWNER, (r.run.withdrawOffer(r.buyer, plot) as Outcome.Rejected).rejection.rule)
        assertTrue(r.run.withdrawOffer(r.seller, plot) is Outcome.Ok)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.withdrawOffer(r.seller, plot) as Outcome.Rejected).rejection.rule)
    }

    @Test fun retiringACompanyClearsItsListingsOffersAndLeases() {
        val r = Rig(); val a = r.buyPlot(); val b = r.buyPlot(); val c = r.buyPlot()
        r.lister(a)
        r.run.offerLease(r.seller, b, 90, r.e.property.fairRent(r.run.appraise(r.parcel(b))), r.e.tick)
        r.e.property.startLease(r.e.property.addOffer(c, r.seller, 90, Money.units(100), r.e.tick), 0, r.e.tick)
        r.run.forget(r.seller, r.e.tick)
        assertTrue(r.e.property.listingsOf(r.seller).isEmpty() && r.e.property.offersOf(r.seller).isEmpty() && r.e.property.leasesOf(r.seller).isEmpty())
    }

    @Test fun aStaleListingAfterAnOwnerChangeIsDropped() {
        val r = Rig(); val plot = r.buyPlot(); r.lister(plot)
        r.e.world.land.transfer(plot, r.buyer, Money.units(1))
        assertEquals(Rules.WRONG_STATE, (r.run.buy(r.buyer, plot, r.e.tick) as Outcome.Rejected).rejection.rule)
        assertNull(r.e.property.listing(plot))
    }

    @Test fun incomeAndLandValueSummarise() {
        val r = Rig(); val a = r.buyPlot(); r.buyPlot()
        r.e.property.startLease(r.e.property.addOffer(a, r.seller, 90, Money.units(300), r.e.tick), 0, r.e.tick)
        assertEquals(Money.units(300), r.run.monthlyIncome(r.seller))
        assertTrue(r.run.landValue(r.seller) > r.run.appraise(r.parcel(a)))
        assertEquals(Money.ZERO, r.run.monthlyIncome(r.buyer))
    }

    @Test fun theCommandsWorkEndToEnd() {
        val r = Rig(); val plot = r.buyPlot()
        val ask = r.ask(plot, 1_050_000L)
        assertTrue(r.sim.execute(Actor.Player(1), Command.ListParcel(r.seller, plot, ask)) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(1), Command.DelistParcel(r.seller, plot)) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(1), Command.ListParcel(r.seller, plot, ask)) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(2), Command.BuyListed(r.buyer, plot)) is Outcome.Ok)
        val fair = r.e.property.fairRent(r.run.appraise(r.parcel(plot)))
        assertTrue(r.sim.execute(Actor.Player(2), Command.OfferLease(r.buyer, plot, 180, fair)) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(2), Command.WithdrawLease(r.buyer, plot)) is Outcome.Ok)
        assertFalse(r.sim.execute(Actor.Player(1), Command.EndLease(r.seller, plot)) is Outcome.Ok)
    }
}
