package app.many.economytycoon.production

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityTypeDef
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.ProjectId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.guardRule
import app.many.economytycoon.core.mulDiv

/** Result of tearing a facility down: what the owner gets back and the stock value written off. */
data class Demolition(val refund: Money, val writtenOffStock: Money)

/**
 * Owns every facility and construction project. Commands validate and apply atomically; [step]
 * advances construction and then runs each facility through the [ProductionEngine].
 */
class ProductionService(
    private val ids: IdAllocators,
    val content: ContentRepository,
    val params: ProductionParams = ProductionParams(),
    var payer: Payer = FreePayer,
    /** Whether a company has researched a technology. Defaults to everything unlocked. */
    var hasTech: (CompanyId, TechId) -> Boolean = { _, _ -> true },
) {
    private val facilities = java.util.TreeMap<FacilityId, Facility>()
    private val projects = java.util.TreeMap<ProjectId, ConstructionProject>()
    val engine = ProductionEngine(content, params)

    // ---- queries -------------------------------------------------------------------------------

    fun facility(id: FacilityId): Facility? = facilities[id]
    fun facilities(): List<Facility> = facilities.values.toList()
    fun facilitiesOf(company: CompanyId): List<Facility> = facilities.values.filter { it.owner == company && it.status != FacilityStatus.DEMOLISHED }
    fun project(id: ProjectId): ConstructionProject? = projects[id]
    fun projects(): List<ConstructionProject> = projects.values.toList()
    fun openProjectsOf(company: CompanyId): List<ConstructionProject> = projects.values.filter { it.owner == company && it.isOpen }

    fun inventoryValue(company: CompanyId): Money {
        var t = 0L
        for (f in facilities.values) if (f.owner == company && f.status != FacilityStatus.DEMOLISHED) t += f.inventory.totalValue().raw
        return Money(t)
    }

    private fun volumeFn(): (GoodId) -> Long = { g -> content.goodOrNull(g)?.volume ?: 1_000L }

    private fun owned(owner: CompanyId, id: FacilityId): Outcome<Facility> {
        val f = facilities[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "facility $id does not exist")
        if (f.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "facility $id belongs to another company")
        if (f.status == FacilityStatus.DEMOLISHED) return Outcome.reject(Rules.WRONG_STATE, "facility $id was demolished")
        return Outcome.ok(f)
    }

    private fun techGate(owner: CompanyId, tech: TechId?, what: String): app.many.economytycoon.core.Rejection? =
        guardRule(tech == null || hasTech(owner, tech), Rules.NOT_UNLOCKED) { "$what requires ${tech?.key}" }

    // ---- construction --------------------------------------------------------------------------

    fun startConstruction(
        owner: CompanyId, type: FacilityTypeId, region: RegionId, city: CityId?, parcel: ParcelId?,
        deposit: DepositId?, name: String, tick: Tick,
    ): Outcome<ConstructionProject> {
        val def = content.facilityTypeOrNull(type) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown facility type ${type.key}")
        techGate(owner, def.requiredTech, def.name)?.let { return Outcome.Rejected(it) }
        if (def.extracts != null && deposit == null) return Outcome.reject(Rules.INVALID_ARGUMENT, "${def.name} needs a deposit")
        if (parcel != null && (facilities.values.any { it.parcel == parcel && it.status != FacilityStatus.DEMOLISHED } ||
                projects.values.any { it.parcel == parcel && it.isOpen })) return Outcome.reject(Rules.DUPLICATE, "parcel already in use")
        val id = ids.project()
        val p = ConstructionProject(id, ProjectKind.BUILD, owner, type, region, city, parcel, deposit, null, tick, def.buildTicks, def.buildCost, def.buildInputs, name.ifBlank { def.name })
        projects[id] = p
        return Outcome.ok(p)
    }

    fun startStorageExpansion(owner: CompanyId, facility: FacilityId, tick: Tick): Outcome<ConstructionProject> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        if (projects.values.any { it.target == facility && it.isOpen }) return Outcome.reject(Rules.DUPLICATE, "an upgrade is already under way")
        val def = content.facilityType(f.type)
        val cost = Money(mulDiv(def.buildCost.raw, 200_000L, 1_000_000L))
        val ticks = (def.buildTicks / 2).coerceAtLeast(1)
        val id = ids.project()
        val p = ConstructionProject(id, ProjectKind.EXPAND_STORAGE, owner, f.type, f.region, f.city, f.parcel, null, facility, tick, ticks, cost, emptyList(), "${f.name.ifBlank { def.name }} storage")
        projects[id] = p
        return Outcome.ok(p)
    }

    /** Takes materials for a project; the caller removes the same quantity from its stock after a successful return. */
    fun deliverMaterials(owner: CompanyId, project: ProjectId, good: GoodId, quantity: Quantity): Outcome<Quantity> {
        val p = projects[project] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown project")
        if (p.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your project")
        if (!p.isOpen) return Outcome.reject(Rules.WRONG_STATE, "project is closed")
        if (quantity.raw <= 0) return Outcome.reject(Rules.INVALID_QUANTITY, "quantity must be positive")
        val taken = p.deliver(good, quantity)
        if (taken.raw == 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "project does not need ${good.key}")
        return Outcome.ok(taken)
    }

    /** Cancels a project; 40% of what was paid comes back, returned for the caller to credit. */
    fun cancelConstruction(owner: CompanyId, project: ProjectId): Outcome<Money> {
        val p = projects[project] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown project")
        if (p.owner != owner) return Outcome.reject(Rules.NOT_OWNER, "not your project")
        if (!p.isOpen) return Outcome.reject(Rules.WRONG_STATE, "project is closed")
        p.state = ProjectState.CANCELLED
        return Outcome.ok(Money(mulDiv(p.paid.raw, 400_000L, 1_000_000L)))
    }

    private fun stepProject(p: ConstructionProject, tick: Tick, report: ProductionReport) {
        if (!p.isOpen) return
        if (p.ticksDone < p.totalTicks) {
            val due = p.nextInstallment()
            // The last stage cannot start before the materials are on site.
            if (p.ticksDone == p.totalTicks - 1 && !p.materialsComplete()) {
                p.state = ProjectState.WAITING_MATERIALS; report.stalledProjects += p.id; return
            }
            if (due.raw > 0 && !payer.charge(p.owner, due, "construction ${p.id.value}")) {
                p.state = ProjectState.WAITING_FUNDS; report.stalledProjects += p.id; return
            }
            p.paid += due
            p.ticksDone++
            p.state = ProjectState.ACTIVE
        }
        if (p.ticksDone >= p.totalTicks) complete(p, tick, report)
    }

    private fun complete(p: ConstructionProject, tick: Tick, report: ProductionReport) {
        p.state = ProjectState.DONE
        when (p.kind) {
            ProjectKind.BUILD -> {
                val def = content.facilityType(p.facilityType)
                val f = newFacility(p.owner, def, p.region, p.city, p.parcel, tick, p.name)
                f.depositId = p.deposit
                facilities[f.id] = f
            }
            ProjectKind.EXPAND_STORAGE -> {
                val f = facilities[p.target!!] ?: return
                f.inventory.capacity = f.inventory.capacity + Quantity(mulDiv(f.inventory.capacity.raw, 250_000L, 1_000_000L))
            }
        }
        report.completedProjects += p.id
    }

    private fun newFacility(owner: CompanyId, def: FacilityTypeDef, region: RegionId, city: CityId?, parcel: ParcelId?, tick: Tick, name: String): Facility {
        val f = Facility(ids.facility(), def.id, owner, region, city, parcel, tick, Inventory(def.storageCapacity, volumeFn()))
        f.name = name
        if (def.maxLines > 0) f.lines.add(ProductionLine(ids.line(), 0))
        return f
    }

    /** Creates a finished facility without a build phase; used for starting positions and scenarios. */
    fun grantFacility(owner: CompanyId, type: FacilityTypeId, region: RegionId, city: CityId?, parcel: ParcelId?, deposit: DepositId?, tick: Tick, name: String = ""): Facility {
        val def = content.facilityType(type)
        val f = newFacility(owner, def, region, city, parcel, tick, name.ifBlank { def.name })
        f.depositId = deposit
        facilities[f.id] = f
        return f
    }

    // ---- line commands -------------------------------------------------------------------------

    fun addLine(owner: CompanyId, facility: FacilityId): Outcome<ProductionLine> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val def = content.facilityType(f.type)
        if (f.lines.size >= def.maxLines) return Outcome.reject(Rules.LIMIT_EXCEEDED, "${def.name} supports ${def.maxLines} lines")
        val cost = Money(mulDiv(def.buildCost.raw, params.lineBuildCostPpm, 1_000_000L))
        if (!payer.charge(owner, cost, "line for ${f.id.value}")) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford a new line")
        val line = ProductionLine(ids.line(), f.lines.size)
        f.lines.add(line)
        return Outcome.ok(line)
    }

    fun setRecipe(owner: CompanyId, facility: FacilityId, line: LineId, recipe: RecipeId?): Outcome<Unit> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val l = f.line(line) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown line")
        if (l.batch != null) return Outcome.reject(Rules.WRONG_STATE, "finish or abandon the running batch first")
        if (recipe == null) { l.recipe = null; return Outcome.ok(Unit) }
        val r = content.recipeOrNull(recipe) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown recipe ${recipe.key}")
        if (r.facilityType != f.type) return Outcome.reject(Rules.INVALID_ARGUMENT, "${r.name} cannot run in this facility")
        techGate(owner, r.requiredTech, r.name)?.let { return Outcome.Rejected(it) }
        l.recipe = recipe
        return Outcome.ok(Unit)
    }

    /** Throws away the running batch; its inputs are lost. Returns the lost carrying value for write-off. */
    fun abandonBatch(owner: CompanyId, facility: FacilityId, line: LineId): Outcome<Money> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val l = f.line(line) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown line")
        val b = l.batch ?: return Outcome.reject(Rules.WRONG_STATE, "no batch running")
        l.batch = null
        return Outcome.ok(b.inputValue)
    }

    fun setLineEnabled(owner: CompanyId, facility: FacilityId, line: LineId, enabled: Boolean): Outcome<Unit> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val l = f.line(line) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown line")
        l.enabled = enabled
        return Outcome.ok(Unit)
    }

    fun setPriority(owner: CompanyId, facility: FacilityId, line: LineId, priority: Int): Outcome<Unit> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val l = f.line(line) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown line")
        l.priority = priority
        return Outcome.ok(Unit)
    }

    fun setPolicy(owner: CompanyId, facility: FacilityId, policy: SchedulePolicy): Outcome<Unit> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        f.policy = policy
        return Outcome.ok(Unit)
    }

    fun setQualityAssurance(owner: CompanyId, facility: FacilityId, level: Rate): Outcome<Unit> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        if (level.ppm !in 0..900_000) return Outcome.reject(Rules.INVALID_ARGUMENT, "quality assurance must be 0-90%")
        f.qualityAssurancePpm = level.ppm
        return Outcome.ok(Unit)
    }

    // ---- machines ------------------------------------------------------------------------------

    fun installMachine(owner: CompanyId, facility: FacilityId, line: LineId, type: MachineTypeId, tick: Tick): Outcome<Machine> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val l = f.line(line) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown line")
        val def = content.machineTypeOrNull(type) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown machine ${type.key}")
        techGate(owner, def.requiredTech, def.name)?.let { return Outcome.Rejected(it) }
        if (l.machines.size >= params.maxMachinesPerLine) return Outcome.reject(Rules.LIMIT_EXCEEDED, "a line holds ${params.maxMachinesPerLine} machines")
        if (l.hasMachine(type)) return Outcome.reject(Rules.DUPLICATE, "${def.name} is already on this line")
        if (!payer.charge(owner, def.cost, "machine ${def.name}")) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford ${def.name}")
        val m = Machine(ids.machine(), type, tick)
        l.machines.add(m)
        return Outcome.ok(m)
    }

    /** Sells a machine back at 30% of its price scaled by condition; the amount is returned for the caller to credit. */
    fun removeMachine(owner: CompanyId, facility: FacilityId, line: LineId, type: MachineTypeId): Outcome<Money> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val l = f.line(line) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown line")
        val m = l.machines.firstOrNull { it.type == type } ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "machine not installed")
        if (l.batch != null && l.recipe?.let { content.recipe(it).machines.contains(type) } == true) return Outcome.reject(Rules.WRONG_STATE, "machine is in use")
        l.machines.remove(m)
        val def = content.machineType(type)
        val value = mulDiv(mulDiv(def.cost.raw, 300_000L, 1_000_000L), m.conditionPpm, 1_000_000L)
        return Outcome.ok(Money(value))
    }

    /**
     * Spends up to [budget] on repairs: worst machines first, then the building. Returns the amount spent.
     */
    fun maintain(owner: CompanyId, facility: FacilityId, budget: Money): Outcome<Money> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        if (budget.raw <= 0) return Outcome.reject(Rules.INVALID_ARGUMENT, "budget must be positive")
        val machines = f.lines.flatMap { it.machines }.sortedWith(compareBy<Machine> { it.conditionPpm }.thenBy { it.id.value })
        val planned = ArrayList<Pair<Machine, Money>>()
        var left = budget.raw
        for (m in machines) {
            if (left <= 0) break
            val need = MachineWear.fullRepairCost(m, content.machineType(m.type)).raw
            val spend = minOf(need, left)
            if (spend > 0) { planned += m to Money(spend); left -= spend }
        }
        val def = content.facilityType(f.type)
        val buildingNeed = mulDiv(def.buildCost.raw, 1_000_000L - f.conditionPpm, 1_000_000L) / 10L
        val buildingSpend = minOf(buildingNeed, left)
        left -= buildingSpend
        val total = Money(budget.raw - left)
        if (total.raw <= 0) return Outcome.ok(Money.ZERO)
        if (!payer.charge(owner, total, "maintenance ${f.id.value}")) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford maintenance")
        for ((m, spend) in planned) MachineWear.repair(m, content.machineType(m.type), spend)
        if (buildingSpend > 0 && buildingNeed > 0) {
            val restore = mulDiv(buildingSpend, 1_000_000L - f.conditionPpm, buildingNeed)
            f.conditionPpm = (f.conditionPpm + restore).coerceAtMost(1_000_000L)
            f.unmaintainedTicks = 0
        }
        return Outcome.ok(total)
    }

    // ---- facility lifecycle --------------------------------------------------------------------

    fun shutdown(owner: CompanyId, facility: FacilityId): Outcome<Unit> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        f.status = FacilityStatus.SHUTDOWN
        return Outcome.ok(Unit)
    }

    fun reopen(owner: CompanyId, facility: FacilityId): Outcome<Unit> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        if (f.status != FacilityStatus.SHUTDOWN) return Outcome.reject(Rules.WRONG_STATE, "facility is not shut down")
        f.status = FacilityStatus.OPERATING
        return Outcome.ok(Unit)
    }

    fun demolish(owner: CompanyId, facility: FacilityId): Outcome<Demolition> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        if (f.inventory.reservedTotal().raw > 0) return Outcome.reject(Rules.WRONG_STATE, "stock is reserved for pending sales")
        val def = content.facilityType(f.type)
        val refund = Money(mulDiv(mulDiv(def.buildCost.raw, params.demolishRefundPpm, 1_000_000L), f.conditionPpm, 1_000_000L))
        val stock = f.inventory.totalValue()
        for (g in f.inventory.goods()) f.inventory.clear(g)
        f.status = FacilityStatus.DEMOLISHED
        for (l in f.lines) { l.batch = null; l.enabled = false }
        return Outcome.ok(Demolition(refund, stock))
    }

    /** Pays to dispose of all stored waste and returns the quantity removed; pollution is relieved in proportion. */
    fun disposeWaste(owner: CompanyId, facility: FacilityId): Outcome<Quantity> {
        val f = owned(owner, facility).let { if (it is Outcome.Rejected) return it else it.getOrThrow() }
        val wasteGoods = f.inventory.goods().filter { content.goodOrNull(it)?.category == GoodCategory.WASTE }
        var units = 0L
        for (g in wasteGoods) units += f.inventory.quantity(g).raw
        if (units == 0L) return Outcome.ok(Quantity.ZERO)
        val cost = Money(mulDiv(params.disposalCostPerUnit.raw, units, Quantity.SCALE))
        if (!payer.charge(owner, cost, "waste disposal ${f.id.value}")) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford waste disposal")
        for (g in wasteGoods) f.inventory.clear(g)
        f.pollutionPpm = f.pollutionPpm / 2
        return Outcome.ok(Quantity(units))
    }

    /** Hands a facility to a new owner (acquisition, bankruptcy sale). Workforce and ledger changes are the caller's job. */
    fun transfer(facility: FacilityId, newOwner: CompanyId): Outcome<Unit> {
        val f = facilities[facility] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown facility")
        f.owner = newOwner
        return Outcome.ok(Unit)
    }

    // ---- simulation ----------------------------------------------------------------------------

    fun step(ctx: TickContext): ProductionReport {
        val report = ProductionReport(ctx.tick)
        for (p in projects.values.toList()) stepProject(p, ctx.tick, report)
        for (f in facilities.values) {
            if (f.status == FacilityStatus.DEMOLISHED) continue
            report.facilities += engine.stepFacility(f, ctx)
        }
        return report
    }

    // ---- persistence support -------------------------------------------------------------------

    internal fun restoreFacility(f: Facility) { facilities[f.id] = f }
    internal fun restoreProject(p: ConstructionProject) { projects[p.id] = p }
    internal fun clear() { facilities.clear(); projects.clear() }
    internal fun newInventory(capacity: Quantity) = Inventory(capacity, volumeFn())

    fun hashInto(h: CanonicalHasher) {
        h.section("production")
        h.putInt(facilities.size)
        for (f in facilities.values) f.hashInto(h)
        h.putInt(projects.size)
        for (p in projects.values) p.hashInto(h)
    }
}
