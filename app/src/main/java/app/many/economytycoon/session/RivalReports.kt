package app.many.economytycoon.session

import app.many.economytycoon.companies.OfferState
import app.many.economytycoon.competitors.Archetype
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.simulation.Economy

data class RivalRow(
    val headline: CompanyHeadline,
    val archetype: Archetype?,
    val aggressionPpm: Long,
    val expansionPpm: Long,
    val controller: CompanyId?,
    val controllerName: String?,
    val stakeHeldByPlayer: Rate,
)

data class OfferRow(
    val id: Long,
    val acquirer: String,
    val acquirerId: CompanyId,
    val target: String,
    val targetId: CompanyId,
    val shares: Long,
    val pricePerShare: Money,
    val total: Money,
    val fairPricePerShare: Money,
    val expiresIn: Long,
    val state: OfferState,
)

/** Competitor and ownership views. */
class RivalReports(private val e: Economy, private val reports: CompanyReports = CompanyReports(e)) {

    fun rivals(viewer: CompanyId?): List<RivalRow> =
        e.companies.active().filter { it.id != viewer && !it.isPlayer }.sortedBy { it.id }.mapNotNull { c ->
            val head = reports.headline(c.id) ?: return@mapNotNull null
            val p = e.competitors.personality(c.id)
            val controller = e.companies.groups.controllerOf(c.id)
            RivalRow(
                headline = head, archetype = p?.archetype, aggressionPpm = p?.aggression ?: 0L, expansionPpm = p?.expansionAppetite ?: 0L,
                controller = controller, controllerName = controller?.let { e.companies.company(it)?.name },
                stakeHeldByPlayer = viewer?.let { e.companies.groups.stake(it, c.id) } ?: Rate.ZERO,
            )
        }.sortedByDescending { it.headline.netWorth.raw }

    /** Offers made for [company], newest first, with a fair price for comparison. */
    fun offersFor(company: CompanyId): List<OfferRow> =
        e.acquisitions.offers().filter { it.target == company }.sortedByDescending { it.made.value }.map { row(it) }

    fun offersBy(company: CompanyId): List<OfferRow> =
        e.acquisitions.offers().filter { it.acquirer == company }.sortedByDescending { it.made.value }.map { row(it) }

    private fun row(o: app.many.economytycoon.companies.Offer) = OfferRow(
        id = o.id, acquirer = name(o.acquirer), acquirerId = o.acquirer, target = name(o.target), targetId = o.target,
        shares = o.shares, pricePerShare = o.pricePerShare, total = o.total,
        fairPricePerShare = e.acquisitions.fairPricePerShare(o.target, e.tick),
        expiresIn = (o.expires.value - e.tick.value).coerceAtLeast(0L), state = o.state,
    )

    private fun name(id: CompanyId) = e.companies.company(id)?.name ?: "Company ${id.value}"

    /** Share of a market held by each company that sells in it, by revenue-free proxy: facilities producing the good. */
    fun producersOf(good: app.many.economytycoon.core.GoodId): List<Pair<String, Int>> {
        val recipes = e.content.recipesProducing(good).map { it.id }.toSet()
        val counts = java.util.TreeMap<CompanyId, Int>()
        for (f in e.production.facilities()) {
            if (f.status == app.many.economytycoon.production.FacilityStatus.DEMOLISHED) continue
            if (f.lines.any { it.recipe in recipes }) counts.merge(f.owner, 1, Int::plus)
        }
        return counts.map { name(it.key) to it.value }.sortedByDescending { it.second }
    }
}
