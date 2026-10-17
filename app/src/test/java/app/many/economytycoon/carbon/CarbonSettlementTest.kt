package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonSettlementTest {
    private var seq = 0L
    private val b = SettlementBuilder(Tick(5)) { ++seq }
    private val buyer = SettlementBuilder.company(acme)
    private val seller = SettlementBuilder.company(borea)
    private val gov = SettlementBuilder.government(uk)

    @Test fun aPurchaseMovesMoneyAndCarriesTheAssetAtCost() {
        b.purchase("auction", buyer, gov, Money.units(500), "permits")
        val i = b.build().payments.single()
        assertEquals(buyer, i.payer)
        assertEquals(gov, i.payee)
        assertEquals(Money.units(500), i.total)
        assertEquals(Chart.INTANGIBLES, i.lines.single().payerDebit)
        assertFalse(i.deferrable)
        assertEquals(ReasonCode.PURCHASE, i.reason)
    }

    @Test fun aChargeIsStatutoryExpensedAndDeferrable() {
        b.charge("tax", buyer, gov, Money.units(70), ReasonCode.TAX, "carbon tax")
        val i = b.build().payments.single()
        assertEquals(SettlementPriority.STATUTORY, i.priority)
        assertTrue(i.deferrable)
        assertEquals(Chart.OTHER_EXPENSE, i.lines.single().payerDebit)
    }

    @Test fun aSubsidyComesFromTheGovernmentAndIsNeverDeferred() {
        b.subsidy("abate", gov, buyer, Money.units(9), "support")
        val i = b.build().payments.single()
        assertEquals(gov, i.payer)
        assertEquals(buyer, i.payee)
        assertFalse(i.deferrable)
        assertEquals(ReasonCode.SUBSIDY, i.reason)
    }

    @Test fun contractorsAreHouseholdsNeverTheSystem() {
        b.capital("abate", buyer, SettlementBuilder.contractors, Money.units(100), "capital")
        b.running("abate", buyer, SettlementBuilder.contractors, Money.units(10), "running")
        assertTrue(b.build().payments.all { it.payee.kind == OwnerKind.HOUSEHOLDS })
    }

    @Test fun zeroAmountsProduceNoIntent() {
        b.purchase("x", buyer, gov, Money.ZERO, "")
        b.charge("x", buyer, gov, Money.ZERO, ReasonCode.TAX, "")
        b.subsidy("x", gov, buyer, Money.ZERO, "")
        b.writeDown(buyer, Chart.INTANGIBLES, Money.ZERO, "")
        b.refund("x", gov, buyer, Money.ZERO, Chart.INTANGIBLES, "")
        assertTrue(b.isEmpty())
        assertTrue(b.build().isEmpty)
    }

    @Test fun aSaleAboveBasisBooksAGainOnTheSellerThroughTheLines() {
        b.tradeOfPermits(buyer, seller, Money.units(1_000), Money.units(400), "trade")
        val i = b.build().payments.single()
        assertEquals(Money.units(1_000), i.total)
        assertEquals(2, i.lines.size)
        assertEquals(Money.units(400), i.lines.first { it.payeeCredit == Chart.INTANGIBLES }.amount)
        assertEquals(Money.units(600), i.lines.first { it.payeeCredit == Chart.REVENUE_OTHER }.amount)
        assertTrue(b.build().adjustments.isEmpty())
    }

    @Test fun aSaleBelowBasisBooksALossAsAnAdjustment() {
        b.tradeOfPermits(buyer, seller, Money.units(300), Money.units(1_000), "trade")
        val s = b.build()
        assertEquals(Money.units(300), s.payments.single().total)
        assertEquals(1, s.adjustments.size)
        assertEquals(Money.units(700), s.adjustments.single().entries.first { it.amount.isPositive }.amount)
    }

    @Test fun writeDownEntriesBalance() {
        b.writeDown(buyer, Chart.INTANGIBLES, Money.units(40), "surrendered")
        val d = b.build().adjustments.single()
        assertEquals(Money.ZERO, Money.sum(d.entries.map { it.amount }))
        assertEquals(ReasonCode.ADJUSTMENT, d.reason)
        assertEquals(Tick(5), d.tick)
    }

    @Test fun keysAreUniqueWithinAStep() {
        b.purchase("a", buyer, gov, Money.units(1), ""); b.purchase("a", buyer, gov, Money.units(1), ""); b.writeDown(buyer, Chart.INTANGIBLES, Money.units(1), "")
        val s = b.build()
        val keys = s.payments.map { it.key } + s.adjustments.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("carbon:") })
    }

    @Test fun settlementsCombine() {
        b.purchase("a", buyer, gov, Money.units(1), "")
        val one = b.build()
        val both = one + one
        assertEquals(2, both.payments.size)
        assertEquals(Money.units(2), both.paymentTotal)
        assertTrue(CarbonSettlement.EMPTY.isEmpty)
    }

    // ---- the cash book ----

    @Test fun theCashBookStopsDoubleSpending() {
        val env = FixedEnvironment(cashOf = { Money.units(100) })
        val book = CashBook(env)
        assertTrue(book.spend(acme, Money.units(60)))
        assertFalse(book.spend(acme, Money.units(60)))
        assertEquals(Money.units(40), book.available(acme))
        book.refund(acme, Money.units(60))
        assertEquals(Money.units(100), book.available(acme))
        assertFalse(book.spend(acme, Money(-1)))
    }

    @Test fun theCashBookTracksGovernmentsSeparately() {
        val book = CashBook(FixedEnvironment(treasury = { Money.units(10) }))
        assertTrue(book.governmentSpend(uk, Money.units(10)))
        assertFalse(book.governmentSpend(uk, Money.units(1)))
        assertEquals(Money.ZERO, book.governmentAvailable(uk))
    }

    @Test fun noCashMeansNothingIsAvailableEvenWhenNegative() {
        val book = CashBook(FixedEnvironment(cashOf = { Money.units(-5) }))
        assertEquals(Money.ZERO, book.available(acme))
    }

    @Test fun theGreenHookDefaultsToNothing() {
        assertEquals(Money.ZERO, GreenSubsidyHook.None.bonus(acme, uk, AbatementKind.FUEL_SWITCH, Money.units(100)))
    }
}
