package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.DistressState
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.ip.IpContext
import app.many.economytycoon.ip.IpOwner
import app.many.economytycoon.ip.IpOwnerKind
import app.many.economytycoon.ip.Practice
import app.many.economytycoon.ip.TechField
import app.many.economytycoon.ip.TechFields
import app.many.economytycoon.ip.UsageReport

/**
 * What the patent module may ask of the running economy: cash and standing from the ledger, research from the research
 * engine, and what companies sold from the sales log. Nothing here changes anything.
 */
class EconomyIpContext(private val e: Economy) : IpContext {

    /** For each good, the technologies its recipes need and the industry family each recipe belongs to. */
    private val techsByGood: Map<GoodId, List<Pair<TechId, IndustryFamily>>> by lazy {
        val out = java.util.TreeMap<GoodId, MutableSet<Pair<TechId, IndustryFamily>>>()
        for (r in e.content.allRecipes) {
            val tech = r.requiredTech ?: continue
            val family = e.content.industry(r.industry).family
            for (o in r.outputs) out.getOrPut(o.good) { java.util.LinkedHashSet() }.add(tech to family)
        }
        out.mapValues { (_, v) -> v.toList() }
    }

    private val fieldOfTech = HashMap<TechId, TechField?>()

    private fun fieldOf(tech: TechId): TechField? = fieldOfTech.getOrPut(tech) { e.content.technologyOrNull(tech)?.let { TechFields.of(it) } }

    /** Records one sale in the log under every technology the company used to make the good. */
    fun recordSale(tick: Tick, company: CompanyId, good: GoodId, country: CountryId, units: Quantity, sales: Money) {
        val techs = techsByGood[good] ?: return
        for ((tech, family) in techs) {
            if (e.research.isUnlocked(company, tech)) e.ipSales.record(tick, company, tech, country, family, units.raw, sales.raw)
        }
    }

    // ---- standing --------------------------------------------------------------------------------

    override fun cash(owner: IpOwner): Money = when (owner.kind) {
        IpOwnerKind.COMPANY -> e.finance.ledger.cash(LedgerOwner.company(CompanyId(owner.id))).coerceAtLeast(Money.ZERO)
        IpOwnerKind.NPE -> Money.ZERO
    }

    override fun isActive(company: CompanyId): Boolean = e.companies.company(company)?.status == app.many.economytycoon.companies.CompanyStatus.ACTIVE

    override fun aiCompanies(): List<CompanyId> =
        e.companies.all().filter { it.kind == CompanyKind.AI && it.status == app.many.economytycoon.companies.CompanyStatus.ACTIVE }.map { it.id }.sorted()

    override fun homeCountry(company: CompanyId): CountryId? = e.companies.company(company)?.country

    override fun isDistressed(company: CompanyId): Boolean {
        val state = e.finance.company(LedgerOwner.company(company))?.distress?.state ?: return false
        return state == DistressState.DISTRESSED || state == DistressState.INSOLVENT || state == DistressState.LIQUIDATING
    }

    override fun corporateTaxRate(country: CountryId): Rate = e.finance.taxPolicy(e.governmentOf(country)).corporateRate

    override fun profitMargin(company: CompanyId): Rate {
        val now = e.tick
        val from = Tick((now.value - MARGIN_DAYS).coerceAtLeast(0L))
        val income = e.finance.statements.income(LedgerOwner.company(company), from, now)
        val revenue = income.revenue.raw
        if (revenue <= 0L) return Rate.percent(12)
        val margin = mulDiv(income.ebitda.raw, Rate.ONE_PPM, revenue)
        return Rate(margin.coerceIn(MIN_MARGIN_PPM, MAX_MARGIN_PPM))
    }

    override fun qualifyingResearchSpend(company: CompanyId, from: Tick, to: Tick): Money =
        e.finance.ledger.activity(AccountRef(LedgerOwner.company(company), Chart.RESEARCH_EXPENSE), from, to).coerceAtLeast(Money.ZERO)

    // ---- research --------------------------------------------------------------------------------

    override fun knownTechs(company: CompanyId): List<TechId> = e.research.peek(company)?.unlockedTechs().orEmpty()

    override fun independentHolders(tech: TechId, exclude: CompanyId): List<CompanyId> =
        e.companies.active().filter { it.id != exclude && e.research.isUnlocked(it.id, tech) }.map { it.id }.sorted()

    // ---- what companies make and sell ---------------------------------------------------------------

    private fun runsTech(facility: app.many.economytycoon.production.Facility, tech: TechId): Boolean =
        facility.lines.any { l -> l.recipe?.let { e.content.recipeOrNull(it)?.requiredTech } == tech }

    override fun knowers(company: CompanyId, tech: TechId): Int {
        var n = 0L
        for (f in e.production.facilitiesOf(company)) {
            if (!runsTech(f, tech)) continue
            for (c in e.workforce.cohortsAt(f.id)) n += c.headcount
        }
        return n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun isWorkedLocally(owner: CompanyId, tech: TechId, country: CountryId): Boolean =
        e.production.facilitiesOf(owner).any { runsTech(it, tech) && e.world.region(it.region).country == country }

    override fun exposure(company: CompanyId, tech: TechId): Rate {
        val now = e.tick
        val from = Tick((now.value - EXPOSURE_DAYS).coerceAtLeast(0L))
        var withTech = 0L
        e.ipSales.between(from, now) { k, a -> if (k.company == company.value && k.tech == tech.key) withTech += a.sales }
        val revenue = e.finance.statements.income(LedgerOwner.company(company), from, now).revenue.raw
        if (revenue <= 0L || withTech <= 0L) return Rate.ZERO
        return Rate(mulDiv(withTech, Rate.ONE_PPM, revenue).coerceIn(0L, Rate.ONE_PPM))
    }

    override fun usage(company: CompanyId, tech: TechId, industries: List<IndustryFamily>, countries: List<CountryId>, from: Tick, to: Tick): UsageReport {
        var units = 0L
        var sales = 0L
        val allowedFamilies = industries.mapTo(HashSet()) { it.ordinal }
        val allowedCountries = countries.mapTo(HashSet()) { it.value }
        e.ipSales.between(from, to) { k, a ->
            if (k.company == company.value && k.tech == tech.key &&
                (allowedCountries.isEmpty() || k.country in allowedCountries) && (allowedFamilies.isEmpty() || k.family in allowedFamilies)
            ) { units += a.units; sales += a.sales }
        }
        return UsageReport(Quantity(units), Money(sales))
    }

    override fun practicing(tech: TechId, country: CountryId, from: Tick, to: Tick): List<Practice> {
        val byCompany = java.util.TreeMap<Long, LongArray>()
        e.ipSales.between(from, to) { k, a ->
            if (k.tech == tech.key && k.country == country.value) {
                val sum = byCompany.getOrPut(k.company) { LongArray(2) }
                sum[0] += a.units; sum[1] += a.sales
            }
        }
        return byCompany.map { (c, s) -> Practice(CompanyId(c), UsageReport(Quantity(s[0]), Money(s[1]))) }.filter { !it.usage.isEmpty }
    }

    override fun annualSalesInField(company: CompanyId, field: TechField): Money {
        val now = e.tick
        var sales = 0L
        e.ipSales.between(Tick((now.value - Calendar.DAYS_PER_YEAR).coerceAtLeast(0L)), now) { k, a ->
            if (k.company == company.value && fieldOf(TechId(k.tech)) == field) sales += a.sales
        }
        return Money(sales)
    }

    override fun marketShare(company: CompanyId, field: TechField): Rate {
        val now = e.tick
        var mine = 0L
        var all = 0L
        e.ipSales.between(Tick((now.value - SHARE_DAYS).coerceAtLeast(0L)), now) { k, a ->
            if (fieldOf(TechId(k.tech)) == field) { all += a.sales; if (k.company == company.value) mine += a.sales }
        }
        return if (all <= 0L) Rate.ZERO else Rate(mulDiv(mine, Rate.ONE_PPM, all).coerceIn(0L, Rate.ONE_PPM))
    }

    private companion object {
        const val MARGIN_DAYS = 90L
        const val EXPOSURE_DAYS = 90L
        const val SHARE_DAYS = 90L
        const val MIN_MARGIN_PPM = 10_000L
        const val MAX_MARGIN_PPM = 600_000L
    }
}
