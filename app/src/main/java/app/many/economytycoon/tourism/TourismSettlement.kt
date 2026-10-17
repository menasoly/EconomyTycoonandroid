package app.many.economytycoon.tourism

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.finance.TaxIntents

/**
 * Turns a month of tourism into ledger intents and then clears the month's totals. Nothing is posted here: the intents
 * go back to the caller. Keys are unique per month, so a replay of the same month cannot pay twice.
 *
 * Who pays whom: guests (households of the destination's region for the domestic share, the outside world for the
 * rest) pay property owners for rooms and extras and attraction owners for tickets; operators pay property owners for
 * the rooms they sold and for committed rooms they did not; operators' customers pay operators; owners pay
 * intermediaries commission, suppliers running costs, households wages, and the government value added tax and levy.
 */
internal object TourismSettlement {

    fun run(state: TourismState, tick: Tick): SettlementRun {
        val month = tick.value / Calendar.DAYS_PER_MONTH
        val intents = ArrayList<SettlementIntent>()
        val statements = ArrayList<PropertyStatement>()
        val attractionStatements = ArrayList<AttractionStatement>()
        for (p in state.propertyMap.values) statements += settleProperty(state, p, month, intents)
        // Operators read the month's sales off the allotments, so they settle first; the allotments then clear them.
        for (op in state.operatorMap.values) settleOperator(state, op, month, intents)
        for (a in state.allotmentMap.values) settleAllotment(state, a, month, intents)
        for (a in state.attractionMap.values) attractionStatements += settleAttraction(state, a, month, intents)
        for (i in state.intermediaryMap.values) { i.monthCommission = Money.ZERO; i.monthRooms = 0L }
        return SettlementRun(month, intents, statements, attractionStatements)
    }

    private fun add(
        out: MutableList<SettlementIntent>, key: String, payer: LedgerOwner, payee: LedgerOwner, amount: Money, priority: SettlementPriority,
        reason: ReasonCode, debit: Chart, credit: Chart, deferrable: Boolean, memo: String,
    ) {
        if (amount.raw <= 0L || payer == payee) return
        out += SettlementIntent.simple(key, payer, payee, amount, priority, reason, debit, credit, deferrable, memo)
    }

    private fun owner(c: app.many.economytycoon.core.CompanyId?): LedgerOwner = c?.let { LedgerOwner.company(it) } ?: LedgerOwner.SYSTEM

    private fun settleProperty(state: TourismState, p: Property, month: Long, out: MutableList<SettlementIntent>): PropertyStatement {
        val m = p.month
        val dest = state.destination(p.destination)
        val vat = dest?.let { TourismTax.vatInside(m.guestRoomRevenue + m.operatorRoomRevenue + m.ancillaryRevenue, it.vatPpm) } ?: Money.ZERO
        var listing = Money.ZERO
        for (id in p.listed) listing += state.intermediary(id)?.listingFeePerMonth ?: Money.ZERO
        val statement = PropertyStatement(
            p.id, m.guestRoomRevenue, m.operatorRoomRevenue, m.ancillaryRevenue, m.commissions, m.walkCost, m.operatingCost, m.wageCost,
            m.levy, vat, listing, m.roomsSold, m.roomsAvailable,
        )
        val company = p.owner
        if (company != null && dest != null) {
            val me = LedgerOwner.company(company)
            val households = LedgerOwner.households(dest.region.value)
            val guestPay = m.guestRoomRevenue + m.ancillaryRevenue
            val gross = guestPay + m.levy
            val domShare = if (guestPay.raw > 0L) mulDiv(m.domesticRevenue.raw, 1_000_000L, guestPay.raw).coerceIn(0L, 1_000_000L) else 0L
            val domestic = Money(mulDiv(gross.raw, domShare, 1_000_000L))
            val key = "tour:rev:${p.id.value}:$month"
            add(out, "$key:dom", households, me, domestic, SettlementPriority.OTHER, ReasonCode.SALE, Chart.OTHER_EXPENSE, Chart.REVENUE_SERVICES, false, "${p.name} guests from home")
            add(out, "$key:for", LedgerOwner.SYSTEM, me, gross - domestic, SettlementPriority.OTHER, ReasonCode.SALE, Chart.OTHER_EXPENSE, Chart.REVENUE_SERVICES, false, "${p.name} guests from abroad")
            for (id in m.commissionIntermediaries) {
                val i = state.intermediary(id) ?: continue
                add(out, "tour:comm:${p.id.value}:${id.value}:$month", me, owner(i.owner), m.commissionOf(id), SettlementPriority.SUPPLIERS, ReasonCode.FEE,
                    Chart.MARKETING_EXPENSE, Chart.REVENUE_SERVICES, true, "commission to ${i.name}")
            }
            for (id in p.listed) {
                val i = state.intermediary(id) ?: continue
                add(out, "tour:list:${p.id.value}:${id.value}:$month", me, owner(i.owner), i.listingFeePerMonth, SettlementPriority.SUPPLIERS, ReasonCode.FEE,
                    Chart.MARKETING_EXPENSE, Chart.REVENUE_SERVICES, true, "listing fee to ${i.name}")
            }
            add(out, "tour:run:${p.id.value}:$month", me, LedgerOwner.SYSTEM, m.operatingCost, SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE,
                Chart.MAINTENANCE_EXPENSE, Chart.REVENUE_SALES, true, "running costs of ${p.name}")
            add(out, "tour:wage:${p.id.value}:$month", me, households, m.wageCost, SettlementPriority.WAGES, ReasonCode.WAGES,
                Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER, true, "staff of ${p.name}")
            add(out, "tour:walk:${p.id.value}:$month", me, LedgerOwner.SYSTEM, m.walkCost, SettlementPriority.OTHER, ReasonCode.FEE,
                Chart.OTHER_EXPENSE, Chart.REVENUE_OTHER, true, "guests walked from ${p.name}")
            val gov = LedgerOwner.government(dest.country)
            if (vat.raw > 0L) out += TaxIntents.sales("tour:vat:${p.id.value}:$month", me, gov, vat)
            if (m.levy.raw > 0L) out += TaxIntents.sales("tour:levy:${p.id.value}:$month", me, gov, m.levy)
        }
        m.clear()
        return statement
    }

    /** The operator pays for the rooms it sold and for the committed rooms it did not. */
    private fun settleAllotment(state: TourismState, a: Allotment, month: Long, out: MutableList<SettlementIntent>) {
        val p = state.property(a.property)
        val op = state.operator(a.operator)
        if (p != null && op != null && p.owner != null) {
            val due = a.monthSoldValue + a.monthShortfallValue
            add(out, "tour:allot:${a.id.value}:$month", owner(op.owner), owner(p.owner), due, SettlementPriority.SUPPLIERS, ReasonCode.SALE,
                Chart.COGS, Chart.REVENUE_SERVICES, true, "rooms of allotment ${a.id.value}")
        }
        a.monthSold = 0L; a.monthSoldValue = Money.ZERO; a.monthShortfallRooms = 0L; a.monthShortfallValue = Money.ZERO; a.monthReleased = 0L
    }

    /** Customers pay the operator the package price of the rooms it sold. */
    private fun settleOperator(state: TourismState, op: TourOperator, month: Long, out: MutableList<SettlementIntent>) {
        var revenue = Money.ZERO
        for (a in state.allotmentsOfOperator(op.id)) revenue += AllotmentRules.packagePrice(a.monthSoldValue, op.marginPpm)
        if (op.owner != null && revenue.raw > 0L) {
            add(out, "tour:pkg:${op.id.value}:$month", LedgerOwner.SYSTEM, LedgerOwner.company(op.owner), revenue, SettlementPriority.OTHER, ReasonCode.SALE,
                Chart.OTHER_EXPENSE, Chart.REVENUE_SALES, false, "packages sold by ${op.name}")
        }
        op.monthRooms = 0L; op.monthMargin = Money.ZERO
    }

    private fun settleAttraction(state: TourismState, a: Attraction, month: Long, out: MutableList<SettlementIntent>): AttractionStatement {
        val dest = state.destination(a.destination)
        val vat = dest?.let { TourismTax.vatInside(a.monthRevenue, it.vatPpm) } ?: Money.ZERO
        val statement = AttractionStatement(a.id, a.monthRevenue, a.monthCost, vat, a.monthVisits)
        val company = a.owner
        if (company != null && dest != null) {
            val me = LedgerOwner.company(company)
            val domShare = dest.domesticSharePpm
            val domestic = Money(mulDiv(a.monthRevenue.raw, domShare, 1_000_000L))
            val key = "tour:tix:${a.id.value}:$month"
            add(out, "$key:dom", LedgerOwner.households(dest.region.value), me, domestic, SettlementPriority.OTHER, ReasonCode.SALE, Chart.OTHER_EXPENSE, Chart.REVENUE_SERVICES, false, "tickets to ${a.name}")
            add(out, "$key:for", LedgerOwner.SYSTEM, me, a.monthRevenue - domestic, SettlementPriority.OTHER, ReasonCode.SALE, Chart.OTHER_EXPENSE, Chart.REVENUE_SERVICES, false, "tickets to ${a.name}")
            add(out, "tour:att:${a.id.value}:$month", me, LedgerOwner.SYSTEM, a.monthCost, SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE,
                Chart.MAINTENANCE_EXPENSE, Chart.REVENUE_SALES, true, "running costs of ${a.name}")
            if (vat.raw > 0L) out += TaxIntents.sales("tour:attvat:${a.id.value}:$month", me, LedgerOwner.government(dest.country), vat)
        }
        a.monthRevenue = Money.ZERO; a.monthCost = Money.ZERO; a.monthVisits = 0L
        return statement
    }
}
