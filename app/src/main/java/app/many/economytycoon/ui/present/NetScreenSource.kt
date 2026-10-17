package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ClientSession
import app.many.economytycoon.net.ClientState
import app.many.economytycoon.net.LobbyAction
import app.many.economytycoon.net.LobbyConfig
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.PlayerStatus
import app.many.economytycoon.net.Presence
import app.many.economytycoon.net.Quality
import app.many.economytycoon.net.ServerShard
import app.many.economytycoon.net.Standing

/** Everything the multiplayer screen needs, already shaped for display. The screen holds no other state about the session. */
interface NetScreenSource {
    fun lobby(): NetLobbyModel
    fun link(): NetLinkBanner
    fun players(): NetPlayersModel
    fun desync(): NetDesyncModel
    fun leaderboard(): NetLeaderboardModel
    fun chat(): List<NetChatRow>

    /** Carries out a lobby action by id; returns a message for the person, or null when nothing needs saying. */
    fun act(id: String): String?
    fun kick(player: PlayerId)
    fun send(text: String): String?
    fun emote(id: Int)
    fun trade(): NetTradeModel
    fun answerTrade(id: Long, accept: Boolean)
}

/**
 * The source for a person playing through [client]. When this device also hosts the game ([shard] is not null) the
 * host-only views, such as connection quality for everyone and the desync history, are filled in from it.
 */
class SessionNetSource(
    private val client: ClientSession,
    private val shard: ServerShard? = null,
    private val lobbyConfig: LobbyConfig = LobbyConfig(),
    private val now: () -> Long,
) : NetScreenSource {
    private fun name(p: PlayerId): String =
        client.lobby()?.members?.firstOrNull { it.id == p }?.name ?: if (p == PlayerId.SERVER) "Host" else "Player ${p.value}"

    override fun lobby() = NetLobbyPresenter.build(client.lobby(), client.player, lobbyConfig)

    override fun link() = NetPlayersPresenter.banner(client.view(now()))

    override fun players(): NetPlayersModel {
        if (shard != null) return NetPlayersPresenter.build(shard.status(now()).players)
        val view = client.lobby() ?: return NetPlayersPresenter.build(emptyList())
        // a guest only knows the lobby, so everyone shares the guest's own view of the link
        val q = client.quality(now())
        return NetPlayersPresenter.build(view.members.map {
            PlayerStatus(it.id, it.name, it.role, it.slot, -1L, it.presence, it.ready, if (it.presence == Presence.CONNECTED) q else Quality.OFFLINE, -1L, 0, -1L, Standing.GOOD, 0, false, it.id == view.host)
        })
    }

    override fun desync(): NetDesyncModel = NetDesyncPresenter.build(shard?.desyncReports().orEmpty(), ::name)

    override fun leaderboard(): NetLeaderboardModel {
        val s = shard ?: return NetLeaderboardPresenter.build(emptyList(), app.many.economytycoon.net.SeasonClock(1, 0, 1), client.tick, null)
        val me = s.lobby.member(client.player)?.company?.takeIf { it >= 0L }
        return NetLeaderboardPresenter.build(s.leaderboard(), s.season.clock, s.tick, me)
    }

    override fun chat() = NetChatPresenter.build(client.chat.recent(), client.player, ::name)

    override fun act(id: String): String? {
        val action = NetLobbyPresenter.request(id) ?: return "That is not something you can do here."
        val offered = lobby().actions.firstOrNull { it.id == id }
        if (offered == null || !offered.enabled) return offered?.hint ?: "That is not available right now."
        client.requestLobby(action, now())
        return null
    }

    override fun kick(player: PlayerId) {
        if (client.state == ClientState.LOBBY || client.state == ClientState.RUNNING) client.requestLobby(LobbyAction.KICK, now(), player)
    }

    override fun send(text: String): String? {
        NetChatPresenter.problem(text)?.let { return it }
        client.say(text.trim())
        return null
    }

    override fun emote(id: Int) { client.emote(id) }

    override fun trade() = NetTradePresenter.build(client.trades(), client.player, ::name)

    override fun answerTrade(id: Long, accept: Boolean) {
        client.answerTrade(app.many.economytycoon.net.TradeId(id), if (accept) app.many.economytycoon.net.TradeAction.ACCEPT else app.many.economytycoon.net.TradeAction.DECLINE)
    }
}
