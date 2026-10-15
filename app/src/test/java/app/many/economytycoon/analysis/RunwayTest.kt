package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunwayTest {
    @Test fun aFallingBalanceBurnsAtADailyRate() {
        val r = Runway.of(Money.units(30_000), Money.units(-3_000), 30L)
        assertEquals(Money.units(100), r.burnPerDay)
        assertEquals(300L, r.days)
        assertTrue(r.isBurning)
        assertEquals(RunwayStatus.COMFORTABLE, r.status())
    }

    @Test fun growingCashHasNoEnd() {
        val r = Runway.of(Money.units(30_000), Money.units(3_000), 30L)
        assertNull(r.days)
        assertFalse(r.isBurning)
        assertEquals(RunwayStatus.GROWING, r.status())
    }

    @Test fun flatCashHasNoEnd() {
        assertEquals(RunwayStatus.GROWING, Runway.of(Money.units(500), Money.ZERO, 30L).status())
    }

    @Test fun shortRunwaysAreGradedByDays() {
        assertEquals(RunwayStatus.CRITICAL, Runway.of(Money.units(1_000), Money.units(-3_000), 30L).status())
        assertEquals(RunwayStatus.SHORT, Runway.of(Money.units(6_000), Money.units(-3_000), 30L).status())
        assertEquals(RunwayStatus.COMFORTABLE, Runway.of(Money.units(12_000), Money.units(-3_000), 30L).status())
    }

    @Test fun noCashIsEmptyWhateverTheTrend() {
        assertEquals(RunwayStatus.EMPTY, Runway.of(Money.ZERO, Money.units(500), 30L).status())
        assertEquals(RunwayStatus.EMPTY, Runway.of(Money.units(-5), Money.units(-500), 30L).status())
        assertEquals(0L, Runway.of(Money.units(-5), Money.units(-500), 30L).days)
    }

    @Test fun aZeroLengthPeriodDoesNotDivideByZero() {
        val r = Runway.of(Money.units(100), Money.units(-100), 0L)
        assertEquals(1L, r.days)
    }

    @Test fun everyStatusHasALabel() {
        assertTrue(RunwayStatus.values().all { it.label.isNotBlank() })
    }
}
