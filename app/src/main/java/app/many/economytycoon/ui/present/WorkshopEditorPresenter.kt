package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.workshop.DifficultyTag
import app.many.economytycoon.workshop.DraftEdit
import app.many.economytycoon.workshop.DraftEditor
import app.many.economytycoon.workshop.DraftGoal
import app.many.economytycoon.workshop.DraftTemplates
import app.many.economytycoon.workshop.EventKind
import app.many.economytycoon.workshop.FailCondition
import app.many.economytycoon.workshop.FailKind
import app.many.economytycoon.workshop.RuleKey
import app.many.economytycoon.workshop.ScenarioDraft
import app.many.economytycoon.workshop.ScriptedEvent
import app.many.economytycoon.workshop.Timing
import app.many.economytycoon.workshop.ValidationReport
import app.many.economytycoon.world.WorldSize

enum class FieldInput { TEXT, LONG_TEXT, NUMBER }

/** One editable value. [path] is the same name validation findings use, so a finding can be shown next to its field. */
data class FieldRow(val path: String, val label: String, val value: String, val input: FieldInput, val hint: String? = null, val problem: Tone? = null)

/** A line in a list section (a goal, an event) with the fields it is made of and the edit that removes it. */
data class ItemRow(val title: String, val fields: List<FieldRow>, val problem: Tone? = null)

data class EditorSection(val key: String, val title: String, val summary: String, val fields: List<FieldRow>, val items: List<ItemRow> = emptyList(), val canAdd: Boolean = false, val problem: Tone? = null)

data class EditorModel(
    val title: String,
    val tiles: List<Tile>,
    val sections: List<EditorSection>,
    val dirty: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val undoLabel: String?,
    val redoLabel: String?,
    val tags: List<TagChip>,
)

data class TagChip(val label: String, val selected: Boolean, val key: String)

data class TemplateRow(val key: String, val name: String, val summary: String)

/** Shows a draft as sections of fields and turns what the author types into edits. All checking of the values is the validator's job. */
object WorkshopEditorPresenter {
    fun templates(): List<TemplateRow> = DraftTemplates.all.map { TemplateRow(it.key, it.name, it.summary) }

    fun model(editor: DraftEditor, report: ValidationReport): EditorModel {
        val d = editor.draft
        val tiles = listOf(
            Tile("Goals", d.goals.size.toString(), note = "${d.requiredGoals.size} required"),
            Tile("Events", d.events.size.toString()),
            Tile("Time allowed", "${d.timing.years} years", note = "${d.deadlineDays} days"),
            Tile("Problems", report.errors.size.toString(), if (report.errors.isEmpty()) Tone.GOOD else Tone.BAD, if (report.warnings.isEmpty()) null else "${report.warnings.size} warnings"),
        )
        val chips = DifficultyTag.values().map { TagChip(it.label, it in d.tags, it.key) }
        return EditorModel(d.title, tiles, sections(d, report), editor.dirty, editor.canUndo, editor.canRedo, editor.undoLabel, editor.redoLabel, chips)
    }

    private fun f(report: ValidationReport, path: String, label: String, value: String, input: FieldInput = FieldInput.TEXT, hint: String? = null) =
        FieldRow(path, label, value, input, hint, WorkshopValidationPresenter.worstAt(report, path))

    fun sections(d: ScenarioDraft, r: ValidationReport): List<EditorSection> {
        fun worst(path: String) = WorkshopValidationPresenter.worstAt(r, path)
        val identity = EditorSection("identity", "Title and story", d.title.ifBlank { "untitled" }, listOf(
            f(r, "id", "Id", d.id, hint = "Lowercase letters, digits and dashes."),
            f(r, "title", "Title", d.title),
            f(r, "tagline", "Tagline", d.tagline),
            f(r, "story", "Story", d.story, FieldInput.LONG_TEXT),
            f(r, "seed", "Seed", d.seed.toString(), FieldInput.NUMBER, "The same seed always builds the same world."),
        ), problem = listOf("id", "title", "tagline", "story", "seed").mapNotNull { worst(it) }.firstOrNull())
        val world = EditorSection("world", "World", "${d.world.size.name.lowercase()} world, ${d.world.rivals} rivals", listOf(
            f(r, "world.rivals", "Rivals", d.world.rivals.toString(), FieldInput.NUMBER),
        ), problem = worst("world"))
        val company = EditorSection("company", "Starting company", "${d.company.name}, ${Formatting.grouped(d.company.capital)} capital", listOf(
            f(r, "company.name", "Company name", d.company.name),
            f(r, "company.capital", "Starting capital", d.company.capital.toString(), FieldInput.NUMBER),
        ), d.company.starters.mapIndexed { i, s ->
            ItemRow("${s.recipe} in region ${s.regionIndex + 1}", listOf(f(r, "company.starters[$i].recipe", "Recipe", s.recipe)), worst("company.starters[$i]"))
        }, canAdd = true, problem = worst("company"))
        val rules = EditorSection("rules", "Rules", if (d.rules.isEmpty()) "engine defaults" else "${d.rules.size} changed", RuleKey.values().map { k ->
            f(r, "rules.${k.key}", k.label, d.rules.valueOf(k).toString(), FieldInput.NUMBER, "Allowed ${Formatting.grouped(k.min)} to ${Formatting.grouped(k.max)}.")
        }, problem = worst("rules"))
        val timing = EditorSection("timing", "Time allowed", "${d.timing.years} years", listOf(
            f(r, "timing.years", "Years", d.timing.years.toString(), FieldInput.NUMBER),
            f(r, "timing.gold", "Gold medal by day", d.timing.goldDays.toString(), FieldInput.NUMBER),
            f(r, "timing.silver", "Silver medal by day", d.timing.silverDays.toString(), FieldInput.NUMBER),
        ), problem = worst("timing"))
        val goals = EditorSection("goals", "Goals", "${d.goals.size} goals", emptyList(), d.goals.mapIndexed { i, g ->
            ItemRow("${if (g.bonus) "Bonus: " else ""}${g.kind.noun}", listOf(
                f(r, "goals[$i].target", "Target", g.target.toString(), FieldInput.NUMBER),
                f(r, "goals[$i].weight", "Weight", g.weight.toString(), FieldInput.NUMBER),
            ), worst("goals[$i]"))
        }, canAdd = d.goals.size < app.many.economytycoon.workshop.DraftLimits.MAX_GOALS, problem = worst("goals"))
        val events = EditorSection("events", "Scripted events", "${d.events.size} events", emptyList(), d.events.mapIndexed { i, e ->
            ItemRow("Day ${e.day}: ${e.kind.label}", listOf(
                f(r, "events[$i].day", "Day", e.day.toString(), FieldInput.NUMBER),
                f(r, "events[$i].amount", "Amount", e.amount.toString(), FieldInput.NUMBER),
                f(r, "events[$i].text", "Message", e.text),
            ), worst("events[$i]"))
        }, canAdd = d.events.size < app.many.economytycoon.workshop.DraftLimits.MAX_EVENTS, problem = worst("events"))
        val fails = EditorSection("fails", "Ways to lose", "${d.fails.size} conditions", emptyList(), d.fails.mapIndexed { i, c ->
            ItemRow("${c.kind.label} ${Formatting.grouped(c.threshold)}", listOf(
                f(r, "fails[$i].threshold", "Threshold", c.threshold.toString(), FieldInput.NUMBER),
                f(r, "fails[$i].afterDay", "Not before day", c.afterDay.toString(), FieldInput.NUMBER),
            ), worst("fails[$i]"))
        }, canAdd = d.fails.size < app.many.economytycoon.workshop.DraftLimits.MAX_FAILS, problem = worst("fails"))
        val hints = EditorSection("hints", "Hints", "${d.hints.size} hints", emptyList(), d.hints.mapIndexed { i, h ->
            ItemRow("Hint ${i + 1}", listOf(f(r, "hints[$i]", "Text", h)), worst("hints[$i]"))
        }, canAdd = d.hints.size < app.many.economytycoon.workshop.DraftLimits.MAX_HINTS, problem = worst("hints"))
        return listOf(identity, world, company, rules, timing, goals, events, fails, hints)
    }

    // ---- typing becomes edits ---------------------------------------------------------------------

    private fun whole(text: String): Long? = text.trim().replace("_", "").replace(",", "").toLongOrNull()

    private val itemPath = Regex("([a-z]+)\\[(\\d+)]\\.([a-zA-Z]+)")

    /** Applies what was typed into the field at [path]. Returns why it could not be applied, or null. */
    fun setField(editor: DraftEditor, path: String, text: String): String? {
        val edit = editFor(editor.draft, path, text) ?: return "There is no field '$path' to change."
        if (edit is Failure) return edit.message
        return when (val r = editor.apply(edit as DraftEdit)) {
            is Outcome.Ok -> null
            is Outcome.Rejected -> r.rejection.message
        }
    }

    private class Failure(val message: String)

    private fun editFor(d: ScenarioDraft, path: String, text: String): Any? {
        when (path) {
            "id" -> return DraftEdit.SetId(text.trim())
            "title" -> return DraftEdit.SetTitle(text)
            "tagline" -> return DraftEdit.SetTagline(text)
            "story" -> return DraftEdit.SetStory(text)
            "company.name" -> return DraftEdit.SetCompanyName(text)
        }
        val n = whole(text)
        when (path) {
            "seed" -> return n?.let { DraftEdit.SetSeed(it) } ?: Failure("The seed must be a whole number.")
            "world.rivals" -> return n?.let { DraftEdit.SetRivals(it.coerceIn(-1L, 1_000L).toInt()) } ?: Failure("Rivals must be a whole number.")
            "company.capital" -> return n?.let { DraftEdit.SetCapital(it) } ?: Failure("Capital must be a whole number.")
            "timing.years" -> return n?.let { DraftEdit.SetTiming(Timing(it.coerceIn(-1L, 1_000L).toInt(), d.timing.goldDays, d.timing.silverDays)) } ?: Failure("Years must be a whole number.")
            "timing.gold" -> return n?.let { DraftEdit.SetTiming(d.timing.copy(goldDays = it.coerceIn(-1L, 1_000_000L).toInt())) } ?: Failure("Days must be a whole number.")
            "timing.silver" -> return n?.let { DraftEdit.SetTiming(d.timing.copy(silverDays = it.coerceIn(-1L, 1_000_000L).toInt())) } ?: Failure("Days must be a whole number.")
        }
        if (path.startsWith("rules.")) {
            val key = RuleKey.byKey(path.removePrefix("rules.")) ?: return null
            return n?.let { DraftEdit.SetRule(key, it) } ?: Failure("${key.label} must be a whole number.")
        }
        val m = itemPath.matchEntire(path)
        if (m != null) return itemEdit(d, m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3], text, n)
        val hint = Regex("hints\\[(\\d+)]").matchEntire(path)
        if (hint != null) return DraftEdit.UpdateHint(hint.groupValues[1].toInt(), text)
        return null
    }

    private fun itemEdit(d: ScenarioDraft, list: String, i: Int, field: String, text: String, n: Long?): Any? {
        fun bad(what: String) = Failure("$what must be a whole number.")
        return when (list) {
            "goals" -> {
                val g = d.goals.getOrNull(i) ?: return Failure("There is no goal ${i + 1}.")
                when (field) {
                    "target" -> n?.let { DraftEdit.UpdateGoal(i, g.copy(target = it)) } ?: bad("The target")
                    "weight" -> n?.let { DraftEdit.UpdateGoal(i, g.copy(weight = it.coerceIn(-1L, 1_000L).toInt())) } ?: bad("The weight")
                    else -> null
                }
            }
            "events" -> {
                val e = d.events.getOrNull(i) ?: return Failure("There is no event ${i + 1}.")
                when (field) {
                    "day" -> n?.let { DraftEdit.UpdateEvent(i, e.copy(day = it.coerceIn(-1L, 1_000_000L).toInt())) } ?: bad("The day")
                    "amount" -> n?.let { DraftEdit.UpdateEvent(i, e.copy(amount = it)) } ?: bad("The amount")
                    "text" -> DraftEdit.UpdateEvent(i, e.copy(text = text))
                    else -> null
                }
            }
            "fails" -> {
                val c = d.fails.getOrNull(i) ?: return Failure("There is no condition ${i + 1}.")
                when (field) {
                    "threshold" -> n?.let { DraftEdit.UpdateFail(i, c.copy(threshold = it)) } ?: bad("The threshold")
                    "afterDay" -> n?.let { DraftEdit.UpdateFail(i, c.copy(afterDay = it.coerceIn(-1L, 1_000_000L).toInt())) } ?: bad("The day")
                    else -> null
                }
            }
            "starters" -> null
            else -> null
        }
    }

    // ---- adding things ------------------------------------------------------------------------------

    /** The choices offered when adding a goal. */
    val goalKinds: List<GoalKind> = GoalKind.values().toList()

    fun addGoal(editor: DraftEditor, kind: GoalKind): String? =
        message(editor.apply(DraftEdit.AddGoal(DraftGoal(kind, defaultTarget(kind)))))

    fun defaultTarget(kind: GoalKind): Long = when (kind) {
        GoalKind.NET_WORTH -> 1_000_000L
        GoalKind.CASH -> 500_000L
        GoalKind.MONTHLY_PROFIT -> 50_000L
        GoalKind.RENT_INCOME -> 10_000L
        GoalKind.INVESTED -> 250_000L
        GoalKind.FACILITIES, GoalKind.PARCELS -> 5L
        GoalKind.EMPLOYEES -> 50L
        GoalKind.TECHNOLOGIES, GoalKind.INDUSTRIES, GoalKind.COMPANIES, GoalKind.CREDIT_RANK, GoalKind.MARKET_LEADER, GoalKind.DEBT_FREE -> 2L
    }

    fun addEvent(editor: DraftEditor, kind: EventKind): String? {
        val day = (editor.draft.events.maxOfOrNull { it.day } ?: 0) + 30
        return message(editor.apply(DraftEdit.AddEvent(ScriptedEvent(day, kind, if (kind.hasAmount) 50_000L else 0L, if (kind == EventKind.ANNOUNCE) "Something has happened." else ""))))
    }

    fun addFail(editor: DraftEditor, kind: FailKind): String? =
        message(editor.apply(DraftEdit.AddFail(FailCondition(kind, if (kind == FailKind.FACILITIES_BELOW) 1L else 0L, 90))))

    fun addHint(editor: DraftEditor): String? = message(editor.apply(DraftEdit.AddHint("Try something new.")))

    fun removeGoal(editor: DraftEditor, index: Int) = message(editor.apply(DraftEdit.RemoveGoal(index)))
    fun removeEvent(editor: DraftEditor, index: Int) = message(editor.apply(DraftEdit.RemoveEvent(index)))
    fun removeFail(editor: DraftEditor, index: Int) = message(editor.apply(DraftEdit.RemoveFail(index)))
    fun removeHint(editor: DraftEditor, index: Int) = message(editor.apply(DraftEdit.RemoveHint(index)))

    fun setWorldSize(editor: DraftEditor, size: WorldSize) = message(editor.apply(DraftEdit.SetWorldSize(size)))
    val worldSizes: List<WorldSize> = WorldSize.values().toList()

    fun toggleTag(editor: DraftEditor, key: String): String? {
        val tag = DifficultyTag.byKey(key) ?: return "There is no tag '$key'."
        return message(editor.apply(if (tag in editor.draft.tags) DraftEdit.RemoveTag(tag) else DraftEdit.AddTag(tag)))
    }

    fun undo(editor: DraftEditor): String? = if (editor.undo()) null else "Nothing to undo."
    fun redo(editor: DraftEditor): String? = if (editor.redo()) null else "Nothing to redo."

    private fun message(o: Outcome<ScenarioDraft>): String? = (o as? Outcome.Rejected)?.rejection?.message
}
