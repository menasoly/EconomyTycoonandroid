package app.many.economytycoon.companies

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LoanStatus
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.RepaymentType
import app.many.economytycoon.finance.TransactionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsolvencyTest {
    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule

    private fun CompanyRig.burn(c: Company, amount: Money, key: String) {
        finance.ledger.post(
            TransactionDraft(
                key, Tick(tick), ReasonCode.TRANSFER,
                listOf(
                    Entry(AccountRef(c.owner, Chart.COGS), amount), Entry(AccountRef(c.owner, Chart.CASH), -amount),
                    Entry(AccountRef(finance.households, Chart.CASH), amount), Entry(AccountRef(finance.households, Chart.REVENUE_OTHER), -amount),
                ),
            ),
        ).getOrThrow()
    }

    private fun CompanyRig.lend(c: Company, amount: Money) =
        finance.loans.originate(
            Tick(0), c.owner, finance.bank,
            app.many.economytycoon.finance.LoanOffer(amount, app.many.economytycoon.core.Rate.percent(8), 360, 30, RepaymentType.AMORTIZING, emptyList()),
        ).getOrThrow()

    @Test fun openingACaseMovesTheCompanyIntoAdministration() {
        val r = CompanyRig(); val c = r.found("Shaky Mills")
        val case = r.insolvency.open(c.id, Tick(10)).getOrThrow()
        assertEquals(CasePhase.ASSESSMENT, case.phase)
        assertEquals(CompanyStatus.ADMINISTRATION, r.companies.require(c.id).status)
        assertEquals(Rules.DUPLICATE, rule(r.insolvency.open(c.id, Tick(11))))
        assertEquals(1, r.insolvency.openCases().size)
    }

    @Test fun unknownCompaniesAreRejected() {
        val r = CompanyRig()
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.insolvency.open(app.many.economytycoon.core.CompanyId(99), Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.insolvency.liquidate(app.many.economytycoon.core.CompanyId(99), Tick(0))))
    }

    @Test fun assessmentWithoutStatementsEndsInLiquidation() {
        val r = CompanyRig(); val c = r.found("Shaky Mills")
        r.insolvency.open(c.id, Tick(0))
        r.insolvency.step(Tick(2))
        assertEquals(CasePhase.ASSESSMENT, r.insolvency.case(c.id)!!.phase)
        r.insolvency.step(Tick(r.insolvency.params.assessmentTicks.toLong()))
        assertEquals(CasePhase.LIQUIDATION, r.insolvency.case(c.id)!!.phase)
        assertEquals(CompanyStatus.LIQUIDATING, r.companies.require(c.id).status)
        assertEquals(listOf(c.id), r.insolvency.awaitingLiquidation())
    }

    @Test fun liquidationReturnsSurplusToShareholders() {
        val r = CompanyRig(); val c = r.found("Calm Mills", capital = Money.units(200_000))
        val before = r.finance.ledger.cash(r.finance.households)
        r.insolvency.open(c.id, Tick(0))
        val result = r.insolvency.liquidate(c.id, Tick(1)).getOrThrow()
        assertEquals(Money.units(200_000), result.residualToShareholders)
        assertEquals(before + Money.units(200_000), r.finance.ledger.cash(r.finance.households))
        assertEquals(CompanyStatus.DISSOLVED, r.companies.require(c.id).status)
        assertEquals(CaseOutcome.LIQUIDATED, r.insolvency.case(c.id)!!.outcome)
        assertTrue(r.finance.invariants().isEmpty())
    }

    @Test fun liquidationRepaysLoansBeforeShareholders() {
        val r = CompanyRig(); val c = r.found("Leveraged Co", capital = Money.units(200_000))
        val loan = r.lend(c, Money.units(100_000))
        r.burn(c, Money.units(250_000), "burn1")
        val bankBefore = r.finance.ledger.cash(r.finance.bank)
        val result = r.insolvency.liquidate(c.id, Tick(5)).getOrThrow()
        assertEquals(Money.ZERO, result.residualToShareholders)
        assertTrue(result.debtWrittenOff.raw > 0)
        assertTrue(r.finance.ledger.cash(r.finance.bank) > bankBefore)
        assertTrue(r.finance.loans.get(loan.id)!!.status != LoanStatus.ACTIVE)
        assertEquals(emptyList<String>(), r.finance.invariants())
    }

    @Test fun fullyCoveredLoansLeaveAResidual() {
        val r = CompanyRig(); val c = r.found("Solid Co", capital = Money.units(300_000))
        r.lend(c, Money.units(50_000))
        val result = r.insolvency.liquidate(c.id, Tick(5)).getOrThrow()
        assertEquals(Money.ZERO, result.debtWrittenOff)
        assertTrue(result.residualToShareholders.raw > Money.units(250_000).raw - Money.units(1_000).raw)
        assertTrue(r.finance.invariants().isEmpty())
    }

    @Test fun dissolvedCompaniesCannotBeLiquidatedTwice() {
        val r = CompanyRig(); val c = r.found("One Shot")
        r.insolvency.liquidate(c.id, Tick(1)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, rule(r.insolvency.liquidate(c.id, Tick(2))))
        assertEquals(Rules.WRONG_STATE, rule(r.insolvency.open(c.id, Tick(2))))
    }

    @Test fun voluntaryDissolutionRequiresCashForDebts() {
        val r = CompanyRig(); val c = r.found("Debtor Co", capital = Money.units(100_000))
        r.lend(c, Money.units(40_000))
        r.burn(c, Money.units(120_000), "burn")
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(r.insolvency.dissolveVoluntarily(c.id, Tick(3))))
        val ok = r.found("Clean Co", capital = Money.units(100_000))
        val res = r.insolvency.dissolveVoluntarily(ok.id, Tick(3)).getOrThrow()
        assertEquals(Money.units(100_000), res.residualToShareholders)
        assertEquals(CompanyStatus.DISSOLVED, r.companies.require(ok.id).status)
    }

    @Test fun stateEnterprisesAreNeverScannedIntoInsolvency() {
        val r = CompanyRig()
        r.found("State Grid", CompanyKind.STATE, capital = Money.units(1_000_000))
        assertTrue(r.insolvency.scanForInsolvency(Tick(5)).isEmpty())
        assertNull(r.insolvency.case(r.companies.all().single().id))
    }

    @Test fun standstillIsReportedOnlyWhileRestructuring() {
        val r = CompanyRig(); val c = r.found("Maybe Co")
        assertFalse(r.insolvency.inStandstill(c.id))
        val case = r.insolvency.open(c.id, Tick(0)).getOrThrow()
        assertFalse(r.insolvency.inStandstill(c.id))
        case.phase = CasePhase.RESTRUCTURING
        assertTrue(r.insolvency.inStandstill(c.id))
        assertNotNull(r.insolvency.case(c.id))
    }

    @Test fun restructuringEndsInRecoveryWhenDistressClears() {
        val r = CompanyRig(); val c = r.found("Phoenix Co")
        val case = r.insolvency.open(c.id, Tick(0)).getOrThrow()
        case.phase = CasePhase.RESTRUCTURING
        // A fresh monitor is HEALTHY, so the next step closes the case as recovered.
        r.insolvency.step(Tick(3))
        assertEquals(CaseOutcome.RECOVERED, case.outcome)
        assertEquals(CasePhase.CLOSED, case.phase)
        assertEquals(CompanyStatus.ACTIVE, r.companies.require(c.id).status)
    }

    @Test fun stateHashChangesWithCases() {
        val r = CompanyRig(); val c = r.found("Hash Co")
        val a = r.hash()
        r.insolvency.open(c.id, Tick(1))
        assertTrue(a != r.hash())
    }
}
