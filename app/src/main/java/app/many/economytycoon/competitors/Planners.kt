package app.many.economytycoon.competitors

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.mulDiv

/** Chooses selling and buying rules for the goods a company makes and consumes. */
class PricingPlanner(private val content: ContentRepository) {

    fun sellRuleFor(p: AiPersonality, stockShare: Long, facility: FacilityId, good: GoodId, reserve: Quantity): SellRule {
        val (strategy, markup) = when (p.archetype) {
            Archetype.DISCOUNTER -> PriceStrategy.UNDERCUT to (30_000L + p.aggression / 20)
            Archetype.PREMIUM -> PriceStrategy.PREMIUM to (40_000L + p.qualityFocus / 20)
            Archetype.CONSERVATIVE -> PriceStrategy.COST_PLUS to 180_000L
            else -> PriceStrategy.MARKET to (10_000L + p.qualityFocus / 50)
        }
        // A warehouse filling up forces a price cut; an empty one lets the price firm.
        val adjusted = when {
            stockShare > 750_000L -> PriceStrategy.UNDERCUT to (60_000L + markup / 2)
            else -> strategy to markup
        }
        val minMargin = when (p.archetype) { Archetype.DISCOUNTER -> 0L; Archetype.CONSERVATIVE -> 100_000L; else -> 40_000L }
        return SellRule(facility, good, adjusted.first, adjusted.second, reserve, null, minMargin, true)
    }

    fun plan(p: AiPersonality, s: Situation, recipesOf: (FacilityView) -> List<RecipeDef>): List<AiAction> {
        val out = ArrayList<AiAction>()
        for (f in s.operating) {
            val recipes = recipesOf(f)
            if (recipes.isEmpty()) continue
            val capacity = f.capacity.raw.coerceAtLeast(1L)
            val used = f.stock.values.sumOf { it.raw }
            val share = mulDiv(used, 1_000_000L, capacity)
            val inputs = recipes.flatMap { r -> r.inputs.map { it.good } }.toSet()
            for (r in recipes) for (o in r.outputs) {
                // Goods the facility feeds back into itself stay in stock.
                val consumed = recipes.firstNotNullOfOrNull { c -> c.inputs.firstOrNull { it.good == o.good } }
                val reserve = if (o.good in inputs && consumed != null) Quantity(consumed.quantity.raw * 2) else Quantity.ZERO
                val wanted = sellRuleFor(p, share, f.id, o.good, reserve)
                if (s.sellRules[f.id to o.good] != wanted) out += AiAction.SetSell(s.company, wanted)
            }
            val produced = recipes.flatMap { r -> r.outputs.map { it.good } }.toSet()
            for (r in recipes) for (i in r.inputs) {
                if (i.good in produced) continue
                val lines = f.lines.count { it.recipe == r.id }.coerceAtLeast(1)
                val target = Quantity(i.quantity.raw * lines * 6)
                val multiple = 1_050_000L + p.riskTolerance / 4
                val wanted = BuyRule(f.id, i.good, target, multiple, true)
                if (s.buyRules[f.id to i.good] != wanted) out += AiAction.SetBuy(s.company, wanted)
            }
        }
        return out
    }
}

/** Keeps lines busy with the best recipe and the right number of workers. */
class ProductionPlanner(private val content: ContentRepository, private val scanner: OpportunityScanner) {

    fun plan(p: AiPersonality, s: Situation, market: MarketOutlook): List<AiAction> {
        val out = ArrayList<AiAction>()
        val allowed = { r: RecipeDef -> r.id in s.availableRecipes }
        for (f in s.operating) {
            val best = scanner.bestRecipe(f.type, f.region, allowed)
            for (line in f.lines) {
                if (best == null) continue
                val current = line.recipe?.let { content.recipeOrNull(it) }
                val switch = when {
                    current == null -> true
                    scanner.batchMargin(current, f.region).raw < 0 && current.id != best.id -> true
                    else -> false
                }
                if (switch) out += AiAction.SetRecipe(s.company, f.id, line.id, best.id)
            }
            // Add capacity where every line is busy and the building has room.
            val running = f.lines.filter { it.recipe != null }
            if (running.isNotEmpty() && f.lines.size < f.maxLines && running.all { it.utilizationPpm > 850_000L } && best != null) {
                val buffer = s.operatingCostPerTick.raw * p.cashBufferTicks
                if (s.cash.raw > buffer * 2) out += AiAction.AddLine(s.company, f.id)
            }
            out += staffing(p, s, f, market)
        }
        return out
    }

    private fun staffing(p: AiPersonality, s: Situation, f: FacilityView, market: MarketOutlook): List<AiAction> {
        val needs = java.util.TreeMap<RoleId, Long>()
        for (line in f.lines) {
            val r = line.recipe?.let { content.recipeOrNull(it) } ?: continue
            if (!line.enabled) continue
            for (l in r.labor) needs[l.role] = (needs[l.role] ?: 0L) + l.headcount
        }
        content.facilityTypeOrNull(f.type)?.overheadStaff?.forEach { needs[it.role] = (needs[it.role] ?: 0L) + it.headcount }
        val out = ArrayList<AiAction>()
        for ((role, n) in needs) {
            val ref = market.wage(role, f.region)
            // Quality-minded firms pay above the going rate, cost-minded ones slightly below.
            val premium = (p.qualityFocus - p.costFocus) / 10
            val wage = Money(mulDiv(ref.raw, 1_000_000L + premium, 1_000_000L).coerceAtLeast(1L))
            val unemployment = market.unemploymentPpm(f.region)
            val wageAdj = if (unemployment < 40_000L) Money(mulDiv(wage.raw, 1_050_000L, 1_000_000L)) else wage
            if (s.staffTargets[f.id to role] != n) out += AiAction.SetStaffing(s.company, f.id, role, n, wageAdj)
        }
        return out
    }
}

/** Decides when to build, borrow, repay and pay dividends. */
class FinancePlanner {
    fun cashBuffer(p: AiPersonality, s: Situation): Money = Money(s.operatingCostPerTick.raw * p.cashBufferTicks)

    fun plan(p: AiPersonality, s: Situation, random: app.many.economytycoon.core.DeterministicRandom): List<AiAction> {
        val out = ArrayList<AiAction>()
        val buffer = cashBuffer(p, s)
        if (s.cash.raw < buffer.raw / 2 && s.creditAvailable.raw > 0) {
            val want = Money.min(Money(buffer.raw - s.cash.raw), s.creditAvailable)
            if (want.raw > 0) out += AiAction.Borrow(s.company, want, 180)
        } else if (s.cash.raw > buffer.raw * 4 && s.debt.raw > 0) {
            val spare = Money(s.cash.raw - buffer.raw * 2)
            val pay = Money.min(spare, s.debt)
            // Risk-tolerant firms keep leverage; others pay down.
            val share = 1_000_000L - p.riskTolerance / 2
            val amount = Money(mulDiv(pay.raw, share, 1_000_000L))
            if (amount.raw > 0) out += AiAction.Repay(s.company, amount)
        }
        val revenue = s.revenuePerTick.raw
        val rd = Money(mulDiv(revenue, p.rdShare, 1_000_000L))
        val marketing = Money(mulDiv(revenue, (p.qualityFocus / 10).coerceAtMost(60_000L), 1_000_000L))
        if (s.budgets[Department.RESEARCH] != rd) out += AiAction.SetBudget(s.company, Department.RESEARCH, rd)
        if (s.budgets[Department.MARKETING] != marketing) out += AiAction.SetBudget(s.company, Department.MARKETING, marketing)
        val policy = DividendPolicy(p.payout, (p.cashBufferTicks).coerceAtLeast(5), 90)
        if (s.dividend != policy) out += AiAction.SetDividend(s.company, policy)
        return out
    }
}
