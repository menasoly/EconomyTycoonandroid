package app.many.economytycoon.finance

import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxDepreciationTest {
    private val co = LedgerOwner(OwnerKind.COMPANY, 1)
    private val policy = TaxPolicy(corporateRate = Rate.percent(25), smallProfitThreshold = Money.units(100_000), smallProfitRate = Rate.percent(10), lossOffsetCap = Rate.percent(50))

    @Test fun smallProfitsAreTaxedAtTheReducedRate() {
        val t = TaxAccount(co)
        assertEquals(Money.units(5_000), t.assess(Tick(90), Money.units(50_000), policy))
    }

    @Test fun profitAboveTheThresholdIsTaxedProgressively() {
        val t = TaxAccount(co)
        // 100k at 10% + 100k at 25%
        assertEquals(Money.units(35_000), t.assess(Tick(90), Money.units(200_000), policy))
    }

    @Test fun lossesCarryForwardAndOffsetLaterProfitUpToTheCap() {
        val t = TaxAccount(co)
        assertEquals(Money.ZERO, t.assess(Tick(90), Money.units(-300_000), policy))
        assertEquals(Money.units(300_000), t.totalCarriedLoss())
        // Profit 200k: offset capped at 50% = 100k, taxable 100k.
        val tax = t.assess(Tick(180), Money.units(200_000), policy)
        assertEquals(Money.units(10_000), tax)
        assertEquals(Money.units(200_000), t.totalCarriedLoss())
    }

    @Test fun oldLossesExpire() {
        val t = TaxAccount(co)
        t.assess(Tick(0), Money.units(-100_000), policy)
        val tax = t.assess(Tick(policy.lossCarryTicks + 10), Money.units(50_000), policy)
        assertEquals(Money.units(5_000), tax)
        assertEquals(Money.ZERO, t.totalCarriedLoss())
    }

    @Test fun straightLineDepreciatesEvenlyDownToSalvage() {
        val reg = AssetRegister()
        val a = reg.add(co, AssetClasses.BUILDING, "hall", Money.units(4_000_000), Tick(0))
        val life = AssetClasses.BUILDING.usefulLifeTicks
        val year = reg.depreciate(co, 360)
        assertEquals(Money.units(90_000), year)
        reg.depreciate(co, life * 2)
        assertEquals(a.salvageValue, a.bookValue)
        assertEquals(Money.ZERO, reg.depreciate(co, 360))
    }

    @Test fun decliningBalanceFrontLoadsDepreciation() {
        val reg = AssetRegister()
        val a = reg.add(co, AssetClasses.MACHINERY, "press", Money.units(1_000_000), Tick(0))
        val first = reg.depreciate(co, 360)
        val second = reg.depreciate(co, 360)
        assertTrue(first > second)
        assertTrue(a.bookValue > a.salvageValue)
    }

    @Test fun periodCloseBooksDepreciationAndAssessesTax() {
        val gov = LedgerOwner(OwnerKind.GOVERNMENT, 1)
        val f = FinanceService(IdAllocators())
        f.setTaxPolicy(gov, policy)
        f.registerCompany(co, gov, Holder.player(1), Money.units(2_000_000), 100, Tick(0)).getOrThrow()
        val seller = LedgerOwner(OwnerKind.COMPANY, 2)
        val buyer = LedgerOwner(OwnerKind.HOUSEHOLDS, 0)
        f.mint(Tick(0), seller, Money.units(1), "s"); f.mint(Tick(0), buyer, Money.units(5_000_000), "b")
        f.purchaseAsset("a", Tick(1), co, seller, AssetClasses.EQUIPMENT, "line", Money.units(600_000)).getOrThrow()
        for (t in 1L..90L) {
            if (t % 5L == 0L) f.recordSale("s$t", Tick(t), co, buyer, Money.units(40_000), Money.ZERO)
            f.closeIfDue(Tick(t)) { gov }
        }
        assertTrue(f.ledger.balance(co, Chart.DEPRECIATION_EXPENSE).isPositive)
        assertTrue(f.ledger.balance(co, Chart.TAX_EXPENSE).isPositive)
        assertTrue(f.company(co)!!.tax.lifetimeTaxPaid.isPositive)
        assertTrue(f.invariants().isEmpty())
        assertTrue(f.latestStatements(co) != null)
    }
}
