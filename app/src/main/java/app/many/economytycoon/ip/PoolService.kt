package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/** Patent pools: companies put essential patents in one shop and license them together on FRAND terms. */
class PoolService(
    private val state: IpState,
    private val book: LicenseBook,
    val params: IpParams = IpParams(),
) {
    /** The administrator keeps at most this share of what the pool collects, ppm. */
    val maxAdminFeePpm: Long = 200_000L
    val minEssentialAffinityPpm: Long = 600_000L
    val minEssentialBreadthPpm: Long = MIN_ESSENTIAL_BREADTH_PPM

    companion object {
        const val MIN_ESSENTIAL_BREADTH_PPM = 500_000L
    }

    /** Whether a patent is needed to make the pool's product: close to its field and broad enough to matter. */
    fun isEssential(p: Patent, pool: PatentPool): Boolean =
        TechFields.affinityPpm(p.field, pool.field) >= minEssentialAffinityPpm && p.grantedBreadthPpm() >= minEssentialBreadthPpm

    fun poolOf(patent: PatentId): PatentPool? = state.poolMap.values.firstOrNull { it.open && it.contains(patent) }

    fun poolsOf(company: CompanyId): List<PatentPool> = state.poolMap.values.filter { it.open && it.memberMap.containsKey(company) }

    /** Members of a pool may use each other's pool patents without a licence. */
    fun coveredByPool(company: CompanyId, patent: PatentId): Boolean {
        val pool = poolOf(patent) ?: return false
        return pool.memberMap.containsKey(company)
    }

    fun form(run: IpRun, administrator: CompanyId, name: String, field: TechField, terms: RoyaltyTerms, adminFeePpm: Long): Outcome<PatentPool> {
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a pool needs a name")
        if (state.poolMap.values.any { it.name.equals(name, ignoreCase = true) }) return Outcome.reject(Rules.NAME_TAKEN, "that pool name is taken")
        if (!run.ctx.isActive(administrator)) return Outcome.reject(Rules.WRONG_STATE, "the administrator is out of business")
        if (adminFeePpm !in 0L..maxAdminFeePpm) return Outcome.reject(Rules.INVALID_ARGUMENT, "the administrator's cut is at most ${maxAdminFeePpm / 10_000}%")
        if (!terms.isRecurring) return Outcome.reject(Rules.INVALID_ARGUMENT, "a pool charges running royalties")
        if (terms.percent.ppm > params.licensing.frandCapPpm) return Outcome.reject(Rules.REGULATION, "the pool's rate is above the standard's ceiling")
        val pool = PatentPool(state.ids.pool(), name.trim(), administrator, field, run.tick, terms, adminFeePpm)
        pool.memberMap[administrator] = PoolMember(administrator, run.tick)
        state.poolMap[pool.id] = pool
        run.out.event(IpEvent.PoolFormed(pool.id, run.tick))
        return Outcome.ok(pool)
    }

    fun join(run: IpRun, id: PoolId, company: CompanyId, patents: List<PatentId>): Outcome<PoolMember> {
        val pool = state.poolMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown pool")
        if (!pool.open) return Outcome.reject(Rules.WRONG_STATE, "the pool is closed")
        if (!run.ctx.isActive(company)) return Outcome.reject(Rules.WRONG_STATE, "the company is out of business")
        if (patents.isEmpty() && company != pool.administrator) return Outcome.reject(Rules.INVALID_ARGUMENT, "bring at least one patent")
        for (pid in patents) {
            val p = state.patentMap[pid] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
            if (p.owner != IpOwner.company(company)) return Outcome.reject(Rules.NOT_OWNER, "${p.title} is not yours")
            if (!isEssential(p, pool)) return Outcome.reject(Rules.REGULATION, "${p.title} is not essential to this pool")
            if (poolOf(pid) != null) return Outcome.reject(Rules.DUPLICATE, "${p.title} is already in a pool")
            val ex = state.licenseMap.values.firstOrNull { it.kind == LicenseKind.EXCLUSIVE && it.isActive && pid in it.patents }
            if (ex != null) return Outcome.reject(Rules.WRONG_STATE, "${p.title} is under an exclusive licence")
        }
        val member = pool.memberMap.getOrPut(company) { PoolMember(company, run.tick) }
        for (pid in patents) {
            member.patentSet.add(pid)
            state.patentMap.getValue(pid).also { it.standardEssential = true; it.frandPledged = true }
        }
        run.out.event(IpEvent.PoolChanged(id, company, true, run.tick))
        return Outcome.ok(member)
    }

    fun leave(run: IpRun, id: PoolId, company: CompanyId): Outcome<Unit> {
        val pool = state.poolMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown pool")
        if (!pool.memberMap.containsKey(company)) return Outcome.reject(Rules.UNKNOWN_ENTITY, "not a member")
        if (company == pool.administrator) return Outcome.reject(Rules.WRONG_STATE, "the administrator must dissolve the pool instead")
        pool.memberMap.remove(company)
        run.out.event(IpEvent.PoolChanged(id, company, false, run.tick))
        return Outcome.ok(Unit)
    }

    fun dissolve(run: IpRun, id: PoolId, by: CompanyId): Outcome<Unit> {
        val pool = state.poolMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown pool")
        if (pool.administrator != by) return Outcome.reject(Rules.NOT_OWNER, "only the administrator dissolves a pool")
        if (!pool.open) return Outcome.reject(Rules.WRONG_STATE, "already dissolved")
        pool.open = false
        pool.dissolvedAt = run.tick
        for (l in state.licenseMap.values) {
            if (l.poolId != id) continue
            if (l.isOffer) { l.status = LicenseStatus.WITHDRAWN; l.endedAt = run.tick }
            else if (l.isActive) { l.status = LicenseStatus.TERMINATED; l.endedAt = run.tick; run.out.event(IpEvent.LicenseEnded(l.id, LicenseStatus.TERMINATED, run.tick)) }
        }
        return Outcome.ok(Unit)
    }

    /** Offers the whole pool, as it stands now, to a licensee on the pool's published terms. */
    fun offerBundle(
        run: IpRun, id: PoolId, licensee: CompanyId, industries: List<IndustryFamily> = emptyList(), countries: List<CountryId> = emptyList(), termDays: Int = 5 * 360,
    ): Outcome<License> {
        val pool = state.poolMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown pool")
        val patents = pool.allPatents().filter { state.patentMap[it]?.isLive == true }
        if (patents.isEmpty()) return Outcome.reject(Rules.WRONG_STATE, "the pool has nothing to license yet")
        if (pool.memberMap.containsKey(licensee)) return Outcome.reject(Rules.DUPLICATE, "members already have the pool's patents")
        return book.offer(
            run,
            OfferRequest(IpOwner.company(pool.administrator), licensee, patents, pool.terms, LicenseKind.NON_EXCLUSIVE, LicenseScope.of(industries, countries), termDays, false, id),
        )
    }

    /** Takes a company's seat out of every pool when it closes, passing nothing on. */
    internal fun companyClosed(run: IpRun, company: CompanyId) {
        for (pool in state.poolMap.values.toList()) {
            if (!pool.open || !pool.memberMap.containsKey(company)) continue
            if (pool.administrator == company) dissolve(run, pool.id, company) else leave(run, pool.id, company)
        }
    }

    fun shareOf(pool: PatentPool, company: CompanyId, tick: Tick): Long {
        val m = pool.memberMap[company] ?: return 0L
        val mine = m.patentSet.sumOf { id -> state.patentMap[id]?.let { PoolPayout.weight(it) } ?: 0L }
        val all = pool.memberMap.values.sumOf { mm -> mm.patentSet.sumOf { id -> state.patentMap[id]?.let { PoolPayout.weight(it) } ?: 0L } }
        return if (all == 0L) 0L else mine * 1_000_000L / all
    }
}
