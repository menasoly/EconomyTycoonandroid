package app.many.economytycoon.simulation

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.production.ConstructionProject
import app.many.economytycoon.production.FacilityStatus

/**
 * Brings materials to construction sites. Stock the owner already holds in the project's region goes
 * first; whatever is still missing as the last build stage approaches is bought from a contractor at the
 * going price plus a markup, so projects do not wait forever on goods nobody sells.
 */
class ConstructionSupplier(private val e: Economy, private val contractorMarkupPpm: Long = 200_000L) {
    var contractorSpend: Money = Money.ZERO
        private set

    fun run(tick: Tick): Int {
        var deliveries = 0
        for (p in e.production.projects().filter { it.isOpen }.sortedBy { it.id }) {
            val owner = e.companies.company(p.owner) ?: continue
            if (!owner.isActive) continue
            for (need in p.required) {
                val missing = p.missing(need.good)
                if (missing.raw <= 0L) continue
                deliveries += fromOwnStock(p, need.good, missing)
                val still = p.missing(need.good)
                if (still.raw > 0L && p.ticksDone >= p.totalTicks - LEAD_TICKS) deliveries += fromContractor(p, need.good, still)
            }
        }
        return deliveries
    }

    private fun fromOwnStock(p: ConstructionProject, good: app.many.economytycoon.core.GoodId, missing: Quantity): Int {
        var left = missing
        var n = 0
        val sites = e.production.facilitiesOf(p.owner).filter { it.region == p.region && it.status != FacilityStatus.DEMOLISHED }
        for (f in sites.sortedBy { it.id }) {
            if (left.raw <= 0L) break
            val take = Quantity.min(left, f.inventory.available(good))
            if (take.raw <= 0L) continue
            val accepted = e.production.deliverMaterials(p.owner, p.id, good, take)
            if (accepted is Outcome.Ok) {
                f.inventory.remove(good, accepted.value)
                left -= accepted.value
                n++
            }
        }
        return n
    }

    private fun fromContractor(p: ConstructionProject, good: app.many.economytycoon.core.GoodId, missing: Quantity): Int {
        val unit = e.markets.price(MarketKey(p.region, good)).perUnit
        val price = Money(mulDiv(unit.raw, 1_000_000L + contractorMarkupPpm, 1_000_000L))
        val cost = app.many.economytycoon.core.Price(price).valueOf(missing)
        if (cost.raw <= 0L) return 0
        if (!e.payer.charge(p.owner, cost, "construction materials ${p.id.value}")) return 0
        val accepted = e.production.deliverMaterials(p.owner, p.id, good, missing)
        if (accepted is Outcome.Rejected) {
            e.payer.credit(p.owner, cost, "refund")
            return 0
        }
        contractorSpend += cost
        return 1
    }

    companion object { const val LEAD_TICKS = 2 }
}
