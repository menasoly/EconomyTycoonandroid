package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.simulation.SimTestSupport

/** A hand-built world for the talent tests: regions with plain numbers, no generator involved. */
class FixedTalentWorld : TalentWorld {
    class Region(
        val country: CountryId,
        var youth: Long,
        var working: Long,
        var unemployed: Long,
        var wage: Money,
        var education: Long,
        var happiness: Long,
    )

    val regions = java.util.TreeMap<RegionId, Region>()
    val stability = java.util.TreeMap<CountryId, Long>()

    fun region(
        id: Long, country: Long = 1L, youth: Long = 1_000L, working: Long = 20_000L, unemployed: Long = 1_000L,
        wage: Money = Money.units(90), education: Long = 400_000L, happiness: Long = 600_000L,
    ): FixedTalentWorld {
        regions[RegionId(id)] = Region(CountryId(country), youth, working, unemployed, wage, education, happiness)
        stability.putIfAbsent(CountryId(country), 700_000L)
        return this
    }

    fun r(id: Long): Region = regions.getValue(RegionId(id))

    override fun countries(): List<CountryId> = regions.values.map { it.country }.distinct().sorted()
    override fun regionsOf(country: CountryId): List<RegionId> = regions.filter { it.value.country == country }.keys.toList()
    override fun countryOf(region: RegionId): CountryId = regions.getValue(region).country
    override fun youthCohort(region: RegionId): Long = regions.getValue(region).youth
    override fun workingAge(region: RegionId): Long = regions.getValue(region).working
    override fun unemployed(region: RegionId): Long = regions.getValue(region).unemployed
    override fun averageWage(region: RegionId): Money = regions.getValue(region).wage
    override fun educationPpm(region: RegionId): Long = regions.getValue(region).education
    override fun happinessPpm(region: RegionId): Long = regions.getValue(region).happiness
    override fun stabilityPpm(country: CountryId): Long = stability[country] ?: 700_000L

    companion object {
        fun standard(): FixedTalentWorld = FixedTalentWorld().region(1L, country = 1L).region(2L, country = 1L).region(3L, country = 2L, wage = Money.units(60), education = 300_000L)
    }
}

object TalentTestSupport {
    val taxonomy: SkillTaxonomy by lazy { DefaultTaxonomy.forRoles(SimTestSupport.content.allRoles) }
    fun newState() = TalentState()

    fun key(company: Long = 1L, role: String = "machinist", region: Long = 1L, facility: Long = 0L) =
        CohortKey(CompanyId(company), FacilityId(facility), RoleId(role), RegionId(region))

    fun fact(
        key: CohortKey, headcount: Long, target: Long = headcount, wage: Money = Money.units(95), marketWage: Money = Money.units(95),
        morale: Long = 650_000L, tenure: Long = 0L, load: Long = Ppm.ONE,
    ) = CohortFact(key, headcount, target, wage, marketWage, morale, tenure, load)

    fun inputs(tick: Long, vararg facts: CohortFact, cash: Long = 1_000_000L, companies: List<Long> = listOf(1L, 2L, 3L)) =
        TalentInputs(Tick(tick), facts.toList(), companies.map { CompanyFact(CompanyId(it), Money.units(cash)) })
}

/** The services of the module wired by hand around one state, for tests of a single service. */
class ServiceRig(val world: FixedTalentWorld = FixedTalentWorld.standard()) {
    val state = TalentState()
    val taxonomy = TalentTestSupport.taxonomy
    val markets = SkillMarketService(state, taxonomy)
    val skills = CohortSkillService(state, taxonomy, markets)
    val training = TrainingService(state, taxonomy, skills)
    val education = EducationService(state, taxonomy)
    val succession = SuccessionService(state, taxonomy, 11L)
    val poaching = PoachingService(state, taxonomy, skills, training, succession, 11L)
}

/** A stand-in for the workforce module in scenario tests: head counts, wages and morale that react to the module's effects. */
class MiniWorkforce {
    class Row(var heads: Long, var target: Long, var wage: Money, var market: Money, var morale: Long, var tenure: Long)

    val rows = java.util.TreeMap<CohortKey, Row>()
    val cash = java.util.TreeMap<CompanyId, Money>()

    fun add(key: CohortKey, heads: Long, target: Long = heads, wage: Money = Money.units(95), market: Money = wage, morale: Long = 650_000L, tenure: Long = 360L): MiniWorkforce {
        rows[key] = Row(heads, target, wage, market, morale, tenure); return this
    }

    fun fund(company: Long, units: Long): MiniWorkforce { cash[CompanyId(company)] = Money.units(units); return this }

    fun copy(): MiniWorkforce {
        val c = MiniWorkforce()
        for ((k, r) in rows) c.rows[k] = Row(r.heads, r.target, r.wage, r.market, r.morale, r.tenure)
        c.cash.putAll(cash)
        return c
    }

    fun inputs(tick: Long): TalentInputs = TalentInputs(
        Tick(tick),
        rows.map { (k, r) -> CohortFact(k, r.heads, r.target, r.wage, r.market, r.morale, r.tenure) },
        cash.map { (c, m) -> CompanyFact(c, m) },
    )

    /** Applies what the module asked for, the way the workforce module would. */
    fun apply(effects: List<WorkforceEffect>) {
        for (e in effects) when (e) {
            is WorkforceEffect.Transfer -> {
                val from = rows[e.from]; val to = rows.getOrPut(e.to) { Row(0L, 0L, e.newWage ?: Money.units(95), e.newWage ?: Money.units(95), 650_000L, 0L) }
                if (from != null) {
                    val n = minOf(e.count, from.heads); from.heads -= n; to.heads += n
                    if (e.newWage != null && n > 0L) to.wage = e.newWage
                }
            }
            is WorkforceEffect.Hire -> rows[e.cohort]?.let { it.heads += e.count }
            is WorkforceEffect.RaiseWage -> rows[e.cohort]?.wage = e.newWage
            is WorkforceEffect.Morale -> rows[e.cohort]?.let { it.morale = (it.morale + e.deltaPpm).coerceIn(0L, Ppm.ONE) }
            is WorkforceEffect.Depart -> rows[e.cohort]?.let { it.heads = (it.heads - e.count).coerceAtLeast(0L) }
            is WorkforceEffect.Emigrate -> rows[e.cohort]?.let { it.heads = (it.heads - e.count).coerceAtLeast(0L) }
            is WorkforceEffect.TrainingShare -> Unit
        }
    }

    /** One day passes: tenure grows and a firm below target recruits a little. */
    fun day() {
        for (r in rows.values) { r.tenure += 1L; if (r.heads < r.target && r.heads > 0L) r.heads += 1L }
    }
}
