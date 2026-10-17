package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ZoningTest {
    private val p = UrbanParams()

    @Test fun `every zone splits its floor area completely`() {
        for (z in ZoneUse.values()) {
            val total = Zoning.residentialArea(z, 100_000L) + Zoning.commercialArea(z, 100_000L) + Zoning.industrialArea(z, 100_000L) + Zoning.civicArea(z, 100_000L)
            assertEquals(z.name, 100_000L, total)
        }
    }

    @Test fun `compatibility is symmetric and bounded`() {
        for (a in ZoneUse.values()) for (b in ZoneUse.values()) {
            assertEquals(Zoning.compatibility(a, b), Zoning.compatibility(b, a))
            assertTrue(Zoning.compatibility(a, b) in -500..500)
        }
        assertTrue(Zoning.compatibility(ZoneUse.RESIDENTIAL, ZoneUse.INDUSTRIAL) < 0)
        assertTrue(Zoning.compatibility(ZoneUse.RESIDENTIAL, ZoneUse.GREEN) > 0)
    }

    @Test fun `rezoning difficulty reflects what is being given up`() {
        assertEquals(0, Zoning.rezoneDifficulty(ZoneUse.MIXED, ZoneUse.MIXED))
        assertTrue(Zoning.rezoneDifficulty(ZoneUse.RESIDENTIAL, ZoneUse.MIXED) < Zoning.rezoneDifficulty(ZoneUse.RESIDENTIAL, ZoneUse.INDUSTRIAL))
        assertTrue(Zoning.rezoneDifficulty(ZoneUse.GREEN, ZoneUse.COMMERCIAL) > Zoning.rezoneDifficulty(ZoneUse.RESIDENTIAL, ZoneUse.COMMERCIAL))
        for (a in ZoneUse.values()) for (b in ZoneUse.values()) assertTrue(Zoning.rezoneDifficulty(a, b) in 0..1000)
    }

    @Test fun `civic land cannot become industrial and same use is not a change`() {
        assertFalse(Zoning.allowed(ZoneUse.CIVIC, ZoneUse.INDUSTRIAL))
        assertFalse(Zoning.allowed(ZoneUse.GREEN, ZoneUse.GREEN))
        assertTrue(Zoning.allowed(ZoneUse.CIVIC, ZoneUse.GREEN))
        assertTrue(Zoning.allowed(ZoneUse.INDUSTRIAL, ZoneUse.RESIDENTIAL))
    }

    @Test fun `remediation makes former industrial land dearer to rezone`() {
        val plain = Zoning.rezoneCost(100_000L, ZoneUse.RESIDENTIAL, ZoneUse.MIXED, p)
        val dirty = Zoning.rezoneCost(100_000L, ZoneUse.INDUSTRIAL, ZoneUse.MIXED, p)
        assertTrue(dirty > plain)
        assertTrue(plain >= p.petitionFee)
        assertTrue(Zoning.rezoneCost(1L, ZoneUse.GREEN, ZoneUse.CIVIC, p) > Money.ZERO)
    }

    @Test fun `capacity and jobs follow the area split`() {
        val area = 700_000L
        assertEquals(area * 920 / 1000 / p.sqmPerResident, Zoning.capacity(ZoneUse.RESIDENTIAL, area, p))
        assertEquals(0L, Zoning.capacity(ZoneUse.INDUSTRIAL, area, p))
        assertTrue(Zoning.jobs(ZoneUse.COMMERCIAL, area, p) > Zoning.jobs(ZoneUse.RESIDENTIAL, area, p))
        assertEquals(0L, Zoning.jobs(ZoneUse.COMMERCIAL, 0L, p))
    }

    @Test fun `floor area ratio caps follow land and height`() {
        val r = DensityRules(2_000, 10)
        assertEquals(200_000L, r.maxFloorArea(100_000L))
        assertEquals(1_200, r.farOf(120_000L, 100_000L))
        assertEquals(0, r.farOf(5L, 0L))
        assertEquals(6_000L * 10L * 10L, r.maxFloorAreaByHeight(10_000L) * 10L)
    }

    @Test fun `the effective ratio is the lower of the ratio and the height limit`() {
        assertEquals(2_000, DensityRules(2_000, 10).effectiveFarMilli())
        assertEquals(1_200, DensityRules(5_000, 2).effectiveFarMilli())
        assertEquals(600, DensityRules(5_000, 1).effectiveFarMilli())
    }

    @Test fun `invalid density rules are refused`() {
        try { DensityRules(-1, 3); fail() } catch (e: IllegalArgumentException) { }
        try { DensityRules(1000, 0); fail() } catch (e: IllegalArgumentException) { }
        try { DensityRules(1000, 3, 0L); fail() } catch (e: IllegalArgumentException) { }
        try { DensityRules(1000, 3, 2_000_000L); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `default rules come from the zone`() {
        for (z in ZoneUse.values()) {
            val r = DensityRules.defaultsFor(z)
            assertEquals(z.defaultFarMilli, r.maxFarMilli)
            assertEquals(z.defaultFloors, r.maxFloors)
        }
        assertFalse(ZoneUse.GREEN.isBuiltUp)
        assertTrue(ZoneUse.MIXED.isBuiltUp)
    }
}
