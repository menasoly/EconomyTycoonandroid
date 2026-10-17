package app.many.economytycoon.ui.present

import app.many.economytycoon.net.LobbyAction
import app.many.economytycoon.net.LobbyConfig
import app.many.economytycoon.net.LobbyPhase
import app.many.economytycoon.net.LobbyView
import app.many.economytycoon.net.MemberInfo
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.Presence
import app.many.economytycoon.net.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetLobbyPresenterTest {
    private fun member(id: Long, name: String, ready: Boolean = false, role: Role = Role.PLAYER, presence: Presence = Presence.CONNECTED) =
        MemberInfo(PlayerId(id), name, role, if (role == Role.PLAYER) (id - 1).toInt() else -1, ready, presence)

    private fun view(phase: LobbyPhase = LobbyPhase.OPEN, vararg m: MemberInfo) = LobbyView(3, phase, PlayerId(1), m.toList())

    private fun action(model: NetLobbyModel, id: String) = model.actions.firstOrNull { it.id == id }

    @Test fun beforeTheLobbyArrivesOnlyLeavingIsOffered() {
        val m = NetLobbyPresenter.build(null, PlayerId(1))
        assertEquals("Connecting", m.phase)
        assertEquals(listOf("leave"), m.actions.map { it.id })
        assertEquals(NetLobbyPresenter.WAITING, m.note)
    }

    @Test fun tilesCountPlayersReadyAndWatchers() {
        val m = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada", true), member(2, "Bob"), member(3, "Cy", role = Role.SPECTATOR)), PlayerId(1))
        assertEquals(listOf("2 / 4", "1", "1"), m.tiles.map { it.value })
    }

    @Test fun rowsShowSeatsStatusAndWhoIsHost() {
        val m = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada", true), member(2, "Bob"), member(3, "Cy", role = Role.SPECTATOR), member(4, "Di", presence = Presence.GRACE)), PlayerId(2))
        assertEquals(listOf("Ready", "Not ready", "Watching", "Reconnecting"), m.members.map { it.status })
        assertEquals(listOf("Seat 1", "Seat 2", "No seat", "Seat 4"), m.members.map { it.seat })
        assertTrue(m.members[0].host)
        assertTrue(m.members[1].me)
        assertEquals(Tone.WARN, m.members[3].tone)
    }

    @Test fun onlyTheHostSeesKickAndNotForThemselves() {
        val asHost = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada"), member(2, "Bob")), PlayerId(1))
        assertEquals(listOf(false, true), asHost.members.map { it.canKick })
        val asGuest = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada"), member(2, "Bob")), PlayerId(2))
        assertEquals(listOf(false, false), asGuest.members.map { it.canKick })
    }

    @Test fun readyToggleFollowsTheCurrentState() {
        val notReady = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada"), member(2, "Bob")), PlayerId(2))
        assertTrue(action(notReady, "ready")?.enabled == true)
        assertNull(action(notReady, "unready"))
        val ready = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada"), member(2, "Bob", true)), PlayerId(2))
        assertTrue(action(ready, "unready")?.enabled == true)
    }

    @Test fun startNeedsTheHostAndEveryoneReady() {
        val waiting = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada", true), member(2, "Bob")), PlayerId(1))
        assertFalse(action(waiting, "start")?.enabled == true)
        assertEquals("Waiting for 1 player to be ready.", action(waiting, "start")?.hint)
        val all = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada", true), member(2, "Bob", true)), PlayerId(1))
        assertTrue(action(all, "start")?.enabled == true)
        val guest = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada", true), member(2, "Bob", true)), PlayerId(2))
        assertNull(action(guest, "start"))
    }

    @Test fun startDoesNotNeedReadinessWhenTheConfigSaysSo() {
        val config = LobbyConfig(requireAllReady = false)
        val m = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada"), member(2, "Bob")), PlayerId(1), config)
        assertTrue(action(m, "start")?.enabled == true)
    }

    @Test fun nothingCanBeChangedOnceTheGameIsRunning() {
        val m = NetLobbyPresenter.build(view(LobbyPhase.RUNNING, member(1, "Ada", true), member(2, "Bob", true)), PlayerId(1))
        assertEquals("Game in progress", m.phase)
        assertFalse(action(m, "start")?.enabled == true)
        assertFalse(action(m, "ready")?.enabled == true || action(m, "unready")?.enabled == true)
        assertNull(action(m, "spectate"))
        assertTrue(action(m, "leave")?.enabled == true)
    }

    @Test fun aSpectatorCanTakeASeatOnlyWhenOneIsFree() {
        val free = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada"), member(5, "Eve", role = Role.SPECTATOR)), PlayerId(5), LobbyConfig(maxPlayers = 2))
        assertTrue(action(free, "play")?.enabled == true)
        val full = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada"), member(2, "Bob"), member(5, "Eve", role = Role.SPECTATOR)), PlayerId(5), LobbyConfig(maxPlayers = 2))
        assertFalse(action(full, "play")?.enabled == true)
        assertEquals("Every seat is taken.", action(full, "play")?.hint)
    }

    @Test fun spectatingCanBeSwitchedOffByConfig() {
        val m = NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada")), PlayerId(1), LobbyConfig(allowSpectators = false))
        assertNull(action(m, "spectate"))
    }

    @Test fun notesExplainTheSituation() {
        assertEquals("The host closed this session.", NetLobbyPresenter.build(view(LobbyPhase.CLOSED, member(1, "Ada")), PlayerId(1)).note)
        assertEquals("You are not part of this session.", NetLobbyPresenter.build(view(LobbyPhase.OPEN, member(1, "Ada")), PlayerId(9)).note)
        assertTrue(NetLobbyPresenter.build(view(LobbyPhase.RUNNING, member(1, "Ada")), PlayerId(1)).note.contains("only watch"))
    }

    @Test fun everyActionMapsToARequest() {
        val ids = listOf("ready", "unready", "start", "spectate", "play", "leave")
        assertEquals(listOf(LobbyAction.READY, LobbyAction.UNREADY, LobbyAction.START, LobbyAction.SPECTATE, LobbyAction.PLAY, LobbyAction.LEAVE), ids.map { NetLobbyPresenter.request(it) })
        assertNull(NetLobbyPresenter.request("dance"))
    }

    @Test fun peopleWhoLeftDoNotCountAsPlayers() {
        val m = NetLobbyPresenter.build(view(LobbyPhase.RUNNING, member(1, "Ada"), member(2, "Bob", presence = Presence.GONE)), PlayerId(1))
        assertEquals("1 / 4", m.tiles[0].value)
        assertEquals("Left", m.members[1].status)
        assertFalse(m.members[1].canKick)
    }
}
