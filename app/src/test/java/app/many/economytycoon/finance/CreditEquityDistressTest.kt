package app.many.economytycoon.finance

import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditEquityDistressTest {
    private fun ratios(coverage: Long, current: Long, de: Long, margin: Long, equity: Long) = Ratios(
        currentRatio = Rate.percent(current), cashRatio = Rate.percent(30), debtToEquity = Rate.percent(de),
        interestCoverage = Rate.percent(coverage), grossMargin = Rate.percent(40), netMargin = Rate.percent(margin),
        returnOnEquity = Rate.percent(10), returnOnAssets = Rate.percent(5), debtToEbitda = Rate.percent(200), equityRatio = Rate.percent(equity),
    )

    private fun inputs(r: Ratios, missed: Int = 0, age: Long = 360L * 3) =
        CreditInputs(r, age, missed, Money.ZERO, Money.units(1_000_000), Money.units(500_000))

    @Test fun strongBorrowersScoreHigherThanWeakOnes() {
        val strong = CreditScoring.score(inputs(ratios(800, 250, 30, 20, 60)))
        val weak = CreditScoring.score(inputs(ratios(80, 70, 400, -10, 5)))
        assertTrue("$strong vs $weak", strong > weak + 200)
        assertTrue(CreditScoring.target(strong) < CreditScoring.target(weak))
    }

    @Test fun missedPaymentsLowerTheScore() {
        val r = ratios(400, 150, 80, 8, 40)
        assertTrue(CreditScoring.score(inputs(r, missed = 2)) < CreditScoring.score(inputs(r, missed = 0)))
    }

    @Test fun ratingMovesOneNotchAtATimeAfterConfirmation() {
        val t = RatingTracker(CreditRating.BBB, confirmations = 2)
        val strong = inputs(ratios(1_200, 300, 20, 25, 70))
        // Upgrades need three consecutive confirmations and move a single notch.
        assertEquals(CreditRating.BBB, t.review(strong))
        assertEquals(CreditRating.BBB, t.review(strong))
        assertEquals(CreditRating.A, t.review(strong))
        assertEquals(CreditRating.A, t.review(strong))
        assertEquals(CreditRating.A, t.review(strong))
        assertEquals(CreditRating.AA, t.review(strong))
    }

    @Test fun ratingDoesNotFlapAroundABoundary() {
        val t = RatingTracker(CreditRating.BBB, confirmations = 2)
        val up = inputs(ratios(1_200, 300, 20, 25, 70))
        val mid = inputs(ratios(400, 150, 50, 10, 50))
        val seen = ArrayList<CreditRating>()
        repeat(6) { seen.add(t.review(if (it % 2 == 0) up else mid)) }
        assertTrue(seen.all { it == CreditRating.BBB })
    }

    @Test fun repeatedMissesForceDefaultRating() {
        val t = RatingTracker(CreditRating.A)
        assertEquals(CreditRating.D, t.review(inputs(ratios(300, 130, 100, 6, 40), missed = 3)))
        assertEquals(0, CreditRating.D.limitMultiple.toInt())
    }

    @Test fun creditLimitShrinksWithExistingDebt() {
        val free = CreditPolicy.terms(CreditRating.A, Rate.percent(4), Money.units(1_000_000), Money.ZERO)
        val loaded = CreditPolicy.terms(CreditRating.A, Rate.percent(4), Money.units(1_000_000), Money.units(3_000_000))
        assertEquals(Money.units(5_000_000), free.limit)
        assertEquals(Money.units(2_000_000), loaded.limit)
        assertEquals(Rate.percent(4) + CreditRating.A.spread, free.rate)
    }

    // ----- equity ---------------------------------------------------------------------------

    private val issuer = LedgerOwner(OwnerKind.COMPANY, 1)
    private val investor = LedgerOwner(OwnerKind.COMPANY, 7)
    private val gov = LedgerOwner(OwnerKind.GOVERNMENT, 1)

    @Test fun foundingAndIssuanceTrackOwnershipAndCash() {
        val f = FinanceService(IdAllocators())
        f.registerCompany(issuer, gov, Holder.player(1), Money.units(100_000), 1_000, Tick(0)).getOrThrow()
        f.mint(Tick(0), investor, Money.units(500_000), "inv")
        val raised = f.equity.issue(issuer, "common", Holder.owner(investor), investor, 500, Money.units(200), Tick(1)).getOrThrow()
        assertEquals(Money.units(100_000), raised)
        val table = f.equity.table(issuer)!!
        assertEquals(1_500L, table.totalOutstanding())
        assertEquals(Rate.ratio(500, 1_500), table.ownershipShare(Holder.owner(investor)))
        assertEquals(Money.units(200_000), f.ledger.cash(issuer))
        assertNull(table.controller(Rate.percent(70)))
        assertEquals(Holder.player(1), table.controller(Rate.percent(50)))
    }

    @Test fun issuanceFailsWhenBuyerCannotPay() {
        val f = FinanceService(IdAllocators())
        f.registerCompany(issuer, gov, Holder.player(1), Money.units(100), 10, Tick(0)).getOrThrow()
        val r = f.equity.issue(issuer, "common", Holder.owner(investor), investor, 10, Money.units(1_000), Tick(1))
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.rejectionOrNull()?.rule)
    }

    @Test fun dividendsAreBoundedByDistributableProfit() {
        val f = FinanceService(IdAllocators())
        f.registerCompany(issuer, gov, Holder.player(1), Money.units(100_000), 1_000, Tick(0)).getOrThrow()
        val customer = LedgerOwner.households()
        f.mint(Tick(0), customer, Money.units(1_000_000), "c")
        f.recordSale("s", Tick(5), issuer, customer, Money.units(60_000), Money.ZERO)
        val cap = f.equity.maxDividend(issuer, f.statements, Tick(10), Money.units(10_000))
        assertEquals(Money.units(60_000), cap)
        val tooMuch = f.equity.payDividend(issuer, Money.units(70_000), Tick(11), f.statements, Money.units(10_000)) { null }
        assertEquals(Rules.LIMIT_EXCEEDED, tooMuch.rejectionOrNull()?.rule)
        val ok = f.equity.payDividend(issuer, Money.units(40_000), Tick(11), f.statements, Money.units(10_000)) { null }
        assertTrue(ok.isOk)
        assertEquals(Money.units(120_000), f.ledger.cash(issuer))
        assertTrue(f.invariants().isEmpty())
    }

    @Test fun dividendSplitsExactlyAcrossHolders() {
        val f = FinanceService(IdAllocators())
        f.registerCompany(issuer, gov, Holder.player(1), Money.units(100_000), 3, Tick(0)).getOrThrow()
        val customer = LedgerOwner.households()
        f.mint(Tick(0), customer, Money.units(1_000_000), "c")
        f.mint(Tick(0), investor, Money.units(1_000), "inv")
        f.equity.issue(issuer, "common", Holder.owner(investor), investor, 4, Money.units(10), Tick(1)).getOrThrow()
        f.recordSale("s", Tick(2), issuer, customer, Money.units(10_000), Money.ZERO)
        val before = f.ledger.moneySupply()
        f.equity.payDividend(issuer, Money(100_001), Tick(3), f.statements, Money.ZERO) { h -> if (h.kind == "owner") investor else null }.getOrThrow()
        assertEquals(before, f.ledger.moneySupply())
        assertEquals(0L, f.ledger.trialBalance())
    }

    @Test fun buybackReducesOutstandingShares() {
        val f = FinanceService(IdAllocators())
        f.registerCompany(issuer, gov, Holder.player(1), Money.units(100_000), 1_000, Tick(0)).getOrThrow()
        f.mint(Tick(0), investor, Money.units(10), "i")
        f.equity.transfer(issuer, "common", Holder.player(1), Holder.owner(investor), 200).getOrThrow()
        val cost = f.equity.buyBack(issuer, "common", Holder.owner(investor), investor, 100, Money.units(50), Tick(2)).getOrThrow()
        assertEquals(Money.units(5_000), cost)
        assertEquals(900L, f.equity.table(issuer)!!.totalOutstanding())
    }

    @Test fun dcfValuesGrowthAndDeductsDebt() {
        val low = Valuation.discountedCashFlow(Money.units(1_000_000), Rate.percent(3), Rate.percent(10), Rate.percent(2), 5, Money.ZERO)
        val high = Valuation.discountedCashFlow(Money.units(1_000_000), Rate.percent(8), Rate.percent(10), Rate.percent(2), 5, Money.ZERO)
        val indebted = Valuation.discountedCashFlow(Money.units(1_000_000), Rate.percent(3), Rate.percent(10), Rate.percent(2), 5, Money.units(2_000_000))
        assertTrue(high > low)
        assertEquals(low - Money.units(2_000_000), indebted)
    }

    // ----- distress ---------------------------------------------------------------------------

    private fun healthyBalance() = BalanceSheet(
        issuer, Tick(1), Money.units(100_000), Money.ZERO, Money.ZERO, Money.ZERO, Money.units(500_000), Money.ZERO, Money.ZERO,
        Money.ZERO, Money.ZERO, Money.units(20_000), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.units(50_000), Money.units(100_000),
        Money.ZERO, Money.units(500_000), Money.ZERO, Money.ZERO, Money.ZERO, Money.units(30_000),
    )

    @Test fun healthyCompaniesStayHealthy() {
        val m = DistressMonitor()
        repeat(100) { m.evaluate(Tick(it.toLong()), ratios(900, 250, 30, 15, 60), healthyBalance(), Money.ZERO, 0) }
        assertEquals(DistressState.HEALTHY, m.state)
    }

    @Test fun sustainedStressEscalatesAfterTheGracePeriod() {
        val m = DistressMonitor(DistressThresholds(distressGraceTicks = 10, recoveryTicks = 5))
        val bad = ratios(50, 40, 500, -20, 5).copy(cashRatio = Rate.percent(1))
        repeat(5) { m.evaluate(Tick(it.toLong()), bad, healthyBalance(), Money.ZERO, 0) }
        assertEquals(DistressState.WATCH, m.state)
        repeat(10) { m.evaluate(Tick(10L + it), bad, healthyBalance(), Money.ZERO, 0) }
        assertEquals(DistressState.DISTRESSED, m.state)
    }

    @Test fun recoveryStepsDownOneLevelAtATime() {
        val m = DistressMonitor(DistressThresholds(distressGraceTicks = 2, recoveryTicks = 3))
        val bad = ratios(50, 40, 500, -20, 5).copy(cashRatio = Rate.percent(1))
        repeat(5) { m.evaluate(Tick(it.toLong()), bad, healthyBalance(), Money.ZERO, 0) }
        assertEquals(DistressState.DISTRESSED, m.state)
        val good = ratios(900, 250, 30, 15, 60)
        repeat(3) { m.evaluate(Tick(10L + it), good, healthyBalance(), Money.ZERO, 0) }
        assertEquals(DistressState.WATCH, m.state)
        repeat(3) { m.evaluate(Tick(20L + it), good, healthyBalance(), Money.ZERO, 0) }
        assertEquals(DistressState.HEALTHY, m.state)
    }

    @Test fun waterfallPaysClassesInOrderAndProRatesTheShortfall() {
        val bank = LedgerOwner.bank()
        val govt = gov
        val supplierA = LedgerOwner(OwnerKind.COMPANY, 20)
        val supplierB = LedgerOwner(OwnerKind.COMPANY, 21)
        val claims = listOf(
            Claim(bank, ClaimClass.SECURED, Money.units(600), "loan"),
            Claim(govt, ClaimClass.STATUTORY, Money.units(100), "tax"),
            Claim(supplierA, ClaimClass.UNSECURED, Money.units(300), "a"),
            Claim(supplierB, ClaimClass.UNSECURED, Money.units(100), "b"),
        )
        val d = LiquidationWaterfall.distribute(Money.units(850), claims)
        fun paid(o: LedgerOwner) = d.first { it.claimant == o }.paid
        assertEquals(Money.units(600), paid(bank))
        assertEquals(Money.units(100), paid(govt))
        assertEquals(Money.units(150), paid(supplierA) + paid(supplierB))
        assertEquals(Money.units(112) + Money(5000), paid(supplierA))
        assertEquals(Money.units(850), Money.sum(d.map { it.paid }))
    }

    @Test fun waterfallNeverPaysMoreThanProceedsOrClaims() {
        val claims = (1..5).map { Claim(LedgerOwner(OwnerKind.COMPANY, it.toLong()), ClaimClass.UNSECURED, Money.units(100L * it), "c$it") }
        for (proceeds in listOf(0L, 1L, 333L, 1_499L, 1_500L, 9_999L)) {
            val d = LiquidationWaterfall.distribute(Money.units(proceeds), claims)
            val total = Money.sum(d.map { it.paid })
            assertTrue(total <= Money.units(proceeds))
            assertTrue(d.all { it.paid <= it.claim })
            assertEquals(Money.units(minOf(proceeds, 1_500L)), total)
        }
    }

    @Test fun equityGivesCompaniesWithoutEarningsSomeCredit() {
        val none = CreditPolicy.terms(CreditRating.BBB, Rate.percent(4), Money.ZERO, Money.ZERO)
        assertEquals(Money.ZERO, none.limit)
        val backed = CreditPolicy.terms(CreditRating.BBB, Rate.percent(4), Money.ZERO, Money.ZERO, Money.units(500_000))
        assertEquals(Money.units(200_000), backed.limit)
        val earning = CreditPolicy.terms(CreditRating.BBB, Rate.percent(4), Money.units(1_000_000), Money.ZERO, Money.units(500_000))
        assertEquals(Money.units(4_000_000), earning.limit)
        val indebted = CreditPolicy.terms(CreditRating.BBB, Rate.percent(4), Money.ZERO, Money.units(250_000), Money.units(500_000))
        assertEquals(Money.ZERO, indebted.limit)
        val negative = CreditPolicy.terms(CreditRating.BBB, Rate.percent(4), Money.ZERO, Money.ZERO, Money.units(-10_000))
        assertEquals(Money.ZERO, negative.limit)
    }
}
