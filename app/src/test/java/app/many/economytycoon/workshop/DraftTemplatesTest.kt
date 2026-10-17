package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTemplatesTest {
    @Test fun templateKeysAreUniqueAndFindable() {
        val keys = DraftTemplates.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (k in keys) assertNotNull(DraftTemplates.byKey(k))
        assertNull(DraftTemplates.byKey("nonsense"))
    }

    @Test fun aTemplateTakesTheIdAndTitleItIsGiven() {
        val d = requireNotNull(DraftTemplates.byKey("sprint")).create("my-sprint", "My Sprint")
        assertEquals("my-sprint", d.id)
        assertEquals("My Sprint", d.title)
    }

    @Test fun aTemplateIsAlreadyNormalised() {
        for (t in DraftTemplates.all) assertEquals(t.key, t.create(), t.create().normalised())
    }

    @Test fun templatesDifferFromEachOther() {
        val hashes = DraftTemplates.all.map { it.create(id = "same-id", title = "Same Title").stateHash() }
        assertEquals(hashes.size, hashes.toSet().size)
    }

    @Test fun theSandboxHasNoRivalsAndOnlyBonusRichness() {
        val d = requireNotNull(DraftTemplates.byKey("sandbox")).create()
        assertEquals(0, d.world.rivals)
        assertTrue(d.goals.any { it.bonus })
        assertTrue(d.requiredGoals.isNotEmpty())
    }

    @Test fun theShoestringFloorHasAGracePeriod() {
        val d = requireNotNull(DraftTemplates.byKey("shoestring")).create()
        val floor = d.fails.single()
        assertTrue(floor.afterDay > 0)
        assertEquals(FailKind.NET_WORTH_BELOW, floor.kind)
    }

    @Test fun aBuiltInScenarioBecomesADraftThatKeepsItsNumbers() {
        val def = ScenarioCatalog.require("lean-times")
        val d = DraftTemplates.fromDef(def)
        assertEquals("lean-times-remix", d.id)
        assertEquals(def.seed, d.seed)
        assertEquals(def.capital.wholeUnits(), d.company.capital)
        assertEquals(def.aiCompanies, d.world.rivals)
        assertEquals(def.goals.map { it.kind to it.target }, d.goals.map { it.kind to it.target })
        assertEquals(def.ruinBelow, d.fails.single().threshold)
        assertEquals(def.tips, d.hints)
        assertEquals(listOf(DifficultyTag.HARSH), d.tags)
    }

    @Test fun builtInDraftsTagTheLongAndShortOnes() {
        assertTrue(DifficultyTag.MARATHON in DraftTemplates.fromDef(ScenarioCatalog.require("tycoon")).tags)
        assertTrue(DifficultyTag.STANDARD in DraftTemplates.fromDef(ScenarioCatalog.require("million-club")).tags)
    }

    @Test fun everyBuiltInHasADraftWithAFreshId() {
        val drafts = DraftTemplates.builtIns()
        assertEquals(ScenarioCatalog.all.size, drafts.size)
        for ((d, def) in drafts.zip(ScenarioCatalog.all)) assertTrue(d.id != def.id)
    }

    @Test fun builtInDraftsRoundTripThroughTheDefinitionWithoutLoss() {
        for (def in ScenarioCatalog.all) {
            val draft = DraftTemplates.fromDef(def, id = "copy-of-${def.id}")
            val back = (DraftConverter.convert(draft) as app.many.economytycoon.core.Outcome.Ok).value.def
            assertEquals(def.copy(id = "copy-of-${def.id}", title = draft.title), back.copy(title = draft.title))
            assertEquals(Money.units(def.capital.wholeUnits()), back.capital)
        }
    }

    @Test fun theBlankDraftIsWhatDefaultsSay() {
        val d = DraftDefaults.blank()
        assertEquals("new-scenario", d.id)
        assertEquals(1, d.goals.size)
        assertTrue(d.timing.deadlineDays >= d.timing.silverDays)
    }

    @Test fun seedsDifferByTitleAndAreRepeatable() {
        assertTrue(DraftDefaults.seedFor("Alpha") != DraftDefaults.seedFor("Beta"))
        assertEquals(DraftDefaults.seedFor("Alpha"), DraftDefaults.seedFor("Alpha"))
        assertTrue(DraftDefaults.seedFor("") > 0L)
    }
}
