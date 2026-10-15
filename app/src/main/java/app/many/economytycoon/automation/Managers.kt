package app.many.economytycoon.automation

import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.MachineWear
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Economy
import app.many.economytycoon.workforce.CohortKey

/** One thing a manager wants done, with the reason in words. */
data class Planned(val kind: ManagerKind, val command: Command, val note: String)

/** Reads what managers need from the economy, in one place so each manager stays short. */
class AutomationContext(val e: Economy, val company: CompanyId, val settings: ManagerSettings) {
    val owner get() = e.companies.company(company)?.owner
    val cash: Money get() = owner?.let { e.finance.statements.balance(it, e.tick).cash } ?: Money.ZERO
    val spendable: Money get() = Money.max(cash - settings.reserve, Money.ZERO)
    val facilities: List<Facility> get() = e.production.facilitiesOf(company).filter { it.isOperating() }

    fun name(f: Facility): String = f.name.ifBlank { e.content.facilityTypeOrNull(f.type)?.name ?: f.type.key }
    fun goodName(g: GoodId): String = e.content.goodOrNull(g)?.name ?: g.key.replace('_', ' ')
}

/** Each function looks at the company and returns what its manager would do today. */
object Managers {
    fun plan(kind: ManagerKind, ctx: AutomationContext): List<Planned> = when (kind) {
        ManagerKind.MAINTENANCE -> maintenance(ctx)
        ManagerKind.WASTE -> waste(ctx)
        ManagerKind.SELLING -> selling(ctx)
        ManagerKind.STAFFING -> staffing(ctx)
        ManagerKind.RESEARCH -> research(ctx)
        ManagerKind.REPAYMENT -> repayment(ctx)
    }

    /** Money needed to bring a facility's building and worn machines back to full condition. */
    fun repairNeed(ctx: AutomationContext, f: Facility): Money {
        val def = ctx.e.content.facilityTypeOrNull(f.type) ?: return Money.ZERO
        val building = if (f.conditionPpm < ctx.settings.repairBelowPpm) mulDiv(def.buildCost.raw, 1_000_000L - f.conditionPpm, 1_000_000L) / 10L else 0L
        var machines = 0L
        for (l in f.lines) for (m in l.machines) {
            if (m.conditionPpm >= ctx.settings.repairBelowPpm) continue
            val md = ctx.e.content.machineTypeOrNull(m.type) ?: continue
            machines += MachineWear.fullRepairCost(m, md).raw
        }
        return Money(building + machines)
    }

    fun maintenance(ctx: AutomationContext): List<Planned> {
        var left = ctx.spendable
        val out = ArrayList<Planned>()
        for (f in ctx.facilities.sortedBy { it.conditionPpm }) {
            val need = repairNeed(ctx, f)
            if (need.raw <= 0L || left.raw <= 0L) continue
            val budget = Money.min(need, left)
            left -= budget
            out += Planned(ManagerKind.MAINTENANCE, Command.Maintain(ctx.company, f.id, budget), "Repairs at ${ctx.name(f)}")
        }
        return out
    }

    fun wasteUnits(ctx: AutomationContext, f: Facility): Long =
        f.inventory.goods().filter { ctx.e.content.goodOrNull(it)?.category == GoodCategory.WASTE }.sumOf { f.inventory.quantity(it).raw } / app.many.economytycoon.core.Quantity.SCALE

    fun waste(ctx: AutomationContext): List<Planned> {
        var left = ctx.spendable
        val out = ArrayList<Planned>()
        for (f in ctx.facilities) {
            val units = wasteUnits(ctx, f)
            if (units < ctx.settings.wasteUnits || left.raw <= 0L) continue
            val cost = Money(mulDiv(ctx.e.production.params.disposalCostPerUnit.raw, units, 1L))
            if (cost > left) continue
            left -= cost
            out += Planned(ManagerKind.WASTE, Command.DisposeWaste(ctx.company, f.id), "Cleared $units units of waste at ${ctx.name(f)}")
        }
        return out
    }

    fun selling(ctx: AutomationContext): List<Planned> {
        val have = ctx.e.companies.policies.sellRules(ctx.company).map { it.facility to it.good }.toSet()
        val out = ArrayList<Planned>()
        for (f in ctx.facilities) {
            val goods = f.lines.mapNotNull { l -> l.recipe?.let { ctx.e.content.recipeOrNull(it) } }
                .flatMap { r -> r.outputs.map { it.good } }.distinct().sortedBy { it.key }
            for (g in goods) {
                val def = ctx.e.content.goodOrNull(g) ?: continue
                if (!def.tradable || def.category == GoodCategory.WASTE || (f.id to g) in have) continue
                out += Planned(ManagerKind.SELLING, Command.SetSellRule(ctx.company, SellRule(f.id, g, PriceStrategy.MARKET, minMarginPpm = ctx.settings.minMarginPpm)), "Selling ${ctx.goodName(g)} from ${ctx.name(f)}")
            }
        }
        return out
    }

    /** Crew a facility needs: one set of each line's recipe labour, plus the building's own staff. */
    fun crewNeeded(ctx: AutomationContext, f: Facility): Map<RoleId, Long> {
        val need = java.util.TreeMap<RoleId, Long>()
        for (l in f.lines) {
            if (!l.enabled) continue
            val r = l.recipe?.let { ctx.e.content.recipeOrNull(it) } ?: continue
            for (req in r.labor) need[req.role] = (need[req.role] ?: 0L) + req.headcount
        }
        if (need.isNotEmpty()) ctx.e.content.facilityTypeOrNull(f.type)?.overheadStaff?.forEach { need[it.role] = (need[it.role] ?: 0L) + it.headcount }
        return need
    }

    fun staffing(ctx: AutomationContext): List<Planned> {
        val out = ArrayList<Planned>()
        for (f in ctx.facilities) for ((role, want) in crewNeeded(ctx, f)) {
            val have = ctx.e.workforce.cohort(CohortKey(ctx.company, f.id, role, f.region))?.target ?: 0L
            if (have >= want) continue
            val roleName = ctx.e.content.allRoles.firstOrNull { it.id == role }?.name ?: role.key
            out += Planned(ManagerKind.STAFFING, Command.SetStaffing(ctx.company, f.id, role, want), "Hiring target for $roleName at ${ctx.name(f)} raised to $want")
        }
        return out
    }

    fun research(ctx: AutomationContext): List<Planned> {
        val state = ctx.e.research.peek(ctx.company)
        val active = state?.projects()?.size ?: 0
        val queued = state?.queued()?.size ?: 0
        if (active >= ctx.e.research.params.maxConcurrent && queued > 0) return emptyList()
        val options = ctx.e.research.available(ctx.company)
        if (options.isEmpty()) return emptyList()
        val focus = ctx.settings.researchFocus
        val pool = (if (focus != null) options.filter { it.category == focus }.ifEmpty { options } else options)
        val pick = pool.minWithOrNull(compareBy({ it.researchCost.raw }, { it.id.key })) ?: return emptyList()
        val cmd = if (active < ctx.e.research.params.maxConcurrent) Command.StartResearch(ctx.company, pick.id) else Command.QueueResearch(ctx.company, pick.id)
        return listOf(Planned(ManagerKind.RESEARCH, cmd, (if (cmd is Command.StartResearch) "Started researching " else "Queued research into ") + pick.name))
    }

    fun repayment(ctx: AutomationContext): List<Planned> {
        val owner = ctx.owner ?: return emptyList()
        val spare = ctx.spendable
        if (spare < MIN_PREPAYMENT) return emptyList()
        val loan = ctx.e.finance.loans.activeFor(owner).maxWithOrNull(compareBy({ it.annualRate.ppm }, { -it.id.value })) ?: return emptyList()
        val amount = Money.min(spare, loan.outstanding)
        if (amount.raw <= 0L) return emptyList()
        return listOf(Planned(ManagerKind.REPAYMENT, Command.Repay(ctx.company, loan.id, amount), "Prepaid loan ${loan.id.value}"))
    }

    val MIN_PREPAYMENT: Money = Money.units(1_000)
}
