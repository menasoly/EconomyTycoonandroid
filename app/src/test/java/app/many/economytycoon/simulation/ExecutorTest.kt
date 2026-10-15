package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.Department
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.markets.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutorTest {
    private class Rig(val sim: Simulation = Simulation(SimTestSupport.config(5, 0), SimTestSupport.content)) {
        val e = sim.economy
        val country = e.world.countries().first()
        val me = Actor.Player(1)
        fun found(name: String = "Test Works", capital: Money = Money.units(400_000)): CompanyId {
            val r = sim.execute(me, Command.FoundCompany(name, country.id, country.capital, capital)) as Outcome.Ok
            return CompanyId(r.value.created!!)
        }
        fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule
    }

    @Test fun foundingCreatesAFundedPlayerCompany() {
        val r = Rig(); val id = r.found()
        val c = r.e.companies.require(id)
        assertEquals(CompanyKind.PLAYER, c.kind)
        assertEquals(Money.units(400_000), r.e.finance.ledger.cash(c.owner))
    }

    @Test fun playersCannotFoundAiOrStateCompanies() {
        val r = Rig()
        val out = r.sim.execute(r.me, Command.FoundCompany("Sneaky", r.country.id, null, Money.units(100_000), CompanyKind.AI))
        assertEquals(Rules.REGULATION, r.rule(out))
    }

    @Test fun onlyTheFounderMayCommandACompany() {
        val r = Rig(); val id = r.found()
        val other = r.sim.execute(Actor.Player(2), Command.Rename(id, "Stolen Name"))
        assertEquals(Rules.NOT_OWNER, r.rule(other))
        assertTrue(r.sim.execute(r.me, Command.Rename(id, "Renamed Works")) is Outcome.Ok)
        assertEquals("Renamed Works", r.e.companies.require(id).name)
    }

    @Test fun anAiMayOnlyActForItself() {
        val r = Rig(); val a = r.found("Alpha Works"); val b = r.found("Beta Works")
        assertEquals(Rules.NOT_OWNER, r.rule(r.sim.execute(Actor.Ai(a), Command.Rename(b, "Hijack"))))
    }

    @Test fun unknownCompaniesAreRefused() {
        val r = Rig()
        assertEquals(Rules.UNKNOWN_ENTITY, r.rule(r.sim.execute(Actor.System, Command.Rename(CompanyId(999), "Nobody"))))
    }

    @Test fun budgetsAreSetThroughTheCompanyService() {
        val r = Rig(); val id = r.found()
        r.sim.execute(r.me, Command.SetBudget(id, Department.RESEARCH, Money.units(75)))
        assertEquals(Money.units(75), r.e.companies.budgets(id).budget(Department.RESEARCH))
    }

    @Test fun buildingNeedsAKnownTypeAndMoney() {
        val r = Rig(); val id = r.found(capital = Money.units(50_000))
        val region = r.e.world.regionsOf(r.country.id).first().id
        assertEquals(Rules.UNKNOWN_ENTITY, r.rule(r.sim.execute(r.me, Command.Build(id, FacilityTypeId("moon_base"), region))))
        val farm = r.sim.execute(r.me, Command.Build(id, FacilityTypeId("grain_farm"), region))
        assertTrue(farm is Outcome.Ok)
        assertEquals(1, r.e.production.openProjectsOf(id).size)
    }

    @Test fun constructionCompletesIntoAFacility() {
        val r = Rig(); val id = r.found(capital = Money.units(2_000_000))
        val region = r.e.world.regionsOf(r.country.id).first().id
        r.sim.execute(r.me, Command.Build(id, FacilityTypeId("grain_farm"), region))
        r.sim.advance(60)
        assertEquals(1, r.e.production.facilitiesOf(id).size)
        assertTrue(r.e.finance.assets.all().any { it.cost.raw > 0 })
    }

    @Test fun placingASellOrderReservesStockAndCancelReleasesIt() {
        val r = Rig(); val id = r.found()
        val region = r.e.world.regionsOf(r.country.id).first().id
        val f = r.e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, r.e.tick)
        f.inventory.add(GoodId("wheat"), Quantity.units(100), Money.units(2_000))
        val placed = r.sim.execute(r.me, Command.PlaceOrder(id, f.id, Side.SELL, GoodId("wheat"), Quantity.units(60), null)) as Outcome.Ok
        assertEquals(Quantity.units(60), f.inventory.reserved(GoodId("wheat")))
        assertTrue(r.sim.execute(r.me, Command.CancelOrder(id, app.many.economytycoon.core.OrderId(placed.value.created!!))) is Outcome.Ok)
        assertEquals(Quantity.ZERO, f.inventory.reserved(GoodId("wheat")))
    }

    @Test fun sellingMoreThanYouHoldIsRefusedWithoutSideEffects() {
        val r = Rig(); val id = r.found()
        val region = r.e.world.regionsOf(r.country.id).first().id
        val f = r.e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, r.e.tick)
        val out = r.sim.execute(r.me, Command.PlaceOrder(id, f.id, Side.SELL, GoodId("wheat"), Quantity.units(60), null))
        assertNotNull(out.rejectionOrNull())
        assertTrue(r.e.markets.openOrders(app.many.economytycoon.markets.Party.company(id)).isEmpty())
    }

    @Test fun dissolvingARichCompanyRetiresIt() {
        val r = Rig(); val id = r.found()
        assertTrue(r.sim.execute(r.me, Command.Dissolve(id)) is Outcome.Ok)
        assertEquals(app.many.economytycoon.companies.CompanyStatus.DISSOLVED, r.e.companies.require(id).status)
        assertEquals(Rules.WRONG_STATE, r.rule(r.sim.execute(r.me, Command.Rename(id, "Ghost Works"))))
    }

    @Test fun staffingTargetsReachTheWorkforce() {
        val r = Rig(); val id = r.found()
        val region = r.e.world.regionsOf(r.country.id).first().id
        val f = r.e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, r.e.tick)
        val role = app.many.economytycoon.core.RoleId("farmhand")
        assertTrue(r.sim.execute(r.me, Command.SetStaffing(id, f.id, role, 6)) is Outcome.Ok)
        assertEquals(6L, r.e.workforce.cohortsAt(f.id).single().target)
        assertEquals(Rules.UNKNOWN_ENTITY, r.rule(r.sim.execute(r.me, Command.SetStaffing(id, f.id, app.many.economytycoon.core.RoleId("astronaut"), 1))))
    }
}
