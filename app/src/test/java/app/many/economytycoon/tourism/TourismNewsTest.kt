package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismNewsTest {
    private val w = TourismWorld()
    private val news get() = TourismNews(w.state) { it.wholeUnits().toString() }

    @Test fun openingsAreGoodNewsNamedAfterTheBuilding() {
        val p = w.hotel(name = "Grand Sol")
        val n = requireNotNull(news.note(TourismEvent.PropertyOpened(p.id, 5L)))
        assertEquals(TourismSeverity.INFO, n.severity)
        assertTrue(n.headline.contains("Grand Sol"))
        assertTrue(n.detail.contains("Costa"))
        assertEquals(w.dest.id, n.destination)
    }

    @Test fun anAttractionOpening() {
        val a = w.attraction()
        val n = requireNotNull(news.note(TourismEvent.AttractionOpened(a.id, 1L)))
        assertEquals(TourismSeverity.INFO, n.severity)
        assertTrue(n.headline.contains(a.name))
    }

    @Test fun badShocksWarnAndGoodOnesInform() {
        val bad = w.service.triggerShock(ShockKind.STRIKE, w.dest.id, null, Tick(1), 3, 500_000L, "Rail strike").getOrThrow()
        val good = w.service.triggerShock(ShockKind.FESTIVAL, w.dest.id, null, Tick(1), 3, 500_000L, "Wine fair").getOrThrow()
        val nb = requireNotNull(news.note(TourismEvent.ShockStarted(bad.id, bad.kind, w.dest.id, 1L)))
        val ng = requireNotNull(news.note(TourismEvent.ShockStarted(good.id, good.kind, w.dest.id, 1L)))
        assertEquals(TourismSeverity.WARNING, nb.severity)
        assertEquals(TourismSeverity.INFO, ng.severity)
        assertEquals("Rail strike", nb.headline)
        assertTrue(nb.detail.contains("fewer"))
        assertTrue(ng.detail.contains("more"))
    }

    @Test fun shocksWithoutADestinationSpeakOfTheRegion() {
        val s = w.service.triggerShock(ShockKind.DISEASE, null, null, Tick(1), 30, 500_000L, " ").getOrThrow()
        val n = requireNotNull(news.note(TourismEvent.ShockStarted(s.id, s.kind, null, 1L)))
        assertEquals(ShockKind.DISEASE.label, n.headline)
        assertNull(n.destination)
    }

    @Test fun quietEventsMakeNoNews() {
        val p = w.hotel()
        assertNull(news.note(TourismEvent.ShockEnded(ShockId(1), ShockKind.STRIKE, 1L)))
        assertNull(news.note(TourismEvent.SoldOut(p.id, 1L)))
    }

    @Test fun walkedGuestsAreAWarning() {
        val p = w.hotel(name = "Hotel Luna")
        val n = requireNotNull(news.note(TourismEvent.GuestsWalked(p.id, 7, 2L)))
        assertEquals(TourismSeverity.WARNING, n.severity)
        assertTrue(n.headline.contains("7"))
        assertTrue(n.headline.contains("Hotel Luna"))
    }

    @Test fun aFullTownIsReportedWithTheRoomNights() {
        val n = requireNotNull(news.note(TourismEvent.DemandTurnedAway(w.dest.id, 340L, 3L)))
        assertTrue(n.headline.contains("Costa"))
        assertTrue(n.detail.contains("340"))
    }

    @Test fun festivalsAndContracts() {
        val p = w.hotel()
        val a = w.allotment(w.operator(), p, days = 20)
        assertTrue(requireNotNull(news.note(TourismEvent.FestivalBegan(w.dest.id, 1L))).headline.contains("festival"))
        assertTrue(requireNotNull(news.note(TourismEvent.AllotmentEnded(a.id, 1L))).headline.contains("Allotment ${a.id.value}"))
        val short = requireNotNull(news.note(TourismEvent.AllotmentShort(a.id, 4, 1L)))
        assertTrue(short.detail.contains("4"))
        assertEquals(w.dest.id, short.destination)
    }

    @Test fun ratingChangesPointTheRightWay() {
        val p = w.hotel()
        val up = requireNotNull(news.note(TourismEvent.RatingChanged(p.id, 38, 41, 1L)))
        val down = requireNotNull(news.note(TourismEvent.RatingChanged(p.id, 38, 34, 1L)))
        assertEquals(TourismSeverity.INFO, up.severity)
        assertEquals(TourismSeverity.WARNING, down.severity)
        assertTrue(up.headline.endsWith("4.1"))
        assertTrue(down.headline.endsWith("3.4"))
    }

    @Test fun fullRoutesNameTheOrigin() {
        val n = requireNotNull(news.note(TourismEvent.RoutesFull(w.abroad.id, w.dest.id, 120L, 1L)))
        assertTrue(n.detail.contains("Overseas"))
        assertTrue(n.detail.contains("120"))
    }

    @Test fun unknownIdsDoNotCrash() {
        assertNotNull(news.note(TourismEvent.PropertyOpened(PropertyId(99), 1L)))
        assertNotNull(news.note(TourismEvent.GuestsWalked(PropertyId(99), 1, 1L)))
        assertNotNull(news.note(TourismEvent.AllotmentEnded(AllotmentId(99), 1L)))
        assertNotNull(news.note(TourismEvent.DemandTurnedAway(DestinationId(99), 1L, 1L)))
    }

    @Test fun aDaysNotesAreSortedMostSevereFirst() {
        val p = w.hotel()
        val report = DayReport(
            Tick(1), emptyList(),
            listOf(TourismEvent.PropertyOpened(p.id, 1L), TourismEvent.GuestsWalked(p.id, 2, 1L), TourismEvent.SoldOut(p.id, 1L), TourismEvent.FestivalBegan(w.dest.id, 1L)),
            emptyList(), emptyList(),
        )
        val notes = news.notes(report)
        assertEquals(3, notes.size)
        assertEquals(TourismSeverity.WARNING, notes.first().severity)
        assertEquals(notes.sortedByDescending { it.severity.ordinal }.map { it.severity }, notes.map { it.severity })
    }

    @Test fun aRealRunProducesCoherentNews() {
        w.hotel(rooms = intArrayOf(20, 0, 0))
        w.service.triggerShock(ShockKind.STRIKE, w.dest.id, null, Tick(w.tick + 3), 5, 700_000L, "Strike").getOrThrow()
        val all = w.days(30).flatMap { news.notes(it) }
        assertTrue(all.any { it.headline == "Strike" })
        assertTrue(all.all { it.headline.isNotBlank() && it.detail.isNotBlank() })
    }

    @Test fun aSettlementSummaryIsAWarningOnlyWhenTheMonthLost() {
        val w2 = TourismWorld(startTick = 120L, homeOutbound = 2L, abroadOutbound = 2L)
        w2.hotel(rooms = intArrayOf(300, 0, 0))
        w2.days(30)
        val run = w2.settle()
        val n = TourismNews(w2.state) { it.wholeUnits().toString() }.summary(run)
        assertEquals(TourismSeverity.WARNING, n.severity)
        assertTrue(n.headline.contains("${run.month}"))
        assertEquals(Money.ZERO, Money.ZERO)
    }
}
