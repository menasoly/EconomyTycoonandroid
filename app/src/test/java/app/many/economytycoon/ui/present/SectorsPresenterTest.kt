package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.intel.Balance
import app.many.economytycoon.intel.Concentration
import app.many.economytycoon.intel.LineFlow
import app.many.economytycoon.intel.SectorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorsPresenterTest {
    private val wheat = GoodId("wheat")
    private val flour = GoodId("flour")
    private fun make(c: Long, g: GoodId, perDay: Long) = LineFlow(CompanyId(c), FacilityId(c), g, perDay, 1_000_000L, true)
    private fun use(c: Long, g: GoodId, perDay: Long) = LineFlow(CompanyId(c), FacilityId(c + 100), g, perDay, 1_000_000L, false)
    private fun sectors(ratio: Long = 1_000_000L, vararg f: LineFlow) = SectorMath.build(f.toList(), { "Co ${it.value}" }, { ratio })
    private val names: (GoodId) -> String = { it.key.replaceFirstChar { c -> c.uppercase() } }

    @Test fun tonesFollowTheMeaningForASeller() {
        assertEquals(Tone.GOOD, SectorsPresenter.priceTone(1_300_000))
        assertEquals(Tone.BAD, SectorsPresenter.priceTone(700_000))
        assertEquals(Tone.NEUTRAL, SectorsPresenter.priceTone(1_000_000))
        assertEquals(Tone.GOOD, SectorsPresenter.concentrationTone(Concentration.COMPETITIVE))
        assertEquals(Tone.WARN, SectorsPresenter.concentrationTone(Concentration.MONOPOLY))
        assertEquals(Tone.BAD, SectorsPresenter.balanceTone(Balance.UNMADE))
    }

    @Test fun priceTextSaysAboveBelowOrUsual() {
        assertEquals("At the usual price", SectorsPresenter.priceText(1_004_000))
        assertTrue(SectorsPresenter.priceText(1_250_000).endsWith("above usual"))
        assertTrue(SectorsPresenter.priceText(800_000).endsWith("below usual"))
    }

    @Test fun aRowSaysWhereTheViewerStands() {
        val s = sectors(1_000_000L, make(1, wheat, 3_000), make(2, wheat, 1_000)).single()
        val mine = SectorsPresenter.row(s, "Wheat", CompanyId(2))
        assertEquals("You are #2 of 2 with 25.0%", mine.yours)
        assertTrue(mine.producers.single { it.mine }.name == "Co 2")
        assertNull(SectorsPresenter.row(s, "Wheat", CompanyId(7)).yours)
        assertNull(SectorsPresenter.row(s, "Wheat", null).yours)
    }

    @Test fun aGoodNobodyMakesIsAnOpeningAndHasNoLeader() {
        val s = sectors(1_000_000L, use(1, flour, 500)).single()
        val r = SectorsPresenter.row(s, "Flour", CompanyId(1))
        assertTrue(r.opening)
        assertEquals("Nobody", r.leader)
        assertTrue(r.producers.isEmpty())
    }

    @Test fun theMineFilterKeepsOnlyWhatTheViewerMakes() {
        val all = sectors(1_000_000L, make(1, wheat, 100), make(2, flour, 100))
        assertEquals(listOf(wheat), SectorsPresenter.select(all, SectorFilter.MINE, CompanyId(1)).map { it.good })
        assertTrue(SectorsPresenter.select(all, SectorFilter.MINE, null).isEmpty())
    }

    @Test fun openingsAreDearestFirst() {
        val a = sectors(1_300_000L, make(1, wheat, 100)).single()
        val b = sectors(1_600_000L, make(1, flour, 100)).single()
        val picked = SectorsPresenter.select(listOf(a, b), SectorFilter.OPENINGS, null)
        assertEquals(listOf(flour, wheat), picked.map { it.good })
    }

    @Test fun cheapGoodsWithFewSellersAreNotOpenings() {
        val a = sectors(900_000L, make(1, wheat, 100))
        assertTrue(SectorsPresenter.select(a, SectorFilter.OPENINGS, null).isEmpty())
    }

    @Test fun allIsSortedByName() {
        val m = SectorsPresenter.model(sectors(1_000_000L, make(1, wheat, 100), make(1, flour, 100)), SectorFilter.ALL, null, names)
        assertEquals(listOf("Flour", "Wheat"), m.rows.map { it.name })
    }

    @Test fun theSummaryCountsGoodsWithNoMaker() {
        val m = SectorsPresenter.model(sectors(1_000_000L, make(1, wheat, 100), use(2, flour, 100)), SectorFilter.ALL, null, names)
        assertEquals("2 goods in production or in use, 1 with no maker", m.summary)
    }

    @Test fun everyFilterHasAnEmptyMessage() {
        val texts = SectorFilter.values().map { SectorsPresenter.emptyText(it) }
        assertEquals(texts.size, texts.toSet().size)
    }

    @Test fun aSessionWithNoProductionHasNoRows() {
        val s = PresentSupport.session("Sector Works", 91L)
        val m = SectorsPresenter.build(s, SectorFilter.MINE)
        assertTrue(m.rows.isEmpty())
        assertFalse(m.empty.isBlank())
    }

    @Test fun aFacilityWithARecipeShowsUnderMine() {
        val s = PresentSupport.session("Sector Works", 91L)
        val e = s.economy
        val me = s.selectedCompany!!
        val region = e.world.regions().first().id
        val f = e.production.grantFacility(me, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        e.production.setRecipe(me, f.id, f.lines[0].id, RecipeId("grow_wheat")).getOrThrow()
        val m = SectorsPresenter.build(s, SectorFilter.MINE)
        val row = m.rows.single { it.good == wheat }
        assertTrue(row.yours!!.startsWith("You are #"))
    }
}
