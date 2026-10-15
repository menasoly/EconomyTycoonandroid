package app.many.economytycoon.finance

import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementTest {
    private val co = LedgerOwner(OwnerKind.COMPANY, 1)
    private val customer = LedgerOwner(OwnerKind.HOUSEHOLDS, 0)
    private val supplier = LedgerOwner(OwnerKind.COMPANY, 2)
    private val gov = LedgerOwner(OwnerKind.GOVERNMENT, 1)

    private fun scenario(): FinanceService {
        val f = FinanceService(IdAllocators())
        f.setTaxPolicy(gov, TaxPolicy())
        f.registerCompany(co, gov, Holder.player(1), Money.units(500_000), 1_000, Tick(0)).getOrThrow()
        f.mint(Tick(0), customer, Money.units(1_000_000), "customers")
        f.mint(Tick(0), supplier, Money.units(10_000), "supplier")
        f.purchaseAsset("asset1", Tick(1), co, supplier, AssetClasses.MACHINERY, "press", Money.units(200_000)).getOrThrow()
        f.recordSale("sale1", Tick(5), co, customer, Money.units(80_000), Money.ZERO).getOrThrow()
        f.ledger.post(TransactionDraft("stock", Tick(6), ReasonCode.INVENTORY, listOf(
            Entry(AccountRef(co, Chart.INVENTORY), Money.units(30_000)), Entry(AccountRef(co, Chart.CASH), -Money.units(30_000)),
            Entry(AccountRef(supplier, Chart.CASH), Money.units(30_000)), Entry(AccountRef(supplier, Chart.REVENUE_SALES), -Money.units(30_000)))))
        f.settlement.settle(Tick(10), listOf(
            SettlementIntent.simple("w1", co, LedgerOwner.households(), Money.units(20_000), SettlementPriority.WAGES, ReasonCode.WAGES, Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER)))
        f.ledger.post(TransactionDraft("dep", Tick(30), ReasonCode.DEPRECIATION, listOf(
            Entry(AccountRef(co, Chart.DEPRECIATION_EXPENSE), Money.units(5_000)), Entry(AccountRef(co, Chart.ACCUMULATED_DEPRECIATION), -Money.units(5_000)))))
        return f
    }

    @Test fun balanceSheetBalances() {
        val f = scenario()
        val bs = f.statements.balance(co, Tick(30))
        assertTrue(bs.balances)
        assertEquals(bs.totalAssets, bs.totalLiabilities + bs.totalEquity)
        assertEquals(Money.units(195_000), bs.fixedAssetsNet)
    }

    @Test fun incomeStatementCapturesRevenueExpensesAndDepreciation() {
        val f = scenario()
        val inc = f.statements.income(co, Tick(0), Tick(30))
        assertEquals(Money.units(80_000), inc.revenue)
        assertEquals(Money.units(20_000), inc.wages)
        assertEquals(Money.units(5_000), inc.depreciation)
        assertEquals(Money.units(55_000), inc.netIncome)
        assertEquals(inc.ebit, inc.ebitda - inc.depreciation)
    }

    @Test fun cashFlowReconcilesToTheChangeInCash() {
        val f = scenario()
        val cf = f.statements.cashFlow(co, Tick(0), Tick(30))
        assertTrue(cf.reconciles)
        assertEquals(Money.units(-200_000) + Money.ZERO, cf.investing)
        assertEquals(cf.openingCash + cf.netChange, cf.closingCash)
    }

    @Test fun cashFlowReconcilesOverAnySubperiod() {
        val f = scenario()
        for ((a, b) in listOf(0L to 5L, 5L to 12L, 12L to 30L, 3L to 29L)) {
            assertTrue("period $a-$b", f.statements.cashFlow(co, Tick(a), Tick(b)).reconciles)
        }
    }

    @Test fun ratiosHandleZeroDenominators() {
        val f = FinanceService(IdAllocators())
        f.mint(Tick(0), co, Money.units(100), "x")
        val r = f.statements.ratios(co, Tick(0), Tick(10))
        assertEquals(Ratios.NONE, r.interestCoverage)
        assertEquals(Ratios.NONE, r.netMargin)
    }

    @Test fun ratiosReflectProfitability() {
        val f = scenario()
        val r = f.statements.ratios(co, Tick(0), Tick(30))
        assertTrue(r.grossMargin == Rate.ONE)
        assertTrue(r.netMargin.ppm in 600_000..700_000)
        assertTrue(r.currentRatio > Rate.ONE)
    }
}
