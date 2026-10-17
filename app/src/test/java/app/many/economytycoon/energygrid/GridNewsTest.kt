package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridNewsTest {
    private val money: (Money) -> String = { it.wholeUnits().toString() }

    private fun news(): Pair<GridWorld, GridNews> {
        val w = GridWorld()
        return w to GridNews(w.state, money)
    }

    @Test fun aTripIsAWarningThatNamesThePlant() {
        val (w, n) = news()
        val u = w.unit(Technology.GAS, 50_000L)
        val note = n.note(GridEvent.UnitFailed(w.grid.id, u.id, 9L)) ?: error("a note")
        assertEquals(NoteSeverity.WARNING, note.severity)
        assertTrue(note.headline.contains(Technology.GAS.label))
        assertTrue(note.detail.contains("day 9"))
    }

    @Test fun routineMaintenanceIsNotNews() {
        val (w, n) = news()
        val u = w.unit(Technology.GAS, 50_000L)
        assertNull(n.note(GridEvent.MaintenanceStarted(w.grid.id, u.id, 5L)))
        assertNull(n.note(GridEvent.UnitBack(w.grid.id, u.id)))
    }

    @Test fun blackoutsAreCritical() {
        val (w, n) = news()
        val start = n.note(GridEvent.BlackoutStarted(w.grid.id, BlackoutId(1), 18, 25_000L)) ?: error("a note")
        val end = n.note(GridEvent.BlackoutEnded(w.grid.id, BlackoutId(1), 3, 60_000L, Money.units(1_200))) ?: error("a note")
        assertEquals(NoteSeverity.CRITICAL, start.severity)
        assertEquals(NoteSeverity.CRITICAL, end.severity)
        assertTrue(start.detail.contains("25 MW"))
        assertTrue(end.detail.contains("60 MWh") && end.detail.contains("1200"))
        assertTrue(end.headline.contains("3 hours"))
    }

    @Test fun anAuctionThatFallsShortIsAWarning() {
        val (w, n) = news()
        assertEquals(NoteSeverity.WARNING, n.note(GridEvent.CapacityAuction(w.grid.id, 80_000L, 20_000L, Money.units(90)))?.severity)
        assertEquals(NoteSeverity.INFO, n.note(GridEvent.CapacityAuction(w.grid.id, 100_000L, 0L, Money.units(90)))?.severity)
    }

    @Test fun agreementEventsMentionTheAgreement() {
        val (w, n) = news()
        assertTrue(n.note(GridEvent.PpaShortfall(w.grid.id, PpaId(4), 12_000L))?.headline?.contains("4") == true)
        assertNotNull(n.note(GridEvent.PpaLapsed(w.grid.id, PpaId(4))))
    }

    @Test fun orderingAndCommissioningAreInformation() {
        val (w, n) = news()
        val u = w.unit(Technology.WIND, 30_000L)
        assertEquals(NoteSeverity.INFO, n.note(GridEvent.UnitOrdered(w.grid.id, u.id, Technology.WIND, 40L))?.severity)
        assertEquals(NoteSeverity.INFO, n.note(GridEvent.UnitCommissioned(w.grid.id, u.id))?.severity)
        assertEquals(NoteSeverity.INFO, n.note(GridEvent.UnitRetired(w.grid.id, u.id))?.severity)
        assertEquals(NoteSeverity.INFO, n.note(GridEvent.MaintenancePostponed(w.grid.id, u.id, 2))?.severity)
    }

    @Test fun priceSpikesShowPeakAndAverage() {
        val (w, n) = news()
        val note = n.note(GridEvent.PriceSpike(w.grid.id, Money.units(480), Money.units(120))) ?: error("a note")
        assertTrue(note.detail.contains("480") && note.detail.contains("120"))
    }

    @Test fun unknownNamesFallBackToIds() {
        val (w, n) = news()
        val note = n.note(GridEvent.UnitFailed(w.grid.id, UnitId(777), 3L)) ?: error("a note")
        assertTrue(note.headline.startsWith("Unit 777"))
        val orphan = n.note(GridEvent.BlackoutStarted(GridId(55), BlackoutId(1), 1, 1_000L)) ?: error("a note")
        assertTrue(orphan.headline.contains("Grid 55"))
    }

    @Test fun aDaysNotesComeMostSevereFirst() {
        val w = GridWorld()
        w.unit(Technology.GAS, 20_000L)
        w.households(80_000L)
        w.facility(20_000L)
        val n = GridNews(w.state, money)
        val notes = w.days(5).flatMap { n.notes(it) }
        assertTrue(notes.any { it.severity == NoteSeverity.CRITICAL })
        for (r in w.days(2)) {
            val ranks = n.notes(r).map { it.severity.ordinal }
            assertEquals(ranks.sortedDescending(), ranks)
        }
    }

    @Test fun theGlossaryHasUniqueKeysAndText() {
        val keys = GridGlossary.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(GridGlossary.entries.all { it.term.isNotBlank() && it.text.length > 40 })
        assertEquals("Merit order", GridGlossary.find("merit-order")?.term)
        assertNull(GridGlossary.find("nonsense"))
    }

    @Test fun commissioningShowsUpInARealRun() {
        val w = GridWorld()
        w.households(20_000L)
        w.unit(Technology.GAS, 60_000L)
        val order = w.service.orderUnit(w.grid.id, Technology.GAS, 30_000L, w.owner, Tick(0)).getOrThrow()
        val n = GridNews(w.state, money)
        val ready = order.unit.statusUntil.toInt()
        val reports = w.days(ready + 2)
        assertTrue(reports.flatMap { it.events }.any { it is GridEvent.UnitCommissioned && it.unit == order.unit.id })
        assertTrue(reports.flatMap { n.notes(it) }.any { it.headline.contains("online") })
    }
}
