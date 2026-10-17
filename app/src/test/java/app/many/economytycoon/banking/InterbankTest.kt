package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterbankTest {
    private val params = BankingFx.params
    private val market = InterbankMarket(params)
    private val policy = Rate.percent(4)

    private fun enrich(b: Bank, amount: Money) { b.reserves += amount; b.retained += amount }

    /** Leaves a bank with only [share] of the reserves it wants to hold. */
    private fun drain(b: Bank, share: Rate) {
        val keep = market.desiredReserves(b).times(Rate.percent(50)).times(Rate.ONE - share)
        val x = b.reserves - keep
        b.reserves -= x; b.retained -= x
    }

    @Test fun marketOfOneIsIdle() {
        val r = market.clear(BankingFx.banks(1), policy)
        assertEquals(Money.ZERO, r.volume)
        assertFalse(r.frozen)
        assertEquals(policy, r.rate)
    }

    @Test fun shortBankBorrowsFromBankWithSurplus() {
        val banks = BankingFx.banks(2)
        // Shift cash from bank 2 to bank 1 so one is short and the other long, without changing the system.
        enrich(banks[0], Money.units(500_000))
        drain(banks[1], Rate.percent(0))
        val r = market.clear(banks, policy)
        assertTrue(r.volume.isPositive)
        assertTrue(banks[1].interbankBorrowed.isPositive)
        assertEquals(banks[0].interbankLent, banks[1].interbankBorrowed)
        BankingFx.assertBalanced(banks)
        BankingFx.assertInterbankMatches(banks)
    }

    @Test fun rateStaysInsideTheCorridor() {
        val banks = BankingFx.banks(3)
        drain(banks[2], Rate.percent(0))
        val r = market.clear(banks, policy)
        val c = CentralBankFacilities.corridor(policy, params.facilities)
        assertTrue(r.rate >= c.floor && r.rate <= c.ceiling)
    }

    @Test fun scarcerFundsCostMore() {
        val loose = BankingFx.banks(3); drain(loose[2], Rate.percent(100))
        val tight = BankingFx.banks(3); for (b in tight) drain(b, Rate.percent(100))
        assertTrue(market.clear(tight, policy).rate >= market.clear(loose, policy).rate)
    }

    @Test fun nobodyLendingFreezesTheMarket() {
        val banks = BankingFx.banks(2)
        drain(banks[0], Rate.percent(0))
        banks[1].applyConfidence(100_000L, RunStage.PANIC)
        val r = market.clear(banks, policy)
        assertTrue(r.frozen)
        assertEquals(CentralBankFacilities.corridor(policy, params.facilities).ceiling, r.rate)
        assertTrue(r.unmet.containsKey(banks[0].id))
    }

    @Test fun weakBorrowersAreShutOut() {
        val banks = BankingFx.banks(2)
        drain(banks[0], Rate.percent(0))
        banks[0].setRating(CreditRating.CCC, 100, 0, 0)
        val r = market.clear(banks, policy)
        assertEquals(Money.ZERO, banks[0].interbankBorrowed)
        assertTrue(r.unmet.containsKey(banks[0].id))
    }

    @Test fun borrowerInSevereBreachCannotBorrow() {
        val s = BankingFx.bank().snapshot().copy(rung = SupervisoryRung.SEVERE)
        assertFalse(market.canBorrow(s, s.cet1Capital))
        assertFalse(market.canLend(s))
    }

    @Test fun positionsAreRepaidOnceTheBorrowerHasCash() {
        val banks = BankingFx.banks(2)
        enrich(banks[1], Money.units(500_000))
        drain(banks[0], Rate.percent(0))
        market.clear(banks, policy)
        assertTrue(banks[0].interbankBorrowed.isPositive)
        banks[0].reserves += banks[0].interbankBorrowed.times(Rate.percent(300)); banks[0].retained += banks[0].interbankBorrowed.times(Rate.percent(300))
        val r = market.clear(banks, policy)
        assertTrue(r.repaid.isPositive)
        BankingFx.assertInterbankMatches(banks)
        BankingFx.assertBalanced(banks)
    }

    @Test fun interestMovesFromBorrowerToLenders() {
        val banks = BankingFx.banks(3)
        enrich(banks[1], Money.units(500_000)); enrich(banks[2], Money.units(500_000))
        drain(banks[0], Rate.percent(0))
        market.clear(banks, policy)
        val retained = banks.map { it.retained }
        market.clear(banks, policy)
        assertTrue(banks[0].retained < retained[0])
        assertTrue(banks[0].periodInterbankCost.isPositive)
        assertEquals(banks.fold(Money.ZERO) { a, b -> a + b.periodInterbankCost }, banks.fold(Money.ZERO) { a, b -> a + b.periodInterbankIncome })
        BankingFx.assertBalanced(banks)
    }

    @Test fun lendingIsLimitedByCapital() {
        val banks = BankingFx.banks(2, capital = 100_000L)
        val huge = banks[0].reserves + Money.units(50_000_000)
        banks[0].reserves += huge; banks[0].retained += huge
        drain(banks[1], Rate.percent(0))
        market.clear(banks, policy)
        val cap = banks[0].snapshot().cet1Capital.times(params.facilities.interbankLimitToCapital)
        assertTrue(banks[0].interbankLent <= cap)
    }

    @Test fun stoppedBanksAreIgnored() {
        val banks = BankingFx.banks(3)
        drain(banks[0], Rate.percent(0))
        banks[2].changeStatus(BankStatus.LIQUIDATED, Tick(1))
        market.clear(banks, policy)
        assertEquals(Money.ZERO, banks[2].interbankLent)
    }

    @Test fun clearingTwiceKeepsConsistency() {
        val banks = BankingFx.banks(4)
        drain(banks[0], Rate.percent(0)); drain(banks[1], Rate.percent(50))
        repeat(10) { market.clear(banks, policy); BankingFx.assertInterbankMatches(banks); BankingFx.assertBalanced(banks) }
    }

    @Test fun scarcityIsHalfWhenNothingIsHappening() {
        val r = market.clear(BankingFx.banks(2), policy)
        assertTrue(r.scarcityPpm in 0L..Rate.ONE_PPM)
    }
}
