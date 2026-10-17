package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.energygrid.GridWorld
import app.many.economytycoon.energygrid.OutageEvent
import app.many.economytycoon.energygrid.OutageKind
import app.many.economytycoon.energygrid.PpaProfile
import app.many.economytycoon.energygrid.Technology
import app.many.economytycoon.energygrid.richWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGridPresenterTest {
    private val money: (Money) -> String = { it.wholeUnits().toString() }

    @Test fun powerUsesTheRightUnit() {
        assertEquals("500 kW", EnergyGridPresenter.power(500L))
        assertEquals("12 MW", EnergyGridPresenter.power(12_000L))
        assertEquals("1.5 GW", EnergyGridPresenter.power(1_500_000L))
    }

    @Test fun energyUsesTheRightUnit() {
        assertEquals("900 kWh", EnergyGridPresenter.energy(900L))
        assertEquals("2 MWh", EnergyGridPresenter.energy(2_000L))
        assertEquals("3 GWh", EnergyGridPresenter.energy(3_000_000L))
    }

    @Test fun reserveTonesFollowTheTarget() {
        assertEquals(Tone.BAD, EnergyGridPresenter.reserveTone(-1L, 150_000L))
        assertEquals(Tone.WARN, EnergyGridPresenter.reserveTone(100_000L, 150_000L))
        assertEquals(Tone.GOOD, EnergyGridPresenter.reserveTone(150_000L, 150_000L))
    }

    @Test fun priceTonesFollowTheCap() {
        val cap = Money.units(500)
        assertEquals(Tone.NEUTRAL, EnergyGridPresenter.priceTone(Money.units(40), cap))
        assertEquals(Tone.WARN, EnergyGridPresenter.priceTone(Money.units(150), cap))
        assertEquals(Tone.BAD, EnergyGridPresenter.priceTone(Money.units(250), cap))
    }

    @Test fun insightTonesFollowPriority() {
        assertEquals(Tone.BAD, EnergyGridPresenter.insightTone(80))
        assertEquals(Tone.WARN, EnergyGridPresenter.insightTone(50))
        assertEquals(Tone.INFO, EnergyGridPresenter.insightTone(30))
        assertEquals(Tone.NEUTRAL, EnergyGridPresenter.insightTone(10))
    }

    @Test fun anEmptyStateHasAMessage() {
        val w = GridWorld()
        val none = app.many.economytycoon.energygrid.EnergyGridState()
        assertTrue(EnergyGridPresenter.list(none, money).rows.isEmpty())
        assertEquals(EnergyGridPresenter.NO_GRIDS, EnergyGridPresenter.list(none, money).empty)
        assertTrue(EnergyGridPresenter.dispatchStack(w.grid, money).hours.isEmpty())
        assertEquals(EnergyGridPresenter.NO_DISPATCH, EnergyGridPresenter.dispatchStack(w.grid, money).empty)
        assertTrue(EnergyGridPresenter.priceHistory(w.grid, 30, money).tiles.isEmpty())
        assertTrue(EnergyGridPresenter.ppaList(w.state, w.grid, 0L, money).rows.isEmpty())
        assertTrue(EnergyGridPresenter.outageReport(w.grid, money).blackouts.isEmpty())
    }

    @Test fun theListShowsEveryGrid() {
        val w = richWorld(10)
        w.otherGrid(region = 9L)
        val list = EnergyGridPresenter.list(w.state, money)
        assertEquals(w.state.grids.size, list.rows.size)
        assertTrue(list.rows.size >= 2)
        assertEquals(w.grid.name, list.rows.first().name)
        assertTrue(list.rows.first().price.endsWith("/MWh"))
        assertEquals("No price yet", list.rows.last().price)
    }

    @Test fun theOverviewHasTilesFactsAndMix() {
        val w = richWorld(20)
        val m = EnergyGridPresenter.overview(w.state, w.grid, 20L, money)
        assertEquals(w.grid.name, m.title)
        assertEquals(listOf("Price", "Reserve", "Peak demand", "Clean energy", "Carbon", "Supply met"), m.tiles.map { it.label })
        assertTrue(m.facts.any { it.label == "Firm capacity" })
        assertTrue(m.mix.isNotEmpty())
        assertTrue(m.mix.all { it.units > 0 })
        assertTrue(m.insights.size <= 6)
    }

    @Test fun anUnderbuiltGridShowsWarnings() {
        val w = GridWorld()
        w.unit(Technology.GAS, 50_000L)
        w.households(80_000L)
        w.days(6)
        val m = EnergyGridPresenter.overview(w.state, w.grid, 6L, money)
        assertEquals(Tone.BAD, m.tiles.first { it.label == "Reserve" }.tone)
        assertTrue(m.insights.any { it.code == "low-reserve" })
    }

    @Test fun theStackShowsTwentyFourHours() {
        val w = richWorld(5)
        val s = EnergyGridPresenter.dispatchStack(w.grid, money)
        assertEquals(24, s.hours.size)
        assertEquals((0..23).toList(), s.hours.map { it.hour })
        assertNotNull(s.peakHour)
        for (h in s.hours) assertTrue(h.layers.sumOf { it.sharePpm } in 999_990L..1_000_010L || h.layers.isEmpty())
    }

    @Test fun theStackNamesThePriceSetter() {
        val w = richWorld(5)
        val s = EnergyGridPresenter.dispatchStack(w.grid, money)
        assertTrue(s.hours.any { it.setter != "None" })
    }

    @Test fun anUnservedHourIsMarkedDark() {
        val w = GridWorld()
        w.unit(Technology.GAS, 20_000L)
        w.households(80_000L)
        w.days(2)
        val s = EnergyGridPresenter.dispatchStack(w.grid, money)
        assertTrue(s.hours.any { it.tone == Tone.BAD && it.unserved != null })
    }

    @Test fun agreementsAreListedActiveFirst() {
        val w = GridWorld()
        w.unit(Technology.COAL, 100_000L, owner = w.owner)
        w.households(50_000L)
        w.facility(10_000L)
        val a = w.service.offerPpa(w.grid.id, w.owner, w.buyer, PpaProfile.BASELOAD, 20_000L, 0L, null, Money.units(50), Tick(0), 60, 100_000L, Tick(0)).getOrThrow()
        val b = w.service.offerPpa(w.grid.id, w.owner, w.buyer, PpaProfile.BASELOAD, 10_000L, 0L, null, Money.units(55), Tick(0), 60, 100_000L, Tick(0)).getOrThrow()
        w.service.acceptPpa(b.id, w.buyer, Tick(0)).getOrThrow()
        val m = EnergyGridPresenter.ppaList(w.state, w.grid, 0L, money)
        assertEquals(listOf(b.id.value, a.id.value), m.rows.map { it.id })
        assertEquals("Active", m.rows.first().status)
        assertEquals(Tone.INFO, m.rows.last().tone)
        assertTrue(m.rows.first().progress.contains("days left"))
        assertEquals("1", m.tiles.first().value)
    }

    @Test fun aShortAgreementIsFlagged() {
        val w = GridWorld()
        w.unit(Technology.COAL, 100_000L, owner = w.owner)
        w.households(60_000L)
        w.facility(10_000L)
        val p = w.service.offerPpa(w.grid.id, w.owner, w.buyer, PpaProfile.BASELOAD, 100_000L, 0L, null, Money.units(50), Tick(0), 60, 100_000L, Tick(0)).getOrThrow()
        w.service.acceptPpa(p.id, w.buyer, Tick(0)).getOrThrow()
        w.days(5)
        val row = EnergyGridPresenter.ppaRow(p, 5L, money)
        assertEquals(Tone.WARN, row.tone)
        assertTrue(row.progress.contains("short"))
    }

    @Test fun theOutageReportCountsBrokenUnits() {
        val w = richWorld(40)
        val r = EnergyGridPresenter.outageReport(w.grid, money)
        assertEquals(4, r.tiles.size)
        assertTrue(r.outages.size <= 20)
    }

    @Test fun outageRowsNameTheUnit() {
        val w = GridWorld()
        val u = w.unit(Technology.GAS, 50_000L)
        val row = EnergyGridPresenter.outageRow(OutageEvent(w.grid.id, u.id, OutageKind.FORCED, 3L, 8L), w.grid)
        assertTrue(row.what.startsWith(Technology.GAS.label))
        assertEquals(Tone.BAD, row.tone)
        assertEquals("Day 3 to day 8", row.period)
        val gone = EnergyGridPresenter.outageRow(OutageEvent(w.grid.id, app.many.economytycoon.energygrid.UnitId(99), OutageKind.MAINTENANCE, 1L, 2L), w.grid)
        assertEquals("Unit 99", gone.what)
        assertEquals(Tone.WARN, gone.tone)
    }

    @Test fun blackoutsAppearNewestFirst() {
        val w = GridWorld()
        w.unit(Technology.GAS, 20_000L)
        w.households(80_000L)
        w.facility(20_000L)
        w.days(3)
        val r = EnergyGridPresenter.outageReport(w.grid, money)
        assertTrue(r.blackouts.isNotEmpty())
        assertEquals(r.blackouts.map { it.id }.sortedDescending(), r.blackouts.map { it.id })
        assertTrue(r.blackouts.first().length.endsWith("hours"))
    }

    @Test fun priceHistoryHasASeriesAndStats() {
        val w = richWorld(25)
        val m = EnergyGridPresenter.priceHistory(w.grid, 20, money)
        assertEquals(20, m.series.size)
        assertEquals(20, m.labels.size)
        assertEquals(6, m.tiles.size)
        assertEquals(5, m.duration.size)
        assertTrue(m.duration.first().first.endsWith("of hours"))
    }

    @Test fun priceHistoryOfAShortWindowUsesWhatThereIs() {
        val w = richWorld(4)
        assertEquals(4, EnergyGridPresenter.priceHistory(w.grid, 30, money).series.size)
    }

    @Test fun presentationDoesNotChangeTheState() {
        val w = richWorld(15)
        val before = w.state.stateHash()
        EnergyGridPresenter.overview(w.state, w.grid, 15L, money)
        EnergyGridPresenter.dispatchStack(w.grid, money)
        EnergyGridPresenter.ppaList(w.state, w.grid, 15L, money)
        EnergyGridPresenter.outageReport(w.grid, money)
        EnergyGridPresenter.priceHistory(w.grid, 10, money)
        EnergyGridPresenter.list(w.state, money)
        assertEquals(before, w.state.stateHash())
    }
}
