package app.many.economytycoon.net

import app.many.economytycoon.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyExpelTest {
    private fun lobby(): Lobby {
        val l = Lobby(LobbyConfig(maxPlayers = 3))
        l.join(PlayerId(1), "Host", 0)
        l.join(PlayerId(2), "Guest", 0)
        return l
    }

    @Test fun expellingBeforeTheStartRemovesTheMember() {
        val l = lobby()
        val r = l.expel(PlayerId(2), ban = false)
        assertTrue(r is Outcome.Ok)
        assertNull(l.member(PlayerId(2)))
        assertEquals(false, l.isBanned(PlayerId(2)))
    }

    @Test fun aBanKeepsThePlayerOut() {
        val l = lobby()
        l.expel(PlayerId(2), ban = true)
        assertTrue(l.isBanned(PlayerId(2)))
        assertTrue(l.join(PlayerId(2), "Guest", 5) is Outcome.Rejected)
    }

    @Test fun expellingTheHostHandsOverTheHost() {
        val l = lobby()
        l.expel(PlayerId(1), ban = false)
        assertEquals(PlayerId(2), l.hostId)
    }

    @Test fun duringAGameAPlayerKeepsTheSeatButIsGone() {
        val l = lobby()
        l.setReady(PlayerId(1), true); l.setReady(PlayerId(2), true)
        l.start(PlayerId(1))
        l.expel(PlayerId(2), ban = false)
        assertEquals(Presence.GONE, l.member(PlayerId(2))?.presence)
        assertEquals(Role.PLAYER, l.member(PlayerId(2))?.role)
    }

    @Test fun expellingAStrangerIsRefused() {
        assertTrue(lobby().expel(PlayerId(9), ban = true) is Outcome.Rejected)
    }

    @Test fun expellingBumpsTheVersionSoClientsSeeTheChange() {
        val l = lobby()
        val v = l.view().version
        l.expel(PlayerId(2), ban = false)
        assertTrue(l.view().version > v)
    }
}
