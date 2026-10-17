package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpOutput
import app.many.economytycoon.ip.IpRun
import app.many.economytycoon.ip.Wallet
import app.many.economytycoon.research.ResearchEvent

/**
 * Computer-run companies protect what they have just researched without waiting for the monthly round of the patent
 * module: a company that finishes a technology files for it, or keeps it secret, the same day if the planner judges that
 * worthwhile. Licensing, litigation and dropping patents stay in the module's monthly round. The orders go through the
 * same executor, and so the same checks, as a player's. Nothing happens in a game without a patent office.
 */
class AiIp(private val e: Economy, private val executor: CommandExecutor) {

    fun onResearch(tick: Tick, events: List<ResearchEvent>) {
        if (!e.ipActive || events.isEmpty()) return
        val finished = events.filterIsInstance<ResearchEvent.Completed>()
            .filter { ev -> e.companies.company(ev.company)?.kind == app.many.economytycoon.companies.CompanyKind.AI }
        if (finished.isEmpty()) return
        val byCompany = java.util.TreeMap<CompanyId, MutableSet<TechId>>()
        for (ev in finished) byCompany.getOrPut(ev.company) { java.util.TreeSet(compareBy { it.key }) }.add(ev.tech)
        for ((company, techs) in byCompany) {
            if (!e.ipContext.isActive(company)) continue
            val run = IpRun(tick, e.ipContext, Wallet(e.ipContext), IpOutput(e.ip.state.ids))
            val moves = e.ip.planner.protectionMoves(run, company, e.ip.planner.personality(company))
            for (cmd in moves) {
                val tech = when (cmd) {
                    is IpCommand.FilePatent -> cmd.tech
                    is IpCommand.KeepSecret -> cmd.tech
                    else -> null
                }
                if (tech != null && tech in techs) executor.execute(Actor.Ai(company), Command.Ip(cmd))
            }
        }
    }
}
