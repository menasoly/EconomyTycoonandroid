package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.net.LeaderRow
import app.many.economytycoon.net.PlayerId
import app.many.economytycoon.net.SeasonClock
import app.many.economytycoon.net.SeasonResult
import app.many.economytycoon.session.Formatting

data class NetLeaderRow(
    val rank: String,
    val name: String,
    val points: String,
    val worth: String,
    val income: String,
    val breakdown: List<Fact>,
    val tone: Tone,
    val mine: Boolean,
)

data class NetLeaderboardModel(val tiles: List<Tile>, val rows: List<NetLeaderRow>, val progressPpm: Long, val empty: String)

data class NetSeasonRow(val title: String, val winner: String, val points: String, val standings: List<String>)

/** The season table: who is ahead, by how much, and how the points were earned. */
object NetLeaderboardPresenter {
    const val EMPTY = "No companies are in the race yet."

    fun points(p: Long): String = (if (p > 0) "+" else "") + Formatting.grouped(p)

    fun rank(r: LeaderRow): String = (if (r.tied) "=" else "") + r.rank

    fun tone(r: LeaderRow, mine: Boolean): Tone = when {
        mine -> Tone.INFO
        r.rank == 1 -> Tone.GOOD
        r.total < 0 -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    fun row(r: LeaderRow, myCompany: Long?, money: (Money) -> String = { Formatting.compactMoney(it) }): NetLeaderRow {
        val mine = myCompany != null && r.company == myCompany
        return NetLeaderRow(
            rank(r), r.name + if (mine) " (you)" else "", points(r.total), money(r.netWorth), Formatting.signedMoney(r.netIncome),
            r.score.lines.map { Fact(it.label, points(it.points), Tones.bySign(it.points)) }, tone(r, mine), mine,
        )
    }

    fun build(rows: List<LeaderRow>, clock: SeasonClock, tick: Long, myCompany: Long?): NetLeaderboardModel {
        val leader = rows.firstOrNull()
        val me = rows.firstOrNull { myCompany != null && it.company == myCompany }
        val gap = if (leader != null && me != null && me !== leader) leader.total - me.total else null
        val tiles = listOf(
            Tile("Season", clock.number.toString(), note = if (clock.over(tick)) "Finished" else "${Formatting.plural(clock.remaining(tick), "tick")} left"),
            Tile("Leader", leader?.name ?: "-", Tone.GOOD, leader?.let { "${points(it.total)} points" }),
            Tile("Your place", me?.let { rank(it) } ?: "-", me?.let { tone(it, true) } ?: Tone.NEUTRAL, gap?.let { "${Formatting.grouped(it)} points behind" }),
        )
        return NetLeaderboardModel(tiles, rows.map { row(it, myCompany) }, clock.progressPpm(tick), EMPTY)
    }

    /** Finished seasons, newest first. */
    fun history(results: List<SeasonResult>, names: (PlayerId) -> String): List<NetSeasonRow> = results.sortedByDescending { it.season }.map { s ->
        val top = s.rows.firstOrNull()
        NetSeasonRow(
            "Season ${s.season}", top?.let { it.player?.let(names) ?: it.name } ?: "No entries", top?.let { points(it.total) } ?: "-",
            s.rows.take(3).map { "${rank(it)}. ${it.player?.let(names) ?: it.name} ${points(it.total)}" },
        )
    }
}
