package app.many.economytycoon.session

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.tourism.PropertyKind
import app.many.economytycoon.tourism.TourismCommand
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.AdvisorPresenter
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.Tips
import app.many.economytycoon.ui.present.TourismPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tourism as the player meets it: through the session, the presenter, the feeds, the route and the reference texts. */
class TourismSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("Sun Hotels", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    @Test fun aNewGameHasDestinationsAndThePresenterReadsThemFromTheSession() {
        val s = start()
        assertTrue(s.tourism.destinations.isNotEmpty())
        repeat(45) { s.simulation.advance() }
        val list = TourismPresenter.destinations(s.tourism, s.settings::money)
        assertEquals(s.tourism.destinations.size, list.rows.size)
        val dest = s.tourism.destinations.first()
        assertTrue(TourismPresenter.overview(s.tourism, dest, s.tick.value, s.settings::money).tiles.isNotEmpty())
        assertTrue(TourismPresenter.properties(s.tourism, dest, s.settings::money).rows.isNotEmpty())
        assertTrue(Invariants.check(s.economy).isEmpty())
    }

    @Test fun theFeedsAdvisorAndAgendaKeepWorkingWithTourismAndSeeAPlayersHotel() {
        val s = start()
        val me = s.selectedCompany!!
        val dest = s.tourism.destinations.first()
        val r = s.simulation.execute(app.many.economytycoon.simulation.Actor.Player(1), Command.Tourism(TourismCommand.BuildProperty(me, dest.id, PropertyKind.HOTEL, 3, listOf(8, 2, 0), "Sun Inn")))
        assertTrue(r.toString(), r is Outcome.Ok)
        repeat(130) { s.step() }
        assertNotNull(s.tourismNews())
        s.tourismInsights().forEach { assertTrue(it.code.startsWith("tourism.")) }
        assertEquals(Route.Tourism, AdvisorPresenter.routeFor("tourism.low-occupancy.3"))
        assertTrue(s.alerts().all { it.code.isNotBlank() })
        assertNotNull(s.agenda())
        assertTrue(s.news.items().isNotEmpty())
        assertTrue(s.tourism.propertiesOf(me).single().isOpen)
    }

    @Test fun theRouteAndReferenceTextsAreWired() {
        assertEquals(Route.Tourism, Route.parse(Route.Tourism.key))
        assertTrue(Glossary.search("occupancy").any { it.id.startsWith("tourism-") })
        assertNotNull(Glossary.byId("tourism-adr"))
        assertNotNull(HelpContent.byId("tourism"))
        assertNotNull(Tips.byId("tourism"))
        assertEquals(Route.Tourism, Tips.byId("tourism")!!.route)
    }

    @Test fun aSavedGameKeepsTourismAndCarriesOn() {
        val s = start()
        repeat(35) { s.simulation.advance() }
        val bytes = app.many.economytycoon.persistence.SimulationSnapshot.save(s.simulation)
        val copy = app.many.economytycoon.persistence.SimulationSnapshot.load(bytes, SimTestSupport.content)
        assertEquals(s.simulation.hash(), copy.hash())
        assertEquals(s.tourism.destinations.size, copy.economy.tourism.destinations.size)
    }
}
