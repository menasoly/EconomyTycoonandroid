package app.many.economytycoon.ip

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What one patent family is worth, with the pieces that make it up. */
data class PatentValuation(
    val patent: PatentId,
    /** Present value of the royalties the patent saves or earns while it lasts. */
    val income: Money,
    /** What it would cost to research and file again, less wear. */
    val cost: Money,
    /** The two approaches blended and adjusted for citations and standards. */
    val value: Money,
    val remainingYears: Int,
    /** Chance the patent survives a challenge, ppm. */
    val validityPpm: Long,
    /** Royalty rate used for the income approach, ppm of sales. */
    val royaltyRatePpm: Long,
    val marketSales: Money,
    val premiumPpm: Long,
)

data class PortfolioValuation(
    val owner: IpOwner,
    val total: Money,
    val incomeTotal: Money,
    val costTotal: Money,
    val patents: List<PatentValuation>,
    val byField: Map<TechField, Money>,
    /** Royalty income per year implied by licences in force. */
    val licensingIncome: Money,
)

/**
 * Values patents two ways. The income approach asks what royalties the patent protects over the rest of its life,
 * discounted and weighted by the chance it holds up in court; the cost approach asks what it would take to create
 * it again, written down as it ages. The blend is lifted by the citations it has earned and by standing as essential.
 */
class PatentValuer(
    private val state: IpState,
    private val office: PatentOffice,
    val params: IpParams = IpParams(),
) {
    private val vp get() = params.valuation

    /** Chance a patent withstands a validity challenge: new, narrow inventions hold up better. */
    fun validityPpm(p: Patent): Long {
        val risk = (1_000_000L - p.noveltyPpm) / 3 + p.claimedBreadthPpm / 10
        return (1_000_000L - risk).coerceIn(300_000L, 950_000L)
    }

    fun citationPremiumPpm(id: PatentId): Long = (state.forwardCitations(id) * vp.citationBonusPpm).coerceAtMost(vp.citationBonusCapPpm)

    /** Sales of products that use the patented technology in countries where the patent is in force. */
    fun marketSales(p: Patent, run: IpRun): Money {
        val from = Tick((run.tick.value - Calendar.DAYS_PER_YEAR).coerceAtLeast(0L))
        var sum = Money.ZERO
        for (c in p.countriesInForce(run.tick)) {
            for (pr in run.ctx.practicing(p.tech, c, from, run.tick)) sum += pr.usage.sales
        }
        val own = p.owner.companyOrNull()?.let { run.ctx.annualSalesInField(it, p.field) } ?: Money.ZERO
        return Money.max(sum, own)
    }

    fun value(p: Patent, run: IpRun): PatentValuation {
        val remainingDays = office.remainingDays(p, run.tick)
        val years = ((remainingDays + Calendar.DAYS_PER_YEAR - 1) / Calendar.DAYS_PER_YEAR).toInt()
        val breadth = if (p.isPending && p.grantedBreadthPpm() == 0L) p.claimedBreadthPpm else p.grantedBreadthPpm()
        val validity = validityPpm(p)
        val market = marketSales(p, run)
        val margin = p.owner.companyOrNull()?.let { run.ctx.profitMargin(it).ppm } ?: 120_000L
        val rate = Royalty.reasonablePercent(params.licensing, margin, breadth, p.noveltyPpm).ppm
        val income = if (years <= 0) Money.ZERO else incomeApproach(p, market, rate, breadth, years, validity)
        val cost = costApproach(p, remainingDays)
        val blended = Money(mulDiv(income.raw, vp.incomeWeightPpm, 1_000_000L) + mulDiv(cost.raw, 1_000_000L - vp.incomeWeightPpm, 1_000_000L))
        var premium = citationPremiumPpm(p.id)
        if (p.standardEssential) premium += vp.essentialPremiumPpm
        val adjusted = Money(mulDiv(blended.raw, 1_000_000L + premium, 1_000_000L))
        val floor = if (years > 0) Money.units(vp.minValueUnits) else Money.ZERO
        return PatentValuation(p.id, income, cost, Money.max(adjusted, floor), years, validity, rate, market, premium)
    }

    /** Present value of the royalty on the protected sales, growing and discounted, each year weighted by validity and by how much of the life is left. */
    private fun incomeApproach(p: Patent, market: Money, ratePpm: Long, breadthPpm: Long, years: Int, validityPpm: Long): Money {
        var annual = Money(mulDiv(mulDiv(market.raw, ratePpm, 1_000_000L), breadthPpm.coerceAtLeast(100_000L), 1_000_000L))
        var factor = 1_000_000L
        var total = 0L
        var grantChance = 1_000_000L
        if (p.isPending && p.standing() == PatentStanding.PENDING) {
            val reg = office.regimeOf(p.filings.first { it.status == FilingStatus.PENDING }.country)
            grantChance = PatentRules.grantProbability(params.office, reg, p.noveltyPpm, p.claimedBreadthPpm)
        }
        for (y in 1..years) {
            factor = mulDiv(factor, 1_000_000L, 1_000_000L + vp.discountRatePpm)
            annual = Money(mulDiv(annual.raw, 1_000_000L + vp.salesGrowthPpm, 1_000_000L))
            total += mulDiv(annual.raw, factor, 1_000_000L)
        }
        val weighted = mulDiv(mulDiv(total, validityPpm, 1_000_000L), grantChance, 1_000_000L)
        val renewals = renewalCost(p, years)
        return Money((weighted - renewals.raw).coerceAtLeast(0L))
    }

    private fun renewalCost(p: Patent, years: Int): Money {
        var sum = Money.ZERO
        for (f in p.filings) {
            if (!f.status.live) continue
            val reg = office.regimeOf(f.country)
            val paid = f.feeYearsPaid
            for (y in 1..years) sum += PatentRules.maintenanceFee(params.office, reg, paid + y)
        }
        return sum
    }

    /** Replacement cost: research plus fees, written down in step with the life used up. */
    private fun costApproach(p: Patent, remainingDays: Long): Money {
        val longest = p.filings.maxOfOrNull { PatentRules.termTicks(office.regimeOf(it.country), p.pharma) } ?: return Money.ZERO
        if (remainingDays <= 0L) return Money.ZERO
        val replacement = Money(mulDiv(p.researchCost.raw, vp.rdReplacementPpm, 1_000_000L)) + p.totalFees
        return Money(mulDiv(replacement.raw, remainingDays.coerceAtMost(longest), longest))
    }

    fun portfolio(owner: IpOwner, run: IpRun): PortfolioValuation {
        val all = state.patentsOf(owner).filter { it.isLive }.map { value(it, run) }.sortedWith(compareByDescending<PatentValuation> { it.value }.thenBy { it.patent })
        val byField = java.util.TreeMap<TechField, Money>()
        for (v in all) {
            val f = state.patentMap.getValue(v.patent).field
            byField[f] = (byField[f] ?: Money.ZERO) + v.value
        }
        val total = Money.sum(all.map { it.value })
        return PortfolioValuation(
            owner, total, Money.sum(all.map { it.income }), Money.sum(all.map { it.cost }), all, byField, licensingIncome(owner),
        )
    }

    /** Annualised royalties the owner's licences in force are paying, from the most recent period of each. */
    fun licensingIncome(owner: IpOwner): Money {
        var sum = 0L
        for (l in state.licenseMap.values) {
            if (!l.isActive || l.licensor != owner || !l.terms.isRecurring) continue
            sum += mulDiv(l.lastDue.raw, Calendar.DAYS_PER_YEAR.toLong(), params.licensing.periodDays.toLong())
        }
        return Money(sum)
    }

    /** The sum of two sets' values; the fair balancing payment between them is half the difference. */
    fun balancingPayment(valueA: Money, valueB: Money): Money = Money(mulDiv((valueA.raw - valueB.raw), 1, 2))

    /** What a distressed seller gets for a patent. */
    fun fireSalePrice(v: PatentValuation): Money = Money(mulDiv(v.value.raw, params.office.fireSalePpm, 1_000_000L))

    fun remainingShare(p: Patent, tick: Tick): Rate {
        val longest = p.filings.maxOfOrNull { PatentRules.termTicks(office.regimeOf(it.country), p.pharma) } ?: return Rate.ZERO
        return Rate(mulDiv(office.remainingDays(p, tick).coerceAtMost(longest), 1_000_000L, longest))
    }
}
