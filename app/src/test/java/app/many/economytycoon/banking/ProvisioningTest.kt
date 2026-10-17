package app.many.economytycoon.banking

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningTest {
    private val neutral: (LoanSector) -> Long = { Rate.ONE_PPM }
    private val bad: (LoanSector) -> Long = { 3_000_000L }

    @Test fun allowanceMatchesNeutralExpectedLossAtOpening() {
        val b = BankingFx.bank()
        assertEquals(Provisioning.ecl(b.snapshot(), neutral, Rate.ONE_PPM).total, b.allowance)
    }

    @Test fun stagesAddUp() {
        val e = Provisioning.ecl(BankingFx.bank().snapshot(), neutral, Rate.ONE_PPM)
        assertEquals(e.performing + e.watch + e.impaired, e.total)
        assertTrue(e.impaired.isPositive && e.watch.isPositive && e.performing.isPositive)
    }

    @Test fun worseConditionsRaiseTheRequirement() {
        val s = BankingFx.bank().snapshot()
        val ok = Provisioning.ecl(s, neutral, Rate.ONE_PPM).total
        assertTrue(Provisioning.ecl(s, bad, Rate.ONE_PPM).total > ok)
        assertTrue(Provisioning.ecl(s, neutral, 500_000L).total > ok)
    }

    @Test fun emptyBookNeedsNothing() {
        val s = BankingFx.bank().snapshot().copy(loans = java.util.TreeMap())
        assertEquals(Money.ZERO, Provisioning.ecl(s, bad, 300_000L).total)
        assertEquals(Money.ZERO, Provisioning.annualExpectedLoss(s, bad))
    }

    @Test fun impairedLoansCarryTheFullLossGivenDefault() {
        val b = BankingFx.bank()
        val onlyImpaired = b.snapshot().copy(loans = java.util.TreeMap(b.loans.filterKeys { it.stage == LoanStage.IMPAIRED }))
        val e = Provisioning.ecl(onlyImpaired, neutral, Rate.ONE_PPM)
        val expected = onlyImpaired.loans.entries.fold(Money.ZERO) { a, (k, p) -> a + p.principal.times(RiskTables.lgd(k.sector)) }
        assertEquals(expected, e.impaired)
    }

    @Test fun adjustingTheAllowanceChargesRetainedEarningsAndKeepsBooksBalanced() {
        val b = BankingFx.bank()
        val retainedBefore = b.retained
        val charge = Provisioning.adjustAllowance(b, bad, Rate.ONE_PPM)
        assertTrue(charge.isPositive)
        assertEquals(retainedBefore - charge, b.retained)
        BankingFx.assertBalanced(b)
        val release = Provisioning.adjustAllowance(b, neutral, Rate.ONE_PPM)
        assertEquals(-charge, release)
        BankingFx.assertBalanced(b)
    }

    @Test fun adjustingTwiceIsANoOp() {
        val b = BankingFx.bank()
        Provisioning.adjustAllowance(b, bad, Rate.ONE_PPM)
        assertEquals(Money.ZERO, Provisioning.adjustAllowance(b, bad, Rate.ONE_PPM))
    }

    @Test fun neutralConditionsMoveLoansTowardsWatchAndImpairedInSmallAmounts() {
        val b = BankingFx.bank()
        val before = b.snapshot()
        val r = Provisioning.migrate(b, neutral, Rate.ZERO, DeterministicRandom(1L))
        val after = b.snapshot()
        assertEquals(before.loansGross, after.loansGross)
        assertTrue(r.toWatch.isPositive)
        assertTrue(r.toWatch < before.loansGross.times(Rate.percent(5)))
        BankingFx.assertBalanced(b)
    }

    @Test fun badConditionsMigrateMoreThanGoodOnes() {
        val a = BankingFx.bank(); val c = BankingFx.bank()
        val ok = Provisioning.migrate(a, neutral, Rate.ZERO, DeterministicRandom(1L))
        val worse = Provisioning.migrate(c, bad, Rate.ZERO, DeterministicRandom(1L))
        assertTrue(worse.toWatch + worse.toImpaired > ok.toWatch + ok.toImpaired)
        assertTrue(ok.cured.raw >= 0L)
    }

    @Test fun migrationConservesTheLoanBook() {
        val b = BankingFx.bank()
        val total = b.snapshot().loansGross
        repeat(24) { Provisioning.migrate(b, bad, Rate.percent(12), DeterministicRandom(it.toLong())) }
        assertEquals(total, b.snapshot().loansGross)
    }

    @Test fun migrationIsDeterministic() {
        val a = BankingFx.bank(); val c = BankingFx.bank()
        repeat(6) { Provisioning.migrate(a, bad, Rate.percent(12), DeterministicRandom(5L)); Provisioning.migrate(c, bad, Rate.percent(12), DeterministicRandom(5L)) }
        assertEquals(a.snapshot().loans, c.snapshot().loans)
    }

    @Test fun writeOffRemovesImpairedLoansAndReturnsRecoveries() {
        val b = BankingFx.bank()
        val impairedBefore = b.snapshot().loansIn(LoanStage.IMPAIRED)
        val reservesBefore = b.reserves
        val allowanceBefore = b.allowance
        val r = Provisioning.writeOff(b, Rate.ONE_PPM)
        assertTrue(r.written.isPositive)
        assertEquals(impairedBefore - r.written, b.snapshot().loansIn(LoanStage.IMPAIRED))
        assertEquals(reservesBefore + r.recovered, b.reserves)
        assertEquals(allowanceBefore - r.loss, b.allowance)
        assertEquals(r.written - r.recovered, r.loss)
        BankingFx.assertBalanced(b)
    }

    @Test fun lowerCollateralMeansSmallerRecoveries() {
        val a = BankingFx.bank(); val c = BankingFx.bank()
        val normal = Provisioning.writeOff(a, Rate.ONE_PPM)
        val crash = Provisioning.writeOff(c, 500_000L)
        assertTrue(crash.recovered <= normal.recovered)
    }

    @Test fun writeOffOfCleanBookDoesNothing() {
        val b = BankingFx.bank(spec = OpeningSpec(Money.units(1_000_000), watchShare = Rate.ZERO, impairedShare = Rate.ZERO))
        assertEquals(WriteOffReport.NONE, Provisioning.writeOff(b, Rate.ONE_PPM))
    }

    @Test fun annualExpectedLossIsPositiveAndGrowsInARecession() {
        val s = BankingFx.bank().snapshot()
        val ok = Provisioning.annualExpectedLoss(s, neutral)
        assertTrue(ok.isPositive)
        assertTrue(Provisioning.annualExpectedLoss(s, bad) > ok)
    }

    @Test fun fullYearOfBadTimesStaysBalanced() {
        val b = BankingFx.bank()
        for (m in 1..12) {
            Provisioning.migrate(b, bad, Rate.percent(12), DeterministicRandom(m.toLong()))
            Provisioning.writeOff(b, 700_000L)
            Provisioning.adjustAllowance(b, bad, 700_000L)
            BankingFx.assertBalanced(b)
        }
        assertTrue(b.snapshot().nplRatio > Rate.percent(2))
    }
}
