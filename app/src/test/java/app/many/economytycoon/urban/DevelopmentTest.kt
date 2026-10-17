package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentTest {
    private val p = UrbanParams()

    /** A district that developers like: high rent against cheap land and room to grow. */
    private fun hot(zone: ZoneUse = ZoneUse.RESIDENTIAL, floor: Long = 40_000L, rent: Money = Money.units(40), land: Money = Money.units(600)): District {
        val d = UrbanTestSupport.district(0, zone, floor, 40_000L, rent)
        d.landValuePerSqm = land
        return d
    }

    @Test fun `use rent factors weight the shares of the zone`() {
        assertTrue(Development.useRentFactorPpm(ZoneUse.COMMERCIAL) > Development.useRentFactorPpm(ZoneUse.RESIDENTIAL))
        assertTrue(Development.useRentFactorPpm(ZoneUse.INDUSTRIAL) < Development.useRentFactorPpm(ZoneUse.RESIDENTIAL))
        assertEquals(0L, Development.useRentFactorPpm(ZoneUse.CIVIC))
    }

    @Test fun `tall buildings cost more per square metre, up to double`() {
        assertEquals(Scale.PPM, Development.heightPremiumPpm(4))
        assertEquals(Scale.PPM, Development.heightPremiumPpm(1))
        assertEquals(1_300_000L, Development.heightPremiumPpm(14))
        assertEquals(2_000_000L, Development.heightPremiumPpm(100))
    }

    @Test fun `value follows rent and cost follows land`() {
        val cheap = hot(land = Money.units(100))
        val dear = hot(land = Money.units(3000))
        assertTrue(Development.costPerSqm(dear, p) > Development.costPerSqm(cheap, p))
        assertTrue(Development.valuePerSqm(hot(rent = Money.units(60)), p) > Development.valuePerSqm(hot(rent = Money.units(30)), p))
    }

    @Test fun `margin is negative when rents are too low to pay for building`() {
        assertTrue(Development.marginPpm(hot(rent = Money.units(2)), p) < 0L)
        assertTrue(Development.marginPpm(hot(rent = Money.units(80)), p) > 0L)
    }

    @Test fun `profitable districts start new floor area up to the headroom`() {
        val d = hot()
        val starts = Development.monthlyStarts(d, p)
        assertTrue(starts > 0L)
        assertTrue(starts <= d.headroom)
        assertTrue(starts <= d.rules.maxFloorArea(d.landSqm) * p.maxStartsPpm / Scale.PPM * 3L)
    }

    @Test fun `nothing is built without a margin, room or a private use`() {
        assertEquals(0L, Development.monthlyStarts(hot(rent = Money.units(2)), p))
        assertEquals(0L, Development.monthlyStarts(hot(zone = ZoneUse.GREEN), p))
        assertEquals(0L, Development.monthlyStarts(hot(zone = ZoneUse.CIVIC), p))
        val full = hot(floor = 100_000L)
        assertEquals(0L, Development.monthlyStarts(full, p))
    }

    @Test fun `booms speed building and tenant protection slows it`() {
        val d = hot()
        val normal = Development.monthlyStarts(d, p)
        assertTrue(Development.monthlyStarts(d, p, 1_500_000L) > normal)
        d.tenantProtectionPermille = 1000
        assertTrue(Development.monthlyStarts(d, p) < normal)
    }

    @Test fun `a stalled pipeline completes after the build time`() {
        val d = hot()
        val first = Development.step(d, p, Tick(0))
        assertTrue(first.started > 0L)
        assertEquals(first.started, d.pipelineArea)
        val months = Development.buildMonths(d)
        val early = Development.step(d, p, Tick(30L * (months - 1)), frozen = true)
        assertEquals(0L, early.completed)
        val before = d.floorArea
        val done = Development.step(d, p, Tick(30L * months), frozen = true)
        assertEquals(first.started, done.completed)
        assertTrue(d.floorArea >= before + first.started)
    }

    @Test fun `a freeze stops starts and demolition but lets work finish`() {
        val d = hot()
        Development.step(d, p, Tick(0))
        val inFlight = d.pipelineArea
        val r = Development.step(d, p, Tick(30L * 40), frozen = true)
        assertEquals(inFlight, r.completed)
        assertEquals(0L, r.started)
        assertEquals(0L, r.demolished)
    }

    @Test fun `old worn buildings are redeveloped when the land is full and valuable`() {
        val d = UrbanTestSupport.district(0, ZoneUse.RESIDENTIAL, 0L, 10_000L, Money.units(60))
        d.landValuePerSqm = Money.units(100)
        d.stock.restore(longArrayOf(0, 0, 0, 0, 24_000L), longArrayOf(Scale.PPM, Scale.PPM, Scale.PPM, Scale.PPM, 250_000L), LongArray(5))
        val gone = Development.monthlyDemolition(d, p)
        assertTrue(gone > 0L)
        assertEquals(24_000L * 6_000L / Scale.PPM, gone)
        d.tenantProtectionPermille = 1000
        assertTrue(Development.monthlyDemolition(d, p) < gone)
    }

    @Test fun `good condition buildings and green land are not demolished`() {
        val good = hot(floor = 100_000L)
        assertEquals(0L, Development.monthlyDemolition(good, p))
        assertEquals(0L, Development.monthlyDemolition(hot(zone = ZoneUse.GREEN, floor = 50L), p))
    }

    @Test fun `heritage districts keep their oldest buildings`() {
        val d = UrbanTestSupport.district(0, ZoneUse.RESIDENTIAL, 0L, 10_000L, Money.units(60))
        d.landValuePerSqm = Money.units(100)
        d.stock.restore(longArrayOf(0, 0, 0, 0, 24_000L), longArrayOf(Scale.PPM, Scale.PPM, Scale.PPM, Scale.PPM, 250_000L), LongArray(5))
        d.heritage = true
        val r = Development.step(d, p, Tick(0))
        assertEquals(0L, r.demolished)
        assertEquals(24_000L, d.stock.areaOf(4))
    }

    @Test fun `floor area never exceeds the density limit over many months`() {
        val d = hot()
        var t = 0L
        repeat(120) { Development.step(d, p, Tick(t)); t += 30L }
        assertTrue(d.floorArea <= d.maxFloorArea)
        assertTrue(d.floorArea + d.pipelineArea <= d.maxFloorArea + 1L)
    }

    @Test fun `build time grows with height but is capped`() {
        val low = hot(); val tall = hot(zone = ZoneUse.COMMERCIAL)
        assertTrue(Development.buildMonths(tall) > Development.buildMonths(low))
        tall.rules = DensityRules(9_000, 200)
        assertEquals(8 + 40 / 3, Development.buildMonths(tall))
    }
}
