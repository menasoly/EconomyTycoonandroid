package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderwritingTest {
    private val params = BankingFx.params
    private val inputs = BankingFx.calm

    private fun ctx(b: Bank, i: BankingInputs = inputs) = LendingContext(i, params, b.snapshot())
    private fun app(sector: LoanSector = LoanSector.CORPORATE, rating: CreditRating = CreditRating.A, units: Long = 50_000L) = LoanApplication(sector, rating, Money.units(units))
    private fun decide(b: Bank, a: LoanApplication = app(), i: BankingInputs = inputs) = Underwriter.evaluate(ctx(b, i), a, Money.ZERO, Money.ZERO)

    @Test(expected = IllegalArgumentException::class) fun emptyApplicationIsRefused() { app(units = 0L) }

    @Test fun expectedLossGrowsWithRatingRiskAndCollateralFalls() {
        val ok = Underwriter.expectedLossRate(LoanSector.CORPORATE, CreditRating.BBB, Rate.ONE_PPM, Rate.ONE_PPM)
        assertTrue(Underwriter.expectedLossRate(LoanSector.CORPORATE, CreditRating.B, Rate.ONE_PPM, Rate.ONE_PPM) > ok)
        assertTrue(Underwriter.expectedLossRate(LoanSector.CORPORATE, CreditRating.BBB, 2_000_000L, Rate.ONE_PPM) > ok)
        assertTrue(Underwriter.expectedLossRate(LoanSector.CORPORATE, CreditRating.BBB, Rate.ONE_PPM, 600_000L) >= ok)
    }

    @Test fun expectedLossOfEmptySectorIsZero() {
        val s = BankingFx.bank().snapshot().copy(loans = java.util.TreeMap())
        assertEquals(Rate.ZERO, Underwriter.expectedLossRateOfBook(s, LoanSector.CORPORATE, Rate.ONE_PPM, Rate.ONE_PPM))
    }

    @Test fun expectedLossOfABookIsBetweenItsBestAndWorstClass() {
        val s = BankingFx.bank().snapshot()
        val el = Underwriter.expectedLossRateOfBook(s, LoanSector.CORPORATE, Rate.ONE_PPM, Rate.ONE_PPM)
        val best = Underwriter.expectedLossRate(LoanSector.CORPORATE, CreditRating.AAA, Rate.ONE_PPM, Rate.ONE_PPM)
        val worst = Underwriter.expectedLossRate(LoanSector.CORPORATE, CreditRating.CCC, Rate.ONE_PPM, Rate.ONE_PPM)
        assertTrue(el >= best && el <= worst)
    }

    @Test fun goodBorrowerInACalmBankIsApproved() {
        val d = decide(BankingFx.bank())
        assertTrue(d is LoanDecision.Approved)
        d as LoanDecision.Approved
        assertTrue(d.amount.isPositive)
        assertTrue(d.amount <= Money.units(50_000))
        assertTrue(d.raroc >= params.lending.hurdleReturn)
    }

    @Test fun approvedAmountNeverExceedsRequest() {
        val b = BankingFx.bank()
        for (units in listOf(200L, 5_000L, 50_000L, 5_000_000L, 500_000_000L)) {
            val d = decide(b, app(units = units))
            if (d is LoanDecision.Approved) assertTrue(d.amount <= Money.units(units))
        }
    }

    @Test fun belowMinimumRatingIsDeclined() {
        val b = BankingFx.bank()
        b.changePolicy(b.policy.copy(minRating = CreditRating.A))
        val d = decide(b, app(rating = CreditRating.BB))
        assertEquals(LoanDecision.Declined(DeclineReason.RATING_BELOW_STANDARD), d)
    }

    @Test fun closedSectorIsDeclined() {
        val b = BankingFx.bank()
        b.changePolicy(b.policy.withAppetite(LoanSector.CORPORATE, Rate.ZERO))
        assertEquals(LoanDecision.Declined(DeclineReason.SECTOR_CLOSED), decide(b))
    }

    @Test fun breachedBankStopsLending() {
        val b = BankingFx.bank()
        b.setRung(SupervisoryRung.RESTRICTED, app.many.economytycoon.core.Tick(1))
        assertEquals(LoanDecision.Declined(DeclineReason.SUPERVISORY_FREEZE), decide(b))
    }

    @Test fun bankUnderRunStopsLending() {
        val b = BankingFx.bank()
        b.applyConfidence(300_000L, RunStage.RUN)
        assertEquals(LoanDecision.Declined(DeclineReason.SUPERVISORY_FREEZE), decide(b))
    }

    @Test fun lowReturnLoansAreDeclined() {
        val b = BankingFx.bank()
        b.changePolicy(b.policy.copy(loanMarginBps = -150))
        val d = decide(b, app(LoanSector.MORTGAGE, CreditRating.AAA), BankingFx.calm.copy(policy = Rate.ZERO, shortYield = Rate.ZERO, longYield = Rate.ZERO))
        assertEquals(LoanDecision.Declined(DeclineReason.BELOW_HURDLE), d)
    }

    @Test fun monthlyGrowthBudgetIsShared() {
        val b = BankingFx.bank()
        val c = ctx(b)
        val d = Underwriter.evaluate(c, app(units = 100_000_000L), c.growthBudget, c.growthBudget)
        assertTrue(d is LoanDecision.Declined)
        assertEquals(DeclineReason.GROWTH_BUDGET, (d as LoanDecision.Declined).reason)
    }

    @Test fun growthBudgetScalesWithAppetite() {
        val b = BankingFx.bank()
        val low = ctx(b).growthBudget
        b.changePolicy(b.policy.copy(growthAppetite = Rate.percent(100)))
        assertTrue(ctx(b).growthBudget > low)
        b.changePolicy(b.policy.copy(growthAppetite = Rate.ZERO))
        assertEquals(Money.ZERO, ctx(b).growthBudget)
    }

    @Test fun sectorBudgetsFollowAppetiteAndSumToTheGrowthBudget() {
        val b = BankingFx.bank()
        val c = ctx(b)
        val sum = LoanSector.values().fold(Money.ZERO) { a, s -> a + c.sectorBudget(s) }
        assertTrue(sum.raw in (c.growthBudget.raw - 10L)..(c.growthBudget.raw + 10L))
        b.changePolicy(b.policy.withAppetite(LoanSector.CORPORATE, Rate.ZERO))
        assertEquals(Money.ZERO, ctx(b).sectorBudget(LoanSector.CORPORATE))
    }

    @Test fun concentrationLimitCapsSectorGrowth() {
        val b = BankingFx.bank()
        b.changePolicy(b.policy.copy(maxSectorShare = Rate.percent(10)))
        val room = Underwriter.concentrationRoom(ctx(b), LoanSector.MORTGAGE)
        val relaxed = BankingFx.bank().also { it.changePolicy(it.policy.copy(maxSectorShare = Rate.percent(100))) }
        assertTrue(Underwriter.concentrationRoom(ctx(relaxed), LoanSector.MORTGAGE) > room)
    }

    @Test fun thinLiquidityLeavesLittleRoom() {
        val b = BankingFx.bank()
        val roomBefore = Underwriter.liquidityRoom(ctx(b))
        val loss = b.reserves
        b.reserves -= loss; b.retained -= loss
        assertTrue(Underwriter.liquidityRoom(ctx(b)) < roomBefore)
    }

    @Test fun lossBudgetShrinksWithRiskierLending() {
        val c = ctx(BankingFx.bank())
        val safe = Underwriter.lossRoom(c, Rate.basisPoints(10))
        val risky = Underwriter.lossRoom(c, Rate.percent(10))
        assertTrue(safe > risky)
    }

    @Test fun recessionRaisesExpectedLossOnTheSameApplication() {
        val b = BankingFx.bank()
        val calm = decide(b) as LoanDecision.Approved
        val bad = decide(b, i = inputs.recession(800_000L))
        if (bad is LoanDecision.Approved) assertTrue(bad.expectedLoss > calm.expectedLoss)
    }
}
