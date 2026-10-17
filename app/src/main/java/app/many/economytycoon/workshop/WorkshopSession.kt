package app.many.economytycoon.workshop

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.SaveStore

/** What reading pasted text gave. */
sealed class ImportResult {
    data class Shared(val draft: ScenarioDraft, val warnings: List<String>) : ImportResult()
    data class Written(val parse: ParseResult) : ImportResult()
    data class Failed(val message: String, val at: TextPosition?) : ImportResult()
}

/**
 * Everything the workshop screens work with in one place: the draft being edited, the shelf of saved drafts, and the
 * latest test results. It holds no rules of its own; each action calls the part that owns the rule.
 */
class WorkshopSession(
    val content: ContentRepository,
    private val saves: SaveStore = MemorySaveStore(),
    executor: java.util.concurrent.Executor = WorkshopRunner.background(),
) {
    private val libraryStore = LibraryStore(saves)
    private val harness = WorkshopHarness(content)

    /** Runs studies and sweeps on another thread; the screens start tests here instead of calling [runStudy] directly. */
    val runner = WorkshopRunner(this, executor)

    /** Bumped whenever the editor is replaced, so a test that finishes after the draft was swapped is not filed under the new one. */
    @Volatile internal var generation = 0
        private set

    var library: WorkshopLibrary = libraryStore.load()
        private set
    @Volatile var editor: DraftEditor = DraftEditor(DraftTemplates.byKey("blank")?.create(library.uniqueId("new-scenario"), "New Scenario") ?: DraftDefaults.blank())
        private set

    /** The library entry the editor was opened from or last saved to, if any. */
    var entryId: Long? = null
        private set
    @Volatile var lastStudy: StudyResult? = null
        private set
    @Volatile var lastSweep: SweepReport? = null
        private set
    var regression: RegressionReport? = null
        private set
    var lastParse: ParseResult? = null
        private set

    val draft: ScenarioDraft get() = editor.draft
    fun validation(): ValidationReport = editor.validate(content)

    // ---- starting and filing drafts -----------------------------------------------------------

    fun startFrom(templateKey: String, title: String): Outcome<ScenarioDraft> {
        val t = DraftTemplates.byKey(templateKey) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "there is no template '$templateKey'")
        val name = title.trim().ifEmpty { t.name }
        val d = t.create(library.uniqueId(slug(name)), name).copy(seed = DraftDefaults.seedFor(name))
        return replaceEditor(d, null)
    }

    /** Starts a draft that plays like a built-in scenario. */
    fun startFromBuiltIn(scenarioId: String): Outcome<ScenarioDraft> {
        val def = app.many.economytycoon.scenarios.ScenarioCatalog.byId(scenarioId) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no built-in scenario '$scenarioId'")
        return replaceEditor(DraftTemplates.fromDef(def, library.uniqueId(def.id + "-remix")), null)
    }

    fun open(id: Long): Outcome<ScenarioDraft> {
        val e = library.get(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "there is no library entry $id")
        return replaceEditor(e.draft, id)
    }

    private fun replaceEditor(d: ScenarioDraft, id: Long?): Outcome<ScenarioDraft> {
        generation++
        editor = DraftEditor(d)
        entryId = id
        lastStudy = null; lastSweep = null; regression = null; lastParse = null
        return Outcome.ok(editor.draft)
    }

    /** Saves the draft to its library entry, or to a new one in [folder]. */
    fun save(folder: String = ""): Outcome<Long> {
        val id = entryId
        val result: Outcome<Long> = if (id == null) library.add(editor.draft, folder) else library.update(id, editor.draft).map { id }
        if (result is Outcome.Ok) {
            entryId = result.value
            editor.markSaved()
            libraryStore.save(library)
        }
        return result
    }

    fun delete(id: Long): Boolean {
        val removed = library.remove(id)
        if (removed) { libraryStore.save(library); saves.remove(baselineKey(id)); if (entryId == id) entryId = null }
        return removed
    }

    /** Applies a change to the library (filing, tags, stars) and writes it out. */
    fun <T> changeLibrary(action: (WorkshopLibrary) -> Outcome<T>): Outcome<T> {
        val r = action(library)
        if (r is Outcome.Ok) libraryStore.save(library)
        return r
    }

    // ---- sharing --------------------------------------------------------------------------------

    fun shareText(): String = ShareCodec.encode(editor.draft)
    fun dslText(): String = DraftPrinter.print(editor.draft)

    /** Reads pasted text, which may be a share code or the written form, into the editor. */
    fun importText(text: String): ImportResult {
        val looksShared = Regex("(?i)ETW\\d+-").containsMatchIn(text) && !text.contains("scenario", ignoreCase = true)
        if (looksShared) {
            return when (val r = ShareCodec.decode(text)) {
                is ShareResult.Failed -> ImportResult.Failed(r.error.message, r.error.at)
                is ShareResult.Decoded -> {
                    replaceEditor(r.draft.copy(id = library.uniqueId(r.draft.id)), null)
                    ImportResult.Shared(editor.draft, r.warnings)
                }
            }
        }
        val parse = DraftParser.parse(text)
        lastParse = parse
        val d = parse.draft
        if (d == null || !parse.ok) {
            val first = parse.errors.firstOrNull()
            return ImportResult.Failed(first?.message ?: "the text could not be read", first?.at)
        }
        replaceEditor(d, null)
        lastParse = parse
        return ImportResult.Written(parse)
    }

    // ---- testing --------------------------------------------------------------------------------

    fun runStudy(spec: StudySpec): Outcome<StudyResult> = studyOf(editor.draft, generation, spec)

    fun runSweep(spec: SweepSpec): SweepReport = sweepOf(editor.draft, generation, spec)

    /** Studies [d], which was the draft when the editor was at generation [at]; the result is filed only if it still is. */
    internal fun studyOf(d: ScenarioDraft, at: Int, spec: StudySpec): Outcome<StudyResult> {
        val r = BalanceStudy(harness).run(d, spec)
        if (r is Outcome.Ok && at == generation) lastStudy = r.value
        return r
    }

    internal fun sweepOf(d: ScenarioDraft, at: Int, spec: SweepSpec): SweepReport =
        SweepRunner(harness).run(d, spec).also { if (at == generation) lastSweep = it }

    fun convert(): Outcome<Conversion> = DraftConverter.convert(editor.draft, content)
    fun compat(): CompatReport = PackCompat.check(editor.draft, content)

    // ---- baselines ------------------------------------------------------------------------------

    private fun baselineKey(id: Long) = "workshop.baseline.$id"

    /** Records the last study as the baseline for the saved entry. The draft must have been saved first. */
    fun captureBaseline(name: String): Outcome<Baseline> {
        val id = entryId ?: return Outcome.reject(Rules.WRONG_STATE, "save the draft before recording a baseline")
        val study = lastStudy ?: return Outcome.reject(Rules.WRONG_STATE, "run a test first")
        val b = Regression.capture(name, editor.draft, study.runs)
        saves.put(baselineKey(id), BaselineCodec.toBytes(b))
        return Outcome.ok(b)
    }

    fun baseline(): Baseline? {
        val id = entryId ?: return null
        val bytes = saves.get(baselineKey(id)) ?: return null
        return try { BaselineCodec.fromBytes(bytes) } catch (e: ByteReader.CorruptDataException) { null }
    }

    /** Compares the last study with the recorded baseline. */
    fun checkBaseline(tolerancePpm: Long = 0L): Outcome<RegressionReport> {
        val b = baseline() ?: return Outcome.reject(Rules.WRONG_STATE, "there is no baseline for this draft")
        val study = lastStudy ?: return Outcome.reject(Rules.WRONG_STATE, "run a test first")
        return Outcome.ok(Regression.compare(b, editor.draft, study.runs, tolerancePpm).also { regression = it })
    }

    companion object {
        /** A scenario id made from a title: lowercase words joined by dashes. */
        fun slug(title: String): String {
            val s = title.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("").replace(Regex("-+"), "-").trim('-')
            return s.take(40).trim('-').ifEmpty { "scenario" }
        }
    }
}
