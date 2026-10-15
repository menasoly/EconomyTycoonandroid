package app.many.economytycoon.replay

import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation

object ReplayTestSupport {
    val content get() = SimTestSupport.content

    /** A short played game with a founded company, so the log is not empty. */
    fun record(seed: Long = 61L, ticks: Int = 40, every: Int = 10): Trace {
        val sim = Simulation(SimTestSupport.config(seed, 3), content)
        val recorder = TraceRecorder(sim, content, every)
        val country = sim.economy.world.countries().first()
        sim.execute(Actor.Player(1L), Command.FoundCompany("Replay Works", country.id, null, Money.units(400_000)))
        recorder.advance(ticks / 2)
        recorder.advance(ticks - ticks / 2)
        return recorder.trace()
    }
}
