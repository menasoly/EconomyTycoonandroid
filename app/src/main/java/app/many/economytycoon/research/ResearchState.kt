package app.many.economytycoon.research

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.TechId

data class ResearchParams(
    /** Projects a company can run side by side. */
    val maxConcurrent: Int = 2,
    /** Most points a project can absorb per tick, as a multiple of its nominal pace, ppm. */
    val paceCapPpm: Long = 2_000_000L,
    val maxQueued: Int = 10,
    /** Effect left on a technology long after it became obsolete, ppm. */
    val obsoleteFloorPpm: Long = 500_000L,
    /** Share of a technology's research cost charged for a licence, ppm. */
    val licenseFeePpm: Long = 400_000L,
    /** Cap on the summed saving of one kind, ppm. */
    val savingsCapPpm: Long = 800_000L,
    /** Cap on the summed bonus of one kind, ppm. */
    val bonusCapPpm: Long = 3_000_000L,
)

/** One technology being researched. Progress is measured in research points. */
class ResearchProject(val tech: TechId, val started: Tick, val costPoints: Long) {
    var invested: Long = 0
        internal set

    val remaining: Long get() = (costPoints - invested).coerceAtLeast(0)
    val isFunded: Boolean get() = invested >= costPoints
    val progressPpm: Long get() = if (costPoints == 0L) 1_000_000L else invested * 1_000_000L / costPoints

    internal fun restore(invested: Long) { this.invested = invested }
}

/** Everything one company knows and is working on. */
class CompanyResearch(val company: CompanyId) {
    internal val unlocked = java.util.TreeMap<TechId, Tick>()
    internal val active = ArrayList<ResearchProject>()
    internal val queue = ArrayList<TechId>()

    fun isUnlocked(t: TechId) = unlocked.containsKey(t)
    fun unlockedAt(t: TechId): Tick? = unlocked[t]
    fun unlockedTechs(): List<TechId> = unlocked.keys.toList()
    fun projects(): List<ResearchProject> = active.toList()
    fun queued(): List<TechId> = queue.toList()
    fun project(t: TechId): ResearchProject? = active.firstOrNull { it.tech == t }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(company.value).putInt(unlocked.size)
        for ((t, at) in unlocked) h.putString(t.key).putLong(at.value)
        h.putInt(active.size)
        for (p in active) h.putString(p.tech.key).putLong(p.started.value).putLong(p.costPoints).putLong(p.invested)
        h.putInt(queue.size)
        for (t in queue) h.putString(t.key)
    }
}

sealed class ResearchEvent {
    abstract val company: CompanyId
    abstract val tech: TechId
    data class Started(override val company: CompanyId, override val tech: TechId, val tick: Tick) : ResearchEvent()
    data class Completed(override val company: CompanyId, override val tech: TechId, val tick: Tick) : ResearchEvent()
    data class Cancelled(override val company: CompanyId, override val tech: TechId, val tick: Tick) : ResearchEvent()
}
