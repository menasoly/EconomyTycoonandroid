package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Ledger
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementEngine
import app.many.economytycoon.finance.SettlementPriority
import app.many.economytycoon.finance.SettlementStatus
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingLedgerTest {
    private val agency = LedgerOwner.households()
    private val ledger = MarketingLedger(agency)
    private val s = MarketingTestSupport.system()

    private fun campaign() = book(s)

    @Test fun zeroAmountsProduceNoIntent() {
        val c = campaign()
        assertNull(ledger.deposit(c, Money.ZERO, Tick(0)))
        assertNull(ledger.daily(c, Money.ZERO, Tick(0)))
        assertNull(ledger.refund(c, Money.ZERO, Tick(0)))
        assertNull(ledger.creative(c, Money.units(-1), Tick(0)))
        assertNull(ledger.splitTest(c, Money.ZERO, Tick(0)))
    }

    @Test fun dailyIntentsPayTheAgencyAsAMarketingExpense() {
        val c = campaign()
        val i = ledger.daily(c, Money.units(500), Tick(3))!!
        assertEquals("mkt:spd:1:3", i.key)
        assertEquals(LedgerOwner.company(acme), i.payer)
        assertEquals(agency, i.payee)
        assertEquals(SettlementPriority.OTHER, i.priority)
        assertEquals(ReasonCode.FEE, i.reason)
        assertTrue(!i.deferrable)
        assertEquals(Chart.MARKETING_EXPENSE, i.lines.single().payerDebit)
    }

    @Test fun refundsRunBackwards() {
        val c = campaign()
        val i = ledger.refund(c, Money.units(40), Tick(2))!!
        assertEquals(agency, i.payer)
        assertEquals(LedgerOwner.company(acme), i.payee)
        assertEquals(Chart.REVENUE_SERVICES, i.lines.single().payerDebit)
        assertEquals(Chart.MARKETING_EXPENSE, i.lines.single().payeeCredit)
    }

    @Test fun oneOffChargesCarryTheirAmountInTheKey() {
        val c = campaign()
        val a = ledger.deposit(c, Money.units(10), Tick(1))!!
        val b = ledger.deposit(c, Money.units(20), Tick(1))!!
        assertTrue(a.key != b.key)
        assertTrue(a.key.startsWith("mkt:dep:1:1:"))
    }

    @Test fun dailyKeysParseBackToTheCampaign() {
        val c = campaign()
        val key = ledger.daily(c, Money.units(5), Tick(9))!!.key
        assertEquals(c.id, MarketingLedger.dailyCampaign(key))
        assertNull(MarketingLedger.dailyCampaign(ledger.refund(c, Money.units(5), Tick(9))!!.key))
        assertNull(MarketingLedger.dailyCampaign("wages:1:2:3"))
        assertNull(MarketingLedger.dailyCampaign("mkt:spd:x:3"))
        assertNull(MarketingLedger.dailyCampaign("mkt:spd:1"))
    }

    @Test fun theFinanceLayerCanSettleTheIntentsAndTheBooksBalance() {
        val finance = Ledger(IdAllocators())
        val company = LedgerOwner.company(acme)
        finance.post(TransactionDraft("seed", Tick(0), ReasonCode.CAPITAL, listOf(Entry(AccountRef(company, Chart.CASH), Money.units(100_000)), Entry(AccountRef(LedgerOwner.SYSTEM, Chart.SHARE_CAPITAL), Money.units(-100_000))), "seed"))
        val settlement = SettlementEngine(finance)
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC))
        val intents = MarketingTestSupport.run(s, 0, 9).flatMap { it.intents }
        val results = settlement.settle(Tick(8), intents)
        assertTrue(results.all { it.status == SettlementStatus.PAID })
        // The week's deposit was paid at booking; only days eight and nine are billed in the daily run.
        assertEquals(Money.units(2_000), finance.balance(company, Chart.MARKETING_EXPENSE))
        assertEquals(Money.units(98_000), finance.cash(company))
        assertEquals(Money.units(2_000), finance.cash(agency))
        assertEquals(c.id, CampaignId(1))
    }

    @Test fun retryingAnIntentIsHarmless() {
        val finance = Ledger(IdAllocators())
        val company = LedgerOwner.company(acme)
        finance.post(TransactionDraft("seed", Tick(0), ReasonCode.CAPITAL, listOf(Entry(AccountRef(company, Chart.CASH), Money.units(10_000)), Entry(AccountRef(LedgerOwner.SYSTEM, Chart.SHARE_CAPITAL), Money.units(-10_000))), "seed"))
        val settlement = SettlementEngine(finance)
        val c = campaign()
        val i = ledger.daily(c, Money.units(500), Tick(0))!!
        assertEquals(SettlementStatus.PAID, settlement.settle(Tick(0), listOf(i)).single().status)
        assertEquals(SettlementStatus.DUPLICATE, settlement.settle(Tick(0), listOf(i)).single().status)
        assertEquals(Money.units(500), finance.balance(company, Chart.MARKETING_EXPENSE))
    }

    @Test fun anUnaffordableDayIsReportedUnpaidAndFeedsTheSuspensionRule() {
        val finance = Ledger(IdAllocators())
        val settlement = SettlementEngine(finance)
        val c = book(s, request(daily = 500, tier = CreativeTier.BASIC))
        var suspended = false
        for (day in 0 until 3) {
            val report = s.engine.advance(Tick(day.toLong()), MarketingTestSupport.audiences())
            val results = settlement.settle(Tick(day.toLong()), report.intents)
            assertTrue(results.all { it.status == SettlementStatus.UNPAID })
            suspended = s.engine.reportPayments(Tick(day.toLong()), results).any { it is MarketingEvent.CampaignSuspended }
        }
        assertTrue(suspended)
        assertEquals(CampaignStatus.SUSPENDED, c.status)
    }

    @Test fun aCancelRefundSettlesBackToTheCompany() {
        val finance = Ledger(IdAllocators())
        val company = LedgerOwner.company(acme)
        finance.post(TransactionDraft("seed", Tick(0), ReasonCode.CAPITAL, listOf(Entry(AccountRef(agency, Chart.CASH), Money.units(50_000)), Entry(AccountRef(LedgerOwner.SYSTEM, Chart.SHARE_CAPITAL), Money.units(-50_000))), "seed"))
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_000, tier = CreativeTier.BASIC))
        val r = s.campaigns.cancel(c.id, Tick(0)).getOrThrow()
        val result = SettlementEngine(finance).settle(Tick(0), r.intents).single()
        assertEquals(SettlementStatus.PAID, result.status)
        assertEquals(r.refund, finance.cash(company))
        assertTrue(Outcome.ok(1).isOk)
        assertEquals(CompanyId(1), acme)
    }
}
