package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.tourism.AttractionKind
import app.many.economytycoon.tourism.RichWorld
import app.many.economytycoon.tourism.TourismState
import app.many.economytycoon.tourism.TourismWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismPresenterTest {
    private val money: (Money) -> String = { Formatting.money(it) }

    @Test fun anEmptyStateListsNothingWithAnExplanation() {
        val m = TourismPresenter.destinations(TourismState(), money)
        assertTrue(m.rows.isEmpty())
        assertEquals(TourismPresenter.NO_DESTINATIONS, m.empty)
    }

    @Test fun destinationRowsShowTheTradingFigures() {
        val w = RichWorld.build(days = 45)
        val m = TourismPresenter.destinations(w.state, money)
        val r = m.rows.single()
        assertEquals("Costa", r.name)
        assertTrue(r.occupancy.endsWith("%"))
        assertTrue(r.note.contains("hotels") && r.note.contains("attractions"))
        assertEquals(w.dest.id.value, r.id)
    }

    @Test fun occupancyTonesStepWithTheLevel() {
        assertEquals(Tone.BAD, TourismPresenter.occupancyTone(100_000L))
        assertEquals(Tone.WARN, TourismPresenter.occupancyTone(500_000L))
        assertEquals(Tone.GOOD, TourismPresenter.occupancyTone(800_000L))
        assertEquals(Tone.INFO, TourismPresenter.occupancyTone(950_000L))
    }

    @Test fun ratingTonesAndFormatting() {
        assertEquals("4.2", TourismPresenter.rating(42))
        assertEquals("3.0", TourismPresenter.rating(30))
        assertEquals(Tone.GOOD, TourismPresenter.ratingTone(41))
        assertEquals(Tone.NEUTRAL, TourismPresenter.ratingTone(35))
        assertEquals(Tone.WARN, TourismPresenter.ratingTone(27))
        assertEquals(Tone.BAD, TourismPresenter.ratingTone(20))
        assertEquals("4-star", TourismPresenter.stars(4))
    }

    @Test fun insightTonesFollowPriority() {
        assertEquals(Tone.BAD, TourismPresenter.insightTone(80))
        assertEquals(Tone.WARN, TourismPresenter.insightTone(50))
        assertEquals(Tone.INFO, TourismPresenter.insightTone(35))
        assertEquals(Tone.NEUTRAL, TourismPresenter.insightTone(10))
    }

    @Test fun theOverviewHasSixTilesFactsAndAFourSegmentAppeal() {
        val w = RichWorld.build(days = 45)
        val m = TourismPresenter.overview(w.state, w.dest, w.tick, money)
        assertEquals("Costa", m.title)
        assertEquals(6, m.tiles.size)
        assertEquals(listOf("Occupancy", "Average rate", "Arrivals", "Turned away", "Reputation", "Prices"), m.tiles.map { it.label })
        assertEquals(4, m.appeal.size)
        assertEquals(listOf("Budget", "Family", "Business", "Luxury"), m.appeal.map { it.segment })
        assertTrue(m.appeal.all { it.parts.isNotEmpty() })
        assertTrue(m.facts.any { it.label == "Hotels" && it.value == "3" })
        assertTrue(m.facts.any { it.label == "Tourism levy" })
        assertTrue(m.facts.any { it.label == "Room tax" })
    }

    @Test fun activeShocksAreListedAsNotices() {
        val w = RichWorld.build(days = 5)
        val strikeStart = w.state.shocks.first().start.value
        val m = TourismPresenter.overview(w.state, w.dest, strikeStart + 1, money)
        assertTrue(m.notices.contains("Rail strike"))
        assertTrue(TourismPresenter.overview(w.state, w.dest, strikeStart + 100, money).notices.isEmpty())
    }

    @Test fun aDestinationWithoutTaxesOmitsThoseFacts() {
        val w = TourismWorld()
        w.hotel(); w.days(10)
        val m = TourismPresenter.overview(w.state, w.dest, w.tick, money)
        assertFalse(m.facts.any { it.label == "Tourism levy" || it.label == "Room tax" })
    }

    @Test fun hotelRowsCoverEveryPropertyIncludingClosedOnes() {
        val w = RichWorld.build(days = 40)
        val closed = w.hotel(); w.service.closeProperty(closed.id).getOrThrow()
        val m = TourismPresenter.properties(w.state, w.dest, money)
        assertEquals(4, m.rows.size)
        assertEquals("Closed", m.rows.first { it.id == closed.id.value }.status)
        assertTrue(m.rows.filter { it.id != closed.id.value }.all { it.status == "Open" })
        assertEquals(4, m.tiles.size)
    }

    @Test fun anEmptyDestinationHasNoHotelTiles() {
        val w = TourismWorld()
        val m = TourismPresenter.properties(w.state, w.dest, money)
        assertTrue(m.rows.isEmpty() && m.tiles.isEmpty())
        assertEquals(TourismPresenter.NO_PROPERTIES, m.empty)
    }

    @Test fun aHotelsDetailShowsKpisFactsAndRecentNights() {
        val w = RichWorld.build(days = 45)
        val p = w.state.properties[1]
        val m = TourismPresenter.propertyDetail(w.state, p, w.tick, money)
        assertEquals(p.name, m.title)
        assertEquals(6, m.tiles.size)
        assertTrue(m.facts.any { it.label == "Pricing" && it.value == "Dynamic pricing" })
        assertTrue(m.facts.any { it.label == "Occupancy against others" })
        assertEquals(14, m.nights.size)
        assertTrue(m.nights.first().sold.contains("/"))
    }

    @Test fun recentNightsComeNewestFirst() {
        val w = RichWorld.build(days = 45)
        val p = w.state.properties[0]
        val m = TourismPresenter.propertyDetail(w.state, p, w.tick, money)
        val newest = p.days.last()
        assertEquals(Formatting.date(app.many.economytycoon.core.Tick(newest.tick)), m.nights.first().day)
    }

    @Test fun aWornHotelGetsAdviceInTheDetail() {
        val w = TourismWorld()
        val p = w.hotel()
        w.days(30)
        p.conditionPpm = 200_000L
        val m = TourismPresenter.propertyDetail(w.state, p, w.tick, money)
        val line = m.insights.firstOrNull { it.code == "condition-low" }
        assertNotNull(line)
        assertEquals(Tone.BAD, line?.tone)
    }

    @Test fun attractionRowsShowVisitsQueuesAndTone() {
        val w = TourismWorld()
        w.hotel(); val a = w.attraction(AttractionKind.MUSEUM)
        val none = TourismPresenter.attractions(w.state, w.dest, money).rows.single()
        assertEquals("No queue", none.wait)
        w.days(40)
        val r = TourismPresenter.attractions(w.state, w.dest, money).rows.single()
        assertEquals(a.id.value, r.id)
        assertEquals("Museum", r.kind)
        assertTrue(r.visits.endsWith("yesterday"))
        w.service.setAttractionOpen(a.id, false).getOrThrow()
        assertEquals(Tone.MUTED, TourismPresenter.attractions(w.state, w.dest, money).rows.single().tone)
    }

    @Test fun anEmptyAttractionListExplainsItself() {
        val w = TourismWorld()
        val m = TourismPresenter.attractions(w.state, w.dest, money)
        assertTrue(m.rows.isEmpty())
        assertEquals(TourismPresenter.NO_ATTRACTIONS, m.empty)
    }

    @Test fun contractsShowTermsProgressAndStatus() {
        val w = TourismWorld()
        val p = w.hotel()
        val a = w.allotment(w.operator(), p, rooms = 10, days = 40, committed = 300_000L)
        w.days(5)
        val m = TourismPresenter.allotments(w.state, p, w.tick, money)
        val r = m.rows.single()
        assertEquals(a.id.value, r.id)
        assertEquals("Active", r.status)
        assertEquals(Tone.GOOD, r.tone)
        assertTrue(r.terms.contains("10 standard rooms"))
        assertTrue(r.terms.contains("30.0%"))
        assertTrue(r.progress.startsWith("Day "))
        assertEquals("Live contracts", m.tiles[0].label)
        assertEquals("1", m.tiles[0].value)
    }

    @Test fun aContractNotStartedYetSaysWhenItStarts() {
        val w = TourismWorld()
        val p = w.hotel()
        val op = w.operator()
        val net = Money(p.rackRate(app.many.economytycoon.tourism.RoomClass.STANDARD).raw * 650_000L / 1_000_000L)
        w.service.proposeAllotment(
            app.many.economytycoon.tourism.ContractSide.PROPERTY, w.owner, op.id, p.id, app.many.economytycoon.tourism.RoomClass.STANDARD, 5, net,
            app.many.economytycoon.core.Tick(w.tick + 10), 20, 3, 0L, app.many.economytycoon.core.Tick(w.tick),
        ).getOrThrow()
        val r = TourismPresenter.allotments(w.state, p, w.tick, money).rows.single()
        assertEquals("Starts in 10 days", r.progress)
    }

    @Test fun noContractsMeansTheEmptyText() {
        val w = TourismWorld()
        val p = w.hotel()
        val m = TourismPresenter.allotments(w.state, p, w.tick, money)
        assertTrue(m.rows.isEmpty())
        assertEquals(TourismPresenter.NO_ALLOTMENTS, m.empty)
        assertNull(m.rows.firstOrNull())
    }

    @Test fun moneyIsFormattedByTheCallersFunction() {
        val w = RichWorld.build(days = 40)
        val a = TourismPresenter.properties(w.state, w.dest) { "<${it.wholeUnits()}>" }
        assertTrue(a.rows.all { it.adr.startsWith("<") && it.revPar.startsWith("<") })
    }
}
