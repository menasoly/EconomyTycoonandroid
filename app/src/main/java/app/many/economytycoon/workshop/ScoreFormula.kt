package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.scenarios.Medal
import java.util.zip.CRC32

data class ScoreLine(val label: String, val points: Long)

/** A score and where it came from, line by line, so a player can see what to improve. */
class ScoreBreakdown(val lines: List<ScoreLine>, val multiplierPercent: Int, val total: Long) {
    fun describe(): List<String> = lines.map { "${it.label}: ${if (it.points >= 0) "+" else ""}${it.points}" } + "difficulty multiplier: $multiplierPercent%" + "total: $total"
}

/**
 * The points for a run of a scenario, the same for every player: progress on the goals, a bonus for winning that grows
 * with speed and medal, bonus goals, a little for sheer net worth, penalties for going bankrupt or getting stuck, all
 * multiplied by how hard the scenario rated. Integer arithmetic only, so a score can be recomputed and checked anywhere.
 */
object ScoreFormula {
    const val PROGRESS_POINTS = 10_000L
    const val WIN_POINTS = 5_000L
    const val SPEED_POINTS = 2_000L
    const val BONUS_GOAL_POINTS = 500L
    const val WORTH_POINT_STEP = 100L
    const val WORTH_POINT_CAP = 3_000L
    const val BANKRUPT_PENALTY = 2_000L
    const val LOSS_PENALTY = 1_000L
    const val STUCK_PENALTY = 1_500L
    const val BASE_MULTIPLIER = 80

    fun medalFor(draft: ScenarioDraft, days: Int): Medal = when {
        days <= draft.timing.goldDays -> Medal.GOLD
        days <= draft.timing.silverDays -> Medal.SILVER
        days <= draft.deadlineDays -> Medal.BRONZE
        else -> Medal.NONE
    }

    private fun medalPoints(m: Medal): Long = when (m) { Medal.GOLD -> 4_000L; Medal.SILVER -> 2_000L; Medal.BRONZE -> 800L; Medal.NONE -> 0L }

    /** Multiplier from the difficulty rating, 80% for the easiest scenario up to 180% for the hardest. */
    fun multiplier(rating: DifficultyRating?): Int = if (rating == null) 100 else BASE_MULTIPLIER + rating.score.coerceIn(0, 100)

    fun score(draft: ScenarioDraft, run: RunMetrics, rating: DifficultyRating? = null): ScoreBreakdown {
        val d = draft.normalised()
        val lines = ArrayList<ScoreLine>()
        val required = d.goals.withIndex().filter { !it.value.bonus }
        val weights = required.sumOf { it.value.weight.toLong() }
        val progress = if (weights == 0L) 0L else required.sumOf { (run.last.goalPpm.getOrNull(it.index) ?: 0L) * it.value.weight } / weights
        lines += ScoreLine("progress on goals", progress * PROGRESS_POINTS / 1_000_000L)
        if (run.won) {
            val days = run.allDoneDay ?: run.days
            lines += ScoreLine("won", WIN_POINTS)
            lines += ScoreLine("${medalFor(d, days).label.lowercase()} medal", medalPoints(medalFor(d, days)))
            val left = (d.deadlineDays - days).coerceAtLeast(0)
            lines += ScoreLine("speed", left.toLong() * SPEED_POINTS / d.deadlineDays.coerceAtLeast(1))
        }
        for ((i, g) in d.goals.withIndex()) if (g.bonus) {
            val p = run.last.goalPpm.getOrNull(i) ?: 0L
            lines += ScoreLine("bonus goal ${i + 1}", p * g.weight * BONUS_GOAL_POINTS / 1_000_000L)
        }
        lines += ScoreLine("net worth", (log2(run.last.netWorth) * WORTH_POINT_STEP).coerceAtMost(WORTH_POINT_CAP))
        if (run.bankruptDay != null) lines += ScoreLine("bankruptcy", -BANKRUPT_PENALTY)
        if (run.end == RunEnd.LOST) lines += ScoreLine("lost", -LOSS_PENALTY)
        if (run.softlockDay != null) lines += ScoreLine("stuck", -STUCK_PENALTY)
        val mult = multiplier(rating)
        val raw = lines.sumOf { it.points }.coerceAtLeast(0L)
        return ScoreBreakdown(lines.filter { it.points != 0L }, mult, raw * mult / 100L)
    }

    private fun log2(v: Long): Long = if (v <= 1L) 0L else (63 - java.lang.Long.numberOfLeadingZeros(v)).toLong()
}

/** A score ready to be posted to a leaderboard: what was played, by whom, and a checksum that catches casual editing. */
data class ScoreSubmission(
    val draftHash: String,
    val scenarioId: String,
    val player: String,
    val seed: Long,
    val days: Int,
    val won: Boolean,
    val score: Long,
) {
    init { require(player.length in 1..MAX_PLAYER) { "a player name needs 1 to $MAX_PLAYER characters" } }

    val checksum: Long get() = CRC32().also { it.update(canonical()) }.value

    private fun canonical(): ByteArray = ByteWriter().also {
        it.writeString(draftHash); it.writeString(scenarioId); it.writeString(player); it.writeVarLong(seed)
        it.writeVarInt(days); it.writeBoolean(won); it.writeVarLong(score)
    }.toByteArray()

    fun toBytes(): ByteArray = ByteWriter().also {
        it.writeVarInt(VERSION); it.writeBytes(canonical()); it.writeFixedLong(checksum)
    }.toByteArray()

    companion object {
        const val VERSION = 1
        const val MAX_PLAYER = 24

        fun of(draft: ScenarioDraft, player: String, run: RunMetrics, breakdown: ScoreBreakdown) =
            ScoreSubmission(draft.stateHash(), draft.id, player, run.seed, run.allDoneDay ?: run.days, run.won, breakdown.total)

        fun fromBytes(bytes: ByteArray): ScoreSubmission {
            val r = ByteReader(bytes)
            val version = r.readVarInt()
            if (version != VERSION) throw ByteReader.CorruptDataException("unsupported score version $version")
            val body = r.readBytes()
            val stored = r.readFixedLong()
            val p = ByteReader(body)
            val s = try {
                ScoreSubmission(p.readString(), p.readString(), p.readString(), p.readVarLong(), p.readVarInt(), p.readBoolean(), p.readVarLong())
            } catch (e: IllegalArgumentException) { throw ByteReader.CorruptDataException(e.message ?: "bad score") }
            if (s.checksum != stored) throw ByteReader.CorruptDataException("score checksum does not match")
            return s
        }
    }
}

data class RankedScore(val position: Int, val submission: ScoreSubmission)

object Leaderboard {
    /**
     * Orders scores for one scenario: higher score first, then faster, then lower seed, then player name, so the order is
     * the same wherever it is computed. Equal scores in equal time share a position, and the next position skips ahead.
     */
    fun rank(scores: List<ScoreSubmission>): List<RankedScore> {
        val sorted = scores.sortedWith(compareByDescending<ScoreSubmission> { it.score }.thenBy { it.days }.thenBy { it.seed }.thenBy { it.player })
        val out = ArrayList<RankedScore>()
        for ((i, s) in sorted.withIndex()) {
            val prev = out.lastOrNull()?.submission
            val pos = if (prev != null && prev.score == s.score && prev.days == s.days) out.last().position else i + 1
            out += RankedScore(pos, s)
        }
        return out
    }

    /** Each player's best score only. */
    fun bestPerPlayer(scores: List<ScoreSubmission>): List<ScoreSubmission> =
        scores.groupBy { it.player }.mapNotNull { (_, list) -> list.maxWithOrNull(compareBy<ScoreSubmission> { it.score }.thenByDescending { it.days }) }
}
