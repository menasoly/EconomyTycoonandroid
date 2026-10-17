package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What a licensor puts on the table. */
data class OfferRequest(
    val licensor: IpOwner,
    val licensee: CompanyId,
    val patents: List<PatentId>,
    val terms: RoyaltyTerms,
    val kind: LicenseKind = LicenseKind.NON_EXCLUSIVE,
    val scope: LicenseScope = LicenseScope.EVERYWHERE,
    val termDays: Int = 5 * Calendar.DAYS_PER_YEAR,
    val sublicensable: Boolean = false,
    val poolId: PoolId? = null,
)

data class AuditResult(val found: Boolean, val underpaid: Money, val penalty: Money, val costShifted: Boolean)

/** Hands what a pool licence earned on to the members. Kept out of the book so both can use it. */
internal object PoolPayout {
    /** Weight of one patent in sharing a pool's income: broader and more novel patents earn more. */
    fun weight(p: Patent): Long = 1_000L + (p.grantedBreadthPpm().coerceAtLeast(p.claimedBreadthPpm / 2) + p.noveltyPpm) / 2_000L

    fun distribute(state: IpState, run: IpRun, pool: PatentPool, amount: Money, ref: String) {
        if (amount.raw <= 0L) return
        pool.collected += amount
        val fee = Money(mulDiv(amount.raw, pool.adminFeePpm, 1_000_000L))
        pool.adminEarned += fee
        val share = amount - fee
        val members = pool.memberMap.values.toList()
        val weights = LongArray(members.size) { i ->
            members[i].patentSet.sumOf { id -> state.patentMap[id]?.let { weight(it) } ?: 0L }
        }
        if (members.isEmpty() || weights.all { it == 0L }) { pool.adminEarned += share; return }
        val parts = share.split(weights)
        for ((i, m) in members.withIndex()) {
            m.earned += parts[i]
            if (m.company != pool.administrator) {
                run.out.charge(run.tick, ChargeKind.POOL_DISTRIBUTION, IpLedger.of(pool.administrator), IpLedger.of(m.company), parts[i], "pool share: ${pool.name}", ref)
            }
        }
        val own = members.indexOfFirst { it.company == pool.administrator }
        if (own < 0) pool.adminEarned += Money.ZERO
    }
}

/**
 * Licences between owners and users of patents: offering, signing, charging royalties every period, audits and
 * endings. A licence "runs with the patent": when a patent is sold its licences go to the buyer.
 */
class LicenseBook(
    private val state: IpState,
    private val office: PatentOffice,
    val params: IpParams = IpParams(),
) {
    private val lp get() = params.licensing

    // ---- reading ----

    fun get(id: LicenseId): License? = state.licenseMap[id]

    fun isLicensed(company: CompanyId, patent: PatentId, country: CountryId, industry: IndustryFamily?, tick: Tick): Boolean =
        state.licenseMap.values.any {
            it.licensee == company && it.runningAt(tick) && patent in it.patents && it.scope.coversCountry(country) && it.scope.coversIndustry(industry)
        }

    fun licensedIn(company: CompanyId, tick: Tick): List<License> = state.licenseMap.values.filter { it.licensee == company && it.runningAt(tick) }

    /** The exclusive licence, if any, that shuts everyone else out of a patent in a country and industry. */
    fun exclusiveHolder(patent: PatentId, country: CountryId, industry: IndustryFamily?, tick: Tick): License? =
        state.licenseMap.values.firstOrNull {
            it.kind == LicenseKind.EXCLUSIVE && it.runningAt(tick) && patent in it.patents && it.scope.coversCountry(country) && it.scope.coversIndustry(industry)
        }

    /** Whether the owner itself may still use the patent in a place, which an exclusive licence takes away. */
    fun ownerMayPractise(owner: IpOwner, patent: PatentId, country: CountryId, industry: IndustryFamily?, tick: Tick): Boolean {
        val ex = exclusiveHolder(patent, country, industry, tick) ?: return true
        return ex.licensee.let { IpOwner.company(it) } == owner
    }

    /** Whether [company] holds or has asked for fair terms on a patent, which makes it a willing licensee. */
    fun isWillingLicensee(company: CompanyId, patent: PatentId): Boolean =
        state.licenseMap.values.any { it.licensee == company && patent in it.patents && it.frand && (it.status == LicenseStatus.OFFERED || it.isActive) }

    /** The rate a licence charges as a share of sales, from its last period or, failing that, its terms. */
    fun rateOf(l: License): Rate = when {
        l.lastSales.raw > 0L -> Rate(mulDiv(l.lastDue.raw, Rate.ONE_PPM, l.lastSales.raw))
        l.terms.basis == RoyaltyBasis.PERCENT || l.terms.basis == RoyaltyBasis.HYBRID -> l.terms.percent
        else -> Rate.ZERO
    }

    // ---- offers ----

    private fun checkTerms(t: RoyaltyTerms, kind: LicenseKind, termDays: Int): Rejection? {
        val shape = when (t.basis) {
            RoyaltyBasis.PER_UNIT -> t.perUnit.raw > 0L
            RoyaltyBasis.PERCENT -> t.percent.ppm > 0L
            RoyaltyBasis.LUMP_SUM -> t.lumpSum.raw > 0L
            RoyaltyBasis.HYBRID -> t.perUnit.raw > 0L && t.percent.ppm > 0L
        }
        if (!shape) return Rejection(Rules.INVALID_ARGUMENT, "the royalty terms do not match the basis")
        if (t.percent.ppm > lp.maxPercentPpm) return Rejection(Rules.REGULATION, "royalties above ${Formatting.pct(lp.maxPercentPpm)} of sales are not enforceable")
        if (termDays < lp.minTermDays || termDays > lp.maxTermYears * Calendar.DAYS_PER_YEAR) return Rejection(Rules.INVALID_ARGUMENT, "the term must be between ${lp.minTermDays} days and ${lp.maxTermYears} years")
        if (kind != LicenseKind.NON_EXCLUSIVE) {
            val paid = t.minimumPerPeriod.raw > 0L || t.lumpSum.raw > 0L || t.upfront.raw > 0L
            if (!paid) return Rejection(Rules.INVALID_ARGUMENT, "exclusivity has to be paid for with a minimum, a lump sum or an upfront fee")
        }
        return null
    }

    private object Formatting {
        fun pct(ppm: Long) = "${ppm / 10_000}%"
    }

    private fun conflict(patents: List<PatentId>, scope: LicenseScope, kind: LicenseKind, licensee: CompanyId, ignore: LicenseId?, tick: Tick): License? =
        state.licenseMap.values.firstOrNull { other ->
            if (other.id == ignore || !other.runningAt(tick) || other.patents.none { it in patents } || !other.scope.overlaps(scope)) return@firstOrNull false
            when {
                other.kind == LicenseKind.EXCLUSIVE || other.kind == LicenseKind.SOLE -> true
                kind != LicenseKind.NON_EXCLUSIVE -> true
                else -> other.licensee == licensee
            }
        }

    private fun essentialCounts(field: TechField): Pair<Map<IpOwner, Int>, Int> {
        val per = java.util.TreeMap<IpOwner, Int>()
        var total = 0
        for (p in state.patentMap.values) if (p.standardEssential && p.field == field && p.isLive) { per[p.owner] = (per[p.owner] ?: 0) + 1; total++ }
        return per to total
    }

    /** Checks an offer against the FRAND promises of the patents it covers. */
    fun frandViolations(run: IpRun, req: OfferRequest): List<FrandViolation> {
        val out = ArrayList<FrandViolation>()
        for (id in req.patents) {
            val p = state.patentMap[id] ?: continue
            if (!p.frandPledged) continue
            val (per, total) = essentialCounts(p.field)
            // A pool speaks for all its members, so its ceiling is the share of the field's essential patents it holds together.
            val holding = if (req.poolId != null) req.patents.count { state.patentMap[it]?.let { q -> q.frandPledged && q.field == p.field } == true } else per[p.owner] ?: 1
            val cap = Frand.fairCap(lp, holding, total.coerceAtLeast(1))
            val from = Tick((run.tick.value - Calendar.DAYS_PER_YEAR).coerceAtLeast(0L))
            val sample = run.ctx.usage(req.licensee, p.tech, req.scope.industries, req.scope.countries, from, run.tick)
            val offered = if (sample.sales.raw > 0L) Frand.offeredRate(req.terms, sample) else if (req.terms.basis == RoyaltyBasis.PERCENT) req.terms.percent else Rate.ZERO
            val comparables = state.licenseMap.values
                .filter { it.isActive && p.id in it.patents && it.licensee != req.licensee && it.kind == LicenseKind.NON_EXCLUSIVE }
                .map { rateOf(it) }.filter { it.ppm > 0L }
            out += Frand.check(lp, req.kind, offered, cap, comparables)
        }
        return out.distinctBy { it.code }
    }

    fun offer(run: IpRun, req: OfferRequest): Outcome<License> {
        val bad = validateOffer(run, req)
        if (bad != null) return Outcome.Rejected(bad)
        val frandPledged = req.patents.any { state.patentMap[it]?.frandPledged == true }
        val l = License(
            state.ids.license(), req.licensor, req.licensee, req.patents.sorted(), req.kind, req.scope, req.terms, req.termDays,
            req.sublicensable, frandPledged || req.poolId != null, run.tick, Tick(run.tick.value + lp.offerDays), req.poolId,
        )
        state.licenseMap[l.id] = l
        run.out.event(IpEvent.LicenseOffered(l.id, run.tick))
        return Outcome.ok(l)
    }

    private fun validateOffer(run: IpRun, req: OfferRequest): Rejection? {
        if (req.patents.isEmpty()) return Rejection(Rules.INVALID_ARGUMENT, "choose at least one patent")
        if (req.patents.toSet().size != req.patents.size) return Rejection(Rules.DUPLICATE, "a patent appears twice")
        if (req.licensor == IpOwner.company(req.licensee)) return Rejection(Rules.INVALID_ARGUMENT, "cannot license to yourself")
        if (!run.ctx.isActive(req.licensee)) return Rejection(Rules.WRONG_STATE, "the licensee is out of business")
        val badTerms = checkTerms(req.terms, req.kind, req.termDays)
        if (badTerms != null) return badTerms
        val pool = if (req.poolId == null) null else state.poolMap[req.poolId] ?: return Rejection(Rules.UNKNOWN_ENTITY, "unknown pool")
        if (pool != null) {
            if (!pool.open) return Rejection(Rules.WRONG_STATE, "the pool is closed")
            if (req.licensor != IpOwner.company(pool.administrator)) return Rejection(Rules.NOT_OWNER, "only the administrator offers pool licences")
            if (req.kind != LicenseKind.NON_EXCLUSIVE) return Rejection(Rules.REGULATION, "pool licences are non-exclusive")
        }
        for (id in req.patents) {
            val p = state.patentMap[id] ?: return Rejection(Rules.UNKNOWN_ENTITY, "unknown patent $id")
            if (!p.isLive) return Rejection(Rules.WRONG_STATE, "${p.title} is no longer alive")
            if (pool == null && p.owner != req.licensor) return Rejection(Rules.NOT_OWNER, "${p.title} belongs to someone else")
            if (pool != null && !pool.contains(id)) return Rejection(Rules.NOT_OWNER, "${p.title} is not in the pool")
            if (req.scope.countries.isNotEmpty() && req.scope.countries.none { p.filingMap[it]?.status?.live == true }) {
                return Rejection(Rules.WRONG_STATE, "${p.title} is not filed in the chosen countries")
            }
        }
        if (state.licenseMap.values.count { it.licensor == req.licensor && it.isOffer } >= lp.maxOffersPerLicensor) return Rejection(Rules.LIMIT_EXCEEDED, "too many open offers")
        if (state.licenseMap.values.count { it.licensor == req.licensor && it.isActive } >= lp.maxActivePerLicensor) return Rejection(Rules.LIMIT_EXCEEDED, "too many licences in force")
        if (state.licenseMap.values.any { it.licensee == req.licensee && it.licensor == req.licensor && it.isOffer && it.patents == req.patents.sorted() && it.scope == req.scope }) {
            return Rejection(Rules.DUPLICATE, "an identical offer is already open")
        }
        val clash = conflict(req.patents, req.scope, req.kind, req.licensee, null, run.tick)
        if (clash != null) return Rejection(Rules.WRONG_STATE, "licence ${clash.id.value} already covers this ground")
        val violations = frandViolations(run, req)
        if (violations.isNotEmpty()) return Rejection(Rules.REGULATION, violations.joinToString(" ") { it.message })
        return null
    }

    /** A holder who has pledged FRAND terms must answer a willing licensee's request; this is the answer, at the fair rate. */
    fun requestFrand(run: IpRun, licensee: CompanyId, patent: PatentId): Outcome<License> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (!p.frandPledged) return Outcome.reject(Rules.WRONG_STATE, "that patent carries no FRAND pledge")
        if (p.owner == IpOwner.company(licensee)) return Outcome.reject(Rules.INVALID_ARGUMENT, "it is already yours")
        val (per, total) = essentialCounts(p.field)
        val cap = Frand.fairCap(lp, per[p.owner] ?: 1, total.coerceAtLeast(1))
        val margin = run.ctx.profitMargin(licensee).ppm
        val reasonable = Royalty.reasonablePercent(lp, margin, p.grantedBreadthPpm().coerceAtLeast(p.claimedBreadthPpm / 2), p.noveltyPpm)
        val rate = if (cap.ppm > 0L) Rate(minOf(cap.ppm, reasonable.ppm)) else reasonable
        val terms = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = rate)
        return offer(run, OfferRequest(p.owner, licensee, listOf(patent), terms, LicenseKind.NON_EXCLUSIVE))
    }

    // ---- accepting and ending ----

    fun accept(run: IpRun, id: LicenseId, by: CompanyId): Outcome<License> {
        val l = state.licenseMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown licence")
        if (l.licensee != by) return Outcome.reject(Rules.NOT_OWNER, "this offer is not addressed to you")
        if (!l.isOffer) return Outcome.reject(Rules.WRONG_STATE, "the offer is no longer open")
        if (run.tick >= l.offerExpires) { l.status = LicenseStatus.WITHDRAWN; l.endedAt = run.tick; return Outcome.reject(Rules.WRONG_STATE, "the offer has lapsed") }
        val stillValid = stillOffered(run, l)
        if (stillValid != null) { endOffer(l, LicenseStatus.WITHDRAWN, run.tick); return Outcome.Rejected(stillValid) }
        val link = l.crossWith?.let { state.licenseMap[it] }?.takeIf { it.isOffer }
        if (link != null && l.id > link.id) return Outcome.reject(Rules.WRONG_STATE, "accept the other party's licence; this one follows it")
        val signing = Royalty.dueAtSigning(l.terms)
        val linkSigning = link?.let { Royalty.dueAtSigning(it.terms) } ?: Money.ZERO
        // Each side pays its own signing amount; both must be affordable or neither licence starts.
        val me = IpOwner.company(l.licensee)
        val other = link?.let { IpOwner.company(it.licensee) }
        if (!run.wallet.canPay(me, signing)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the signing amount")
        if (other != null && other != me && !run.wallet.canPay(other, linkSigning)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the other party cannot pay its signing amount")
        activate(run, l, signing)
        if (link != null) activateLinked(run, link)
        return Outcome.ok(l)
    }

    private fun activateLinked(run: IpRun, link: License): Boolean {
        val stillValid = stillOffered(run, link)
        if (stillValid != null) { endOffer(link, LicenseStatus.WITHDRAWN, run.tick); return false }
        val signing = Royalty.dueAtSigning(link.terms)
        if (!run.wallet.canPay(IpOwner.company(link.licensee), signing)) { endOffer(link, LicenseStatus.WITHDRAWN, run.tick); return false }
        activate(run, link, signing)
        return true
    }

    private fun stillOffered(run: IpRun, l: License): Rejection? {
        if (!run.ctx.isActive(l.licensee)) return Rejection(Rules.WRONG_STATE, "the licensee is out of business")
        for (id in l.patents) {
            val p = state.patentMap[id] ?: return Rejection(Rules.UNKNOWN_ENTITY, "unknown patent")
            if (!p.isLive) return Rejection(Rules.WRONG_STATE, "${p.title} is no longer alive")
            if (l.poolId == null && p.owner != l.licensor) return Rejection(Rules.NOT_OWNER, "${p.title} has changed hands")
        }
        if (conflict(l.patents, l.scope, l.kind, l.licensee, l.id, run.tick) != null) return Rejection(Rules.WRONG_STATE, "another licence now covers this ground")
        return null
    }

    private fun activate(run: IpRun, l: License, signing: Money) {
        l.status = LicenseStatus.ACTIVE
        l.startsAt = run.tick
        l.endsAt = Tick(run.tick.value + l.termDays)
        l.lastSettledAt = run.tick
        if (signing.raw > 0L) {
            run.wallet.reserve(IpOwner.company(l.licensee), signing)
            pay(run, l, signing, ChargeKind.LUMP_SUM, "licence fee")
            l.paidTotal += signing
        }
        run.out.event(IpEvent.LicenseSigned(l.id, run.tick))
    }

    /** Sends money from the licensee to whoever collects for the licensor, and feeds pools and scripted entities. */
    private fun pay(run: IpRun, l: License, amount: Money, kind: ChargeKind, memo: String) {
        val pool = l.poolId?.let { state.poolMap[it] }
        val receiver = if (pool != null) IpOwner.company(pool.administrator) else l.licensor
        run.out.charge(run.tick, kind, IpLedger.of(l.licensee), IpLedger.of(receiver), amount, memo, l.id.value.toString())
        if (pool != null) PoolPayout.distribute(state, run, pool, amount, l.id.value.toString())
        else receiver.npeOrNull()?.let { state.npeMap[it] }?.let { it.chest += amount; it.earned += amount }
    }

    private fun endOffer(l: License, status: LicenseStatus, tick: Tick) { l.status = status; l.endedAt = tick }

    fun decline(run: IpRun, id: LicenseId, by: CompanyId): Outcome<Unit> {
        val l = state.licenseMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown licence")
        if (l.licensee != by) return Outcome.reject(Rules.NOT_OWNER, "this offer is not addressed to you")
        if (!l.isOffer) return Outcome.reject(Rules.WRONG_STATE, "the offer is no longer open")
        endOffer(l, LicenseStatus.DECLINED, run.tick)
        l.crossWith?.let { state.licenseMap[it] }?.takeIf { it.isOffer }?.let { endOffer(it, LicenseStatus.WITHDRAWN, run.tick) }
        run.out.event(IpEvent.LicenseDeclined(id, run.tick))
        return Outcome.ok(Unit)
    }

    fun withdraw(run: IpRun, id: LicenseId, by: IpOwner): Outcome<Unit> {
        val l = state.licenseMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown licence")
        if (l.licensor != by) return Outcome.reject(Rules.NOT_OWNER, "not your offer")
        if (!l.isOffer) return Outcome.reject(Rules.WRONG_STATE, "the offer is no longer open")
        endOffer(l, LicenseStatus.WITHDRAWN, run.tick)
        l.crossWith?.let { state.licenseMap[it] }?.takeIf { it.isOffer }?.let { endOffer(it, LicenseStatus.WITHDRAWN, run.tick) }
        return Outcome.ok(Unit)
    }

    /**
     * The licensee may walk away at any time for a share of the minimums still to come. The licensor may end a licence
     * only when the licensee is in breach.
     */
    fun terminate(run: IpRun, id: LicenseId, by: IpOwner): Outcome<Money> {
        val l = state.licenseMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown licence")
        if (!l.isActive) return Outcome.reject(Rules.WRONG_STATE, "the licence is not in force")
        val licenseeSide = by == IpOwner.company(l.licensee)
        if (!licenseeSide && by != l.licensor) return Outcome.reject(Rules.NOT_OWNER, "not a party to this licence")
        var fee = Money.ZERO
        if (licenseeSide) {
            if (!l.compulsory) {
                val remainingPeriods = remainingPeriods(l, run.tick)
                fee = Money(mulDiv(l.terms.minimumPerPeriod.raw * remainingPeriods, lp.earlyTerminationPpm, 1_000_000L))
                if (fee.raw > 0L) {
                    if (!run.wallet.tryReserve(by, fee)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the early termination fee")
                    pay(run, l, fee, ChargeKind.ROYALTY, "early termination")
                    l.paidTotal += fee
                }
            }
        } else {
            if (l.compulsory) return Outcome.reject(Rules.REGULATION, "a compulsory licence cannot be ended by the licensor")
            if (l.strikes == 0) return Outcome.reject(Rules.WRONG_STATE, "the licensee is not in breach")
        }
        end(run, l, LicenseStatus.TERMINATED)
        l.crossWith?.let { state.licenseMap[it] }?.takeIf { it.isActive }?.let { end(run, it, LicenseStatus.TERMINATED) }
        return Outcome.ok(fee)
    }

    private fun remainingPeriods(l: License, tick: Tick): Long {
        val end = l.endsAt ?: return 0L
        return ((end.value - tick.value).coerceAtLeast(0L) + lp.periodDays - 1) / lp.periodDays
    }

    private fun end(run: IpRun, l: License, status: LicenseStatus) {
        l.status = status
        l.endedAt = run.tick
        run.out.event(IpEvent.LicenseEnded(l.id, status, run.tick))
    }

    // ---- periodic work ----

    fun step(run: IpRun) {
        for (l in state.licenseMap.values.toList()) {
            when (l.status) {
                LicenseStatus.OFFERED -> if (run.tick >= l.offerExpires) endOffer(l, LicenseStatus.WITHDRAWN, run.tick)
                LicenseStatus.ACTIVE -> stepActive(run, l)
                else -> {}
            }
        }
    }

    private fun stepActive(run: IpRun, l: License) {
        val ends = l.endsAt ?: return
        val last = l.lastSettledAt ?: l.startsAt ?: run.tick
        if (!run.ctx.isActive(l.licensee)) { end(run, l, LicenseStatus.TERMINATED); return }
        if (l.patents.none { state.patentMap[it]?.isLive == true }) {
            if (run.tick > last) settle(run, l)
            end(run, l, LicenseStatus.EXPIRED)
            return
        }
        if (run.tick >= ends) {
            settle(run, l)
            if (l.isActive) end(run, l, LicenseStatus.EXPIRED)
            return
        }
        if (run.tick.value - last.value >= lp.periodDays) settle(run, l)
    }

    /** Counts what the licensee sold under the licence since the last settlement and charges the royalty. */
    internal fun settle(run: IpRun, l: License) {
        val from = l.lastSettledAt ?: l.startsAt ?: run.tick
        val patents = l.patents.mapNotNull { state.patentMap[it] }
        val countries = if (l.scope.allCountries) patents.flatMap { it.countriesInForce(run.tick) }.toSortedSet().toList() else l.scope.countries
        var truth = UsageReport.ZERO
        for (tech in patents.map { it.tech }.distinct()) {
            truth = truth.max(run.ctx.usage(l.licensee, tech, l.scope.industries, countries, from, run.tick))
        }
        val reported = Royalty.scaleUsage(truth, run.ctx.reportingHonesty(l.licensee))
        val dueTrue = Royalty.due(l.terms, truth)
        val dueReported = Royalty.due(l.terms, reported)
        l.lastSettledAt = run.tick
        l.dueTrue += dueTrue
        l.dueReported += dueReported
        l.unitsReported = Quantity(l.unitsReported.raw + reported.units.raw)
        l.salesReported += reported.sales
        l.lastDue = dueReported
        l.lastUnits = reported.units
        l.lastSales = reported.sales
        if (!l.terms.isRecurring) return
        val owed = dueReported + l.arrears
        if (owed.raw <= 0L) { run.out.event(IpEvent.RoyaltySettled(l.id, Money.ZERO, Money.ZERO, run.tick)); return }
        if (run.wallet.tryReserve(IpOwner.company(l.licensee), owed)) {
            pay(run, l, owed, ChargeKind.ROYALTY, "royalty")
            l.paidTotal += owed
            l.arrears = Money.ZERO
            l.strikes = 0
            run.out.event(IpEvent.RoyaltySettled(l.id, dueReported, Money.ZERO, run.tick))
        } else {
            l.arrears += dueReported
            l.strikes++
            run.out.event(IpEvent.RoyaltySettled(l.id, dueReported, owed, run.tick))
            if (l.strikes >= lp.breachStrikes) end(run, l, LicenseStatus.BREACHED)
        }
    }

    /**
     * Starts a licence at once without an offer: for settlements, court orders and compulsory grants. The
     * [signing] amount is charged to the licensee as [kind] and counted as paid.
     */
    internal fun grantDirect(
        run: IpRun, req: OfferRequest, signing: Money, kind: ChargeKind, compulsory: Boolean, frand: Boolean, memo: String,
    ): License {
        val l = License(
            state.ids.license(), req.licensor, req.licensee, req.patents.sorted(), req.kind, req.scope, req.terms, req.termDays,
            req.sublicensable, frand, run.tick, run.tick, req.poolId, compulsory,
        )
        l.status = LicenseStatus.ACTIVE
        l.startsAt = run.tick
        l.endsAt = Tick(run.tick.value + req.termDays)
        l.lastSettledAt = run.tick
        state.licenseMap[l.id] = l
        if (signing.raw > 0L) {
            pay(run, l, signing, kind, memo)
            l.paidTotal += signing
        }
        run.out.event(IpEvent.LicenseSigned(l.id, run.tick))
        return l
    }

    // ---- audits ----

    fun audit(run: IpRun, id: LicenseId, by: IpOwner): Outcome<AuditResult> {
        val l = state.licenseMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown licence")
        val pool = l.poolId?.let { state.poolMap[it] }
        val auditor = if (pool != null) IpOwner.company(pool.administrator) else l.licensor
        if (by != auditor) return Outcome.reject(Rules.NOT_OWNER, "only the licensor audits")
        if (!l.isActive || !l.terms.isRecurring) return Outcome.reject(Rules.WRONG_STATE, "nothing to audit")
        val lastAt = l.lastAuditAt
        if (lastAt == null || run.tick.value - lastAt.value >= Calendar.DAYS_PER_YEAR) l.auditsThisYear = 0
        if (l.auditsThisYear >= lp.auditsPerYear) return Outcome.reject(Rules.RATE_LIMITED, "the licence has been audited too recently")
        val cost = Money.units(lp.auditCostUnits)
        if (by.isCompany && !run.wallet.tryReserve(by, cost)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay for the audit")
        run.out.charge(run.tick, ChargeKind.AUDIT_COST, IpLedger.of(by), IpLedger.professionals, cost, "royalty audit", id.value.toString())
        l.auditsThisYear++
        l.lastAuditAt = run.tick
        val found = state.dice.chance("audit", Rate(lp.auditDetectionPpm), id.value, run.tick.value)
        val trueSince = l.dueTrue - l.auditedTrue
        val reportedSince = l.dueReported - l.auditedReported
        val underpaid = (trueSince - reportedSince).coerceAtLeast(Money.ZERO)
        if (!found) return Outcome.ok(AuditResult(false, Money.ZERO, Money.ZERO, false))
        l.auditedTrue = l.dueTrue
        l.auditedReported = l.dueReported
        var penalty = Money.ZERO
        var shifted = false
        if (underpaid.raw > 0L) {
            val licenseeOwner = IpOwner.company(l.licensee)
            val over = underpaid.raw * Rate.ONE_PPM > reportedSince.raw * lp.auditThresholdPpm
            penalty = if (over) Money(mulDiv(underpaid.raw, lp.auditPenaltyPpm, 1_000_000L)) else Money.ZERO
            val total = underpaid + penalty + if (over) cost else Money.ZERO
            // The shortfall is owed in full; it goes out as deferrable charges so an unpaid sum becomes arrears.
            run.wallet.reserve(licenseeOwner, Money.min(total, run.wallet.available(licenseeOwner)))
            pay(run, l, underpaid, ChargeKind.ROYALTY, "audit shortfall")
            if (penalty.raw > 0L) pay(run, l, penalty, ChargeKind.SETTLEMENT, "audit penalty")
            if (over) {
                run.out.charge(run.tick, ChargeKind.AUDIT_COST, IpLedger.of(l.licensee), IpLedger.of(by), cost, "audit costs refunded", id.value.toString())
                shifted = true
            }
            l.paidTotal += underpaid + penalty
            l.recovered += underpaid + penalty
        }
        run.out.event(IpEvent.AuditCompleted(id, underpaid, penalty, run.tick))
        return Outcome.ok(AuditResult(true, underpaid, penalty, shifted))
    }

    // ---- ownership changes ----

    /** True when selling [patents] would split a licence bundle in two. */
    internal fun splitsABundle(seller: IpOwner, patents: Set<PatentId>): License? =
        state.licenseMap.values.firstOrNull { it.licensor == seller && (it.isActive || it.isOffer) && it.patents.any { p -> p in patents } && !patents.containsAll(it.patents) }

    /** Moves the licensor role of licences wholly inside [patents] from [from] to [to]; open offers are withdrawn. */
    internal fun transferLicensor(run: IpRun, from: IpOwner, to: IpOwner, patents: Set<PatentId>) {
        for (l in state.licenseMap.values) {
            if (l.licensor != from || l.poolId != null || !patents.containsAll(l.patents)) continue
            when (l.status) {
                LicenseStatus.ACTIVE -> l.licensor = to
                LicenseStatus.OFFERED -> endOffer(l, LicenseStatus.WITHDRAWN, run.tick)
                else -> {}
            }
        }
    }

    /** Ends everything a closed company was party to as licensee; as licensor its patents move through the market. */
    internal fun companyClosed(run: IpRun, company: CompanyId) {
        for (l in state.licenseMap.values.toList()) {
            if (l.licensee == company) {
                if (l.isOffer) endOffer(l, LicenseStatus.WITHDRAWN, run.tick) else if (l.isActive) end(run, l, LicenseStatus.TERMINATED)
            } else if (l.licensor == IpOwner.company(company) && l.isOffer) {
                endOffer(l, LicenseStatus.WITHDRAWN, run.tick)
            }
        }
    }

    internal fun link(a: License, b: License) { a.crossWith = b.id; b.crossWith = a.id }
}
