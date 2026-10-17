package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ClientState
import app.many.economytycoon.net.NetRig
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.ToySimulation
import app.many.economytycoon.net.TradeAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetScreenSourceTest {
    private fun source(rig: NetRig, i: Int, host: Boolean) =
        SessionNetSource(rig.client(i), if (host) rig.shard else null, rig.config.lobby) { rig.now }

    @Test fun theLobbyScreenFollowsTheRealLobby() {
        val rig = NetRig(income = false)
        rig.clients.forEach { it.connect(rig.now) }
        rig.waitFor(10_000L) { rig.clients.all { it.lobby() != null } }
        val src = source(rig, 0, host = false)
        assertEquals("Waiting for players", src.lobby().phase)
        assertEquals(2, src.lobby().members.size)
        assertNull(src.act("ready"))
        rig.waitFor(5_000L) { rig.shard.lobby.member(rig.client(0).player)?.ready == true }
        assertTrue(rig.shard.lobby.member(rig.client(0).player)?.ready == true)
    }

    @Test fun anActionThatIsNotAvailableExplainsWhy() {
        val rig = NetRig(income = false)
        rig.clients.forEach { it.connect(rig.now) }
        rig.waitFor(10_000L) { rig.clients.all { it.lobby() != null } }
        val host = rig.clients.indexOfFirst { it.player == rig.shard.lobby.hostId }
        assertEquals("Waiting for 2 players to be ready.", source(rig, host, false).act("start"))
        assertEquals("That is not something you can do here.", source(rig, host, false).act("dance"))
    }

    @Test fun theHostSeesConnectionQualityForEveryone() {
        val rig = NetRig(income = false)
        rig.startGame()
        rig.run(20)
        val m = source(rig, 0, host = true).players()
        assertEquals(2, m.rows.size)
        assertTrue(m.rows.all { it.quality != "Offline" })
        assertEquals(1, m.rows.count { "Host" in it.tags })
    }

    @Test fun aGuestSeesTheLobbyWithItsOwnLinkQuality() {
        val rig = NetRig(income = false)
        rig.startGame()
        rig.run(20)
        val m = source(rig, 0, host = false).players()
        assertEquals(2, m.rows.size)
        assertEquals(1, m.rows.count { "Host" in it.tags })
    }

    @Test fun theLeaderboardAndDesyncViewsComeFromTheHost() {
        val rig = NetRig()
        rig.startGame()
        rig.sims[1].companies.values.first().cash += 5
        rig.run(40)
        val src = source(rig, 0, host = true)
        assertEquals(2, src.leaderboard().rows.size)
        assertTrue(src.desync().rows.isNotEmpty())
        assertTrue(source(rig, 0, host = false).leaderboard().rows.isEmpty())
    }

    @Test fun chatGoesOutAndComesBackWithNames() {
        val rig = NetRig(income = false)
        rig.startGame()
        val a = source(rig, 0, false); val b = source(rig, 1, false)
        assertEquals("Write something first.", a.send("  "))
        assertNull(a.send("hello team"))
        a.emote(2)
        rig.frames(10)
        val rows = b.chat()
        assertEquals("hello team", rows.first().text)
        assertEquals(rig.client(0).lobby()?.members?.first { it.id == rig.client(0).player }?.name, rows.first().who)
        assertEquals("[Well played]", rows[1].text)
    }

    @Test fun tradeOffersAppearAndCanBeAccepted() {
        val rig = NetRig(income = false)
        rig.startGame()
        rig.client(0).offerTrade(rig.client(1).player, listOf(TradeAsset.cash(100_000)), listOf(TradeAsset.cash(10_000)), 60)
        rig.run(4)
        rig.frames(30)
        val b = source(rig, 1, false)
        val offer = b.trade().rows.single()
        assertTrue(offer.canAnswer)
        b.answerTrade(offer.id, accept = true)
        rig.run(10)
        rig.drain()
        assertEquals("Done", b.trade().rows.single().status)
    }

    @Test fun kickingNeedsAnActiveSession() {
        val rig = NetRig(income = false)
        val src = source(rig, 0, false)
        src.kick(PlayerId(2))
        assertEquals(ClientState.IDLE, rig.client(0).state)
        assertNotNull(src.link())
    }
}
