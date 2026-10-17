package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttractionsTest {
    private val w = TourismWorld()

    private fun museum(capacity: Int = 1_500): Attraction = w.attraction(AttractionKind.MUSEUM, capacity = capacity)

    @Test fun everyKindHasSensiblePositiveParameters() {
        for (k in AttractionKind.values()) {
            assertTrue(k.referenceCapacity > 0 && k.referenceTicket.raw > 0L && k.buildCost.raw > 0L)
            assertTrue(k.attendancePpm in 1..1_000_000L)
            assertTrue(k.cultureBoostPpm + k.natureBoostPpm + k.nightlifeBoostPpm > 0L)
        }
    }

    @Test fun priceFactorIsOneAtTheReferenceAndFallsAsTheTicketRises() {
        val ref = Money.units(20)
        assertEquals(1_000_000L, AttractionModel.priceFactorPpm(ref, ref))
        assertTrue(AttractionModel.priceFactorPpm(Money.units(30), ref) < 1_000_000L)
        assertTrue(AttractionModel.priceFactorPpm(Money.units(10), ref) > 1_000_000L)
        assertEquals(80_000L, AttractionModel.priceFactorPpm(Money.units(5_000), ref))
        assertEquals(1_000_000L, AttractionModel.priceFactorPpm(ref, Money.ZERO))
    }

    @Test fun demandFollowsTouristNumbers() {
        val a = museum()
        val few = AttractionModel.demand(a, AttractionDayInputs(1_000L, 0L))
        val many = AttractionModel.demand(a, AttractionDayInputs(10_000L, 0L))
        assertTrue(many > few * 9)
    }

    @Test fun demandFallsWhenTheTicketRises() {
        val a = museum()
        val inputs = AttractionDayInputs(10_000L, 0L)
        val cheap = AttractionModel.demand(a, inputs)
        w.service.setTicketPrice(a.id, Money.units(40)).getOrThrow()
        assertTrue(AttractionModel.demand(a, inputs) < cheap)
    }

    @Test fun badWeatherHurtsOutdoorAttractionsOnly() {
        val indoor = museum()
        val outdoor = w.attraction(AttractionKind.THEME_PARK)
        val dry = AttractionDayInputs(20_000L, 0L)
        val storm = AttractionDayInputs(20_000L, 600_000L)
        assertEquals(AttractionModel.demand(indoor, dry), AttractionModel.demand(indoor, storm))
        assertTrue(AttractionModel.demand(outdoor, storm) < AttractionModel.demand(outdoor, dry))
    }

    @Test fun residentsAddToDemand() {
        val a = museum()
        assertTrue(AttractionModel.demand(a, AttractionDayInputs(5_000L, 0L, 300_000L)) > AttractionModel.demand(a, AttractionDayInputs(5_000L, 0L, 0L)))
    }

    @Test fun nobodyWaitsBelowTheFreeUtilisation() {
        assertEquals(0, AttractionModel.waitMinutes(700, 1_000))
        assertEquals(0, AttractionModel.waitMinutes(0, 1_000))
    }

    @Test fun waitsGrowWithDemandAndAreCapped() {
        val a = AttractionModel.waitMinutes(900, 1_000)
        val b = AttractionModel.waitMinutes(1_200, 1_000)
        assertTrue(0 < a && a < b)
        assertEquals(AttractionModel.MAX_WAIT_MINUTES, AttractionModel.waitMinutes(900_000, 1_000))
        assertEquals(AttractionModel.MAX_WAIT_MINUTES, AttractionModel.waitMinutes(10, 0))
    }

    @Test fun satisfactionFallsWithQueuesAndTurnedAwayVisitors() {
        val ref = Money.units(14)
        val calm = AttractionModel.satisfactionPpm(0, 0L, 1_000L, ref, ref, 800_000L)
        val queue = AttractionModel.satisfactionPpm(60, 0L, 1_000L, ref, ref, 800_000L)
        val turned = AttractionModel.satisfactionPpm(0, 400L, 1_000L, ref, ref, 800_000L)
        assertTrue(queue < calm && turned < calm)
        assertTrue(AttractionModel.satisfactionPpm(180, 1_000L, 1_000L, Money.units(100), ref, 0L) in 0..1_000_000L)
    }

    @Test fun aQuietDayHasNoQueueAndSellsEveryTicketDemanded() {
        val a = museum()
        val d = AttractionModel.runDay(a, AttractionDayInputs(2_000L, 0L), w.tick)
        assertEquals(d.demand, d.visits)
        assertEquals(0L, d.turnedAway)
        assertEquals(0, d.waitMinutes)
        assertEquals(a.ticketPrice * d.visits, d.revenue)
        assertTrue(d.cost.raw > 0L)
    }

    @Test fun anOverfullDayTurnsPeopleAwayAndQueues() {
        val a = museum(capacity = 100)
        val d = AttractionModel.runDay(a, AttractionDayInputs(50_000L, 0L), w.tick)
        assertEquals(100L, d.visits)
        assertEquals(d.demand - 100L, d.turnedAway)
        assertTrue(d.waitMinutes > 0)
    }

    @Test fun anAttractionNotYetOpenOrClosedDoesNothing() {
        val a = museum()
        w.service.setAttractionOpen(a.id, false).getOrThrow()
        val closed = AttractionModel.runDay(a, AttractionDayInputs(50_000L, 0L), w.tick)
        assertEquals(0L, closed.visits)
        assertEquals(Money.ZERO, closed.revenue)
        w.service.setAttractionOpen(a.id, true).getOrThrow()
        val early = AttractionModel.runDay(a, AttractionDayInputs(50_000L, 0L), a.opensAt - 1)
        assertEquals(0L, early.visits)
    }

    @Test fun boostsScaleWithSizeAndQualityAndVanishWhenClosed() {
        val a = museum()
        val full = a.cultureBoostPpm()
        assertTrue(full > 0L)
        assertEquals(0L, a.natureBoostPpm())
        val small = museum(capacity = 300)
        assertTrue(small.cultureBoostPpm() < full)
        w.service.setAttractionOpen(a.id, false).getOrThrow()
        assertEquals(0L, a.cultureBoostPpm())
    }

    @Test fun boostSizeIsCappedAtOneAndAHalfTimesReference() {
        val huge = museum(capacity = 30_000)
        val big = museum(capacity = 2_250)
        assertEquals(big.cultureBoostPpm(), huge.cultureBoostPpm())
    }

    @Test fun boostsListsHaveOneEntryPerAttraction() {
        val list = listOf(museum(), w.attraction(AttractionKind.NATURE_PARK))
        val (culture, nature, nightlife) = AttractionModel.boosts(list)
        assertEquals(2, culture.size)
        assertEquals(2, nature.size)
        assertEquals(2, nightlife.size)
        assertTrue(nature[1] > 0L)
    }

    @Test fun historyIsBoundedAndHashFollowsContent() {
        val a = museum()
        fun h() = CanonicalHasher().also { a.hashInto(it) }.finish()
        val before = h()
        repeat(Attraction.HISTORY_DAYS + 10) { a.pushDay(AttractionModel.runDay(a, AttractionDayInputs(2_000L, 0L), w.tick + it)) }
        assertEquals(Attraction.HISTORY_DAYS, a.days.size)
        assertNotEquals(before, h())
        assertEquals(h(), h())
    }
}
