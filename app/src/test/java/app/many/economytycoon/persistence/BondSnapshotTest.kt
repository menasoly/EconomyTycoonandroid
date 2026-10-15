package app.many.economytycoon.persistence

import app.many.economytycoon.bonds.DEALER
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BondSnapshotTest {
    private fun world(): Simulation {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val country = sim.economy.world.countries().first()
        for (i in 1..4) sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(2_000_000), CompanyKind.AI))
        return sim
    }

    @Test fun issuesHoldingsCostsAndTheDealerSurviveASave() {
        val sim = world()
        sim.advance(450)
        val bonds = sim.economy.bonds
        // Give the dealer some stock so its record is non-trivial.
        val held = bonds.outstanding().firstOrNull { it.holderIds().any { h -> h != DEALER } }
        if (held != null) {
            val h = held.holderIds().first { it != DEALER }
            sim.economy.bondRunner.trade(h, held.id, 1, false, sim.tick)
        }
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = sim.economy.bonds; val b = copy.economy.bonds
        assertEquals(a.all().map { it.id to it.status }, b.all().map { it.id to it.status })
        assertEquals(a.all().map { it.couponPpm to it.unitsSold }, b.all().map { it.couponPpm to it.unitsSold })
        assertEquals(a.all().map { x -> x.holderIds().map { x.heldBy(it) to x.costOf(it) } }, b.all().map { x -> x.holderIds().map { x.heldBy(it) to x.costOf(it) } })
        assertEquals(a.all().map { it.couponsPaid }, b.all().map { it.couponsPaid })
        assertEquals(a.dealerFunded, b.dealerFunded)
        assertTrue(a.all().isNotEmpty())
    }

    @Test fun aLoadedGameKeepsPayingCouponsAndRepaying() {
        val sim = world()
        sim.advance(200)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 500) { sim.advance(); copy.advance(); assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash()) }
        assertTrue(sim.economy.bonds.all().any { it.couponsPaid.raw > 0L })
        assertEquals(sim.economy.bonds.all().map { it.status }, copy.economy.bonds.all().map { it.status })
    }

    @Test fun theBondCommandsSurviveAReplay() {
        val sim = world()
        val c = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Human Works", sim.economy.world.countries().first().id, null, Money.units(500_000))) as Outcome.Ok).value.created!!)
        sim.advance(70)
        val open = sim.economy.bonds.open().firstOrNull()
        if (open != null) sim.submit(Actor.Player(1), Command.SubscribeBond(c, open.id, 5))
        sim.advance(30)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
    }
}
