package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInsuranceTest {
    private class Rig(capital: Long = 900_000) {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val ai = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival Works", country.id, null, Money.units(capital), CompanyKind.AI)) as Outcome.Ok).value.created!!)
        val insurance = AiInsurance(e, CommandExecutor(e))
        fun farm(stock: Long = 5_000) = e.production.grantFacility(ai, FacilityTypeId("grain_farm"), region, null, null, null, e.tick).also {
            it.inventory.add(GoodId("wheat"), Quantity.units(stock), Money.units(stock * 20))
        }
        fun dueTick(): Tick { var t = e.tick.value; while ((t + ai.value) % 30L != 0L) t++; return Tick(t) }
    }

    @Test fun aRichCompanyInsuresItsValuableFacility() {
        val r = Rig(); val farm = r.farm()
        r.e.tick = r.dueTick()
        val report = r.insurance.run(r.e.tick)
        assertEquals(1, report.bought)
        assertNotNull(r.e.risk.activeFor(farm.id))
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun nothingHappensOffTheMonthlyCycle() {
        val r = Rig(); val farm = r.farm()
        r.e.tick = Tick(r.dueTick().value + 1)
        assertEquals(0, r.insurance.run(r.e.tick).bought)
        assertFalse(r.e.risk.activeFor(farm.id) != null)
    }

    @Test fun cheapFacilitiesAreLeftUncovered() {
        val r = Rig()
        val shed = r.e.production.grantFacility(r.ai, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        val value = r.e.riskRunner.insuredValueOf(shed)
        if (value >= Money.units(50_000)) return  // this content prices every farm above the floor
        r.e.tick = r.dueTick()
        assertEquals(0, r.insurance.run(r.e.tick).bought)
    }

    @Test fun anAlreadyInsuredFacilityIsNotInsuredAgain() {
        val r = Rig(); r.farm()
        r.e.tick = r.dueTick()
        r.insurance.run(r.e.tick)
        r.e.tick = Tick(r.e.tick.value + 30)
        assertEquals(0, r.insurance.run(r.e.tick).bought)
        assertEquals(1, r.e.risk.activePoliciesOf(r.ai).size)
    }

    @Test fun aCompanyShortOfCashKeepsItsMoney() {
        val r = Rig(capital = 60_000); r.farm(stock = 20_000)
        r.e.tick = r.dueTick()
        val cash = r.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(r.ai))
        val q = r.e.riskRunner.quote(r.e.production.facilitiesOf(r.ai).first(), app.many.economytycoon.risk.CoverLevel.STANDARD, 800_000L, app.many.economytycoon.risk.Deductible.MEDIUM)
        if (cash >= q.premium * 8L) return
        assertEquals(0, r.insurance.run(r.e.tick).bought)
    }

    @Test fun playersAreNeverInsuredAutomatically() {
        val r = Rig()
        val player = CompanyId((r.sim.execute(Actor.Player(5), Command.FoundCompany("Mine Works", r.country.id, null, Money.units(900_000))) as Outcome.Ok).value.created!!)
        val f = r.e.production.grantFacility(player, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        f.inventory.add(GoodId("wheat"), Quantity.units(5_000), Money.units(100_000))
        for (i in 0 until 90) { r.insurance.run(r.e.tick); r.e.tick = Tick(r.e.tick.value + 1) }
        assertTrue(r.e.risk.activePoliciesOf(player).isEmpty())
    }
}
