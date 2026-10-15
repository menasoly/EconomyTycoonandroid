package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.regulation.Law
import app.many.economytycoon.regulation.Lever

/**
 * Computer-run companies with factories in a country spend a little to push laws that cost them money towards
 * defeat and laws that save them money towards passing. Each company weighs in at most once per law, and only
 * some of them bother.
 */
class AiLobbying(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var donations: Int = 0)

    private val cashShareBps = 50L
    private val cap = Money.units(25_000)
    private val cashCover = 20L

    /** Whether a law would help (+1) or hurt (-1) a company that runs factories in the country, or leave it cold (0). */
    fun effect(law: Law, pollutes: Boolean): Int {
        val raise = law.raises
        return when (law.lever) {
            Lever.CORPORATE_TAX, Lever.PAYROLL_TAX, Lever.SALES_TAX, Lever.MINIMUM_WAGE -> if (raise) -1 else 1
            Lever.ENVIRONMENT -> if (pollutes) (if (raise) -1 else 1) else 0
            Lever.TARIFF -> 0
        }
    }

    fun run(tick: Tick): Report {
        val report = Report()
        val open = e.regulation.laws().filter { it.isOpen }
        if (open.isEmpty()) return report
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.map { it.id }.sorted()
        for (law in open) {
            val regions = e.world.regionsOf(law.country).map { it.id }.toSet()
            for (id in ai) {
                if (law.backedBy(id).raw > 0L || law.opposedBy(id).raw > 0L) continue
                if ((law.id.value + id.value) % 5L >= 2L) continue
                donate(id, law, regions, report)
            }
        }
        return report
    }

    private fun donate(id: CompanyId, law: Law, regions: Set<app.many.economytycoon.core.RegionId>, report: Report) {
        val local = e.production.facilitiesOf(id).filter { it.region in regions && it.status == FacilityStatus.OPERATING }
        if (local.isEmpty()) return
        val pollutes = local.any { it.pollutionPpm > 300_000L }
        val stance = effect(law, pollutes)
        if (stance == 0) return
        val cash = e.finance.ledger.cash(LedgerOwner.company(id))
        val amount = Money.min(cap, Money(mulDiv(cash.raw.coerceAtLeast(0L), cashShareBps, 10_000L)))
        if (amount < e.regulation.params.minLobby || cash < amount * cashCover) return
        val r = executor.execute(Actor.Ai(id), Command.LobbyLaw(id, law.id, stance > 0, amount))
        if (r is Outcome.Ok) report.donations++
    }
}
