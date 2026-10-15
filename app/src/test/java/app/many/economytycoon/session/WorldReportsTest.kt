package app.many.economytycoon.session

import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldReportsTest {
    private val sim = SimTestSupport.sim(51L, 3).also { it.advance(65) }
    private val reports = WorldReports(sim.economy)

    @Test fun everyCountryHasADashboardWithMacroNumbers() {
        val countries = reports.countries()
        assertEquals(sim.economy.world.countries().size, countries.size)
        for (c in countries) {
            assertTrue(c.population > 0)
            assertTrue(c.unemploymentPpm in 0..1_000_000)
            assertTrue(c.gdp != null)
            assertTrue(c.minimumWage.raw > 0)
        }
    }

    @Test fun regionsAddUpToTheCountry() {
        val country = sim.economy.world.countries().first()
        val rows = reports.regions(country.id)
        assertEquals(sim.economy.world.regionsOf(country.id).size, rows.size)
        assertEquals(sim.economy.world.populationOf(country.id), rows.sumOf { it.population })
        assertTrue(rows.all { it.employmentPpm in 0..1_000_000 && it.happinessPpm in 0..1_000_000 })
    }

    @Test fun citiesAddUpToTheirRegion() {
        val region = reports.regions().first()
        val cities = reports.cities(region.id)
        assertEquals(region.cities, cities.size)
        assertEquals(region.population, cities.sumOf { it.population })
        for (c in cities) assertEquals(c.population, c.children + c.working + c.seniors)
    }

    @Test fun macroHistoryGrowsEachReviewPeriod() {
        val country = sim.economy.world.countries().first().id
        val history = reports.macroHistory(country)
        assertTrue(history.size >= 2)
        assertEquals(history.map { it.tick }.sorted(), history.map { it.tick })
    }

    @Test fun worldPopulationMatchesTheSumOfCountries() {
        assertEquals(reports.countries().sumOf { it.population }, reports.worldPopulation())
    }
}
