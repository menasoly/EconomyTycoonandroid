package app.many.economytycoon.blueprint

import app.many.economytycoon.core.FacilityTypeId

/** One saved blueprint with the player's own organisation of it. */
data class LibraryEntry(
    val id: BlueprintId,
    val blueprint: Blueprint,
    /** Slash-separated folder path; empty for the top level. */
    val folder: String,
    /** Lower-case, sorted, unique. */
    val tags: List<String>,
    val favourite: Boolean,
    /** In-game day it was added. */
    val addedDay: Long,
    /** The entry this was copied from with [BlueprintLibrary.duplicate]; null for originals. */
    val copyOf: BlueprintId?,
    /** Bumped each time the blueprint inside is replaced. */
    val revision: Int,
) {
    val name: String get() = blueprint.name
    val hash: String get() = blueprint.hash
}

/** How much a library may hold. */
data class LibraryLimits(
    val maxEntries: Int = 200,
    val maxFolders: Int = 40,
    val maxFolderDepth: Int = 3,
    val maxTagsPerEntry: Int = 8,
    val maxTotalBytes: Int = 512 * 1024,
) {
    companion object {
        const val MAX_SEGMENT = 24
        const val MAX_TAG = 20
    }
}

enum class SortKey(val label: String) { NAME("Name"), NEWEST("Newest first"), TYPE("Facility type"), LINES("Most lines"), FAVOURITES("Favourites first") }

/** A search over the library. Text is split into words that must all appear somewhere in an entry. */
data class BlueprintQuery(
    val text: String = "",
    /** Only entries in this folder or below; null searches everywhere. */
    val folder: String? = null,
    /** Entries must carry every one of these tags. */
    val tags: Set<String> = emptySet(),
    val favouritesOnly: Boolean = false,
    val facilityType: FacilityTypeId? = null,
    val sort: SortKey = SortKey.NAME,
)

enum class ImportStatus(val label: String) {
    IMPORTED("Added"), DUPLICATE("Already saved"), INVALID("Could not read"), REFUSED("Not added"),
}

/** One block of pasted text and what became of it. */
data class ImportRow(
    val index: Int,
    val status: ImportStatus,
    val name: String?,
    val message: String,
    val entry: BlueprintId?,
    val error: ParseError?,
    val notes: List<String> = emptyList(),
)

data class ImportReport(val rows: List<ImportRow>) {
    fun count(status: ImportStatus) = rows.count { it.status == status }
    val imported: Int get() = count(ImportStatus.IMPORTED)
    val failed: Int get() = count(ImportStatus.INVALID) + count(ImportStatus.REFUSED)
    val summary: String
        get() = when {
            rows.isEmpty() -> "No blueprint codes found."
            else -> buildString {
                append("$imported added")
                count(ImportStatus.DUPLICATE).takeIf { it > 0 }?.let { append(", $it already saved") }
                if (failed > 0) append(", $failed not added")
                append('.')
            }
        }
}

sealed class AddResult {
    data class Added(val entry: LibraryEntry) : AddResult()
    data class Duplicate(val existing: LibraryEntry) : AddResult()
    data class Refused(val rule: String, val message: String) : AddResult()
}
