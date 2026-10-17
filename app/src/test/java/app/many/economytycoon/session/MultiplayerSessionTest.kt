package app.many.economytycoon.session

import app.many.economytycoon.net.ClientState
import app.many.economytycoon.net.LobbyConfig
import app.many.economytycoon.net.LocalTable
import app.many.economytycoon.net.RealSimulationPort
import app.many.economytycoon.net.SeededTokenSource
import app.many.economytycoon.net.SessionId
import app.many.economytycoon.net.ShardConfig
import app.many.economytycoon.net.SimulationDriver
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.NetLobbyPresenter
import app.many.economytycoon.ui.present.SessionNetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The game on screen as one seat at a multiplayer table, and the route and texts that lead to it. */
class MultiplayerSessionTest {
    @Test fun aSessionPlaysThroughTheHostsCommitsAndKeepsItsOwnBookkeeping() {
        val session = GameSession(SimTestSupport.sim(seed = 7, ai = 2), playerId = 1L, settings = GameSettings(autosave = false))
        session.externallyClocked = true
        val driver = object : SimulationDriver {
            override fun step() = session.step()
            override fun replace(next: Simulation) = false
        }
        val mine = RealSimulationPort(session.simulation, SimTestSupport.content, driver)
        val authority = RealSimulationPort(SimTestSupport.sim(seed = 7, ai = 2), SimTestSupport.content)
        val other = RealSimulationPort(SimTestSupport.sim(seed = 7, ai = 2), SimTestSupport.content)
        val config = ShardConfig(session = SessionId(9), seed = 7, hashInterval = 5, lobby = LobbyConfig(maxPlayers = 2), snapshotMinIntervalMs = 100L)
        val table = LocalTable(config, authority, SeededTokenSource(3), tickMs = 100L)
        val me = table.join("Me", mine)
        val you = table.join("You", other)
        val node = table.node()
        var now = 0L
        fun until(ms: Long, c: () -> Boolean) { var t = 0L; while (t < ms && !c()) { now += 20; node.pump(now); t += 20 } }

        me.connect(now); you.connect(now)
        until(20_000L) { me.lobby() != null && you.lobby() != null }
        me.ready(now); you.ready(now)
        until(20_000L) { table.shard.lobby.players().all { it.ready } }
        (if (me.player == table.shard.lobby.hostId) me else you).startGame(now)
        until(20_000L) { me.state == ClientState.RUNNING && you.state == ClientState.RUNNING }
        until(8_000L) { authority.tick >= 40L }
        until(10_000L) { mine.tick == authority.tick && mine.stateHash().contentEquals(authority.stateHash()) }

        assertTrue(authority.tick >= 40L)
        assertEquals(authority.simulation.hash(), session.simulation.hash())
        assertNotNull(session.lastTickSummary)
        assertEquals(0, session.advanceBy(10_000L))
        assertEquals(null, session.save("manual", "x"))

        // the screens read the same table
        val source = SessionNetSource(me, table.shard, config.lobby) { now }
        assertTrue(source.players().rows.size >= 2)
        assertNotNull(NetLobbyPresenter.build(me.lobby(), me.player, config.lobby))
    }

    @Test fun lookingAtGoalsAndResearchChangesNothingInTheGame() {
        val sim = SimTestSupport.sim(seed = 7, ai = 0)
        val session = GameSession(sim, playerId = 1L, settings = GameSettings(autosave = false))
        val port = RealSimulationPort(sim, SimTestSupport.content)
        val seat = app.many.economytycoon.net.Seating.seat(port, listOf(app.many.economytycoon.net.SeatAssignment(0, app.many.economytycoon.net.PlayerId(1), "Alpha Works"))).single()
        val company = app.many.economytycoon.core.CompanyId(seat.company)
        val before = sim.hash()
        session.reports.people.research(company)
        session.reports.people.techTree(company)
        session.objectives.newlyCompleted(company)
        assertEquals(before, sim.hash())
    }

    @Test fun aSingleplayerSessionKeepsItsOwnClock() {
        val session = GameSession(SimTestSupport.sim(seed = 7, ai = 0), playerId = 1L, settings = GameSettings(autosave = false))
        assertFalse(session.externallyClocked)
        assertTrue(session.advanceBy(5_000L) > 0)
    }

    @Test fun theRouteAndReferenceTextsAreWired() {
        assertEquals(Route.Multiplayer, Route.parse(Route.Multiplayer.key))
        assertEquals(Route.Multiplayer, Route.parse("multiplayer"))
        assertEquals(app.many.economytycoon.ui.nav.Tab.MORE, Route.Multiplayer.tab)
        assertEquals(setOf("host", "lockstep", "command-delay", "desync", "escrow", "season", "spectator", "resume", "link-quality"), Glossary.inGroup(Glossary.TOGETHER).map { it.id }.toSet())
        assertNotNull(HelpContent.byId("multiplayer"))
        assertTrue(HelpContent.search("escrow").any { it.id == "multiplayer" })
    }
}
