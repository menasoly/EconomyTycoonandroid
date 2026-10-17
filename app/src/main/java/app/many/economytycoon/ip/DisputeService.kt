package app.many.economytycoon.ip

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rejection
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * Infringement, theft and validity disputes from the first sighting to the last payment. The service owns every
 * dispute: it spots infringers, moves cases through notice, filing, discovery, trial, judgment and appeal, runs up
 * the costs, lets the sides settle, and applies the outcome to the patent register and the books.
 */
class DisputeService(
    private val state: IpState,
    private val office: PatentOffice,
    private val book: LicenseBook,
    private val pools: PoolService,
    val params: IpParams = IpParams(),
) {
    private val lp get() = params.litigation

    fun get(id: DisputeId): Dispute? = state.disputeMap[id]

    // ---- rights and detection ----

    /** Whether [company] may use a patent in a place without being sued: it owns it, is licensed, shares a pool or designed around it. */
    fun mayPractise(company: CompanyId, p: Patent, country: CountryId, tick: Tick): Boolean =
        p.owner == IpOwner.company(company) ||
            book.isLicensed(company, p.id, country, p.industry, tick) ||
            pools.coveredByPool(company, p.id) ||
            state.hasClearance(company, p.id)

    private fun hasOpenFor(patent: PatentId, defendant: IpOwner, country: CountryId, tick: Tick): Boolean =
        state.disputeMap.values.any {
            it.patent == patent && it.defendant == defendant && it.country == country &&
                (it.isOpen || (it.closedAt?.let { c -> tick.value - c.value < lp.detectionCoolOffDays } ?: false))
        }

    private fun openCount(claimant: IpOwner): Int = state.disputeMap.values.count { it.claimant == claimant && it.isOpen }

    private fun initialEvidence(claimant: IpOwner): Long {
        val monitoring = claimant.companyOrNull()?.let { state.policy(it).monitoringPpm } ?: 800_000L
        return mulDiv(lp.evidenceFromMonitoringPpm, 500_000L + monitoring / 2, 1_000_000L)
    }

    private fun defenceEvidence(breadthPpm: Long): Long =
        (lp.defenceBaseEvidencePpm + (1_000_000L - breadthPpm) / 5).coerceIn(0L, 900_000L)

    /** Monthly sweep: owners notice companies using their patents without a licence. */
    fun detect(run: IpRun) {
        val month = run.tick.value / Calendar.DAYS_PER_MONTH
        val from = Tick((run.tick.value - lp.lookbackDays).coerceAtLeast(0L))
        for (p in state.patentMap.values.toList()) {
            if (!p.isLive) continue
            val owner = p.owner
            val company = owner.companyOrNull()
            if (company != null) {
                if (!run.ctx.isActive(company) || state.policy(company).stance == EnforcementStance.PASSIVE) continue
            }
            for (f in p.filingMap.values) {
                if (!f.inForceAt(run.tick)) continue
                if (openCount(owner) >= lp.maxOpenPerClaimant) break
                val practisers = run.ctx.practicing(p.tech, f.country, from, run.tick).sortedBy { it.company }
                for (pr in practisers) {
                    if (pr.company == company || pr.usage.sales < Money.units(params.npe.minTargetSalesUnits)) continue
                    if (!run.ctx.isActive(pr.company) || mayPractise(pr.company, p, f.country, run.tick)) continue
                    val defendant = IpOwner.company(pr.company)
                    if (hasOpenFor(p.id, defendant, f.country, run.tick)) continue
                    val monitoring = company?.let { state.policy(it).monitoringPpm } ?: 800_000L
                    val visibility = 500_000L + (pr.usage.sales.raw / Money.SCALE).coerceAtMost(1_000_000L) / 2
                    val chance = mulDiv(mulDiv(lp.detectionPpmPerMonth, monitoring, 1_000_000L), visibility, 1_000_000L)
                    if (!state.dice.chance("detect", Rate(chance), p.id.value * 10_000 + pr.company.value, month)) continue
                    open(run, owner, p, defendant, f.country, pr.usage)
                }
            }
        }
    }

    private fun open(run: IpRun, claimant: IpOwner, p: Patent, defendant: IpOwner, country: CountryId, usage: UsageReport): Dispute {
        val f = p.filingMap.getValue(country)
        val d = Dispute(state.ids.dispute(), DisputeKind.INFRINGEMENT, claimant, defendant, p.id, null, country, run.tick)
        d.claimantEvidencePpm = initialEvidence(claimant)
        d.defendantEvidencePpm = defenceEvidence(f.breadthPpm)
        d.infringingUnits = usage.units
        d.infringingSales = usage.sales
        state.disputeMap[d.id] = d
        run.out.event(IpEvent.InfringementDetected(d.id, p.id, defendant, run.tick))
        return d
    }

    /** A rights holder reports a company it believes is infringing. */
    fun report(run: IpRun, claimant: IpOwner, patent: PatentId, defendant: CompanyId, country: CountryId): Outcome<Dispute> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != claimant) return Outcome.reject(Rules.NOT_OWNER, "only the owner enforces a patent")
        val f = p.filingMap[country] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "not filed there")
        if (!f.inForceAt(run.tick)) return Outcome.reject(Rules.WRONG_STATE, "the patent is not in force there")
        if (claimant == IpOwner.company(defendant)) return Outcome.reject(Rules.INVALID_ARGUMENT, "cannot accuse yourself")
        if (!run.ctx.isActive(defendant)) return Outcome.reject(Rules.WRONG_STATE, "that company is out of business")
        if (mayPractise(defendant, p, country, run.tick)) return Outcome.reject(Rules.WRONG_STATE, "the company has the right to use it")
        if (hasOpenFor(patent, IpOwner.company(defendant), country, run.tick)) return Outcome.reject(Rules.DUPLICATE, "that matter is already open")
        if (openCount(claimant) >= lp.maxOpenPerClaimant) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many disputes open")
        val from = Tick((run.tick.value - lp.lookbackDays).coerceAtLeast(0L))
        val usage = run.ctx.practicing(p.tech, country, from, run.tick).firstOrNull { it.company == defendant }?.usage
            ?: return Outcome.reject(Rules.WRONG_STATE, "no sign that the company uses it")
        return Outcome.ok(open(run, claimant, p, IpOwner.company(defendant), country, usage))
    }

    /** A secret that was stolen and noticed becomes a case against the thief. */
    fun reportTheft(run: IpRun, secret: SecretId, thief: CompanyId, evidencePpm: Long): Outcome<Dispute> {
        val s = state.secretMap[secret] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown secret")
        if (thief !in s.knownToSet) return Outcome.reject(Rules.WRONG_STATE, "no theft on record")
        val claimant = IpOwner.company(s.owner)
        if (state.disputeMap.values.any { it.secret == secret && it.defendant == IpOwner.company(thief) && it.isOpen }) return Outcome.reject(Rules.DUPLICATE, "already open")
        val country = run.ctx.homeCountry(s.owner) ?: CountryId(0)
        val d = Dispute(state.ids.dispute(), DisputeKind.MISAPPROPRIATION, claimant, IpOwner.company(thief), null, secret, country, run.tick)
        d.claimantEvidencePpm = evidencePpm
        d.defendantEvidencePpm = lp.defenceBaseEvidencePpm
        state.disputeMap[d.id] = d
        return Outcome.ok(d)
    }

    /** A company asks a court to strike a patent down in one country. */
    fun challenge(run: IpRun, challenger: CompanyId, patent: PatentId, country: CountryId): Outcome<Dispute> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        val f = p.filingMap[country] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "not filed there")
        if (!f.inForceAt(run.tick)) return Outcome.reject(Rules.WRONG_STATE, "the patent is not in force there")
        if (p.owner == IpOwner.company(challenger)) return Outcome.reject(Rules.INVALID_ARGUMENT, "cannot challenge your own patent")
        if (state.disputeMap.values.any { it.kind == DisputeKind.INVALIDITY && it.patent == patent && it.country == country && it.isOpen }) return Outcome.reject(Rules.DUPLICATE, "a challenge is already open")
        val me = IpOwner.company(challenger)
        if (openCount(me) >= lp.maxOpenPerClaimant) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many disputes open")
        val fee = courtFee(country)
        if (!run.wallet.tryReserve(me, fee)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the court fee")
        run.out.charge(run.tick, ChargeKind.COURT_FEE, IpLedger.of(challenger), IpLedger.government(country), fee, "validity challenge", patent.value.toString())
        val d = Dispute(state.ids.dispute(), DisputeKind.INVALIDITY, me, p.owner, patent, null, country, run.tick)
        d.claimantEvidencePpm = initialEvidence(me)
        d.defendantEvidencePpm = defenceEvidence(f.breadthPpm)
        d.stage = DisputeStage.FILED
        d.claimantCosts += fee
        d.claimantCostsPaid += fee
        state.disputeMap[d.id] = d
        run.out.event(IpEvent.DisputeAdvanced(d.id, DisputeStage.FILED, run.tick))
        return Outcome.ok(d)
    }

    private fun courtFee(country: CountryId): Money =
        Money.units(lp.filingCostUnits).times(Rate(office.regimeOf(country).legalCostMultiplierPpm))

    // ---- moving a case along ----

    private fun advance(run: IpRun, d: Dispute, stage: DisputeStage) {
        d.stage = stage
        d.stageSince = run.tick
        run.out.event(IpEvent.DisputeAdvanced(d.id, stage, run.tick))
    }

    private fun check(id: DisputeId, by: IpOwner, claimantOnly: Boolean): Pair<Dispute?, Rejection?> {
        val d = state.disputeMap[id] ?: return null to Rejection(Rules.UNKNOWN_ENTITY, "unknown dispute")
        if (!d.isOpen) return d to Rejection(Rules.WRONG_STATE, "the dispute is closed")
        if (claimantOnly && d.claimant != by) return d to Rejection(Rules.NOT_OWNER, "only the claimant can do that")
        if (!d.involves(by)) return d to Rejection(Rules.NOT_OWNER, "not a party to this dispute")
        return d to null
    }

    /** Sends the defendant a demand; from now on any infringement is wilful. */
    fun sendNotice(run: IpRun, id: DisputeId, by: IpOwner, demand: Money? = null): Outcome<Dispute> {
        val (d, bad) = check(id, by, true)
        if (bad != null || d == null) return Outcome.Rejected(bad ?: Rejection(Rules.UNKNOWN_ENTITY, "unknown dispute"))
        if (d.stage != DisputeStage.DETECTED) return Outcome.reject(Rules.WRONG_STATE, "notice can only follow a sighting")
        d.willful = true
        d.noticeAt = run.tick
        d.demand = demand ?: defaultDemand(run, d)
        advance(run, d, DisputeStage.NOTICE)
        return Outcome.ok(d)
    }

    /** What a sensible claimant asks for at the notice stage: the reasonable royalty on past sales, less a discount for avoiding court. */
    fun defaultDemand(run: IpRun, d: Dispute): Money {
        val e = estimate(run, d)
        return Money(mulDiv(e.awardIfWon.raw, 1_000_000L - lp.settlementDiscountPpm, 1_000_000L))
    }

    /** Takes the case to court. */
    fun file(run: IpRun, id: DisputeId, by: IpOwner): Outcome<Dispute> {
        val (d, bad) = check(id, by, true)
        if (bad != null || d == null) return Outcome.Rejected(bad ?: Rejection(Rules.UNKNOWN_ENTITY, "unknown dispute"))
        if (d.stage != DisputeStage.DETECTED && d.stage != DisputeStage.NOTICE) return Outcome.reject(Rules.WRONG_STATE, "the case is already in court")
        val fee = courtFee(d.country)
        if (!payOrNpe(run, by, fee, ChargeKind.COURT_FEE, IpLedger.government(d.country), "court filing fee", d.id)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay the court fee")
        d.claimantCosts += fee
        d.claimantCostsPaid += fee
        advance(run, d, DisputeStage.FILED)
        return Outcome.ok(d)
    }

    private fun payOrNpe(run: IpRun, by: IpOwner, amount: Money, kind: ChargeKind, payee: app.many.economytycoon.finance.LedgerOwner, memo: String, id: DisputeId): Boolean {
        val npe = by.npeOrNull()?.let { state.npeMap[it] }
        if (npe != null) {
            if (npe.chest < amount) return false
            npe.chest -= amount
            npe.spent += amount
            return true
        }
        if (!run.wallet.tryReserve(by, amount)) return false
        run.out.charge(run.tick, kind, IpLedger.of(by), payee, amount, memo, id.value.toString())
        return true
    }

    fun withdraw(run: IpRun, id: DisputeId, by: IpOwner): Outcome<Dispute> {
        val (d, bad) = check(id, by, true)
        if (bad != null || d == null) return Outcome.Rejected(bad ?: Rejection(Rules.UNKNOWN_ENTITY, "unknown dispute"))
        if (d.stage == DisputeStage.JUDGMENT || d.stage == DisputeStage.APPEAL) return Outcome.reject(Rules.WRONG_STATE, "judgment has been given")
        close(run, d, DisputeResult.WITHDRAWN)
        return Outcome.ok(d)
    }

    /** The side that lost may appeal during the window after judgment, once. */
    fun appeal(run: IpRun, id: DisputeId, by: IpOwner): Outcome<Dispute> {
        val (d, bad) = check(id, by, false)
        if (bad != null || d == null) return Outcome.Rejected(bad ?: Rejection(Rules.UNKNOWN_ENTITY, "unknown dispute"))
        if (d.stage != DisputeStage.JUDGMENT) return Outcome.reject(Rules.WRONG_STATE, "there is no judgment to appeal")
        val claimantWon = d.decidedForClaimant == true
        val loser = if (claimantWon) d.defendant else d.claimant
        if (by != loser) return Outcome.reject(Rules.INVALID_ARGUMENT, "only the losing side appeals")
        if (d.appealedBy != null) return Outcome.reject(Rules.LIMIT_EXCEEDED, "already appealed")
        d.appealedBy = by
        advance(run, d, DisputeStage.APPEAL)
        return Outcome.ok(d)
    }

    // ---- the daily clock ----

    fun step(run: IpRun) {
        val monthly = Calendar.isMonthStart(run.tick)
        if (monthly) detect(run)
        for (d in state.disputeMap.values.toList()) {
            if (!d.isOpen) continue
            if (!stillMeaningful(run, d)) continue
            accrue(run, d)
            val days = run.tick.value - d.stageSince.value
            when (d.stage) {
                DisputeStage.DETECTED -> if (days >= DETECTED_PATIENCE_DAYS) close(run, d, DisputeResult.LAPSED)
                DisputeStage.NOTICE -> if (days >= 2L * lp.noticeResponseDays) close(run, d, DisputeResult.LAPSED)
                DisputeStage.FILED -> if (days >= lp.pleadingDays) { gatherEvidence(d); advance(run, d, DisputeStage.DISCOVERY) }
                DisputeStage.DISCOVERY -> if (days >= lp.discoveryDays) { gatherEvidence(d); advance(run, d, DisputeStage.TRIAL) }
                DisputeStage.TRIAL -> if (days >= lp.trialDays) judge(run, d)
                DisputeStage.JUDGMENT -> if (days >= lp.appealWindowDays) finalize(run, d)
                DisputeStage.APPEAL -> if (days >= lp.appealDays) decideAppeal(run, d)
                DisputeStage.CLOSED -> {}
            }
            if (monthly && d.isOpen) { refreshSales(run, d); payCosts(run, d) }
        }
    }

    private fun stillMeaningful(run: IpRun, d: Dispute): Boolean {
        val claimantCo = d.claimant.companyOrNull()
        val defendantCo = d.defendant.companyOrNull()
        if (claimantCo != null && !run.ctx.isActive(claimantCo)) { close(run, d, DisputeResult.WITHDRAWN); return false }
        if (defendantCo != null && !run.ctx.isActive(defendantCo)) { close(run, d, DisputeResult.LAPSED); return false }
        val pid = d.patent ?: return true
        val p = state.patentMap[pid]
        val f = p?.filingMap?.get(d.country)
        if (p == null || f == null || !f.status.live) {
            // A patent that ended during a suit takes the suit with it, except for damages already earned once judged.
            if (d.stage != DisputeStage.JUDGMENT && d.stage != DisputeStage.APPEAL) { close(run, d, DisputeResult.LAPSED); return false }
        }
        if (d.kind == DisputeKind.INFRINGEMENT && d.stage.ordinal < DisputeStage.JUDGMENT.ordinal && p != null && p.owner != d.claimant) {
            close(run, d, DisputeResult.LAPSED)
            return false
        }
        return true
    }

    private fun accrue(run: IpRun, d: Dispute) {
        val level = office.regimeOf(d.country).legalCostMultiplierPpm
        val daily = Money(mulDiv(Litigation.dailyCost(lp, d.stage).raw, level, 1_000_000L))
        if (daily.raw <= 0L) return
        d.claimantCosts += daily
        d.defendantCosts += daily
    }

    private fun gatherEvidence(d: Dispute) {
        val stage = d.stage.ordinal.toLong()
        d.claimantEvidencePpm = (d.claimantEvidencePpm + state.dice.between("discovery.c", 0L, lp.discoveryGainMaxPpm, d.id.value, stage)).coerceAtMost(1_000_000L)
        d.defendantEvidencePpm = (d.defendantEvidencePpm + state.dice.between("discovery.d", 0L, lp.discoveryGainMaxPpm, d.id.value, stage)).coerceAtMost(1_000_000L)
    }

    private fun refreshSales(run: IpRun, d: Dispute) {
        if (d.kind != DisputeKind.INFRINGEMENT) return
        val p = d.patent?.let { state.patentMap[it] } ?: return
        val co = d.defendant.companyOrNull() ?: return
        val from = Tick((run.tick.value - Calendar.DAYS_PER_MONTH).coerceAtLeast(0L))
        val u = run.ctx.practicing(p.tech, d.country, from, run.tick).firstOrNull { it.company == co }?.usage ?: return
        d.infringingSales += u.sales
        d.infringingUnits = Quantity(d.infringingUnits.raw + u.units.raw)
    }

    private fun payCosts(run: IpRun, d: Dispute) {
        val cDue = d.claimantCosts - d.claimantCostsPaid
        val dDue = d.defendantCosts - d.defendantCostsPaid
        if (cDue.raw > 0L) { chargeCosts(run, d, d.claimant, cDue); d.claimantCostsPaid += cDue }
        if (dDue.raw > 0L) { chargeCosts(run, d, d.defendant, dDue); d.defendantCostsPaid += dDue }
    }

    private fun chargeCosts(run: IpRun, d: Dispute, who: IpOwner, amount: Money) {
        val npe = who.npeOrNull()?.let { state.npeMap[it] }
        if (npe != null) { val pay = Money.min(amount, npe.chest); npe.chest -= pay; npe.spent += pay; return }
        run.wallet.reserve(who, Money.min(amount, run.wallet.available(who)))
        run.out.charge(run.tick, ChargeKind.LEGAL_FEES, IpLedger.of(who), IpLedger.professionals, amount, "legal fees", d.id.value.toString())
    }

    // ---- reading a case ----

    fun marginOf(d: Dispute): Long = margin(d)

    private fun margin(d: Dispute): Long {
        val p = d.patent?.let { state.patentMap[it] }
        val reg = office.regimeOf(d.country)
        return when (d.kind) {
            DisputeKind.INFRINGEMENT -> {
                val p0 = p ?: return 0L
                val breadth = p0.filingMap[d.country]?.breadthPpm ?: p0.claimedBreadthPpm
                Litigation.infringementStrength(lp, reg, breadth, p0.noveltyPpm, d.claimantEvidencePpm) - Litigation.defenceStrength(p0.noveltyPpm, d.defendantEvidencePpm)
            }
            DisputeKind.INVALIDITY -> {
                val p0 = p ?: return 0L
                Litigation.challengeStrength(p0.noveltyPpm, d.claimantEvidencePpm) - Litigation.validityDefence(reg, p0.noveltyPpm, d.defendantEvidencePpm)
            }
            DisputeKind.MISAPPROPRIATION -> {
                val s = d.secret?.let { state.secretMap[it] }
                Litigation.misappropriationStrength(d.claimantEvidencePpm, s?.protectionPpm ?: 0L) - d.defendantEvidencePpm
            }
        }
    }

    /** Where the case stands, with the reserves each side should hold when thinking about a deal. */
    fun estimate(run: IpRun, d: Dispute): CaseEstimate {
        val reg = office.regimeOf(d.country)
        val m = margin(d)
        val award = potentialAward(run, d)
        val days = (run.tick.value - d.stageSince.value).toInt()
        val toGo = Litigation.costToGo(lp, reg, d.stage, days)
        val shifted = Money(mulDiv(toGo.raw + d.defendantCosts.raw, lp.costShiftPpm, 1_000_000L))
        val harm = if (d.kind == DisputeKind.INFRINGEMENT) Money(mulDiv(d.infringingSales.raw, 100_000L, 1_000_000L)) else Money.ZERO
        return Litigation.estimate(lp, m, award, shifted, toGo, toGo, harm)
    }

    private fun potentialAward(run: IpRun, d: Dispute): Money {
        val reg = office.regimeOf(d.country)
        return when (d.kind) {
            DisputeKind.INFRINGEMENT -> {
                val p = d.patent?.let { state.patentMap[it] } ?: return Money.ZERO
                val defendant = d.defendant.companyOrNull()
                val margin = defendant?.let { run.ctx.profitMargin(it).ppm } ?: 120_000L
                val breadth = p.filingMap[d.country]?.breadthPpm ?: p.claimedBreadthPpm
                val rate = Royalty.reasonablePercent(params.licensing, margin, breadth, p.noveltyPpm).ppm
                val claimantCo = d.claimant.companyOrNull()
                val share = claimantCo?.let { run.ctx.marketShare(it, p.field) } ?: Rate.ZERO
                val claimantMargin = claimantCo?.let { run.ctx.profitMargin(it) } ?: Rate(lp.lostProfitMarginPpm)
                Litigation.damages(lp, reg, d.infringingSales, rate, d.willful, share, claimantMargin)
            }
            DisputeKind.MISAPPROPRIATION -> {
                val s = d.secret?.let { state.secretMap[it] } ?: return Money.ZERO
                val adv = Money(mulDiv(run.ctx.annualSalesInField(s.owner, s.field).raw, params.valuation.baseRoyaltyPpm, 1_000_000L))
                Litigation.damagesForTheft(lp, reg, adv, 1)
            }
            DisputeKind.INVALIDITY -> Money.ZERO
        }
    }

    // ---- judgment ----

    private fun judge(run: IpRun, d: Dispute) {
        val roll = state.dice.rate("trial", d.id.value, 0L).ppm
        val forClaimant = Litigation.decide(lp, margin(d), roll)
        d.decidedForClaimant = forClaimant
        d.judgmentAt = run.tick
        applyJudgmentTerms(run, d)
        advance(run, d, DisputeStage.JUDGMENT)
        run.out.event(IpEvent.JudgmentGiven(d.id, forClaimant, d.award, d.injunctionUntil != null, run.tick))
    }

    private fun decideAppeal(run: IpRun, d: Dispute) {
        val roll = state.dice.rate("appeal", d.id.value, 0L).ppm
        val reversed = roll < Litigation.reversalPpm(lp, margin(d))
        if (reversed) d.decidedForClaimant = d.decidedForClaimant != true
        applyJudgmentTerms(run, d)
        run.out.event(IpEvent.JudgmentGiven(d.id, d.decidedForClaimant == true, d.award, d.injunctionUntil != null, run.tick))
        finalize(run, d)
    }

    /**
     * Fixes what the judgment orders. The loser pays: damages plus a share of the winner's costs when the claimant
     * wins an infringement or theft case, otherwise a share of the winner's costs alone. A patent falls when a
     * challenge succeeds, or when a suit against a weak patent meets a strong defence.
     */
    private fun applyJudgmentTerms(run: IpRun, d: Dispute) {
        d.award = Money.ZERO
        d.injunctionUntil = null
        d.courtRoyalty = null
        d.patentRevoked = false
        val won = d.decidedForClaimant == true
        val winnerCosts = if (won) d.claimantCosts else d.defendantCosts
        val shifted = Money(mulDiv(winnerCosts.raw, lp.costShiftPpm, 1_000_000L))
        val p = d.patent?.let { state.patentMap[it] }
        when (d.kind) {
            DisputeKind.INFRINGEMENT -> if (won) {
                d.award = potentialAward(run, d) + shifted
                if (p != null) setRemedy(run, d, p)
            } else {
                d.award = shifted
                d.patentRevoked = p != null && p.noveltyPpm < lp.invalidityNoveltyFloorPpm && d.defendantEvidencePpm >= lp.invalidityNoveltyFloorPpm
            }
            DisputeKind.INVALIDITY -> {
                d.patentRevoked = won
                d.award = shifted
            }
            DisputeKind.MISAPPROPRIATION -> d.award = if (won) potentialAward(run, d) + shifted else shifted
        }
    }

    /** An injunction when the court allows one; otherwise a royalty that runs with the patent. */
    private fun setRemedy(run: IpRun, d: Dispute, p: Patent) {
        val reg = office.regimeOf(d.country)
        val defendant = d.defendant.companyOrNull() ?: return
        val claimantIsNpe = d.claimant.isNpe
        val willing = book.isWillingLicensee(defendant, p.id)
        val allowed = Frand.injunctionAllowed(p.frandPledged, willing) && (!claimantIsNpe || reg.injunctionsForNpe)
        if (allowed) {
            d.injunctionUntil = Tick(run.tick.value + lp.injunctionDays)
        } else {
            val margin = run.ctx.profitMargin(defendant).ppm
            val rate = Royalty.reasonablePercent(params.licensing, margin, p.filingMap[d.country]?.breadthPpm ?: p.claimedBreadthPpm, p.noveltyPpm)
            d.courtRoyalty = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = rate)
        }
    }

    private fun finalize(run: IpRun, d: Dispute) {
        val won = d.decidedForClaimant == true
        val result = if (won) DisputeResult.CLAIMANT_WON else DisputeResult.DEFENDANT_WON
        if (d.award.raw > 0L) {
            val payer = if (won) d.defendant else d.claimant
            val payee = if (won) d.claimant else d.defendant
            transfer(run, d, payer, payee, d.award, ChargeKind.DAMAGES, "court award")
            d.awardPaid = true
        }
        val royalty = d.courtRoyalty
        val pid = d.patent
        val defendantCo = d.defendant.companyOrNull()
        if (royalty != null && pid != null && defendantCo != null) {
            val req = OfferRequest(d.claimant, defendantCo, listOf(pid), royalty, LicenseKind.NON_EXCLUSIVE, LicenseScope.of(emptyList(), listOf(d.country)), 5 * Calendar.DAYS_PER_YEAR)
            book.grantDirect(run, req, Money.ZERO, ChargeKind.ROYALTY, compulsory = true, frand = state.patentMap[pid]?.frandPledged == true, memo = "court-ordered licence")
        }
        if (d.patentRevoked && pid != null) office.revoke(pid, d.country, d.id, run)
        close(run, d, result, d.award)
    }

    private fun transfer(run: IpRun, d: Dispute, payer: IpOwner, payee: IpOwner, amount: Money, kind: ChargeKind, memo: String) {
        val payerNpe = payer.npeOrNull()?.let { state.npeMap[it] }
        val payeeNpe = payee.npeOrNull()?.let { state.npeMap[it] }
        if (payerNpe != null) { val pay = Money.min(amount, payerNpe.chest); payerNpe.chest -= pay; payerNpe.spent += pay; creditNpeOrLedger(run, d, payee, payeeNpe, pay, kind, memo, fromNpe = true); return }
        run.out.charge(run.tick, kind, IpLedger.of(payer), IpLedger.of(payee), amount, memo, d.id.value.toString())
        if (payeeNpe != null) { payeeNpe.chest += amount; payeeNpe.earned += amount }
    }

    private fun creditNpeOrLedger(run: IpRun, d: Dispute, payee: IpOwner, payeeNpe: Npe?, amount: Money, kind: ChargeKind, memo: String, fromNpe: Boolean) {
        if (amount.raw <= 0L) return
        if (payeeNpe != null) { payeeNpe.chest += amount; payeeNpe.earned += amount; return }
        if (fromNpe) run.out.charge(run.tick, kind, IpLedger.households(), IpLedger.of(payee), amount, memo, d.id.value.toString())
    }

    private fun close(run: IpRun, d: Dispute, result: DisputeResult, paid: Money = Money.ZERO) {
        payCosts(run, d)
        d.result = result
        d.closedAt = run.tick
        d.stage = DisputeStage.CLOSED
        d.stageSince = run.tick
        run.out.event(IpEvent.DisputeClosed(d.id, result, paid, run.tick))
    }

    // ---- settlement ----

    /** Puts a figure on the table. The other side may accept it, or the claimant may keep going. */
    fun offerSettlement(run: IpRun, id: DisputeId, by: IpOwner, amount: Money): Outcome<Dispute> {
        val (d, bad) = check(id, by, false)
        if (bad != null || d == null) return Outcome.Rejected(bad ?: Rejection(Rules.UNKNOWN_ENTITY, "unknown dispute"))
        if (d.stage == DisputeStage.JUDGMENT || d.stage == DisputeStage.APPEAL) return Outcome.reject(Rules.WRONG_STATE, "judgment has been given")
        if (amount.raw < 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "an offer cannot be negative")
        d.offerBy = by
        d.offerAmount = amount
        return Outcome.ok(d)
    }

    /** The party who did not make the standing offer accepts it: the defendant pays, a licence for the patent begins, the case ends. */
    fun acceptSettlement(run: IpRun, id: DisputeId, by: IpOwner): Outcome<Money> {
        val (d, bad) = check(id, by, false)
        if (bad != null || d == null) return Outcome.Rejected(bad ?: Rejection(Rules.UNKNOWN_ENTITY, "unknown dispute"))
        val offerBy = d.offerBy ?: return Outcome.reject(Rules.WRONG_STATE, "no offer is standing")
        if (offerBy == by) return Outcome.reject(Rules.INVALID_ARGUMENT, "you made the offer")
        if (d.stage == DisputeStage.JUDGMENT || d.stage == DisputeStage.APPEAL) return Outcome.reject(Rules.WRONG_STATE, "judgment has been given")
        val amount = d.offerAmount
        val payerAble = d.defendant.companyOrNull()?.let { run.wallet.canPay(d.defendant, amount) } ?: true
        if (!payerAble) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the defendant cannot pay that now")
        settle(run, d, amount)
        return Outcome.ok(amount)
    }

    private fun settle(run: IpRun, d: Dispute, amount: Money) {
        val defendantCo = d.defendant.companyOrNull()
        val pid = d.patent
        if (d.kind == DisputeKind.INFRINGEMENT && pid != null && defendantCo != null) {
            val terms = RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = amount)
            val req = OfferRequest(d.claimant, defendantCo, listOf(pid), terms, LicenseKind.NON_EXCLUSIVE, LicenseScope.of(emptyList(), listOf(d.country)), lp.settlementLicenseYears * Calendar.DAYS_PER_YEAR)
            if (defendantCo.let { run.wallet.tryReserve(d.defendant, amount) } || amount.raw == 0L) {
                book.grantDirect(run, req, amount, ChargeKind.SETTLEMENT, compulsory = false, frand = state.patentMap[pid]?.frandPledged == true, memo = "settlement licence")
            }
        } else if (amount.raw > 0L) {
            transfer(run, d, d.defendant, d.claimant, amount, ChargeKind.SETTLEMENT, "settlement")
        }
        d.injunctionUntil = null
        close(run, d, DisputeResult.SETTLED, amount)
    }

    /** Accepts a standing offer if it lies inside the accepting side's reserve; used by computer-controlled parties. */
    fun autoRespond(run: IpRun, d: Dispute, responder: IpOwner): Boolean {
        val offerBy = d.offerBy ?: return false
        if (offerBy == responder || !d.isOpen) return false
        val e = estimate(run, d)
        val ok = if (responder == d.claimant) d.offerAmount >= e.claimantReserve else d.offerAmount <= e.defendantReserve
        return ok && acceptSettlement(run, d.id, responder).isOk
    }

    // ---- hooks from other services ----

    /** A licence between the parties ends any open infringement case on the same patent. */
    fun onLicensed(run: IpRun, l: License) {
        for (d in state.disputeMap.values.toList()) {
            if (!d.isOpen || d.kind != DisputeKind.INFRINGEMENT) continue
            if (d.patent !in l.patents || d.defendant != IpOwner.company(l.licensee)) continue
            if (!l.scope.coversCountry(d.country)) continue
            d.injunctionUntil = null
            close(run, d, DisputeResult.LICENSED)
        }
        for (d in state.disputeMap.values) {
            if (d.kind == DisputeKind.INFRINGEMENT && d.patent in l.patents && d.defendant == IpOwner.company(l.licensee) && d.injunctionAt(run.tick)) d.injunctionUntil = run.tick
        }
    }

    /** A successful design-around frees the company of the patent. */
    fun onDesignAround(run: IpRun, company: CompanyId, patent: PatentId) {
        for (d in state.disputeMap.values.toList()) {
            if (d.kind != DisputeKind.INFRINGEMENT || d.patent != patent || d.defendant != IpOwner.company(company)) continue
            if (d.isOpen && d.stage != DisputeStage.JUDGMENT && d.stage != DisputeStage.APPEAL) close(run, d, DisputeResult.DESIGNED_AROUND)
            if (d.injunctionAt(run.tick)) d.injunctionUntil = run.tick
        }
    }

    /** True while a court order keeps [company] from using the patent's technology in the country. */
    fun isEnjoined(company: CompanyId, patent: PatentId, country: CountryId, tick: Tick): Boolean =
        state.disputeMap.values.any {
            it.kind == DisputeKind.INFRINGEMENT && it.patent == patent && it.defendant == IpOwner.company(company) &&
                it.country == country && it.injunctionAt(tick)
        }

    private companion object {
        const val DETECTED_PATIENCE_DAYS = 60L
    }
}
