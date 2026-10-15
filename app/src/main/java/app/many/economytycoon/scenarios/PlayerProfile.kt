package app.many.economytycoon.scenarios

import app.many.economytycoon.achievements.AchievementBook
import app.many.economytycoon.achievements.LifetimeStats
import app.many.economytycoon.achievements.Stat

/** What the player has achieved in one scenario, across all their games. */
data class ScenarioRecord(val medal: Medal, val bestDays: Long, val wins: Int, val losses: Int) {
    val attempted get() = wins + losses > 0
}

/** Results that outlive any single saved game. */
class PlayerProfile {
    private val records = java.util.TreeMap<String, ScenarioRecord>()

    /** Totals over every game played. */
    val stats = LifetimeStats()

    /** Achievements earned so far. */
    val achievements = AchievementBook()

    fun record(id: String): ScenarioRecord? = records[id]
    fun all(): Map<String, ScenarioRecord> = records.toMap()

    fun medal(id: String): Medal = records[id]?.medal ?: Medal.NONE

    fun won(id: String, medal: Medal, days: Long) {
        val old = records[id]
        val bestMedal = if (old == null || medal > old.medal) medal else old.medal
        val best = if (old == null || old.bestDays <= 0L || days < old.bestDays) days else old.bestDays
        records[id] = ScenarioRecord(bestMedal, best, (old?.wins ?: 0) + 1, old?.losses ?: 0)
        stats.add(Stat.SCENARIOS_WON)
    }

    fun lost(id: String) {
        val old = records[id]
        records[id] = ScenarioRecord(old?.medal ?: Medal.NONE, old?.bestDays ?: 0L, old?.wins ?: 0, (old?.losses ?: 0) + 1)
        stats.add(Stat.SCENARIOS_LOST)
    }

    /** The scenarios that can be started: the first, and any that follows one already won. */
    fun unlocked(): List<ScenarioDef> {
        val out = ArrayList<ScenarioDef>()
        for ((i, def) in ScenarioCatalog.all.withIndex()) {
            if (i == 0 || medal(ScenarioCatalog.all[i - 1].id) != Medal.NONE || medal(def.id) != Medal.NONE) out += def
        }
        return out
    }

    fun isUnlocked(id: String): Boolean = unlocked().any { it.id == id }

    fun medalCount(medal: Medal): Int = ScenarioCatalog.all.count { medal(it.id) == medal }

    /** Gold counts three, silver two, bronze one. */
    fun stars(): Int = ScenarioCatalog.all.sumOf { medal(it.id).ordinal }

    internal fun put(id: String, r: ScenarioRecord) { records[id] = r }
    internal fun clear() { records.clear() }
}
