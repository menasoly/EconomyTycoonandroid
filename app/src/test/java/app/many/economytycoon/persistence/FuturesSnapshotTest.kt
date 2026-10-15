package app.many.economytycoon.persistence

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.futures.Side
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturesSnapshotTest {
    private fun world(): Simulation {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        for (i in 1..5) {
            val id = CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(3_000_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
            val farm = e.production.grantFacility(id, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
            farm.inventory.add(GoodId("wheat"), Quantity.units(3_000), Money.units(24_000))
        }
        return sim
    }

    @Test fun positionsMarginsAndCallsSurviveASave() {
        val sim = world()
        sim.advance(200)
        assertTrue(sim.economy.futures.all().isNotEmpty())
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = sim.economy.futures; val b = copy.economy.futures
        assertEquals(a.all().map { Triple(it.id, it.status, it.side) }, b.all().map { Triple(it.id, it.status, it.side) })
        assertEquals(a.all().map { it.margin to it.mark }, b.all().map { it.margin to it.mark })
        assertEquals(a.all().map { it.realised to it.feePaid }, b.all().map { it.realised to it.feePaid })
        assertEquals(a.all().map { it.calledAt to it.closedAt }, b.all().map { it.calledAt to it.closedAt })
        assertEquals(a.clearingFunded, b.clearingFunded)
    }

    @Test fun aLoadedGameKeepsSettlingInStep() {
        val sim = world()
        sim.advance(100)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 300) { sim.advance(); copy.advance(); assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash()) }
        assertTrue(sim.economy.futures.all().any { !it.isOpen })
    }

    @Test fun theFuturesCommandsSurviveAReplay() {
        val sim = world()
        val country = sim.economy.world.countries().first()
        val c = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Human Works", country.id, null, Money.units(500_000))) as Outcome.Ok).value.created!!)
        sim.advance(5)
        sim.submit(Actor.Player(1), Command.OpenFuture(c, GoodId("wheat"), Side.LONG, 3, 30))
        sim.advance(10)
        val id = sim.economy.futures.openOf(c).firstOrNull()?.id?.value
        if (id != null) sim.submit(Actor.Player(1), Command.AddMargin(c, id, Money.units(1_000)))
        sim.advance(10)
        if (id != null) sim.submit(Actor.Player(1), Command.CloseFuture(c, id))
        sim.advance(5)
        assertTrue(id != null)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
    }

    @Test fun anEmptyRegisterRoundTrips() {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertTrue(copy.economy.futures.all().isEmpty())
        assertEquals(sim.hash(), copy.hash())
    }
}
