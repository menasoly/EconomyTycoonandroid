package app.many.economytycoon.banking

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankStrategyPolicyTest {
    private fun hash(f: (CanonicalHasher) -> Unit) = CanonicalHasher().also(f).finish()

    @Test fun everyArchetypeHasAStrategyWithAllSectors() {
        for (a in BankArchetype.values()) {
            val s = BankStrategies.of(a)
            assertEquals(a, s.archetype)
            assertEquals(LoanSector.values().size, s.sectorBias.size)
            assertTrue(s.thinkMonths >= 1)
            assertTrue(a.label.isNotBlank())
        }
    }

    @Test fun archetypesDifferInTemperament() {
        val safe = BankStrategies.of(BankArchetype.CONSERVATIVE_RETAIL)
        val bold = BankStrategies.of(BankArchetype.AGGRESSIVE_GROWTH)
        assertTrue(bold.growthAppetite > safe.growthAppetite)
        assertTrue(bold.riskAppetite > safe.riskAppetite)
        assertTrue(safe.capitalCushion > bold.capitalCushion)
        assertTrue(safe.liquidityCushion > bold.liquidityCushion)
    }

    @Test fun wholesaleTradersLeanOnMarketFunding() {
        val t = BankStrategies.of(BankArchetype.WHOLESALE_TRADER)
        val r = BankStrategies.of(BankArchetype.CONSERVATIVE_RETAIL)
        assertTrue(t.wholesaleReliance > r.wholesaleReliance)
    }

    @Test fun propertyLendersFavourProperty() {
        val s = BankStrategies.of(BankArchetype.PROPERTY_LENDER)
        assertTrue(s.biasOf(LoanSector.COMMERCIAL_PROPERTY) > s.biasOf(LoanSector.CONSUMER))
    }

    @Test fun rolledStrategiesStayInRangeAndAreDeterministic() {
        repeat(30) {
            val a = BankStrategies.roll(DeterministicRandom(it.toLong()))
            val b = BankStrategies.roll(DeterministicRandom(it.toLong()))
            assertEquals(a, b)
            for (r in listOf(a.growthAppetite, a.riskAppetite, a.depositAggression, a.payout)) assertTrue(r.ppm in 10_000L..990_000L)
            assertTrue(a.sectorBias.values.all { it.ppm in 10_000L..990_000L })
        }
    }

    @Test fun rollingProducesVariety() {
        val seen = (0L..40L).map { BankStrategies.roll(DeterministicRandom(it)).archetype }.toSet()
        assertTrue(seen.size >= 3)
        val a = BankStrategies.roll(DeterministicRandom(1L)); val b = BankStrategies.roll(DeterministicRandom(2L))
        assertFalse(a == b)
    }

    @Test(expected = IllegalArgumentException::class) fun strategyNeedsAPositiveThinkInterval() {
        BankStrategies.of(BankArchetype.BALANCED).copy(thinkMonths = 0)
    }

    @Test(expected = IllegalArgumentException::class) fun strategyNeedsABiasForEverySector() {
        BankStrategies.of(BankArchetype.BALANCED).copy(sectorBias = emptyMap())
    }

    @Test fun strategyHashFollowsItsFields() {
        val s = BankStrategies.of(BankArchetype.BALANCED)
        assertEquals(hash { s.hashInto(it) }, hash { s.copy().hashInto(it) })
        assertFalse(hash { s.hashInto(it) } == hash { s.copy(payout = Rate.percent(99)).hashInto(it) })
    }

    @Test fun defaultPolicyCoversEveryProductAndSector() {
        val p = BankPolicy()
        for (d in DepositProduct.values()) assertEquals(0L, p.premiumOf(d))
        for (s in LoanSector.values()) assertTrue(p.appetiteOf(s).ppm > 0L)
    }

    @Test fun policyFollowsStrategyTemperament() {
        val bold = BankPolicy.from(BankStrategies.of(BankArchetype.AGGRESSIVE_GROWTH))
        val safe = BankPolicy.from(BankStrategies.of(BankArchetype.CONSERVATIVE_RETAIL))
        assertTrue(bold.minRating.ordinal >= safe.minRating.ordinal)
        assertTrue(bold.growthAppetite > safe.growthAppetite)
        assertTrue(bold.depositPremiumBps.getValue(DepositProduct.SAVINGS) > safe.depositPremiumBps.getValue(DepositProduct.SAVINGS))
    }

    @Test fun withersChangeOnlyWhatTheySay() {
        val p = BankPolicy()
        val q = p.withPremium(DepositProduct.TERM, 40).withAppetite(LoanSector.MORTGAGE, Rate.percent(5))
        assertEquals(40L, q.premiumOf(DepositProduct.TERM))
        assertEquals(0L, q.premiumOf(DepositProduct.DEMAND))
        assertEquals(Rate.percent(5), q.appetiteOf(LoanSector.MORTGAGE))
        assertEquals(p.appetiteOf(LoanSector.CORPORATE), q.appetiteOf(LoanSector.CORPORATE))
    }

    @Test(expected = IllegalArgumentException::class) fun policyCannotLendToDefaultedBorrowers() {
        BankPolicy(minRating = CreditRating.D)
    }

    @Test fun policyHashFollowsItsFields() {
        val p = BankPolicy()
        assertEquals(hash { p.hashInto(it) }, hash { p.copy().hashInto(it) })
        assertFalse(hash { p.hashInto(it) } == hash { p.withPremium(DepositProduct.DEMAND, 1).hashInto(it) })
        assertFalse(hash { p.hashInto(it) } == hash { p.copy(loanMarginBps = 3).hashInto(it) })
    }
}
