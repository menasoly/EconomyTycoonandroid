package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.splitLargestRemainder
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.ClaimClass
import app.many.economytycoon.finance.DistressState
import app.many.economytycoon.finance.Distribution
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.FinanceService
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.LiquidationWaterfall
import app.many.economytycoon.finance.LoanStatus
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft

enum class CasePhase { ASSESSMENT, RESTRUCTURING, LIQUIDATION, CLOSED }

enum class CaseOutcome { RECOVERED, LIQUIDATED }

data class InsolvencyParams(
    val assessmentTicks: Int = 5,
    /** Debt payments are suspended this long while the company tries to recover. */
    val standstillTicks: Int = 45,
)

class InsolvencyCase(val company: CompanyId, val opened: Tick) {
    var phase: CasePhase = CasePhase.ASSESSMENT
        internal set
    var phaseSince: Tick = opened
        internal set
    var outcome: CaseOutcome? = null
        internal set
    var closed: Tick? = null
        internal set

    internal fun restore(phase: CasePhase, since: Tick, outcome: CaseOutcome?, closed: Tick?) {
        this.phase = phase; phaseSince = since; this.outcome = outcome; this.closed = closed
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(company.value).putLong(opened.value).putInt(phase.ordinal).putLong(phaseSince.value)
            .putInt(outcome?.ordinal ?: -1).putLong(closed?.value ?: -1L)
    }
}

class LiquidationResult(val distributions: List<Distribution>, val residualToShareholders: Money, val debtWrittenOff: Money)

/**
 * Administration of failing companies. A case starts with a short assessment; viable companies get a
 * debt standstill to recover, the rest are wound up. Winding up pays creditors by class from the cash
 * the company holds (the simulation sells its assets first) and returns any surplus to shareholders.
 */
class InsolvencyService(
    private val finance: FinanceService,
    private val companies: CompanyService,
    val params: InsolvencyParams = InsolvencyParams(),
) {
    private val cases = java.util.TreeMap<CompanyId, InsolvencyCase>()

    fun case(id: CompanyId): InsolvencyCase? = cases[id]
    fun cases(): List<InsolvencyCase> = cases.values.toList()
    fun openCases(): List<InsolvencyCase> = cases.values.filter { it.phase != CasePhase.CLOSED }

    /** True while debt service should be paused for the company. */
    fun inStandstill(id: CompanyId): Boolean = cases[id]?.phase == CasePhase.RESTRUCTURING

    fun open(id: CompanyId, tick: Tick): Outcome<InsolvencyCase> {
        val c = companies.company(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (!c.isActive) return Outcome.reject(Rules.WRONG_STATE, "company is no longer trading")
        if (cases[id]?.phase.let { it != null && it != CasePhase.CLOSED }) return Outcome.reject(Rules.DUPLICATE, "a case is already open")
        val case = InsolvencyCase(id, tick)
        cases[id] = case
        companies.setStatus(id, CompanyStatus.ADMINISTRATION, tick)
        return Outcome.ok(case)
    }

    /** Opens cases for companies whose distress monitor has reached insolvency. */
    fun scanForInsolvency(tick: Tick): List<CompanyId> {
        val opened = ArrayList<CompanyId>()
        for (c in companies.active()) {
            if (c.kind == CompanyKind.STATE || c.status != CompanyStatus.ACTIVE) continue
            val fin = finance.company(c.owner) ?: continue
            if (fin.distress.state == DistressState.INSOLVENT && open(c.id, tick).isOk) opened += c.id
        }
        return opened
    }

    fun step(tick: Tick) {
        for (case in cases.values.toList()) {
            if (case.phase == CasePhase.CLOSED) continue
            val state = finance.company(companies.require(case.company).owner)?.distress?.state ?: DistressState.INSOLVENT
            val age = tick - case.phaseSince
            when (case.phase) {
                CasePhase.ASSESSMENT -> if (age >= params.assessmentTicks) {
                    val stmt = finance.latestStatements(companies.require(case.company).owner)
                    val viable = stmt != null && stmt.income.ebitda.raw > 0 && stmt.balance.cash.raw > 0
                    enter(case, if (viable) CasePhase.RESTRUCTURING else CasePhase.LIQUIDATION, tick)
                }
                CasePhase.RESTRUCTURING -> {
                    if (state == DistressState.HEALTHY || state == DistressState.WATCH) {
                        case.outcome = CaseOutcome.RECOVERED; case.closed = tick; case.phase = CasePhase.CLOSED; case.phaseSince = tick
                        companies.setStatus(case.company, CompanyStatus.ACTIVE, tick)
                    } else if (age >= params.standstillTicks) enter(case, CasePhase.LIQUIDATION, tick)
                }
                CasePhase.LIQUIDATION, CasePhase.CLOSED -> Unit
            }
        }
    }

    private fun enter(case: InsolvencyCase, phase: CasePhase, tick: Tick) {
        case.phase = phase; case.phaseSince = tick
        if (phase == CasePhase.LIQUIDATION) {
            companies.setStatus(case.company, CompanyStatus.LIQUIDATING, tick)
            finance.company(companies.require(case.company).owner)?.distress?.beginLiquidation()
        }
    }

    /** Companies waiting for the simulation to sell their assets and call [liquidate]. */
    fun awaitingLiquidation(): List<CompanyId> = cases.values.filter { it.phase == CasePhase.LIQUIDATION }.map { it.company }

    /** Pays out the company's cash by priority, returns the surplus to shareholders and ends the company. */
    fun liquidate(id: CompanyId, tick: Tick): Outcome<LiquidationResult> {
        val company = companies.company(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (company.status == CompanyStatus.DISSOLVED) return Outcome.reject(Rules.WRONG_STATE, "already dissolved")
        val result = windUp(company, tick)
        cases[id]?.let { it.outcome = CaseOutcome.LIQUIDATED; it.closed = tick; it.phase = CasePhase.CLOSED; it.phaseSince = tick }
        return Outcome.ok(result)
    }

    /** Voluntary closure of a company that can pay everything it owes. */
    fun dissolveVoluntarily(id: CompanyId, tick: Tick): Outcome<LiquidationResult> {
        val company = companies.company(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (!company.isActive) return Outcome.reject(Rules.WRONG_STATE, "company is not trading")
        val owner = company.owner
        val debt = finance.loans.debtOf(owner) + finance.settlement.totalArrears(owner)
        if (finance.ledger.cash(owner) < debt) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cash does not cover debts; use insolvency instead")
        return Outcome.ok(windUp(company, tick))
    }

    private fun windUp(company: Company, tick: Tick): LiquidationResult {
        val owner = company.owner
        val ledger = finance.ledger
        val government = finance.company(owner)?.government ?: finance.defaultGovernment()
        val claims = LiquidationWaterfall.claimsOf(owner, ledger, finance.loans, finance.settlement.arrears(), government, finance.households)
        val dist = LiquidationWaterfall.distribute(ledger.cash(owner), claims)
        var written = Money.ZERO

        val pool = HashMap<Triple<LedgerOwner, ClaimClass, Money>, ArrayDeque<String>>()
        for (c in claims.sortedBy { it.label }) pool.getOrPut(Triple(c.claimant, c.cls, c.amount)) { ArrayDeque() }.addLast(c.label)
        var n = 0
        for (d in dist) {
            val label = pool[Triple(d.claimant, d.cls, d.claim)]?.removeFirstOrNull() ?: ""
            if (label.startsWith("loan ")) {
                val loan = finance.loans.get(LoanId(label.removePrefix("loan ").toLong())) ?: continue
                val pay = Money.min(d.paid, loan.outstanding)
                if (pay.raw > 0) finance.loans.prepay(tick, loan.id, pay)
                if (loan.outstanding.raw > 0 || loan.status == LoanStatus.ACTIVE) {
                    written += loan.outstanding
                    loan.status = LoanStatus.DEFAULTED
                    finance.loans.writeOff(tick, loan.id)
                }
                continue
            }
            if (d.paid.raw <= 0) continue
            val entries = when {
                label == "wages payable" -> listOf(
                    Entry(AccountRef(owner, Chart.WAGES_PAYABLE), d.paid), Entry(AccountRef(owner, Chart.CASH), -d.paid),
                    Entry(AccountRef(d.claimant, Chart.CASH), d.paid), Entry(AccountRef(d.claimant, Chart.REVENUE_OTHER), -d.paid),
                )
                label == "tax payable" -> listOf(
                    Entry(AccountRef(owner, Chart.TAX_PAYABLE), d.paid), Entry(AccountRef(owner, Chart.CASH), -d.paid),
                    Entry(AccountRef(d.claimant, Chart.CASH), d.paid), Entry(AccountRef(d.claimant, Chart.REVENUE_OTHER), -d.paid),
                )
                else -> listOf(
                    Entry(AccountRef(owner, Chart.PAYABLES), d.paid), Entry(AccountRef(owner, Chart.CASH), -d.paid),
                    Entry(AccountRef(d.claimant, Chart.CASH), d.paid), Entry(AccountRef(d.claimant, Chart.RECEIVABLES), -d.paid),
                )
            }
            ledger.post(TransactionDraft("liq:${company.id.value}:${n++}", tick, ReasonCode.LIQUIDATION, entries, "liquidation payment"))
        }

        // Whatever cash is left belongs to the shareholders.
        val residual = ledger.cash(owner)
        if (residual.raw > 0) payShareholders(company, residual, tick)

        finance.settlement.restoreArrears(finance.settlement.arrears().filter { it.payer != owner })
        finance.removeCompany(owner)
        companies.setStatus(company.id, CompanyStatus.DISSOLVED, tick)
        companies.retire(company.id)
        return LiquidationResult(dist, residual, written)
    }

    private fun payShareholders(company: Company, amount: Money, tick: Tick) {
        val owner = company.owner
        val table = finance.equity.table(owner)
        val holders = table?.holders().orEmpty()
        val weights = LongArray(holders.size) { i -> table!!.classes().sumOf { table.held(holders[i], it.id) } }
        if (holders.isEmpty() || weights.all { it == 0L }) return
        val parts = splitLargestRemainder(amount.raw, weights)
        val entries = ArrayList<Entry>()
        entries += Entry(AccountRef(owner, Chart.SHARE_CAPITAL), amount)
        entries += Entry(AccountRef(owner, Chart.CASH), -amount)
        val merged = java.util.TreeMap<AccountRef, Long>()
        for (i in holders.indices) {
            if (parts[i] == 0L) continue
            val payee = payeeFor(holders[i])
            val cash = AccountRef(payee, Chart.CASH); val gain = AccountRef(payee, Chart.REVENUE_OTHER)
            merged[cash] = (merged[cash] ?: 0L) + parts[i]
            merged[gain] = (merged[gain] ?: 0L) - parts[i]
        }
        for ((k, v) in merged) if (v != 0L) entries += Entry(k, Money(v))
        finance.ledger.post(TransactionDraft("liq:residual:${company.id.value}", tick, ReasonCode.LIQUIDATION, entries, "return to shareholders"))
    }

    private fun payeeFor(holder: Holder): LedgerOwner =
        if (holder.kind == "owner") LedgerOwner(app.many.economytycoon.finance.OwnerKind.COMPANY, holder.id) else finance.households

    internal fun restoreCase(c: InsolvencyCase) { cases[c.company] = c }
    internal fun clearAll() { cases.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.section("insolvency")
        h.putInt(cases.size)
        for (c in cases.values) c.hashInto(h)
    }
}

