package app.many.economytycoon.agenda

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.LoanStatus
import app.many.economytycoon.simulation.Economy

/** Collects what is due for one company in the coming days from loans, contracts, tenders and grants. */
class AgendaBuilder(private val e: Economy) {
    fun build(company: CompanyId, now: Long, horizon: Int = DEFAULT_DAYS): Agenda {
        val end = now + horizon
        val items = ArrayList<AgendaItem>()
        loans(company, now, end, items)
        contracts(company, now, end, items)
        tenders(company, now, end, items)
        grants(company, now, end, items)
        carbon(company, now, end, items)
        val sorted = items.sortedWith(compareBy<AgendaItem>({ it.day }, { it.kind.ordinal }, { it.title }))
        return Agenda(now, end, sorted)
    }

    private fun loans(company: CompanyId, now: Long, end: Long, out: MutableList<AgendaItem>) {
        val owner = app.many.economytycoon.finance.LedgerOwner.company(company)
        for (l in e.finance.loans.activeFor(owner)) {
            if (l.status != LoanStatus.ACTIVE) continue
            val made = l.periods - l.paymentsRemaining
            val next = l.startTick.value + (made + 1L) * l.paymentInterval
            if (next in now..end) {
                out += AgendaItem(
                    day = next, kind = AgendaKind.LOAN_PAYMENT, title = "Loan ${l.id.value}",
                    detail = "Payment ${made + 1} of ${l.periods}",
                    cash = Money(-l.scheduledPayment.raw), risky = true,
                )
            }
            val last = l.maturity.value
            if (last in now..end) out += AgendaItem(last, AgendaKind.LOAN_MATURITY, "Loan ${l.id.value}", "Final payment clears the loan")
        }
    }

    private fun contracts(company: CompanyId, now: Long, end: Long, out: MutableList<AgendaItem>) {
        for (c in e.contracts.activeOf(company)) {
            val due = c.nextDue?.value ?: continue
            if (due !in now..end) continue
            val selling = c.seller == company
            val goodName = e.content.goodOrNull(c.terms.good)?.name ?: c.terms.good.key
            val value = c.terms.deliveryValue
            out += AgendaItem(
                day = due, kind = if (selling) AgendaKind.DELIVERY_OUT else AgendaKind.DELIVERY_IN,
                title = goodName, detail = "Contract ${c.id.value}, ${c.remainingDeliveries} left",
                cash = if (selling) value else Money(-value.raw), good = c.terms.good, risky = selling,
            )
        }
    }

    private fun tenders(company: CompanyId, now: Long, end: Long, out: MutableList<AgendaItem>) {
        for (t in e.tenders.biddingBy(company)) {
            val closes = t.closesAt.value
            if (closes in now..end) out += AgendaItem(closes, AgendaKind.TENDER_CLOSES, nameOf(t.good), "Your bid is in; the award follows", good = t.good)
        }
        for (t in e.tenders.awardedTo(company)) {
            val due = t.deliverBy.value
            if (due in now..end) out += AgendaItem(due, AgendaKind.TENDER_DUE, nameOf(t.good), "Deliver the full amount or pay a penalty", cash = t.awardedValue, good = t.good, risky = true)
        }
    }

    private fun carbon(company: CompanyId, now: Long, end: Long, out: MutableList<AgendaItem>) {
        if (!e.carbonRunner.active || e.carbonState.compliance.accountsOf(company).isEmpty()) return
        val view = e.carbonViews.company(company, app.many.economytycoon.core.Tick(now))
        for (a in view.accounts) {
            val p = a.pending
            if (p != null && p.outstanding.isPositive && p.deadline.value in now..end) {
                out += AgendaItem(p.deadline.value, AgendaKind.CARBON_DEADLINE, "Carbon ${p.year}", "${p.outstanding.format()} t to surrender or offset", risky = true)
            }
            val auction = e.carbonViews.market(a.country, app.many.economytycoon.core.Tick(now))?.nextAuction?.value ?: continue
            if (a.kind == app.many.economytycoon.carbon.RegimeKind.CAP_AND_TRADE && auction in now..end) {
                out += AgendaItem(auction, AgendaKind.PERMIT_AUCTION, "Permit auction", "Bids must be in before the day")
            }
        }
    }

    private fun grants(company: CompanyId, now: Long, end: Long, out: MutableList<AgendaItem>) {
        for (g in e.grants.open()) {
            val closes = g.applyUntil.value
            if (g.applicationOf(company) == null && closes in now..end) out += AgendaItem(closes, AgendaKind.GRANT_CLOSES, "Grant ${g.id.value}", "Last day to apply")
        }
        for (g in e.grants.active()) {
            if (g.applicationOf(company) == null) continue
            val ends = g.endsAt.value
            if (ends in now..end) out += AgendaItem(ends, AgendaKind.GRANT_ENDS, "Grant ${g.id.value}", "Growth is counted and paid out")
        }
    }

    private fun nameOf(good: app.many.economytycoon.core.GoodId): String = e.content.goodOrNull(good)?.name ?: good.key

    companion object { const val DEFAULT_DAYS = 90 }
}
