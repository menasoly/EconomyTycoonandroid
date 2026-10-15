package app.many.economytycoon.ui.present

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.production.SchedulePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilitiesPresenterTest {
    private fun withFacility() = PresentSupport.session("Build Works").also { s ->
        val c = s.selectedCompany!!
        val region = s.economy.world.regions().first().id
        val option = s.reports.planner.options(c, region).filter { it.canBuild && !it.needsDeposit }.minByOrNull { it.cost.raw }!!
        assertTrue(s.issueNow(s.reports.planner.buildCommand(c, option, region, "Pilot plant")!!).isOk)
        repeat(120) { s.step() }
    }

    @Test fun anEmptyCompanyHasAnEmptyList() {
        val m = FacilitiesPresenter.list(PresentSupport.session())
        assertTrue(m.items.isEmpty())
        assertEquals("0", m.tiles.first().value)
    }

    @Test fun aBuiltFacilityAppearsWithItsDetails() {
        val s = withFacility()
        val m = FacilitiesPresenter.list(s)
        assertEquals(1, m.items.size)
        val item = m.items.single()
        assertEquals("Pilot plant", item.title)
        assertTrue(item.condition in 0.0..1.0)
        assertTrue(item.subtitle.contains("·"))
    }

    @Test fun searchAndFilterHideRows() {
        val s = withFacility()
        assertEquals(0, FacilitiesPresenter.list(s, query = "zzzz-nothing").items.size)
        assertEquals(1, FacilitiesPresenter.list(s, query = "pilot").items.size)
        assertEquals(1, FacilitiesPresenter.list(s, query = "zzzz-nothing").hiddenByFilter)
    }

    @Test fun everySortOrderKeepsTheRows() {
        val s = withFacility()
        for (sort in FacilitySort.values()) assertEquals(1, FacilitiesPresenter.list(s, sort).items.size)
    }

    @Test fun detailDescribesLinesStockAndStatus() {
        val s = withFacility()
        val id = s.reports.company.facilities(s.selectedCompany!!).single().id
        val d = FacilityDetailPresenter.build(s, id)!!
        assertEquals("Pilot plant", d.title)
        assertTrue(d.facts.any { it.label == "Condition" })
        assertEquals(s.economy.production.facility(id)!!.lines.size, d.lines.size)
        assertEquals(d.operating, s.economy.production.facility(id)!!.status == FacilityStatus.OPERATING)
        assertTrue(d.lines.all { it.progress in 0.0..1.0 })
    }

    @Test fun unknownFacilitiesHaveNoDetail() {
        assertNull(FacilityDetailPresenter.build(PresentSupport.session(), FacilityId(9999)))
    }

    @Test fun labelsAndTonesCoverEveryCase() {
        for (st in FacilityStatus.values()) assertNotNull(FacilitiesPresenter.statusLabel(st))
        assertEquals("By line priority", FacilitiesPresenter.policyLabel(SchedulePolicy.PRIORITY))
        assertEquals("Best margin first", FacilitiesPresenter.policyLabel(SchedulePolicy.AUTO_MARGIN))
    }

    @Test fun detailOffersRecipesAndRolesForTheFacility() {
        val s = withFacility()
        val c = s.selectedCompany!!
        val id = s.reports.company.facilities(c).single().id
        val d = FacilityDetailPresenter.build(s, id)!!
        assertTrue(d.recipes.isNotEmpty())
        assertTrue(d.recipes.map { it.locked }.zipWithNext().all { (a, b) -> !a || b })
        val line = s.economy.production.facility(id)!!.lines.first()
        val recipe = d.recipes.first { !it.locked }
        val r = s.issueNow(app.many.economytycoon.simulation.Command.SetRecipe(c, id, line.id, recipe.id))
        assertTrue(r.toString(), r.isOk)
        val after = FacilityDetailPresenter.build(s, id)!!
        assertTrue(after.roles.any { it.needed > 0 })
        assertTrue(after.lines.first().title.contains(recipe.name))
        assertTrue(after.maintenanceHint.isNotBlank())
    }
}
