package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.macro.CyclePhase
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.visualization.DataPoint
import app.many.economytycoon.visualization.LineSeries

data class CountryItem(val id: CountryId, val name: String, val phase: String, val phaseTone: Tone, val facts: List<Fact>)

data class RegionItem(val id: RegionId, val name: String, val country: String, val population: String, val employment: String, val employmentTone: Tone, val wage: String, val happiness: Double, val grid: Double, val facilities: String)

data class WorldModel(val population: String, val countries: List<CountryItem>, val regions: List<RegionItem>)

data class CityItem(val name: String, val population: String, val unemployment: String, val wage: String, val happiness: Double, val tags: List<String>)

data class RegionDetailModel(val name: String, val facts: List<Fact>, val cities: List<CityItem>, val ageBands: List<Pair<String, Double>>)

data class MacroSeries(val gdp: LineSeries, val inflation: LineSeries, val unemployment: LineSeries, val policyRate: LineSeries)

object WorldPresenter {
    fun phaseLabel(p: CyclePhase?): Pair<String, Tone> = when (p) {
        CyclePhase.EXPANSION -> "Expansion" to Tone.GOOD
        CyclePhase.PEAK -> "Peak" to Tone.WARN
        CyclePhase.CONTRACTION -> "Contraction" to Tone.BAD
        CyclePhase.TROUGH -> "Trough" to Tone.WARN
        null -> "Not yet measured" to Tone.MUTED
    }

    fun build(session: GameSession): WorldModel {
        val money = session.settings::money
        val reports = session.reports.world
        val countries = reports.countries().map { c ->
            val (label, tone) = phaseLabel(c.phase)
            CountryItem(
                c.id, c.name, label, tone, listOf(
                    Fact("Population", Formatting.grouped(c.population)),
                    Fact("GDP", c.gdp?.let { money(it) } ?: "—"),
                    Fact("Growth", c.growthPpm?.let { Formatting.signedPercentPpm(it) } ?: "—", c.growthPpm?.let { Tones.bySign(it) } ?: Tone.NEUTRAL),
                    Fact("Inflation", Formatting.percentPpm(c.inflationPpm), if (c.inflationPpm > 60_000L) Tone.WARN else Tone.NEUTRAL),
                    Fact("Unemployment", Formatting.percentPpm(c.unemploymentPpm), if (c.unemploymentPpm > 100_000L) Tone.WARN else Tone.NEUTRAL),
                    Fact("Policy rate", Formatting.percentPpm(c.policyRatePpm)),
                    Fact("Corporate tax", Formatting.percentPpm(c.corporateTaxPpm)),
                    Fact("Sales tax", Formatting.percentPpm(c.salesTaxPpm)),
                    Fact("Payroll tax", Formatting.percentPpm(c.payrollTaxPpm)),
                    Fact("Minimum wage", money(c.minimumWage) + " a day"),
                    Fact("Treasury", money(c.treasury), if (c.treasury.raw < 0) Tone.BAD else Tone.NEUTRAL),
                    Fact("Stability", Formatting.percentPpm(c.stabilityPpm), Tones.health(c.stabilityPpm)),
                ),
            )
        }
        val regions = reports.regions().map { r ->
            RegionItem(
                r.id, r.name, r.country, Formatting.grouped(r.population), Formatting.percentPpm(r.employmentPpm), Tones.health(r.employmentPpm), money(r.averageWage),
                r.happinessPpm / 1_000_000.0, r.powerLoadPpm / 1_000_000.0, when (r.facilities) { 0 -> "none of yours"; 1 -> "1 facility"; else -> "${r.facilities} facilities" },
            )
        }
        return WorldModel(Formatting.grouped(reports.worldPopulation()), countries, regions)
    }

    fun region(session: GameSession, id: RegionId): RegionDetailModel? {
        val reports = session.reports.world
        val row = reports.regions().firstOrNull { it.id == id } ?: return null
        val money = session.settings::money
        val cities = reports.cities(id)
        val facts = listOf(
            Fact("Country", row.country), Fact("Population", Formatting.grouped(row.population)), Fact("Employment", Formatting.percentPpm(row.employmentPpm), Tones.health(row.employmentPpm)),
            Fact("Average wage", money(row.averageWage)), Fact("Happiness", Formatting.percentPpm(row.happinessPpm), Tones.health(row.happinessPpm)),
            Fact("Power grid load", Formatting.percentPpm(row.powerLoadPpm), if (row.powerLoadPpm > 900_000L) Tone.WARN else Tone.NEUTRAL),
        )
        val items = cities.sortedByDescending { it.population }.map { c ->
            val tags = ArrayList<String>()
            if (c.capital) tags += "Capital"
            if (c.port) tags += "Port"
            if (c.airport) tags += "Airport"
            CityItem(c.name, Formatting.grouped(c.population), Formatting.percentPpm(c.unemploymentPpm), money(c.averageWage), c.happinessPpm / 1_000_000.0, tags)
        }
        val ages = listOf("Children" to cities.sumOf { it.children }.toDouble(), "Working age" to cities.sumOf { it.working }.toDouble(), "Seniors" to cities.sumOf { it.seniors }.toDouble())
        return RegionDetailModel(row.name, facts, items, ages)
    }

    fun macro(session: GameSession, country: CountryId): MacroSeries {
        val h = session.reports.world.macroHistory(country)
        fun series(name: String, slot: Int, pick: (app.many.economytycoon.session.MacroPoint) -> Double?) =
            LineSeries(name, h.mapNotNull { p -> pick(p)?.let { DataPoint(p.tick.toDouble(), it) } }, slot)
        return MacroSeries(
            series("GDP", 0) { it.gdp.raw / 10_000.0 }, series("Inflation %", 1) { it.inflationPpm / 10_000.0 },
            series("Unemployment %", 2) { it.unemploymentPpm / 10_000.0 }, series("Policy rate %", 3) { it.policyRatePpm / 10_000.0 },
        )
    }
}
