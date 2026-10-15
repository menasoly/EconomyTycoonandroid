package app.many.economytycoon.facilityprofit

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfitMathTest {
    private fun m(n: Long) = Money.units(n)

    @Test fun defectsRemoveTheirShareOfOutput() {
        assertEquals(m(900), ProfitMath.goodOutput(m(1_000), 100_000))
        assertEquals(m(1_000), ProfitMath.goodOutput(m(1_000), 0))
        assertEquals(Money.ZERO, ProfitMath.goodOutput(m(1_000), 1_000_000))
    }

    @Test fun defectRateIsClamped() {
        assertEquals(m(1_000), ProfitMath.goodOutput(m(1_000), -5))
        assertEquals(Money.ZERO, ProfitMath.goodOutput(m(1_000), 5_000_000))
    }

    @Test fun batchProfitSubtractsEveryCost() {
        assertEquals(m(1_000 - 400 - 100 - 50), ProfitMath.profitPerBatch(m(1_000), 0, m(400), m(100), m(50)))
    }

    @Test fun batchProfitCanBeNegative() {
        assertEquals(m(-300), ProfitMath.profitPerBatch(m(100), 0, m(300), m(60), m(40)))
    }

    @Test fun perDayDividesByDuration() {
        assertEquals(m(50), ProfitMath.perDay(m(100), 2, 1_000_000))
        assertEquals(m(25), ProfitMath.perDay(m(100), 2, 500_000))
    }

    @Test fun perDayAtZeroUtilisationIsZero() = assertEquals(Money.ZERO, ProfitMath.perDay(m(100), 2, 0))

    @Test fun perDayClampsUtilisationAndDuration() {
        assertEquals(m(100), ProfitMath.perDay(m(100), 0, 5_000_000))
        assertEquals(m(100), ProfitMath.perDay(m(100), -3, 1_000_000))
    }

    @Test fun returnOnCapital() {
        // 10 a day on 1,800 over a 360 day year is 3,600 on 1,800: 200%.
        assertEquals(2_000_000L, ProfitMath.annualReturnPpm(m(10), m(1_800)))
        assertEquals(0L, ProfitMath.annualReturnPpm(m(10), Money.ZERO))
        assertEquals(-1_000_000L, ProfitMath.annualReturnPpm(m(-5), m(1_800)))
    }

    @Test fun paybackRoundsUp() {
        assertEquals(10L, ProfitMath.paybackDays(m(10), m(100)))
        assertEquals(11L, ProfitMath.paybackDays(m(10), m(101)))
        assertNull(ProfitMath.paybackDays(Money.ZERO, m(100)))
        assertNull(ProfitMath.paybackDays(m(-1), m(100)))
    }

    @Test fun marginIsProfitOverCost() {
        assertEquals(250_000L, ProfitMath.marginPpm(m(25), m(100)))
        assertEquals(-500_000L, ProfitMath.marginPpm(m(-50), m(100)))
        assertEquals(0L, ProfitMath.marginPpm(m(5), Money.ZERO))
    }

    @Test fun shareIsCappedAtWhole() {
        assertEquals(250_000L, ProfitMath.sharePpm(m(25), m(100)))
        assertEquals(1_000_000L, ProfitMath.sharePpm(m(500), m(100)))
        assertEquals(0L, ProfitMath.sharePpm(m(-5), m(100)))
        assertEquals(0L, ProfitMath.sharePpm(m(5), Money.ZERO))
    }
}
