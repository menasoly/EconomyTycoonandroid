package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.session.Formatting

enum class DiffKind(val symbol: String) { ADDED("+"), REMOVED("-"), CHANGED("~") }

/** One difference between two configurations. [section] groups entries for display; [path] locates it. */
data class DiffEntry(val section: String, val path: String, val kind: DiffKind, val before: String?, val after: String?) {
    val text: String
        get() = when (kind) {
            DiffKind.ADDED -> "$path: $after"
            DiffKind.REMOVED -> "$path: was $before"
            DiffKind.CHANGED -> "$path: $before -> $after"
        }
}

data class BlueprintDiff(val entries: List<DiffEntry>) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val sections: List<String> get() = entries.map { it.section }.distinct()
    fun inSection(section: String) = entries.filter { it.section == section }
    fun count(kind: DiffKind) = entries.count { it.kind == kind }

    /** One-line summary such as `3 changes: 1 added, 2 changed`. */
    val summary: String
        get() {
            if (entries.isEmpty()) return "No differences."
            val parts = DiffKind.values().filter { count(it) > 0 }.joinToString(", ") { "${count(it)} ${it.name.lowercase()}" }
            return "${entries.size} ${if (entries.size == 1) "difference" else "differences"}: $parts"
        }

    companion object { val NONE = BlueprintDiff(emptyList()) }
}

/** Compares two configurations slot by slot. Names come from [content] when given, ids otherwise. */
class BlueprintDiffer(private val content: ContentRepository? = null) {
    fun diff(a: BlueprintSpec, b: BlueprintSpec): BlueprintDiff {
        val x = a.normalized()
        val y = b.normalized()
        val out = ArrayList<DiffEntry>()
        if (x.facilityType != y.facilityType) out += change("Facility", "type", facilityName(x), facilityName(y))
        lines(x, y, out)
        if (x.policy != y.policy) out += change("Schedule", "policy", x.policy.name.lowercase().replace('_', ' '), y.policy.name.lowercase().replace('_', ' '))
        if (x.qualityPpm != y.qualityPpm) out += change("Schedule", "quality assurance", pct(x.qualityPpm), pct(y.qualityPpm))
        if (x.storageExpansions != y.storageExpansions) out += change("Storage", "expansions", x.storageExpansions.toString(), y.storageExpansions.toString())
        staff(x, y, out)
        sells(x, y, out)
        buys(x, y, out)
        return BlueprintDiff(out)
    }

    private fun lines(x: BlueprintSpec, y: BlueprintSpec, out: MutableList<DiffEntry>) {
        for (i in 0 until maxOf(x.lines.size, y.lines.size)) {
            val p = "line ${i + 1}"
            val l = x.lines.getOrNull(i)
            val r = y.lines.getOrNull(i)
            when {
                l == null && r != null -> out += DiffEntry("Lines", p, DiffKind.ADDED, null, describe(r))
                l != null && r == null -> out += DiffEntry("Lines", p, DiffKind.REMOVED, describe(l), null)
                l != null && r != null -> {
                    if (l.recipe != r.recipe) out += change("Lines", "$p recipe", recipeName(l), recipeName(r))
                    if (l.enabled != r.enabled) out += change("Lines", "$p switch", if (l.enabled) "on" else "off", if (r.enabled) "on" else "off")
                    for (m in r.machines - l.machines.toSet()) out += DiffEntry("Machines", "$p machine", DiffKind.ADDED, null, machineName(m.key))
                    for (m in l.machines - r.machines.toSet()) out += DiffEntry("Machines", "$p machine", DiffKind.REMOVED, machineName(m.key), null)
                }
            }
        }
    }

    private fun staff(x: BlueprintSpec, y: BlueprintSpec, out: MutableList<DiffEntry>) {
        val l = x.staff.associateBy { it.role }
        val r = y.staff.associateBy { it.role }
        for (role in (l.keys + r.keys).sorted()) {
            val p = "staff ${roleName(role.key)}"
            val a = l[role]; val b = r[role]
            when {
                a == null && b != null -> out += DiffEntry("Staffing", p, DiffKind.ADDED, null, staffText(b))
                a != null && b == null -> out += DiffEntry("Staffing", p, DiffKind.REMOVED, staffText(a), null)
                a != null && b != null && a != b -> out += change("Staffing", p, staffText(a), staffText(b))
            }
        }
    }

    private fun sells(x: BlueprintSpec, y: BlueprintSpec, out: MutableList<DiffEntry>) {
        val l = x.sells.associateBy { it.good }
        val r = y.sells.associateBy { it.good }
        for (g in (l.keys + r.keys).sorted()) {
            val p = "sell ${goodName(g.key)}"
            val a = l[g]; val b = r[g]
            when {
                a == null && b != null -> out += DiffEntry("Selling", p, DiffKind.ADDED, null, sellText(b))
                a != null && b == null -> out += DiffEntry("Selling", p, DiffKind.REMOVED, sellText(a), null)
                a != null && b != null && a != b -> out += change("Selling", p, sellText(a), sellText(b))
            }
        }
    }

    private fun buys(x: BlueprintSpec, y: BlueprintSpec, out: MutableList<DiffEntry>) {
        val l = x.buys.associateBy { it.good }
        val r = y.buys.associateBy { it.good }
        for (g in (l.keys + r.keys).sorted()) {
            val p = "buy ${goodName(g.key)}"
            val a = l[g]; val b = r[g]
            when {
                a == null && b != null -> out += DiffEntry("Buying", p, DiffKind.ADDED, null, buyText(b))
                a != null && b == null -> out += DiffEntry("Buying", p, DiffKind.REMOVED, buyText(a), null)
                a != null && b != null && a != b -> out += change("Buying", p, buyText(a), buyText(b))
            }
        }
    }

    private fun change(section: String, path: String, before: String, after: String) = DiffEntry(section, path, DiffKind.CHANGED, before, after)

    private fun describe(l: LineSpec): String {
        val ms = if (l.machines.isEmpty()) "" else " with " + l.machines.joinToString(", ") { machineName(it.key) }
        return recipeName(l) + ms + if (l.enabled) "" else " (off)"
    }

    private fun recipeName(l: LineSpec): String = l.recipe?.let { r -> content?.recipeOrNull(r)?.name ?: r.key } ?: "no recipe"
    private fun machineName(key: String) = content?.allMachineTypes?.firstOrNull { it.id.key == key }?.name ?: key
    private fun roleName(key: String) = content?.allRoles?.firstOrNull { it.id.key == key }?.name ?: key
    private fun goodName(key: String) = content?.allGoods?.firstOrNull { it.id.key == key }?.name ?: key
    private fun facilityName(s: BlueprintSpec) = content?.facilityTypeOrNull(s.facilityType)?.name ?: s.facilityType.key
    private fun pct(ppm: Long) = "${ppm / 10_000}%"
    private fun qty(q: Quantity) = Formatting.grouped(q.wholeUnits())
    private fun staffText(s: StaffSpec) = buildString {
        append(s.target)
        s.wage?.let { append(" at ").append(Formatting.money(it)) }
        if (s.trainingPpm > 0) append(", training ").append(pct(s.trainingPpm))
    }
    private fun sellText(s: SellSpec) = buildString {
        append(s.strategy.name.lowercase().replace('_', ' ')).append(' ').append(signedPct(s.markupPpm)).append(", keep ").append(qty(s.reserve))
        s.maxPerOrder?.let { append(", max ").append(qty(it)) }
        if (!s.enabled) append(" (off)")
    }
    private fun buyText(b: BuySpec) = "stock ${qty(b.target)} up to ${b.maxPriceMultiplePpm / 10_000}% of market" + if (b.enabled) "" else " (off)"
    private fun signedPct(ppm: Long) = (if (ppm >= 0) "+" else "") + "${ppm / 10_000}%"
}
