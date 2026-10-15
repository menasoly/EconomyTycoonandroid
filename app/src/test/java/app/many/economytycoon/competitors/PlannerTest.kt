package app.many.economytycoon.competitors

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.competitors.CompetitorTestSupport.baker
import app.many.economytycoon.competitors.CompetitorTestSupport.bread
import app.many.economytycoon.competitors.CompetitorTestSupport.content
import app.many.economytycoon.competitors.CompetitorTestSupport.facility
import app.many.economytycoon.competitors.CompetitorTestSupport.flour
import app.many.economytycoon.competitors.CompetitorTestSupport.line
import app.many.economytycoon.competitors.CompetitorTestSupport.situation
import app.many.economytycoon.competitors.CompetitorTestSupport.wheat
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.TechId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private val market = CompetitorTestSupport.Outlook()
    private val scanner = OpportunityScanner(content, market)
    private val pricing = PricingPlanner(content)
    private val production = ProductionPlanner(content, scanner)
    private val finance = FinancePlanner()
    private fun p(a: Archetype) = Personalities.of(a)
    private fun recipesOf(f: FacilityView) = f.lines.mapNotNull { it.recipe }.distinct().map { content.recipe(it) }
    private val rnd get() = RandomProvider(3).stream("t")

    // ----- pricing -----

    @Test fun archetypesPickDifferentStrategies() {
        val g = FacilityId(1)
        assertEquals(PriceStrategy.UNDERCUT, pricing.sellRuleFor(p(Archetype.DISCOUNTER), 100_000, g, bread, Quantity.ZERO).strategy)
        assertEquals(PriceStrategy.PREMIUM, pricing.sellRuleFor(p(Archetype.PREMIUM), 100_000, g, bread, Quantity.ZERO).strategy)
        assertEquals(PriceStrategy.COST_PLUS, pricing.sellRuleFor(p(Archetype.CONSERVATIVE), 100_000, g, bread, Quantity.ZERO).strategy)
        assertEquals(PriceStrategy.MARKET, pricing.sellRuleFor(p(Archetype.BALANCED), 100_000, g, bread, Quantity.ZERO).strategy)
    }

    @Test fun fullWarehouseForcesAPriceCut() {
        val rule = pricing.sellRuleFor(p(Archetype.PREMIUM), 900_000, FacilityId(1), bread, Quantity.ZERO)
        assertEquals(PriceStrategy.UNDERCUT, rule.strategy)
        assertTrue(rule.markupPpm >= 60_000L)
    }

    @Test fun newFacilitiesGetSellAndBuyRules() {
        val f = facility(1, listOf(line(1, "make_bread")))
        val actions = pricing.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), ::recipesOf)
        assertEquals(1, actions.filterIsInstance<AiAction.SetSell>().size)
        val buy = actions.filterIsInstance<AiAction.SetBuy>().single()
        assertEquals(flour, buy.rule.good)
        assertEquals(Quantity.units(12), buy.rule.target)
    }

    @Test fun matchingRulesAreNotReissued() {
        val f = facility(1, listOf(line(1, "make_bread")))
        val personality = p(Archetype.BALANCED)
        val first = pricing.plan(personality, situation(facilities = listOf(f)), ::recipesOf)
        val sells = first.filterIsInstance<AiAction.SetSell>().associate { (it.rule.facility to it.rule.good) to it.rule }
        val buys = first.filterIsInstance<AiAction.SetBuy>().associate { (it.rule.facility to it.rule.good) to it.rule }
        val second = pricing.plan(personality, situation(facilities = listOf(f), sells = sells, buys = buys), ::recipesOf)
        assertTrue(second.isEmpty())
    }

    @Test fun goodsProducedInHouseAreNotBought() {
        val f = facility(1, listOf(line(1, "make_flour"), line(2, "make_bread")))
        val actions = pricing.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), ::recipesOf)
        val buys = actions.filterIsInstance<AiAction.SetBuy>().map { it.rule.good }
        assertEquals(listOf(wheat), buys)
        val flourSell = actions.filterIsInstance<AiAction.SetSell>().first { it.rule.good == flour }
        assertTrue(flourSell.rule.reserve.raw > 0)
    }

    @Test fun shutDownFacilitiesAreIgnored() {
        val f = facility(1, listOf(line(1, "make_bread")), operating = false)
        assertTrue(pricing.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), ::recipesOf).isEmpty())
    }

    // ----- production -----

    @Test fun emptyLinesGetTheBestRecipe() {
        val f = facility(1, listOf(line(1, null), line(2, "make_bread")))
        val actions = production.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), market)
        val set = actions.filterIsInstance<AiAction.SetRecipe>().single()
        assertEquals(LineId(1), set.line); assertEquals(RecipeId("make_bread"), set.recipe)
    }

    @Test fun losingRecipesAreSwitched() {
        market.multiplier[flour] = 100_000L
        val f = facility(1, listOf(line(1, "make_flour")))
        val set = production.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), market).filterIsInstance<AiAction.SetRecipe>().single()
        assertEquals(RecipeId("make_bread"), set.recipe)
        market.multiplier.clear()
    }

    @Test fun lineIsAddedWhenEverythingIsBusyAndCashIsComfortable() {
        val busy = facility(1, listOf(line(1, "make_bread", util = 950_000L)))
        val rich = production.plan(p(Archetype.BALANCED), situation(cash = 1_000_000, facilities = listOf(busy)), market)
        assertEquals(1, rich.filterIsInstance<AiAction.AddLine>().size)
        val poor = production.plan(p(Archetype.BALANCED), situation(cash = 1_000, facilities = listOf(busy)), market)
        assertTrue(poor.none { it is AiAction.AddLine })
        val idle = facility(1, listOf(line(1, "make_bread", util = 300_000L)))
        assertTrue(production.plan(p(Archetype.BALANCED), situation(facilities = listOf(idle)), market).none { it is AiAction.AddLine })
    }

    @Test fun staffingFollowsTheLinesAndWagesTheMarket() {
        val f = facility(1, listOf(line(1, "make_flour"), line(2, "make_bread")))
        val staff = production.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), market).filterIsInstance<AiAction.SetStaffing>().single()
        assertEquals(baker, staff.role); assertEquals(3L, staff.target)
        assertEquals(Money.units(5), staff.wage)
    }

    @Test fun tightLabourMarketRaisesOffers() {
        market.unemployment = 20_000L
        val f = facility(1, listOf(line(1, "make_bread")))
        val staff = production.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), market).filterIsInstance<AiAction.SetStaffing>().single()
        assertTrue(staff.wage > Money.units(5))
        market.unemployment = 80_000L
    }

    @Test fun disabledLinesNeedNoStaff() {
        val f = facility(1, listOf(line(1, "make_bread", enabled = false)))
        assertTrue(production.plan(p(Archetype.BALANCED), situation(facilities = listOf(f)), market).none { it is AiAction.SetStaffing })
    }

    // ----- finance -----

    @Test fun borrowsWhenCashIsThinAndCreditExists() {
        val s = situation(cash = 5_000, opex = 1_000, credit = 500_000)
        val borrow = finance.plan(p(Archetype.BALANCED), s, rnd).filterIsInstance<AiAction.Borrow>().single()
        assertEquals(Money.units(20_000), borrow.amount)
        assertTrue(finance.plan(p(Archetype.BALANCED), situation(cash = 5_000, opex = 1_000, credit = 0), rnd).none { it is AiAction.Borrow })
    }

    @Test fun repaysDebtWhenCashRich() {
        val s = situation(cash = 500_000, debt = 100_000, opex = 1_000)
        val cautious = finance.plan(p(Archetype.CONSERVATIVE), s, rnd).filterIsInstance<AiAction.Repay>().single()
        val bold = finance.plan(p(Archetype.EXPANDER), s, rnd).filterIsInstance<AiAction.Repay>().single()
        assertTrue(cautious.amount > bold.amount)
    }

    @Test fun budgetsAndDividendsFollowThePersonality() {
        val s = situation(revenue = 10_000)
        val actions = finance.plan(p(Archetype.INNOVATOR), s, rnd)
        val rd = actions.filterIsInstance<AiAction.SetBudget>().first { it.department == Department.RESEARCH }
        assertEquals(Money.units(900), rd.perTick)
        val div = actions.filterIsInstance<AiAction.SetDividend>().single()
        assertEquals(100_000L, div.policy.payoutPpm)
    }

    @Test fun matchingBudgetsAreLeftAlone() {
        val personality = p(Archetype.BALANCED)
        val first = finance.plan(personality, situation(revenue = 10_000), rnd)
        val budgets = first.filterIsInstance<AiAction.SetBudget>().associate { it.department to it.perTick }
        val div = first.filterIsInstance<AiAction.SetDividend>().single().policy
        val second = finance.plan(personality, situation(revenue = 10_000, budgets = budgets, dividend = div), rnd)
        assertTrue(second.isEmpty())
    }

    // ----- research -----

    @Test fun researchFillsFreeSlotsInOrder() {
        val ranked = listOf(TechId("a"), TechId("b"), TechId("c"))
        val picks = ResearchChooser().plan(situation(slots = 2, ranked = ranked)).filterIsInstance<AiAction.StartResearch>()
        assertEquals(listOf(TechId("a"), TechId("b")), picks.map { it.tech })
        assertTrue(ResearchChooser().plan(situation(slots = 0, ranked = ranked)).isEmpty())
    }
}
