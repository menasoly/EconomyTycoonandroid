package app.many.economytycoon.marketing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockDynamicsTest {
    private val params = MarketingParams()
    private fun impulse(vararg pairs: Pair<Channel, Long>): LongArray {
        val a = LongArray(Channel.values().size)
        for ((c, v) in pairs) a[c.ordinal] = v
        return a
    }

    @Test fun anIdleBrandStaysAtZero() {
        val s = BrandStock()
        repeat(10) { StockDynamics.step(s, impulse(), Ppm.ONE, params) }
        assertEquals(0L, s.awarenessPpm)
        assertEquals(0L, s.loyaltyPpm)
        assertTrue(s.isDormant)
    }

    @Test fun exposureBuildsAwareness() {
        val s = BrandStock()
        repeat(30) { StockDynamics.step(s, impulse(Channel.TELEVISION to 300_000L), Ppm.ONE, params) }
        assertTrue(s.awarenessPpm > 100_000L)
    }

    @Test fun awarenessGrowthSlowsAsItFills() {
        val s = BrandStock()
        var last = 0L
        var lastGain = Long.MAX_VALUE
        val gains = ArrayList<Long>()
        repeat(200) {
            StockDynamics.step(s, impulse(Channel.TELEVISION to 400_000L, Channel.RADIO to 300_000L), Ppm.ONE, params)
            if (it >= 40 && it % 40 == 0) { gains += s.awarenessPpm - last; lastGain = gains.last() }
            if (it % 40 == 0) last = s.awarenessPpm
        }
        assertTrue(gains.size >= 3)
        assertTrue("gains $gains", gains.first() > lastGain)
    }

    @Test fun awarenessSettlesBelowOne() {
        val s = BrandStock()
        repeat(600) { StockDynamics.step(s, impulse(Channel.TELEVISION to 900_000L, Channel.ONLINE to 600_000L), Ppm.ONE, params) }
        assertTrue(s.awarenessPpm < 1_000_000L)
        assertTrue(s.awarenessPpm > 700_000L)
    }

    @Test fun awarenessFadesWhenTheMoneyStops() {
        val s = BrandStock()
        repeat(120) { StockDynamics.step(s, impulse(Channel.TELEVISION to 500_000L), Ppm.ONE, params) }
        val peak = s.awarenessPpm
        repeat(60) { StockDynamics.step(s, impulse(), Ppm.ONE, params) }
        assertTrue(s.awarenessPpm < peak)
        assertTrue(s.awarenessPpm > peak / 3)
    }

    @Test fun carryOverKeepsExposureAliveForAWhile() {
        val tv = BrandStock(); val online = BrandStock()
        repeat(40) {
            StockDynamics.step(tv, impulse(Channel.TELEVISION to 500_000L), Ppm.ONE, params)
            StockDynamics.step(online, impulse(Channel.ONLINE to 500_000L), Ppm.ONE, params)
        }
        repeat(5) {
            StockDynamics.step(tv, impulse(), Ppm.ONE, params)
            StockDynamics.step(online, impulse(), Ppm.ONE, params)
        }
        assertTrue(tv.adstock(Channel.TELEVISION) > online.adstock(Channel.ONLINE))
    }

    @Test fun adstockApproachesTheImpulse() {
        val s = BrandStock()
        repeat(100) { StockDynamics.step(s, impulse(Channel.RADIO to 250_000L), Ppm.ONE, params) }
        assertTrue(Math.abs(s.adstock(Channel.RADIO) - 250_000L) < 3_000L)
    }

    @Test fun loyaltyNeedsAwarenessAndIsCappedByIt() {
        val s = BrandStock()
        repeat(150) { StockDynamics.step(s, impulse(Channel.LOYALTY_PROGRAMME to 400_000L), Ppm.ONE, params) }
        assertTrue(s.awarenessPpm < 200_000L)
        assertTrue(s.loyaltyPpm <= s.awarenessPpm)
    }

    @Test fun loyaltyProgrammesBuildMoreLoyaltyThanPress() {
        val withLoyalty = BrandStock(); val pressOnly = BrandStock()
        withLoyalty.set(600_000L, 0L); pressOnly.set(600_000L, 0L)
        repeat(100) {
            StockDynamics.step(withLoyalty, impulse(Channel.LOYALTY_PROGRAMME to 300_000L), Ppm.ONE, params)
            StockDynamics.step(pressOnly, impulse(Channel.LOCAL_PRESS to 300_000L), Ppm.ONE, params)
        }
        assertTrue(withLoyalty.loyaltyPpm > pressOnly.loyaltyPpm)
    }

    @Test fun goodQualityPullsLoyaltyUpAndBadQualityDown() {
        val good = BrandStock(); val bad = BrandStock()
        good.set(700_000L, 300_000L); bad.set(700_000L, 300_000L)
        repeat(60) {
            StockDynamics.step(good, impulse(), 1_300_000L, params)
            StockDynamics.step(bad, impulse(), 600_000L, params)
        }
        assertTrue(good.loyaltyPpm > bad.loyaltyPpm)
    }

    @Test fun taintBluntsNewExposureAndFades() {
        val clean = BrandStock(); val tainted = BrandStock()
        tainted.setTaint(800_000L)
        repeat(20) {
            StockDynamics.step(clean, impulse(Channel.TELEVISION to 400_000L), Ppm.ONE, params)
            StockDynamics.step(tainted, impulse(Channel.TELEVISION to 400_000L), Ppm.ONE, params)
        }
        assertTrue(tainted.awarenessPpm < clean.awarenessPpm)
        assertTrue(tainted.taintPpm < 800_000L)
    }

    @Test fun everyValueStaysInRange() {
        val s = BrandStock()
        repeat(500) {
            StockDynamics.step(s, impulse(Channel.TELEVISION to 1_000_000L, Channel.ONLINE to 1_000_000L, Channel.SPONSORSHIP to 1_000_000L), 5_000_000L, params)
            assertTrue(s.awarenessPpm in 0..1_000_000L)
            assertTrue(s.loyaltyPpm in 0..1_000_000L)
            for (c in Channel.ordered) assertTrue(s.adstock(c) in 0..1_000_000L)
        }
    }

    @Test fun equilibriumMatchesLongRunSimulation() {
        val imp = impulse(Channel.RADIO to 300_000L, Channel.TELEVISION to 200_000L, Channel.SPONSORSHIP to 100_000L)
        val sim = StockDynamics.steadyState(imp, Ppm.ONE, params)
        val (a, l) = StockDynamics.equilibrium(imp, Ppm.ONE, params)
        assertTrue("awareness $a vs ${sim.awarenessPpm}", Math.abs(a - sim.awarenessPpm) < 15_000L)
        assertTrue("loyalty $l vs ${sim.loyaltyPpm}", Math.abs(l - sim.loyaltyPpm) < 15_000L)
    }

    @Test fun equilibriumOfNothingIsNothing() {
        assertEquals(0L to 0L, StockDynamics.equilibrium(impulse(), Ppm.ONE, params))
    }

    @Test fun moreExposureNeverLowersTheEquilibrium() {
        var last = -1L
        for (v in listOf(0L, 50_000L, 150_000L, 400_000L, 900_000L)) {
            val (a, _) = StockDynamics.equilibrium(impulse(Channel.TELEVISION to v), Ppm.ONE, params)
            assertTrue(a >= last)
            last = a
        }
    }

    @Test fun fadedAwarenessDecaysGeometrically() {
        assertEquals(Ppm.decay(800_000L, params.awarenessDecayPpm, 10), StockDynamics.fadedAwareness(800_000L, 10, params))
    }

    @Test fun copyIsIndependent() {
        val s = BrandStock(); s.set(500_000L, 100_000L)
        val c = s.copy()
        s.set(0L, 0L)
        assertEquals(500_000L, c.awarenessPpm)
    }
}
