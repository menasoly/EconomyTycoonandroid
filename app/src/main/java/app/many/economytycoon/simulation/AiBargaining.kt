package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.unions.Demand
import app.many.economytycoon.unions.ResponseKind

/**
 * Computer-run companies answer wage demands. Each has a fixed temperament: some pay up when the ask is
 * moderate, some counter with part of it, some dig in. They always answer well before the deadline.
 */
class AiBargaining(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var accepted: Int = 0, var countered: Int = 0, var refused: Int = 0)

    private val acceptUpToPpm = longArrayOf(80_000L, 50_000L, 35_000L)
    private val counterSharePpm = longArrayOf(700_000L, 600_000L, 500_000L)

    fun temperament(company: CompanyId): Int = (company.value % 3L).toInt()

    fun decide(company: CompanyId, d: Demand): Pair<ResponseKind, Long> {
        val t = temperament(company)
        return when {
            d.raisePpm <= acceptUpToPpm[t] -> ResponseKind.ACCEPT to 0L
            t == 2 && d.raisePpm > 100_000L -> ResponseKind.REFUSE to 0L
            else -> ResponseKind.COUNTER to mulDiv(d.raisePpm, counterSharePpm[t], 1_000_000L).coerceAtLeast(1L)
        }
    }

    fun run(tick: Tick): Report {
        val report = Report()
        for (d in e.unions.demands().filter { it.isOpen }) {
            val u = e.unions.get(d.union) ?: continue
            val c = e.companies.company(u.company) ?: continue
            if (c.kind != CompanyKind.AI) continue
            val (kind, counter) = decide(c.id, d)
            val r = executor.execute(Actor.Ai(c.id), Command.RespondDemand(c.id, d.id, kind, counter))
            if (r is Outcome.Ok) when (kind) { ResponseKind.ACCEPT -> report.accepted++; ResponseKind.COUNTER -> report.countered++; ResponseKind.REFUSE -> report.refused++ }
        }
        return report
    }
}
