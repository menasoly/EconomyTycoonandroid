package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.talent.TalentAi
import app.many.economytycoon.talent.TalentCommand

/**
 * Computer-run companies look after their people: once a week each plans a few moves from its own character (train a
 * weak team, retain people it could lose, sign a non-compete, run a recruiting campaign, bid for a rival's staff) and
 * once a quarter an ambitious one may open a training business. Every move goes through the executor as the company's
 * own order, so rivals obey the same rules as the player.
 */
class AiTalent(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var commands: Int = 0, var refused: Int = 0)

    private val planner = TalentAi(e.talent)

    fun run(tick: Tick): Report {
        val report = Report()
        if (!e.talentRunner.active) return report
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.map { it.id }.sorted()
        val weekly = ai.filter { (tick.value + it.value) % WEEK == 0L }
        val quarterly = ai.filter { (tick.value + it.value) % QUARTER == 0L }
        if (weekly.isEmpty() && quarterly.isEmpty()) return report
        val inputs = e.talentRunner.inputs(tick)
        val world = e.talentRunner.world
        for (id in weekly) {
            val personality = e.competitors.personality(id) ?: continue
            issue(id, planner.plan(id, personality, tick, inputs, world), report)
        }
        for (id in quarterly) {
            val personality = e.competitors.personality(id) ?: continue
            planner.planProvider(id, personality, tick, e.talentRunner.inputs(tick), world)?.let { issue(id, listOf(it), report) }
        }
        return report
    }

    private fun issue(id: app.many.economytycoon.core.CompanyId, commands: List<TalentCommand>, report: Report) {
        for (cmd in commands) {
            val r = executor.execute(Actor.Ai(id), Command.Talent(id, cmd))
            if (r is Outcome.Ok) report.commands++ else report.refused++
        }
    }

    private companion object {
        const val WEEK = 7L
        const val QUARTER = 90L
    }
}
