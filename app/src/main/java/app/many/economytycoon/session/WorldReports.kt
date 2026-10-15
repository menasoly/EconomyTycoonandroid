package app.many.economytycoon.session

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.macro.CyclePhase
import app.many.economytycoon.macro.MacroSnapshot
import app.many.economytycoon.simulation.Economy

data class CountryDashboard(
    val id: CountryId,
    val name: String,
    val population: Long,
    val unemploymentPpm: Long,
    val inflationPpm: Long,
    val policyRatePpm: Long,
    val phase: CyclePhase?,
    val gdp: Money?,
    val growthPpm: Long?,
    val confidencePpm: Long?,
    val corporateTaxPpm: Long,
    val salesTaxPpm: Long,
    val payrollTaxPpm: Long,
    val minimumWage: Money,
    val treasury: Money,
    val stabilityPpm: Long,
)

data class RegionRow(
    val id: RegionId,
    val name: String,
    val country: String,
    val population: Long,
    val employmentPpm: Long,
    val averageWage: Money,
    val happinessPpm: Long,
    val powerLoadPpm: Long,
    val facilities: Int,
    val cities: Int,
)

data class CityRow(
    val id: CityId,
    val name: String,
    val population: Long,
    val children: Long,
    val working: Long,
    val seniors: Long,
    val unemploymentPpm: Long,
    val averageWage: Money,
    val educationPpm: Long,
    val happinessPpm: Long,
    val port: Boolean,
    val airport: Boolean,
    val capital: Boolean,
)

data class MacroPoint(val tick: Long, val gdp: Money, val growthPpm: Long?, val inflationPpm: Long, val unemploymentPpm: Long, val policyRatePpm: Long)

/** Countries, regions and cities as the geography screens show them. */
class WorldReports(private val e: Economy) {

    fun countries(): List<CountryDashboard> = e.world.countries().sortedBy { it.id }.map { dashboard(it.id) }

    fun dashboard(id: CountryId): CountryDashboard {
        val c = e.world.country(id)
        val snap: MacroSnapshot? = e.macro.latest(id)
        val cities = e.world.citiesOf(id)
        val labor = cities.sumOf { e.world.population(it.id).laborForce }
        val unemployed = cities.sumOf { e.world.population(it.id).unemployed }
        return CountryDashboard(
            id = id, name = c.name, population = e.world.populationOf(id),
            unemploymentPpm = if (labor == 0L) 0L else unemployed * 1_000_000L / labor,
            inflationPpm = snap?.inflationPpm ?: 0L, policyRatePpm = e.macro.policyRatePpm(id),
            phase = snap?.phase, gdp = snap?.gdp, growthPpm = snap?.growthPpm, confidencePpm = snap?.confidencePpm,
            corporateTaxPpm = c.corporateTaxPpm, salesTaxPpm = c.salesTaxPpm, payrollTaxPpm = c.payrollTaxPpm,
            minimumWage = Money(c.minimumWagePerTick * Money.SCALE),
            treasury = e.finance.ledger.cash(e.governmentOf(id)), stabilityPpm = c.stabilityPpm,
        )
    }

    fun macroHistory(id: CountryId): List<MacroPoint> =
        e.macro.history(id).map { MacroPoint(it.tick.value, it.gdp, it.growthPpm, it.inflationPpm, it.unemploymentPpm, it.policyRatePpm) }

    fun regions(country: CountryId? = null): List<RegionRow> =
        e.world.regions().filter { country == null || it.country == country }.sortedBy { it.id }.map { r ->
            val cities = e.world.citiesIn(r.id)
            val pops = cities.map { e.world.population(it.id) }
            val labor = pops.sumOf { it.laborForce }
            val employed = pops.sumOf { it.employed }
            val total = pops.sumOf { it.total }
            val wageHeads = pops.sumOf { it.employed }
            val wageSum = pops.sumOf { it.averageWage.raw * it.employed }
            RegionRow(
                id = r.id, name = r.name, country = e.world.country(r.country).name, population = total,
                employmentPpm = if (labor == 0L) 0L else (employed * 1_000_000L / labor).coerceAtMost(1_000_000L),
                averageWage = if (wageHeads == 0L) Money.ZERO else Money(wageSum / wageHeads),
                happinessPpm = if (total == 0L) 0L else pops.sumOf { it.happinessPpm * it.total } / total,
                powerLoadPpm = e.grid.loadPpm(r.id),
                facilities = e.production.facilities().count { it.region == r.id && it.status != app.many.economytycoon.production.FacilityStatus.DEMOLISHED },
                cities = cities.size,
            )
        }

    fun cities(region: RegionId): List<CityRow> =
        e.world.citiesIn(region).sortedBy { it.id }.map { c ->
            val p = e.world.population(c.id)
            CityRow(
                c.id, c.name, p.total, p.children, p.working, p.seniors, p.unemploymentPpm, p.averageWage,
                p.educationPpm, p.happinessPpm, c.hasPort, c.hasAirport, c.isCapital,
            )
        }

    fun worldPopulation(): Long = e.world.totalPopulation()
}
