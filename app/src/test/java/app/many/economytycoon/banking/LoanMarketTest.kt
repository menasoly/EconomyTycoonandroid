package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanMarketTest {
    private val params = BankingFx.params
    private val market = LoanMarket(params)
    private val inputs = BankingFx.calm

    private fun approved(amount: Long, ratePpm: Long): LoanDecision.Approved {
        val quote = LoanQuote(LoanSector.CORPORATE, CreditRating.A, 60, Rate(ratePpm), Rate.ZERO, Rate.ZERO, Rate.ZERO, Rate.ZERO)
        return LoanDecision.Approved(Money.units(amount), quote, Rate.ZERO, Rate.percent(20), null)
    }

    @Test fun cheaperQuotesPullHarder() {
        val avg = 60_000L
        assertTrue(market.pull(50_000L, avg) > market.pull(60_000L, avg))
        assertTrue(market.pull(60_000L, avg) > market.pull(70_000L, avg))
        assertEquals(params.lending.neutralWeight, market.pull(avg, avg))
    }

    @Test fun pullIsBounded() {
        assertEquals(params.lending.minWeight, market.pull(10_000_000L, 0L))
        assertEquals(params.lending.neutralWeight * 5L, market.pull(0L, 10_000_000L))
    }

    @Test fun allocationNeverExceedsApprovalsOrBlock() {
        val parts = market.allocate(Money.units(100), listOf(BankId(1) to approved(30, 60_000), BankId(2) to approved(500, 60_000)))
        assertEquals(Money.units(30), parts.getValue(BankId(1)))
        assertEquals(Money.units(70), parts.getValue(BankId(2)))
    }

    @Test fun cheaperBanksWinMoreOfTheDemand() {
        val parts = market.allocate(Money.units(100), listOf(BankId(1) to approved(1_000, 50_000), BankId(2) to approved(1_000, 70_000)))
        assertTrue(parts.getValue(BankId(1)) > parts.getValue(BankId(2)))
        assertEquals(Money.units(100), Money.sum(parts.values))
    }

    @Test fun nothingIsAllocatedWithoutBuyersOrDemand() {
        assertTrue(market.allocate(Money.units(100), emptyList()).isEmpty())
        assertTrue(market.allocate(Money.ZERO, listOf(BankId(1) to approved(10, 1))).isEmpty())
    }

    @Test fun demandBeyondWhatBanksOfferStaysUnserved() {
        val parts = market.allocate(Money.units(1_000), listOf(BankId(1) to approved(10, 60_000), BankId(2) to approved(20, 60_000)))
        assertEquals(Money.units(30), Money.sum(parts.values))
    }

    @Test fun borrowersBankWhereTheirKindDoes() {
        assertEquals(Depositor.HOUSEHOLD, market.borrowerBook(LoanSector.MORTGAGE).depositor)
        assertEquals(Depositor.HOUSEHOLD, market.borrowerBook(LoanSector.CONSUMER).depositor)
        assertEquals(Depositor.CORPORATE, market.borrowerBook(LoanSector.CORPORATE).depositor)
        assertEquals(Depositor.CORPORATE, market.borrowerBook(LoanSector.TRADE_FINANCE).depositor)
        assertTrue(LoanSector.values().all { market.borrowerBook(it).product == DepositProduct.DEMAND })
    }

    @Test fun bookingAGrowsTheLoanBookAndCreatesDepositsMinusLeakage() {
        val b = BankingFx.bank()
        val loans = b.snapshot().loansGross; val deposits = b.snapshot().depositsTotal
        val o = market.book(b, LoanSector.CORPORATE, CreditRating.A, Money.units(100_000), 70_000L)
        assertEquals(loans + o.amount, b.snapshot().loansGross)
        val leak = o.amount.times(params.lending.leakage)
        assertEquals(deposits + o.amount - leak, b.snapshot().depositsTotal)
        assertEquals(o.amount.times(params.lending.originationFee), o.fee)
        BankingFx.assertBalanced(b)
    }

    @Test fun newLoansLandPerformingAtTheQuotedRate() {
        val b = BankingFx.bank()
        val key = PoolKey(LoanSector.CONSUMER, CreditRating.BBB, LoanStage.PERFORMING)
        val before = b.loanPool(key)
        market.book(b, LoanSector.CONSUMER, CreditRating.BBB, Money.units(50_000), 90_000L)
        val after = b.loanPool(key)
        assertEquals(before.principal + Money.units(50_000), after.principal)
        assertTrue(after.ratePpm in minOf(before.ratePpm, 90_000L)..maxOf(before.ratePpm, 90_000L))
    }

    @Test fun monthlyRunCreatesLoansAndKeepsEveryBankBalanced() {
        val banks = BankingFx.banks(3)
        val before = Money.sum(banks.map { it.snapshot().loansGross })
        val done = market.run(banks, inputs)
        assertTrue(done.isNotEmpty())
        assertTrue(Money.sum(banks.map { it.snapshot().loansGross }) > before)
        assertEquals(done.fold(Money.ZERO) { a, o -> a + o.amount }, Money.sum(banks.map { it.snapshot().loansGross }) - before)
        BankingFx.assertBalanced(banks)
    }

    @Test fun lendingNeverExceedsDemand() {
        val banks = BankingFx.banks(3)
        val done = market.run(banks, inputs)
        for (sector in LoanSector.values()) {
            val lent = Money.sum(done.filter { it.sector == sector }.map { it.amount })
            assertTrue(lent <= inputs.loanDemand(BankingFx.country, sector))
        }
    }

    @Test fun noDemandNoLoans() {
        val banks = BankingFx.banks(2)
        val none = inputs.copy(demand = LoanSector.values().associateWith { Money.ZERO })
        assertTrue(market.run(banks, none).isEmpty())
    }

    @Test fun frozenBanksDoNotLend() {
        val banks = BankingFx.banks(2)
        banks[0].setRung(SupervisoryRung.RESTRICTED, app.many.economytycoon.core.Tick(1))
        val done = market.run(banks, inputs)
        assertTrue(done.none { it.bank == banks[0].id })
        assertTrue(done.any { it.bank == banks[1].id })
    }

    @Test fun recessionShrinksNewLending() {
        val calm = market.run(BankingFx.banks(3), inputs).fold(Money.ZERO) { a, o -> a + o.amount }
        val bad = market.run(BankingFx.banks(3), inputs.recession(800_000L)).fold(Money.ZERO) { a, o -> a + o.amount }
        assertTrue(bad < calm)
    }

    @Test fun runIsDeterministic() {
        val a = market.run(BankingFx.banks(3), inputs)
        val b = market.run(BankingFx.banks(3), inputs)
        assertEquals(a, b)
    }

    @Test fun feesAreIncomeToTheBank() {
        val b = BankingFx.bank()
        val retained = b.retained
        val o = market.book(b, LoanSector.CORPORATE, CreditRating.A, Money.units(1_000_000), 70_000L)
        assertEquals(retained + o.fee, b.retained)
    }
}
