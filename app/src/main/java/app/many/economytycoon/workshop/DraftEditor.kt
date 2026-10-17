package app.many.economytycoon.workshop

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Outcome

/**
 * Holds a draft being edited, with the history to undo and redo it. Every accepted edit leaves the draft in canonical
 * order. Edits that change nothing are accepted but leave no history. The editor is the only place a draft changes.
 */
class DraftEditor(initial: ScenarioDraft, private val historyLimit: Int = DEFAULT_HISTORY) {
    private class Step(val label: String, val before: ScenarioDraft, val after: ScenarioDraft, val key: String?)

    init { require(historyLimit >= 1) { "history needs room for at least one step" } }

    var draft: ScenarioDraft = initial.normalised()
        private set

    /** Counts every change to the draft, including undo and redo, so a screen can tell when to redraw. */
    var revision: Long = 0L
        private set

    private val undoStack = ArrayDeque<Step>()
    private val redoStack = ArrayDeque<Step>()
    private var savedHash: String = draft.stateHash()
    private var lastWasEdit = false
    private var cachedFor: String? = null
    private var cachedReport: ValidationReport? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label

    /** Labels of the steps that can be undone, newest first. */
    fun history(): List<String> = undoStack.reversed().map { it.label }

    /** Whether the draft differs from how it was when last marked saved. */
    val dirty: Boolean get() = draft.stateHash() != savedHash

    fun markSaved() { savedHash = draft.stateHash() }

    fun apply(edit: DraftEdit): Outcome<ScenarioDraft> {
        val result = edit.apply(draft)
        val next = (result as? Outcome.Ok)?.value?.normalised() ?: return result
        if (next == draft) return Outcome.ok(draft)
        record(Step(edit.label, draft, next, edit.coalesceKey))
        return Outcome.ok(draft)
    }

    /**
     * Applies several edits as one undo step. If any is refused nothing changes and the first refusal is returned.
     */
    fun applyAll(label: String, edits: List<DraftEdit>): Outcome<ScenarioDraft> {
        var work = draft
        for (e in edits) {
            when (val r = e.apply(work)) {
                is Outcome.Ok -> work = r.value.normalised()
                is Outcome.Rejected -> return r
            }
        }
        if (work == draft) return Outcome.ok(draft)
        record(Step(label, draft, work, null))
        return Outcome.ok(draft)
    }

    private fun record(step: Step) {
        val top = undoStack.lastOrNull()
        if (step.key != null && lastWasEdit && top != null && top.key == step.key) {
            undoStack.removeLast()
            undoStack.addLast(Step(top.label, top.before, step.after, top.key))
        } else {
            undoStack.addLast(step)
            while (undoStack.size > historyLimit) undoStack.removeFirst()
        }
        redoStack.clear()
        draft = step.after
        lastWasEdit = true
        revision++
    }

    fun undo(): Boolean {
        val step = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(step)
        draft = step.before
        lastWasEdit = false
        revision++
        return true
    }

    fun redo(): Boolean {
        val step = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(step)
        draft = step.after
        lastWasEdit = false
        revision++
        return true
    }

    /** Forgets the history, keeping the draft. */
    fun clearHistory() { undoStack.clear(); redoStack.clear(); lastWasEdit = false }

    /** Validation of the current draft. The last answer is kept while the draft stays as it is. */
    fun validate(content: ContentRepository? = null): ValidationReport {
        val hash = draft.stateHash() + (content?.versions?.joinToString(prefix = "+") ?: "-")
        val cached = cachedReport
        if (cached != null && cachedFor == hash) return cached
        return DraftValidator.validate(draft, content).also { cachedReport = it; cachedFor = hash }
    }

    companion object { const val DEFAULT_HISTORY = 100 }
}
