package app.many.economytycoon.urban

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalitiesTest {
    private val g = UrbanTestSupport.geometry()
    private fun surfaces(zones: Map<Int, ZoneUse>, base: ZoneUse = ZoneUse.RESIDENTIAL, services: ServiceEffects = ServiceEffects.NEUTRAL) =
        Externalities.compute(g, UrbanTestSupport.districts(base, 60_000L, zones), services)

    @Test fun `green space is always fully intense and an empty district is not`() {
        val green = UrbanTestSupport.district(zone = ZoneUse.GREEN)
        assertEquals(1000, Externalities.intensityPermille(green))
        assertEquals(0, Externalities.intensityPermille(UrbanTestSupport.district(zone = ZoneUse.INDUSTRIAL, floorArea = 0L)))
        val full = UrbanTestSupport.district(zone = ZoneUse.INDUSTRIAL, floorArea = 1_000_000L)
        assertEquals(1000, Externalities.intensityPermille(full))
    }

    @Test fun `the zone grid carries the zone of every cell`() {
        val z = Externalities.zoneGrid(g, UrbanTestSupport.districts(zones = mapOf(4 to ZoneUse.CIVIC)))
        assertEquals(ZoneUse.CIVIC.ordinal.toLong(), z[5, 5])
        assertEquals(ZoneUse.RESIDENTIAL.ordinal.toLong(), z[0, 0])
    }

    @Test fun `pollution peaks at a factory and fades with distance`() {
        val s = Externalities.compute(g, UrbanTestSupport.districts(ZoneUse.RESIDENTIAL, 2_000L, mapOf(4 to ZoneUse.INDUSTRIAL)).also { it[4].stock.addNew(30_000L) })
        val inside = s.pollution[5, 5]
        val edge = s.pollution[8, 5]
        val far = s.pollution[0, 0]
        assertTrue("$inside $edge $far", inside > edge && edge > far)
    }

    @Test fun `all surfaces stay within zero to a thousand`() {
        val s = surfaces(mapOf(0 to ZoneUse.INDUSTRIAL, 1 to ZoneUse.INDUSTRIAL, 3 to ZoneUse.INDUSTRIAL, 4 to ZoneUse.COMMERCIAL), ZoneUse.INDUSTRIAL)
        for (grid in listOf(s.pollution, s.noise, s.crime, s.amenity)) {
            assertTrue(grid.min() >= 0L)
            assertTrue(grid.max() <= 1000L)
        }
    }

    @Test fun `parks raise amenity around them`() {
        val without = surfaces(emptyMap())
        val with = surfaces(mapOf(4 to ZoneUse.GREEN))
        assertTrue(with.amenity[5, 5] > without.amenity[5, 5])
        assertTrue(with.amenity[7, 5] > without.amenity[7, 5])
    }

    @Test fun `better sanitation and policing lower pollution and crime`() {
        val zones = mapOf(4 to ZoneUse.INDUSTRIAL, 0 to ZoneUse.COMMERCIAL)
        val poor = surfaces(zones, services = ServiceEffects(0, 0, 500))
        val good = surfaces(zones, services = ServiceEffects(1000, 1000, 500))
        assertTrue(good.pollution.sum() < poor.pollution.sum())
        assertTrue(good.crime.sum() < poor.crime.sum())
        assertEquals(poor.noise, good.noise)
    }

    @Test fun `traffic adds noise and pollution`() {
        val calm = UrbanTestSupport.districts()
        val busy = UrbanTestSupport.districts()
        busy[4].trafficLoadPermille = 900
        val a = Externalities.compute(g, calm)
        val b = Externalities.compute(g, busy)
        assertTrue(b.noise[5, 5] > a.noise[5, 5])
        assertTrue(b.pollution[5, 5] > a.pollution[5, 5])
    }

    @Test fun `deprived districts have more crime`() {
        val rich = UrbanTestSupport.districts()
        val poor = UrbanTestSupport.districts()
        poor[4].lowIncomeSharePpm = 800_000L
        rich[4].lowIncomeSharePpm = 100_000L
        assertTrue(Externalities.compute(g, poor).crime[5, 5] > Externalities.compute(g, rich).crime[5, 5])
    }

    @Test fun `public improvements add to amenity permanently`() {
        val base = UrbanTestSupport.districts()
        val improved = UrbanTestSupport.districts()
        improved[4].amenityBonus = 200
        assertTrue(Externalities.compute(g, improved).amenity[5, 5] > Externalities.compute(g, base).amenity[5, 5])
    }

    @Test fun `neighbour compatibility is negative next to a factory and positive next to a park`() {
        val zones = Grid(3, 1, longArrayOf(ZoneUse.RESIDENTIAL.ordinal.toLong(), ZoneUse.INDUSTRIAL.ordinal.toLong(), ZoneUse.GREEN.ordinal.toLong()))
        val n = Externalities.neighbourGrid(zones)
        assertEquals(Zoning.compatibility(ZoneUse.RESIDENTIAL, ZoneUse.INDUSTRIAL).toLong(), n[0, 0])
        assertEquals(Math.floorDiv((Zoning.compatibility(ZoneUse.INDUSTRIAL, ZoneUse.RESIDENTIAL) + Zoning.compatibility(ZoneUse.INDUSTRIAL, ZoneUse.GREEN)).toLong(), 2L), n[1, 0])
        assertTrue(n[1, 0] < 0L)
    }

    @Test fun `computation is repeatable`() {
        val a = surfaces(mapOf(2 to ZoneUse.COMMERCIAL, 6 to ZoneUse.INDUSTRIAL))
        val b = surfaces(mapOf(2 to ZoneUse.COMMERCIAL, 6 to ZoneUse.INDUSTRIAL))
        assertEquals(a.pollution, b.pollution)
        assertEquals(a.amenity, b.amenity)
        assertEquals(a.crime, b.crime)
    }
}
