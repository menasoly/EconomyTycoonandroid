package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money

/** A season is a fixed run of ticks; everyone is scored on what changed inside it. */
data class SeasonClock(val number: Int, val startTick: Long, val lengthTicks: Long) {
    init { require(lengthTicks > 0 && number >= 1 && startTick >= 0) { "invalid season" } }

    val endTick: Long get() = startTick + lengthTicks
    fun over(tick: Long) = tick >= endTick
    fun elapsed(tick: Long): Long = (tick - startTick).coerceIn(0L, lengthTicks)
    fun remaining(tick: Long): Long = (endTick - tick).coerceIn(0L, lengthTicks)

    /** Progress in parts per million. */
    fun progressPpm(tick: Long): Long = elapsed(tick) * 1_000_000L / lengthTicks

    fun next() = SeasonClock(number + 1, endTick, lengthTicks)
}

/** A finished season: the final table, kept for the record. */
data class SeasonResult(val season: Int, val endedAt: Long, val rows: List<LeaderRow>)

/**
 * Skill rating from placements. Each finish is treated as a set of one-on-one results against everybody who
 * finished lower; the expected result comes from a fixed table of the logistic curve, so ratings are exact
 * integers and the same on every device.
 */
object Rating {
    const val START = 1000
    const val K = 32

    /** Expected score in parts per million for a rating lead of 0, 50, ... 800 points. */
    private val TABLE = intArrayOf(
        500_000, 571_463, 640_065, 703_385, 759_747, 808_318, 849_020, 882_338, 909_091,
        930_242, 946_760, 959_537, 969_347, 976_836, 982_528, 986_840, 990_099,
    )

    /** Chance in ppm that a player rated [a] beats one rated [b]. */
    fun expectedPpm(a: Int, b: Int): Int {
        val diff = (a - b).coerceIn(-800, 800)
        val lead = Math.abs(diff)
        val i = lead / 50
        val frac = lead % 50
        val lo = TABLE[i]
        val hi = if (i + 1 < TABLE.size) TABLE[i + 1] else lo
        val up = lo + (hi - lo) * frac / 50
        return if (diff >= 0) up else 1_000_000 - up
    }

    /** New ratings after one finish; [order] lists players best first. Total rating is conserved up to rounding. */
    fun update(ratings: Map<PlayerId, Int>, order: List<PlayerId>): Map<PlayerId, Int> {
        val n = order.size
        if (n < 2) return ratings
        val delta = HashMap<PlayerId, Long>()
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = order[i]; val b = order[j]
            val ra = ratings[a] ?: START; val rb = ratings[b] ?: START
            val exp = expectedPpm(ra, rb).toLong()
            val change = K * (1_000_000L - exp)
            delta[a] = (delta[a] ?: 0L) + change
            delta[b] = (delta[b] ?: 0L) - change
        }
        val out = LinkedHashMap(ratings)
        for (p in order) out[p] = (ratings[p] ?: START) + Math.floorDiv(delta[p] ?: 0L, 1_000_000L * (n - 1)).toInt()
        return out
    }
}

/**
 * The record of finished seasons and each player's rating, titles and best finish. It is only appended to when a
 * season closes, which makes it safe to keep on a server between sessions.
 */
class SeasonLedger {
    private val results = ArrayList<SeasonResult>()
    private val ratings = java.util.TreeMap<PlayerId, Int>()
    private val titles = java.util.TreeMap<PlayerId, Int>()
    private val best = java.util.TreeMap<PlayerId, Int>()

    fun results(): List<SeasonResult> = results.toList()
    fun rating(p: PlayerId): Int = ratings[p] ?: Rating.START
    fun titles(p: PlayerId): Int = titles[p] ?: 0
    fun bestFinish(p: PlayerId): Int? = best[p]
    fun ratingTable(): List<Pair<PlayerId, Int>> = ratings.entries.map { it.key to it.value }.sortedWith(compareByDescending<Pair<PlayerId, Int>> { it.second }.thenBy { it.first })

    /** Closes a season with its final table; only the rows run by people count towards ratings. */
    fun close(season: Int, endedAt: Long, rows: List<LeaderRow>): SeasonResult {
        require(results.none { it.season == season }) { "season $season is already closed" }
        val result = SeasonResult(season, endedAt, rows)
        results += result
        val humans = rows.filter { it.player != null }
        val order = humans.mapNotNull { it.player }
        val updated = Rating.update(order.associateWith { rating(it) }, order)
        for ((p, r) in updated) ratings[p] = r
        for ((i, row) in humans.withIndex()) {
            val p = row.player ?: continue
            val place = i + 1
            best[p] = minOf(best[p] ?: Int.MAX_VALUE, place)
            if (place == 1 && humans.size > 1) titles[p] = (titles[p] ?: 0) + 1
        }
        return result
    }

    fun write(w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarInt(results.size)
        for (r in results) {
            w.writeVarInt(r.season); w.writeVarLong(r.endedAt); w.writeVarInt(r.rows.size)
            for (row in r.rows) {
                w.writeVarInt(row.rank); w.writeVarLong(row.company); w.writeString(row.name); w.writeVarLong(row.player?.value ?: -1L)
                w.writeVarInt(row.score.lines.size)
                for (l in row.score.lines) { w.writeString(l.label); w.writeVarLong(l.points) }
                w.writeVarLong(row.score.total); w.writeMoney(row.netWorth); w.writeMoney(row.netIncome); w.writeMoney(row.revenue); w.writeBoolean(row.tied)
            }
        }
        w.writeVarInt(ratings.size); for ((p, v) in ratings) { w.writeVarLong(p.value); w.writeVarInt(v) }
        w.writeVarInt(titles.size); for ((p, v) in titles) { w.writeVarLong(p.value); w.writeVarInt(v) }
        w.writeVarInt(best.size); for ((p, v) in best) { w.writeVarLong(p.value); w.writeVarInt(v) }
    }

    fun read(r: ByteReader) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported season ledger version $version")
        val loaded = ArrayList<SeasonResult>()
        repeat(bounded(r, MAX_SEASONS)) {
            val season = r.readVarInt(); val endedAt = r.readVarLong()
            val rows = List(bounded(r, MAX_ROWS)) {
                val rank = r.readVarInt(); val company = r.readVarLong(); val name = r.readString(); val player = r.readVarLong().takeIf { it >= 0 }?.let { PlayerId(it) }
                val lines = List(bounded(r, MAX_LINES)) { ScoreLine(r.readString(), r.readVarLong()) }
                val total = r.readVarLong()
                LeaderRow(rank, company, name, player, ScoreBreakdown(lines, total), r.readMoney(), r.readMoney(), r.readMoney(), r.readBoolean())
            }
            loaded += SeasonResult(season, endedAt, rows)
        }
        val newRatings = java.util.TreeMap<PlayerId, Int>(); repeat(bounded(r, MAX_ROWS)) { newRatings[PlayerId(r.readVarLong())] = r.readVarInt() }
        val newTitles = java.util.TreeMap<PlayerId, Int>(); repeat(bounded(r, MAX_ROWS)) { newTitles[PlayerId(r.readVarLong())] = r.readVarInt() }
        val newBest = java.util.TreeMap<PlayerId, Int>(); repeat(bounded(r, MAX_ROWS)) { newBest[PlayerId(r.readVarLong())] = r.readVarInt() }
        results.clear(); results.addAll(loaded)
        ratings.clear(); ratings.putAll(newRatings); titles.clear(); titles.putAll(newTitles); best.clear(); best.putAll(newBest)
    }

    private fun bounded(r: ByteReader, max: Int): Int {
        val n = r.readVarInt()
        if (n < 0 || n > max) throw ByteReader.CorruptDataException("implausible count $n")
        return n
    }

    companion object {
        const val VERSION = 1
        private const val MAX_SEASONS = 100_000
        private const val MAX_ROWS = 1_000
        private const val MAX_LINES = 32
    }
}

/**
 * Runs the current season: remembers the net worth each company began with, builds the live table and closes the
 * season into the ledger when its time is up.
 */
class SeasonTracker(
    private val ledger: SeasonLedger,
    private val rules: ScoreRules = ScoreRules(),
    lengthTicks: Long = 360L,
) {
    var clock: SeasonClock = SeasonClock(1, 0L, lengthTicks)
        private set
    private val baselines = java.util.TreeMap<Long, Money>()

    /** Records where everyone stands as the season begins. Companies that appear later start from zero. */
    fun begin(statements: List<CompanyStatement>) {
        baselines.clear()
        for (s in statements) baselines[s.company] = s.netWorth
    }

    fun baselineOf(company: Long): Money = baselines[company] ?: Money.ZERO

    fun write(w: ByteWriter) {
        w.writeVarInt(VERSION)
        w.writeVarInt(clock.number); w.writeVarLong(clock.startTick); w.writeVarLong(clock.lengthTicks)
        w.writeVarInt(baselines.size)
        for ((company, worth) in baselines) { w.writeVarLong(company); w.writeVarLong(worth.raw) }
    }

    fun read(r: ByteReader) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported season tracker version $version")
        val number = r.readVarInt(); val start = r.readVarLong(); val length = r.readVarLong()
        if (number < 1 || start < 0L || length < 1L) throw ByteReader.CorruptDataException("implausible season clock")
        val n = r.readVarInt()
        if (n < 0 || n > MAX_COMPANIES) throw ByteReader.CorruptDataException("implausible company count $n")
        val loaded = java.util.TreeMap<Long, Money>()
        repeat(n) { loaded[r.readVarLong()] = Money(r.readVarLong()) }
        clock = SeasonClock(number, start, length)
        baselines.clear(); baselines.putAll(loaded)
    }

    fun table(statements: List<CompanyStatement>): List<LeaderRow> = SeasonScoring.table(statements, baselines, rules)

    /** Closes the season if its end has been reached and starts the next one; returns the result or null. */
    fun advance(tick: Long, statements: () -> List<CompanyStatement>): SeasonResult? {
        if (!clock.over(tick)) return null
        val stmts = statements()
        val result = ledger.close(clock.number, tick, table(stmts))
        clock = clock.next()
        begin(stmts)
        return result
    }

    private companion object {
        const val VERSION = 1
        const val MAX_COMPANIES = 100_000
    }
}
