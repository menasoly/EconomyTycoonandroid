package app.many.economytycoon.achievements

import java.util.TreeMap

/** Which achievements a player has earned, and when (in lifetime days played). */
class AchievementBook {
    private val unlocked = TreeMap<String, Long>()

    fun isUnlocked(id: String): Boolean = unlocked.containsKey(id)
    fun unlockedAt(id: String): Long? = unlocked[id]
    fun all(): Map<String, Long> = unlocked.toMap()
    val count: Int get() = unlocked.size

    /** Marks [id] earned. False when it already was, or is not an achievement of this version. */
    fun unlock(id: String, atDay: Long): Boolean {
        if (AchievementCatalog.byId(id) == null || unlocked.containsKey(id)) return false
        unlocked[id] = atDay.coerceAtLeast(0L)
        return true
    }

    fun points(): Int = unlocked.keys.sumOf { AchievementCatalog.byId(it)?.points ?: 0 }

    fun unlockedIn(category: Category): List<Achievement> = AchievementCatalog.inCategory(category).filter { isUnlocked(it.id) }

    internal fun put(id: String, atDay: Long) { unlocked[id] = atDay }
    internal fun clear() { unlocked.clear() }

    override fun equals(other: Any?): Boolean = other is AchievementBook && other.unlocked == unlocked
    override fun hashCode(): Int = unlocked.hashCode()
}
