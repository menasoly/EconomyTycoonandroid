package app.many.economytycoon.blueprint

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Command

/** Order in which kinds of steps run: a thing must exist before anything can be installed in or set on it. */
enum class ApplyPhase(val label: String) {
    BUILD("Build"), EXPAND("Add lines and storage"), REMOVE("Remove machines"), INSTALL("Install machines"),
    CONFIGURE("Configure lines"), STAFF("Staffing"), TRADE("Trading rules"),
}

/**
 * One thing a blueprint asks for, expressed against line [slot]s (positions in the facility's line order)
 * instead of live ids, so a plan can be made before the facility or its lines exist.
 */
sealed class StepAction {
    abstract val phase: ApplyPhase
    /** Line position this acts on, or -1 when it acts on the facility as a whole. */
    open val slot: Int get() = -1

    data class Build(val type: FacilityTypeId, val region: RegionId, val city: CityId?, val parcel: ParcelId?, val deposit: DepositId?, val name: String) : StepAction() {
        override val phase get() = ApplyPhase.BUILD
    }
    data class AddLine(override val slot: Int) : StepAction() { override val phase get() = ApplyPhase.EXPAND }
    object ExpandStorage : StepAction() { override val phase get() = ApplyPhase.EXPAND }
    data class RemoveMachine(override val slot: Int, val machine: MachineTypeId) : StepAction() { override val phase get() = ApplyPhase.REMOVE }
    data class InstallMachine(override val slot: Int, val machine: MachineTypeId) : StepAction() { override val phase get() = ApplyPhase.INSTALL }
    data class SetRecipe(override val slot: Int, val recipe: RecipeId?) : StepAction() { override val phase get() = ApplyPhase.CONFIGURE }
    data class SetEnabled(override val slot: Int, val enabled: Boolean) : StepAction() { override val phase get() = ApplyPhase.CONFIGURE }
    data class SetPriority(override val slot: Int, val priority: Int) : StepAction() { override val phase get() = ApplyPhase.CONFIGURE }
    data class SetPolicy(val policy: SchedulePolicy) : StepAction() { override val phase get() = ApplyPhase.CONFIGURE }
    data class SetQuality(val levelPpm: Long) : StepAction() { override val phase get() = ApplyPhase.CONFIGURE }
    data class SetStaffing(val role: RoleId, val target: Long, val wage: Money?) : StepAction() { override val phase get() = ApplyPhase.STAFF }
    data class SetTraining(val role: RoleId, val ppm: Long) : StepAction() { override val phase get() = ApplyPhase.STAFF }
    data class SetSell(val spec: SellSpec) : StepAction() { override val phase get() = ApplyPhase.TRADE }
    data class RemoveSell(val good: GoodId) : StepAction() { override val phase get() = ApplyPhase.TRADE }
    data class SetBuy(val spec: BuySpec) : StepAction() { override val phase get() = ApplyPhase.TRADE }
    data class RemoveBuy(val good: GoodId) : StepAction() { override val phase get() = ApplyPhase.TRADE }

    /**
     * The simulation command for this action, or null when it cannot be formed yet: the facility is not built
     * ([facility] null) or the line at [slot] has no id in [lineIds] yet. Nothing is executed here.
     */
    fun toCommand(company: CompanyId, facility: FacilityId?, lineIds: List<LineId>): Command? {
        if (this is Build) return Command.Build(company, type, region, city, parcel, deposit, name)
        val f = facility ?: return null
        if (this is AddLine) return Command.AddLine(company, f)
        val line = if (slot >= 0) lineIds.getOrNull(slot) ?: return null else null
        return when (this) {
            is Build, is AddLine -> null
            ExpandStorage -> Command.ExpandStorage(company, f)
            is RemoveMachine -> Command.RemoveMachine(company, f, line ?: return null, machine)
            is InstallMachine -> Command.InstallMachine(company, f, line ?: return null, machine)
            is SetRecipe -> Command.SetRecipe(company, f, line ?: return null, recipe)
            is SetEnabled -> Command.SetLineEnabled(company, f, line ?: return null, enabled)
            is SetPriority -> Command.SetLinePriority(company, f, line ?: return null, priority)
            is SetPolicy -> Command.SetSchedulePolicy(company, f, policy)
            is SetQuality -> Command.SetQuality(company, f, levelPpm)
            is SetStaffing -> Command.SetStaffing(company, f, role, target, wage)
            is SetTraining -> Command.SetTraining(company, f, role, ppm)
            is SetSell -> Command.SetSellRule(company, spec.toRule(f))
            is RemoveSell -> Command.RemoveSellRule(company, f, good)
            is SetBuy -> Command.SetBuyRule(company, spec.toRule(f))
            is RemoveBuy -> Command.RemoveBuyRule(company, f, good)
        }
    }
}

/** Where a blueprint is applied. */
sealed class ApplyTarget {
    /** A facility that does not exist yet; the plan starts with a build step. */
    data class NewFacility(
        val region: RegionId, val name: String = "", val city: CityId? = null, val parcel: ParcelId? = null,
        val deposit: DepositId? = null, val site: SiteInfo? = null,
    ) : ApplyTarget()

    /** An existing facility, matched to the blueprint line by line in priority order. */
    data class Existing(val view: FacilityView) : ApplyTarget()
}

/** Choices that change what a plan contains. */
data class ApplyOptions(
    val staffing: Boolean = true,
    val trading: Boolean = true,
    val storage: Boolean = true,
    val schedule: Boolean = true,
    /** Sell machines the blueprint's line does not have. Off by default because selling loses money. */
    val removeExtraMachines: Boolean = false,
    /** Empty a line whose blueprint line has no recipe. */
    val clearRecipes: Boolean = false,
    /** Remove standing sell and buy rules the blueprint does not mention. */
    val removeExtraRules: Boolean = false,
    /** Cash available; steps that would overspend it are left out. Null means no limit. */
    val budget: Money? = null,
)

enum class SkipReason(val label: String) {
    ALREADY_DONE("Already matches"),
    UNKNOWN_CONTENT("No longer in the game"),
    TECH_NOT_RESEARCHED("Technology not researched"),
    WRONG_FACILITY("Wrong kind of facility"),
    LINE_LIMIT("Line limit reached"),
    MACHINE_LIMIT("Machine limit reached"),
    LINE_BUSY("Line is mid-batch"),
    UNAFFORDABLE("Not enough cash"),
    DEPENDENCY_SKIPPED("Needs a step that was skipped"),
    SITE_RULE("Site does not allow it"),
    NOT_OPERATING("Facility is not operating"),
}

data class PlannedStep(
    val index: Int,
    val action: StepAction,
    val summary: String,
    val cost: Money,
    /** Indices of earlier steps that must succeed first. */
    val dependsOn: List<Int>,
) {
    val phase: ApplyPhase get() = action.phase
}

data class SkippedStep(val action: StepAction?, val summary: String, val reason: SkipReason, val detail: String)

/** A step with the command to run it, once ids are known. [pending] says why there is no command yet. */
data class BoundStep(val step: PlannedStep, val command: Command?, val pending: String?)

/** A blueprint translated into ordered steps for one target, with a preview of what would change. */
data class ApplyPlan(
    val blueprintName: String,
    val target: ApplyTarget,
    val steps: List<PlannedStep>,
    val skipped: List<SkippedStep>,
    /** When set, nothing can be applied and this says why. */
    val blocked: String?,
    /** The facility's configuration before (an empty one for a new facility). */
    val before: BlueprintSpec,
    /** The configuration after every step succeeds. */
    val after: BlueprintSpec,
    /** What the plan will change. */
    val diff: BlueprintDiff,
    /** What the blueprint wanted that the plan does not deliver. */
    val unapplied: BlueprintDiff,
) {
    val totalCost: Money get() = steps.fold(Money.ZERO) { a, s -> a + s.cost }
    val isBlocked: Boolean get() = blocked != null
    val noOps: List<SkippedStep> get() = skipped.filter { it.reason == SkipReason.ALREADY_DONE }
    val problems: List<SkippedStep> get() = skipped.filter { it.reason != SkipReason.ALREADY_DONE }

    /** True when the plan delivers everything the blueprint asked for. */
    val isComplete: Boolean get() = !isBlocked && problems.isEmpty()
    val isNoOp: Boolean get() = !isBlocked && steps.isEmpty() && problems.isEmpty()

    fun step(index: Int): PlannedStep? = steps.firstOrNull { it.index == index }
    fun stepsIn(phase: ApplyPhase) = steps.filter { it.phase == phase }

    /**
     * Pairs every step with its command. Pass the facility and its lines in position order; steps that
     * need something that does not exist yet (a new line, an unbuilt facility) come back pending.
     */
    fun bind(company: CompanyId, facility: FacilityId?, lineIds: List<LineId>): List<BoundStep> =
        steps.map { s ->
            val c = s.action.toCommand(company, facility, lineIds)
            BoundStep(s, c, if (c != null) null else if (facility == null) "waiting for the facility to be built" else "waiting for line ${s.action.slot + 1} to exist")
        }

    /** The commands that can run right now, in order. */
    fun commands(company: CompanyId, facility: FacilityId?, lineIds: List<LineId>): List<Command> =
        bind(company, facility, lineIds).mapNotNull { it.command }
}
