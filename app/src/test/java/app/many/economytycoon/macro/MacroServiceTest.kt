package app.many.economytycoon.macro

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroServiceTest {
    private fun hash(s: MacroService) = CanonicalHasher().also { s.hashInto(it) }.finish().toHex()

    @Test fun gdpIsConsumptionPlusInvestmentPlusGovernmentPlusNetExports() {
        val g = GdpLedger()
        g.record(GdpKind.CONSUMPTION, Money.units(100)); g.record(GdpKind.INVESTMENT, Money.units(30))
        g.record(GdpKind.GOVERNMENT, Money.units(20)); g.record(GdpKind.EXPORTS, Money.units(15)); g.record(GdpKind.IMPORTS, Money.units(25))
        assertEquals(Money.units(140), g.openGdp())
        assertEquals(Money.units(140), g.close())
        assertEquals(Money.ZERO, g.openGdp())
        assertEquals(Money.units(140), g.ago(1))
        assertNull(g.ago(2))
    }

    @Test fun negativeFlowsAreIgnoredAndGdpNeverNegative() {
        val g = GdpLedger()
        g.record(GdpKind.CONSUMPTION, Money.units(-5))
        g.record(GdpKind.IMPORTS, Money.units(50))
        assertEquals(Money.ZERO, g.openGdp())
    }

    @Test fun reviewsOnlyHappenOnPeriodBoundaries() {
        val r = MacroRig()
        assertTrue(r.service.step(Tick(0), r.random).isEmpty())
        assertTrue(r.service.step(Tick(17), r.random).isEmpty())
        assertEquals(1, r.service.step(Tick(30), r.random).size)
    }

    @Test fun steadyEconomyHasNoOutputGapAndNoGrowth() {
        val r = MacroRig()
        val snaps = r.run(16)
        assertEquals(0L, snaps.last().outputGapPpm)
        assertEquals(0L, snaps.last().growthPpm)
        assertNull(snaps[5].growthPpm)
        assertEquals(Money.units(1_100_000), snaps.last().gdp)
    }

    @Test fun growthIsMeasuredAgainstTheSamePeriodLastYear() {
        val r = MacroRig()
        assertTrue(r.run(12).all { it.growthPpm == null })
        val s = r.run(1, consumption = 1_100_000).single()
        assertEquals(272_727L, s.growthPpm)
        val t = r.run(1, consumption = 400_000).single()
        assertTrue(t.growthPpm!! < 0)
    }

    @Test fun outputGapTurnsPositiveWhenOutputJumps() {
        val r = MacroRig()
        r.run(6)
        val boom = r.run(1, consumption = 1_600_000).single()
        assertTrue(boom.outputGapPpm > 0)
        val bust = r.run(1, consumption = 100_000).single()
        assertTrue(bust.outputGapPpm < 0)
    }

    @Test fun centralBankRaisesRatesWhenInflationRuns() {
        val r = MacroRig(); r.view.inflation[r.country] = 90_000L
        val first = r.run(1).single().policyRatePpm
        val later = r.run(12).last().policyRatePpm
        assertTrue(first > 40_000L)
        assertTrue(later > first)
        assertTrue(later <= r.params.maxPolicyRatePpm)
    }

    @Test fun centralBankCutsRatesWhenInflationIsBelowTarget() {
        val r = MacroRig(); r.view.inflation[r.country] = -10_000L
        val later = r.run(20).last().policyRatePpm
        assertTrue(later < 40_000L)
        assertTrue(later >= r.params.minPolicyRatePpm)
    }

    @Test fun rateMovesAreStepLimited() {
        val r = MacroRig(); r.view.inflation[r.country] = 200_000L
        val rates = r.run(5).map { it.policyRatePpm }
        var prev = 40_000L
        for (x in rates) { assertTrue(x - prev <= r.params.maxRateStepPpm); prev = x }
    }

    @Test fun manualModeHoldsTheChosenRate() {
        val r = MacroRig(); r.view.inflation[r.country] = 200_000L
        r.service.of(r.country).bank.setManual(10_000L)
        val last = r.run(10).last()
        assertEquals(10_000L, last.policyRatePpm)
        r.service.of(r.country).bank.useRule()
        assertTrue(r.run(3).last().policyRatePpm > 10_000L)
    }

    @Test fun businessCycleStaysInRangeAndMovesDemand() {
        val r = MacroRig()
        val snaps = r.run(120)
        assertTrue(snaps.all { it.confidencePpm in 50_000L..950_000L })
        assertTrue(snaps.all { it.demandShiftPpm in 850_000L..1_150_000L })
        assertTrue(snaps.map { it.phase }.toSet().size >= 2)
    }

    @Test fun shocksLowerConfidenceThenFade() {
        val a = MacroRig(); val b = MacroRig()
        a.service.shock(a.country, -300_000L)
        val sa = a.run(3).last(); val sb = b.run(3).last()
        assertTrue(sa.confidencePpm < sb.confidencePpm)
        val la = a.run(60).last(); val lb = b.run(60).last()
        assertTrue(Math.abs(la.confidencePpm - lb.confidencePpm) < 150_000L)
    }

    @Test fun differentSeedsGiveDifferentCycles() {
        val a = MacroRig(seed = 1).run(40).map { it.confidencePpm }
        val b = MacroRig(seed = 2).run(40).map { it.confidencePpm }
        assertTrue(a != b)
    }

    @Test fun sameSeedGivesSameHistory() {
        val a = MacroRig(seed = 5); val b = MacroRig(seed = 5)
        a.run(48); b.run(48)
        assertEquals(hash(a.service), hash(b.service))
    }

    @Test fun countriesAreIndependent() {
        val r = MacroRig(); r.view.ids += CountryId(2); r.view.inflation[CountryId(2)] = 120_000L
        r.run(10)
        assertTrue(r.service.policyRatePpm(CountryId(2)) > r.service.policyRatePpm(CountryId(1)))
        assertEquals(2, r.service.countries().size)
    }

    @Test fun historyIsCapped() {
        val r = MacroRig(params = MacroParams(historyPeriods = 10))
        r.run(25)
        assertEquals(10, r.service.history(r.country).size)
        assertEquals(10, r.service.of(r.country).gdp.past().size)
    }

    @Test fun saveAndLoadRoundTrips() {
        val r = MacroRig(); r.run(15)
        r.service.of(r.country).bank.setManual(55_000L)
        r.service.shock(r.country, 100_000L)
        r.service.recordRevenue(r.country, Money.units(500))
        val w = ByteWriter(); MacroCodec.write(r.service, w)
        val copy = MacroRig(); MacroCodec.read(ByteReader(w.toByteArray()), copy.service)
        assertEquals(hash(r.service), hash(copy.service))
        r.run(3); copy.tick = r.tick - 90; copy.run(3)
        assertEquals(r.service.latest(r.country)?.policyRatePpm, copy.service.latest(r.country)?.policyRatePpm)
    }

    @Test fun badVersionIsRejected() {
        val w = ByteWriter(); w.writeVarInt(42)
        assertTrue(runCatching { MacroCodec.read(ByteReader(w.toByteArray()), MacroRig().service) }.isFailure)
    }
}
