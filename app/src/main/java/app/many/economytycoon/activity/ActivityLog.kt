package app.many.economytycoon.activity

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.LoggedCommand

/** Who gave the order. */
enum class Origin(val label: String) { PLAYER("You"), AI("Rival"), SYSTEM("Game") }

/** One thing done for a company, in words. */
data class ActivityEntry(
    val sequence: Long,
    val tick: Long,
    val origin: Origin,
    val area: ActivityArea,
    val text: String,
    val movesMoney: Boolean,
)

/** Turns the log of commands into a readable history for one company. */
object ActivityLog {
    fun originOf(a: Actor): Origin = when (a) {
        is Actor.Player -> Origin.PLAYER
        is Actor.Ai -> Origin.AI
        Actor.System -> Origin.SYSTEM
    }

    fun entry(l: LoggedCommand, names: DescribeNames): ActivityEntry = ActivityEntry(
        l.sequence, l.tick, originOf(l.actor), CommandDescriber.area(l.command), CommandDescriber.describe(l.command, names), CommandDescriber.movesMoney(l.command),
    )

    /**
     * What was done for [company], newest first. Only commands that act for the company are kept, and only those
     * from [origins]; at most [limit] entries are returned.
     */
    fun of(
        log: List<LoggedCommand>,
        company: CompanyId,
        names: DescribeNames = DescribeNames.PLAIN,
        origins: Set<Origin> = setOf(Origin.PLAYER),
        limit: Int = 200,
    ): List<ActivityEntry> {
        val out = ArrayList<ActivityEntry>()
        for (i in log.indices.reversed()) {
            val l = log[i]
            if (l.command.company != company) continue
            if (originOf(l.actor) !in origins) continue
            out += entry(l, names)
            if (out.size >= limit) break
        }
        return out
    }

    fun inArea(entries: List<ActivityEntry>, area: ActivityArea?): List<ActivityEntry> =
        if (area == null) entries else entries.filter { it.area == area }

    /** Areas that appear in the list, most frequent first. */
    fun areasIn(entries: List<ActivityEntry>): List<ActivityArea> =
        entries.groupingBy { it.area }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<ActivityArea, Int>> { it.value }.thenBy { it.key.ordinal }).map { it.key }

    /** Entries grouped by the day they happened, newest day first. */
    fun byDay(entries: List<ActivityEntry>): List<Pair<Long, List<ActivityEntry>>> {
        val days = java.util.TreeMap<Long, MutableList<ActivityEntry>>(Comparator.reverseOrder())
        for (e in entries) days.getOrPut(e.tick) { ArrayList() } += e
        return days.map { it.key to it.value.toList() }
    }
}
