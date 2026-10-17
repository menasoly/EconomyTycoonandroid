package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.urban.InsightSeverity
import app.many.economytycoon.urban.UrbanReader
import app.many.economytycoon.urban.UrbanService
import app.many.economytycoon.urban.UrbanTestSupport
import app.many.economytycoon.urban.UrbanView
import app.many.economytycoon.urban.ZoneUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanPresenterTest {
    private val T = UrbanTestSupport
    private val money: MoneyText = { "$" + it.wholeUnits() }

    private fun view(svc: UrbanService = T.withCity(), days: Int = 60): UrbanView {
        T.run(svc, 0, days)
        return UrbanReader.view(svc, Tick(days.toLong()))
    }

    @Test fun `liveability is shown with one decimal`() {
        assertEquals("0.0", UrbanPresenter.liveabilityText(0))
        assertEquals("62.5", UrbanPresenter.liveabilityText(625))
        assertEquals("100.0", UrbanPresenter.liveabilityText(1000))
    }

    @Test fun `tones follow the score`() {
        assertEquals(Tone.BAD, UrbanPresenter.liveabilityTone(200))
        assertEquals(Tone.WARN, UrbanPresenter.liveabilityTone(500))
        assertEquals(Tone.GOOD, UrbanPresenter.liveabilityTone(800))
    }

    @Test fun `every severity and zone has a label and tone`() {
        for (s in InsightSeverity.values()) {
            assertTrue(UrbanPresenter.severityLabel(s).isNotBlank())
            UrbanPresenter.severityTone(s)
        }
        assertEquals(Tone.GOOD, UrbanPresenter.zoneTone(ZoneUse.GREEN))
        for (z in ZoneUse.values()) UrbanPresenter.zoneTone(z)
        assertEquals(Tone.BAD, UrbanPresenter.severityTone(InsightSeverity.CRITICAL))
    }

    @Test fun `an empty view says there are no cities`() {
        val m = UrbanPresenter.overview(UrbanReader.view(T.service(), Tick(0)))
        assertTrue(m.cities.isEmpty() && m.tiles.isEmpty())
        assertTrue(m.note.contains("No city"))
    }

    @Test fun `the overview ranks the cities and totals the people`() {
        val svc = T.withCity()
        svc.addCity(CityId(2), T.country, "Second", T.inputs(80_000L, Money.units(60)), Tick(0)).getOrThrow()
        val v = view(svc)
        val m = UrbanPresenter.overview(v)
        assertEquals(2, m.cities.size)
        assertEquals("#1 of 2", m.cities[0].rank)
        assertEquals("#2 of 2", m.cities[1].rank)
        assertEquals(4, m.tiles.size)
        assertEquals("2", m.tiles[0].value)
        assertTrue(m.tiles[2].note != null)
        assertTrue(m.cities.all { it.headline.startsWith("Best:") })
    }

    @Test fun `the city page has tiles, factors, districts and advice`() {
        val v = view()
        val m = UrbanPresenter.city(v, T.city, money)!!
        assertEquals("Testville", m.title)
        assertEquals(6, m.tiles.size)
        assertEquals(8, m.factors.size)
        assertEquals(9, m.districts.size)
        assertTrue(m.insights.isNotEmpty())
        assertTrue(m.districts.all { it.facts.size == 10 && it.name.isNotBlank() })
        assertTrue(m.trend.isNotBlank())
    }

    @Test fun `an unknown city has no page`() {
        assertNull(UrbanPresenter.city(view(), CityId(404), money))
    }

    @Test fun `money is written by the formatter that is passed in`() {
        val m = UrbanPresenter.city(view(), T.city, money)!!
        assertTrue(m.tiles.last().value.startsWith("$"))
        assertTrue(m.districts[0].facts.any { it.value.startsWith("$") })
    }

    @Test fun `ratios print with two decimals`() {
        assertEquals("2.40", UrbanPresenter.ratio(2400))
        assertEquals("0.05", UrbanPresenter.ratio(50))
    }

    @Test fun `badges mark heritage and open petitions`() {
        val svc = T.withCity()
        val d = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL && !it.heritage }
        svc.execute(app.many.economytycoon.urban.UrbanCommand.FilePetition(T.city, d.index, app.many.economytycoon.urban.PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null), Tick(0)).getOrThrow()
        val m = UrbanPresenter.city(view(svc, 1), T.city, money)!!
        assertTrue(m.districts.any { "Heritage" in it.badges })
        assertTrue(m.districts[d.index].badges.contains("Petition open"))
    }

    @Test fun `the trend compares first and last month`() {
        val v = view(days = 200)
        val m = UrbanPresenter.city(v, T.city, money)!!
        assertTrue(m.trend.startsWith("Up") || m.trend.startsWith("Down") || m.trend.startsWith("Steady"))
    }

    @Test fun `presenting is repeatable`() {
        val v = view()
        assertEquals(UrbanPresenter.overview(v), UrbanPresenter.overview(v))
        assertEquals(UrbanPresenter.city(v, T.city, money), UrbanPresenter.city(v, T.city, money))
    }
}
