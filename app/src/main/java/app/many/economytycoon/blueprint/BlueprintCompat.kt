package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.ContentVersion
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId

/** Old id to new id, per kind of content, for things that were renamed between content versions. */
data class RenameTable(
    val facilities: Map<String, String> = emptyMap(),
    val recipes: Map<String, String> = emptyMap(),
    val machines: Map<String, String> = emptyMap(),
    val roles: Map<String, String> = emptyMap(),
    val goods: Map<String, String> = emptyMap(),
) {
    companion object { val NONE = RenameTable() }
}

enum class Compatibility(val label: String) {
    /** Everything in the blueprint exists as written. */
    COMPATIBLE("Up to date"),
    /** Renames or version drift only; the migrated blueprint matches the original's meaning. */
    MIGRATABLE("Needs updating"),
    /** Some parts no longer exist; the migrated blueprint leaves them out. */
    PARTIAL("Partly out of date"),
    /** The kind of facility itself is gone. */
    BROKEN("No longer usable"),
}

enum class NoteKind { RENAMED, REMOVED, VERSION }

data class MigrationNote(val kind: NoteKind, val text: String)

data class CompatibilityReport(
    val status: Compatibility,
    val notes: List<MigrationNote>,
    /** The blueprint with renames applied and missing parts left out; null when [status] is [Compatibility.BROKEN]. */
    val migrated: Blueprint?,
) {
    val isUsable: Boolean get() = status != Compatibility.BROKEN
    fun notes(kind: NoteKind) = notes.filter { it.kind == kind }
}

/** Checks a blueprint against the content now loaded and writes human-readable migration notes. */
class CompatibilityChecker(private val content: ContentRepository, private val renames: RenameTable = RenameTable.NONE) {

    fun check(bp: Blueprint): CompatibilityReport {
        val notes = ArrayList<MigrationNote>()
        versionNotes(bp.contentTag, notes)
        val type = resolve(bp.spec.facilityType.key, renames.facilities) { content.facilityTypeOrNull(FacilityTypeId(it)) != null }
        if (type.second == null) {
            notes += MigrationNote(NoteKind.REMOVED, "The facility type '${bp.spec.facilityType.key}' no longer exists, so this blueprint cannot be used.")
            return CompatibilityReport(Compatibility.BROKEN, notes, null)
        }
        type.first?.let { notes += renamed("facility type", bp.spec.facilityType.key, it) }
        var removed = false

        val lines = bp.spec.lines.mapIndexed { i, l ->
            val recipe = l.recipe?.let { r ->
                val res = resolve(r.key, renames.recipes) { content.recipeOrNull(RecipeId(it)) != null }
                when {
                    res.second == null -> { removed = true; notes += MigrationNote(NoteKind.REMOVED, "Line ${i + 1}: recipe '${r.key}' no longer exists; the line is left without a recipe."); null }
                    else -> { res.first?.let { notes += renamed("recipe", r.key, it) }; RecipeId(res.second ?: r.key) }
                }
            }
            val machines = l.machines.mapNotNull { m ->
                val res = resolve(m.key, renames.machines) { content.machineTypeOrNull(MachineTypeId(it)) != null }
                when {
                    res.second == null -> { removed = true; notes += MigrationNote(NoteKind.REMOVED, "Line ${i + 1}: machine '${m.key}' no longer exists and is left out."); null }
                    else -> { res.first?.let { notes += renamed("machine", m.key, it) }; MachineTypeId(res.second ?: m.key) }
                }
            }
            l.copy(recipe = recipe, machines = machines)
        }
        val staff = bp.spec.staff.mapNotNull { s ->
            val res = resolve(s.role.key, renames.roles) { content.allRoles.any { r -> r.id.key == it } }
            if (res.second == null) { removed = true; notes += MigrationNote(NoteKind.REMOVED, "Role '${s.role.key}' no longer exists; its staffing target is left out."); null }
            else { res.first?.let { notes += renamed("role", s.role.key, it) }; s.copy(role = RoleId(res.second ?: s.role.key)) }
        }
        val sells = bp.spec.sells.mapNotNull { s ->
            val g = good(s.good.key, notes) ?: run { removed = true; notes += MigrationNote(NoteKind.REMOVED, "Good '${s.good.key}' no longer exists; its selling rule is left out."); null }
            g?.let { s.copy(good = it) }
        }
        val buys = bp.spec.buys.mapNotNull { b ->
            val g = good(b.good.key, notes) ?: run { removed = true; notes += MigrationNote(NoteKind.REMOVED, "Good '${b.good.key}' no longer exists; its buying rule is left out."); null }
            g?.let { b.copy(good = it) }
        }
        val spec = bp.spec.copy(facilityType = FacilityTypeId(type.second ?: bp.spec.facilityType.key), lines = lines, staff = staff, sells = sells, buys = buys).normalized()
        val changed = spec != bp.spec.normalized()
        val status = when {
            removed -> Compatibility.PARTIAL
            changed || notes.isNotEmpty() -> Compatibility.MIGRATABLE
            else -> Compatibility.COMPATIBLE
        }
        val migrated = if (status == Compatibility.COMPATIBLE) bp else bp.copy(spec = spec, contentTag = currentTag())
        return CompatibilityReport(status, notes, migrated)
    }

    /** The tag a blueprint captured now carries, e.g. `base@1.4,food@2.0`. */
    fun currentTag(): String = tagOf(content)

    private fun good(key: String, notes: MutableList<MigrationNote>): GoodId? {
        val viaTable = renames.goods[key]
        val start = viaTable ?: key
        val resolved = content.goodOrNull(GoodId(start))?.id ?: return null
        if (resolved.key != key) notes += renamed("good", key, resolved.key)
        return resolved
    }

    private fun renamed(kind: String, old: String, new: String) = MigrationNote(NoteKind.RENAMED, "The $kind '$old' is now called '$new'.")

    /** Follows renames until an id that exists; first is the final id when it changed, second the id found or null. */
    private fun resolve(key: String, table: Map<String, String>, exists: (String) -> Boolean): Pair<String?, String?> {
        var cur = key
        var hops = 0
        while (!exists(cur) && hops < 8) { cur = table[cur] ?: return null to null; hops++ }
        if (!exists(cur)) return null to null
        return (cur.takeIf { it != key }) to cur
    }

    private fun versionNotes(tag: String, notes: MutableList<MigrationNote>) {
        if (tag.isBlank()) return
        val made = parseTag(tag)
        val now = content.versions.associateBy { it.packId }
        for (v in made) {
            val cur = now[v.packId]
            when {
                cur == null -> notes += MigrationNote(NoteKind.VERSION, "Made with the content pack '${v.packId}', which is not loaded.")
                v < cur -> notes += MigrationNote(NoteKind.VERSION, "Made for ${v.packId} ${v.major}.${v.minor}; the game now has ${cur.major}.${cur.minor}.")
                v > cur -> notes += MigrationNote(NoteKind.VERSION, "Made for ${v.packId} ${v.major}.${v.minor}, which is newer than the installed ${cur.major}.${cur.minor}; some parts may be missing.")
            }
        }
    }

    companion object {
        fun tagOf(content: ContentRepository): String = content.versions.joinToString(",") { "${it.packId}@${it.major}.${it.minor}" }

        /** Reads `pack@1.4,other@2.0`; entries that do not parse are skipped. */
        fun parseTag(tag: String): List<ContentVersion> = tag.split(',').mapNotNull { part ->
            val (id, ver) = part.trim().split('@', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val nums = ver.split('.')
            val major = nums.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val minor = nums.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (id.isBlank()) null else ContentVersion(id, major, minor)
        }
    }
}
