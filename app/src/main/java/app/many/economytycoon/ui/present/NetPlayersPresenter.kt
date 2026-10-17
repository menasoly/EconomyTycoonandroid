package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ClientState
import app.many.economytycoon.net.ClientView
import app.many.economytycoon.net.PlayerStatus
import app.many.economytycoon.net.Presence
import app.many.economytycoon.net.Quality
import app.many.economytycoon.net.Role
import app.many.economytycoon.net.Standing
import app.many.economytycoon.session.Formatting

data class NetPlayerRow(
    val name: String,
    val tags: List<String>,
    val quality: String,
    val qualityTone: Tone,
    val ping: String,
    val loss: String,
    val sync: String,
    val syncTone: Tone,
    val standing: String,
    val standingTone: Tone,
)

data class NetPlayersModel(val tiles: List<Tile>, val rows: List<NetPlayerRow>, val empty: String)

/** The connection banner a client shows about its own link to the host. */
data class NetLinkBanner(val state: String, val tone: Tone, val detail: String, val quality: String, val qualityTone: Tone)

/** Everyone in the session with the state of their connection, as the host sees it. */
object NetPlayersPresenter {
    const val EMPTY = "Nobody is connected yet."

    fun qualityTone(q: Quality): Tone = when (q) {
        Quality.EXCELLENT, Quality.GOOD -> Tone.GOOD
        Quality.FAIR -> Tone.WARN
        Quality.POOR -> Tone.BAD
        Quality.OFFLINE -> Tone.MUTED
    }

    fun ping(rttMs: Long): String = if (rttMs < 0L) "No reading" else "$rttMs ms"

    fun loss(ppm: Int): String = Formatting.percentPpm(ppm.toLong())

    fun sync(lagTicks: Long, quality: Quality): Pair<String, Tone> = when {
        quality == Quality.OFFLINE -> "Not connected" to Tone.MUTED
        lagTicks < 0L -> "Not started" to Tone.NEUTRAL
        lagTicks == 0L -> "In step" to Tone.GOOD
        lagTicks <= 10L -> "${Formatting.plural(lagTicks, "tick")} behind" to Tone.WARN
        else -> "${Formatting.plural(lagTicks, "tick")} behind" to Tone.BAD
    }

    fun standing(s: Standing): Pair<String, Tone> = when (s) {
        Standing.GOOD -> "Good" to Tone.GOOD
        Standing.WATCHED -> "Watched" to Tone.NEUTRAL
        Standing.WARNED -> "Warned" to Tone.WARN
        Standing.KICKED -> "Removed" to Tone.BAD
        Standing.BANNED -> "Banned" to Tone.BAD
    }

    fun row(p: PlayerStatus): NetPlayerRow {
        val tags = ArrayList<String>()
        if (p.host) tags += "Host"
        if (p.role == Role.SPECTATOR) tags += "Spectator"
        if (p.muted) tags += "Muted"
        if (p.presence == Presence.GRACE) tags += "Reconnecting"
        val (sync, syncTone) = sync(p.lagTicks, p.quality)
        val (standing, standingTone) = standing(p.standing)
        return NetPlayerRow(p.name, tags, p.quality.label, qualityTone(p.quality), ping(p.rttMs), loss(p.lossPpm), sync, syncTone, standing, standingTone)
    }

    fun build(players: List<PlayerStatus>): NetPlayersModel {
        val connected = players.filter { it.quality != Quality.OFFLINE }
        val readings = connected.map { it.rttMs }.filter { it >= 0L }
        val worst = connected.maxByOrNull { it.quality.ordinal }
        val tiles = listOf(
            Tile("Connected", "${connected.size} / ${players.size}", if (connected.size == players.size) Tone.GOOD else Tone.WARN),
            Tile("Average ping", if (readings.isEmpty()) "-" else "${readings.sum() / readings.size} ms"),
            Tile("Weakest link", worst?.quality?.label ?: "-", worst?.let { qualityTone(it.quality) } ?: Tone.NEUTRAL, worst?.name),
        )
        return NetPlayersModel(tiles, players.map { row(it) }, EMPTY)
    }

    fun banner(v: ClientView): NetLinkBanner {
        val (state, tone) = when (v.state) {
            ClientState.IDLE -> "Not connected" to Tone.MUTED
            ClientState.CONNECTING -> "Connecting" to Tone.INFO
            ClientState.LOBBY -> "In the lobby" to Tone.GOOD
            ClientState.SYNCING -> "Catching up" to Tone.WARN
            ClientState.RUNNING -> (if (v.lagTicks > 10L) "Falling behind" to Tone.WARN else "Connected" to Tone.GOOD)
            ClientState.RECONNECTING -> "Reconnecting" to Tone.WARN
            ClientState.CLOSED -> "Left the session" to Tone.MUTED
            ClientState.FAILED -> "Connection failed" to Tone.BAD
        }
        val detail = when (v.state) {
            ClientState.SYNCING -> if (v.snapshotTotal > 0) "Receiving the game state, ${v.snapshotReceived} of ${v.snapshotTotal} parts." else "Asking the host for the game state."
            ClientState.RUNNING -> sync(v.lagTicks, v.quality).first + if (v.inFlight > 0) ", ${Formatting.plural(v.inFlight.toLong(), "order")} waiting" else ""
            else -> ""
        }
        return NetLinkBanner(state, tone, detail, v.quality.label, qualityTone(v.quality))
    }
}
