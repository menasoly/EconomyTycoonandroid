package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules

/** A draft kept in the library with the author's own filing. [created] and [updated] count library changes, not time. */
data class LibraryEntry(
    val id: Long,
    val draft: ScenarioDraft,
    val folder: String = "",
    val tags: List<String> = emptyList(),
    val note: String = "",
    val starred: Boolean = false,
    val created: Long = 0L,
    val updated: Long = 0L,
)

/** What reading a bundle into the library gave: the new entries, what went wrong, and what was merely odd. */
data class BundleImport(val added: List<Long>, val problems: List<String>, val warnings: List<String>)

enum class LibrarySort(val label: String) { RECENT("Recently changed"), OLDEST("Oldest first"), TITLE("Title"), DIFFICULTY("Difficulty") }

data class LibraryQuery(
    val text: String = "",
    /** Only this folder and the ones inside it; null for all. */
    val folder: String? = null,
    val tag: String? = null,
    val difficulty: DifficultyTag? = null,
    val starredOnly: Boolean = false,
    val sort: LibrarySort = LibrarySort.RECENT,
)

/**
 * The author's shelf of drafts. Entries live in folders (a slash makes a folder inside a folder), carry free tags and a
 * note, and can be searched. Identifiers within the shelf are unique, and so are scenario ids, so the shelf can be
 * exported whole without two entries claiming the same scenario.
 */
class WorkshopLibrary(private val capacity: Int = MAX_ENTRIES) {
    private val items = java.util.TreeMap<Long, LibraryEntry>()
    private var nextId = 1L
    private var clock = 0L

    val size: Int get() = items.size
    fun all(): List<LibraryEntry> = items.values.toList()
    fun get(id: Long): LibraryEntry? = items[id]
    fun byScenarioId(scenarioId: String): LibraryEntry? = items.values.firstOrNull { it.draft.id == scenarioId }
    internal fun clockValue(): Long = clock

    private fun tick(): Long = ++clock

    /** A scenario id based on [base] that no entry uses: `base`, then `base-2`, `base-3`. */
    fun uniqueId(base: String): String {
        if (byScenarioId(base) == null) return base
        var n = 2
        while (byScenarioId("$base-$n") != null) n++
        return "$base-$n"
    }

    fun add(draft: ScenarioDraft, folder: String = "", tags: List<String> = emptyList(), note: String = ""): Outcome<Long> {
        if (items.size >= capacity) return Outcome.reject(Rules.LIMIT_EXCEEDED, "the library holds at most $capacity drafts")
        checkFolder(folder)?.let { return it }
        cleanTags(tags).let { if (it is Outcome.Rejected) return it }
        if (byScenarioId(draft.id) != null) return Outcome.reject(Rules.DUPLICATE, "the library already has a draft with the id '${draft.id}'")
        if (note.length > MAX_NOTE) return Outcome.reject(Rules.INVALID_ARGUMENT, "a note holds at most $MAX_NOTE characters")
        val id = nextId++
        val now = tick()
        items[id] = LibraryEntry(id, draft.normalised(), folder, (cleanTags(tags) as Outcome.Ok).value, note, false, now, now)
        return Outcome.ok(id)
    }

    /** Replaces the draft of an entry, keeping its filing. */
    fun update(id: Long, draft: ScenarioDraft): Outcome<LibraryEntry> {
        val e = items[id] ?: return unknown(id)
        val clash = byScenarioId(draft.id)
        if (clash != null && clash.id != id) return Outcome.reject(Rules.DUPLICATE, "the library already has a draft with the id '${draft.id}'")
        return Outcome.ok(e.copy(draft = draft.normalised(), updated = tick()).also { items[id] = it })
    }

    fun remove(id: Long): Boolean = items.remove(id) != null

    fun move(id: Long, folder: String): Outcome<LibraryEntry> {
        val e = items[id] ?: return unknown(id)
        checkFolder(folder)?.let { return it }
        return Outcome.ok(e.copy(folder = folder, updated = tick()).also { items[id] = it })
    }

    fun setNote(id: Long, note: String): Outcome<LibraryEntry> {
        val e = items[id] ?: return unknown(id)
        if (note.length > MAX_NOTE) return Outcome.reject(Rules.INVALID_ARGUMENT, "a note holds at most $MAX_NOTE characters")
        return Outcome.ok(e.copy(note = note, updated = tick()).also { items[id] = it })
    }

    fun star(id: Long, starred: Boolean): Outcome<LibraryEntry> {
        val e = items[id] ?: return unknown(id)
        if (e.starred == starred) return Outcome.ok(e)
        return Outcome.ok(e.copy(starred = starred, updated = tick()).also { items[id] = it })
    }

    fun tag(id: Long, tag: String): Outcome<LibraryEntry> {
        val e = items[id] ?: return unknown(id)
        val t = normaliseTag(tag) ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "a tag is one to $MAX_TAG_LENGTH letters, digits or dashes")
        if (t in e.tags) return Outcome.ok(e)
        if (e.tags.size >= MAX_TAGS) return Outcome.reject(Rules.LIMIT_EXCEEDED, "an entry holds at most $MAX_TAGS tags")
        return Outcome.ok(e.copy(tags = (e.tags + t).sorted(), updated = tick()).also { items[id] = it })
    }

    fun untag(id: Long, tag: String): Outcome<LibraryEntry> {
        val e = items[id] ?: return unknown(id)
        val t = normaliseTag(tag) ?: return Outcome.ok(e)
        if (t !in e.tags) return Outcome.ok(e)
        return Outcome.ok(e.copy(tags = e.tags - t, updated = tick()).also { items[id] = it })
    }

    /** Puts a copy of an entry next to it under a fresh scenario id and a title marked as a copy. */
    fun duplicate(id: Long): Outcome<Long> {
        val e = items[id] ?: return unknown(id)
        val newId = uniqueId(e.draft.id.take(36) + "-copy")
        val title = (e.draft.title + " copy").take(DraftLimits.TITLE.last)
        return add(e.draft.copy(id = newId, title = title), e.folder, e.tags, e.note)
    }

    /** Renames a folder and everything inside it; entries in [from] move to [to]. Returns how many entries moved. */
    fun renameFolder(from: String, to: String): Outcome<Int> {
        checkFolder(to)?.let { return it }
        if (from.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "the top level cannot be renamed")
        var moved = 0
        for (e in items.values.toList()) {
            val inside = e.folder == from || e.folder.startsWith("$from/")
            if (!inside) continue
            val folder = to + e.folder.removePrefix(from)
            if (folder.length > MAX_FOLDER) return Outcome.reject(Rules.INVALID_ARGUMENT, "the new name makes a folder path too long")
            items[e.id] = e.copy(folder = folder, updated = tick())
            moved++
        }
        return Outcome.ok(moved)
    }

    /** Folders in use with the number of entries directly in each, in name order; the top level is the empty name. */
    fun folders(): Map<String, Int> = items.values.groupingBy { it.folder }.eachCount().toSortedMap()

    /** Every tag in use with how many entries carry it, in name order. */
    fun tags(): Map<String, Int> = items.values.flatMap { it.tags }.groupingBy { it }.eachCount().toSortedMap()

    fun search(q: LibraryQuery = LibraryQuery()): List<LibraryEntry> {
        val words = q.text.lowercase().split(' ', '\t').filter { it.isNotEmpty() }
        val hits = items.values.filter { e ->
            (q.folder == null || e.folder == q.folder || e.folder.startsWith(q.folder + "/")) &&
                (q.tag == null || normaliseTag(q.tag) in e.tags) &&
                (q.difficulty == null || q.difficulty in e.draft.tags) &&
                (!q.starredOnly || e.starred) &&
                words.all { w -> haystack(e).contains(w) }
        }
        return when (q.sort) {
            LibrarySort.RECENT -> hits.sortedWith(compareByDescending<LibraryEntry> { it.updated }.thenBy { it.id })
            LibrarySort.OLDEST -> hits.sortedWith(compareBy<LibraryEntry> { it.created }.thenBy { it.id })
            LibrarySort.TITLE -> hits.sortedWith(compareBy<LibraryEntry> { it.draft.title.lowercase() }.thenBy { it.id })
            LibrarySort.DIFFICULTY -> hits.sortedWith(compareBy<LibraryEntry> { difficultyRank(it.draft) }.thenBy { it.draft.title.lowercase() }.thenBy { it.id })
        }
    }

    private fun haystack(e: LibraryEntry): String =
        listOf(e.draft.title, e.draft.tagline, e.draft.id, e.draft.story, e.folder, e.note, e.tags.joinToString(" "), e.draft.tags.joinToString(" ") { it.key }).joinToString("\n").lowercase()

    /** Order from gentlest to harshest by the author's own tags; untagged drafts sit in the middle. */
    private fun difficultyRank(d: ScenarioDraft): Int = when {
        DifficultyTag.GENTLE in d.tags || DifficultyTag.SANDBOX in d.tags -> 0
        DifficultyTag.HARSH in d.tags -> 3
        DifficultyTag.STANDARD in d.tags -> 1
        else -> 2
    }

    /** Reads a shared scenario into the library under a free id. */
    fun importShare(text: String, folder: String = ""): Outcome<Long> = when (val r = ShareCodec.decode(text)) {
        is ShareResult.Failed -> Outcome.reject(Rules.INVALID_ARGUMENT, r.error.toString())
        is ShareResult.Decoded -> add(r.draft.copy(id = uniqueId(r.draft.id)), folder)
    }

    fun exportShare(id: Long): Outcome<String> = items[id]?.let { Outcome.ok(ShareCodec.encode(it.draft)) } ?: unknown(id)

    /** One text holding the chosen entries, in the order given; entries that do not exist are skipped. */
    fun exportBundle(ids: List<Long>): Outcome<String> {
        val drafts = ids.distinct().mapNotNull { items[it]?.draft }
        if (drafts.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "there is nothing to put in the bundle")
        if (drafts.size > ShareBundle.MAX_SCENARIOS) return Outcome.reject(Rules.LIMIT_EXCEEDED, "a bundle holds at most ${ShareBundle.MAX_SCENARIOS} scenarios")
        return Outcome.ok(ShareBundle.encode(drafts))
    }

    /** Files every readable scenario of a bundle; those that cannot be read or do not fit are reported, not fatal. */
    fun importBundle(text: String, folder: String = ""): BundleImport {
        val read = ShareBundle.decode(text)
        val added = ArrayList<Long>(); val problems = read.failures.map { it.toString() }.toMutableList()
        for (d in read.drafts) {
            when (val r = add(d.copy(id = uniqueId(d.id)), folder)) {
                is Outcome.Ok -> added += r.value
                is Outcome.Rejected -> problems += "'${d.id}': ${r.rejection.message}"
            }
        }
        return BundleImport(added, problems, read.warnings)
    }

    // ---- persistence hooks (used by the codec) ----------------------------------------------------

    internal fun restore(entries: List<LibraryEntry>, next: Long, clockNow: Long) {
        items.clear()
        for (e in entries) items[e.id] = e
        nextId = maxOf(next, (items.keys.maxOrNull() ?: 0L) + 1L)
        clock = maxOf(clockNow, items.values.maxOfOrNull { it.updated } ?: 0L)
    }

    internal fun nextIdValue(): Long = nextId

    private fun <T> unknown(id: Long): Outcome<T> = Outcome.reject(Rules.UNKNOWN_ENTITY, "there is no library entry $id")

    private fun checkFolder(folder: String): Outcome.Rejected? {
        if (folder.length > MAX_FOLDER) return Outcome.Rejected(app.many.economytycoon.core.Rejection(Rules.INVALID_ARGUMENT, "a folder path holds at most $MAX_FOLDER characters"))
        if (folder.startsWith("/") || folder.endsWith("/") || folder.contains("//") || folder.any { it.code < 0x20 }) {
            return Outcome.Rejected(app.many.economytycoon.core.Rejection(Rules.INVALID_ARGUMENT, "'$folder' is not a folder path; use names separated by single slashes"))
        }
        return null
    }

    private fun cleanTags(tags: List<String>): Outcome<List<String>> {
        if (tags.size > MAX_TAGS) return Outcome.reject(Rules.LIMIT_EXCEEDED, "an entry holds at most $MAX_TAGS tags")
        val out = ArrayList<String>()
        for (t in tags) out += normaliseTag(t) ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "'$t' is not a tag; use one to $MAX_TAG_LENGTH letters, digits or dashes")
        return Outcome.ok(out.distinct().sorted())
    }

    companion object {
        const val MAX_ENTRIES = 200
        const val MAX_NOTE = 400
        const val MAX_TAGS = 8
        const val MAX_TAG_LENGTH = 20
        const val MAX_FOLDER = 60

        fun normaliseTag(tag: String): String? = tag.trim().lowercase().takeIf { it.matches(Regex("[a-z0-9-]{1,$MAX_TAG_LENGTH}")) }
    }
}
