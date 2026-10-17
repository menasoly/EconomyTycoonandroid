package app.many.economytycoon.agenda

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money

/** What kind of date is coming up. */
enum class AgendaKind(val label: String) {
    LOAN_PAYMENT("Loan payment"),
    LOAN_MATURITY("Loan ends"),
    DELIVERY_OUT("Delivery to send"),
    DELIVERY_IN("Delivery expected"),
    TENDER_CLOSES("Tender closes"),
    TENDER_DUE("Tender delivery due"),
    GRANT_CLOSES("Grant applications close"),
    GRANT_ENDS("Grant period ends"),
    CARBON_DEADLINE("Carbon deadline"),
    PERMIT_AUCTION("Permit auction"),
}

/** One dated thing the company has to do or expects, with the money moving on that day if known. */
data class AgendaItem(
    /** Game day it falls on. */
    val day: Long,
    val kind: AgendaKind,
    val title: String,
    val detail: String,
    /** Money that leaves (negative) or arrives (positive) on the day; null if not fixed beforehand. */
    val cash: Money? = null,
    val good: GoodId? = null,
    /** True when missing the date costs the company something. */
    val risky: Boolean = false,
)

/** Items in date order, with the bits that planning cares about. */
data class Agenda(val from: Long, val to: Long, val items: List<AgendaItem>) {
    fun between(first: Long, last: Long): List<AgendaItem> = items.filter { it.day in first..last }
    fun byDay(): Map<Long, List<AgendaItem>> = items.groupBy { it.day }.toSortedMap()
    fun of(kind: AgendaKind): List<AgendaItem> = items.filter { it.kind == kind }

    /** Money known to be leaving within the first [days] days. */
    fun outflow(days: Long): Money = Money.sum(items.filter { it.day < from + days && (it.cash?.raw ?: 0L) < 0L }.map { it.cash!! })
    fun inflow(days: Long): Money = Money.sum(items.filter { it.day < from + days && (it.cash?.raw ?: 0L) > 0L }.map { it.cash!! })
    val next: AgendaItem? get() = items.firstOrNull()
    val risky: List<AgendaItem> get() = items.filter { it.risky }
}
