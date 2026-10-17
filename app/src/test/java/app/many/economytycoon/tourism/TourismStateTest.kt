package app.many.economytycoon.tourism

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismStateTest {
    @Test fun aNewStateIsEmpty() {
        val s = TourismState()
        assertTrue(s.isEmpty)
        assertTrue(s.destinations.isEmpty() && s.properties.isEmpty() && s.attractions.isEmpty() && s.allotments.isEmpty())
        assertEquals(-1L, s.lastTick)
    }

    @Test fun countersAllocateIncreasingIdsFromOne() {
        val c = TourismCounters()
        assertEquals(1L, c.property().value)
        assertEquals(2L, c.property().value)
        assertEquals(1L, c.destination().value)
        assertEquals(1L, c.shock().value)
    }

    @Test fun countersResetToTheirStart() {
        val c = TourismCounters()
        repeat(5) { c.property(); c.allotment() }
        c.reset()
        assertEquals(1L, c.property().value)
        assertEquals(1L, c.allotment().value)
    }

    @Test fun lookupsFindWhatWasAdded() {
        val w = TourismWorld()
        val p = w.hotel()
        val a = w.attraction()
        val s = w.state
        assertEquals(p, s.property(p.id))
        assertEquals(a, s.attraction(a.id))
        assertNull(s.property(PropertyId(999)))
        assertNull(s.destination(DestinationId(999)))
        assertNull(s.origin(OriginId(999)))
        assertNull(s.operator(OperatorId(999)))
        assertNull(s.allotment(AllotmentId(999)))
        assertNull(s.intermediary(IntermediaryId(999)))
        assertNull(s.shock(ShockId(999)))
    }

    @Test fun listsComeInIdOrder() {
        val w = TourismWorld()
        repeat(5) { w.hotel() }
        assertEquals(w.state.properties.map { it.id }.sorted(), w.state.properties.map { it.id })
    }

    @Test fun queriesFilterByDestinationAndOwner() {
        val w = TourismWorld()
        val other = w.service.addDestination(CityId(2), app.many.economytycoon.core.RegionId(1), app.many.economytycoon.core.CountryId(1), "Alps", app.many.economytycoon.world.ClimateZone.COLD, app.many.economytycoon.world.Point(10, 10), false).getOrThrow()
        val a = w.hotel(); val b = w.hotel(who = w.rival); val c = w.hotel(destination = other)
        assertEquals(listOf(a.id, b.id), w.state.propertiesIn(w.dest.id).map { it.id })
        assertEquals(listOf(c.id), w.state.propertiesIn(other.id).map { it.id })
        assertEquals(listOf(a.id, c.id), w.state.propertiesOf(w.owner).map { it.id })
        assertEquals(listOf(b.id), w.state.propertiesOf(w.rival).map { it.id })
    }

    @Test fun openPropertiesExcludeClosedAndUnderConstruction() {
        val w = TourismWorld()
        val a = w.hotel(); val b = w.hotel()
        w.service.closeProperty(b.id).getOrThrow()
        val building = w.service.addProperty(w.dest.id, w.owner, null, PropertyKind.HOTEL, 3, intArrayOf(10, 0, 0), Tick(w.tick), "New").getOrThrow().property
        assertEquals(PropertyStatus.UNDER_CONSTRUCTION, building.status)
        assertEquals(listOf(a.id), w.state.openPropertiesIn(w.dest.id).map { it.id })
    }

    @Test fun allotmentQueriesSeparateLiveFromProposed() {
        val w = TourismWorld()
        val p = w.hotel()
        val op = w.operator()
        val live = w.allotment(op, p, days = 30)
        val declined = w.allotment(op, p, netShare = 100_000L.coerceAtLeast(310_000L), days = 30, rooms = 1)
        assertEquals(AllotmentStatus.ACTIVE, live.status)
        assertEquals(AllotmentStatus.DECLINED, declined.status)
        assertEquals(listOf(live.id), w.state.liveAllotmentsOfProperty(p.id).map { it.id })
        assertEquals(2, w.state.allotmentsOfProperty(p.id).size)
        assertEquals(2, w.state.allotmentsOfOperator(op.id).size)
    }

    @Test fun intermediariesByChannel() {
        val w = TourismWorld()
        w.platform(IntermediaryKind.ONLINE_PLATFORM); w.platform(IntermediaryKind.AGENCY); w.platform(IntermediaryKind.ONLINE_PLATFORM)
        assertEquals(2, w.state.intermediariesOfChannel(Channel.ONLINE).size)
        assertEquals(1, w.state.intermediariesOfChannel(Channel.AGENCY).size)
        assertEquals(0, w.state.intermediariesOfChannel(Channel.DIRECT).size)
    }

    @Test fun activeShocksDependOnTheDate() {
        val w = TourismWorld()
        w.service.triggerShock(ShockKind.STRIKE, w.dest.id, null, Tick(200), 5, 500_000L, "Strike").getOrThrow()
        assertTrue(w.state.activeShocks(199).isEmpty())
        assertEquals(1, w.state.activeShocks(200).size)
        assertEquals(1, w.state.activeShocks(204).size)
        assertTrue(w.state.activeShocks(205).isEmpty())
    }

    @Test fun clearForgetsEverythingIncludingTheCounters() {
        val w = RichWorld.build(days = 5)
        assertFalse(w.state.isEmpty)
        w.state.clear()
        assertTrue(w.state.isEmpty && w.state.properties.isEmpty() && w.state.shocks.isEmpty())
        assertEquals(-1L, w.state.lastTick)
        assertEquals(TourismState().stateHash().toHex(), w.state.stateHash().toHex())
        assertEquals(1L, w.state.counters.property().value)
    }

    @Test fun anEmptyStateHashIsStable() {
        assertEquals(TourismState().stateHash().toHex(), TourismState().stateHash().toHex())
        assertEquals(64, TourismState().stateHash().toHex().length)
    }

    @Test fun anyChangeMovesTheHash() {
        val w = TourismWorld()
        val seen = HashSet<String>()
        seen += w.state.stateHash().toHex()
        val p = w.hotel()
        assertTrue(seen.add(w.state.stateHash().toHex()))
        w.service.setOverbooking(p.id, 10_000L).getOrThrow()
        assertTrue(seen.add(w.state.stateHash().toHex()))
        w.attraction()
        assertTrue(seen.add(w.state.stateHash().toHex()))
        w.platform()
        assertTrue(seen.add(w.state.stateHash().toHex()))
        w.operator()
        assertTrue(seen.add(w.state.stateHash().toHex()))
        w.day()
        assertTrue(seen.add(w.state.stateHash().toHex()))
    }

    @Test fun theSameHistoryGivesTheSameHash() {
        val a = RichWorld.build(seed = 3L, days = 20)
        val b = RichWorld.build(seed = 3L, days = 20)
        assertEquals(a.state.stateHash().toHex(), b.state.stateHash().toHex())
    }

    @Test fun differentSeedsDivergeOnceRandomShocksStrike() {
        fun run(seed: Long): String {
            val w = TourismWorld(seed)
            w.hotel()
            w.days(360)
            return w.state.stateHash().toHex()
        }
        assertNotEquals(run(5L), run(6L))
    }

    @Test fun invariantsHoldAfterTrading() {
        TourismInvariants.check(RichWorld.build(days = 40).state)
    }
}
