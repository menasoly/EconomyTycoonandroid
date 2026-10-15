package app.many.economytycoon.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterminismTest {
    @Test fun twoRunsFromOneSeedMatchTickForTick() {
        val a = SimTestSupport.sim(11); val b = SimTestSupport.sim(11)
        repeat(60) {
            a.advance(); b.advance()
            if (it % 10 == 9) assertEquals("diverged at tick ${a.tick.value}", a.hash(), b.hash())
        }
    }

    @Test fun differentSeedsDiverge() {
        val a = SimTestSupport.sim(1); val b = SimTestSupport.sim(2)
        a.advance(20); b.advance(20)
        assertTrue(a.hash() != b.hash())
    }

    @Test fun replayingTheLogReproducesTheRun() {
        val sim = SimTestSupport.sim(21)
        val e = sim.economy
        val country = e.world.countries().first()
        sim.execute(Actor.Player(1), Command.FoundCompany("Replay Works", country.id, country.capital, app.many.economytycoon.core.Money.units(300_000)))
        sim.advance(5)
        val me = e.companies.byName("Replay Works")!!
        sim.submit(Actor.Player(1), Command.SetBudget(me.id, app.many.economytycoon.companies.Department.MARKETING, app.many.economytycoon.core.Money.units(50)))
        sim.advance(25)
        val copy = Simulation.replay(SimTestSupport.config(21), SimTestSupport.content, sim.commandLog(), sim.tick.value)
        assertEquals(sim.hash(), copy.hash())
    }

    @Test fun commandsAreOnlyAppliedAtTheirTick() {
        val sim = SimTestSupport.sim(3)
        val e = sim.economy
        val country = e.world.countries().first()
        sim.execute(Actor.Player(1), Command.FoundCompany("Queue Works", country.id, country.capital, app.many.economytycoon.core.Money.units(300_000)))
        val me = e.companies.byName("Queue Works")!!
        sim.submit(Actor.Player(1), Command.SetBudget(me.id, app.many.economytycoon.companies.Department.LEGAL, app.many.economytycoon.core.Money.units(10)))
        assertEquals(1, sim.pendingCommands())
        assertEquals(app.many.economytycoon.core.Money.ZERO, e.companies.budgets(me.id).budget(app.many.economytycoon.companies.Department.LEGAL))
        sim.advance()
        assertEquals(app.many.economytycoon.core.Money.units(10), e.companies.budgets(me.id).budget(app.many.economytycoon.companies.Department.LEGAL))
        assertEquals(0, sim.pendingCommands())
    }
}
