package app.many.economytycoon.session

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.Tips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The grids as the player meets them: through the session, the route and the reference texts. */
class EnergyGridSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("Volt Works", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    @Test fun aNewGameHasGridsAndTheSessionReadsThem() {
        val s = start()
        val g = s.energyGrids.grids.first()
        assertNotNull(s.gridFacts(g.id))
        repeat(40) { s.simulation.advance() }
        assertTrue(Invariants.check(s.economy).isEmpty())
        assertNotNull(s.gridNews())
    }

    @Test fun theRouteAndReferenceTextsAreWired() {
        assertEquals(Route.Electricity, Route.parse(Route.Electricity.key))
        assertTrue(Glossary.search("grid").any { it.id.startsWith("grid-") })
        assertNotNull(HelpContent.byId("electricity"))
        assertNotNull(Tips.byId("electricity"))
    }
}
