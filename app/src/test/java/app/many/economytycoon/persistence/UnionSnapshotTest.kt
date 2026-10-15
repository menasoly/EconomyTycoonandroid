package app.many.economytycoon.persistence

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.workforce.CohortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnionSnapshotTest {
    private fun world(): Simulation {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val role = e.content.allRoles.first().id
        for (i in 1..4) {
            val id = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(2_000_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
            val c = e.workforce.ensure(CohortKey(id, FacilityId(i.toLong()), role, region))
            c.headcount = 50; c.moralePpm = 200_000L; c.target = 50
        }
        return sim
    }

    private fun keepAngry(sim: Simulation) {
        for (c in sim.economy.workforce.all()) { c.moralePpm = 200_000L }
    }

    private fun run(sim: Simulation, days: Int) { repeat(days) { keepAngry(sim); sim.advance() } }

    @Test fun unionsAndDemandsSurviveASave() {
        val sim = world()
        run(sim, 400)
        assertTrue(sim.economy.unions.all().isNotEmpty())
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = sim.economy.unions; val b = copy.economy.unions
        assertEquals(a.all().map { Triple(it.id, it.strengthPpm, it.members) }, b.all().map { Triple(it.id, it.strengthPpm, it.members) })
        assertEquals(a.all().map { it.raisesWon to it.strikes }, b.all().map { it.raisesWon to it.strikes })
        assertEquals(a.demands().map { it.id to it.status }, b.demands().map { it.id to it.status })
        assertEquals(a.demands().map { it.agreedPpm }, b.demands().map { it.agreedPpm })
        assertEquals(a.demandSequence, b.demandSequence)
    }

    @Test fun aLoadedGameKeepsBargainingInStep() {
        val sim = world()
        run(sim, 150)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 300) {
            keepAngry(sim); keepAngry(copy)
            sim.advance(); copy.advance()
            assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash())
        }
        assertTrue(sim.economy.unions.demands().isNotEmpty())
    }

    @Test fun anEmptySaveRestoresAnEmptyRegister() {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertTrue(copy.economy.unions.all().isEmpty() && copy.economy.unions.demands().isEmpty())
        assertEquals(sim.hash(), copy.hash())
    }
}
