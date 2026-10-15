package app.many.economytycoon.session

import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.EconomicEvent
import app.many.economytycoon.simulation.Economy
import app.many.economytycoon.simulation.TickSummary

enum class NewsKind { WORLD, MARKET, COMPANY, RESEARCH, FINANCE }

data class NewsItem(val tick: Long, val kind: NewsKind, val headline: String)

/**
 * Collects what happened into a short list of headlines. It reads tick summaries and the event history; it
 * keeps only the latest [capacity] items so a long game does not grow it without bound.
 */
class NewsFeed(private val e: Economy, private val capacity: Int = 200) {
    private val items = ArrayDeque<NewsItem>()
    private var lastEventSequence = -1L

    fun items(): List<NewsItem> = items.toList().reversed()

    fun record(summary: TickSummary, history: List<app.many.economytycoon.simulation.ScheduledEvent>) {
        val t = summary.tick.value
        for (s in history) {
            if (s.sequence <= lastEventSequence) continue
            lastEventSequence = s.sequence
            add(NewsItem(s.tick.value, kindOf(s.event), s.event.title))
        }
        if (summary.liquidations > 0) add(NewsItem(t, NewsKind.COMPANY, "${Formatting.plural(summary.liquidations.toLong(), "company")} went out of business"))
        if (summary.takeovers > 0) add(NewsItem(t, NewsKind.COMPANY, "${Formatting.plural(summary.takeovers.toLong(), "takeover")} completed"))
        if (summary.researchCompleted > 0) add(NewsItem(t, NewsKind.RESEARCH, "${Formatting.plural(summary.researchCompleted.toLong(), "technology")} researched"))
        for (ev in summary.exchangeEvents) {
            val name = e.companies.company(ev.issuer)?.name ?: "A company"
            when (ev) {
                is app.many.economytycoon.exchange.ExchangeEvent.Listed -> add(NewsItem(t, NewsKind.FINANCE, "$name floats on the stock exchange at ${Formatting.money(ev.price)} a share"))
                is app.many.economytycoon.exchange.ExchangeEvent.Delisted -> add(NewsItem(t, NewsKind.FINANCE, "$name leaves the stock exchange"))
                is app.many.economytycoon.exchange.ExchangeEvent.Surge -> add(NewsItem(t, NewsKind.FINANCE, "$name shares jump ${Formatting.percentPpm(ev.changePpm)}"))
                is app.many.economytycoon.exchange.ExchangeEvent.Slump -> add(NewsItem(t, NewsKind.FINANCE, "$name shares fall ${Formatting.percentPpm(-ev.changePpm)}"))
            }
        }
        for (ev in summary.tenderEvents) {
            val tender = e.tenders.get(ev.tender)
            val good = tender?.let { e.content.goodOrNull(it.good)?.name?.lowercase() } ?: "goods"
            val country = tender?.let { t -> e.world.countries().firstOrNull { it.id == t.country }?.name } ?: "A government"
            when (ev) {
                is app.many.economytycoon.tenders.TenderEvent.Awarded -> {
                    val name = e.companies.company(ev.winner)?.name ?: "A company"
                    add(NewsItem(t, NewsKind.MARKET, "$name wins the $country tender for $good at ${Formatting.money(ev.price)} a unit"))
                }
                is app.many.economytycoon.tenders.TenderEvent.Failed -> {
                    val name = e.companies.company(ev.winner)?.name ?: "A company"
                    add(NewsItem(t, NewsKind.MARKET, "$name fails to deliver $good to $country"))
                }
                is app.many.economytycoon.tenders.TenderEvent.NoBids -> add(NewsItem(t, NewsKind.MARKET, "$country finds no bidders for its $good tender"))
                is app.many.economytycoon.tenders.TenderEvent.Issued -> add(NewsItem(t, NewsKind.MARKET, "$country invites bids for $good"))
                else -> Unit
            }
        }
        for (ev in summary.grantEvents) {
            val program = e.grants.get(ev.program)
            val country = program?.let { g -> e.world.countries().firstOrNull { it.id == g.country }?.name } ?: "A government"
            when (ev) {
                is app.many.economytycoon.grants.GrantEvent.Announced -> {
                    val what = if (ev.kind == app.many.economytycoon.grants.GrantKind.JOBS) "a grant for new jobs" else "a grant for new ${program?.family?.displayName?.lowercase() ?: "industrial"} facilities"
                    add(NewsItem(t, NewsKind.FINANCE, "$country offers $what"))
                }
                is app.many.economytycoon.grants.GrantEvent.Settled -> if (ev.paid.raw > 0L) {
                    val name = e.companies.company(ev.company)?.name ?: "A company"
                    add(NewsItem(t, NewsKind.FINANCE, "$name receives ${Formatting.money(ev.paid)} in grants from $country"))
                }
                else -> Unit
            }
        }
        for (ev in summary.propertyEvents) {
            if (ev !is app.many.economytycoon.property.PropertyEvent.Sold) continue
            val parcel = e.world.land.get(ev.parcel) ?: continue
            val place = parcel.city?.let { e.world.cityOrNull(it)?.name } ?: e.world.region(parcel.region).name
            val buyer = e.companies.company(ev.buyer)?.name ?: "A company"
            val seller = e.companies.company(ev.seller)?.name ?: "a company"
            add(NewsItem(t, NewsKind.COMPANY, "$buyer buys land in $place from $seller for ${Formatting.compactMoney(ev.price)}"))
        }
        for (ev in summary.futuresEvents) {
            if (ev !is app.many.economytycoon.futures.FuturesEvent.Liquidated) continue
            val p = e.futures.get(ev.position) ?: continue
            val name = e.companies.company(ev.company)?.name ?: "A company"
            add(NewsItem(t, NewsKind.MARKET, "$name is closed out of its ${e.content.good(p.good).name} futures"))
        }
        for (ev in summary.unionEvents) {
            val union = e.unions.get(ev.union)
            val company = union?.let { e.companies.company(it.company)?.name } ?: "A company"
            val region = union?.let { u -> e.world.regions().firstOrNull { it.id == u.region }?.name } ?: "a region"
            when (ev) {
                is app.many.economytycoon.unions.UnionEvent.Formed -> add(NewsItem(t, NewsKind.COMPANY, "Workers at $company organise in $region"))
                is app.many.economytycoon.unions.UnionEvent.Disbanded -> add(NewsItem(t, NewsKind.COMPANY, "The union at $company in $region dissolves"))
                is app.many.economytycoon.unions.UnionEvent.Struck -> add(NewsItem(t, NewsKind.COMPANY, "Workers at $company walk out in $region for ${Formatting.plural(ev.days.toLong(), "day")}"))
                is app.many.economytycoon.unions.UnionEvent.Agreed -> add(NewsItem(t, NewsKind.COMPANY, "$company agrees a ${Formatting.percentPpm(ev.raisePpm)} raise with its union in $region"))
                is app.many.economytycoon.unions.UnionEvent.StrikeEnded -> add(NewsItem(t, NewsKind.COMPANY, "The strike at $company in $region ends"))
                is app.many.economytycoon.unions.UnionEvent.BackedDown -> add(NewsItem(t, NewsKind.COMPANY, "The union at $company in $region drops its wage demand"))
                is app.many.economytycoon.unions.UnionEvent.Demanded -> Unit
            }
        }
        for (ev in summary.bondEvents) {
            val bond = e.bonds.get(ev.bond)
            val country = bond?.let { b -> e.world.countries().firstOrNull { it.id == b.country }?.name } ?: "A government"
            when (ev) {
                is app.many.economytycoon.bonds.BondEvent.Issued -> add(NewsItem(t, NewsKind.FINANCE, "$country offers ${bond?.tenorDays ?: 0}-day bonds paying ${Formatting.percentPpm(bond?.couponPpm ?: 0L)}"))
                is app.many.economytycoon.bonds.BondEvent.Placed -> add(NewsItem(t, NewsKind.FINANCE, "$country raises ${Formatting.compactMoney(ev.raised)} from its bond sale"))
                is app.many.economytycoon.bonds.BondEvent.NoSale -> add(NewsItem(t, NewsKind.FINANCE, "$country finds no buyers for its bonds"))
                is app.many.economytycoon.bonds.BondEvent.Redeemed -> add(NewsItem(t, NewsKind.FINANCE, "$country repays ${Formatting.compactMoney(ev.repaid)} of maturing bonds"))
                is app.many.economytycoon.bonds.BondEvent.Coupon -> Unit
            }
        }
        for (ev in summary.regulationEvents) {
            val country = e.world.countries().firstOrNull { it.id == ev.country }?.name ?: "A country"
            val law = when (ev) {
                is app.many.economytycoon.regulation.RegulationEvent.Proposed -> e.regulation.law(ev.law)
                is app.many.economytycoon.regulation.RegulationEvent.Passed -> e.regulation.law(ev.law)
                is app.many.economytycoon.regulation.RegulationEvent.Rejected -> e.regulation.law(ev.law)
                is app.many.economytycoon.regulation.RegulationEvent.Enacted -> e.regulation.law(ev.law)
                else -> null
            }
            val what = law?.let { Formatting.lawSummary(it) } ?: "a law"
            when (ev) {
                is app.many.economytycoon.regulation.RegulationEvent.Proposed -> add(NewsItem(t, NewsKind.WORLD, "$country's government proposes $what"))
                is app.many.economytycoon.regulation.RegulationEvent.Passed -> add(NewsItem(t, NewsKind.WORLD, "$country's parliament passes $what"))
                is app.many.economytycoon.regulation.RegulationEvent.Rejected -> add(NewsItem(t, NewsKind.WORLD, "$country's parliament rejects $what"))
                is app.many.economytycoon.regulation.RegulationEvent.Enacted -> add(NewsItem(t, NewsKind.WORLD, "$country puts $what into force"))
                is app.many.economytycoon.regulation.RegulationEvent.Election -> {
                    val result = if (ev.from == ev.to) "re-elects its ${ev.to.label.lowercase()} government" else "swings from ${ev.from.label.lowercase()} to ${ev.to.label.lowercase()} government"
                    add(NewsItem(t, NewsKind.WORLD, "$country $result"))
                }
                is app.many.economytycoon.regulation.RegulationEvent.Fined -> {
                    val name = e.companies.company(ev.fine.company)?.name ?: "A company"
                    val tail = if (ev.fine.closed) "; the plant is ordered shut" else ""
                    add(NewsItem(t, NewsKind.COMPANY, "$name is fined ${Formatting.money(ev.fine.amount)} for pollution$tail"))
                }
            }
        }
        for (ev in summary.riskEvents) {
            when (ev) {
                is app.many.economytycoon.risk.RiskEvent.Struck -> {
                    val d = ev.disaster
                    val region = e.world.regions().firstOrNull { it.id == d.region }?.name ?: "a region"
                    val verb = when (d.peril) {
                        app.many.economytycoon.risk.Peril.FIRE -> "A fire broke out in"
                        app.many.economytycoon.risk.Peril.STORM -> "A storm hit"
                        app.many.economytycoon.risk.Peril.FLOOD -> "Floods struck"
                        app.many.economytycoon.risk.Peril.QUAKE -> "An earthquake shook"
                    }
                    add(NewsItem(t, NewsKind.WORLD, "$verb $region; ${Formatting.plural(d.facilitiesHit.toLong(), "facility")} damaged"))
                }
                is app.many.economytycoon.risk.RiskEvent.Lapsed -> {
                    val name = e.companies.company(ev.holder)?.name ?: "A company"
                    add(NewsItem(t, NewsKind.COMPANY, "$name let a fire and storm policy lapse"))
                }
                else -> Unit
            }
        }
        for (ev in summary.contractEvents) {
            when (ev) {
                is app.many.economytycoon.contracts.ContractEvent.Breached -> {
                    val c = e.contracts.get(ev.contract)
                    val name = e.companies.company(ev.at)?.name ?: "A company"
                    val other = c?.other(ev.at)?.let { e.companies.company(it)?.name } ?: "a partner"
                    add(NewsItem(t, NewsKind.COMPANY, "$name broke its supply contract with $other"))
                }
                is app.many.economytycoon.contracts.ContractEvent.Fulfilled -> {
                    val c = e.contracts.get(ev.contract)
                    if (c != null) {
                        val a = e.companies.company(c.seller)?.name ?: "A company"
                        val b = e.companies.company(c.buyer)?.name ?: "a customer"
                        add(NewsItem(t, NewsKind.COMPANY, "$a completed a supply contract with $b"))
                    }
                }
                else -> Unit
            }
        }
        if (summary.closedBooks) add(NewsItem(t, NewsKind.FINANCE, "Companies closed their books for the month"))
        if (summary.problems.isNotEmpty()) add(NewsItem(t, NewsKind.WORLD, "Ledger check found ${Formatting.plural(summary.problems.size.toLong(), "problem")}"))
    }

    private fun kindOf(ev: EconomicEvent): NewsKind = when (ev) {
        is EconomicEvent.SupplyShock, is EconomicEvent.DemandShift, is EconomicEvent.DemandRestored -> NewsKind.MARKET
        is EconomicEvent.ConfidenceShock -> NewsKind.WORLD
    }

    /** Adds a headline that did not come from a tick summary. */
    fun post(tick: Tick, kind: NewsKind, headline: String) = add(NewsItem(tick.value, kind, headline))

    private fun add(item: NewsItem) {
        items.addLast(item)
        while (items.size > capacity) items.removeFirst()
    }

    fun since(tick: Tick): List<NewsItem> = items().filter { it.tick >= tick.value }
}
