package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.BlueprintController
import app.many.economytycoon.blueprint.BlueprintId
import app.many.economytycoon.blueprint.BlueprintQuery
import app.many.economytycoon.blueprint.Compatibility
import app.many.economytycoon.blueprint.CompatibilityChecker
import app.many.economytycoon.blueprint.LibraryEntry
import app.many.economytycoon.blueprint.SortKey
import app.many.economytycoon.content.ContentRepository

data class BlueprintRow(
    val id: BlueprintId,
    val name: String,
    /** Facility kind, line count and machine count on one line. */
    val subtitle: String,
    /** The recipes it runs, comma separated; empty when no line has one. */
    val recipes: String,
    val folder: String,
    val tags: List<String>,
    val favourite: Boolean,
    val status: String,
    val statusTone: Tone,
    val isCopy: Boolean,
    val shortHash: String,
)

data class FolderRow(val path: String, val label: String, val depth: Int, val count: Int, val selected: Boolean)

data class BlueprintLibraryModel(
    val tiles: List<Tile>,
    val folders: List<FolderRow>,
    val tags: List<String>,
    val rows: List<BlueprintRow>,
    val sorts: List<SortKey>,
    val query: BlueprintQuery,
    val full: Boolean,
    val empty: String?,
    val warnings: List<String>,
)

/** The blueprint library list: search, folders, tags, favourites and a one-line state for each saved set-up. */
object BlueprintLibraryPresenter {
    const val EMPTY = "No blueprints yet. Open one of your facilities and save its set-up, or paste a code someone shared."
    const val NO_MATCH = "Nothing matches. Clear the search or pick another folder."

    fun statusTone(c: Compatibility): Tone = when (c) {
        Compatibility.COMPATIBLE -> Tone.GOOD
        Compatibility.MIGRATABLE -> Tone.INFO
        Compatibility.PARTIAL -> Tone.WARN
        Compatibility.BROKEN -> Tone.BAD
    }

    fun row(e: LibraryEntry, content: ContentRepository, compat: Compatibility): BlueprintRow {
        val spec = e.blueprint.spec
        val typeName = content.facilityTypeOrNull(spec.facilityType)?.name ?: spec.facilityType.key
        val machines = spec.machineCount
        val recipes = spec.lines.mapNotNull { it.recipe }.map { content.recipeOrNull(it)?.name ?: it.key }.distinct()
        return BlueprintRow(
            id = e.id, name = e.name,
            subtitle = "$typeName · ${plural(spec.lines.size, "line")}" + if (machines > 0) " · ${plural(machines, "machine")}" else "",
            recipes = recipes.joinToString(", "), folder = e.folder, tags = e.tags, favourite = e.favourite,
            status = compat.label, statusTone = statusTone(compat), isCopy = e.copyOf != null, shortHash = e.blueprint.shortHash,
        )
    }

    private fun plural(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

    fun folderRows(folders: List<String>, entries: List<LibraryEntry>, selected: String?): List<FolderRow> {
        val all = FolderRow("", "All blueprints", 0, entries.size, selected == null)
        val rows = folders.map { path ->
            val depth = path.count { it == '/' }
            FolderRow(path, path.substringAfterLast('/'), depth + 1, entries.count { it.folder == path || it.folder.startsWith("$path/") }, selected == path)
        }
        return listOf(all) + rows
    }

    /**
     * Builds the list model. [compat] gives the compatibility of each entry; entries it does not know count as up to date.
     */
    fun model(
        entries: List<LibraryEntry>,
        folders: List<String>,
        tags: List<String>,
        matches: List<LibraryEntry>,
        query: BlueprintQuery,
        content: ContentRepository,
        compat: (LibraryEntry) -> Compatibility,
        full: Boolean,
        warnings: List<String> = emptyList(),
    ): BlueprintLibraryModel {
        val rows = matches.map { row(it, content, compat(it)) }
        val outOfDate = entries.count { compat(it) != Compatibility.COMPATIBLE }
        val tiles = listOf(
            Tile("Saved", entries.size.toString(), note = "${entries.count { it.favourite }} favourites"),
            Tile("Folders", folders.size.toString()),
            Tile("Out of date", outOfDate.toString(), if (outOfDate > 0) Tone.WARN else Tone.NEUTRAL, note = if (outOfDate > 0) "Open one to see what changed" else null),
        )
        return BlueprintLibraryModel(
            tiles, folderRows(folders, entries, query.folder), tags, rows, SortKey.values().toList(), query, full,
            when { entries.isEmpty() -> EMPTY; rows.isEmpty() -> NO_MATCH; else -> null }, warnings,
        )
    }

    fun build(controller: BlueprintController, query: BlueprintQuery = BlueprintQuery()): BlueprintLibraryModel? {
        val host = controller.host() ?: return null
        val checker = CompatibilityChecker(host.content)
        val cache = HashMap<BlueprintId, Compatibility>()
        val compat = { e: LibraryEntry -> cache.getOrPut(e.id) { checker.check(e.blueprint).status } }
        val lib = controller.library
        val warnings = ArrayList<String>()
        controller.loadReport?.let {
            if (it.dropped > 0) warnings += "${plural(it.dropped, "saved blueprint")} could not be read and ${if (it.dropped == 1) "was" else "were"} left out."
            if (it.unreadable) warnings += "The saved library was unreadable, so it starts empty."
        }
        return model(lib.all(), lib.folders(), lib.tags(), lib.find(query), query, host.content, compat, lib.isFull, warnings)
    }
}
