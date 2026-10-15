package app.many.economytycoon.finance

import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanTest {
    private val borrower = LedgerOwner(OwnerKind.COMPANY, 1)

    private fun world(): FinanceService {
        val f = FinanceService(IdAllocators())
        f.mint(Tick(0), f.bank, Money.units(10_000_000), "bank")
        f.mint(Tick(0), borrower, Money.units(100_000), "company")
        return f
    }

    private fun offer(type: RepaymentType = RepaymentType.AMORTIZING, principal: Long = 120_000, term: Int = 360, rate: Long = 6) =
        LoanOffer(Money.units(principal), Rate.percent(rate), term, 30, type)

    @Test fun annuityPaymentRepaysExactlyOverTheTerm() {
        val principal = Money.units(120_000)
        val pay = LoanMath.payment(principal, Rate.percent(6), 30, 12, RepaymentType.AMORTIZING)
        // Interest over twelve 30-day periods on a declining balance.
        var balance = principal.raw
        val perPeriod = Rate.percent(6).ppm * 30
        val den = Rate.ONE_PPM * 360
        repeat(12) {
            val interest = Math.round(balance.toDouble() * perPeriod / den)
            balance = balance + interest - pay.raw
        }
        assertTrue("residual $balance", Math.abs(balance) < 2_000)
    }

    @Test fun zeroRateAmortizationSplitsPrincipalEvenly() {
        val pay = LoanMath.payment(Money.units(1_200), Rate.ZERO, 30, 12, RepaymentType.AMORTIZING)
        assertEquals(Money.units(100), pay)
    }

    @Test fun originationMovesCashAndRecordsDebtOnBothBooks() {
        val f = world()
        val r = f.loans.originate(Tick(1), borrower, f.bank, offer())
        assertTrue(r.isOk)
        val fee = Money.units(120_000).times(f.loans.policy.originationFee)
        assertEquals(Money.units(100_000) + Money.units(120_000) - fee, f.ledger.cash(borrower))
        assertEquals(Money.units(120_000), -f.ledger.balance(borrower, Chart.DEBT_SHORT))
        assertEquals(Money.units(120_000), f.ledger.balance(f.bank, Chart.LOANS_RECEIVABLE))
        assertTrue(f.invariants().isEmpty())
    }

    @Test fun lenderWithoutCashCannotLend() {
        val f = FinanceService(IdAllocators())
        val r = f.loans.originate(Tick(1), borrower, f.bank, offer())
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.rejectionOrNull()?.rule)
    }

    @Test fun securedLoanNeedsEnoughCollateral() {
        val f = world()
        val thin = offer().copy(collateral = listOf(Collateral("truck", Money.units(10_000))))
        assertEquals(Rules.CREDIT_LIMIT, f.loans.originate(Tick(1), borrower, f.bank, thin).rejectionOrNull()?.rule)
        val ok = offer().copy(collateral = listOf(Collateral("plant", Money.units(150_000))))
        assertTrue(f.loans.originate(Tick(1), borrower, f.bank, ok).isOk)
    }

    @Test fun interestAccruesWithoutLosingRemainders() {
        val f = world()
        val loan = f.loans.originate(Tick(0), borrower, f.bank, offer(principal = 1_000, rate = 7)).getOrThrow()
        var total = 0L
        repeat(360) { total += loan.accrue(f.loans.policy.penaltyRate).raw }
        // 1000 units at 7% for one year, to within one raw unit of carry.
        val expected = Money.units(1_000).times(Rate.percent(7)).raw
        assertTrue("accrued $total expected $expected", Math.abs(total - expected) <= 1)
    }

    @Test fun fullLifecycleRepaysTheLoanAndLeavesTheBooksConsistent() {
        val f = world()
        f.loans.originate(Tick(0), borrower, f.bank, offer(principal = 60_000, term = 180)).getOrThrow()
        f.mint(Tick(0), borrower, Money.units(100_000), "buffer")
        for (t in 1L..180L) f.settleTick(Tick(t), emptyList())
        val loan = f.loans.all().single()
        assertEquals(LoanStatus.PAID_OFF, loan.status)
        assertEquals(Money.ZERO, loan.outstanding)
        assertEquals(Money.ZERO, f.ledger.balance(borrower, Chart.DEBT_SHORT))
        assertEquals(Money.ZERO, f.ledger.balance(f.bank, Chart.LOANS_RECEIVABLE))
        assertTrue(f.ledger.balance(borrower, Chart.INTEREST_EXPENSE).isPositive)
        assertEquals(f.ledger.balance(borrower, Chart.INTEREST_EXPENSE), -f.ledger.balance(f.bank, Chart.INTEREST_INCOME))
        assertTrue(f.invariants().isEmpty())
    }

    @Test fun missedPaymentsEscalateToDefault() {
        val f = FinanceService(IdAllocators())
        f.mint(Tick(0), f.bank, Money.units(1_000_000), "bank")
        f.mint(Tick(0), borrower, Money.units(1_000), "tiny")
        val offer = LoanOffer(Money.units(50_000), Rate.percent(8), 360, 30, RepaymentType.AMORTIZING)
        val loan = f.loans.originate(Tick(0), borrower, f.bank, offer).getOrThrow()
        // Spend everything so payments cannot be made.
        val cash = f.ledger.cash(borrower)
        f.settlement.settle(Tick(0), listOf(SettlementIntent.simple("burn", borrower, LedgerOwner(OwnerKind.COMPANY, 9), cash, SettlementPriority.OTHER, ReasonCode.PURCHASE, Chart.OTHER_EXPENSE, Chart.REVENUE_SALES)))
        for (t in 1L..120L) f.settleTick(Tick(t), emptyList())
        assertEquals(LoanStatus.DEFAULTED, loan.status)
        assertTrue(loan.missedPayments >= f.loans.policy.maxMissedPayments)
        val loss = f.loans.writeOff(Tick(121), loan.id)
        assertTrue(loss.isOk)
        assertTrue(f.invariants().isEmpty())
    }

    @Test fun prepaymentReducesPrincipal() {
        val f = world()
        val loan = f.loans.originate(Tick(0), borrower, f.bank, offer()).getOrThrow()
        val paid = f.loans.prepay(Tick(5), loan.id, Money.units(20_000)).getOrThrow()
        assertEquals(Money.units(20_000), paid)
        assertEquals(Money.units(100_000), loan.outstanding)
        // Asking for more than is owed repays only what is owed and closes the loan.
        assertEquals(Money.units(100_000), f.loans.prepay(Tick(6), loan.id, Money.units(10_000_000)).getOrThrow())
        assertEquals(Money.ZERO, loan.outstanding)
    }

    @Test fun covenantBreachesAreDetected() {
        val f = world()
        val o = offer().copy(covenants = listOf(Covenant.MaxDebtToEquity(Rate.percent(200)), Covenant.MinInterestCoverage(Rate.percent(300))))
        val loan = f.loans.originate(Tick(0), borrower, f.bank, o).getOrThrow()
        val breaches = f.loans.breaches(loan, Rate.percent(50), Rate.percent(350), Rate.percent(500))
        assertEquals(listOf("max_debt_to_equity"), breaches.map { it.name })
    }

    @Test fun totalInterestGrowsWithRateAndTerm() {
        val a = LoanMath.totalInterest(Money.units(100_000), Rate.percent(5), 30, 12, RepaymentType.AMORTIZING)
        val b = LoanMath.totalInterest(Money.units(100_000), Rate.percent(9), 30, 12, RepaymentType.AMORTIZING)
        val c = LoanMath.totalInterest(Money.units(100_000), Rate.percent(5), 30, 24, RepaymentType.AMORTIZING)
        assertTrue(b > a)
        assertTrue(c > a)
    }
}
