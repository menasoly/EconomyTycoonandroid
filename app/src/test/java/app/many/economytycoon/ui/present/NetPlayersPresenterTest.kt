package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ClientState
import app.many.economytycoon.net.ClientView
import app.many.economytycoon.net.LobbyPhase
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.PlayerStatus
import app.many.economytycoon.net.Presence
import app.many.economytycoon.net.Quality
import app.many.economytycoon.net.Role
import app.many.economytycoon.net.Standing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetPlayersPresenterTest {
    private fun status(
        name: String, quality: Quality = Quality.GOOD, rtt: Long = 60, loss: Int = 0, lag: Long = 0, role: Role = Role.PLAYER,
        presence: Presence = Presence.CONNECTED, standing: Standing = Standing.GOOD, muted: Boolean = false, host: Boolean = false,
    ) = PlayerStatus(PlayerId(name.length.toLong()), name, role, 0, 1, presence, true, quality, rtt, loss, lag, standing, 0, muted, host)

    private fun view(state: ClientState = ClientState.RUNNING, lag: Long = 0, inFlight: Int = 0, got: Int = 0, total: Int = 0) =
        ClientView(state, PlayerId(1), Role.PLAYER, 10, 10 + lag, lag, null, Quality.GOOD, 50, 0, inFlight, 0, 0, got, total, 0)

    @Test fun qualityTonesRunFromGoodToMuted() {
        assertEquals(listOf(Tone.GOOD, Tone.GOOD, Tone.WARN, Tone.BAD, Tone.MUTED), Quality.values().map { NetPlayersPresenter.qualityTone(it) })
    }

    @Test fun pingAndLossAreReadable() {
        assertEquals("84 ms", NetPlayersPresenter.ping(84))
        assertEquals("No reading", NetPlayersPresenter.ping(-1))
        assertEquals("12.5%", NetPlayersPresenter.loss(125_000))
        assertEquals("0.0%", NetPlayersPresenter.loss(0))
    }

    @Test fun syncDescribesHowFarBehindAPlayerIs() {
        assertEquals("In step" to Tone.GOOD, NetPlayersPresenter.sync(0, Quality.GOOD))
        assertEquals("3 ticks behind" to Tone.WARN, NetPlayersPresenter.sync(3, Quality.GOOD))
        assertEquals("1 tick behind" to Tone.WARN, NetPlayersPresenter.sync(1, Quality.GOOD))
        assertEquals("40 ticks behind" to Tone.BAD, NetPlayersPresenter.sync(40, Quality.POOR))
        assertEquals("Not connected" to Tone.MUTED, NetPlayersPresenter.sync(0, Quality.OFFLINE))
        assertEquals("Not started" to Tone.NEUTRAL, NetPlayersPresenter.sync(-1, Quality.GOOD))
    }

    @Test fun rowsCarryTagsInAStableOrder() {
        val r = NetPlayersPresenter.row(status("Ada", host = true, muted = true, presence = Presence.GRACE))
        assertEquals(listOf("Host", "Muted", "Reconnecting"), r.tags)
        assertEquals(listOf("Spectator"), NetPlayersPresenter.row(status("Bob", role = Role.SPECTATOR)).tags)
    }

    @Test fun standingsHaveWords() {
        assertEquals("Warned" to Tone.WARN, NetPlayersPresenter.standing(Standing.WARNED))
        assertEquals(Standing.values().size, Standing.values().map { NetPlayersPresenter.standing(it).first }.toSet().size)
    }

    @Test fun tilesSummarizeTheSession() {
        val m = NetPlayersPresenter.build(listOf(status("Ada", Quality.EXCELLENT, 20), status("Bobby", Quality.POOR, 300), status("Cy", Quality.OFFLINE, -1)))
        assertEquals("2 / 3", m.tiles[0].value)
        assertEquals(Tone.WARN, m.tiles[0].tone)
        assertEquals("160 ms", m.tiles[1].value)
        assertEquals("Poor", m.tiles[2].value)
        assertEquals("Bobby", m.tiles[2].note)
        assertEquals(3, m.rows.size)
    }

    @Test fun anEmptySessionHasDashesNotErrors() {
        val m = NetPlayersPresenter.build(emptyList())
        assertEquals(listOf("0 / 0", "-", "-"), m.tiles.map { it.value })
        assertEquals(NetPlayersPresenter.EMPTY, m.empty)
    }

    @Test fun theBannerFollowsTheClientState() {
        assertEquals("Connected" to Tone.GOOD, NetPlayersPresenter.banner(view()).let { it.state to it.tone })
        assertEquals("Falling behind", NetPlayersPresenter.banner(view(lag = 30)).state)
        assertEquals(Tone.BAD, NetPlayersPresenter.banner(view(ClientState.FAILED)).tone)
        assertEquals("Reconnecting", NetPlayersPresenter.banner(view(ClientState.RECONNECTING)).state)
        assertTrue(ClientState.values().all { NetPlayersPresenter.banner(view(it)).state.isNotEmpty() })
    }

    @Test fun theBannerReportsSnapshotProgressAndWaitingOrders() {
        assertEquals("Asking the host for the game state.", NetPlayersPresenter.banner(view(ClientState.SYNCING)).detail)
        assertEquals("Receiving the game state, 3 of 8 parts.", NetPlayersPresenter.banner(view(ClientState.SYNCING, got = 3, total = 8)).detail)
        assertTrue(NetPlayersPresenter.banner(view(inFlight = 2)).detail.endsWith("2 orders waiting"))
    }
}
