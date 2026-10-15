package app.many.economytycoon.ui.present

import app.many.economytycoon.companies.OfferState
import app.many.economytycoon.competitors.Archetype
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class RivalItem(
    val id: CompanyId,
    val name: String,
    val style: String,
    val netWorth: String,
    val revenue: String,
    val profit: String,
    val profitTone: Tone,
    val staff: String,
    val standing: String,
    val standingTone: Tone,
    val yourStake: String?,
    val owner: String?,
)

data class OfferItem(
    val id: Long,
    val title: String,
    val price: String,
    val fairness: String,
    val fairnessTone: Tone,
    val expires: String,
    val state: String,
    val canRespond: Boolean,
    val canWithdraw: Boolean,
)

data class RivalsModel(val rivals: List<RivalItem>, val incoming: List<OfferItem>, val outgoing: List<OfferItem>)

data class RivalDetailModel(val item: RivalItem, val facts: List<Fact>, val industries: List<String>, val offers: List<OfferItem>)

object RivalsPresenter {
    fun archetypeLabel(a: Archetype?): String = when (a) {
        Archetype.DISCOUNTER -> "Price cutter"
        Archetype.PREMIUM -> "Premium brand"
        Archetype.BALANCED -> "All-rounder"
        Archetype.INNOVATOR -> "Innovator"
        Archetype.EXPANDER -> "Expander"
        Archetype.CONSERVATIVE -> "Cautious"
        null -> "Unknown style"
    }

    fun offerState(s: OfferState): String = Formatting.title(s.name.lowercase())

    private fun offerItem(session: GameSession, o: app.many.economytycoon.session.OfferRow, incoming: Boolean): OfferItem {
        val money = session.settings::money
        val fair = o.fairPricePerShare.raw
        val pct = if (fair <= 0L) 0L else (o.pricePerShare.raw - fair) * 1_000_000L / fair
        val pending = o.state == OfferState.PENDING
        return OfferItem(
            id = o.id,
            title = if (incoming) "${o.acquirer} offers for ${Formatting.plural(o.shares, "share")}" else "Your offer to ${o.target} for ${Formatting.plural(o.shares, "share")}",
            price = "${money(o.pricePerShare)} a share · ${money(o.total)} in all",
            fairness = Formatting.signedPercentPpm(pct) + " vs. fair value",
            fairnessTone = if (incoming) (if (pct >= 0) Tone.GOOD else Tone.WARN) else (if (pct >= 0) Tone.NEUTRAL else Tone.WARN),
            expires = if (pending) "expires in " + Formatting.duration(o.expiresIn) else "", state = offerState(o.state),
            canRespond = incoming && pending, canWithdraw = !incoming && pending,
        )
    }

    fun item(session: GameSession, row: app.many.economytycoon.session.RivalRow): RivalItem {
        val h = row.headline; val money = session.settings::money
        return RivalItem(
            h.id, h.name, archetypeLabel(row.archetype), money(h.netWorth), money(h.revenue), money(h.netIncome), Tones.bySign(h.netIncome.raw), Formatting.grouped(h.employees),
            OverviewPresenter.standing(h.rating, h.distress), OverviewPresenter.distressTone(h.distress),
            row.stakeHeldByPlayer.takeIf { it.ppm > 0 }?.let { Formatting.percent(it) }, row.controllerName,
        )
    }

    fun build(session: GameSession): RivalsModel {
        val company = session.selectedCompany
        val rivals = session.reports.rivals.rivals(company).map { item(session, it) }
        val incoming = company?.let { session.reports.rivals.offersFor(it).map { o -> offerItem(session, o, true) } }.orEmpty()
        val outgoing = company?.let { session.reports.rivals.offersBy(it).map { o -> offerItem(session, o, false) } }.orEmpty()
        return RivalsModel(rivals, incoming, outgoing)
    }

    fun detail(session: GameSession, id: CompanyId): RivalDetailModel? {
        val row = session.reports.rivals.rivals(session.selectedCompany).firstOrNull { it.headline.id == id } ?: return null
        val h = row.headline; val money = session.settings::money
        val facts = listOf(
            Fact("Based in", h.country), Fact("Style", archetypeLabel(row.archetype)), Fact("Cash", money(h.cash)), Fact("Debt", money(h.debt)),
            Fact("Facilities", h.facilities.toString()), Fact("Aggression", Formatting.percentPpm(row.aggressionPpm)), Fact("Appetite to expand", Formatting.percentPpm(row.expansionPpm)),
        )
        val offers = session.selectedCompany?.let { me -> session.reports.rivals.offersBy(me).filter { it.targetId == id }.map { offerItem(session, it, false) } }.orEmpty()
        return RivalDetailModel(item(session, row), facts, h.industries.map { Formatting.title(it.key) }, offers)
    }
}

/** Who owns what: stakes the selected company holds in others, and stakes others hold in it. */
data class StakeItem(val company: CompanyId, val name: String, val share: String, val controlled: Boolean)

data class OwnershipModel(val holdings: List<StakeItem>, val holders: List<StakeItem>, val controller: String?)

object OwnershipPresenter {
    fun build(session: GameSession): OwnershipModel? {
        val me = session.selectedCompany ?: return null
        val groups = session.economy.companies.groups
        fun name(id: CompanyId) = session.economy.companies.company(id)?.name ?: "Company ${id.value}"
        val holdings = groups.stakesHeldBy(me).sortedByDescending { it.second.ppm }.map { (id, rate) -> StakeItem(id, name(id), Formatting.percent(rate), groups.controllerOf(id) == me) }
        val holders = groups.stakesIn(me).sortedByDescending { it.second.ppm }.map { (id, rate) -> StakeItem(id, name(id), Formatting.percent(rate), groups.controllerOf(me) == id) }
        return OwnershipModel(holdings, holders, groups.controllerOf(me)?.let { name(it) })
    }
}
