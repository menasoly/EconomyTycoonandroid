package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GentrificationTest {
    private fun district(growthPpm: Long, residents: Long = 10_000L): District {
        val d = UrbanTestSupport.district(0, ZoneUse.RESIDENTIAL, 100_000L)
        d.residents = residents
        d.rentPerSqm = Money.units(10)
        d.recordRent()
        d.rentPerSqm = Money(Money.units(10).raw + Money.units(10).raw * growthPpm / Scale.PPM)
        return d
    }

    @Test fun `calm rents cause no displacement`() {
        val d = district(40_000L)
        val before = d.lowIncomeSharePpm
        val r = Gentrification.step(d, 0)
        assertEquals(0L, r.displaced)
        assertEquals(before, d.lowIncomeSharePpm)
        assertEquals(0L, Gentrification.pressurePpm(d))
    }

    @Test fun `fast rising rents replace low income households`() {
        val d = district(400_000L)
        val before = d.lowIncomeSharePpm
        val r = Gentrification.step(d, 0)
        assertTrue(r.displaced > 0L)
        assertTrue(d.lowIncomeSharePpm < before)
        assertEquals(d.displacedTotal, r.displaced)
        assertTrue(r.backlashPpm > 0L)
        assertEquals(340_000L, Gentrification.pressurePpm(d))
    }

    @Test fun `tenant protection and housing aid slow the change`() {
        val open = district(400_000L); val shielded = district(400_000L); val aided = district(400_000L)
        shielded.tenantProtectionPermille = 1000
        Gentrification.step(open, 0); Gentrification.step(shielded, 0); Gentrification.step(aided, 1000)
        assertTrue(shielded.displacedTotal < open.displacedTotal)
        assertTrue(aided.displacedTotal < open.displacedTotal)
    }

    @Test fun `the low income share never falls below its floor`() {
        val d = district(900_000L)
        d.lowIncomeSharePpm = Gentrification.LOW_INCOME_FLOOR_PPM + 1_000L
        repeat(10) { Gentrification.step(d, 0) }
        assertEquals(Gentrification.LOW_INCOME_FLOOR_PPM, d.lowIncomeSharePpm)
    }

    @Test fun `falling rents let the mix recover to a ceiling`() {
        val d = district(-200_000L)
        d.lowIncomeSharePpm = Gentrification.LOW_INCOME_CEILING_PPM - 2_000L
        val r = Gentrification.step(d, 0)
        assertEquals(-2_000L, r.lowIncomeShiftPpm)
        assertEquals(Gentrification.LOW_INCOME_CEILING_PPM, d.lowIncomeSharePpm)
        Gentrification.step(d, 0)
        assertEquals(Gentrification.LOW_INCOME_CEILING_PPM, d.lowIncomeSharePpm)
    }

    @Test fun `backlash fades when the pressure stops`() {
        val d = district(0L)
        d.backlashPpm = 400_000L
        repeat(20) { Gentrification.step(d, 0) }
        assertTrue(d.backlashPpm < 400_000L * 40L / 100L)
        assertTrue(d.backlashPpm > 0L)
    }

    @Test fun `backlash stays a share`() {
        val d = district(5_000_000L)
        d.backlashPpm = 990_000L
        repeat(30) { Gentrification.step(d, 0) }
        assertTrue(d.backlashPpm in 0L..Scale.PPM)
    }

    @Test fun `an empty district displaces nobody`() {
        val d = district(400_000L, residents = 0L)
        assertEquals(0L, Gentrification.step(d, 0).displaced)
    }
}
