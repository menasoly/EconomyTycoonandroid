package app.many.economytycoon.simulation

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.markets.Party
import app.many.economytycoon.markets.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandingRulesTest {
    private val wheat = GoodId("wheat")

    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val id = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Rule Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val farm = e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        val rules = StandingRules(e)
    }

    @Test fun sellRulesOfferStockAboveTheReserve() {
        val r = Rig()
        r.farm.inventory.add(wheat, Quantity.units(100), Money.units(2_000))
        r.e.companies.policies.setSell(r.id, SellRule(r.farm.id, wheat, PriceStrategy.MARKET, 0, reserve = Quantity.units(30)))
        r.e.tick = Tick(1)
        val rep = r.rules.plan(Tick(1))
        assertEquals(1, rep.sellOrders)
        val order = r.e.markets.openOrders(Party.company(r.id)).single()
        assertEquals(Side.SELL, order.side)
        assertEquals(Quantity.units(70), order.quantity)
        assertEquals(Quantity.units(70), r.farm.inventory.reserved(wheat))
    }

    @Test fun staleRuleOrdersAreReplacedRatherThanStacked() {
        val r = Rig()
        r.farm.inventory.add(wheat, Quantity.units(100), Money.units(2_000))
        r.e.companies.policies.setSell(r.id, SellRule(r.farm.id, wheat))
        r.e.tick = Tick(1)
        r.rules.plan(Tick(1))
        r.e.tick = Tick(2)
        val again = r.rules.plan(Tick(2))
        assertEquals(1, again.cancelledStale)
        assertEquals(1, r.e.markets.openOrders(Party.company(r.id)).size)
        assertEquals(Quantity.units(100), r.farm.inventory.reserved(wheat))
    }

    @Test fun buyRulesTopUpToTheTargetAndCountOrdersInFlight() {
        val r = Rig()
        r.farm.inventory.add(GoodId("seeds"), Quantity.units(20), Money.units(800))
        r.e.companies.policies.setBuy(r.id, BuyRule(r.farm.id, GoodId("seeds"), Quantity.units(50)))
        r.e.tick = Tick(1)
        r.rules.plan(Tick(1))
        val order = r.e.markets.openOrders(Party.company(r.id)).single()
        assertEquals(Side.BUY, order.side)
        assertEquals(Quantity.units(30), order.quantity)
        // Planning again in the same tick must not double the order.
        assertEquals(0, r.rules.plan(Tick(1)).let { it.buyOrders })
    }

    @Test fun buyOrdersAreLimitedByCash() {
        val r = Rig()
        r.e.companies.policies.setBuy(r.id, BuyRule(r.farm.id, GoodId("seeds"), Quantity.units(10_000_000)))
        r.rules.plan(Tick(1))
        val order = r.e.markets.openOrders(Party.company(r.id)).single()
        val worth = order.limit!!.valueOf(order.quantity)
        assertTrue(worth <= r.e.finance.ledger.cash(LedgerOwner.company(r.id)))
    }

    @Test fun rulesForDemolishedOrForeignFacilitiesArePassedOver() {
        val r = Rig()
        r.e.companies.policies.setSell(r.id, SellRule(app.many.economytycoon.core.FacilityId(999), wheat))
        assertEquals(0, r.rules.plan(Tick(1)).sellOrders)
    }

    @Test fun dividendsFollowThePolicyScheduleAndRespectDistributableProfit() {
        val r = Rig()
        r.e.companies.policies.setDividend(r.id, DividendPolicy(500_000, 0, 30))
        // No profit has been made, so nothing is distributable.
        assertEquals(Money.ZERO, r.rules.payDividends(Tick(30 - r.id.value)))
    }
}
