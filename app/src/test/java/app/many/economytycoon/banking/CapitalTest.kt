package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapitalTest {
    private val rules = BankingFx.params.capital

    private fun position(b: Bank) = CapitalEngine.position(b.snapshot(), rules)

    @Test fun freshBankIsWellCapitalised() {
        val p = position(BankingFx.bank())
        assertEquals(SupervisoryRung.NORMAL, p.rung)
        assertTrue(p.cet1Ratio > rules.minCet1)
        assertTrue(p.headroom.ppm > 0L)
        assertTrue(p.surplus.isPositive)
    }

    @Test fun totalIsTheSumOfItsParts() {
        val p = position(BankingFx.bank())
        assertEquals(p.rwa.credit + p.rwa.securities + p.rwa.other + p.rwa.operational, p.rwa.total)
    }

    @Test fun ratiosAreOrderedByCapitalTier() {
        val b = BankingFx.bank()
        b.additionalTier1 = Money.units(50_000); b.tier2 = Money.units(30_000); b.reserves += Money.units(80_000)
        val p = position(b)
        assertTrue(p.cet1Ratio < p.tier1Ratio)
        assertTrue(p.tier1Ratio < p.totalRatio)
    }

    @Test fun lossesMoveABankDownTheLadder() {
        val rungs = HashSet<SupervisoryRung>()
        val b = BankingFx.bank()
        val step = b.snapshot().cet1Capital.times(Rate.percent(8))
        var last = SupervisoryRung.NORMAL
        repeat(14) {
            b.reserves -= step; b.retained -= step
            val r = position(b).rung
            assertTrue("rung must never improve while losing money", r >= last)
            last = r
            rungs.add(r)
        }
        assertTrue(rungs.size >= 3)
        assertEquals(SupervisoryRung.CRITICAL, last)
    }

    @Test fun breachStartsAtRestricted() {
        assertFalse(SupervisoryRung.BUFFER.isBreach)
        assertTrue(SupervisoryRung.RESTRICTED.isBreach)
        assertTrue(SupervisoryRung.CRITICAL.isBreach)
    }

    @Test fun negativeCapitalIsCritical() {
        val b = BankingFx.bank()
        val loss = b.snapshot().bookEquity + Money.units(1)
        b.reserves -= loss; b.retained -= loss
        assertEquals(SupervisoryRung.CRITICAL, position(b).rung)
    }

    @Test fun largeBanksCarryTheSystemicBuffer() {
        val small = BankingFx.bank(capital = 1_000_000L).snapshot()
        val huge = small.copy(reserves = small.reserves + Money.units(rules.systemicAssetsUnits))
        assertEquals(rules.systemicBuffer.ppm, CapitalEngine.combinedBuffer(huge, rules).ppm - CapitalEngine.combinedBuffer(small, rules).ppm)
    }

    @Test fun distributionIsFreeOnlyOutsideTheBuffer() {
        val buffer = Rate.percent(4)
        assertEquals(Rate.ONE, CapitalEngine.distributionLimit(Rate.percent(12), SupervisoryRung.NORMAL, buffer, rules))
        assertEquals(Rate.ZERO, CapitalEngine.distributionLimit(Rate.percent(3), SupervisoryRung.RESTRICTED, buffer, rules))
        var last = Rate.ZERO
        for (x in listOf(rules.minCet1.ppm + 100_000L, rules.minCet1.ppm + 1_100_000L, rules.minCet1.ppm + 2_100_000L, rules.minCet1.ppm + 3_100_000L)) {
            val cet1 = Rate(x.coerceAtMost(rules.minCet1.ppm + buffer.ppm - 1))
            val limit = CapitalEngine.distributionLimit(cet1, SupervisoryRung.BUFFER, buffer, rules)
            assertTrue(limit >= last)
            last = limit
        }
    }

    @Test fun exposureCapacityShrinksWithCushion() {
        val s = BankingFx.bank().snapshot()
        val low = CapitalEngine.exposureCapacity(s, rules, Rate.percent(1))
        val high = CapitalEngine.exposureCapacity(s, rules, Rate.percent(5))
        assertTrue(low > high)
        assertEquals(Money.ZERO, CapitalEngine.exposureCapacity(s, rules, Rate.percent(90)))
    }

    @Test fun capitalNeededIsZeroWhenAlreadyAboveCushionAndGrowsWithCushion() {
        val s = BankingFx.bank().snapshot()
        assertEquals(Money.ZERO, CapitalEngine.capitalNeeded(s, rules, Rate.ZERO))
        val some = CapitalEngine.capitalNeeded(s, rules, Rate.percent(15))
        assertTrue(some.isPositive)
        assertTrue(CapitalEngine.capitalNeeded(s, rules, Rate.percent(20)) > some)
    }

    @Test fun ratioAfterReflectsEquityChange() {
        val s = BankingFx.bank().snapshot()
        val now = CapitalEngine.position(s, rules).cet1Ratio
        assertTrue(CapitalEngine.ratioAfter(s, rules, Money.units(100_000)) > now)
        assertTrue(CapitalEngine.ratioAfter(s, rules, -Money.units(100_000)) < now)
        assertEquals(now, CapitalEngine.ratioAfter(s, rules, Money.ZERO))
    }

    @Test fun impairedLoansWeighMoreThanPerformingOnes() {
        val b = BankingFx.bank()
        val before = position(b).rwa.credit
        val key = b.loans.keys.first { it.stage == LoanStage.PERFORMING }
        val moved = b.removeLoans(key, b.loanPool(key).principal.times(Rate.percent(50)))
        b.addLoans(key.copy(stage = LoanStage.IMPAIRED), moved, 50_000L)
        assertTrue(position(b).rwa.credit > before)
    }
}
