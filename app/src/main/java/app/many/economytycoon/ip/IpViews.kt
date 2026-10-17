package app.many.economytycoon.ip

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.TechId

/** One national right of a patent, as a screen needs it. */
data class FilingRow(val country: CountryId, val status: FilingStatus, val expiresAt: Tick?, val nextFeeDue: Tick?, val nextFee: Money?, val breadthPpm: Long)

data class PatentRow(
    val id: PatentId,
    val title: String,
    val field: TechField,
    val standing: PatentStanding,
    val owner: IpOwner,
    val inventor: CompanyId,
    val filings: List<FilingRow>,
    val noveltyPpm: Long,
    val breadthPpm: Long,
    val citations: Int,
    val remainingDays: Long,
    val value: Money,
    val validityPpm: Long,
    val askingPrice: Money?,
    val essential: Boolean,
    val frandPledged: Boolean,
    val autoMaintain: Boolean,
    val licensedOut: Int,
    val fees: Money,
)

data class SecretRow(
    val id: SecretId,
    val tech: TechId,
    val name: String,
    val status: SecretStatus,
    val protectionPpm: Long,
    val knowers: Int,
    val hazardPpm: Long,
    val halfLifeDays: Long,
    val stolenBy: List<CompanyId>,
    val securitySpent: Money,
)

enum class LicenseSide { LICENSEE, LICENSOR }

data class LicenseRow(
    val id: LicenseId,
    val side: LicenseSide,
    val counterparty: IpOwner,
    val patents: List<String>,
    val kind: LicenseKind,
    val status: LicenseStatus,
    val terms: RoyaltyTerms,
    val ratePpm: Long,
    val paidTotal: Money,
    val arrears: Money,
    val daysLeft: Long?,
    val frand: Boolean,
    val compulsory: Boolean,
    val crossLicensed: Boolean,
    val inPool: Boolean,
    val lastDue: Money,
)

data class DisputeRow(
    val id: DisputeId,
    val kind: DisputeKind,
    val claimant: IpOwner,
    val defendant: IpOwner,
    val iAmClaimant: Boolean,
    val patent: String?,
    val country: CountryId,
    val stage: DisputeStage,
    val result: DisputeResult,
    val estimate: CaseEstimate?,
    val myCosts: Money,
    val theirCosts: Money,
    val offerBy: IpOwner?,
    val offerAmount: Money,
    val demand: Money,
    val award: Money,
    val injunctionUntil: Tick?,
    val daysInStage: Long,
    val willful: Boolean,
    val patentId: PatentId? = null,
)

data class PoolRow(
    val id: PoolId,
    val name: String,
    val administrator: CompanyId,
    val field: TechField,
    val open: Boolean,
    val members: Int,
    val patents: Int,
    val collected: Money,
    val iAmMember: Boolean,
    val myShareOfIncomePpm: Long,
    val myEarnings: Money,
    val ratePpm: Long,
)

data class DesignRow(val id: DesignAroundId, val patent: String, val status: DesignAroundStatus, val cost: Money, val successPpm: Long, val daysLeft: Long)

data class CompulsoryRow(val id: CompulsoryId, val patent: String, val country: CountryId, val ground: CompulsoryGround, val status: CompulsoryStatus, val daysToDecision: Long, val reason: String)

data class NpeRow(val id: NpeId, val name: String, val strategy: NpeStrategy, val patents: Int, val chest: Money, val demandsSent: Int, val aggressionPpm: Long)

/** A patent somebody else has listed. */
data class MarketRow(val patent: PatentId, val title: String, val seller: IpOwner, val price: Money, val value: Money, val remainingDays: Long)

/** A competitor using something the company owns, with a sensible licence to offer. */
data class UnlicensedUse(val patent: PatentId, val title: String, val user: CompanyId, val country: CountryId, val sales: Money, val suggested: RoyaltyTerms)

data class ProtectionRow(
    val tech: TechId,
    val name: String,
    val choice: Protection,
    val patentValue: Money,
    val secretValue: Money,
    val fees: Money,
    val grantChancePpm: Long,
    val halfLifeDays: Long,
    val reasons: List<String>,
    val countries: List<CountryId>,
    val breadth: BreadthChoice,
)

/** Everything the intellectual property screens show about one company at one moment. */
data class IpModel(
    val company: CompanyId,
    val tick: Tick,
    val policy: IpPolicy,
    val patents: List<PatentRow>,
    val secrets: List<SecretRow>,
    val licensesIn: List<LicenseRow>,
    val licensesOut: List<LicenseRow>,
    val offersToMe: List<LicenseRow>,
    val disputes: List<DisputeRow>,
    val pools: List<PoolRow>,
    val designs: List<DesignRow>,
    val compulsory: List<CompulsoryRow>,
    val market: List<MarketRow>,
    val npes: List<NpeRow>,
    val unprotected: List<ProtectionRow>,
    val unlicensedUse: List<UnlicensedUse>,
    val portfolioValue: Money,
    val licensingIncomePerYear: Money,
    val royaltiesPaid: Money,
    val royaltiesReceived: Money,
    val insights: List<Insight>,
)

/** Builds [IpModel]s from the live engine. Pure reading: nothing here changes the state. */
class IpViews(private val engine: IpEngine) {
    private val state get() = engine.state
    private val params get() = engine.params

    fun build(ctx: IpContext, company: CompanyId, tick: Tick): IpModel {
        val run = IpRun(tick, ctx, Wallet(ctx), IpOutput(state.ids))
        val me = IpOwner.company(company)
        val portfolio = engine.valuer.portfolio(me, run)
        val values = portfolio.patents.associateBy { it.patent }
        val titles = { id: PatentId -> state.patentMap[id]?.title ?: "Patent ${id.value}" }
        val mine = state.patentsOf(me)
        val patentRows = mine.map { patentRow(it, tick, values[it.id], me) }
        val licensesIn = state.licenseMap.values.filter { it.licensee == company && (it.isActive || it.status == LicenseStatus.EXPIRED || it.status == LicenseStatus.TERMINATED || it.status == LicenseStatus.BREACHED) }
        val licensesOut = state.licenseMap.values.filter { it.licensor == me && !it.isOffer && it.status != LicenseStatus.DECLINED && it.status != LicenseStatus.WITHDRAWN }
        val offers = state.licenseMap.values.filter { it.licensee == company && it.isOffer }
        val model = IpModel(
            company, tick, state.policy(company), patentRows,
            state.secretsOf(company).map { secretRow(it) },
            licensesIn.map { licenseRow(it, LicenseSide.LICENSEE, IpOwner(it.licensor.kind, it.licensor.id), tick, titles) },
            licensesOut.map { licenseRow(it, LicenseSide.LICENSOR, IpOwner.company(it.licensee), tick, titles) },
            offers.map { licenseRow(it, LicenseSide.LICENSEE, it.licensor, tick, titles) },
            state.disputesOf(me).sortedWith(compareByDescending<Dispute> { it.isOpen }.thenByDescending { it.openedAt }.thenBy { it.id }).map { disputeRow(run, it, me, titles) },
            state.poolMap.values.filter { it.open && (it.memberMap.containsKey(company) || it.administrator == company) }.map { poolRow(it, company, tick) },
            state.designAroundMap.values.filter { it.company == company }.map { DesignRow(it.id, titles(it.patent), it.status, it.cost, it.successPpm, (it.completesAt.value - tick.value).coerceAtLeast(0L)) },
            state.compulsoryMap.values.filter { it.applicant == company }.map { CompulsoryRow(it.id, titles(it.patent), it.country, it.ground, it.status, (it.decideAt.value - tick.value).coerceAtLeast(0L), it.reason) },
            marketRows(run, me),
            state.npeMap.values.map { n -> NpeRow(n.id, n.name, n.strategy, state.patentsOf(n.owner).size, n.chest, n.demandsSent, n.aggressionPpm) },
            engine.planner.adviceFor(run, company).map { a ->
                ProtectionRow(a.tech, a.name, a.advice.choice, a.advice.patentValue, a.advice.secretValue, a.quote.fees, a.quote.perCountry.maxOf { it.grantChancePpm }, a.advice.secretHalfLifeDays, a.advice.reasons, a.countries, a.breadth)
            },
            unlicensedUse(run, company),
            portfolio.total, portfolio.licensingIncome,
            sumPaid(state.licenseMap.values.filter { it.licensee == company }),
            sumPaid(state.licenseMap.values.filter { it.licensor == me }),
            emptyList(),
        )
        val facts = IpAdvisor.facts(engine, run, company)
        return model.copy(insights = IpAdvisor.advise(facts))
    }

    private fun sumPaid(l: List<License>): Money = Money.sum(l.map { it.paidTotal })

    private fun patentRow(p: Patent, tick: Tick, v: PatentValuation?, me: IpOwner): PatentRow {
        val filings = p.filingMap.values.map { f ->
            val fee = if (f.status == FilingStatus.GRANTED) PatentRules.maintenanceFee(params.office, engine.office.regimeOf(f.country), f.feeYearsPaid + 1) else null
            FilingRow(f.country, f.status, f.expiresAt, f.nextFeeDue, fee, f.breadthPpm)
        }
        return PatentRow(
            p.id, p.title, p.field, p.standing(), p.owner, p.inventor, filings, p.noveltyPpm, p.grantedBreadthPpm().coerceAtLeast(p.claimedBreadthPpm / 2),
            state.forwardCitations(p.id), engine.office.remainingDays(p, tick), v?.value ?: Money.ZERO, engine.valuer.validityPpm(p), p.askingPrice,
            p.standardEssential, p.frandPledged, p.autoMaintain, state.licenseMap.values.count { it.isActive && it.licensor == me && p.id in it.patents }, p.totalFees,
        )
    }

    private fun secretRow(s: TradeSecret): SecretRow {
        val hazard = engine.secrets.hazardPpm(s)
        val half = if (hazard <= 0L) Long.MAX_VALUE / 4 else 693_147L / hazard
        return SecretRow(s.id, s.tech, engine.content.technologyOrNull(s.tech)?.name ?: nameOf(s.tech), s.status, s.protectionPpm, s.knowers, hazard, half, s.knownTo, s.securitySpent)
    }

    private fun licenseRow(l: License, side: LicenseSide, other: IpOwner, tick: Tick, titles: (PatentId) -> String): LicenseRow = LicenseRow(
        l.id, side, other, l.patents.map(titles), l.kind, l.status, l.terms, engine.book.rateOf(l).ppm, l.paidTotal, l.arrears,
        l.endsAt?.let { (it.value - tick.value).coerceAtLeast(0L) }, l.frand, l.compulsory, l.crossWith != null, l.poolId != null, l.lastDue,
    )

    private fun disputeRow(run: IpRun, d: Dispute, me: IpOwner, titles: (PatentId) -> String): DisputeRow {
        val iAmClaimant = d.claimant == me
        return DisputeRow(
            d.id, d.kind, d.claimant, d.defendant, iAmClaimant, d.patent?.let(titles), d.country, d.stage, d.result,
            if (d.isOpen) engine.disputes.estimate(run, d) else null,
            if (iAmClaimant) d.claimantCosts else d.defendantCosts, if (iAmClaimant) d.defendantCosts else d.claimantCosts,
            d.offerBy, d.offerAmount, d.demand, d.award, d.injunctionUntil, run.tick.value - d.stageSince.value, d.willful, d.patent,
        )
    }

    private fun poolRow(p: PatentPool, company: CompanyId, tick: Tick): PoolRow {
        val m = p.memberMap[company]
        return PoolRow(
            p.id, p.name, p.administrator, p.field, p.open, p.memberMap.size, p.allPatents().size, p.collected, m != null,
            if (m != null) engine.pools.shareOf(p, company, tick) else 0L, (m?.earned ?: Money.ZERO) + if (p.administrator == company) p.adminEarned else Money.ZERO, p.terms.percent.ppm,
        )
    }

    private fun marketRows(run: IpRun, me: IpOwner): List<MarketRow> =
        engine.market.listed().filter { it.owner != me }.map { p ->
            MarketRow(p.id, p.title, p.owner, p.askingPrice ?: Money.ZERO, engine.valuer.value(p, run).value, engine.office.remainingDays(p, run.tick))
        }.sortedBy { it.price }

    private fun unlicensedUse(run: IpRun, company: CompanyId): List<UnlicensedUse> {
        val out = ArrayList<UnlicensedUse>()
        val from = Tick((run.tick.value - params.litigation.lookbackDays).coerceAtLeast(0L))
        for (p in state.patentsOf(IpOwner.company(company))) {
            for (country in p.countriesInForce(run.tick)) {
                for (pr in run.ctx.practicing(p.tech, country, from, run.tick).sortedBy { it.company }) {
                    if (pr.company == company || engine.disputes.mayPractise(pr.company, p, country, run.tick)) continue
                    val rate = Royalty.reasonablePercent(params.licensing, run.ctx.profitMargin(pr.company).ppm, p.filingMap[country]?.breadthPpm ?: p.claimedBreadthPpm, p.noveltyPpm)
                    out += UnlicensedUse(p.id, p.title, pr.company, country, pr.usage.sales, RoyaltyTerms(RoyaltyBasis.PERCENT, percent = rate))
                }
            }
        }
        return out
    }

    companion object {
        /** Readable name of a technology id when the content is not at hand: the key with its first letter capitalised. */
        fun nameOf(tech: TechId): String = tech.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
