package app.many.economytycoon.workshop

enum class ChangeKind(val sign: String) { ADDED("+"), REMOVED("-"), CHANGED("~") }

/** One difference between two drafts. [path] names the field the same way validation findings do. */
data class DraftChange(val path: String, val kind: ChangeKind, val before: String?, val after: String?) {
    override fun toString() = when (kind) {
        ChangeKind.ADDED -> "+ $path: $after"
        ChangeKind.REMOVED -> "- $path: $before"
        ChangeKind.CHANGED -> "~ $path: $before -> $after"
    }
}

/**
 * Reads two drafts as flat lists of named values and lists what differs. Lists are compared position by position, so
 * inserting a goal at the front shows as every later goal changing; that is how the editor numbers them too. The
 * result is in field order, never in hash order, so the same pair always gives the same text.
 */
object DraftDiff {
    fun changes(a: ScenarioDraft, b: ScenarioDraft): List<DraftChange> {
        val x = flatten(a.normalised()); val y = flatten(b.normalised())
        val out = ArrayList<DraftChange>()
        val order = LinkedHashSet<String>().also { it.addAll(x.keys); it.addAll(y.keys) }
        for (path in order.sortedWith(compareBy({ section(it) }, { it }))) {
            val before = x[path]; val after = y[path]
            when {
                before == null -> out += DraftChange(path, ChangeKind.ADDED, null, after)
                after == null -> out += DraftChange(path, ChangeKind.REMOVED, before, null)
                before != after -> out += DraftChange(path, ChangeKind.CHANGED, before, after)
            }
        }
        return out
    }

    fun lines(a: ScenarioDraft, b: ScenarioDraft): List<String> {
        val c = changes(a, b)
        return if (c.isEmpty()) listOf("no differences") else listOf("${c.size} differences") + c.map { it.toString() }
    }

    /** Whether a change touches what a player experiences, as opposed to the wording around it. */
    fun affectsPlay(c: DraftChange): Boolean = section(c.path) !in WORDING

    private val WORDING = setOf(0, 1)

    /** Wording first, then the setup, then rules, goals and the rest: the order an author thinks about a scenario. */
    private fun section(path: String): Int = when {
        path == "id" || path == "title" || path == "tagline" || path == "story" -> 0
        path.startsWith("hints") -> 1
        path == "seed" || path.startsWith("world") -> 2
        path.startsWith("company") -> 3
        path.startsWith("rules") -> 4
        path.startsWith("timing") -> 5
        path.startsWith("goals") -> 6
        path.startsWith("fails") -> 7
        path.startsWith("events") -> 8
        else -> 9
    }

    private fun flatten(d: ScenarioDraft): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["id"] = d.id; m["title"] = d.title; m["tagline"] = d.tagline; m["story"] = d.story; m["seed"] = d.seed.toString()
        m["world.size"] = d.world.size.name.lowercase(); m["world.rivals"] = d.world.rivals.toString(); m["world.country"] = d.world.countryIndex.toString()
        m["company.name"] = d.company.name; m["company.capital"] = d.company.capital.toString()
        d.company.starters.forEachIndexed { i, s -> m["company.starters[$i]"] = "${s.recipe} in region ${s.regionIndex}" }
        for ((k, v) in d.rules.entries()) m["rules.${k.key}"] = v.toString()
        m["timing.years"] = d.timing.years.toString(); m["timing.gold"] = d.timing.goldDays.toString(); m["timing.silver"] = d.timing.silverDays.toString()
        d.goals.forEachIndexed { i, g -> m["goals[$i]"] = "${g.kind.name.lowercase()} ${g.target} weight ${g.weight}" + if (g.bonus) " bonus" else "" }
        d.fails.forEachIndexed { i, f -> m["fails[$i]"] = "${f.kind.key} ${f.threshold} after day ${f.afterDay}" }
        d.events.forEachIndexed { i, e -> m["events[$i]"] = "day ${e.day} ${e.kind.key} ${e.amount}" + if (e.text.isEmpty()) "" else " \"${e.text}\"" }
        d.hints.forEachIndexed { i, h -> m["hints[$i]"] = h }
        m["tags"] = d.tags.joinToString(" ") { it.key }
        m["packs"] = d.packs.joinToString(" ")
        for (optional in listOf("tagline", "story", "tags", "packs")) if (m[optional] == "") m.remove(optional)
        return m
    }
}
