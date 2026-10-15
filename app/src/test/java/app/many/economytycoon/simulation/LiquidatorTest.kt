package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyStatus
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

class LiquidatorTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        init { e.finance.mint(Tick.ZERO, e.finance.households, Money.units(5_000_000), "test") }
        val id = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Doomed Works", country.id, null, Money.units(200_000))) as Outcome.Ok).value.created!!)
        val farm = e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    }

    @Test fun liquidationSellsStockAndBuildingsThenEndsTheCompany() {
        val r = Rig()
        r.farm.inventory.add(GoodId("wheat"), Quantity.units(500), Money.units(10_000))
        val report = Liquidator(r.e).liquidate(r.id, Tick(10))
        assertTrue(report.raised.raw > 0)
        assertEquals(CompanyStatus.DISSOLVED, r.e.companies.require(r.id).status)
        assertTrue(r.e.production.facilitiesOf(r.id).isEmpty())
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun openOrdersAreCancelledAndReservationsReleased() {
        val r = Rig()
        r.farm.inventory.add(GoodId("wheat"), Quantity.units(100), Money.units(2_000))
        r.sim.execute(Actor.Player(1), Command.PlaceOrder(r.id, r.farm.id, Side.SELL, GoodId("wheat"), Quantity.units(80), null))
        Liquidator(r.e).liquidate(r.id, Tick(10))
        assertTrue(r.e.markets.openOrders(Party.company(r.id)).isEmpty())
        assertEquals(emptyList<String>(), Invariants.reservations(r.e))
    }

    @Test fun shareholdersGetTheSurplusInCash() {
        val r = Rig()
        val before = r.e.finance.ledger.cash(r.e.finance.households)
        val report = Liquidator(r.e).liquidate(r.id, Tick(10))
        val after = r.e.finance.ledger.cash(r.e.finance.households)
        assertTrue(report.result!!.residualToShareholders.raw > 0)
        assertTrue(after.raw > before.raw - Money.units(100_000).raw)
        assertEquals(Money.ZERO, r.e.finance.ledger.cash(LedgerOwner.company(r.id)))
    }

    @Test fun runOnlyTouchesCompaniesWaitingInLiquidation() {
        val r = Rig()
        assertTrue(Liquidator(r.e).run(Tick(1)).isEmpty())
        assertEquals(CompanyStatus.ACTIVE, r.e.companies.require(r.id).status)
    }

    @Test fun retiringACompanyDropsItsRulesAndResearch() {
        val r = Rig()
        r.e.companies.policies.setSell(r.id, app.many.economytycoon.companies.SellRule(r.farm.id, GoodId("wheat")))
        Liquidator(r.e).liquidate(r.id, Tick(10))
        assertTrue(r.e.companies.policies.sellRules(r.id).isEmpty())
        assertTrue(r.e.research.peek(r.id) == null)
    }
}
