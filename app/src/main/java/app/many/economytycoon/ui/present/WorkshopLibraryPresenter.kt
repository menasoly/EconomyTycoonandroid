package app.many.economytycoon.ui.present

import app.many.economytycoon.workshop.DifficultyTag
import app.many.economytycoon.workshop.LibraryEntry
import app.many.economytycoon.workshop.LibraryQuery
import app.many.economytycoon.workshop.LibrarySort
import app.many.economytycoon.workshop.WorkshopLibrary

data class LibraryRow(val id: Long, val title: String, val subtitle: String, val folder: String, val tags: List<String>, val starred: Boolean, val note: String)

data class FolderChip(val path: String?, val label: String, val count: Int, val selected: Boolean)

data class LibraryModel(val tiles: List<Tile>, val rows: List<LibraryRow>, val folders: List<FolderChip>, val tags: List<String>, val sorts: List<LibrarySort>, val empty: String?)

/** The shelf of saved drafts: filters on top, one row per draft. */
object WorkshopLibraryPresenter {
    const val EMPTY = "Nothing saved yet. Save a draft from the editor and it will be kept here."
    const val NO_MATCH = "No drafts match. Clear the search or the filters."

    fun row(e: LibraryEntry): LibraryRow {
        val d = e.draft
        val sub = listOf("${d.goals.size} goals", "${d.timing.years} years", d.tags.joinToString(", ") { it.label }).filter { it.isNotEmpty() }.joinToString(" - ")
        return LibraryRow(e.id, d.title, sub, e.folder, e.tags, e.starred, e.note)
    }

    fun model(lib: WorkshopLibrary, q: LibraryQuery): LibraryModel {
        val hits = lib.search(q)
        val folders = ArrayList<FolderChip>()
        folders += FolderChip(null, "All", lib.size, q.folder == null)
        for ((path, n) in lib.folders()) folders += FolderChip(path, path.ifEmpty { "Top level" }, n, q.folder == path)
        val tiles = listOf(
            Tile("Saved", lib.size.toString(), note = "of ${WorkshopLibrary.MAX_ENTRIES}"),
            Tile("Shown", hits.size.toString()),
            Tile("Starred", lib.all().count { it.starred }.toString()),
        )
        val empty = when {
            lib.size == 0 -> EMPTY
            hits.isEmpty() -> NO_MATCH
            else -> null
        }
        return LibraryModel(tiles, hits.map { row(it) }, folders, lib.tags().keys.toList(), LibrarySort.values().toList(), empty)
    }

    /** The query after the author taps a folder chip; tapping the chosen one again clears it. */
    fun withFolder(q: LibraryQuery, path: String?): LibraryQuery = q.copy(folder = if (q.folder == path) null else path)

    fun withTag(q: LibraryQuery, tag: String): LibraryQuery = q.copy(tag = if (q.tag == tag) null else tag)

    fun withDifficulty(q: LibraryQuery, tag: DifficultyTag): LibraryQuery = q.copy(difficulty = if (q.difficulty == tag) null else tag)

    fun filtersActive(q: LibraryQuery): Boolean = q.text.isNotBlank() || q.folder != null || q.tag != null || q.difficulty != null || q.starredOnly
}
