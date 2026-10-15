package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.grants.GrantKind
import app.many.economytycoon.grants.GrantProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGrantsTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val ai = AiGrants(e, sim.executor)
        init { e.finance.mint(e.tick, LedgerOwner.government(country.id), Money.units(5_000_000), "test:treasury") }
        fun rival(name: String, capital: Long = 900_000, farm: Boolean = true): CompanyId {
            val id = CompanyId((sim.execute(Actor.System, Command.FoundCompany(name, country.id, null, Money.units(capital), CompanyKind.AI)) as Outcome.Ok).value.created!!)
            if (farm) e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
            return id
        }
        fun jobs(): GrantProgram = (e.grants.announce(country.id, GrantKind.JOBS, null, Money.units(100), Money.units(5_000), Money.units(50_000), e.tick) as Outcome.Ok).value
        fun build(f: IndustryFamily): GrantProgram = (e.grants.announce(country.id, GrantKind.FACILITIES, f, Money.units(5_000), Money.units(20_000), Money.units(50_000), e.tick) as Outcome.Ok).value
    }

    @Test fun rivalsWithAPresenceApplyToAJobsGrant() {
        val r = Rig(); val a = r.rival("Rival A"); val b = r.rival("Rival B")
        val g = r.jobs()
        val report = r.ai.run(r.e.tick)
        assertEquals(2, report.applications)
        assertTrue(g.applicationOf(a) != null && g.applicationOf(b) != null)
    }

    @Test fun aRivalWithNoPresenceStaysOut() {
        val r = Rig(); val a = r.rival("Rival A", farm = false)
        val g = r.jobs()
        r.ai.run(r.e.tick)
        assertNull(g.applicationOf(a))
    }

    @Test fun rivalsApplyOnlyOnce() {
        val r = Rig(); r.rival("Rival A")
        r.jobs()
        r.ai.run(r.e.tick)
        assertEquals(0, r.ai.run(r.e.tick).applications)
    }

    @Test fun playersAreLeftToApplyThemselves() {
        val r = Rig()
        val p = CompanyId((r.sim.execute(Actor.Player(1), Command.FoundCompany("Player Works", r.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        r.e.production.grantFacility(p, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        val g = r.jobs()
        r.ai.run(r.e.tick)
        assertNull(g.applicationOf(p))
    }

    @Test fun anInvestmentGrantNeedsMoneyToBuildWith() {
        val r = Rig(); val rich = r.rival("Rich Rival", 900_000); val poor = r.rival("Poor Rival", 100_000)
        val g = r.build(IndustryFamily.AGRICULTURE)
        r.ai.run(r.e.tick)
        assertTrue(g.applicationOf(rich) != null)
        assertNull(g.applicationOf(poor))
    }

    @Test fun closedProgrammesAreNotJoined() {
        val r = Rig(); val a = r.rival("Rival A")
        val g = r.jobs()
        r.e.tick = app.many.economytycoon.core.Tick(g.applyUntil.value)
        assertEquals(0, r.ai.run(r.e.tick).applications)
        assertNull(g.applicationOf(a))
    }

    @Test fun aLongGameLetsRivalsCollectGrants() {
        val sim = SimTestSupport.sim(77, ai = 5)
        sim.advance(260)
        val e = sim.economy
        val programmes = e.grants.all()
        assertTrue("no programmes announced", programmes.isNotEmpty())
        val applications = programmes.sumOf { it.applications.size }
        assertTrue("rivals never applied", applications > 0)
        assertEquals(emptyList<String>(), e.finance.invariants())
    }
}
