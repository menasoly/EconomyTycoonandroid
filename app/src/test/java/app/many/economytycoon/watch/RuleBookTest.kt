package app.many.economytycoon.watch

import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBookTest {
    private val cash = WatchRule.Cash(Money.units(20_000))

    @Test fun addAssignsIncreasingIds() {
        val b = RuleBook()
        assertEquals(1L, b.add(cash))
        assertEquals(2L, b.add(cash))
        assertEquals(2, b.size)
    }

    @Test fun theBookHasALimit() {
        val b = RuleBook(maxRules = 2)
        b.add(cash); b.add(cash)
        assertNull(b.add(cash))
        assertEquals(2, b.size)
    }

    @Test fun removeForgetsTheRule() {
        val b = RuleBook()
        val id = b.add(cash)!!
        assertTrue(b.remove(id))
        assertFalse(b.remove(id))
        assertNull(b.get(id))
    }

    @Test fun idsAreNotReusedAfterRemoval() {
        val b = RuleBook()
        val a = b.add(cash)!!
        b.remove(a)
        assertEquals(a + 1, b.add(cash))
    }

    @Test fun firingStartsACooldown() {
        val b = RuleBook()
        val id = b.add(cash, cooldownDays = 10)!!
        val f = FakeFacts(day = 100)
        assertEquals(1, b.evaluate(f, FakeNames, plainMoney).size)
        f.day = 105
        assertTrue(b.evaluate(f, FakeNames, plainMoney).isEmpty())
        f.day = 110
        assertEquals(1, b.evaluate(f, FakeNames, plainMoney).size)
        assertEquals(110L, b.lastFiredDay(id))
    }

    @Test fun zeroCooldownFiresEveryDay() {
        val b = RuleBook()
        b.add(cash, cooldownDays = 0)
        val f = FakeFacts(day = 1)
        for (d in 1..5L) { f.day = d; assertEquals(1, b.evaluate(f, FakeNames, plainMoney).size) }
    }

    @Test fun disabledRulesStaySilent() {
        val b = RuleBook()
        val id = b.add(cash)!!
        b.setEnabled(id, false)
        assertTrue(b.evaluate(FakeFacts(), FakeNames, plainMoney).isEmpty())
        b.setEnabled(id, true)
        assertEquals(1, b.evaluate(FakeFacts(), FakeNames, plainMoney).size)
    }

    @Test fun ruleThatDoesNotFireDoesNotStartACooldown() {
        val b = RuleBook()
        val id = b.add(WatchRule.Cash(Money.units(1)))!!
        b.evaluate(FakeFacts(), FakeNames, plainMoney)
        assertNull(b.lastFiredDay(id))
    }

    @Test fun replacingARuleClearsItsCooldown() {
        val b = RuleBook()
        val id = b.add(cash)!!
        b.evaluate(FakeFacts(), FakeNames, plainMoney)
        assertTrue(b.replace(id, WatchRule.Cash(Money.units(30_000))))
        assertNull(b.lastFiredDay(id))
        assertFalse(b.replace(999, cash))
    }

    @Test fun hitsComeInRuleOrder() {
        val b = RuleBook()
        b.add(WatchRule.Idle(1)); b.add(cash)
        val hits = b.evaluate(FakeFacts(idleFacilities = 3), FakeNames, plainMoney)
        assertEquals(listOf(1L, 2L), hits.map { it.ruleId })
    }

    @Test fun unknownIdsAreNeverReady() = assertFalse(RuleBook().ready(5, 100))

    @Test fun clearEmptiesTheBook() {
        val b = RuleBook()
        b.add(cash); b.clear()
        assertEquals(0, b.size)
    }

    private fun full(): RuleBook {
        val b = RuleBook()
        b.add(WatchRule.Price(RegionId(2), GoodId("steel"), false, Money.units(70), Severity.WARNING), 3)
        b.add(WatchRule.Cash(Money.units(1), Severity.CRITICAL))
        b.add(WatchRule.Profit(Money.units(-100)))
        b.add(WatchRule.Stock(CompanyId(4), 80_000))
        b.add(WatchRule.Loan(14))
        b.add(WatchRule.Delivery(2, Severity.WARNING))
        b.add(WatchRule.Idle(2))
        b.setEnabled(3, false)
        b.evaluate(FakeFacts(day = 55, cash = Money.ZERO), FakeNames, plainMoney)
        return b
    }

    @Test fun everyKindRoundTrips() {
        val a = full()
        val b = RuleBook.fromBytes(a.toBytes())
        assertEquals(a.all(), b.all())
        for (e in a.all()) assertEquals(a.lastFiredDay(e.id), b.lastFiredDay(e.id))
    }

    @Test fun roundTripKeepsIdsMovingForward() {
        val b = RuleBook.fromBytes(full().toBytes())
        assertEquals(8L, b.add(cash))
    }

    @Test fun encodingIsStable() = assertTrue(full().toBytes().contentEquals(full().toBytes()))

    @Test fun damagedBytesGiveAnEmptyBook() {
        assertEquals(0, RuleBook.fromBytes(null).size)
        assertEquals(0, RuleBook.fromBytes(ByteArray(0)).size)
        assertEquals(0, RuleBook.fromBytes(byteArrayOf(1, 2, 3)).size)
        val bytes = full().toBytes()
        assertEquals(0, RuleBook.fromBytes(bytes.copyOf(bytes.size / 2)).size)
    }

    @Test fun wrongVersionGivesAnEmptyBook() {
        val w = ByteWriter(); w.writeVarInt(77); w.writeVarLong(1)
        assertEquals(0, RuleBook.fromBytes(w.toByteArray()).size)
    }

    @Test fun implausibleCountsAreRefused() {
        val w = ByteWriter(); w.writeVarInt(RuleBook.VERSION); w.writeVarLong(1); w.writeVarInt(100_000)
        assertEquals(0, RuleBook.fromBytes(w.toByteArray()).size)
    }
}
