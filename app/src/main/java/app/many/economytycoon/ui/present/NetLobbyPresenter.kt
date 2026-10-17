package app.many.economytycoon.ui.present

import app.many.economytycoon.net.LobbyAction
import app.many.economytycoon.net.LobbyConfig
import app.many.economytycoon.net.LobbyPhase
import app.many.economytycoon.net.LobbyView
import app.many.economytycoon.net.MemberInfo
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.Presence
import app.many.economytycoon.net.Role
import app.many.economytycoon.session.Formatting

data class NetMemberRow(
    val id: PlayerId,
    val name: String,
    val role: String,
    val seat: String,
    val status: String,
    val tone: Tone,
    val host: Boolean,
    val me: Boolean,
    val canKick: Boolean,
)

/** Something the person can do in the lobby; [enabled] false means it is shown but cannot be used yet, with [hint] saying why. */
data class NetLobbyAction(val id: String, val label: String, val enabled: Boolean, val hint: String)

data class NetLobbyModel(
    val title: String,
    val phase: String,
    val tiles: List<Tile>,
    val members: List<NetMemberRow>,
    val actions: List<NetLobbyAction>,
    val note: String,
)

/** The multiplayer lobby: who is here, who is ready and what the person can do next. */
object NetLobbyPresenter {
    const val WAITING = "Waiting for the lobby. If this takes long, check the connection."

    fun phaseText(p: LobbyPhase): String = when (p) {
        LobbyPhase.OPEN -> "Waiting for players"
        LobbyPhase.RUNNING -> "Game in progress"
        LobbyPhase.CLOSED -> "Closed"
    }

    fun presence(m: MemberInfo): Pair<String, Tone> = when (m.presence) {
        Presence.GRACE -> "Reconnecting" to Tone.WARN
        Presence.GONE -> "Left" to Tone.MUTED
        Presence.CONNECTED -> when {
            m.role == Role.SPECTATOR -> "Watching" to Tone.INFO
            m.ready -> "Ready" to Tone.GOOD
            else -> "Not ready" to Tone.NEUTRAL
        }
    }

    fun row(m: MemberInfo, host: PlayerId, me: PlayerId): NetMemberRow {
        val (status, tone) = presence(m)
        return NetMemberRow(
            m.id, m.name, if (m.role == Role.PLAYER) "Player" else "Spectator",
            if (m.slot >= 0) "Seat ${m.slot + 1}" else "No seat", status, tone,
            host = m.id == host, me = m.id == me, canKick = host == me && m.id != me && m.presence != Presence.GONE,
        )
    }

    fun actions(view: LobbyView, me: PlayerId, config: LobbyConfig): List<NetLobbyAction> {
        val mine = view.members.firstOrNull { it.id == me } ?: return emptyList()
        val players = view.players.filter { it.presence != Presence.GONE }
        val open = view.phase == LobbyPhase.OPEN
        val out = ArrayList<NetLobbyAction>()
        if (mine.role == Role.PLAYER) {
            out += if (mine.ready) NetLobbyAction("unready", "Not ready", open, if (open) "Change your mind before the game starts." else "The game has started.")
            else NetLobbyAction("ready", "Ready", open, if (open) "Tell the others you are set." else "The game has started.")
        }
        if (me == view.host) {
            val waiting = players.count { !it.ready }
            val hint = when {
                !open -> "The game has started."
                players.size < config.minPlayers -> "At least ${Formatting.plural(config.minPlayers.toLong(), "player")} needed."
                config.requireAllReady && waiting > 0 -> "Waiting for ${Formatting.plural(waiting.toLong(), "player")} to be ready."
                else -> "Start the game for everyone."
            }
            out += NetLobbyAction("start", "Start game", open && players.size >= config.minPlayers && (!config.requireAllReady || waiting == 0), hint)
        }
        if (open && config.allowSpectators && mine.role == Role.PLAYER) out += NetLobbyAction("spectate", "Watch instead", true, "Give up your seat and watch.")
        if (open && mine.role == Role.SPECTATOR) {
            val full = players.size >= config.maxPlayers
            out += NetLobbyAction("play", "Take a seat", !full, if (full) "Every seat is taken." else "Join the players.")
        }
        out += NetLobbyAction("leave", "Leave", true, "Leave the session.")
        return out
    }

    fun build(view: LobbyView?, me: PlayerId, config: LobbyConfig = LobbyConfig()): NetLobbyModel {
        if (view == null) return NetLobbyModel("Multiplayer", "Connecting", emptyList(), emptyList(), listOf(NetLobbyAction("leave", "Leave", true, "Stop trying to connect.")), WAITING)
        val live = view.members.filter { it.presence != Presence.GONE }
        val tiles = listOf(
            Tile("Players", "${view.players.count { it.presence != Presence.GONE }} / ${config.maxPlayers}"),
            Tile("Ready", "${view.players.count { it.ready }}", if (view.players.isNotEmpty() && view.players.all { it.ready }) Tone.GOOD else Tone.NEUTRAL),
            Tile("Watching", view.spectators.count { it.presence != Presence.GONE }.toString()),
        )
        val note = when {
            view.phase == LobbyPhase.CLOSED -> "The host closed this session."
            live.none { it.id == me } -> "You are not part of this session."
            view.phase == LobbyPhase.RUNNING -> "The game is running. Players who join now can only watch."
            else -> "Seats the players leave empty are played by computer-controlled companies."
        }
        return NetLobbyModel("Multiplayer", phaseText(view.phase), tiles, view.members.map { row(it, view.host, me) }, actions(view, me, config), note)
    }

    /** The lobby request an action id stands for, or null for an id this screen does not know. */
    fun request(id: String): LobbyAction? = when (id) {
        "ready" -> LobbyAction.READY
        "unready" -> LobbyAction.UNREADY
        "start" -> LobbyAction.START
        "spectate" -> LobbyAction.SPECTATE
        "play" -> LobbyAction.PLAY
        "leave" -> LobbyAction.LEAVE
        else -> null
    }
}
