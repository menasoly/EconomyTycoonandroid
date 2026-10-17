package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityTypeDef
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.ProductionParams
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.session.Formatting
import java.util.TreeMap
import java.util.TreeSet

/**
 * Turns a blueprint into an ordered [ApplyPlan] for a target. The planner mirrors the production rules
 * (line and machine limits, technology gates, busy lines) so the plan says up front what would be refused.
 * It builds [Command][app.many.economytycoon.simulation.Command] values only through [StepAction.toCommand];
 * running them is the executor's business.
 */
class ApplyPlanner(
    private val content: ContentRepository,
    private val production: ProductionParams = ProductionParams(),
    validation: ValidationParams = ValidationParams(maxMachinesPerLine = production.maxMachinesPerLine),
) {
    private val validator = BlueprintValidator(content, validation)
    private val differ = BlueprintDiffer(content)

    private class SimLine(var priority: Int, var enabled: Boolean, var recipe: RecipeId?, val machines: TreeSet<MachineTypeId>, val running: Boolean)

    /** Mutable picture of the target that each emitted step updates, so later checks see earlier steps. */
    private class Sim(
        val lines: MutableList<SimLine>, var policy: SchedulePolicy, var quality: Long, var storage: Int,
        val staff: TreeMap<RoleId, StaffSpec>, val sells: TreeMap<GoodId, SellSpec>, val buys: TreeMap<GoodId, BuySpec>,
    )

    fun plan(
        blueprint: Blueprint,
        target: ApplyTarget,
        research: ResearchView = ResearchView.ALL,
        options: ApplyOptions = ApplyOptions(),
    ): ApplyPlan = Run(blueprint.spec.normalized(), blueprint.name, target, research, options).execute()

    private inner class Run(
        val spec: BlueprintSpec, val name: String, val target: ApplyTarget, val research: ResearchView, val options: ApplyOptions,
    ) {
        val steps = ArrayList<PlannedStep>()
        val skipped = ArrayList<SkippedStep>()
        var remaining: Money? = options.budget
        val type: FacilityTypeDef? = content.facilityTypeOrNull(spec.facilityType)
        val existing = (target as? ApplyTarget.Existing)?.view
        var buildStep: Int? = null
        val addStep = HashMap<Int, Int>()
        val installStep = HashMap<Pair<Int, MachineTypeId>, Int>()
        val deadSlots = HashSet<Int>()
        lateinit var sim: Sim
        lateinit var before: BlueprintSpec

        fun execute(): ApplyPlan {
            val t = type
            val reason = when {
                t == null -> "This blueprint is for '${spec.facilityType.key}', which no longer exists."
                existing != null && existing.type != spec.facilityType ->
                    "This blueprint is for a ${t.name}, but the facility is a ${content.facilityTypeOrNull(existing.type)?.name ?: existing.type.key}."
                existing != null && !existing.operating -> "The facility is not operating. Reopen it before applying a blueprint."
                else -> null
            }
            before = existing?.toSpec() ?: BlueprintSpec(spec.facilityType, emptyList())
            if (t == null || reason != null) return finish(reason ?: "unavailable")
            sim = initialSim(t)
            val blockedBy = build(t)
            if (blockedBy != null) return finish(blockedBy)
            expand(t)
            removeMachines()
            install()
            configure(t)
            staffing()
            trading()
            return finish(null)
        }

        fun initialSim(t: FacilityTypeDef): Sim {
            if (existing == null) {
                val first = if (t.maxLines > 0) mutableListOf(SimLine(0, true, null, TreeSet(), false)) else mutableListOf()
                return Sim(first, SchedulePolicy.PRIORITY, 0L, 0, TreeMap(), TreeMap(), TreeMap())
            }
            val ordered = existing.lines.sortedWith(compareBy({ it.priority }, { it.id }))
            return Sim(
                ordered.map { SimLine(it.priority, it.enabled, it.recipe, TreeSet(it.machines), it.running) }.toMutableList(),
                existing.policy, existing.qualityPpm, existing.storageExpansions,
                TreeMap(existing.staff.associateBy { it.role }), TreeMap(existing.sells.associateBy { it.good }), TreeMap(existing.buys.associateBy { it.good }),
            )
        }

        // ---- plumbing ---------------------------------------------------------------------------

        fun skip(action: StepAction?, summary: String, reason: SkipReason, detail: String) { skipped += SkippedStep(action, summary, reason, detail) }

        /** Adds a step unless the budget cannot pay for it; returns its index or null. */
        fun emit(action: StepAction, summary: String, cost: Money, deps: List<Int?>): Int? {
            val left = remaining
            if (left != null && cost > left) {
                skip(action, summary, SkipReason.UNAFFORDABLE, "Costs ${Formatting.money(cost)} but only ${Formatting.money(left)} is left to spend.")
                return null
            }
            if (left != null) remaining = left - cost
            val idx = steps.size
            steps += PlannedStep(idx, action, summary, cost, deps.filterNotNull().distinct().sorted())
            return idx
        }

        fun slotDeps(slot: Int): List<Int?> = listOf(buildStep, addStep[slot])
        fun lineLabel(slot: Int) = "line ${slot + 1}"
        fun machineName(m: MachineTypeId) = content.machineTypeOrNull(m)?.name ?: m.key
        fun recipeName(r: RecipeId) = content.recipeOrNull(r)?.name ?: r.key

        fun techGap(tech: app.many.economytycoon.core.TechId?, what: String): String? {
            if (tech == null || research.hasTech(tech)) return null
            val chain = validator.missingTechChain(tech, research).joinToString(" > ") { content.technologyOrNull(it)?.name ?: it.key }
            return "$what needs research: $chain."
        }

        // ---- phases -----------------------------------------------------------------------------

        fun build(t: FacilityTypeDef): String? {
            val tg = target as? ApplyTarget.NewFacility ?: return null
            techGap(t.requiredTech, t.name)?.let { return it }
            val site = validator.validate(BlueprintSpec(spec.facilityType, emptyList()), research, tg.site)
                .errors.firstOrNull { it.code == IssueCodes.DEPOSIT_REQUIRED || it.code == IssueCodes.NOT_COASTAL }
            if (site != null) return site.message
            if (t.extracts != null && tg.deposit == null) return "${t.name} needs a deposit; choose one for the build."
            val label = tg.name.ifBlank { name }
            val idx = emit(StepAction.Build(spec.facilityType, tg.region, tg.city, tg.parcel, tg.deposit, label), "Build ${t.name} \"$label\"", t.buildCost, emptyList())
                ?: return "You cannot afford to build a ${t.name} (${Formatting.money(t.buildCost)})."
            buildStep = idx
            return null
        }

        fun expand(t: FacilityTypeDef) {
            val lineCost = Money(mulDiv(t.buildCost.raw, production.lineBuildCostPpm, 1_000_000L))
            for (slot in sim.lines.size until spec.lines.size) {
                val summary = "Add ${lineLabel(slot)}"
                if (slot >= t.maxLines) {
                    skip(StepAction.AddLine(slot), summary, SkipReason.LINE_LIMIT, "${t.name} supports ${t.maxLines} lines.")
                    deadSlots += slot; continue
                }
                val idx = emit(StepAction.AddLine(slot), summary, lineCost, listOf(buildStep))
                if (idx == null) { deadSlots += slot; continue }
                addStep[slot] = idx
                sim.lines += SimLine(slot, true, null, TreeSet(), false)
            }
            if (options.storage && spec.storageExpansions > sim.storage) {
                val cost = Money(mulDiv(t.buildCost.raw, CostEstimator.EXPANSION_COST_PPM, 1_000_000L))
                while (sim.storage < spec.storageExpansions) {
                    if (emit(StepAction.ExpandStorage, "Expand storage", cost, listOf(buildStep)) == null) break
                    sim.storage++
                }
            }
        }

        fun removeMachines() {
            if (!options.removeExtraMachines) return
            for ((slot, want) in spec.lines.withIndex()) {
                val line = sim.lines.getOrNull(slot) ?: continue
                for (m in line.machines.toList()) {
                    if (m in want.machines) continue
                    val action = StepAction.RemoveMachine(slot, m)
                    val summary = "Remove ${machineName(m)} from ${lineLabel(slot)}"
                    val usedNow = line.running && line.recipe?.let { r -> content.recipeOrNull(r)?.machines?.contains(m) } == true
                    if (usedNow) { skip(action, summary, SkipReason.LINE_BUSY, "${machineName(m)} is in use by the running batch."); continue }
                    if (emit(action, summary, Money.ZERO, slotDeps(slot)) != null) line.machines.remove(m)
                }
            }
        }

        fun install() {
            for ((slot, want) in spec.lines.withIndex()) {
                if (slot in deadSlots) {
                    for (m in want.machines) skip(StepAction.InstallMachine(slot, m), "Install ${machineName(m)} on ${lineLabel(slot)}", SkipReason.DEPENDENCY_SKIPPED, "${lineLabel(slot)} could not be added.")
                    continue
                }
                val line = sim.lines.getOrNull(slot) ?: continue
                for (m in want.machines) {
                    val action = StepAction.InstallMachine(slot, m)
                    val summary = "Install ${machineName(m)} on ${lineLabel(slot)}"
                    val def = content.machineTypeOrNull(m)
                    when {
                        m in line.machines -> skip(action, summary, SkipReason.ALREADY_DONE, "${machineName(m)} is already installed.")
                        def == null -> skip(action, summary, SkipReason.UNKNOWN_CONTENT, "Machine '${m.key}' no longer exists.")
                        techGap(def.requiredTech, def.name) != null -> skip(action, summary, SkipReason.TECH_NOT_RESEARCHED, techGap(def.requiredTech, def.name) ?: "")
                        line.machines.size >= production.maxMachinesPerLine ->
                            skip(action, summary, SkipReason.MACHINE_LIMIT, "A line holds ${production.maxMachinesPerLine} machines.")
                        else -> {
                            val idx = emit(action, summary, def.cost, slotDeps(slot))
                            if (idx != null) { installStep[slot to m] = idx; line.machines += m }
                        }
                    }
                }
            }
        }

        fun configure(t: FacilityTypeDef) {
            for ((slot, want) in spec.lines.withIndex()) {
                val line = sim.lines.getOrNull(slot)
                if (line == null || slot in deadSlots) {
                    skip(StepAction.SetRecipe(slot, want.recipe), "Configure ${lineLabel(slot)}", SkipReason.DEPENDENCY_SKIPPED, "${lineLabel(slot)} could not be added.")
                    continue
                }
                recipe(t, slot, line, want)
                if (line.enabled != want.enabled) {
                    if (emit(StepAction.SetEnabled(slot, want.enabled), "Switch ${lineLabel(slot)} ${if (want.enabled) "on" else "off"}", Money.ZERO, slotDeps(slot)) != null) line.enabled = want.enabled
                }
                if (line.priority != slot) {
                    if (emit(StepAction.SetPriority(slot, slot), "Rank ${lineLabel(slot)} number ${slot + 1}", Money.ZERO, slotDeps(slot)) != null) line.priority = slot
                }
            }
            if (options.schedule) {
                if (sim.policy != spec.policy && emit(StepAction.SetPolicy(spec.policy), "Schedule by ${spec.policy.name.lowercase().replace('_', ' ')}", Money.ZERO, listOf(buildStep)) != null) sim.policy = spec.policy
                if (sim.quality != spec.qualityPpm && emit(StepAction.SetQuality(spec.qualityPpm), "Quality assurance ${spec.qualityPpm / 10_000}%", Money.ZERO, listOf(buildStep)) != null) sim.quality = spec.qualityPpm
            }
        }

        fun recipe(t: FacilityTypeDef, slot: Int, line: SimLine, want: LineSpec) {
            val rid = want.recipe
            if (rid == null) {
                if (options.clearRecipes && line.recipe != null) {
                    val busy = line.running
                    val action = StepAction.SetRecipe(slot, null)
                    if (busy) skip(action, "Clear ${lineLabel(slot)}", SkipReason.LINE_BUSY, "Finish the running batch first.")
                    else if (emit(action, "Clear ${lineLabel(slot)}", Money.ZERO, slotDeps(slot)) != null) line.recipe = null
                }
                return
            }
            val action = StepAction.SetRecipe(slot, rid)
            val summary = "Run ${recipeName(rid)} on ${lineLabel(slot)}"
            val def = content.recipeOrNull(rid)
            if (line.recipe == rid) { skip(action, summary, SkipReason.ALREADY_DONE, "${recipeName(rid)} is already set."); return }
            if (def == null) { skip(action, summary, SkipReason.UNKNOWN_CONTENT, "Recipe '${rid.key}' no longer exists."); return }
            if (def.facilityType != t.id) { skip(action, summary, SkipReason.WRONG_FACILITY, "${def.name} cannot run in ${t.name}."); return }
            techGap(def.requiredTech, def.name)?.let { skip(action, summary, SkipReason.TECH_NOT_RESEARCHED, it); return }
            if (line.running) { skip(action, summary, SkipReason.LINE_BUSY, "Finish the running batch on ${lineLabel(slot)} first."); return }
            val missing = def.machines.filter { it !in line.machines }
            if (missing.isNotEmpty()) {
                skip(action, summary, SkipReason.DEPENDENCY_SKIPPED, "${def.name} needs ${missing.joinToString(", ") { machineName(it) }} on ${lineLabel(slot)}.")
                return
            }
            val deps = slotDeps(slot) + def.machines.map { installStep[slot to it] }
            if (emit(action, summary, Money.ZERO, deps) != null) line.recipe = rid
        }

        fun staffing() {
            if (!options.staffing) return
            val roles = content.allRoles.map { it.id }.toSet()
            for (s in spec.staff) {
                val roleName = content.allRoles.firstOrNull { it.id == s.role }?.name ?: s.role.key
                if (s.role !in roles) {
                    skip(StepAction.SetStaffing(s.role, s.target, s.wage), "Staff $roleName", SkipReason.UNKNOWN_CONTENT, "Role '${s.role.key}' no longer exists.")
                    continue
                }
                val cur = sim.staff[s.role]
                val sameTarget = cur != null && cur.target == s.target && (s.wage == null || cur.wage == s.wage)
                if (sameTarget) skip(StepAction.SetStaffing(s.role, s.target, s.wage), "Staff $roleName", SkipReason.ALREADY_DONE, "Target already ${s.target}.")
                else if (emit(StepAction.SetStaffing(s.role, s.target, s.wage), "Staff ${s.target} $roleName", Money.ZERO, listOf(buildStep)) != null) {
                    sim.staff[s.role] = (cur ?: StaffSpec(s.role, 0)).copy(target = s.target, wage = s.wage ?: cur?.wage)
                }
                val curTraining = sim.staff[s.role]?.trainingPpm ?: 0L
                if (curTraining != s.trainingPpm && sim.staff.containsKey(s.role)) {
                    if (emit(StepAction.SetTraining(s.role, s.trainingPpm), "Train ${s.trainingPpm / 10_000}% of $roleName", Money.ZERO, listOf(buildStep)) != null) {
                        sim.staff[s.role] = sim.staff.getValue(s.role).copy(trainingPpm = s.trainingPpm)
                    }
                }
            }
        }

        fun trading() {
            if (!options.trading) return
            for (s in spec.sells) {
                val gname = content.goodOrNull(s.good)?.name ?: s.good.key
                val action = StepAction.SetSell(s)
                val summary = "Sell $gname with a ${s.strategy.name.lowercase().replace('_', ' ')} price"
                when {
                    content.goodOrNull(s.good) == null -> skip(action, summary, SkipReason.UNKNOWN_CONTENT, "Good '${s.good.key}' no longer exists.")
                    sim.sells[s.good] == s -> skip(action, summary, SkipReason.ALREADY_DONE, "The rule already matches.")
                    else -> if (emit(action, summary, Money.ZERO, listOf(buildStep)) != null) sim.sells[s.good] = s
                }
            }
            for (b in spec.buys) {
                val gname = content.goodOrNull(b.good)?.name ?: b.good.key
                val action = StepAction.SetBuy(b)
                val summary = "Keep $gname stocked"
                when {
                    content.goodOrNull(b.good) == null -> skip(action, summary, SkipReason.UNKNOWN_CONTENT, "Good '${b.good.key}' no longer exists.")
                    sim.buys[b.good] == b -> skip(action, summary, SkipReason.ALREADY_DONE, "The rule already matches.")
                    else -> if (emit(action, summary, Money.ZERO, listOf(buildStep)) != null) sim.buys[b.good] = b
                }
            }
            if (!options.removeExtraRules) return
            for (g in sim.sells.keys.toList()) if (spec.sells.none { it.good == g }) {
                if (emit(StepAction.RemoveSell(g), "Stop selling ${content.goodOrNull(g)?.name ?: g.key}", Money.ZERO, listOf(buildStep)) != null) sim.sells.remove(g)
            }
            for (g in sim.buys.keys.toList()) if (spec.buys.none { it.good == g }) {
                if (emit(StepAction.RemoveBuy(g), "Stop stocking ${content.goodOrNull(g)?.name ?: g.key}", Money.ZERO, listOf(buildStep)) != null) sim.buys.remove(g)
            }
        }

        fun finish(blocked: String?): ApplyPlan {
            if (blocked != null) {
                return ApplyPlan(name, target, emptyList(), emptyList(), blocked, before, before, BlueprintDiff.NONE, differ.diff(before, spec))
            }
            val after = BlueprintSpec(
                spec.facilityType,
                sim.lines.withIndex().sortedWith(compareBy({ it.value.priority }, { it.index })).map { LineSpec(it.value.priority, it.value.enabled, it.value.recipe, it.value.machines.toList()) },
                sim.policy, sim.quality, sim.staff.values.toList(), sim.sells.values.toList(), sim.buys.values.toList(), sim.storage.coerceAtMost(BlueprintLimits.MAX_STORAGE_EXPANSIONS),
            ).normalized()
            return ApplyPlan(name, target, steps.toList(), skipped.toList(), null, before, after, differ.diff(before, after), differ.diff(after, mergeWanted(after)))
        }

        /** What the blueprint wants, laid over the facility's lines beyond it so the comparison is fair. */
        fun mergeWanted(after: BlueprintSpec): BlueprintSpec {
            val extraLines = after.lines.drop(spec.lines.size)
            val wantedLines = spec.lines.mapIndexed { i, w ->
                val a = after.lines.getOrNull(i) ?: return@mapIndexed w
                w.copy(
                    machines = if (options.removeExtraMachines) w.machines else (w.machines + a.machines).distinct().sorted(),
                    recipe = if (w.recipe == null && !options.clearRecipes) a.recipe else w.recipe,
                )
            }
            val staffWanted = if (options.staffing) spec.staff else after.staff
            val sellsWanted = if (options.trading) spec.sells else after.sells
            val buysWanted = if (options.trading) spec.buys else after.buys
            val keepStaff = after.staff.filter { a -> staffWanted.none { it.role == a.role } }
            val keepSells = if (options.removeExtraRules) emptyList() else after.sells.filter { a -> sellsWanted.none { it.good == a.good } }
            val keepBuys = if (options.removeExtraRules) emptyList() else after.buys.filter { a -> buysWanted.none { it.good == a.good } }
            return BlueprintSpec(
                spec.facilityType, wantedLines + extraLines,
                if (options.schedule) spec.policy else after.policy, if (options.schedule) spec.qualityPpm else after.qualityPpm,
                staffWanted + keepStaff, sellsWanted + keepSells, buysWanted + keepBuys,
                if (options.storage) maxOf(spec.storageExpansions, after.storageExpansions) else after.storageExpansions,
            ).normalized()
        }
    }
}
