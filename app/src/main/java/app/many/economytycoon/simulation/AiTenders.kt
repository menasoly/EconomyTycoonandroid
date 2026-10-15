package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.tenders.Tender

/**
 * Computer-run companies bid for tenders they already have the stock to fill. They price at their cost plus a
 * margin that differs from company to company, never above the government's ceiling and never below the floor.
 */
class AiTenders(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var bids: Int = 0)

    private val baseMarginPpm = 80_000L
    private val marginStepPpm = 15_000L

    fun run(tick: Tick): Report {
        val report = Report()
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.map { it.id }.sorted()
        for (t in e.tenders.open()) {
            for (id in ai) {
                if (t.bidOf(id) != null || e.tenders.isBarred(id, tick)) continue
                bidOn(id, t, tick, report)
            }
        }
        return report
    }

    private fun supplier(id: CompanyId, t: Tender): Facility? =
        e.production.facilitiesOf(id).filter { it.status == FacilityStatus.OPERATING && it.inventory.available(t.good) >= t.quantity }
            .minWithOrNull(compareBy<Facility> { it.inventory.unitCost(t.good).raw }.thenBy { it.id })

    private fun bidOn(id: CompanyId, t: Tender, tick: Tick, report: Report) {
        val f = supplier(id, t) ?: return
        val ref = e.tenderRunner.reference(t)
        val cost = f.inventory.unitCost(t.good)
        val margin = baseMarginPpm + (id.value % 5L) * marginStepPpm
        val wanted = Money(mulDiv(Money.max(cost, Money(mulDiv(ref.raw, 600_000L, 1_000_000L))).raw, 1_000_000L + margin, 1_000_000L))
        val price = Money.min(Money.max(wanted, e.tenderRunner.floorFor(t)), t.ceiling)
        if (price < cost) return
        val r = executor.execute(Actor.Ai(id), Command.BidTender(id, t.id, f.id, price))
        if (r is Outcome.Ok) report.bids++
    }
}
