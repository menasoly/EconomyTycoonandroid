package app.many.economytycoon.ip

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * Governments forcing patent holders to license. Which grounds exist, how long an applicant must have tried to
 * negotiate, how long the authority takes and what royalty it sets all come from the country's regime.
 */
class CompulsoryService(
    private val state: IpState,
    private val office: PatentOffice,
    private val book: LicenseBook,
    val params: IpParams = IpParams(),
) {
    fun requestsOf(company: CompanyId): List<CompulsoryRequest> = state.compulsoryMap.values.filter { it.applicant == company }

    /** Why a request would fail right now, or null when it is allowed. */
    fun check(run: IpRun, applicant: CompanyId, patent: PatentId, country: CountryId, ground: CompulsoryGround, recheck: Boolean = false): app.many.economytycoon.core.Rejection? {
        val rules = office.regimeOf(country).compulsory
        fun no(rule: String, msg: String) = app.many.economytycoon.core.Rejection(rule, msg)
        val p = state.patentMap[patent] ?: return no(Rules.UNKNOWN_ENTITY, "unknown patent")
        val f = p.filingMap[country] ?: return no(Rules.UNKNOWN_ENTITY, "the patent is not filed there")
        if (!f.inForceAt(run.tick)) return no(Rules.WRONG_STATE, "the patent is not in force there")
        if (!rules.enabled) return no(Rules.REGULATION, "this country does not grant compulsory licences")
        if (p.owner == IpOwner.company(applicant)) return no(Rules.INVALID_ARGUMENT, "you own that patent")
        if (book.isLicensed(applicant, patent, country, p.industry, run.tick)) return no(Rules.DUPLICATE, "you are already licensed")
        if (!recheck && state.compulsoryMap.values.any { it.applicant == applicant && it.patent == patent && it.country == country && it.status.open }) return no(Rules.DUPLICATE, "a request is already open")
        when (ground) {
            CompulsoryGround.NON_WORKING -> {
                if (rules.nonWorkingYears <= 0) return no(Rules.REGULATION, "non-working is not a ground here")
                val granted = f.grantedAt ?: return no(Rules.WRONG_STATE, "not granted yet")
                if (run.tick.value - granted.value < rules.nonWorkingYears.toLong() * Calendar.DAYS_PER_YEAR) return no(Rules.WRONG_STATE, "too soon to call it unworked")
                val owner = p.owner.companyOrNull()
                if (owner != null && run.ctx.isWorkedLocally(owner, p.tech, country)) return no(Rules.WRONG_STATE, "the patent is worked in the country")
            }
            CompulsoryGround.PUBLIC_HEALTH -> {
                if (!rules.publicHealth) return no(Rules.REGULATION, "public health is not a ground here")
                if (!run.ctx.publicHealthEmergency(country)) return no(Rules.WRONG_STATE, "there is no emergency")
            }
            CompulsoryGround.ANTI_COMPETITIVE -> {
                if (!rules.antiCompetitive) return no(Rules.REGULATION, "competition is not a ground here")
                val owner = p.owner.companyOrNull()
                val share = owner?.let { run.ctx.marketShare(it, p.field).ppm } ?: 0L
                if (share < ANTI_COMPETITIVE_SHARE_PPM) return no(Rules.WRONG_STATE, "the holder does not dominate the field")
            }
            CompulsoryGround.GOVERNMENT_USE -> {
                if (!rules.governmentUse) return no(Rules.REGULATION, "government use is not a ground here")
                val home = p.owner.companyOrNull()?.let { run.ctx.homeCountry(it) }
                if (home == country) return no(Rules.WRONG_STATE, "the holder is a domestic company")
            }
        }
        if (ground != CompulsoryGround.PUBLIC_HEALTH && !triedToNegotiate(applicant, patent, run.tick, rules.negotiationDays)) {
            return no(Rules.WRONG_STATE, "you must have tried to license voluntarily for ${rules.negotiationDays} days")
        }
        return null
    }

    /** An applicant has tried when an offer to it sat unaccepted, or was declined, at least the required days ago. */
    private fun triedToNegotiate(applicant: CompanyId, patent: PatentId, tick: Tick, days: Int): Boolean =
        state.licenseMap.values.any {
            it.licensee == applicant && patent in it.patents && it.status != LicenseStatus.ACTIVE &&
                tick.value - it.offeredAt.value >= days
        }

    fun request(run: IpRun, applicant: CompanyId, patent: PatentId, country: CountryId, ground: CompulsoryGround): Outcome<CompulsoryRequest> {
        check(run, applicant, patent, country, ground)?.let { return Outcome.Rejected(it) }
        val me = IpOwner.company(applicant)
        val fee = Money.units(params.litigation.filingCostUnits / 3).times(Rate(office.regimeOf(country).legalCostMultiplierPpm))
        if (!run.wallet.tryReserve(me, fee)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the application fee")
        run.out.charge(run.tick, ChargeKind.COURT_FEE, IpLedger.of(applicant), IpLedger.government(country), fee, "compulsory licence application", patent.value.toString())
        val rules = office.regimeOf(country).compulsory
        val r = CompulsoryRequest(state.ids.compulsory(), applicant, patent, country, ground, run.tick, Tick(run.tick.value + rules.reviewDays))
        state.compulsoryMap[r.id] = r
        return Outcome.ok(r)
    }

    fun withdraw(id: CompulsoryId, by: CompanyId): Outcome<CompulsoryRequest> {
        val r = state.compulsoryMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown request")
        if (r.applicant != by) return Outcome.reject(Rules.NOT_OWNER, "not your request")
        if (!r.status.open) return Outcome.reject(Rules.WRONG_STATE, "already decided")
        r.status = CompulsoryStatus.WITHDRAWN
        return Outcome.ok(r)
    }

    /** The royalty an authority would set on a patent for an applicant. */
    fun royaltyFor(run: IpRun, p: Patent, applicant: CompanyId, country: CountryId): Rate {
        val rules = office.regimeOf(country).compulsory
        val breadth = p.filingMap[country]?.breadthPpm ?: p.claimedBreadthPpm
        val fair = Royalty.reasonablePercent(params.licensing, run.ctx.profitMargin(applicant).ppm, breadth, p.noveltyPpm)
        return Rate(mulDiv(fair.ppm, rules.royaltyFactorPpm, 1_000_000L).coerceIn(1_000L, rules.maxRoyaltyPpm))
    }

    fun step(run: IpRun) {
        for (r in state.compulsoryMap.values.toList()) {
            if (!r.status.open || run.tick < r.decideAt) continue
            val reason = check(run, r.applicant, r.patent, r.country, r.ground, recheck = true)
            val stillApplies = reason == null
            if (!run.ctx.isActive(r.applicant)) { r.status = CompulsoryStatus.WITHDRAWN; continue }
            val p = state.patentMap[r.patent]
            if (p == null || !stillApplies) {
                r.status = CompulsoryStatus.DENIED
                r.reason = reason?.message ?: "the patent is gone"
                run.out.event(IpEvent.CompulsoryDecided(r.id, false, run.tick))
                continue
            }
            val odds = groundOdds(r.ground)
            if (!state.dice.chance("compulsory", Rate(odds), r.id.value)) {
                r.status = CompulsoryStatus.DENIED
                r.reason = "the authority was not persuaded"
                run.out.event(IpEvent.CompulsoryDecided(r.id, false, run.tick))
                continue
            }
            val rules = office.regimeOf(r.country).compulsory
            val terms = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = royaltyFor(run, p, r.applicant, r.country))
            val req = OfferRequest(p.owner, r.applicant, listOf(p.id), terms, LicenseKind.NON_EXCLUSIVE, LicenseScope.of(emptyList(), listOf(r.country)), rules.termYears * Calendar.DAYS_PER_YEAR)
            val l = book.grantDirect(run, req, Money.ZERO, ChargeKind.ROYALTY, compulsory = true, frand = p.frandPledged, memo = "compulsory licence")
            r.status = CompulsoryStatus.GRANTED
            r.licenseId = l.id
            r.reason = r.ground.label
            run.out.event(IpEvent.CompulsoryDecided(r.id, true, run.tick))
        }
    }

    private fun groundOdds(g: CompulsoryGround): Long = when (g) {
        CompulsoryGround.PUBLIC_HEALTH -> 950_000L
        CompulsoryGround.GOVERNMENT_USE -> 850_000L
        CompulsoryGround.NON_WORKING -> 750_000L
        CompulsoryGround.ANTI_COMPETITIVE -> 600_000L
    }

    private companion object {
        const val ANTI_COMPETITIVE_SHARE_PPM = 400_000L
    }
}
