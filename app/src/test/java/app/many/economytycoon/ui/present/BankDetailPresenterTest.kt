package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.banking.BankingFx
import app.many.economytycoon.banking.BankingService
import app.many.economytycoon.banking.DepositProduct
import app.many.economytycoon.banking.StaticBankingInputs
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankDetailPresenterTest {
    private val inputs = StaticBankingInputs()
    private val country = BankingFx.country
    private val me = CompanyId(7)
    private val money: (Money) -> String = { Formatting.compactMoney(it) }

    private fun setup(): Pair<BankingService, BankId> {
        val s = BankingService()
        val id = s.found(BankAction.FoundBank("Mine", country, me, Money.units(1_000_000), true), inputs, Tick(0), RandomProvider(1L)).getOrThrow().bank
        return s to id
    }

    private fun detail(s: BankingService, id: BankId, manage: Boolean = true) = BankDetailPresenter.build(s, id, inputs, 0L, money, manage)!!

    @Test fun unknownBankHasNoDetail() {
        assertNull(BankDetailPresenter.build(BankingService(), BankId(99), inputs, 0L, money, true))
    }

    @Test fun detailHasAllSections() {
        val (s, id) = setup()
        val d = detail(s, id)
        assertEquals("Mine", d.name)
        assertEquals(6, d.tiles.size)
        assertTrue(d.balanceAssets.isNotEmpty())
        assertTrue(d.balanceFunding.isNotEmpty())
        assertTrue(d.capital.isNotEmpty())
        assertTrue(d.liquidity.isNotEmpty())
        assertTrue(d.earnings.isNotEmpty())
        assertTrue(d.policy.isNotEmpty())
        assertEquals("Active", d.status)
    }

    @Test fun balanceSheetTotalsMatchTheBank() {
        val (s, id) = setup()
        val d = detail(s, id)
        val bank = s.bank(id)!!
        assertEquals(money(bank.totalAssets), d.balanceAssets.last().value)
        assertEquals("Total assets", d.balanceAssets.last().label)
    }

    @Test fun lendingRowsOnlyShowSectorsWithLoans() {
        val (s, id) = setup()
        val d = detail(s, id)
        assertTrue(d.lending.size <= 8)
        assertTrue(d.lending.all { it.balance.isNotEmpty() })
    }

    @Test fun policyRowsListEveryDepositProduct() {
        val (s, id) = setup()
        val rows = detail(s, id).policy.map { it.label }
        for (p in DepositProduct.values()) assertTrue(rows.any { it.startsWith(p.label) })
        assertTrue("Loan margin" in rows)
    }

    @Test fun healthyBankHasNoNotice() {
        val (s, id) = setup()
        assertNull(detail(s, id).notice)
    }

    @Test fun readOnlyViewCannotManage() {
        val (s, id) = setup()
        assertTrue(detail(s, id, true).canManage)
        assertFalse(detail(s, id, false).canManage)
    }

    @Test fun historyIsEmptyBeforeTheFirstClose() {
        val (s, id) = setup()
        assertTrue(detail(s, id).history.isEmpty())
    }

    @Test fun historyListsNewestFirstAfterMonths() {
        val (s, id) = setup()
        val r = RandomProvider(5L)
        for (t in 1L..95L) s.step(Tick(t), inputs, r)
        val h = detail(s, id).history
        assertNotNull(h)
        assertTrue(h.size in 1..6)
        assertTrue(detail(s, id).tiles.last().value != "n/a")
    }

    @Test fun detailIsDeterministic() {
        val (s1, id1) = setup()
        val (s2, id2) = setup()
        assertEquals(detail(s1, id1), detail(s2, id2))
    }
}
