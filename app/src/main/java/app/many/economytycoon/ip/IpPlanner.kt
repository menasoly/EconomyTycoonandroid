package app.many.economytycoon.ip

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * How a computer-controlled company leans on intellectual property. Each trait is drawn once per company from the
 * world seed, so rivals differ from one another but each behaves the same way from game to game.
 */
data class IpPersonality(
    /** Appetite for patents: low values prefer to leave things unfiled. */
    val patentingPpm: Long,
    /** Willingness to license its patents to others. */
    val licensingPpm: Long,
    /** Willingness to sue and to challenge other firms' patents. */
    val litigiousPpm: Long,
    /** Preference for secrecy over patents. */
    val secrecyPpm: Long,
) {
    companion object {
        fun of(dice: IpDice, company: CompanyId): IpPersonality = IpPersonality(
            dice.between("pers.patent", 200_000L, 900_000L, company.value),
            dice.between("pers.license", 150_000L, 900_000L, company.value),
            dice.between("pers.litigate", 100_000L, 900_000L, company.value),
            dice.between("pers.secret", 100_000L, 900_000L, company.value),
        )
    }
}

/**
 * The rules of thumb the computer companies follow once a month: protect what is worth protecting in the cheaper of
 * the two ways, license patents to firms that already use them, settle or fight disputes by comparing what each course
 * is expected to cost, and drop patents that no longer earn their fees. The planner only proposes commands; the
 * engine runs them through the same checks as a player's.
 */
class IpPlanner(
    private val state: IpState,
    private val office: PatentOffice,
    private val book: LicenseBook,
    private val disputes: DisputeService,
    private val designs: DesignAroundService,
    private val valuer: PatentValuer,
    private val content: ContentRepository,
    val params: IpParams = IpParams(),
) {
    private val pp get() = params.planner

    fun personality(company: CompanyId): IpPersonality = IpPersonality.of(state.dice, company)

    fun plan(run: IpRun, company: CompanyId): List<IpCommand> {
        val me = personality(company)
        val out = ArrayList<IpCommand>()
        out += disputeMoves(run, company, me)
        out += protectionMoves(run, company, me)
        out += licensingMoves(run, company, me)
        out += cullMoves(run, company)
        out += challengeMoves(run, company, me)
        return out
    }

    // ---- patent or secret ----

    /** A technology the company has not protected, with the comparison of the two ways of protecting it. */
    data class ProtectionAdvice(
        val tech: TechId,
        val name: String,
        val advice: ChoiceAdvice,
        val quote: FilingQuote,
        val countries: List<CountryId>,
        val breadth: BreadthChoice,
        val annualAdvantage: Money,
    )

    /** Every unprotected technology that earns the company something, with advice; used by the planner and by the screens. */
    fun adviceFor(run: IpRun, company: CompanyId): List<ProtectionAdvice> {
        val policy = state.policy(company)
        val countries = policy.defaultCountries.ifEmpty { listOfNotNull(run.ctx.homeCountry(company)) }
        if (countries.isEmpty()) return emptyList()
        val regime = office.regimeOf(countries.first())
        val out = ArrayList<ProtectionAdvice>()
        for (tech in run.ctx.knownTechs(company)) {
            if (state.patentOfTech(company, tech) != null || state.liveSecretFor(company, tech) != null) continue
            val def = content.technologyOrNull(tech) ?: continue
            val field = TechFields.of(def)
            val advantage = Money(mulDiv(run.ctx.annualSalesInField(company, field).raw, params.valuation.baseRoyaltyPpm, 1_000_000L))
            if (advantage.raw <= 0L) continue
            val req = FilingRequest(company, tech, countries, policy.defaultBreadth, 10)
            val quote = (office.quote(req, run.tick, run.ctx.independentHolders(tech, company).size) as? Outcome.Ok)?.value ?: continue
            val input = ChoiceInput(
                annualAdvantage = advantage,
                grantChancePpm = quote.perCountry.maxOf { it.grantChancePpm },
                grantedBreadthPpm = policy.defaultBreadth.breadthPpm,
                patentTermYears = regime.termYears,
                examinationDays = quote.perCountry.maxOf { it.examinationDays },
                patentLifetimeCost = quote.fees + PatentRules.lifetimeMaintenance(params.office, regime, false, regime.termYears),
                knowers = run.ctx.knowers(company, tech),
                exposurePpm = run.ctx.exposure(company, tech).ppm,
                protectionPpm = SecretRules.protectionFor(params.secrets, Money.ZERO),
                discountRatePpm = params.valuation.discountRatePpm,
            )
            out += ProtectionAdvice(tech, def.name, IpChoice.advise(input, params.secrets, params.office), quote, countries, policy.defaultBreadth, advantage)
        }
        return out
    }

    /** Chooses for each unprotected technology the company sells whether to patent it, keep it secret or do neither, tilted by its character. */
    fun protectionMoves(run: IpRun, company: CompanyId, me: IpPersonality): List<IpCommand> {
        val out = ArrayList<IpCommand>()
        for (a in adviceFor(run, company)) {
            if (out.size >= pp.maxFilingsPerRound) break
            val patentScore = mulDiv(a.advice.patentValue.raw, 500_000L + me.patentingPpm, 1_000_000L)
            val secretScore = mulDiv(a.advice.secretValue.raw, 500_000L + me.secrecyPpm, 1_000_000L)
            when {
                patentScore <= 0L && secretScore <= 0L -> {}
                patentScore >= secretScore -> out += IpCommand.FilePatent(company, a.tech, a.countries, a.breadth, 10)
                else -> out += IpCommand.KeepSecret(company, a.tech)
            }
        }
        return out
    }

    // ---- licensing out ----

    fun licensingMoves(run: IpRun, company: CompanyId, me: IpPersonality): List<IpCommand> {
        if (me.licensingPpm < 400_000L || state.policy(company).stance == EnforcementStance.LITIGATE) return emptyList()
        val from = Tick((run.tick.value - params.litigation.lookbackDays).coerceAtLeast(0L))
        val owner = IpOwner.company(company)
        val out = ArrayList<IpCommand>()
        for (p in state.patentsOf(owner)) {
            if (out.size >= MAX_OFFERS_PER_ROUND) break
            if (!p.isLive) continue
            for (country in p.countriesInForce(run.tick)) {
                for (pr in run.ctx.practicing(p.tech, country, from, run.tick).sortedBy { it.company }) {
                    if (pr.company == company || disputes.mayPractise(pr.company, p, country, run.tick)) continue
                    if (hasOpenOffer(owner, pr.company, p.id) || hasOpenDispute(p.id, pr.company)) continue
                    val margin = run.ctx.profitMargin(pr.company).ppm
                    val rate = Royalty.reasonablePercent(params.licensing, margin, p.filingMap[country]?.breadthPpm ?: p.claimedBreadthPpm, p.noveltyPpm)
                    val terms = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = rate)
                    out += IpCommand.OfferLicense(company, OfferRequest(owner, pr.company, listOf(p.id), terms, LicenseKind.NON_EXCLUSIVE, LicenseScope.of(emptyList(), listOf(country))))
                    if (out.size >= MAX_OFFERS_PER_ROUND) break
                }
                if (out.size >= MAX_OFFERS_PER_ROUND) break
            }
        }
        return out
    }

    private fun hasOpenOffer(licensor: IpOwner, licensee: CompanyId, patent: PatentId): Boolean =
        state.licenseMap.values.any { it.isOffer && it.licensor == licensor && it.licensee == licensee && patent in it.patents }

    private fun hasOpenDispute(patent: PatentId, defendant: CompanyId): Boolean =
        state.disputeMap.values.any { it.isOpen && it.patent == patent && it.defendant == IpOwner.company(defendant) }

    // ---- disputes ----

    fun disputeMoves(run: IpRun, company: CompanyId, me: IpPersonality): List<IpCommand> {
        val owner = IpOwner.company(company)
        val policy = state.policy(company)
        val out = ArrayList<IpCommand>()
        for (d in state.disputesOf(owner)) {
            if (!d.isOpen) continue
            val e = disputes.estimate(run, d)
            val standing = d.offerBy
            if (standing != null && standing != owner) {
                val okay = if (d.claimant == owner) d.offerAmount >= e.claimantReserve else d.offerAmount <= e.defendantReserve
                if (okay) { out += IpCommand.AcceptSettlement(company, d.id); continue }
            }
            if (d.claimant == owner) claimantMoves(run, company, me, policy, d, e, out) else defendantMoves(run, company, d, e, out)
        }
        return out
    }

    private fun claimantMoves(run: IpRun, company: CompanyId, me: IpPersonality, policy: IpPolicy, d: Dispute, e: CaseEstimate, out: MutableList<IpCommand>) {
        val owner = IpOwner.company(company)
        when (d.stage) {
            DisputeStage.DETECTED -> out += if (policy.stance == EnforcementStance.LITIGATE && me.litigiousPpm >= 300_000L) IpCommand.FileSuit(company, d.id) else IpCommand.SendNotice(company, d.id)
            DisputeStage.NOTICE -> {
                val waited = run.tick.value - (d.noticeAt?.value ?: run.tick.value)
                if (waited < params.litigation.noticeResponseDays) return
                if (e.claimantWinPpm >= 500_000L && me.litigiousPpm >= 300_000L) out += IpCommand.FileSuit(company, d.id)
                else if (e.zoneOfAgreement && d.offerBy != owner) out += IpCommand.OfferSettlement(company, d.id, Litigation.midpoint(e))
            }
            DisputeStage.FILED, DisputeStage.DISCOVERY, DisputeStage.TRIAL ->
                if (e.zoneOfAgreement && d.offerBy != owner) out += IpCommand.OfferSettlement(company, d.id, Litigation.midpoint(e))
            DisputeStage.JUDGMENT -> {
                val lost = d.decidedForClaimant == false
                if (lost && d.appealedBy == null && Litigation.reversalPpm(params.litigation, e.marginPpm) >= APPEAL_WORTH_PPM) out += IpCommand.Appeal(company, d.id)
            }
            else -> {}
        }
    }

    private fun defendantMoves(run: IpRun, company: CompanyId, d: Dispute, e: CaseEstimate, out: MutableList<IpCommand>) {
        val owner = IpOwner.company(company)
        when (d.stage) {
            DisputeStage.JUDGMENT -> {
                val lost = d.decidedForClaimant == true
                if (lost && d.appealedBy == null && Litigation.reversalPpm(params.litigation, e.marginPpm) >= APPEAL_WORTH_PPM) out += IpCommand.Appeal(company, d.id)
            }
            DisputeStage.DETECTED, DisputeStage.NOTICE, DisputeStage.FILED, DisputeStage.DISCOVERY, DisputeStage.TRIAL -> {
                val pid = d.patent
                if (e.claimantWinPpm >= 600_000L && d.kind == DisputeKind.INFRINGEMENT && pid != null) {
                    val p = state.patentMap[pid]
                    val alreadyRunning = designs.running(company).any { it.patent == pid }
                    if (p != null && !alreadyRunning && !state.hasClearance(company, pid) && designs.quote(p).cost.raw * 2L < e.awardIfWon.raw) {
                        out += IpCommand.StartDesignAround(company, pid, d.id)
                        return
                    }
                }
                if (e.claimantWinPpm >= 500_000L && e.zoneOfAgreement && d.offerBy != owner && d.stage != DisputeStage.DETECTED) {
                    out += IpCommand.OfferSettlement(company, d.id, Litigation.midpoint(e))
                }
            }
            else -> {}
        }
    }

    // ---- housekeeping ----

    /** Gives up patents whose worth no longer covers their renewal fees, unless they are licensed out or for sale. */
    fun cullMoves(run: IpRun, company: CompanyId): List<IpCommand> {
        val owner = IpOwner.company(company)
        val out = ArrayList<IpCommand>()
        for (p in state.patentsOf(owner)) {
            if (!p.isLive || p.isListed || p.standardEssential || p.frandPledged) continue
            if (state.licenseMap.values.any { it.isActive && it.licensor == owner && p.id in it.patents }) continue
            if (pools().any { it.contains(p.id) }) continue
            var fees = Money.ZERO
            for (f in p.filingMap.values) {
                if (f.status != FilingStatus.GRANTED) continue
                fees += PatentRules.maintenanceFee(params.office, office.regimeOf(f.country), f.feeYearsPaid + 1)
            }
            if (fees.raw <= 0L) continue
            val value = valuer.value(p, run).value
            if (mulDiv(value.raw, 1_000_000L, fees.raw) < pp.lapseBelowValueRatioPpm) out += IpCommand.AbandonPatent(company, p.id, null)
        }
        return out
    }

    private fun pools(): List<PatentPool> = state.poolMap.values.filter { it.open }

    /** A litigious firm attacks weak patents that stand in the way of products it already sells. */
    fun challengeMoves(run: IpRun, company: CompanyId, me: IpPersonality): List<IpCommand> {
        if (me.litigiousPpm < 600_000L) return emptyList()
        val from = Tick((run.tick.value - params.litigation.lookbackDays).coerceAtLeast(0L))
        for (p in state.patentMap.values) {
            if (p.owner == IpOwner.company(company) || !p.isLive || p.noveltyPpm >= WEAK_NOVELTY_PPM) continue
            for (country in p.countriesInForce(run.tick)) {
                val selling = run.ctx.practicing(p.tech, country, from, run.tick).any { it.company == company }
                if (!selling || disputes.mayPractise(company, p, country, run.tick)) continue
                if (state.disputeMap.values.any { it.isOpen && it.kind == DisputeKind.INVALIDITY && it.patent == p.id && it.country == country }) continue
                return listOf(IpCommand.ChallengePatent(company, p.id, country))
            }
        }
        return emptyList()
    }

    private companion object {
        const val MAX_OFFERS_PER_ROUND = 2
        const val APPEAL_WORTH_PPM = 100_000L
        const val WEAK_NOVELTY_PPM = 400_000L
    }
}
