package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.FinanceService
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.SettlementIntent

data class CompanyParams(
    val minCapital: Money = Money.units(10_000),
    val maxCapital: Money = Money.units(5_000_000),
    val maxPlayerCompanies: Int = 5,
    val minNameLength: Int = 3,
    val maxNameLength: Int = 40,
    val initialShares: Long = 1_000_000,
    val controlThreshold: Rate = Rate.percent(50),
)

/**
 * Registry of every legal entity plus the soft state attached to each one: departments, reputation,
 * standing rules and the ownership graph. Money stays in the finance engine.
 */
class CompanyService(
    private val ids: IdAllocators,
    val finance: FinanceService,
    val params: CompanyParams = CompanyParams(),
) {
    private val companies = java.util.TreeMap<CompanyId, Company>()
    private val budgets = java.util.TreeMap<CompanyId, DepartmentBudgets>()
    private val progress = java.util.TreeMap<CompanyId, DepartmentProgress>()
    val brands = BrandBook()
    val groups = CorporateGroups()
    val policies = PolicyBook()

    // ---- queries -------------------------------------------------------------------------------

    fun company(id: CompanyId): Company? = companies[id]
    fun require(id: CompanyId): Company = companies[id] ?: error("unknown company $id")
    fun all(): List<Company> = companies.values.toList()
    fun active(): List<Company> = companies.values.filter { it.isActive }
    fun ofKind(kind: CompanyKind): List<Company> = companies.values.filter { it.kind == kind && it.isActive }
    fun byOwner(owner: LedgerOwner): Company? = if (owner.kind == app.many.economytycoon.finance.OwnerKind.COMPANY) companies[CompanyId(owner.id)] else null
    fun byName(name: String): Company? = companies.values.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) && it.status != CompanyStatus.DISSOLVED }
    fun ownedBy(holder: Holder): List<Company> = companies.values.filter { it.founder == holder && it.isActive }

    fun budgets(id: CompanyId): DepartmentBudgets = budgets.getOrPut(id) { DepartmentBudgets() }
    fun progress(id: CompanyId): DepartmentProgress = progress.getOrPut(id) { DepartmentProgress() }

    // ---- founding and changes ------------------------------------------------------------------

    fun found(
        name: String, kind: CompanyKind, country: CountryId, hq: CityId?, founder: Holder,
        capital: Money, government: LedgerOwner, tick: Tick, strategy: String = "",
    ): Outcome<Company> {
        val clean = name.trim()
        if (clean.length < params.minNameLength || clean.length > params.maxNameLength) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "name must be ${params.minNameLength}-${params.maxNameLength} characters")
        }
        if (clean.any { it.isISOControl() }) return Outcome.reject(Rules.INVALID_ARGUMENT, "name contains control characters")
        if (byName(clean) != null) return Outcome.reject(Rules.NAME_TAKEN, "a company called \"$clean\" already exists")
        if (kind != CompanyKind.STATE && (capital < params.minCapital || capital > params.maxCapital)) {
            return Outcome.reject(Rules.INVALID_ARGUMENT, "founding capital must be between ${params.minCapital} and ${params.maxCapital}")
        }
        if (kind == CompanyKind.PLAYER && ownedBy(founder).count { it.kind == CompanyKind.PLAYER } >= params.maxPlayerCompanies) {
            return Outcome.reject(Rules.LIMIT_EXCEEDED, "a player may run at most ${params.maxPlayerCompanies} companies")
        }
        val id = ids.company()
        val company = Company(id, clean, kind, country, tick, founder)
        val reg = finance.registerCompany(company.owner, government, founder, capital, params.initialShares, tick)
        if (reg is Outcome.Rejected) return Outcome.Rejected(reg.rejection)
        company.restore(clean, hq, CompanyStatus.ACTIVE, strategy, null)
        companies[id] = company
        return Outcome.ok(company)
    }

    fun rename(id: CompanyId, newName: String): Outcome<Unit> {
        val c = companies[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        val clean = newName.trim()
        if (clean.length < params.minNameLength || clean.length > params.maxNameLength) return Outcome.reject(Rules.INVALID_ARGUMENT, "name length out of range")
        val other = byName(clean)
        if (other != null && other.id != id) return Outcome.reject(Rules.NAME_TAKEN, "name already in use")
        c.name = clean
        return Outcome.ok(Unit)
    }

    fun setHeadquarters(id: CompanyId, city: CityId): Outcome<Unit> {
        val c = companies[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        c.hq = city
        return Outcome.ok(Unit)
    }

    fun addIndustry(id: CompanyId, industry: IndustryId) { companies[id]?.industries?.add(industry) }

    internal fun setStatus(id: CompanyId, status: CompanyStatus, tick: Tick) {
        val c = companies[id] ?: return
        c.status = status
        if (status == CompanyStatus.DISSOLVED || status == CompanyStatus.ACQUIRED) c.endedAt = tick
    }

    /** Drops all soft state of a company that has ceased to exist. The registry entry stays for history. */
    internal fun retire(id: CompanyId) {
        brands.remove(id); policies.remove(id); groups.remove(id)
        budgets.remove(id); progress.remove(id)
    }

    // ---- per-tick ------------------------------------------------------------------------------

    /** Department spending as settlement intents, paid to [payee] (usually the household pool). */
    fun departmentIntents(tick: Tick, payee: LedgerOwner): List<SettlementIntent> {
        val out = ArrayList<SettlementIntent>()
        for (c in active()) {
            if (c.status != CompanyStatus.ACTIVE) continue
            val b = budgets[c.id] ?: continue
            out += b.intents(c.owner, payee, tick.value)
        }
        return out
    }

    /** Moves department effects forward. [sizeScale] gives a company's reference spend (payroll, typically). */
    fun advanceDepartments(sizeScale: (CompanyId) -> Money) {
        for (c in active()) {
            val b = budgets[c.id] ?: continue
            progress(c.id).advance(b, sizeScale(c.id))
        }
    }

    fun advanceBrands(marketing: (CompanyId) -> Long, quality: (CompanyId, IndustryId) -> Long, fairness: (CompanyId, IndustryId) -> Long) {
        for (c in active()) {
            val m = marketing(c.id)
            for (ind in c.industries) brands.brand(c.id, ind).advance(m, quality(c.id, ind), fairness(c.id, ind))
        }
    }

    fun refreshGroups() { groups.rebuild(finance.equity, all().filter { it.status != CompanyStatus.DISSOLVED }, params.controlThreshold) }

    // ---- persistence support -------------------------------------------------------------------

    internal fun restoreCompany(c: Company) { companies[c.id] = c }
    internal fun restoreBudgets(id: CompanyId, b: DepartmentBudgets) { budgets[id] = b }
    internal fun restoreProgress(id: CompanyId, p: DepartmentProgress) { progress[id] = p }
    internal fun budgetEntries() = budgets.entries.toList()
    internal fun progressEntries() = progress.entries.toList()
    internal fun clearAll() { companies.clear(); budgets.clear(); progress.clear(); brands.clear(); policies.clear(); groups.restore(emptyList()) }

    fun hashInto(h: CanonicalHasher) {
        h.section("companies")
        h.putInt(companies.size)
        for (c in companies.values) c.hashInto(h)
        h.putInt(budgets.size)
        for ((id, b) in budgets) { h.putLong(id.value); b.hashInto(h) }
        h.putInt(progress.size)
        for ((id, p) in progress) { h.putLong(id.value); p.hashInto(h) }
        brands.hashInto(h); groups.hashInto(h); policies.hashInto(h)
    }
}
