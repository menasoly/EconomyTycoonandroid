package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.grants.GrantKind

/**
 * Computer-run companies apply to every open programme they can plausibly grow into: ones that already operate in
 * the country, and for an investment grant only when the favoured industry is one they could afford to build in.
 */
class AiGrants(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var applications: Int = 0)

    fun run(tick: Tick): Report {
        val report = Report()
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.sortedBy { it.id }
        for (g in e.grants.open()) {
            val regions = e.world.regionsOf(g.country).map { it.id }.toSet()
            val minimumBuild = when (g.kind) {
                GrantKind.JOBS -> null
                GrantKind.FACILITIES -> e.content.allFacilityTypes.filter { it.family == g.family && it.requiredTech == null && it.extracts == null && it.maxLines > 0 }.minOfOrNull { it.buildCost.raw }
            }
            for (c in ai) {
                if (g.applicationOf(c.id) != null) continue
                val present = e.production.facilitiesOf(c.id).any { it.region in regions }
                if (!present) continue
                if (g.kind == GrantKind.FACILITIES) {
                    if (minimumBuild == null) continue
                    if (e.finance.ledger.cash(c.owner).raw < minimumBuild * 2L) continue
                }
                val r = executor.execute(Actor.Ai(c.id), Command.ApplyGrant(c.id, g.id))
                if (r is Outcome.Ok) report.applications++
            }
        }
        return report
    }
}
