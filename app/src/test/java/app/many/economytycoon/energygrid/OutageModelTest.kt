package app.many.economytycoon.energygrid

import app.many.economytycoon.core.DeterministicRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutageModelTest {
    @Test fun aNewUnitFailsAtItsBaseRate() {
        assertEquals(4_000L, OutageModel.forcedChancePpm(Technology.GAS, 0L, 1_000_000L))
    }

    @Test fun badWeatherMultipliesTheRisk() {
        assertEquals(12_000L, OutageModel.forcedChancePpm(Technology.GAS, 0L, 3_000_000L))
    }

    @Test fun ageDoublesTheRateAtTheEndOfLife() {
        val life = Technology.GAS.profile.lifetimeTicks.toLong()
        assertEquals(6_000L, OutageModel.forcedChancePpm(Technology.GAS, life / 2, 1_000_000L))
        assertEquals(8_000L, OutageModel.forcedChancePpm(Technology.GAS, life, 1_000_000L))
        assertEquals(8_000L, OutageModel.forcedChancePpm(Technology.GAS, life * 3, 1_000_000L))
    }

    @Test fun theChanceIsCapped() {
        assertEquals(5_000L, OutageModel.forcedChancePpm(Technology.WIND, 0L, 6_000_000L, OutageParams(maxForcedChancePpm = 5_000L)))
    }

    @Test fun aCertainOutageLastsWithinTheTechnologysRange() {
        val rnd = DeterministicRandom(5)
        repeat(50) {
            val d = OutageModel.rollForced(1_000_000L, Technology.GAS, rnd)
            assertTrue(d in 2..5)
        }
    }

    @Test fun noChanceMeansNoOutage() {
        val rnd = DeterministicRandom(5)
        repeat(50) { assertEquals(0, OutageModel.rollForced(0L, Technology.COAL, rnd)) }
    }

    @Test fun theDrawsTakenDoNotDependOnTheOutcome() {
        val a = DeterministicRandom(11)
        val b = DeterministicRandom(11)
        OutageModel.rollForced(1_000_000L, Technology.COAL, a)
        OutageModel.rollForced(0L, Technology.COAL, b)
        assertEquals(a.state, b.state)
    }

    @Test fun outagesAreRepeatable() {
        val run = { seed: Long ->
            val r = DeterministicRandom(seed)
            List(200) { OutageModel.rollForced(20_000L, Technology.COAL, r) }
        }
        assertEquals(run(9), run(9))
        assertTrue(run(9).any { it > 0 })
    }

    @Test fun maintenanceFallsDueAfterItsInterval() {
        assertFalse(OutageModel.maintenanceDue(Technology.GAS, 100L, 189L))
        assertTrue(OutageModel.maintenanceDue(Technology.GAS, 100L, 190L))
    }

    @Test fun aTightGridPutsMaintenanceOff() {
        assertTrue(OutageModel.mayStartMaintenance(20_000L, 100_000L, 0))
        assertFalse(OutageModel.mayStartMaintenance(10_000L, 100_000L, 0))
        assertFalse(OutageModel.mayStartMaintenance(10_000L, 100_000L, 3))
    }

    @Test fun maintenanceCannotBePostponedForever() {
        assertTrue(OutageModel.mayStartMaintenance(-5_000L, 100_000L, OutageParams().maxDeferrals))
    }

    @Test fun anOutageEventKnowsItsLength() {
        assertEquals(5L, OutageEvent(GridId(1), UnitId(1), OutageKind.FORCED, 10L, 15L).days)
    }
}
