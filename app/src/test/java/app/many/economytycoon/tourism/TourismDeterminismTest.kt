package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismDeterminismTest {
    private fun script(seed: Long): TourismWorld {
        val w = TourismWorld(seed)
        val a = w.hotel(); val b = w.hotel(4, intArrayOf(40, 20, 5), who = w.rival)
        val i = w.platform()
        w.service.listWith(a.id, i.id).getOrThrow()
        w.service.setPricing(b.id, RatePolicy.DYNAMIC, b.settings.rackRate).getOrThrow()
        w.allotment(w.operator(), a, rooms = 6, committed = 300_000L)
        w.days(20)
        w.service.triggerShock(ShockKind.WEATHER, w.dest.id, null, Tick(w.tick), 5, 600_000L, "Storm").getOrThrow()
        w.service.setOverbooking(a.id, 40_000L).getOrThrow()
        w.days(20)
        w.settle()
        return w
    }

    @Test fun theSameScriptGivesTheSameHash() {
        assertEquals(script(1L).state.stateHash().toHex(), script(1L).state.stateHash().toHex())
    }

    @Test fun theSameScriptGivesTheSameSettlement() {
        fun intents(w: TourismWorld) = w.run { days(30); settle() }.intents.map { it.key to it.total.raw }
        assertEquals(intents(script(2L)), intents(script(2L)))
    }

    @Test fun theSameScriptGivesTheSameDailyReports() {
        fun totals(seed: Long): List<Triple<Long, Long, Long>> {
            val w = script(seed)
            return w.days(15).map { Triple(it.totalArrivals, it.destinations[0].day.roomsSold, it.destinations[0].day.roomRevenue.raw) }
        }
        assertEquals(totals(3L), totals(3L))
    }

    @Test fun anyOrderOfCreatingIndependentThingsKeepsIdsConsistent() {
        val a = TourismWorld(); val b = TourismWorld()
        val pa = a.hotel(); a.platform(); val oa = a.operator()
        val pb = b.hotel(); b.platform(); val ob = b.operator()
        assertEquals(pa.id, pb.id)
        assertEquals(oa.id, ob.id)
    }

    @Test fun saveAndLoadInTheMiddleDoesNotChangeTheFuture() {
        val straight = script(4L)
        val cut = script(4L)
        val loaded = TourismService(TourismCodec.fromBytes(TourismCodec.toBytes(cut.state)))
        val inputs = DayInputs()
        val r1 = app.many.economytycoon.core.RandomProvider(77L)
        val r2 = app.many.economytycoon.core.RandomProvider(77L)
        repeat(25) {
            straight.service.advanceDay(Tick(straight.tick + it), inputs, r1)
            loaded.advanceDay(Tick(cut.tick + it), inputs, r2)
        }
        assertEquals(straight.state.stateHash().toHex(), loaded.state.stateHash().toHex())
    }

    @Test fun differentInputsGiveDifferentHistories() {
        fun hash(wage: Long): String {
            val w = TourismWorld()
            w.hotel()
            w.days(20, DayInputs(defaultWage = Money.units(wage)))
            return w.state.stateHash().toHex()
        }
        assertNotEquals(hash(60L), hash(61L))
    }

    @Test fun theWeatherInputsAreReadNotRemembered() {
        val a = TourismWorld(); val b = TourismWorld()
        a.hotel(); b.hotel(); a.attraction(AttractionKind.THEME_PARK); b.attraction(AttractionKind.THEME_PARK)
        val storm = DayInputs(weather = mapOf(a.dest.id to listOf(app.many.economytycoon.world.WeatherEvent(app.many.economytycoon.core.RegionId(1), app.many.economytycoon.world.WeatherKind.STORM, Tick(150L), 3, 1_000_000L))))
        a.days(3, storm); b.days(3)
        a.days(40); b.days(40)
        a.days(0); b.days(0)
        assertTrue(a.state.attractions[0].lifetimeVisits < b.state.attractions[0].lifetimeVisits)
    }

    @Test fun noStatePersistsOutsideTheStateObject() {
        val first = script(6L)
        val second = script(6L)
        assertEquals(first.state.stateHash().toHex(), second.state.stateHash().toHex())
        val third = script(6L)
        assertEquals(second.state.stateHash().toHex(), third.state.stateHash().toHex())
    }

    @Test fun iterationOrderDoesNotDependOnInsertionOrder() {
        val a = TourismWorld(); val b = TourismWorld()
        val first = a.hotel(); a.hotel(4, intArrayOf(30, 10, 0))
        b.hotel(); b.hotel(4, intArrayOf(30, 10, 0))
        assertEquals(first.id, b.state.properties.first().id)
        a.days(25); b.days(25)
        assertEquals(a.state.stateHash().toHex(), b.state.stateHash().toHex())
    }
}
