package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.competitors.AiAction
import app.many.economytycoon.competitors.FacilityView
import app.many.economytycoon.competitors.LineView
import app.many.economytycoon.competitors.Situation
import app.many.economytycoon.competitors.TargetView
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.research.ResearchAgenda
import app.many.economytycoon.research.ResearchPlanner

class AiRoundReport {
    var companiesThinking: Int = 0
    var actionsPlanned: Int = 0
    var actionsApplied: Int = 0
    var actionsRejected: Int = 0
}

/**
 * Connects the pure AI planners to the economy: describes each company's position, asks the planner for
 * actions, and runs them through the same command executor the player uses.
 */
class AiDriver(private val e: Economy, private val executor: CommandExecutor) {
    private val window = 30L

    fun run(tick: Tick): AiRoundReport {
        val report = AiRoundReport()
        for (id in e.competitors.dueCompanies(tick).sorted()) {
            val c = e.companies.company(id) ?: continue
            if (c.status != CompanyStatus.ACTIVE) continue
            report.companiesThinking++
            val actions = e.competitors.think(situation(id, tick), e.random)
            report.actionsPlanned += actions.size
            for (a in actions) {
                var ok = true
                for (cmd in toCommands(a)) {
                    if (executor.execute(Actor.Ai(id), cmd) is Outcome.Rejected) { ok = false; break }
                }
                if (ok) report.actionsApplied++ else report.actionsRejected++
            }
        }
        return report
    }

    // ---- describing a company ------------------------------------------------------------------

    fun situation(id: CompanyId, tick: Tick): Situation {
        val c = e.companies.require(id)
        val owner = c.owner
        val from = Tick((tick.value - window).coerceAtLeast(0L))
        val income = e.finance.statements.income(owner, from, tick)
        val span = (tick.value - from.value).coerceAtLeast(1L)
        val expenses = income.cogs + income.wages + income.energy + income.rent + income.maintenance + income.transport +
            income.marketing + income.research + income.waste + income.otherExpenses
        val debt = e.finance.loans.debtOf(owner)
        val terms = e.finance.creditTerms(owner, tick)
        val rec = e.research.of(id)

        val facilities = e.production.facilitiesOf(id).sortedBy { it.id }.map { f ->
            FacilityView(
                f.id, f.type, f.region, f.status == FacilityStatus.OPERATING,
                f.lines.map { LineView(it.id, it.recipe, it.enabled, it.blocked.code, it.utilizationPpm) },
                e.content.facilityType(f.type).maxLines,
                f.inventory.goods().associateWith { f.inventory.quantity(it) },
                f.inventory.capacity,
                e.facilityStats.profitOf(f.id),
                tick.value - f.built.value,
            )
        }
        val unlocked: (app.many.economytycoon.core.TechId?) -> Boolean = { it == null || e.research.isUnlocked(id, it) }
        val recipes = e.content.allRecipes.filter { unlocked(it.requiredTech) }.map { it.id }.toSet()
        val types = e.content.allFacilityTypes.filter { unlocked(it.requiredTech) }.map { it.id }.toSet()

        return Situation(
            company = id, tick = tick, cash = e.finance.ledger.cash(owner), debt = debt,
            creditAvailable = Money.max(Money.ZERO, terms.limit - debt),
            operatingCostPerTick = Money.max(Money.max(Money.units(100), Money(expenses.raw / span)), e.aiReserve.dailyCost(id)),
            revenuePerTick = Money(income.revenue.raw / span),
            facilities = facilities,
            sellRules = e.companies.policies.sellRules(id).associateBy { it.facility to it.good },
            buyRules = e.companies.policies.buyRules(id).associateBy { it.facility to it.good },
            budgets = app.many.economytycoon.companies.Department.values().associateWith { e.companies.budgets(id).budget(it) },
            dividend = e.companies.policies.dividend(id),
            staffTargets = staffTargets(id),
            researchSlotsFree = (e.research.params.maxConcurrent - rec.projects().size).coerceAtLeast(0),
            researchRanked = ResearchPlanner.rank(e.research, id, ResearchAgenda()).map { it.id },
            availableRecipes = recipes,
            availableFacilityTypes = types,
            targets = targetsFor(id),
            homeRegions = e.world.regionsOf(c.country).map { it.id }.sorted(),
            pendingBuilds = e.production.openProjectsOf(id).size,
        )
    }

    private fun staffTargets(id: CompanyId): Map<Pair<app.many.economytycoon.core.FacilityId, app.many.economytycoon.core.RoleId>, Long> {
        val out = LinkedHashMap<Pair<app.many.economytycoon.core.FacilityId, app.many.economytycoon.core.RoleId>, Long>()
        for (cohort in e.workforce.cohortsOf(id)) out[cohort.key.facility to cohort.key.role] = cohort.target
        return out
    }

    private fun targetsFor(self: CompanyId): List<TargetView> {
        val tick = e.tick
        val out = ArrayList<TargetView>()
        for (t in e.companies.active().sortedBy { it.id }) {
            if (t.id == self || t.status != CompanyStatus.ACTIVE || t.kind == app.many.economytycoon.companies.CompanyKind.STATE) continue
            val table = e.finance.equity.table(t.owner) ?: continue
            val total = table.outstanding("common")
            if (total <= 0L) continue
            val mine = table.held(app.many.economytycoon.finance.Holder.owner(LedgerOwner.company(self)), "common")
            val equity = e.finance.statements.balance(t.owner, tick).totalEquity
            val fair = Money(Money.max(equity, Money.units(1)).raw / total)
            out += TargetView(t.id, Money.max(fair, Money(1)), total - mine, total, t.industries.toSet())
        }
        return out
    }

    // ---- turning decisions into commands -------------------------------------------------------

    fun toCommands(a: AiAction): List<Command> = when (a) {
        is AiAction.Build -> buildCommands(a)
        is AiAction.AddLine -> listOf(Command.AddLine(a.company, a.facility))
        is AiAction.SetRecipe -> listOf(Command.SetRecipe(a.company, a.facility, a.line, a.recipe))
        is AiAction.Shutdown -> listOf(Command.Shutdown(a.company, a.facility))
        is AiAction.SetStaffing -> listOf(Command.SetStaffing(a.company, a.facility, a.role, a.target, a.wage))
        is AiAction.SetSell -> listOf(Command.SetSellRule(a.company, a.rule))
        is AiAction.SetBuy -> listOf(Command.SetBuyRule(a.company, a.rule))
        is AiAction.SetBudget -> listOf(Command.SetBudget(a.company, a.department, a.perTick))
        is AiAction.SetDividend -> listOf(Command.SetDividend(a.company, a.policy))
        is AiAction.Borrow -> listOf(Command.Borrow(a.company, a.amount, a.termTicks))
        is AiAction.Repay -> listOf(Command.Repay(a.company, null, a.amount))
        is AiAction.StartResearch -> listOf(Command.StartResearch(a.company, a.tech))
        is AiAction.Bid -> listOf(Command.MakeOffer(a.company, a.target, a.shares, a.pricePerShare))
    }

    private fun buildCommands(a: AiAction.Build): List<Command> {
        val def = e.content.facilityTypeOrNull(a.type) ?: return emptyList()
        val extracts: GoodId? = def.extracts
        if (extracts == null) return listOf(Command.Build(a.company, a.type, a.region, e.world.citiesIn(a.region).maxByOrNull { e.world.population(it.id).total }?.id))
        val deposit = e.world.deposits.unclaimed(a.region).filter { it.good == extracts }.minByOrNull { it.id } ?: return emptyList()
        val out = ArrayList<Command>()
        if (!deposit.surveyed) out += Command.SurveyDeposit(a.company, deposit.id)
        out += Command.ClaimDeposit(a.company, deposit.id)
        out += Command.Build(a.company, a.type, a.region, null, null, deposit.id)
        return out
    }

}
