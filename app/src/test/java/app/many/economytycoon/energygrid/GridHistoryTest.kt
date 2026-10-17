package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridHistoryTest {
    private fun hour(h: Int, demand: Long = 100_000L, price: Money = Money.units(50), unserved: Long = 0L, gas: Long = 100_000L, reserve: Long = 500_000L, curtailed: Long = 0L) =
        HourProfile(
            h, demand, price, unserved, curtailed, 0L, 0L, 0L, 0L, reserve,
            Technology.values().map { if (it == Technology.GAS) gas else 0L }, 7L,
        )

    private fun day(f: (Int) -> HourProfile = { hour(it) }) = List(24) { f(it) }

    @Test fun aFlatDayHasTheFlatNumbers() {
        val s = DaySummary.of(5L, day(), Money.units(2000))
        assertEquals(Money.units(50), s.averagePrice)
        assertEquals(2_400_000L, s.demandKwh)
        assertEquals(2_400_000L, s.servedKwh)
        assertEquals(100_000L, s.peakDemandKw)
        assertEquals(2_400_000L, s.generationKwh[Technology.GAS.ordinal])
        // 2,400 MWh of gas at 400 kg each
        assertEquals(960_000L, s.carbonKg)
        assertEquals(0, s.shedHours)
        assertEquals(Money.ZERO, s.lostLoadValue)
    }

    @Test fun averagePriceIsWeightedByEnergyServed() {
        val s = DaySummary.of(0L, day { if (it == 18) hour(it, price = Money.units(100)) else hour(it) }, Money.units(2000))
        assertEquals(Money(520_833L), s.averagePrice)
        assertEquals(Money.units(100), s.peakPrice)
        assertEquals(Money.units(50), s.lowPrice)
    }

    @Test fun unservedHoursAreCountedAndValued() {
        val s = DaySummary.of(0L, day { if (it in 18..19) hour(it, unserved = 10_000L) else hour(it) }, Money.units(2000))
        assertEquals(2, s.shedHours)
        assertEquals(20_000L, s.unservedKwh)
        assertEquals(Money.units(40_000), s.lostLoadValue)
        assertEquals(2_400_000L - 20_000L, s.servedKwh)
    }

    @Test fun thinReserveHoursAreCounted() {
        val s = DaySummary.of(0L, day { hour(it, reserve = if (it < 3) 50_000L else 400_000L) }, Money.units(2000))
        assertEquals(3, s.scarcityHours)
        assertEquals(50_000L, s.lowestReservePpm)
    }

    @Test fun curtailmentAndTradeAreSummed() {
        val s = DaySummary.of(0L, day { hour(it, curtailed = 1_000L).copy(importKw = 200L, exportKw = 50L) }, Money.units(2000))
        assertEquals(24_000L, s.curtailedKwh)
        assertEquals(4_800L, s.importKwh)
        assertEquals(1_200L, s.exportKwh)
    }

    @Test fun anEmptyDayStillSummarises() {
        val s = DaySummary.of(0L, day { hour(it, demand = 0L, gas = 0L) }, Money.units(2000))
        assertEquals(0L, s.demandKwh)
        assertEquals(Money.units(50), s.averagePrice)
    }

    @Test(expected = IllegalArgumentException::class) fun aDayNeedsTwentyFourHours() {
        DaySummary.of(0L, day().take(23), Money.units(2000))
    }

    @Test fun hoursCountGenerationAcrossTechnologies() {
        val h = hour(0).copy(outputKw = Technology.values().map { 10L })
        assertEquals(70L, h.generationKw)
    }

    @Test(expected = IllegalArgumentException::class) fun anHourNeedsOneOutputPerTechnology() {
        hour(0).copy(outputKw = listOf(1L, 2L))
    }

    @Test fun hashesAreStableAndSensitive() {
        fun h(s: DaySummary) = CanonicalHasher().also { s.hashInto(it) }.finish()
        val a = DaySummary.of(1L, day(), Money.units(2000))
        assertEquals(h(a), h(DaySummary.of(1L, day(), Money.units(2000))))
        assertNotEquals(h(a), h(DaySummary.of(2L, day(), Money.units(2000))))
        fun hp(p: HourProfile) = CanonicalHasher().also { p.hashInto(it) }.finish()
        assertNotEquals(hp(hour(1)), hp(hour(1, price = Money.units(51))))
        assertTrue(hp(hour(1)) == hp(hour(1)))
    }
}
