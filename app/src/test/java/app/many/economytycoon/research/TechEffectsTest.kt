package app.many.economytycoon.research

import app.many.economytycoon.content.EffectKind
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.research.ResearchTestSupport.Rig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechEffectsTest {
    private val milling = TechId("milling"); private val ovens = TechId("ovens")
    private val efficiency = TechId("efficiency"); private val mega = TechId("megamill")
    private fun recipe(r: Rig, id: String) = r.service.content.recipe(RecipeId(id))

    @Test fun industryScopedEffectsOnlyReachThatIndustry() {
        val r = Rig(); r.service.grant(r.a, milling, Tick(0))
        assertEquals(100_000L, r.effects.total(r.a, EffectKind.THROUGHPUT_BONUS, recipe(r, "make_flour")))
        assertEquals(0L, r.effects.total(r.a, EffectKind.THROUGHPUT_BONUS, recipe(r, "dig_ore")))
        assertEquals(0L, r.effects.total(r.b, EffectKind.THROUGHPUT_BONUS, recipe(r, "make_flour")))
    }

    @Test fun recipeScopedEffectsOnlyReachThatRecipe() {
        val r = Rig(); r.service.grant(r.a, ovens, Tick(0))
        assertEquals(50_000L, r.effects.total(r.a, EffectKind.YIELD_BONUS, recipe(r, "make_bread")))
        assertEquals(0L, r.effects.total(r.a, EffectKind.YIELD_BONUS, recipe(r, "make_flour")))
    }

    @Test fun globalEffectsReachEveryRecipe() {
        val r = Rig(); r.service.grant(r.a, efficiency, Tick(0))
        for (id in listOf("make_flour", "make_bread", "dig_ore")) assertEquals(200_000L, r.effects.total(r.a, EffectKind.INPUT_SAVINGS, recipe(r, id)))
        assertEquals(100_000L, r.effects.transportSavings(r.a).ppm)
    }

    @Test fun savingsAreCappedAndBonusesStack() {
        val r = Rig()
        r.service.grant(r.a, efficiency, Tick(0)); r.service.grant(r.a, mega, Tick(0)); r.service.grant(r.a, milling, Tick(0))
        assertEquals(800_000L, r.effects.total(r.a, EffectKind.INPUT_SAVINGS, recipe(r, "make_flour")))
        assertEquals(600_000L, r.effects.total(r.a, EffectKind.THROUGHPUT_BONUS, recipe(r, "make_flour")))
    }

    @Test fun obsoleteTechnologiesFadeToTheFloor() {
        val r = Rig(); r.service.grant(r.a, efficiency, Tick(0))
        val def = r.service.content.technology(efficiency)
        assertEquals(1_000_000L, r.effects.strengthPpm(def, Tick(0), Tick(100)))
        assertEquals(750_000L, r.effects.strengthPpm(def, Tick(0), Tick(150)))
        assertEquals(500_000L, r.effects.strengthPpm(def, Tick(0), Tick(900)))
        r.effects.now = Tick(200)
        assertEquals(100_000L, r.effects.total(r.a, EffectKind.INPUT_SAVINGS, recipe(r, "make_flour")))
        assertEquals(1_000_000L, r.effects.strengthPpm(r.service.content.technology(milling), Tick(0), Tick(5_000)))
    }

    @Test fun recipesNeedTheirTechnology() {
        val r = Rig(); val bread = recipe(r, "make_bread")
        assertFalse(r.effects.recipeAvailable(r.a, bread))
        assertTrue(r.effects.recipeAvailable(r.a, recipe(r, "make_flour")))
        r.service.grant(r.a, ovens, Tick(0))
        assertTrue(r.effects.recipeAvailable(r.a, bread))
        assertFalse(r.effects.recipeAvailable(r.b, bread))
    }

    @Test fun modifiersAdapterForwardsEveryKind() {
        val r = Rig(); r.service.grant(r.a, efficiency, Tick(0)); r.service.grant(r.a, milling, Tick(0))
        val m = ResearchModifiers(r.effects); val flour = recipe(r, "make_flour")
        assertEquals(100_000L, m.throughputBonus(r.a, flour).ppm)
        assertEquals(200_000L, m.inputSavings(r.a, flour).ppm)
        assertEquals(0L, m.yieldBonus(r.a, flour).ppm)
        assertEquals(0L, m.energySavings(r.a, flour).ppm)
        assertEquals(0L, m.storageBonus(r.a).ppm)
    }

    @Test fun plannerPrefersCheapUsefulTechnologyAndFillsSlots() {
        val r = Rig()
        val started = ResearchPlanner.fillSlots(r.service, r.a, ResearchAgenda(), Tick(0))
        assertEquals(listOf(milling), started.map { it.id })
        r.service.grant(r.a, milling, Tick(0)); r.service.cancel(r.a, milling, Tick(0))
        val second = ResearchPlanner.fillSlots(r.service, r.a, ResearchAgenda(), Tick(1))
        assertEquals(2, second.size)
        assertEquals(setOf(ovens, efficiency), second.map { it.id }.toSet())
    }

    @Test fun agendaWeightsShiftTheChoice() {
        val r = Rig(); r.service.grant(r.a, milling, Tick(0))
        val managerial = ResearchAgenda(mapOf(TechCategory.MANAGEMENT to 5_000_000L, TechCategory.PRODUCTION to 100_000L))
        assertEquals(efficiency, ResearchPlanner.rank(r.service, r.a, managerial).first().id)
        val capped = ResearchAgenda(maxTier = 1)
        assertTrue(ResearchPlanner.rank(r.service, r.a, capped).all { it.tier <= 1 })
    }

    @Test fun plannerRankingIsDeterministic() {
        val a = Rig(); val b = Rig()
        a.service.grant(a.a, milling, Tick(0)); b.service.grant(b.a, milling, Tick(0))
        assertEquals(ResearchPlanner.rank(a.service, a.a, ResearchAgenda()).map { it.id }, ResearchPlanner.rank(b.service, b.a, ResearchAgenda()).map { it.id })
    }
}
