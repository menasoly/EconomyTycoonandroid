package app.many.economytycoon.ui.present

import app.many.economytycoon.net.ClientView
import app.many.economytycoon.net.DesyncCause
import app.many.economytycoon.net.DesyncReport
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.session.Formatting

data class NetDesyncRow(val tick: String, val headline: String, val detail: String, val groups: List<String>, val tone: Tone)

data class NetDesyncModel(val tiles: List<Tile>, val rows: List<NetDesyncRow>, val empty: String)

/** Where and why two copies of the game stopped agreeing. */
object NetDesyncPresenter {
    const val EMPTY = "Every copy of the game has matched so far."
    private const val SHOWN = 20

    fun headline(cause: DesyncCause): String = when (cause) {
        DesyncCause.NONE -> "Resolved"
        DesyncCause.CLIENTS -> "A player's copy diverged"
        DesyncCause.SERVER -> "The host's copy diverged"
        DesyncCause.UNKNOWN -> "Several copies diverged"
    }

    fun tone(cause: DesyncCause): Tone = when (cause) {
        DesyncCause.NONE -> Tone.NEUTRAL
        DesyncCause.CLIENTS -> Tone.WARN
        DesyncCause.SERVER, DesyncCause.UNKNOWN -> Tone.BAD
    }

    private fun list(names: List<String>): String = when (names.size) {
        0 -> "nobody"
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    fun row(r: DesyncReport, names: (PlayerId) -> String): NetDesyncRow {
        val detail = when (r.cause) {
            DesyncCause.CLIENTS -> "${list(r.culprits.map(names))} did not match the host. They were given a fresh copy."
            DesyncCause.SERVER -> "Every player agreed with each other but not with the host."
            DesyncCause.UNKNOWN -> "No two players agreed, so the cause cannot be placed."
            DesyncCause.NONE -> "The copies agree again."
        }
        val groups = r.groups.map { g -> "${list(g.players.map(names))}: ${g.hex.take(8)}" }
        return NetDesyncRow("Tick ${Formatting.grouped(r.tick)}", headline(r.cause), detail, groups, tone(r.cause))
    }

    fun build(reports: List<DesyncReport>, names: (PlayerId) -> String): NetDesyncModel {
        val latest = reports.sortedByDescending { it.tick }
        val blamed = reports.flatMap { it.culprits }.groupingBy { it }.eachCount()
        val worst = blamed.entries.sortedWith(compareByDescending<Map.Entry<PlayerId, Int>> { it.value }.thenBy { it.key }).firstOrNull()
        val tiles = listOf(
            Tile("Mismatches", reports.size.toString(), if (reports.isEmpty()) Tone.GOOD else Tone.WARN),
            Tile("Latest", latest.firstOrNull()?.let { "Tick ${Formatting.grouped(it.tick)}" } ?: "-"),
            Tile("Most often", worst?.let { names(it.key) } ?: "-", if (worst == null) Tone.NEUTRAL else Tone.WARN, worst?.let { Formatting.plural(it.value.toLong(), "time") }),
        )
        return NetDesyncModel(tiles, latest.take(SHOWN).map { row(it, names) }, EMPTY)
    }

    /** One line for the client's own history of mismatches and repairs. */
    fun selfSummary(v: ClientView): String = when {
        v.desyncs == 0 -> "Your copy has matched the host's throughout."
        v.resyncs >= v.desyncs -> "Your copy drifted ${Formatting.plural(v.desyncs.toLong(), "time")} and was repaired each time."
        else -> "Your copy drifted ${Formatting.plural(v.desyncs.toLong(), "time")}; a repair is in progress."
    }
}
