package app.many.economytycoon.blueprint

import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.SaveStore

/** A new-facility apply that has been started: the plan (whose first step builds) and the request waiting to configure it. */
data class BeganApply(val plan: ApplyPlan, val pending: PendingApply)

/**
 * The one object the session holds for blueprints. It owns the library and the pending requests, keeps them saved,
 * and answers the questions the screens ask. It forms plans but never runs commands: whoever calls it submits them.
 */
class BlueprintController(
    val library: BlueprintLibrary,
    val pending: PendingApplyBook,
    private val libraryStore: BlueprintStore?,
    private val pendingStore: PendingApplyStore?,
    private val hostProvider: () -> BlueprintHost?,
    private val renames: RenameTable = RenameTable.NONE,
    /** What loading the saved library found; the screen can tell the player about dropped entries. */
    val loadReport: LibraryLoad? = null,
) {
    private var analyzerFor: Pair<app.many.economytycoon.content.ContentRepository, BlueprintAnalyzer>? = null

    fun host(): BlueprintHost? = hostProvider()

    private fun analyzer(host: BlueprintHost): BlueprintAnalyzer {
        val cached = analyzerFor
        if (cached != null && cached.first === host.content) return cached.second
        return BlueprintAnalyzer(host.content, renames).also { analyzerFor = host.content to it }
    }

    fun save() {
        libraryStore?.save(library)
        pendingStore?.save(pending)
    }

    /** Runs a change to the library and saves afterwards. */
    fun <T> edit(block: (BlueprintLibrary) -> T): T {
        val out = block(library)
        save()
        return out
    }

    // ---- capture and sharing ---------------------------------------------------------------------

    fun capture(
        facility: FacilityId, name: String = "", options: CaptureOptions = CaptureOptions(), note: String = "",
        folder: String = "", tags: Collection<String> = emptyList(),
    ): Outcome<LibraryEntry> {
        val host = host() ?: return Outcome.reject(Rules.WRONG_STATE, "Start or load a game first.")
        val view = host.view(facility) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That facility is not yours.")
        val bp = BlueprintCapture.capture(view, name.ifBlank { view.name }, options, note, analyzer(host).currentTag(), host.day)
            .let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        return when (val r = edit { it.add(bp, folder, tags, false, host.day) }) {
            is AddResult.Added -> Outcome.ok(r.entry)
            is AddResult.Duplicate -> Outcome.reject(Rules.DUPLICATE, "This set-up is already saved as '${r.existing.name}'.")
            is AddResult.Refused -> Outcome.reject(r.rule, r.message)
        }
    }

    fun importText(text: String, folder: String = ""): ImportReport = edit { it.import(text, folder, host()?.day ?: 0L) }

    fun exportText(ids: Collection<BlueprintId>, encoding: TextEncoding = TextEncoding.BASE32): String = library.export(ids, encoding)

    // ---- looking ---------------------------------------------------------------------------------

    fun analysis(id: BlueprintId, region: RegionId? = null): BlueprintAnalysis? {
        val host = host() ?: return null
        val entry = library.get(id) ?: return null
        return analyzer(host).analyze(entry.blueprint, host.research(), region?.let { host.site(it) })
    }

    // ---- applying --------------------------------------------------------------------------------

    fun planExisting(id: BlueprintId, facility: FacilityId, options: ApplyOptions = ApplyOptions()): Outcome<ApplyPlan> {
        val host = host() ?: return Outcome.reject(Rules.WRONG_STATE, "Start or load a game first.")
        val entry = library.get(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That blueprint is not in the library.")
        val view = host.view(facility) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That facility is not yours.")
        return plan(host, entry.blueprint, ApplyTarget.Existing(view), options.withBudget(host))
    }

    fun planNew(id: BlueprintId, region: RegionId, name: String = "", deposit: DepositId? = null, options: ApplyOptions = ApplyOptions()): Outcome<ApplyPlan> {
        val host = host() ?: return Outcome.reject(Rules.WRONG_STATE, "Start or load a game first.")
        val entry = library.get(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That blueprint is not in the library.")
        val target = ApplyTarget.NewFacility(region, name.trim().ifBlank { entry.name }, host.cityFor(region), null, deposit, host.site(region))
        return plan(host, entry.blueprint, target, options.withBudget(host))
    }

    private fun ApplyOptions.withBudget(host: BlueprintHost) = if (budget != null) this else copy(budget = host.cash())

    private fun plan(host: BlueprintHost, bp: Blueprint, target: ApplyTarget, options: ApplyOptions): Outcome<ApplyPlan> {
        val usable = analyzer(host).usable(bp) ?: return Outcome.reject(Rules.WRONG_STATE, "This blueprint is for a facility that no longer exists.")
        return Outcome.ok(ApplyPlanner(host.content).plan(usable, target, host.research(), options))
    }

    /**
     * Starts building a new facility from a blueprint and remembers to configure it once it stands. The returned
     * plan's first step is the build; the caller submits it. Everything after it waits in [pending].
     */
    fun beginNew(id: BlueprintId, region: RegionId, name: String = "", deposit: DepositId? = null, options: ApplyOptions = ApplyOptions()): Outcome<BeganApply> {
        val host = host() ?: return Outcome.reject(Rules.WRONG_STATE, "Start or load a game first.")
        val plan = when (val p = planNew(id, region, name, deposit, options)) {
            is Outcome.Rejected -> return p
            is Outcome.Ok -> p.value
        }
        plan.blocked?.let { return Outcome.reject(Rules.WRONG_STATE, it) }
        val build = plan.steps.firstOrNull()?.action as? StepAction.Build ?: return Outcome.reject(Rules.WRONG_STATE, "Nothing to build.")
        val entry = library.get(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That blueprint is not in the library.")
        val request = pending.add(analyzer(host).usable(entry.blueprint) ?: entry.blueprint, region, build.name, host.day, host.facilities().map { it.id }, options)
            ?: return Outcome.reject(Rules.LIMIT_EXCEEDED, "You already have ${PendingApplyBook.MAX} buildings waiting to be configured.")
        save()
        return Outcome.ok(BeganApply(plan, request))
    }

    /** Buildings that have finished and are waiting for their blueprint. */
    fun readyPending(): List<ReadyApply> = host()?.let { pending.ready(it.facilities()) }.orEmpty()

    /** Plans the configuration of a finished building and clears its request. */
    fun finishPending(ready: ReadyApply): Outcome<ApplyPlan> {
        val host = host() ?: return Outcome.reject(Rules.WRONG_STATE, "Start or load a game first.")
        val view = host.view(ready.facility) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "That facility is not yours.")
        pending.remove(ready.pending.id)
        save()
        return Outcome.ok(ApplyPlanner(host.content).plan(ready.pending.blueprint, ApplyTarget.Existing(view), host.research(), ready.pending.options().copy(budget = host.cash())))
    }

    /** Forgets requests that never found their building. */
    fun expirePending(): List<PendingApply> {
        val day = host()?.day ?: return emptyList()
        return pending.expire(day).also { if (it.isNotEmpty()) save() }
    }

    companion object {
        /** Opens the controller over a save store, loading what is there. */
        fun open(saves: SaveStore, renames: RenameTable = RenameTable.NONE, host: () -> BlueprintHost?): BlueprintController {
            val ls = BlueprintStore(saves)
            val ps = PendingApplyStore(saves)
            val load = ls.load()
            return BlueprintController(load.library, ps.load(), ls, ps, host, renames, load)
        }
    }
}
