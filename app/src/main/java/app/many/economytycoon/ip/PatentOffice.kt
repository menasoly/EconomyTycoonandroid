package app.many.economytycoon.ip

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

/** Whether a company has finished researching a technology. */
fun interface TechHolding {
    fun holds(company: CompanyId, tech: TechId): Boolean
}

/** What a company asks for when it files. */
data class FilingRequest(
    val company: CompanyId,
    val tech: TechId,
    val countries: List<CountryId>,
    val breadth: BreadthChoice = BreadthChoice.STANDARD,
    val claims: Int = 10,
    val industry: IndustryFamily? = null,
)

/** The terms a filing would get, shown before the company commits. */
data class FilingQuote(
    val noveltyPpm: Long,
    val fees: Money,
    val perCountry: List<CountryQuote>,
    val cites: List<PatentId>,
)

data class CountryQuote(val country: CountryId, val fee: Money, val grantChancePpm: Long, val examinationDays: Int)

/**
 * The register of filings. It prices and accepts applications, examines them when their time comes, charges the
 * annual fees, lets patents lapse or expire, and records who owns what. Courts and the market reach in through the
 * narrow `internal` methods at the bottom.
 */
class PatentOffice(
    private val state: IpState,
    private val content: ContentRepository,
    private val holding: TechHolding,
    val params: IpParams = IpParams(),
) {
    private val office get() = params.office

    fun regimeOf(country: CountryId): LegalRegime = state.regimes.regimeOf(country)

    private fun publishedAt(p: Patent): Tick = Tick(p.filedAt.value + office.publicationDays)

    /** Patents that count as prior art at [tick] in or near [field], with their closeness summed in ppm. */
    fun priorArtAffinity(field: TechField, tick: Tick, exclude: TechId?): Long {
        var sum = 0L
        for (p in state.patentMap.values) {
            if (publishedAt(p) > tick || p.tech == exclude) continue
            sum += TechFields.affinityPpm(field, p.field)
        }
        return sum
    }

    private fun samePublished(tech: TechId, inventor: CompanyId, tick: Tick): Boolean =
        state.patentMap.values.any { it.tech == tech && it.inventor != inventor && publishedAt(it) <= tick }

    fun pendingIn(country: CountryId): Int = state.patentMap.values.count { it.filingMap[country]?.status == FilingStatus.PENDING }

    /** Earlier patents a new one in [field] builds on: the closest and then the most recent, up to the citation limit. */
    fun citationsFor(field: TechField, tick: Tick, exclude: TechId): List<PatentId> =
        state.patentMap.values
            .filter { publishedAt(it) <= tick && it.tech != exclude && TechFields.affinityPpm(field, it.field) >= office.minCitationAffinityPpm }
            .sortedWith(compareByDescending<Patent> { TechFields.affinityPpm(field, it.field) }.thenByDescending { it.filedAt }.thenBy { it.id })
            .take(office.maxCitations).map { it.id }

    private fun check(req: FilingRequest, tick: Tick): Outcome<Unit> {
        val def = content.technologyOrNull(req.tech) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology ${req.tech.key}")
        if (!holding.holds(req.company, req.tech)) return Outcome.reject(Rules.NOT_UNLOCKED, "${def.name} has not been researched")
        if (req.countries.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "choose at least one country to file in")
        if (req.countries.toSet().size != req.countries.size) return Outcome.reject(Rules.DUPLICATE, "a country appears twice")
        if (req.countries.size > office.maxJurisdictions) return Outcome.reject(Rules.LIMIT_EXCEEDED, "no more than ${office.maxJurisdictions} countries per patent")
        if (req.claims !in office.minClaims..office.maxClaims) return Outcome.reject(Rules.INVALID_ARGUMENT, "claims must be between ${office.minClaims} and ${office.maxClaims}")
        if (state.patentOfTech(req.company, req.tech) != null) return Outcome.reject(Rules.DUPLICATE, "${def.name} is already patented by this company")
        val pending = state.patentsOf(req.company).count { it.isPending }
        if (pending >= office.maxPendingPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many applications waiting")
        if (state.patentsOf(IpOwner.company(req.company)).size >= office.maxPatentsPerOwner) return Outcome.reject(Rules.LIMIT_EXCEEDED, "portfolio limit reached")
        return Outcome.ok(Unit)
    }

    /** What filing would cost and how likely it is to succeed, without filing. */
    fun quote(req: FilingRequest, tick: Tick, othersKnowing: Int): Outcome<FilingQuote> {
        val checked = check(req, tick)
        if (checked is Outcome.Rejected) return checked
        val def = content.technologyOrNull(req.tech) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology")
        val field = TechFields.of(def)
        val novelty = noveltyFor(req, def, field, tick, othersKnowing)
        val perCountry = req.countries.map { c ->
            val reg = regimeOf(c)
            CountryQuote(
                c, PatentRules.applicationFee(office, reg, req.claims),
                PatentRules.grantProbability(office, reg, novelty, req.breadth.breadthPpm),
                PatentRules.examinationDays(office, reg, pendingIn(c), 0L),
            )
        }
        return Outcome.ok(FilingQuote(novelty, Money.sum(perCountry.map { it.fee }), perCountry, citationsFor(field, tick, req.tech)))
    }

    private fun noveltyFor(req: FilingRequest, def: app.many.economytycoon.content.TechDef, field: TechField, tick: Tick, othersKnowing: Int): Long {
        val jitter = state.dice.spread("novelty", office.noveltyJitterPpm, req.company.value, IpDice.keyOf(req.tech.key))
        return PatentRules.novelty(office, def, priorArtAffinity(field, tick, req.tech), samePublished(req.tech, req.company, tick), othersKnowing, jitter)
    }

    /** Files an application. The fees are reserved from the company's cash and returned as charges. */
    fun file(run: IpRun, req: FilingRequest, othersKnowing: Int): Outcome<Patent> {
        val checked = check(req, run.tick)
        if (checked is Outcome.Rejected) return checked
        val def = content.technologyOrNull(req.tech) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology")
        val field = TechFields.of(def)
        val novelty = noveltyFor(req, def, field, run.tick, othersKnowing)
        if (novelty < office.minNoveltyToFilePpm) return Outcome.reject(Rules.INVALID_ARGUMENT, "${def.name} is too close to what is already known to patent")
        val owner = IpOwner.company(req.company)
        val fees = req.countries.map { c -> c to PatentRules.applicationFee(office, regimeOf(c), req.claims) }
        val total = Money.sum(fees.map { it.second })
        if (!run.wallet.tryReserve(owner, total)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "filing costs ${total.format()}")
        val industry = req.industry
        val pharma = industry == IndustryFamily.PHARMACEUTICALS || industry == IndustryFamily.HEALTHCARE
        val id = state.ids.patent()
        val patent = Patent(
            id, req.company, req.tech, def.name, field, industry, run.tick, novelty, req.claims, req.breadth.breadthPpm,
            citationsFor(field, run.tick, req.tech), def.researchCost, pharma, owner,
        )
        patent.autoMaintain = state.policy(req.company).autoMaintain
        for ((c, fee) in fees) {
            val days = PatentRules.examinationDays(
                office, regimeOf(c), pendingIn(c), state.dice.spread("examdelay", office.examDelayJitterPpm, id.value, c.value),
            )
            val f = Filing(c, run.tick, Tick(run.tick.value + days), req.breadth.breadthPpm)
            f.feesPaid = fee
            patent.filingMap[c] = f
            run.out.charge(run.tick, ChargeKind.FILING_FEE, IpLedger.of(req.company), IpLedger.government(c), fee, "patent application: ${def.name}", id.value.toString())
        }
        patent.totalFees = total
        state.patentMap[id] = patent
        for (c in patent.cites) state.forwardCites[c] = state.forwardCitations(c) + 1
        run.out.event(IpEvent.PatentFiled(id, req.company, req.countries, total, run.tick))
        return Outcome.ok(patent)
    }

    /** Daily work: examinations that are due, annual fees, grace periods and expiry. */
    fun step(run: IpRun) {
        for (p in state.patentMap.values.toList()) {
            for (f in p.filingMap.values.toList()) {
                when (f.status) {
                    FilingStatus.PENDING -> if (run.tick >= f.examinationDue) examine(run, p, f)
                    FilingStatus.GRANTED -> maintain(run, p, f)
                    else -> {}
                }
            }
        }
    }

    private fun examine(run: IpRun, p: Patent, f: Filing) {
        val reg = regimeOf(f.country)
        val roll = state.dice.rate("exam", p.id.value, f.country.value).ppm
        when (val r = PatentRules.examine(office, reg, p.noveltyPpm, f.breadthPpm, roll)) {
            is ExamResult.Refused -> {
                f.status = FilingStatus.REJECTED
                f.endedAt = run.tick
                run.out.event(IpEvent.PatentRefused(p.id, f.country, run.tick))
            }
            is ExamResult.Granted -> grant(run, p, f, r.breadthPpm, false)
            is ExamResult.Narrowed -> grant(run, p, f, r.breadthPpm, true)
        }
    }

    private fun grant(run: IpRun, p: Patent, f: Filing, breadthPpm: Long, narrowed: Boolean) {
        val reg = regimeOf(f.country)
        val fee = PatentRules.grantFee(office, reg)
        val owner = p.owner
        // The grant fee is due on allowance. An owner that cannot pay lets the application go.
        if (!run.wallet.tryReserve(owner, fee)) {
            f.status = FilingStatus.ABANDONED
            f.endedAt = run.tick
            run.out.event(IpEvent.PatentLapsed(p.id, f.country, run.tick))
            return
        }
        run.out.charge(run.tick, ChargeKind.FILING_FEE, IpLedger.of(owner), IpLedger.government(f.country), fee, "grant fee: ${p.title}", p.id.value.toString())
        f.feesPaid += fee
        p.totalFees += fee
        f.status = FilingStatus.GRANTED
        f.breadthPpm = breadthPpm
        f.grantedAt = run.tick
        f.expiresAt = Tick(f.filedAt.value + PatentRules.termTicks(reg, p.pharma))
        f.nextFeeDue = Tick(run.tick.value + Calendar.DAYS_PER_YEAR)
        run.out.event(IpEvent.PatentGranted(p.id, f.country, narrowed, run.tick))
    }

    private fun maintain(run: IpRun, p: Patent, f: Filing) {
        val exp = f.expiresAt
        if (exp != null && run.tick >= exp) {
            f.status = FilingStatus.EXPIRED
            f.endedAt = run.tick
            f.nextFeeDue = null
            f.graceUntil = null
            run.out.event(IpEvent.PatentExpired(p.id, f.country, run.tick))
            return
        }
        val due = f.nextFeeDue ?: return
        if (run.tick < due) return
        val grace = f.graceUntil
        if (grace == null) f.graceUntil = Tick(due.value + office.graceDays)
        if (p.autoMaintain && payFee(run, p, f) != null) return
        val until = f.graceUntil ?: return
        if (run.tick >= until) {
            f.status = FilingStatus.LAPSED
            f.endedAt = run.tick
            f.nextFeeDue = null
            f.graceUntil = null
            run.out.event(IpEvent.PatentLapsed(p.id, f.country, run.tick))
        }
    }

    private fun payFee(run: IpRun, p: Patent, f: Filing): Money? {
        val reg = regimeOf(f.country)
        val due = f.nextFeeDue ?: return null
        val year = f.feeYearsPaid + 1
        val base = PatentRules.maintenanceFee(office, reg, year)
        val late = run.tick > due
        val fee = if (late) base + base.times(app.many.economytycoon.core.Rate(office.lateFeeSurchargePpm)) else base
        if (!run.wallet.tryReserve(p.owner, fee)) return null
        run.out.charge(run.tick, ChargeKind.MAINTENANCE_FEE, IpLedger.of(p.owner), IpLedger.government(f.country), fee, "renewal year $year: ${p.title}", p.id.value.toString())
        f.feesPaid += fee
        p.totalFees += fee
        f.feeYearsPaid = year
        f.nextFeeDue = Tick(due.value + Calendar.DAYS_PER_YEAR)
        f.graceUntil = null
        run.out.event(IpEvent.MaintenancePaid(p.id, f.country, fee, run.tick))
        return fee
    }

    /** Pays the fee that is due in a country by hand, for patents not set to renew themselves. */
    fun payMaintenance(run: IpRun, patent: PatentId, country: CountryId, by: CompanyId): Outcome<Money> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != IpOwner.company(by)) return Outcome.reject(Rules.NOT_OWNER, "not your patent")
        val f = p.filingMap[country] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "not filed there")
        if (f.status != FilingStatus.GRANTED) return Outcome.reject(Rules.WRONG_STATE, "nothing to renew")
        val due = f.nextFeeDue ?: return Outcome.reject(Rules.WRONG_STATE, "no fee is due")
        if (run.tick < Tick(due.value - OPEN_BEFORE_DUE_DAYS)) return Outcome.reject(Rules.WRONG_STATE, "the next fee is not open yet")
        val paid = payFee(run, p, f) ?: return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the renewal fee")
        return Outcome.ok(paid)
    }

    /** Drops one country, or the whole family when [country] is null. Fees paid are not returned. */
    fun abandon(run: IpRun, patent: PatentId, country: CountryId?, by: CompanyId): Outcome<Unit> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != IpOwner.company(by)) return Outcome.reject(Rules.NOT_OWNER, "not your patent")
        val targets = if (country == null) p.filingMap.values.toList() else listOfNotNull(p.filingMap[country])
        if (targets.isEmpty()) return Outcome.reject(Rules.UNKNOWN_ENTITY, "not filed there")
        var any = false
        for (f in targets) if (f.status.live) { end(f, FilingStatus.ABANDONED, run.tick); any = true }
        if (!any) return Outcome.reject(Rules.WRONG_STATE, "already ended")
        if (!p.isLive) { p.askingPrice = null; p.listedAt = null }
        run.out.event(IpEvent.PatentAbandoned(p.id, run.tick))
        return Outcome.ok(Unit)
    }

    fun setAutoMaintain(patent: PatentId, by: CompanyId, on: Boolean): Outcome<Unit> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != IpOwner.company(by)) return Outcome.reject(Rules.NOT_OWNER, "not your patent")
        p.autoMaintain = on
        return Outcome.ok(Unit)
    }

    private fun end(f: Filing, status: FilingStatus, tick: Tick) {
        f.status = status
        f.endedAt = tick
        f.nextFeeDue = null
        f.graceUntil = null
    }

    /** Called by a court that strikes a patent down in one country. */
    internal fun revoke(patent: PatentId, country: CountryId, dispute: DisputeId?, run: IpRun) {
        val p = state.patentMap[patent] ?: return
        val f = p.filingMap[country] ?: return
        if (!f.status.live) return
        end(f, FilingStatus.REVOKED, run.tick)
        run.out.event(IpEvent.PatentRevoked(patent, country, dispute, run.tick))
    }

    /** Forces every filing of a family to end, used when a company is wound up with nobody to take its patents. */
    internal fun endAll(patent: PatentId, tick: Tick) {
        val p = state.patentMap[patent] ?: return
        for (f in p.filingMap.values) if (f.status.live) end(f, FilingStatus.ABANDONED, tick)
        p.askingPrice = null
        p.listedAt = null
    }

    /** Remaining life in days of the family's longest-lived live filing; zero when nothing is live. */
    fun remainingDays(p: Patent, tick: Tick): Long {
        var best = 0L
        for (f in p.filingMap.values) {
            val e = f.expiresAt
            if (f.status == FilingStatus.GRANTED && e != null) best = maxOf(best, e.value - tick.value)
            else if (f.status == FilingStatus.PENDING) best = maxOf(best, PatentRules.termTicks(regimeOf(f.country), p.pharma) - (tick.value - f.filedAt.value))
        }
        return best.coerceAtLeast(0L)
    }

    private companion object {
        const val OPEN_BEFORE_DUE_DAYS = 60L
    }
}
