package app.many.economytycoon.blueprint

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import java.util.TreeMap
import java.util.TreeSet

/**
 * The player's saved blueprints: folders, tags, favourites, search, import and export, with capacity limits.
 * Entries are unique by configuration (the canonical hash); [duplicate] is the one way to keep a deliberate copy.
 * Not thread safe; the owner calls it from the game thread like the other player stores.
 */
class BlueprintLibrary(val limits: LibraryLimits = LibraryLimits()) {
    private val entries = TreeMap<BlueprintId, LibraryEntry>()
    private val explicitFolders = TreeSet<String>()
    private var nextId = 1L

    val size: Int get() = entries.size
    val isFull: Boolean get() = entries.size >= limits.maxEntries

    fun get(id: BlueprintId): LibraryEntry? = entries[id]
    fun all(): List<LibraryEntry> = entries.values.toList()
    fun findByHash(hash: String): LibraryEntry? = entries.values.firstOrNull { it.copyOf == null && it.hash == hash } ?: entries.values.firstOrNull { it.hash == hash }

    /** Every folder in use, ancestors included, sorted. */
    fun folders(): List<String> {
        val out = TreeSet<String>()
        for (f in explicitFolders + entries.values.map { it.folder }) {
            var path = ""
            for (seg in f.split('/').filter { it.isNotEmpty() }) { path = if (path.isEmpty()) seg else "$path/$seg"; out += path }
        }
        return out.toList()
    }

    fun entriesIn(folder: String): List<LibraryEntry> = entries.values.filter { it.folder == folder }
    fun tags(): List<String> = entries.values.flatMap { it.tags }.distinct().sorted()

    // ---- adding ----------------------------------------------------------------------------------

    /**
     * Saves a blueprint. A configuration already in the library is not added again ([AddResult.Duplicate])
     * unless [allowDuplicate]; names are made unique within their folder.
     */
    fun add(
        blueprint: Blueprint, folder: String = "", tags: Collection<String> = emptyList(), favourite: Boolean = false,
        day: Long = 0L, allowDuplicate: Boolean = false,
    ): AddResult {
        findRoot(blueprint.hash)?.let { if (!allowDuplicate) return AddResult.Duplicate(it) }
        return insert(blueprint, folder, tags, favourite, day, copyOf = null)
    }

    private fun findRoot(hash: String) = entries.values.firstOrNull { it.copyOf == null && it.hash == hash }

    private fun insert(bp: Blueprint, folder: String, tags: Collection<String>, favourite: Boolean, day: Long, copyOf: BlueprintId?): AddResult {
        if (entries.size >= limits.maxEntries) return AddResult.Refused(Rules.LIMIT_EXCEEDED, "The library holds ${limits.maxEntries} blueprints; remove some first.")
        val path = normalizeFolder(folder) ?: return AddResult.Refused(Rules.INVALID_ARGUMENT, "That folder name is not allowed.")
        if (depth(path) > limits.maxFolderDepth) return AddResult.Refused(Rules.LIMIT_EXCEEDED, "Folders can be nested ${limits.maxFolderDepth} deep.")
        if (path !in folders() && path.isNotEmpty() && folders().size + newAncestors(path) > limits.maxFolders) {
            return AddResult.Refused(Rules.LIMIT_EXCEEDED, "The library holds ${limits.maxFolders} folders.")
        }
        val cleanTags = normalizeTags(tags) ?: return AddResult.Refused(Rules.INVALID_ARGUMENT, "A tag may use letters, digits, spaces, - and _ (up to ${LibraryLimits.MAX_TAG}).")
        if (cleanTags.size > limits.maxTagsPerEntry) return AddResult.Refused(Rules.LIMIT_EXCEEDED, "An entry holds ${limits.maxTagsPerEntry} tags.")
        val named = bp.copy(name = uniqueName(bp.name, path, null))
        if (totalBytes() + BlueprintCodec.encode(named).size > limits.maxTotalBytes) return AddResult.Refused(Rules.LIMIT_EXCEEDED, "The library is out of space.")
        val entry = LibraryEntry(BlueprintId(nextId++), named, path, cleanTags, favourite, day, copyOf, 1)
        entries[entry.id] = entry
        return AddResult.Added(entry)
    }

    private fun newAncestors(path: String): Int {
        val existing = folders().toSet()
        var p = ""
        var n = 0
        for (seg in path.split('/')) { p = if (p.isEmpty()) seg else "$p/$seg"; if (p !in existing) n++ }
        return n
    }

    private fun totalBytes(): Int = entries.values.sumOf { BlueprintCodec.encode(it.blueprint).size }

    private fun uniqueName(name: String, folder: String, except: BlueprintId?): String {
        val taken = entries.values.filter { it.folder == folder && it.id != except }.map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = name.take(BlueprintLimits.MAX_NAME - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in taken) return candidate
            n++
        }
    }

    /** Keeps a deliberate copy of an entry, next to it. The copy hashes like the original. */
    fun duplicate(id: BlueprintId, day: Long = 0L): Outcome<LibraryEntry> {
        val src = entries[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That blueprint is not in the library.")
        val suffix = " (copy)"
        val name = src.name.take(BlueprintLimits.MAX_NAME - suffix.length).trimEnd() + suffix
        val root = src.copyOf ?: src.id
        return when (val r = insert(src.blueprint.copy(name = name), src.folder, src.tags, false, day, root)) {
            is AddResult.Added -> Outcome.ok(r.entry)
            is AddResult.Refused -> Outcome.reject(r.rule, r.message)
            is AddResult.Duplicate -> Outcome.reject(Rules.DUPLICATE, "Already saved.")
        }
    }

    // ---- editing ---------------------------------------------------------------------------------

    fun remove(id: BlueprintId): Outcome<LibraryEntry> {
        val gone = entries.remove(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That blueprint is not in the library.")
        val copies = entries.values.filter { it.copyOf == id }.sortedBy { it.id }
        if (copies.isNotEmpty()) {
            val newRoot = copies.first()
            entries[newRoot.id] = newRoot.copy(copyOf = gone.copyOf)
            for (c in copies.drop(1)) entries[c.id] = c.copy(copyOf = gone.copyOf ?: newRoot.id)
        }
        return Outcome.ok(gone)
    }

    fun rename(id: BlueprintId, name: String): Outcome<LibraryEntry> = update(id) { e ->
        val n = name.trim()
        when {
            n.isEmpty() -> Outcome.reject(Rules.INVALID_ARGUMENT, "A blueprint needs a name.")
            n.length > BlueprintLimits.MAX_NAME -> Outcome.reject(Rules.INVALID_ARGUMENT, "Names are at most ${BlueprintLimits.MAX_NAME} characters.")
            entries.values.any { it.id != id && it.folder == e.folder && it.name.equals(n, ignoreCase = true) } -> Outcome.reject(Rules.NAME_TAKEN, "This folder already has a blueprint called '$n'.")
            else -> Outcome.ok(e.copy(blueprint = e.blueprint.renamed(n)))
        }
    }

    fun setNote(id: BlueprintId, note: String): Outcome<LibraryEntry> = update(id) { e ->
        if (note.trim().length > BlueprintLimits.MAX_NOTE) Outcome.reject(Rules.INVALID_ARGUMENT, "Notes are at most ${BlueprintLimits.MAX_NOTE} characters.")
        else Outcome.ok(e.copy(blueprint = e.blueprint.withNote(note)))
    }

    fun setFavourite(id: BlueprintId, favourite: Boolean): Outcome<LibraryEntry> = update(id) { Outcome.ok(it.copy(favourite = favourite)) }

    fun toggleFavourite(id: BlueprintId): Outcome<LibraryEntry> = update(id) { Outcome.ok(it.copy(favourite = !it.favourite)) }

    fun addTag(id: BlueprintId, tag: String): Outcome<LibraryEntry> = update(id) { e ->
        val t = normalizeTags(listOf(tag))
        when {
            t == null || t.isEmpty() -> Outcome.reject(Rules.INVALID_ARGUMENT, "A tag may use letters, digits, spaces, - and _ (up to ${LibraryLimits.MAX_TAG}).")
            t[0] in e.tags -> Outcome.ok(e)
            e.tags.size >= limits.maxTagsPerEntry -> Outcome.reject(Rules.LIMIT_EXCEEDED, "An entry holds ${limits.maxTagsPerEntry} tags.")
            else -> Outcome.ok(e.copy(tags = (e.tags + t[0]).sorted()))
        }
    }

    fun removeTag(id: BlueprintId, tag: String): Outcome<LibraryEntry> = update(id) { e -> Outcome.ok(e.copy(tags = e.tags - tag.trim().lowercase())) }

    fun move(id: BlueprintId, folder: String): Outcome<LibraryEntry> {
        val path = normalizeFolder(folder) ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "That folder name is not allowed.")
        if (depth(path) > limits.maxFolderDepth) return Outcome.reject(Rules.LIMIT_EXCEEDED, "Folders can be nested ${limits.maxFolderDepth} deep.")
        if (path.isNotEmpty() && path !in folders() && folders().size + newAncestors(path) > limits.maxFolders) return Outcome.reject(Rules.LIMIT_EXCEEDED, "The library holds ${limits.maxFolders} folders.")
        return update(id) { e ->
            if (e.folder == path) Outcome.ok(e)
            else Outcome.ok(e.copy(folder = path, blueprint = e.blueprint.copy(name = uniqueName(e.name, path, e.id))))
        }
    }

    /**
     * Swaps the blueprint inside an entry for a new one (an edited variant, say). The new configuration must not
     * already be saved as another original.
     */
    fun replace(id: BlueprintId, blueprint: Blueprint): Outcome<LibraryEntry> = update(id) { e ->
        val clash = entries.values.firstOrNull { it.id != id && it.copyOf == null && it.hash == blueprint.hash }
        if (clash != null && e.copyOf == null) Outcome.reject(Rules.DUPLICATE, "'${clash.name}' already has this configuration.")
        else Outcome.ok(e.copy(blueprint = blueprint.copy(name = uniqueName(blueprint.name, e.folder, id)), revision = e.revision + 1))
    }

    private fun update(id: BlueprintId, change: (LibraryEntry) -> Outcome<LibraryEntry>): Outcome<LibraryEntry> {
        val e = entries[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That blueprint is not in the library.")
        val out = change(e)
        if (out is Outcome.Ok) entries[id] = out.value
        return out
    }

    // ---- folders ---------------------------------------------------------------------------------

    fun createFolder(path: String): Outcome<String> {
        val p = normalizeFolder(path)?.takeIf { it.isNotEmpty() } ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "That folder name is not allowed.")
        if (depth(p) > limits.maxFolderDepth) return Outcome.reject(Rules.LIMIT_EXCEEDED, "Folders can be nested ${limits.maxFolderDepth} deep.")
        if (p in folders()) return Outcome.reject(Rules.DUPLICATE, "That folder already exists.")
        if (folders().size + newAncestors(p) > limits.maxFolders) return Outcome.reject(Rules.LIMIT_EXCEEDED, "The library holds ${limits.maxFolders} folders.")
        explicitFolders += p
        return Outcome.ok(p)
    }

    /** Renames a folder and everything below it. Entries whose names would clash get a numbered name. */
    fun renameFolder(from: String, to: String): Outcome<Int> {
        val src = normalizeFolder(from)?.takeIf { it.isNotEmpty() && it in folders() } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That folder does not exist.")
        val dst = normalizeFolder(to)?.takeIf { it.isNotEmpty() } ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "That folder name is not allowed.")
        if (dst == src) return Outcome.ok(0)
        if (dst.startsWith("$src/")) return Outcome.reject(Rules.INVALID_ARGUMENT, "A folder cannot move inside itself.")
        val moved = explicitFolders.filter { it == src || it.startsWith("$src/") }
        val inside = entries.values.filter { it.folder == src || it.folder.startsWith("$src/") }
        val deepest = (moved + inside.map { it.folder }).maxOfOrNull { depth(dst + it.removePrefix(src)) } ?: depth(dst)
        if (deepest > limits.maxFolderDepth) return Outcome.reject(Rules.LIMIT_EXCEEDED, "Folders can be nested ${limits.maxFolderDepth} deep.")
        explicitFolders.removeAll(moved.toSet())
        for (f in moved) explicitFolders += dst + f.removePrefix(src)
        for (e in inside) {
            val folder = dst + e.folder.removePrefix(src)
            entries[e.id] = e.copy(folder = folder, blueprint = e.blueprint.copy(name = uniqueName(e.name, folder, e.id)))
        }
        return Outcome.ok(inside.size)
    }

    /** Removes a folder; its blueprints move to [moveTo] (the top level by default). Returns how many moved. */
    fun deleteFolder(path: String, moveTo: String = ""): Outcome<Int> {
        val p = normalizeFolder(path)?.takeIf { it.isNotEmpty() && it in folders() } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That folder does not exist.")
        val dest = normalizeFolder(moveTo) ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "That folder name is not allowed.")
        if (dest == p || dest.startsWith("$p/")) return Outcome.reject(Rules.INVALID_ARGUMENT, "Choose a folder outside the one being deleted.")
        val inside = entries.values.filter { it.folder == p || it.folder.startsWith("$p/") }
        for (e in inside) entries[e.id] = e.copy(folder = dest, blueprint = e.blueprint.copy(name = uniqueName(e.name, dest, e.id)))
        explicitFolders.removeAll { it == p || it.startsWith("$p/") }
        return Outcome.ok(inside.size)
    }

    // ---- search ----------------------------------------------------------------------------------

    fun find(q: BlueprintQuery = BlueprintQuery()): List<LibraryEntry> {
        val words = q.text.lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }
        val folder = q.folder?.let { normalizeFolder(it) }
        val tags = q.tags.map { it.trim().lowercase() }
        val hits = entries.values.filter { e ->
            (folder == null || folder.isEmpty() || e.folder == folder || e.folder.startsWith("$folder/")) &&
                (tags.all { it in e.tags }) && (!q.favouritesOnly || e.favourite) &&
                (q.facilityType == null || e.blueprint.facilityType == q.facilityType) &&
                words.all { w -> haystack(e).contains(w) }
        }
        val byName = compareBy<LibraryEntry>({ it.name.lowercase() }, { it.id })
        return when (q.sort) {
            SortKey.NAME -> hits.sortedWith(byName)
            SortKey.NEWEST -> hits.sortedWith(compareBy<LibraryEntry>({ -it.addedDay }, { -it.id.value }))
            SortKey.TYPE -> hits.sortedWith(compareBy<LibraryEntry>({ it.blueprint.facilityType.key }).then(byName))
            SortKey.LINES -> hits.sortedWith(compareBy<LibraryEntry>({ -it.blueprint.spec.lines.size }).then(byName))
            SortKey.FAVOURITES -> hits.sortedWith(compareBy<LibraryEntry>({ !it.favourite }).then(byName))
        }
    }

    private fun haystack(e: LibraryEntry): String = buildString {
        append(e.name.lowercase()).append(' ').append(e.blueprint.note.lowercase()).append(' ').append(e.folder.lowercase()).append(' ')
        append(e.blueprint.facilityType.key).append(' ')
        e.tags.forEach { append(it).append(' ') }
        e.blueprint.spec.lines.forEach { l -> l.recipe?.let { append(it.key).append(' ') } }
    }

    // ---- sharing ---------------------------------------------------------------------------------

    /** All the given blueprints as pasteable text, one block per blueprint, separated by blank lines. */
    fun export(ids: Collection<BlueprintId>, encoding: TextEncoding = TextEncoding.BASE32, wrap: Int = 64): String =
        ids.distinct().sorted().mapNotNull { entries[it] }.joinToString("\n\n") { BlueprintText.encode(it.blueprint, encoding, wrap) }

    fun exportAll(encoding: TextEncoding = TextEncoding.BASE32, wrap: Int = 64): String = export(entries.keys, encoding, wrap)

    /** Reads every code in [text] and saves what is new. Nothing in the text can stop the other blocks from importing. */
    fun import(text: String, folder: String = "", day: Long = 0L, tags: Collection<String> = emptyList()): ImportReport {
        val rows = BlueprintText.parseAll(text).mapIndexed { i, r ->
            when (r) {
                is ParseResult.Failure -> ImportRow(i, ImportStatus.INVALID, null, "${r.error.message} (${r.error.where})", null, r.error)
                is ParseResult.Success -> when (val a = add(r.blueprint, folder, tags, false, day)) {
                    is AddResult.Added -> ImportRow(i, ImportStatus.IMPORTED, a.entry.name, "Added '${a.entry.name}'.", a.entry.id, null, r.notes)
                    is AddResult.Duplicate -> ImportRow(i, ImportStatus.DUPLICATE, r.blueprint.name, "Same set-up as '${a.existing.name}', which you already have.", a.existing.id, null, r.notes)
                    is AddResult.Refused -> ImportRow(i, ImportStatus.REFUSED, r.blueprint.name, a.message, null, null, r.notes)
                }
            }
        }
        return ImportReport(rows)
    }

    // ---- state -----------------------------------------------------------------------------------

    fun clear() { entries.clear(); explicitFolders.clear(); nextId = 1L }

    fun hashInto(h: CanonicalHasher) {
        h.section("blueprint.library").putLong(nextId).putInt(entries.size)
        for (e in entries.values) {
            h.putLong(e.id.value).putString(e.hash).putString(e.name).putString(e.blueprint.note).putString(e.blueprint.contentTag).putLong(e.blueprint.createdDay)
                .putString(e.folder).putBoolean(e.favourite).putLong(e.addedDay).putLong(e.copyOf?.value ?: -1L).putInt(e.revision).putInt(e.tags.size)
            e.tags.forEach { h.putString(it) }
        }
        h.putInt(explicitFolders.size)
        explicitFolders.forEach { h.putString(it) }
    }

    fun stateHash(): String = CanonicalHasher().also { hashInto(it) }.finish().toHex()

    // ---- persistence hooks (see LibraryCodec) ------------------------------------------------------

    internal fun nextIdValue() = nextId
    internal fun explicitFolderList(): List<String> = explicitFolders.toList()

    /** Restores an entry exactly as saved. */
    internal fun restore(e: LibraryEntry) {
        entries[e.id] = e
        nextId = maxOf(nextId, e.id.value + 1)
    }
    internal fun restoreFolder(path: String) { explicitFolders += path }
    internal fun restoreNextId(next: Long) { nextId = maxOf(nextId, next) }

    companion object {
        private val segment = Regex("[A-Za-z0-9 _-]{1,${LibraryLimits.MAX_SEGMENT}}")
        private val tagPattern = Regex("[a-z0-9 _-]{1,${LibraryLimits.MAX_TAG}}")

        /** Cleans a folder path; null when a segment uses characters or lengths that are not allowed. */
        fun normalizeFolder(raw: String): String? {
            val segs = raw.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
            if (segs.any { !segment.matches(it) }) return null
            return segs.joinToString("/")
        }

        fun depth(path: String): Int = if (path.isEmpty()) 0 else path.count { it == '/' } + 1

        /** Lower-cases and de-duplicates tags; null when one is not allowed. */
        fun normalizeTags(raw: Collection<String>): List<String>? {
            val out = raw.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted()
            return if (out.any { !tagPattern.matches(it) }) null else out
        }
    }
}
