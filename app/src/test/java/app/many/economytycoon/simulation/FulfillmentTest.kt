package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.markets.MarketReport
import app.many.economytycoon.markets.Party
import app.many.economytycoon.markets.Side
import app.many.economytycoon.markets.Trade
import app.many.economytycoon.production.Facility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FulfillmentTest {
    private val wheat = GoodId("wheat")

    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        fun company(name: String, capital: Money = Money.units(300_000)): CompanyId {
            val r = sim.execute(Actor.Player(name.length.toLong() + name.hashCode()), Command.FoundCompany(name, country.id, null, capital)) as Outcome.Ok
            return CompanyId(r.value.created!!)
        }
        fun farm(owner: CompanyId): Facility = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    }

    private fun trade(r: Rig, buyer: Party, seller: CompanyId, sellerFacility: Facility, buyerSource: Long, qty: Quantity, price: Price): MarketReport {
        val report = MarketReport(r.e.tick)
        report.trades += Trade(
            r.e.ids.trade(), MarketKey(r.region, GoodId("wheat")), buyer, Party.company(seller), qty, price, r.e.tick,
            null, null, buyerSource, sellerFacility.id.value,
        )
        return report
    }

    @Test fun aCompanyTradeMovesGoodsAndMoney() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000))
        fa.inventory.reserve(wheat, Quantity.units(40))
        val cashA = r.e.finance.ledger.cash(LedgerOwner.company(a)); val cashB = r.e.finance.ledger.cash(LedgerOwner.company(b))
        val rep = Fulfillment(r.e).run(trade(r, Party.company(b), a, fa, fb.id.value, Quantity.units(40), Price(Money.units(20))))
        assertEquals(1, rep.executed)
        assertEquals(Quantity.units(60), fa.inventory.quantity(wheat))
        assertEquals(Quantity.units(40), fb.inventory.quantity(wheat))
        assertEquals(cashA + Money.units(800), r.e.finance.ledger.cash(LedgerOwner.company(a)))
        assertEquals(cashB - Money.units(800), r.e.finance.ledger.cash(LedgerOwner.company(b)))
        assertEquals(Money.units(800), fb.inventory.totalValue())
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun aBuyerWithoutCashLeavesTheSellerWhole() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Broke Works", Money.units(50_000))
        val fa = r.farm(a); val fb = r.farm(b)
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000))
        fa.inventory.reserve(wheat, Quantity.units(40))
        val rep = Fulfillment(r.e).run(trade(r, Party.company(b), a, fa, fb.id.value, Quantity.units(40), Price(Money.units(5_000))))
        assertEquals(0, rep.executed)
        assertEquals(1, rep.skippedNoCash)
        assertEquals(Quantity.units(100), fa.inventory.quantity(wheat))
        assertEquals(Quantity.ZERO, fa.inventory.reserved(wheat))
    }

    @Test fun aFullBuyerStoreTakesOnlyWhatFits() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Cramped Works")
        val fa = r.farm(a); val fb = r.farm(b)
        fb.inventory.capacity = Quantity.units(10)
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000))
        fa.inventory.reserve(wheat, Quantity.units(40))
        Fulfillment(r.e).run(trade(r, Party.company(b), a, fa, fb.id.value, Quantity.units(40), Price(Money.units(20))))
        assertTrue(fb.inventory.quantity(wheat) < Quantity.units(40))
        assertEquals(Quantity.ZERO, fa.inventory.reserved(wheat))
        assertEquals(Quantity.units(100), fa.inventory.quantity(wheat) + fb.inventory.quantity(wheat))
    }

    @Test fun householdPurchasesChargeSalesTaxToTheSeller() {
        val r = Rig(); val a = r.company("Seller Works")
        val fa = r.farm(a)
        r.e.finance.mint(r.e.tick, r.e.finance.households, Money.units(100_000), "test")
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000))
        fa.inventory.reserve(wheat, Quantity.units(50))
        val rep = Fulfillment(r.e).run(trade(r, Party.households(r.region), a, fa, 0, Quantity.units(50), Price(Money.units(20))))
        assertEquals(Money.units(1_000), rep.consumerSpend)
        assertEquals(1, rep.taxIntents.size)
        assertTrue(rep.taxIntents.single().total.raw > 0)
        assertEquals(Quantity.units(50), fa.inventory.quantity(wheat))
        assertTrue(r.e.finance.ledger.balance(LedgerOwner.company(a), Chart.REVENUE_SALES).raw != 0L)
    }

    @Test fun tradesFromMissingSellersAreDropped() {
        val r = Rig(); val b = r.company("Buyer Works"); val a = r.company("Seller Works")
        val fa = r.farm(a); val fb = r.farm(b)
        val report = trade(r, Party.company(b), a, fa, fb.id.value, Quantity.units(10), Price(Money.units(20)))
        val bogus = MarketReport(r.e.tick)
        bogus.trades += report.trades.single().copy(sellerSource = 9_999)
        assertEquals(1, Fulfillment(r.e).run(bogus).skippedNoStock)
    }
}
