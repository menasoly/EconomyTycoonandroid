package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintControllerTest {
    private val saves = MemorySaveStore()
    private val host = FakeHost().also { it.put(T.view(id = 7)) }
    private fun controller(h: BlueprintHost? = host, store: MemorySaveStore = saves) = BlueprintController.open(store) { h }

    @Test fun captureSavesTheFacilitySetup() {
        val c = controller()
        val e = c.capture(FacilityId(7), folder = "mine", tags = listOf("bread")).getOrThrow()
        assertEquals("Bakery 7", e.name)
        assertEquals("test@1.4", e.blueprint.contentTag)
        assertEquals(10L, e.blueprint.createdDay)
        assertEquals(T.sampleSpec().copy(staff = T.sampleSpec().staff.map { it.copy(wage = null) }).canonicalHash(), e.hash)
        assertEquals(1, c.library.size)
    }

    @Test fun captureTwiceIsRefusedAsDuplicate() {
        val c = controller()
        c.capture(FacilityId(7)).getOrThrow()
        val again = c.capture(FacilityId(7))
        assertEquals(Rules.DUPLICATE, again.rejectionOrNull()?.rule)
        assertTrue(again.rejectionOrNull()!!.message.contains("Bakery 7"))
    }

    @Test fun captureOfUnknownFacilityOrWithoutGameFails() {
        assertEquals(Rules.UNKNOWN_ENTITY, controller().capture(FacilityId(99)).rejectionOrNull()?.rule)
        assertEquals(Rules.WRONG_STATE, controller(null).capture(FacilityId(7)).rejectionOrNull()?.rule)
    }

    @Test fun everyChangeIsSavedAndReloads() {
        val c = controller()
        val e = c.capture(FacilityId(7)).getOrThrow()
        c.edit { it.rename(e.id, "Renamed") }
        c.edit { it.addTag(e.id, "x") }
        val again = controller()
        assertEquals("Renamed", again.library.get(e.id)!!.name)
        assertEquals(listOf("x"), again.library.get(e.id)!!.tags)
        assertEquals(c.library.stateHash(), again.library.stateHash())
        assertTrue(again.loadReport!!.isClean)
    }

    @Test fun importGoesThroughTheLibraryAndSaves() {
        val c = controller()
        val report = c.importText(BlueprintText.encode(T.sample()), "shared")
        assertEquals(1, report.imported)
        assertEquals("shared", c.library.all().single().folder)
        assertEquals(1, controller().library.size)
        assertEquals(10L, c.library.all().single().addedDay)
    }

    @Test fun exportRoundTripsThroughAnotherController() {
        val c = controller()
        c.capture(FacilityId(7))
        val text = c.exportText(c.library.all().map { it.id })
        val other = controller(store = MemorySaveStore())
        assertEquals(1, other.importText(text).imported)
        assertEquals(c.library.all().single().hash, other.library.all().single().hash)
    }

    @Test fun analysisCombinesCompatibilityValidationAndCost() {
        val c = controller()
        val id = c.capture(FacilityId(7)).getOrThrow().id
        val a = c.analysis(id)!!
        assertEquals(Compatibility.COMPATIBLE, a.compat.status)
        assertTrue(a.canApply)
        assertNotNull(a.cost)
        host.tech = T.known()
        val locked = c.analysis(id)!!
        assertFalse(locked.canApply)
        assertTrue(locked.validation.has(IssueCodes.TECH_MISSING))
        assertNull(c.analysis(BlueprintId(77)))
    }

    @Test fun planExistingUsesTheFacilityAndCash() {
        val c = controller()
        val id = c.capture(FacilityId(7)).getOrThrow().id
        host.put(T.view(BlueprintSpec(T.bakery, List(3) { T.line(null, priority = it) }), id = 8))
        val plan = c.planExisting(id, FacilityId(8)).getOrThrow()
        assertFalse(plan.isBlocked)
        assertTrue(plan.steps.isNotEmpty())
        host.cash = Money.units(5)
        val poor = c.planExisting(id, FacilityId(8)).getOrThrow()
        assertTrue(poor.problems.any { it.reason == SkipReason.UNAFFORDABLE })
        assertEquals(Rules.UNKNOWN_ENTITY, c.planExisting(id, FacilityId(50)).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, c.planExisting(BlueprintId(50), FacilityId(8)).rejectionOrNull()?.rule)
    }

    @Test fun beginNewRegistersAPendingRequestAndReturnsTheBuild() {
        val c = controller()
        val id = c.capture(FacilityId(7)).getOrThrow().id
        val began = c.beginNew(id, T.region, "North bakery").getOrThrow()
        val build = began.plan.commands(host.company, null, emptyList()).single() as Command.Build
        assertEquals("North bakery", build.name)
        assertEquals(app.many.economytycoon.core.CityId(3), build.city)
        assertEquals(1, c.pending.size)
        assertEquals(listOf(7L), began.pending.knownFacilities)
        assertEquals(1, controller().pending.size)
    }

    @Test fun finishedBuildingIsConfiguredOnce() {
        val c = controller()
        val id = c.capture(FacilityId(7)).getOrThrow().id
        c.beginNew(id, T.region, "North bakery").getOrThrow()
        assertTrue(c.readyPending().isEmpty())
        host.put(T.view(BlueprintSpec(T.bakery, listOf(T.line(null))), id = 20).copy(name = "North bakery"))
        val ready = c.readyPending().single()
        assertEquals(FacilityId(20), ready.facility)
        val plan = c.finishPending(ready).getOrThrow()
        assertFalse(plan.isBlocked)
        assertTrue(plan.steps.none { it.action is StepAction.Build })
        assertEquals(0, c.pending.size)
        assertTrue(c.readyPending().isEmpty())
    }

    @Test fun beginNewIsBlockedByMissingResearchOrCash() {
        val c = controller()
        val id = c.capture(FacilityId(7)).getOrThrow().id
        host.cash = Money.units(10)
        assertEquals(Rules.WRONG_STATE, c.beginNew(id, T.region).rejectionOrNull()?.rule)
        assertEquals(0, c.pending.size)
    }

    @Test fun pendingBookHasACapacity() {
        val c = controller()
        val id = c.capture(FacilityId(7)).getOrThrow().id
        repeat(PendingApplyBook.MAX) { c.beginNew(id, T.region, "B$it").getOrThrow() }
        assertEquals(Rules.LIMIT_EXCEEDED, c.beginNew(id, T.region, "one more").rejectionOrNull()?.rule)
    }

    @Test fun oldRequestsExpire() {
        val c = controller()
        val id = c.capture(FacilityId(7)).getOrThrow().id
        c.beginNew(id, T.region, "Slow").getOrThrow()
        host.day += 400
        assertEquals(1, c.expirePending().size)
        assertEquals(0, c.pending.size)
        assertEquals(0, controller().pending.size)
    }
}
