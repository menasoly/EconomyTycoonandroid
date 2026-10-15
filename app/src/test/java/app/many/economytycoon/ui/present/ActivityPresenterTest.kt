package app.many.economytycoon.ui.present

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.ActivityEntry
import app.many.economytycoon.activity.Origin
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityPresenterTest {
    private fun e(seq: Long, tick: Long, area: ActivityArea, text: String = "Did something", origin: Origin = Origin.PLAYER, money: Boolean = false) =
        ActivityEntry(seq, tick, origin, area, text, money)

    @Test fun daysAreNamedRelativeToToday() {
        assertEquals("Today", ActivityPresenter.dayLabel(50, 50))
        assertEquals("Yesterday", ActivityPresenter.dayLabel(49, 50))
        assertTrue(ActivityPresenter.dayLabel(45, 50).startsWith("5 days ago"))
        assertFalse(ActivityPresenter.dayLabel(5, 50).contains("days ago"))
    }

    @Test fun rowsCarryOriginAndTone() {
        val r = ActivityPresenter.row(e(1, 0, ActivityArea.FINANCE, origin = Origin.AI))
        assertEquals("Rival", r.origin)
        assertEquals(Tone.INFO, r.tone)
        assertEquals("Finance", r.area)
        assertEquals(Tone.MUTED, ActivityPresenter.originTone(Origin.SYSTEM))
    }

    @Test fun theModelGroupsByDayNewestFirst() {
        val entries = listOf(e(3, 10, ActivityArea.TRADE), e(2, 10, ActivityArea.FINANCE), e(1, 4, ActivityArea.COMPANY))
        val m = ActivityPresenter.model(entries, null, 10)
        assertEquals(listOf("Today", "6 days ago"), m.days.map { it.label.substringBefore(" ·") })
        assertEquals(2, m.days[0].rows.size)
        assertEquals(3, m.count)
    }

    @Test fun chosenAreaNarrowsTheListButNotTheChips() {
        val entries = listOf(e(3, 10, ActivityArea.TRADE), e(2, 10, ActivityArea.FINANCE), e(1, 4, ActivityArea.TRADE))
        val m = ActivityPresenter.model(entries, ActivityArea.TRADE, 10)
        assertEquals(2, m.count)
        assertEquals(setOf(ActivityArea.TRADE, ActivityArea.FINANCE), m.areas.toSet())
        assertEquals(ActivityArea.TRADE, m.chosen)
    }

    @Test fun emptyMessagesDependOnWhyItIsEmpty() {
        assertEquals(ActivityPresenter.EMPTY, ActivityPresenter.model(emptyList(), null, 0).empty)
        assertEquals("Nothing in this area.", ActivityPresenter.model(listOf(e(1, 0, ActivityArea.TRADE)), ActivityArea.FINANCE, 0).empty)
    }

    @Test fun anOrderGivenInTheGameAppearsInPlainWords() {
        val s = PresentSupport.session("Activity Works", 71L)
        val me = s.selectedCompany!!
        val region = s.economy.world.regions().first().id
        assertTrue(ActivityPresenter.build(s, null)!!.days.isEmpty())
        val r = s.issueNow(Command.Build(me, FacilityTypeId("grain_farm"), region))
        assertTrue(r.toString(), r.isOk)
        val m = ActivityPresenter.build(s, null)!!
        val row = m.days.single().rows.single()
        assertTrue(row.text, row.text.startsWith("Started building grain farm in"))
        assertEquals("Facilities", row.area)
        assertEquals("Today", m.days.single().label)
    }

    @Test fun namesAndMoneyFollowTheSettings() {
        val s = PresentSupport.session("Activity Works", 71L)
        val me = s.selectedCompany!!
        s.economy.finance.mint(s.tick, app.many.economytycoon.finance.LedgerOwner.bank(), Money.units(5_000_000), "test:lender")
        s.issueNow(Command.Borrow(me, Money.units(10_000), 90))
        val text = ActivityPresenter.build(s, null)!!.days.single().rows.single().text
        assertTrue(text, text.startsWith("Borrowed ") && text.contains("10"))
        assertNotNull(s.describeNames())
    }
}
