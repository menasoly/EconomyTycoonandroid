package app.many.economytycoon.persistence

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegulationSnapshotTest {
    private fun world(): Simulation {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        e.world.replaceCountry(country.copy(environmentalStrictnessPpm = 800_000L))
        for (i in 1..3) {
            val ai = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(300_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
            e.production.grantFacility(ai, FacilityTypeId("grain_farm"), region, null, null, null, e.tick).pollutionPpm = 1_000_000L
        }
        return sim
    }

    @Test fun governmentsLawsLobbyingAndFinesSurviveASave() {
        val sim = world()
        sim.advance(400)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = sim.economy.regulation; val b = copy.economy.regulation
        assertEquals(a.governments().map { Triple(it.country, it.stance, it.nextElection) }, b.governments().map { Triple(it.country, it.stance, it.nextElection) })
        assertEquals(a.governments().map { it.approvalPpm to it.terms }, b.governments().map { it.approvalPpm to it.terms })
        assertEquals(a.laws().map { it.id to it.status }, b.laws().map { it.id to it.status })
        assertEquals(a.laws().map { it.supportPool to it.opposePool }, b.laws().map { it.supportPool to it.opposePool })
        assertEquals(a.laws().map { it.supportScorePpm }, b.laws().map { it.supportScorePpm })
        assertEquals(a.fines(), b.fines())
        assertTrue(a.laws().isNotEmpty())
    }

    @Test fun enactedLawsStillApplyAfterALoad() {
        val sim = world()
        sim.advance(600)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (c in sim.economy.world.countries()) {
            assertEquals(c, copy.economy.world.country(c.id))
            val gov = app.many.economytycoon.finance.LedgerOwner.government(c.id)
            assertEquals(sim.economy.finance.taxPolicy(gov), copy.economy.finance.taxPolicy(gov))
            for (r in sim.economy.world.regionsOf(c.id)) assertEquals(sim.economy.laborMarkets.minimumWage(r.id), copy.economy.laborMarkets.minimumWage(r.id))
        }
    }

    @Test fun aLoadedGameKeepsLegislatingTheSameWay() {
        val sim = world()
        sim.advance(300)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 200) { sim.advance(); copy.advance(); assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash()) }
        assertEquals(sim.economy.regulation.laws().map { it.id to it.status }, copy.economy.regulation.laws().map { it.id to it.status })
    }

    @Test fun pollutersGetFinedInALongGameAndTheRecordSurvives() {
        val sim = world()
        sim.advance(200)
        val fines = sim.economy.regulation.fines()
        assertTrue(fines.isNotEmpty())
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(fines, copy.economy.regulation.fines())
    }
}
