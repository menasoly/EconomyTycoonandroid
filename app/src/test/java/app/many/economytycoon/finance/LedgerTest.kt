package app.many.economytycoon.finance

import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private val a = LedgerOwner(OwnerKind.COMPANY, 1)
    private val b = LedgerOwner(OwnerKind.COMPANY, 2)

    private fun ledger() = Ledger(IdAllocators())

    private fun draft(key: String, tick: Long, vararg e: Pair<AccountRef, Long>) =
        TransactionDraft(key, Tick(tick), ReasonCode.TRANSFER, e.map { Entry(it.first, Money.units(it.second)) })

    @Test fun balancedTransactionPosts() {
        val l = ledger()
        val r = l.post(draft("t1", 1, AccountRef(a, Chart.CASH) to 100, AccountRef(a, Chart.SHARE_CAPITAL) to -100))
        assertTrue(r.isOk)
        assertEquals(Money.units(100), l.cash(a))
        assertEquals(0L, l.trialBalance())
    }

    @Test fun unbalancedTransactionIsRejectedAndChangesNothing() {
        val l = ledger()
        val r = l.post(draft("t1", 1, AccountRef(a, Chart.CASH) to 100, AccountRef(a, Chart.SHARE_CAPITAL) to -90))
        assertTrue(r.isRejected)
        assertEquals(Money.ZERO, l.cash(a))
        assertEquals(0, l.transactionCount)
    }

    @Test fun duplicateKeyIsRejected() {
        val l = ledger()
        val d = draft("same", 1, AccountRef(a, Chart.CASH) to 5, AccountRef(b, Chart.CASH) to -5)
        assertTrue(l.post(d).isOk)
        val second = l.post(d)
        assertEquals(Rules.DUPLICATE, second.rejectionOrNull()?.rule)
        assertEquals(Money.units(5), l.cash(a))
    }

    @Test fun singleEntryAndZeroTransactionsAreRejected() {
        val l = ledger()
        assertTrue(l.post(draft("x", 1, AccountRef(a, Chart.CASH) to 0)).isRejected)
        assertTrue(l.post(draft("y", 1, AccountRef(a, Chart.CASH) to 0, AccountRef(b, Chart.CASH) to 0)).isRejected)
    }

    @Test fun postingBehindTheLedgerClockIsRejected() {
        val l = ledger()
        l.post(draft("t5", 5, AccountRef(a, Chart.CASH) to 1, AccountRef(b, Chart.CASH) to -1))
        val late = l.post(draft("t3", 3, AccountRef(a, Chart.CASH) to 1, AccountRef(b, Chart.CASH) to -1))
        assertTrue(late.isRejected)
    }

    @Test fun historicalBalancesAreExact() {
        val l = ledger()
        l.post(draft("t1", 1, AccountRef(a, Chart.CASH) to 10, AccountRef(b, Chart.CASH) to -10))
        l.post(draft("t4", 4, AccountRef(a, Chart.CASH) to 30, AccountRef(b, Chart.CASH) to -30))
        l.post(draft("t9", 9, AccountRef(a, Chart.CASH) to -5, AccountRef(b, Chart.CASH) to 5))
        val ref = AccountRef(a, Chart.CASH)
        assertEquals(Money.ZERO, l.balanceAt(ref, Tick(0)))
        assertEquals(Money.units(10), l.balanceAt(ref, Tick(3)))
        assertEquals(Money.units(40), l.balanceAt(ref, Tick(4)))
        assertEquals(Money.units(40), l.balanceAt(ref, Tick(8)))
        assertEquals(Money.units(35), l.balanceAt(ref, Tick(100)))
        assertEquals(Money.units(30), l.activity(ref, Tick(1), Tick(4)))
    }

    @Test fun compactionKeepsBalancesAndLaterHistory() {
        val l = ledger()
        for (t in 1L..20L) l.post(draft("t$t", t, AccountRef(a, Chart.CASH) to 1, AccountRef(b, Chart.CASH) to -1))
        l.compact(Tick(15))
        val ref = AccountRef(a, Chart.CASH)
        assertEquals(Money.units(20), l.cash(a))
        assertEquals(Money.units(20), l.balanceAt(ref, Tick(20)))
        assertEquals(Money.units(17), l.balanceAt(ref, Tick(17)))
    }

    @Test fun naturalBalancesFollowAccountClass() {
        val l = ledger()
        l.post(draft("t1", 1, AccountRef(a, Chart.CASH) to 100, AccountRef(a, Chart.SHARE_CAPITAL) to -100))
        assertEquals(Money.units(100), l.natural(AccountRef(a, Chart.SHARE_CAPITAL)))
        l.post(draft("t2", 2, AccountRef(a, Chart.DEPRECIATION_EXPENSE) to 10, AccountRef(a, Chart.ACCUMULATED_DEPRECIATION) to -10))
        assertEquals(Money.units(10), l.natural(AccountRef(a, Chart.ACCUMULATED_DEPRECIATION)))
        assertEquals(Money.units(10), l.natural(AccountRef(a, Chart.DEPRECIATION_EXPENSE)))
    }

    @Test fun replayRebuildsTheSameBalances() {
        val l = ledger()
        for (t in 1L..10L) l.post(draft("t$t", t, AccountRef(a, Chart.CASH) to t, AccountRef(b, Chart.CASH) to -t))
        val copy = ledger()
        copy.replay(l.transactions())
        assertEquals(l.cash(a), copy.cash(a))
        assertEquals(l.cash(b), copy.cash(b))
        assertFalse(copy.hasKey("missing"))
    }

    @Test fun hashChangesWithBalances() {
        val l1 = ledger()
        val l2 = ledger()
        l1.post(draft("t", 1, AccountRef(a, Chart.CASH) to 1, AccountRef(b, Chart.CASH) to -1))
        l2.post(draft("t", 1, AccountRef(a, Chart.CASH) to 2, AccountRef(b, Chart.CASH) to -2))
        val h1 = app.many.economytycoon.core.CanonicalHasher().also { l1.hashInto(it) }.finish()
        val h2 = app.many.economytycoon.core.CanonicalHasher().also { l2.hashInto(it) }.finish()
        assertTrue(h1 != h2)
    }
}
