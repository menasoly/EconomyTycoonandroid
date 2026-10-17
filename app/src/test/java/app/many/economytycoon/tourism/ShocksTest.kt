package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Season
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShocksTest {
    private fun shock(kind: ShockKind, severity: Long = 1_000_000L, start: Long = 100L, days: Int = 10, dest: DestinationId? = DestinationId(1)) =
        Shock(ShockId(1), kind, dest, null, Tick(start), days, severity, kind.label)

    @Test fun aShockIsActiveFromItsStartForItsDuration() {
        val s = shock(ShockKind.STRIKE)
        assertFalse(s.activeAt(Tick(99)))
        assertTrue(s.activeAt(Tick(100)))
        assertTrue(s.activeAt(Tick(109)))
        assertFalse(s.activeAt(Tick(110)))
        assertEquals(Tick(110), s.end)
    }

    @Test fun aShockWithoutADestinationTouchesEveryone() {
        assertTrue(shock(ShockKind.DISEASE, dest = null).touches(DestinationId(9)))
        assertTrue(shock(ShockKind.DISEASE).touches(DestinationId(1)))
        assertFalse(shock(ShockKind.DISEASE).touches(DestinationId(2)))
    }

    @Test fun remainingShareFallsToZero() {
        val s = shock(ShockKind.STRIKE, days = 10)
        assertEquals(1_000_000L, s.remainingPpm(Tick(50)))
        assertEquals(500_000L, s.remainingPpm(Tick(105)))
        assertEquals(0L, s.remainingPpm(Tick(110)))
    }

    @Test(expected = IllegalArgumentException::class) fun zeroDurationIsRejected() {
        shock(ShockKind.STRIKE, days = 0)
    }

    @Test(expected = IllegalArgumentException::class) fun zeroSeverityIsRejected() {
        shock(ShockKind.STRIKE, severity = 0L)
    }

    @Test fun badShocksReduceArrivalsAndGoodOnesRaiseThem() {
        for (k in ShockKind.values()) {
            val e = ShockEffects.of(shock(k))
            for (s in VisitorSegment.values()) {
                if (k.isBad) assertTrue("$k $s", e.arrivals(s) <= 1_000_000L) else assertTrue("$k $s", e.arrivals(s) >= 1_000_000L)
            }
        }
    }

    @Test fun severityScalesTheEffect() {
        val mild = ShockEffects.of(shock(ShockKind.DISEASE, severity = 300_000L))
        val harsh = ShockEffects.of(shock(ShockKind.DISEASE, severity = 900_000L))
        assertTrue(harsh.arrivals(VisitorSegment.FAMILY) < mild.arrivals(VisitorSegment.FAMILY))
        assertTrue(harsh.cancelExtraPpm > mild.cancelExtraPpm)
        assertTrue(harsh.staffPpm < mild.staffPpm)
    }

    @Test fun fullSeverityGivesTheTableValue() {
        val e = ShockEffects.of(shock(ShockKind.DISEASE))
        assertEquals(ShockKind.DISEASE.arrivalsPpm, e.arrivals(VisitorSegment.BUDGET))
        assertEquals(ShockKind.DISEASE.safetyDropPpm, e.safetyDropPpm)
        assertEquals(ShockKind.DISEASE.transportPpm, e.transportPpm)
    }

    @Test fun aConferenceDrawsBusinessMostAndAFestivalFamilies() {
        val conf = ShockEffects.of(shock(ShockKind.CONFERENCE))
        assertTrue(conf.arrivals(VisitorSegment.BUSINESS) > conf.arrivals(VisitorSegment.FAMILY))
        val fest = ShockEffects.of(shock(ShockKind.FESTIVAL))
        assertTrue(fest.arrivals(VisitorSegment.FAMILY) > fest.arrivals(VisitorSegment.BUSINESS))
        assertTrue(fest.eventPpm > 0L)
    }

    @Test fun aStrikeHurtsBusinessTravelMost() {
        val e = ShockEffects.of(shock(ShockKind.STRIKE))
        assertTrue(e.arrivals(VisitorSegment.BUSINESS) < e.arrivals(VisitorSegment.LUXURY))
    }

    @Test fun combiningNothingGivesNoEffect() {
        assertEquals(ShockEffects.NONE, ShockEffects.combine(emptyList()))
    }

    @Test fun combinedMultipliersMultiplyAndDropsAddUpToOne() {
        val a = ShockEffects.of(shock(ShockKind.DISEASE))
        val b = ShockEffects.of(shock(ShockKind.SAFETY_SCARE))
        val c = ShockEffects.combine(listOf(a, b))
        val product = a.arrivals(VisitorSegment.BUDGET) * b.arrivals(VisitorSegment.BUDGET) / 1_000_000L
        assertTrue(Math.abs(c.arrivals(VisitorSegment.BUDGET) - product) <= 1L)
        assertEquals(minOf(1_000_000L, a.safetyDropPpm + b.safetyDropPpm), c.safetyDropPpm)
        val many = ShockEffects.combine(List(10) { b })
        assertEquals(1_000_000L, many.safetyDropPpm)
        assertTrue(many.cancelExtraPpm <= 1_000_000L)
    }

    @Test fun eventStrengthTakesTheLargest() {
        val f = ShockEffects.of(shock(ShockKind.FESTIVAL))
        val c = ShockEffects.of(shock(ShockKind.CONFERENCE))
        assertEquals(maxOf(f.eventPpm, c.eventPpm), ShockEffects.combine(listOf(f, c)).eventPpm)
    }

    @Test fun combiningIsOrderIndependent() {
        val a = ShockEffects.of(shock(ShockKind.DISEASE, 400_000L))
        val b = ShockEffects.of(shock(ShockKind.WEATHER, 700_000L))
        val c = ShockEffects.of(shock(ShockKind.FESTIVAL, 500_000L))
        val x = ShockEffects.combine(listOf(a, b, c))
        val y = ShockEffects.combine(listOf(c, a, b))
        for (s in VisitorSegment.values()) assertTrue(Math.abs(x.arrivals(s) - y.arrivals(s)) <= 2L)
        assertEquals(x.safetyDropPpm, y.safetyDropPpm)
    }

    @Test fun rollsHappenOnlyOnRollDays() {
        val rnd = DeterministicRandom(1L)
        for (t in 1L..6L) assertTrue(ShockGenerator.roll(Tick(t), ClimateZone.COLD, emptySet(), rnd).isEmpty())
    }

    @Test fun rollsAreDeterministicAndConsumeTheSameDrawsWhateverIsActive() {
        val a = DeterministicRandom(5L)
        val b = DeterministicRandom(5L)
        ShockGenerator.roll(Tick(14), ClimateZone.COLD, emptySet(), a)
        ShockGenerator.roll(Tick(14), ClimateZone.COLD, ShockKind.values().toSet(), b)
        assertEquals(a.state, b.state)
    }

    @Test fun anActiveKindIsNotStartedAgain() {
        for (seed in 1L..400L) {
            val out = ShockGenerator.roll(Tick(14), ClimateZone.COLD, setOf(ShockKind.DISEASE, ShockKind.STRIKE, ShockKind.CONFERENCE, ShockKind.SAFETY_SCARE, ShockKind.TRANSPORT_DISRUPTION), DeterministicRandom(seed))
            assertTrue(out.isEmpty())
        }
    }

    @Test fun rollsProduceSensibleShocksOverManySeeds() {
        var hits = 0
        for (seed in 1L..2_000L) {
            for ((kind, days, severity) in ShockGenerator.roll(Tick(14), ClimateZone.TEMPERATE, emptySet(), DeterministicRandom(seed))) {
                hits++
                assertTrue(days >= 2)
                assertTrue(severity in 300_000L..900_000L)
                assertTrue(kind != ShockKind.WEATHER && kind != ShockKind.FESTIVAL)
            }
        }
        assertTrue("some shocks occur", hits > 5)
        assertTrue("but not many", hits < 400)
    }

    @Test fun winterRaisesTheDiseaseChance() {
        assertTrue(ShockGenerator.chancePpm(ShockKind.DISEASE, ClimateZone.TEMPERATE, Season.WINTER) > ShockGenerator.chancePpm(ShockKind.DISEASE, ClimateZone.TEMPERATE, Season.SUMMER))
        assertEquals(0L, ShockGenerator.chancePpm(ShockKind.FESTIVAL, ClimateZone.TEMPERATE, Season.SUMMER))
    }

    @Test fun festivalRunsFromTheFirstOfItsMonthForItsDays() {
        // Month 6 starts at tick 150.
        assertEquals(0L, ShockGenerator.festivalPpm(6, 7, Tick(149)))
        assertTrue(ShockGenerator.festivalPpm(6, 7, Tick(150)) > 0L)
        assertTrue(ShockGenerator.festivalPpm(6, 7, Tick(156)) > 0L)
        assertEquals(0L, ShockGenerator.festivalPpm(6, 7, Tick(157)))
        assertEquals(0L, ShockGenerator.festivalPpm(0, 7, Tick(150)))
    }

    @Test fun festivalArrivalsAreRaisedOnlyDuringTheFestival() {
        assertEquals(1_000_000L, ShockGenerator.festivalArrivalsPpm(VisitorSegment.FAMILY, 6, 7, Tick(200)))
        assertTrue(ShockGenerator.festivalArrivalsPpm(VisitorSegment.FAMILY, 6, 7, Tick(151)) > 1_000_000L)
        assertTrue(ShockGenerator.festivalArrivalsPpm(VisitorSegment.FAMILY, 6, 7, Tick(151)) > ShockGenerator.festivalArrivalsPpm(VisitorSegment.BUSINESS, 6, 7, Tick(151)))
    }

    @Test fun shockHashDependsOnEveryField() {
        fun h(s: Shock) = CanonicalHasher().also { s.hashInto(it) }.finish()
        val base = shock(ShockKind.STRIKE)
        assertEquals(h(base), h(shock(ShockKind.STRIKE)))
        assertNotEquals(h(base), h(shock(ShockKind.STRIKE, severity = 500_000L)))
        assertNotEquals(h(base), h(shock(ShockKind.STRIKE, days = 11)))
        assertNotEquals(h(base), h(shock(ShockKind.DISEASE)))
        assertNotEquals(h(base), h(shock(ShockKind.STRIKE, dest = null)))
    }
}
