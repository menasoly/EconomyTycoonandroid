package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.risk.Peril

/**
 * Computer-run companies insure the facilities worth the most when they can comfortably afford the premium.
 * Companies in places where quakes or floods are likely take wider cover. Choices depend only on the state.
 */
class AiInsurance(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var bought: Int = 0)

    private val every = 30L
    private val perCompany = 3
    private val minInsuredValue = Money.units(50_000)
    private val cashCover = 8L

    fun run(tick: Tick): Report {
        val report = Report()
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.map { it.id }.sorted()
        for (id in ai) if ((tick.value + id.value) % every == 0L) insure(id, tick, report)
        return report
    }

    private fun levelFor(region: app.many.economytycoon.world.Region): CoverLevel {
        val risk = e.risk
        val big = Peril.values().filter { it.regional }.sumOf { risk.regionFactorPpm(region, it) } / 3
        return if (big >= 1_400_000L) CoverLevel.FULL else CoverLevel.STANDARD
    }

    private fun insure(id: CompanyId, tick: Tick, report: Report) {
        val cash = e.finance.ledger.cash(LedgerOwner.company(id))
        val candidates = e.production.facilitiesOf(id)
            .filter { it.status == FacilityStatus.OPERATING && e.risk.activeFor(it.id) == null }
            .map { it to e.riskRunner.insuredValueOf(it) }
            .filter { it.second >= minInsuredValue }
            .sortedWith(compareByDescending<Pair<app.many.economytycoon.production.Facility, Money>> { it.second }.thenBy { it.first.id })
        var bought = 0
        for ((f, _) in candidates) {
            if (bought >= perCompany) break
            val level = levelFor(e.world.region(f.region))
            val q = e.riskRunner.quote(f, level, 800_000L, Deductible.MEDIUM)
            if (cash < q.premium * cashCover) continue
            val r = executor.execute(Actor.Ai(id), Command.BuyPolicy(id, f.id, level, 800_000L, Deductible.MEDIUM))
            if (r is Outcome.Ok) { bought++; report.bought++ }
        }
    }
}
