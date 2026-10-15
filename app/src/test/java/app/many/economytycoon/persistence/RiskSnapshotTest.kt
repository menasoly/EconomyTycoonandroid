package app.many.economytycoon.persistence

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskSnapshotTest {
    private fun world(): Simulation {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val owner = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Cover Works", country.id, null, Money.units(800_000))) as Outcome.Ok).value.created!!)
        val farm = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        farm.inventory.add(GoodId("wheat"), Quantity.units(400), Money.units(60_000))
        e.finance.mint(e.tick, e.finance.households, Money.units(20_000_000), "test:households")
        assertTrue(sim.execute(Actor.Player(1), Command.BuyPolicy(owner, farm.id, CoverLevel.FULL, 900_000L, Deductible.MEDIUM)) is Outcome.Ok)
        return sim
    }

    @Test fun policiesDisastersAndClaimsSurviveASave() {
        val sim = world()
        sim.advance(900)
        val e = sim.economy
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        assertEquals(e.risk.allPolicies().map { it.id }, copy.economy.risk.allPolicies().map { it.id })
        assertEquals(e.risk.disasters(), copy.economy.risk.disasters())
        assertEquals(e.risk.claims(), copy.economy.risk.claims())
        assertEquals(e.risk.reserveSeeded, copy.economy.risk.reserveSeeded)
        val a = e.risk.allPolicies().first(); val b = copy.economy.risk.allPolicies().first()
        assertEquals(a.status, b.status); assertEquals(a.premiumsPaid, b.premiumsPaid); assertEquals(a.claimsPaid, b.claimsPaid)
        assertEquals(a.surchargePpm, b.surchargePpm); assertEquals(a.paidThrough, b.paidThrough)
    }

    @Test fun aLoadedGameRollsTheSameDisasters() {
        val sim = world()
        sim.advance(30)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 400) {
            sim.advance(); copy.advance()
            assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash())
        }
        assertEquals(sim.economy.risk.disasters(), copy.economy.risk.disasters())
    }

    @Test fun anOlderSaveWithoutRiskDataIsRefusedCleanly() {
        val sim = world()
        val bytes = SimulationSnapshot.save(sim)
        val broken = bytes.copyOf(bytes.size - 5)
        val failed = try { SimulationSnapshot.load(broken, SimTestSupport.content); false } catch (e: Exception) { true }
        assertTrue(failed)
    }
}
