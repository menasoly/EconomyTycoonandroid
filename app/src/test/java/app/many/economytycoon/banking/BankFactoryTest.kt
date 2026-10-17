package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankFactoryTest {
    @Test fun openingBooksBalance() {
        val b = BankingFx.bank()
        assertEquals(Money.ZERO, b.snapshot().imbalance)
        BankingFx.assertBalanced(BankingFx.bank(2, capital = 37_000L))
    }

    @Test fun equityIsExactlyTheCapitalPaidIn() {
        val s = BankingFx.bank(capital = 2_500_000L).snapshot()
        assertEquals(Money.units(2_500_000L), s.bookEquity)
        assertEquals(Money.ZERO, s.retained)
    }

    @Test fun sizeFollowsTheEquityShare() {
        val s = BankingFx.bank(capital = 700_000L).snapshot()
        // 7% equity share: assets before the allowance are capital / 0.07.
        val gross = s.totalAssets + s.allowance
        assertEquals(Money.units(10_000_000L), gross)
    }

    @Test fun loansAreSpreadOverEverySectorWithAnAppetite() {
        val s = BankingFx.bank().snapshot()
        for (sector in LoanSector.values()) assertTrue(sector.name, s.loansOf(sector).isPositive)
        assertEquals(Money.ZERO, s.loansIn(LoanStage.PERFORMING) + s.loansIn(LoanStage.WATCH) + s.loansIn(LoanStage.IMPAIRED) - s.loansGross)
    }

    @Test fun stagesStartWithMostLoansPerforming() {
        val s = BankingFx.bank().snapshot()
        assertTrue(s.loansIn(LoanStage.PERFORMING) > s.loansIn(LoanStage.WATCH))
        assertTrue(s.loansIn(LoanStage.WATCH) > s.loansIn(LoanStage.IMPAIRED))
        assertTrue(s.loansIn(LoanStage.IMPAIRED).isPositive)
    }

    @Test fun allowanceCoversExpectedLossAtNormalConditions() {
        val s = BankingFx.bank().snapshot()
        val ecl = Provisioning.ecl(s, { Rate.ONE_PPM }, Rate.ONE_PPM).total
        assertEquals(ecl, s.allowance)
        assertTrue(s.allowance.isPositive)
    }

    @Test fun depositsFillTheBooksAndAreSplitByDepositor() {
        val s = BankingFx.bank().snapshot()
        assertTrue(s.depositsTotal.isPositive)
        assertTrue(s.depositsBy(Depositor.HOUSEHOLD) > s.depositsBy(Depositor.CORPORATE))
        assertEquals(s.depositsTotal, s.depositsBy(Depositor.HOUSEHOLD) + s.depositsBy(Depositor.CORPORATE))
    }

    @Test fun depositRatesAreSetFromTheMarket() {
        val s = BankingFx.bank().snapshot()
        val demand = s.deposits.getValue(DepositKey(Depositor.HOUSEHOLD, DepositProduct.DEMAND)).ratePpm
        val term = s.deposits.getValue(DepositKey(Depositor.HOUSEHOLD, DepositProduct.TERM)).ratePpm
        assertTrue(term > demand)
    }

    @Test fun aNewBankStartsWithFullConfidenceAndNoHistory() {
        val s = BankingFx.bank().snapshot()
        assertEquals(Rate.ONE_PPM, s.confidencePpm)
        assertEquals(RunStage.CALM, s.runStage)
        assertTrue(s.history.isEmpty())
        assertEquals(CreditRating.BBB, s.rating)
    }

    @Test fun aStrategyShapesThePolicy() {
        val careful = BankingFx.bank(1, strategy = BankStrategies.of(BankArchetype.CONSERVATIVE_RETAIL)).policy
        val bold = BankingFx.bank(2, strategy = BankStrategies.of(BankArchetype.AGGRESSIVE_GROWTH)).policy
        assertTrue(careful.minRating < bold.minRating)
        assertTrue(careful.dividendPayout > bold.dividendPayout)
        assertTrue(careful.capitalCushion > bold.capitalCushion)
    }

    @Test fun aPlayerBankGetsTheDefaultPolicy() {
        val b = BankingFx.bank(operator = BankOperator.PLAYER, strategy = null)
        assertEquals(BankPolicy(), b.policy)
    }

    @Test(expected = IllegalArgumentException::class) fun capitalMustBePositive() {
        OpeningSpec(Money.ZERO)
    }

    @Test(expected = IllegalArgumentException::class) fun assetsMustLeaveRoomForReserves() {
        OpeningSpec(Money.units(1), loanShare = Rate.percent(80), securitiesShare = Rate.percent(30))
    }

    @Test fun reservesAreWhatIsLeftOver() {
        val s = BankingFx.bank().snapshot()
        assertTrue(s.reserves.isPositive)
        assertTrue(s.reserves < s.loansGross)
    }
}
