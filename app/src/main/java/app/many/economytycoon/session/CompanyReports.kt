package app.many.economytycoon.session

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.CashFlowStatement
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.DistressState
import app.many.economytycoon.finance.IncomeStatement
import app.many.economytycoon.finance.LoanStatus
import app.many.economytycoon.finance.Ratios
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.simulation.Economy

/** One company on the overview screen. */
data class CompanyHeadline(
    val id: CompanyId,
    val name: String,
    val kind: CompanyKind,
    val status: CompanyStatus,
    val country: String,
    val cash: Money,
    val netWorth: Money,
    val debt: Money,
    val revenue: Money,
    val netIncome: Money,
    val employees: Long,
    val facilities: Int,
    val industries: List<IndustryId>,
    val rating: CreditRating?,
    val distress: DistressState?,
)

data class FacilityRow(
    val id: FacilityId,
    val name: String,
    val typeName: String,
    val region: String,
    val status: FacilityStatus,
    val conditionPpm: Long,
    val utilizationPpm: Long,
    val activeLines: Int,
    val lineCapacity: Int,
    val stockValue: Money,
    val headcount: Long,
    val blockedReason: String?,
)

data class StockRow(val good: GoodId, val name: String, val quantity: Quantity, val reserved: Quantity, val unitCost: Money, val value: Money)

data class LoanRow(
    val id: LoanId,
    val outstanding: Money,
    val principal: Money,
    val annualRate: Rate,
    val status: LoanStatus,
    val maturity: Tick,
    val nextPayment: Money,
    val missedPayments: Int,
)

/** Everything the finance screen shows for one company. */
class FinancePack(
    val company: CompanyId,
    val asOf: Tick,
    val month: IncomeStatement,
    val year: IncomeStatement,
    val balance: BalanceSheet,
    val cashFlow: CashFlowStatement,
    val ratios: Ratios,
    val rating: CreditRating?,
    val distress: DistressState?,
    val loans: List<LoanRow>,
    val creditLimit: Money?,
    val borrowRate: Rate?,
)

/** Read-only company views built from the live economy. Nothing here changes game state. */
class CompanyReports(private val e: Economy) {
    private val month = 30L
    private val year = 360L

    fun headline(id: CompanyId): CompanyHeadline? {
        val c = e.companies.company(id) ?: return null
        val now = e.tick
        val from = Tick((now.value - month).coerceAtLeast(0L))
        val income = e.finance.statements.income(c.owner, from, now)
        val balance = e.finance.statements.balance(c.owner, now)
        val fin = e.finance.company(c.owner)
        return CompanyHeadline(
            id = id, name = c.name, kind = c.kind, status = c.status,
            country = e.world.country(c.country).name,
            cash = e.finance.ledger.cash(c.owner), netWorth = balance.netWorth, debt = e.finance.loans.debtOf(c.owner),
            revenue = income.revenue, netIncome = income.netIncome,
            employees = e.workforce.employedBy(id), facilities = e.production.facilitiesOf(id).size,
            industries = c.industries.toList(), rating = fin?.rating?.rating, distress = fin?.distress?.state,
        )
    }

    /** Active companies, richest first, with the player's own at the top when asked. */
    fun leaderboard(playerFirst: Boolean = false): List<CompanyHeadline> {
        val rows = e.companies.active().mapNotNull { headline(it.id) }
        val sorted = rows.sortedWith(compareByDescending<CompanyHeadline> { it.netWorth.raw }.thenBy { it.id })
        return if (playerFirst) sorted.sortedBy { if (it.kind == CompanyKind.PLAYER) 0 else 1 } else sorted
    }

    fun finance(id: CompanyId): FinancePack? {
        val c = e.companies.company(id) ?: return null
        val now = e.tick
        fun since(days: Long) = Tick((now.value - days).coerceAtLeast(0L))
        val st = e.finance.statements
        val fin = e.finance.company(c.owner)
        val terms = if (fin != null) e.finance.creditTerms(c.owner, now) else null
        val loans = e.finance.loans.forBorrower(c.owner).filter { it.status != LoanStatus.PAID_OFF }.map {
            LoanRow(it.id, it.outstanding, it.principal, it.annualRate, it.status, it.maturity, it.scheduledPayment, it.missedPayments)
        }
        return FinancePack(
            company = id, asOf = now,
            month = st.income(c.owner, since(month), now), year = st.income(c.owner, since(year), now),
            balance = st.balance(c.owner, now), cashFlow = st.cashFlow(c.owner, since(month), now),
            ratios = st.ratios(c.owner, since(month), now, periodsPerYear = year / month),
            rating = fin?.rating?.rating, distress = fin?.distress?.state, loans = loans,
            creditLimit = terms?.limit, borrowRate = terms?.rate,
        )
    }

    fun facilities(id: CompanyId): List<FacilityRow> =
        e.production.facilitiesOf(id).map { f ->
            val type = e.content.facilityTypeOrNull(f.type)
            val enabled = f.lines.filter { it.enabled }
            val active = enabled.count { it.batch != null }
            val util = if (enabled.isEmpty()) 0L else enabled.sumOf { it.utilizationPpm } / enabled.size
            val blocked = enabled.firstOrNull { it.blocked != app.many.economytycoon.production.Blocked.None }?.blocked
            FacilityRow(
                id = f.id, name = f.name.ifBlank { type?.name ?: Formatting.title(f.type.key) }, typeName = type?.name ?: f.type.key,
                region = e.world.region(f.region).name, status = f.status, conditionPpm = f.conditionPpm,
                utilizationPpm = util, activeLines = active, lineCapacity = type?.maxLines ?: f.lines.size,
                stockValue = f.inventory.totalValue(), headcount = e.workforce.cohortsAt(f.id).sumOf { it.headcount },
                blockedReason = blocked?.let { describe(it) },
            )
        }.sortedBy { it.id }

    fun stock(facility: FacilityId): List<StockRow> {
        val f = e.production.facility(facility) ?: return emptyList()
        return f.inventory.entries().filter { it.second.quantity.raw > 0 }.map { (good, slot) ->
            StockRow(good, e.content.good(good).name, slot.quantity, slot.reserved, slot.unitCost, slot.unitCost.valueOf(slot.quantity))
        }.sortedBy { it.name }
    }

    /** Companies of the player, in the order they were founded. */
    fun playerCompanies(): List<CompanyHeadline> =
        e.companies.ofKind(CompanyKind.PLAYER).sortedBy { it.id }.mapNotNull { headline(it.id) }

    fun regionName(id: RegionId): String = e.world.region(id).name

    private fun describe(b: app.many.economytycoon.production.Blocked): String = b.javaClass.simpleName.let { Formatting.title(it.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()) }
}
