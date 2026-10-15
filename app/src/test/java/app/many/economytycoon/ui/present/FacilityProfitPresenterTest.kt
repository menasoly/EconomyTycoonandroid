package app.many.economytycoon.ui.present

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.facilityprofit.FacilityPerformance
import app.many.economytycoon.facilityprofit.LinePerformance
import app.many.economytycoon.facilityprofit.PortfolioPerformance
import app.many.economytycoon.facilityprofit.Switch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilityProfitPresenterTest {
    private val money: (Money) -> String = { it.wholeUnits().toString() }
    private fun m(n: Long) = Money.units(n)

    private fun line(id: Long, out: Long = 1_000, util: Long = 1_000_000, enabled: Boolean = true, recipe: Boolean = true) =
        LinePerformance(LineId(id), if (recipe) RecipeId("r") else null, if (recipe) "Flour" else null, enabled, util, m(out), m(400), m(100), m(50), 0L, 2)

    private fun facility(id: Long, vararg lines: LinePerformance) = FacilityPerformance(FacilityId(id), "Mill $id", "Flour mill", "Northvale", lines.toList(), m(20), m(30), m(10_000))

    @Test fun tonesFollowTheSign() {
        assertEquals(Tone.GOOD, FacilityProfitPresenter.netTone(m(1)))
        assertEquals(Tone.BAD, FacilityProfitPresenter.netTone(m(-1)))
        assertEquals(Tone.NEUTRAL, FacilityProfitPresenter.netTone(Money.ZERO))
    }

    @Test fun emptyLinesAreExplained() {
        val r = FacilityProfitPresenter.lineRow(line(1, recipe = false), money)
        assertEquals("Empty line", r.title)
        assertEquals(Tone.MUTED, r.tone)
    }

    @Test fun pausedLinesAreMuted() {
        val r = FacilityProfitPresenter.lineRow(line(1, enabled = false), money)
        assertEquals(Tone.MUTED, r.tone)
        assertTrue(r.detail.startsWith("Paused"))
    }

    @Test fun losingLinesAreBad() {
        assertEquals(Tone.BAD, FacilityProfitPresenter.lineRow(line(1, out = 100), money).tone)
        assertEquals(Tone.GOOD, FacilityProfitPresenter.lineRow(line(1), money).tone)
    }

    @Test fun lowUtilisationIsDescribed() {
        assertTrue(FacilityProfitPresenter.lineRow(line(1, util = 200_000), money).detail.startsWith("Running 20.0%"))
        assertTrue(FacilityProfitPresenter.lineRow(line(1, util = 900_000), money).detail.startsWith("Busy 90.0%"))
    }

    @Test fun notesFlagTheMostUsefulProblem() {
        assertEquals("No production lines.", FacilityProfitPresenter.note(facility(1), money))
        assertEquals("Every line is idle.", FacilityProfitPresenter.note(facility(1, line(1, enabled = false)), money))
        assertTrue(FacilityProfitPresenter.note(facility(1, line(1, out = 100)), money)!!.contains("lose money"))
        assertTrue(FacilityProfitPresenter.note(facility(1, line(1, util = 100_000)), money)!!.contains("lost to lines"))
        assertNull(FacilityProfitPresenter.note(facility(1, line(1)), money))
    }

    @Test fun rowShowsNetReturnAndPayback() {
        val r = FacilityProfitPresenter.row(facility(1, line(1)), money)
        assertEquals("175 a day", r.net)
        assertEquals(Tone.GOOD, r.tone)
        assertEquals(listOf("Lines earn", "Upkeep and staff", "Return on cost", "Pays back in"), r.facts.map { it.label })
        assertEquals("Flour mill, Northvale", r.subtitle)
    }

    @Test fun neverPayingBackSaysNever() {
        val r = FacilityProfitPresenter.row(facility(1, line(1, out = 100)), money)
        assertEquals("Never", r.facts.last().value)
        assertEquals(Tone.BAD, r.tone)
    }

    @Test fun modelRanksBestFirstAndShowsSwitches() {
        val p = PortfolioPerformance(listOf(facility(1, line(1, out = 100)), facility(2, line(2))), listOf(Switch(FacilityId(1), LineId(1), "Flour", RecipeId("bread"), "Bread", m(40))))
        val model = FacilityProfitPresenter.model(p, money)
        assertEquals(listOf(FacilityId(2), FacilityId(1)), model.rows.map { it.id })
        assertEquals("Mill 1: Flour to Bread", model.switches.single().title)
        assertEquals("+40 a day", model.switches.single().gain)
        assertEquals("Mill 2", model.tiles[2].value)
    }

    @Test fun emptyPortfolioHasPlaceholders() {
        val model = FacilityProfitPresenter.model(PortfolioPerformance(emptyList(), emptyList()), money)
        assertTrue(model.rows.isEmpty())
        assertEquals("-", model.tiles[2].value)
        assertEquals(FacilityProfitPresenter.EMPTY, model.empty)
    }

    @Test fun switchListIsCappedAtTen() {
        val many = (1L..15L).map { Switch(FacilityId(1), LineId(it), "a", RecipeId("b"), "B", m(it)) }
        assertEquals(10, FacilityProfitPresenter.model(PortfolioPerformance(listOf(facility(1)), many), money).switches.size)
    }

    @Test fun liveSessionBuildsAModel() {
        val s = PresentSupport.session("Profit Works", 12L).also { x -> repeat(30) { x.step() } }
        val model = FacilityProfitPresenter.build(s)!!
        assertEquals(4, model.tiles.size)
    }
}
