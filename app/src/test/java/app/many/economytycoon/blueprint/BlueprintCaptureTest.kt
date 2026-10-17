package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.ProductionService
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.workforce.CohortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintCaptureTest {
    private val company = CompanyId(1)

    private fun liveFacility(svc: ProductionService): app.many.economytycoon.production.Facility {
        val f = svc.grantFacility(company, T.bakery, T.region, null, null, null, Tick(0), "Live bakery")
        svc.addLine(company, f.id).getOrThrow()
        svc.addLine(company, f.id).getOrThrow()
        svc.installMachine(company, f.id, f.lines[1].id, T.oven, Tick(0)).getOrThrow()
        svc.installMachine(company, f.id, f.lines[2].id, T.oven, Tick(0)).getOrThrow()
        svc.installMachine(company, f.id, f.lines[2].id, T.mixer, Tick(0)).getOrThrow()
        svc.setRecipe(company, f.id, f.lines[0].id, T.makeFlour).getOrThrow()
        svc.setRecipe(company, f.id, f.lines[1].id, T.makeBread).getOrThrow()
        svc.setRecipe(company, f.id, f.lines[2].id, T.makeSourdough).getOrThrow()
        svc.setPolicy(company, f.id, SchedulePolicy.AUTO_MARGIN).getOrThrow()
        svc.setQualityAssurance(company, f.id, Rate(300_000L)).getOrThrow()
        return f
    }

    @Test fun readsALiveFacilityIntoAView() {
        val svc = ProductionService(IdAllocators(), T.content)
        val f = liveFacility(svc)
        val sell = SellRule(f.id, T.bread, reserve = Quantity.units(20))
        val buy = BuyRule(f.id, T.wheat, Quantity.units(80))
        val view = FacilityViewReader.read(f, T.content, listOf(sell), listOf(buy))
        assertNotNull(view)
        view!!
        assertEquals(3, view.lines.size)
        assertEquals(listOf(T.makeFlour, T.makeBread, T.makeSourdough), view.lines.map { it.recipe })
        assertEquals(listOf(T.mixer, T.oven), view.lines[2].machines)
        assertEquals(SchedulePolicy.AUTO_MARGIN, view.policy)
        assertEquals(300_000L, view.qualityPpm)
        assertEquals(1, view.sells.size)
        assertEquals(1, view.buys.size)
        assertEquals(0, view.storageExpansions)
        assertTrue(view.operating)
    }

    @Test fun demolishedFacilitiesHaveNoView() {
        val svc = ProductionService(IdAllocators(), T.content)
        val f = liveFacility(svc)
        svc.demolish(company, f.id).getOrThrow()
        assertNull(FacilityViewReader.read(f, T.content))
    }

    @Test fun otherFacilitiesRulesAreIgnored() {
        val svc = ProductionService(IdAllocators(), T.content)
        val f = liveFacility(svc)
        val other = svc.grantFacility(company, T.bakery, T.region, null, null, null, Tick(0))
        val view = FacilityViewReader.read(f, T.content, listOf(SellRule(other.id, T.bread)), listOf(BuyRule(other.id, T.wheat, Quantity.units(1))))!!
        assertTrue(view.sells.isEmpty() && view.buys.isEmpty())
    }

    @Test fun storageExpansionsAreCountedFromCapacity() {
        val base = Quantity.units(1_000)
        assertEquals(0, FacilityViewReader.expansionsOf(base, base))
        assertEquals(1, FacilityViewReader.expansionsOf(base, Quantity.units(1_250)))
        assertEquals(2, FacilityViewReader.expansionsOf(base, Quantity(1_562_500L)))
        assertEquals(BlueprintLimits.MAX_STORAGE_EXPANSIONS, FacilityViewReader.expansionsOf(base, Quantity.units(1_000_000)))
    }

    @Test fun capturePreservesTheSetupAndNormalizes() {
        val view = T.view()
        val bp = BlueprintCapture.capture(view, options = CaptureOptions.ALL, contentTag = "test@1.4", day = 3).getOrThrow()
        assertEquals(T.sampleSpec(), bp.spec)
        assertEquals("Bakery 7", bp.name)
        assertEquals("test@1.4", bp.contentTag)
        assertEquals(3L, bp.createdDay)
    }

    @Test fun optionsDropTheirParts() {
        val view = T.view()
        val bare = BlueprintCapture.capture(view, options = CaptureOptions(staffing = false, trading = false, storage = false, schedulePolicy = false, quality = false)).getOrThrow()
        assertTrue(bare.spec.staff.isEmpty() && bare.spec.sells.isEmpty() && bare.spec.buys.isEmpty())
        assertEquals(0, bare.spec.storageExpansions)
        assertEquals(SchedulePolicy.PRIORITY, bare.spec.policy)
        assertEquals(0L, bare.spec.qualityPpm)
        assertEquals(3, bare.spec.lines.size)
    }

    @Test fun wagesAreLeftOutUnlessAsked() {
        val view = T.view()
        val plain = BlueprintCapture.capture(view).getOrThrow()
        assertTrue(plain.spec.staff.all { it.wage == null })
        val withWages = BlueprintCapture.capture(view, options = CaptureOptions(wages = true)).getOrThrow()
        assertEquals(Money.units(105), withWages.spec.staff.first { it.role == T.baker }.wage)
        val noTraining = BlueprintCapture.capture(view, options = CaptureOptions(training = false)).getOrThrow()
        assertTrue(noTraining.spec.staff.all { it.trainingPpm == 0L })
    }

    @Test fun sameSetupWithDifferentPriorityNumbersCapturesToTheSameHash() {
        val spec = T.sampleSpec()
        val shifted = spec.copy(lines = spec.lines.map { it.copy(priority = it.priority * 10 + 4) })
        val a = BlueprintCapture.capture(T.view(spec)).getOrThrow()
        val b = BlueprintCapture.capture(T.view(shifted)).getOrThrow()
        assertEquals(a.hash, b.hash)
    }

    @Test fun emptyFacilityIsRefused() {
        val view = T.view().copy(lines = emptyList())
        val out = BlueprintCapture.capture(view)
        assertFalse(out.isOk)
        assertEquals(Rules.INVALID_ARGUMENT, out.rejectionOrNull()?.rule)
    }

    @Test fun blankNameFallsBackToTheTypeAndLongNamesAreCut() {
        val blank = BlueprintCapture.capture(T.view().copy(name = " ")).getOrThrow()
        assertEquals("bakery", blank.name)
        val long = BlueprintCapture.capture(T.view(), name = "n".repeat(100)).getOrThrow()
        assertEquals(BlueprintLimits.MAX_NAME, long.name.length)
    }

    @Test fun staffFromCohortsComesThroughTheReader() {
        val svc = ProductionService(IdAllocators(), T.content)
        val f = liveFacility(svc)
        val ids = IdAllocators()
        val c = app.many.economytycoon.workforce.Cohort(ids.cohort(), CohortKey(company, f.id, T.baker, T.region), Money.units(120))
        c.target = 5
        val view = FacilityViewReader.read(f, T.content, cohorts = listOf(c))!!
        assertEquals(listOf(StaffSpec(T.baker, 5, Money.units(120), 0L)), view.staff)
    }
}
