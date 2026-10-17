package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanEventsTest {
    private val climate = EventClimate(coastal = false, fireQualityPermille = 500, sanitationQualityPermille = 500, hasIndustry = true, hasTransit = true)

    private fun event(kind: UrbanEventKind, id: Long = 1, district: Int = -1, start: Long = 0, end: Long = 100, magnitude: Int = 500) =
        UrbanEvent(UrbanEventId(id), kind, district, Tick(start), Tick(end), magnitude)

    private fun book(vararg e: UrbanEvent) = EventBook().also { b -> e.forEach { b.add(it) } }

    @Test fun `an event is active only inside its window and until it ends`() {
        val e = event(UrbanEventKind.FLOOD, 1, 2, 10, 20)
        assertFalse(e.isActive(Tick(9)))
        assertTrue(e.isActive(Tick(10)))
        assertTrue(e.isActive(Tick(19)))
        assertFalse(e.isActive(Tick(20)))
        e.ended = true
        assertFalse(e.isActive(Tick(15)))
    }

    @Test fun `the book finds events by kind and district`() {
        val b = book(event(UrbanEventKind.FLOOD, 1, 2), event(UrbanEventKind.FIRE, 2, 3), event(UrbanEventKind.FLOOD, 3, 5, 200, 300))
        assertEquals(2, b.active(Tick(50)).size)
        assertTrue(b.isActive(UrbanEventKind.FLOOD, 2, Tick(50)))
        assertFalse(b.isActive(UrbanEventKind.FLOOD, 3, Tick(50)))
        assertFalse(b.isActive(UrbanEventKind.FLOOD, 5, Tick(50)))
        assertEquals(listOf(3L, 1L, 2L).first(), b.recent(1).first().id.value)
        assertEquals(3, b.recent(10).size)
        assertEquals(b.get(UrbanEventId(2)), b.all()[1])
    }

    @Test fun `a boom raises developer appetite`() {
        assertEquals(Scale.PPM, EventBook().boomPpm(Tick(5)))
        val b = book(event(UrbanEventKind.GROWTH_BOOM, 1, magnitude = 400))
        assertEquals(Scale.PPM + 400_000L, b.boomPpm(Tick(5)))
        assertEquals(Scale.PPM, b.boomPpm(Tick(500)))
    }

    @Test fun `disasters and strikes lower availability but never below a floor`() {
        assertEquals(1000, EventBook().availabilityPermille(Tick(1)))
        assertEquals(800, book(event(UrbanEventKind.FLOOD, magnitude = 400)).availabilityPermille(Tick(1)))
        assertEquals(750, book(event(UrbanEventKind.TRANSIT_STRIKE)).availabilityPermille(Tick(1)))
        assertEquals(300, book(event(UrbanEventKind.EARTHQUAKE, 1, magnitude = 1000), event(UrbanEventKind.FLOOD, 2, magnitude = 1000), event(UrbanEventKind.TRANSIT_STRIKE, 3)).availabilityPermille(Tick(1)))
        assertEquals(1000, book(event(UrbanEventKind.GROWTH_BOOM)).availabilityPermille(Tick(1)))
    }

    @Test fun `protests freeze building in their district only`() {
        val b = book(event(UrbanEventKind.GENTRIFICATION_BACKLASH, 1, 4))
        assertTrue(b.developmentFrozen(4, Tick(5)))
        assertFalse(b.developmentFrozen(3, Tick(5)))
        assertFalse(b.developmentFrozen(4, Tick(500)))
    }

    @Test fun `pruning forgets only old finished events`() {
        val old = event(UrbanEventKind.FIRE, 1, 1, 0, 10).also { it.ended = true }
        val fresh = event(UrbanEventKind.FIRE, 2, 1, 0, 400).also { it.ended = true }
        val running = event(UrbanEventKind.FLOOD, 3, 1, 0, 20)
        val b = book(old, fresh, running)
        b.prune(Tick(500), 100L)
        assertEquals(listOf(2L, 3L), b.all().map { it.id.value })
    }

    private fun rolls(seed: Long, months: Int, ds: List<District> = UrbanTestSupport.districts(floorArea = 50_000L, zones = mapOf(2 to ZoneUse.INDUSTRIAL)), c: EventClimate = climate, lines: List<TransitLine> = emptyList()): List<UrbanEvent> {
        val rnd = DeterministicRandom(seed)
        var id = 0L
        val book = EventBook()
        val out = ArrayList<UrbanEvent>()
        for (m in 0 until months) {
            val now = Tick(m * 30L)
            val fresh = EventGenerator.roll(now, ds, c, book, rnd, { UrbanEventId(++id) }, lines)
            fresh.forEach { book.add(it) }
            out += fresh
        }
        return out
    }

    @Test fun `rolling is repeatable and varies with the seed`() {
        assertEquals(rolls(3, 120).map { it.kind to it.startedAt }, rolls(3, 120).map { it.kind to it.startedAt })
        assertTrue(rolls(3, 120).map { it.kind to it.startedAt } != rolls(4, 120).map { it.kind to it.startedAt })
    }

    @Test fun `the number of random draws does not depend on what happens`() {
        val a = DeterministicRandom(9); val b = DeterministicRandom(9)
        val ds = UrbanTestSupport.districts(floorArea = 50_000L)
        var id = 0L
        EventGenerator.roll(Tick(0), ds, climate, EventBook(), a, { UrbanEventId(++id) }, emptyList())
        EventGenerator.roll(Tick(0), UrbanTestSupport.districts(floorArea = 0L), climate.copy(hasIndustry = false, hasTransit = false), EventBook(), b, { UrbanEventId(++id) }, emptyList())
        assertEquals(a.nextLong(), b.nextLong())
    }

    @Test fun `over many months every random kind eventually happens`() {
        val kinds = rolls(5, 1_200).map { it.kind }.toSet()
        assertTrue(kinds.containsAll(listOf(UrbanEventKind.GROWTH_BOOM, UrbanEventKind.FLOOD, UrbanEventKind.FIRE, UrbanEventKind.FACTORY_CLOSURE)))
        assertFalse(UrbanEventKind.GENTRIFICATION_BACKLASH in kinds)
    }

    @Test fun `coastal cities flood more often`() {
        val inland = rolls(7, 3_000).count { it.kind == UrbanEventKind.FLOOD }
        val coast = rolls(7, 3_000, c = climate.copy(coastal = true)).count { it.kind == UrbanEventKind.FLOOD }
        assertTrue(coast > inland)
    }

    @Test fun `good fire services reduce fires`() {
        val poor = rolls(8, 3_000, c = climate.copy(fireQualityPermille = 0)).count { it.kind == UrbanEventKind.FIRE }
        val good = rolls(8, 3_000, c = climate.copy(fireQualityPermille = 1000)).count { it.kind == UrbanEventKind.FIRE }
        assertTrue(good < poor)
    }

    @Test fun `no industry means no factory closures and no transit no strikes`() {
        val none = rolls(9, 2_000, c = climate.copy(hasIndustry = false, hasTransit = false))
        assertTrue(none.none { it.kind == UrbanEventKind.FACTORY_CLOSURE || it.kind == UrbanEventKind.TRANSIT_STRIKE })
    }

    @Test fun `closures strike industrial districts and fires strike built ones`() {
        val ds = UrbanTestSupport.districts(floorArea = 0L, zones = mapOf(2 to ZoneUse.INDUSTRIAL, 6 to ZoneUse.INDUSTRIAL))
        ds[2].stock.addNew(40_000L); ds[6].stock.addNew(40_000L); ds[5].stock.addNew(30_000L)
        val events = rolls(11, 2_000, ds)
        assertTrue(events.filter { it.kind == UrbanEventKind.FACTORY_CLOSURE }.all { it.district == 2 || it.district == 6 })
        assertTrue(events.filter { it.kind == UrbanEventKind.FIRE }.all { it.district in listOf(2, 5, 6) })
        assertTrue(events.filter { it.kind == UrbanEventKind.FLOOD }.all { it.district in listOf(2, 5, 6) })
    }

    @Test fun `a city with nothing built suffers no local disasters`() {
        val events = rolls(12, 1_500, UrbanTestSupport.districts(floorArea = 0L))
        assertTrue(events.none { it.kind.needsDistrict })
        assertTrue(events.isNotEmpty())
    }

    @Test fun `booms and strikes do not overlap themselves`() {
        val events = rolls(13, 3_000, lines = listOf(UrbanTestSupport.activeLine(UrbanTestSupport.gridNetwork(), 1, "L", TransitMode.BUS, listOf(0, 1))))
        for (kind in listOf(UrbanEventKind.GROWTH_BOOM, UrbanEventKind.TRANSIT_STRIKE)) {
            val of = events.filter { it.kind == kind }.sortedBy { it.startedAt }
            for ((x, y) in of.zipWithNext()) assertTrue(y.startedAt >= x.endsAt)
        }
    }

    @Test fun `strikes stop the active lines`() {
        val line = UrbanTestSupport.activeLine(UrbanTestSupport.gridNetwork(), 4, "L", TransitMode.BUS, listOf(0, 1))
        val strikes = rolls(14, 3_000, lines = listOf(line)).filter { it.kind == UrbanEventKind.TRANSIT_STRIKE }
        assertTrue(strikes.isNotEmpty())
        assertTrue(strikes.all { it.lines == listOf(line.id) })
    }

    @Test fun `durations and magnitudes stay in range`() {
        for (e in rolls(15, 2_000)) {
            val days = e.endsAt.value - e.startedAt.value
            assertTrue("${e.kind} $days", days in 5L..360L)
            assertTrue(e.magnitudePermille in 0..1000)
        }
    }

    @Test fun `angry districts start a movement once`() {
        val ds = UrbanTestSupport.districts()
        ds[3].backlashPpm = 800_000L
        ds[5].backlashPpm = 100_000L
        var id = 0L
        val b = EventBook()
        val fresh = EventGenerator.backlashes(Tick(10), ds, b, { UrbanEventId(++id) })
        assertEquals(listOf(3), fresh.map { it.district })
        assertEquals(UrbanEventKind.GENTRIFICATION_BACKLASH, fresh[0].kind)
        assertEquals(800, fresh[0].magnitudePermille)
        assertEquals(Tick(100), fresh[0].endsAt)
        fresh.forEach { b.add(it) }
        assertTrue(EventGenerator.backlashes(Tick(20), ds, b, { UrbanEventId(++id) }).isEmpty())
        assertEquals(1, EventGenerator.backlashes(Tick(200), ds, b, { UrbanEventId(++id) }).size)
    }

    @Test fun `every kind has a description that names the place`() {
        for (k in UrbanEventKind.values()) {
            val e = event(k, 1, if (k.needsDistrict) 2 else -1)
            val text = EventGenerator.describe(e, "Old Town")
            assertTrue(k.name, text.isNotBlank())
            if (k.needsDistrict) assertTrue(text.contains("Old Town"))
        }
        assertTrue(EventGenerator.describe(event(UrbanEventKind.FLOOD), null).contains("the city"))
    }

    @Test fun `hash covers the events`() {
        fun h(b: EventBook) = CanonicalHasher().also { b.hashInto(it) }.finish()
        val a = book(event(UrbanEventKind.FIRE, 1, 2)); val b = book(event(UrbanEventKind.FIRE, 1, 2))
        assertEquals(h(a), h(b))
        b.get(UrbanEventId(1))!!.damageSqm = 40L
        assertTrue(h(a) != h(b))
        assertEquals(Money.ZERO, Money.ZERO)
    }
}
