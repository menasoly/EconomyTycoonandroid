package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackoutTest {
    private fun draw(id: Long, c: CustomerClass, kw: Long) = LoadDraw(LoadId(id), c, kw)

    private val four = listOf(
        draw(4, CustomerClass.HOUSEHOLD, 400),
        draw(3, CustomerClass.INDUSTRIAL, 300),
        draw(2, CustomerClass.COMMERCIAL, 200),
        draw(1, CustomerClass.INTERRUPTIBLE, 100),
    )

    @Test fun nothingIsShedWhenNothingIsShort() {
        assertTrue(LoadShedder.shed(four, 0L).isEmpty())
    }

    @Test fun interruptibleCustomersGoFirst() {
        assertEquals(listOf(ShedAllocation(LoadId(1), 50)), LoadShedder.shed(four, 50))
    }

    @Test fun thenShopsThenFactoriesThenHomes() {
        assertEquals(listOf(ShedAllocation(LoadId(1), 100), ShedAllocation(LoadId(2), 50)), LoadShedder.shed(four, 150))
        val big = LoadShedder.shed(four, 700)
        assertEquals(listOf(100L, 200L, 300L, 100L), big.map { it.kw })
        assertEquals(listOf(1L, 2L, 3L, 4L), big.map { it.load.value })
    }

    @Test fun aTierSharesTheCutInProportionToItsDraw() {
        val tier = listOf(draw(1, CustomerClass.INDUSTRIAL, 100), draw(2, CustomerClass.INDUSTRIAL, 300))
        assertEquals(listOf(25L, 75L), LoadShedder.shed(tier, 100).map { it.kw })
    }

    @Test fun oddKilowattsAreSharedWithoutLosingAny() {
        val tier = List(3) { draw(it + 1L, CustomerClass.COMMERCIAL, 1) }
        val cut = LoadShedder.shed(tier, 2)
        assertEquals(2L, cut.sumOf { it.kw })
        assertTrue(cut.all { it.kw <= 1L })
    }

    @Test fun cutsNeverExceedWhatIsDrawnAndAddUpToTheShortfall() {
        for (short in 0L..1000L step 37L) {
            val cut = LoadShedder.shed(four, short)
            assertEquals(minOf(short, 1000L), cut.sumOf { it.kw })
            for (c in cut) assertTrue(c.kw <= four.first { it.load == c.load }.kw)
        }
    }

    @Test fun shortfallBeyondAllLoadShedsEverything() {
        assertEquals(1000L, LoadShedder.shed(four, 5_000).sumOf { it.kw })
    }

    @Test fun idleLoadsAreSkipped() {
        val draws = listOf(draw(1, CustomerClass.INTERRUPTIBLE, 0), draw(2, CustomerClass.COMMERCIAL, 10))
        assertEquals(listOf(ShedAllocation(LoadId(2), 5)), LoadShedder.shed(draws, 5))
    }

    @Test fun damageGrowsWithHoursAndIsCapped() {
        assertEquals(100_000L, BlackoutDamage.conditionLossPpm(CustomerClass.INDUSTRIAL, 5))
        assertEquals(BlackoutDamage.MAX_LOSS_PPM, BlackoutDamage.conditionLossPpm(CustomerClass.INDUSTRIAL, 20))
        assertEquals(0L, BlackoutDamage.conditionLossPpm(CustomerClass.HOUSEHOLD, 10))
        assertTrue(BlackoutDamage.conditionLossPpm(CustomerClass.INDUSTRIAL, 1) > BlackoutDamage.conditionLossPpm(CustomerClass.COMMERCIAL, 1))
    }

    @Test fun aRecordAccumulatesItsHours() {
        val r = BlackoutRecord(BlackoutId(1), GridId(1), 10L, 22)
        r.addHour(300L, listOf(LoadId(1), LoadId(2)), Money.units(5))
        r.addHour(500L, listOf(LoadId(2), LoadId(3)), Money.units(7))
        assertEquals(2, r.hours)
        assertEquals(500L, r.peakShedKw)
        assertEquals(800L, r.shedKwh)
        assertEquals(3, r.loadsAffected)
        assertEquals(Money.units(12), r.damage)
        assertTrue(r.open)
        r.close(11L)
        assertEquals(11L, r.endTick)
        assertEquals(10L * 24 + 22, r.startedAtHour)
    }
}
