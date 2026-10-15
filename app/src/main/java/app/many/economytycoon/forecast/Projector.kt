package app.many.economytycoon.forecast

import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Simulation

/**
 * Plays the game forward on a private copy to show where a company is heading. The copy is made by saving and
 * loading the whole simulation, random streams included, so with nothing changed the copy goes exactly where the
 * real game will. The real game is never touched.
 */
object Projector {
    const val MAX_DAYS = 720
    const val DEFAULT_STEP = 5

    /** An independent copy of the simulation as it stands now. */
    fun fork(sim: Simulation): Simulation = SimulationSnapshot.load(SimulationSnapshot.save(sim), sim.content)

    private fun point(sim: Simulation, company: CompanyId): ForecastPoint? {
        val e = sim.economy
        val c = e.companies.company(company) ?: return null
        val st = e.finance.statements
        val b = st.balance(c.owner, e.tick)
        val income = st.income(c.owner, Tick(0), e.tick)
        return ForecastPoint(e.tick.value, b.cash, b.netWorth, b.totalDebt, income.revenue)
    }

    /**
     * Runs a copy of [sim] for [days] days and records the company's accounts every [step] days. Each of [actions]
     * is tried first, at once, as [actor]; refused ones are reported and do not stop the run.
     */
    fun project(
        sim: Simulation,
        company: CompanyId,
        days: Int,
        step: Int = DEFAULT_STEP,
        actions: List<Command> = emptyList(),
        actor: Actor = Actor.Player(1),
        describe: (Command) -> String = { it::class.simpleName ?: "action" },
    ): Forecast = runCopy(fork(sim), company, days, step, actions, actor, describe)

    /** The bytes a trial run starts from. Taking them is quick; the run itself can then happen on another thread. */
    fun capture(sim: Simulation): ByteArray = SimulationSnapshot.save(sim)

    /** Like [project], from bytes taken earlier with [capture], so the live game can carry on meanwhile. */
    fun projectSaved(
        saved: ByteArray,
        content: app.many.economytycoon.content.ContentRepository,
        company: CompanyId,
        days: Int,
        step: Int = DEFAULT_STEP,
        actions: List<Command> = emptyList(),
        actor: Actor = Actor.Player(1),
        describe: (Command) -> String = { it::class.simpleName ?: "action" },
    ): Forecast = runCopy(SimulationSnapshot.load(saved, content), company, days, step, actions, actor, describe)

    private fun runCopy(
        copy: Simulation,
        company: CompanyId,
        days: Int,
        step: Int,
        actions: List<Command>,
        actor: Actor,
        describe: (Command) -> String,
    ): Forecast {
        require(days in 1..MAX_DAYS) { "a trial run covers 1 to $MAX_DAYS days" }
        require(step >= 1) { "the step must be at least a day" }
        val start = copy.tick.value
        val results = actions.map { c ->
            val r = copy.execute(actor, c)
            ActionResult(describe(c), (r as? Outcome.Rejected)?.rejection?.toString())
        }
        val points = ArrayList<ForecastPoint>()
        point(copy, company)?.let { points += it } ?: return Forecast(company, start, emptyList(), EndReason.COMPANY_GONE, results)
        var reason = EndReason.COMPLETED
        var done = 0
        while (done < days) {
            val chunk = minOf(step, days - done)
            copy.advance(chunk)
            done += chunk
            val p = point(copy, company)
            if (p == null) { reason = EndReason.COMPANY_GONE; break }
            points += p
            if (copy.economy.companies.company(company)?.status != CompanyStatus.ACTIVE) { reason = EndReason.NOT_ACTIVE; break }
        }
        return Forecast(company, start, points, reason, results)
    }

    /** The run in which nothing is done, beside one in which [actions] are taken. */
    fun compare(
        sim: Simulation,
        company: CompanyId,
        days: Int,
        actions: List<Command>,
        step: Int = DEFAULT_STEP,
        actor: Actor = Actor.Player(1),
        describe: (Command) -> String = { it::class.simpleName ?: "action" },
    ): Comparison = Comparison(project(sim, company, days, step), project(sim, company, days, step, actions, actor, describe))
}
