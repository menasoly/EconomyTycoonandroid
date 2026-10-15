package app.many.economytycoon.finance

import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementTest {
    private val payer = LedgerOwner(OwnerKind.COMPANY, 1)
    private val supplier = LedgerOwner(OwnerKind.COMPANY, 2)
    private val worker = LedgerOwner.households()
    private val gov = LedgerOwner(OwnerKind.GOVERNMENT, 1)

    private fun setup(cash: Long): Triple<Ledger, SettlementEngine, FinanceService> {
        val f = FinanceService(IdAllocators())
        if (cash > 0) f.mint(Tick(0), payer, Money.units(cash), "seed")
        return Triple(f.ledger, f.settlement, f)
    }

    private fun pay(key: String, to: LedgerOwner, amount: Long, p: SettlementPriority, debit: Chart = Chart.COGS, credit: Chart = Chart.REVENUE_SALES) =
        SettlementIntent.simple(key, payer, to, Money.units(amount), p, ReasonCode.PURCHASE, debit, credit)

    @Test fun affordableIntentIsPaidInFull() {
        val (ledger, engine, _) = setup(1_000)
        val r = engine.settle(Tick(1), listOf(pay("p1", supplier, 400, SettlementPriority.SUPPLIERS)))
        assertEquals(SettlementStatus.PAID, r.single().status)
        assertEquals(Money.units(600), ledger.cash(payer))
        assertEquals(Money.units(400), ledger.cash(supplier))
    }

    @Test fun priorityOrderDecidesWhoGetsPaidWhenCashIsShort() {
        val (ledger, engine, _) = setup(500)
        val intents = listOf(
            pay("supplier", supplier, 400, SettlementPriority.SUPPLIERS),
            pay("tax", gov, 300, SettlementPriority.STATUTORY, Chart.TAX_EXPENSE, Chart.REVENUE_OTHER),
            pay("wages", worker, 150, SettlementPriority.WAGES, Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER),
        )
        val results = engine.settle(Tick(1), intents).associateBy { it.intent.key }
        assertEquals(SettlementStatus.PAID, results.getValue("tax").status)
        assertEquals(SettlementStatus.PAID, results.getValue("wages").status)
        assertEquals(SettlementStatus.DEFERRED, results.getValue("supplier").status)
        assertEquals(Money.units(50), ledger.cash(payer))
        assertEquals(Money.units(400), engine.totalArrears(payer))
    }

    @Test fun arrearsAreClearedWhenCashReturns() {
        val (ledger, engine, f) = setup(100)
        engine.settle(Tick(1), listOf(pay("big", supplier, 400, SettlementPriority.SUPPLIERS)))
        assertEquals(1, engine.arrears().size)
        f.mint(Tick(2), payer, Money.units(1_000), "topup")
        val r = engine.settle(Tick(3), emptyList())
        assertEquals(SettlementStatus.PAID, r.single().status)
        assertTrue(engine.arrears().isEmpty())
        assertEquals(Money.units(400), ledger.cash(supplier))
        assertEquals(0L, ledger.trialBalance())
    }

    @Test fun nonDeferrableIntentIsReportedUnpaidWithoutBooking() {
        val (ledger, engine, _) = setup(10)
        val intent = SettlementIntent.simple("loan", payer, supplier, Money.units(50), SettlementPriority.SECURED_DEBT, ReasonCode.LOAN_REPAYMENT, Chart.DEBT_SHORT, Chart.LOANS_RECEIVABLE, deferrable = false)
        val r = engine.settle(Tick(1), listOf(intent))
        assertEquals(SettlementStatus.UNPAID, r.single().status)
        assertEquals(0, ledger.transactionCount - 1)
        assertTrue(engine.arrears().isEmpty())
    }

    @Test fun sameKeyIsNeverSettledTwice() {
        val (ledger, engine, _) = setup(1_000)
        val intent = pay("once", supplier, 100, SettlementPriority.SUPPLIERS)
        engine.settle(Tick(1), listOf(intent))
        val again = engine.settle(Tick(2), listOf(intent))
        assertEquals(SettlementStatus.DUPLICATE, again.single().status)
        assertEquals(Money.units(900), ledger.cash(payer))
    }

    @Test fun randomIntentsNeverUnbalanceTheLedgerOrCreateMoney() {
        val rnd = DeterministicRandom(99)
        val f = FinanceService(IdAllocators())
        val owners = (1L..5L).map { LedgerOwner(OwnerKind.COMPANY, it) }
        for (o in owners) f.mint(Tick(0), o, Money.units(2_000), "seed${o.id}")
        val supply = f.ledger.moneySupply()
        for (t in 1L..200L) {
            val intents = ArrayList<SettlementIntent>()
            repeat(rnd.nextInt(8)) { n ->
                val from = owners[rnd.nextInt(owners.size)]
                var to = owners[rnd.nextInt(owners.size)]
                if (to == from) to = owners[(owners.indexOf(from) + 1) % owners.size]
                val amount = Money.units(rnd.nextInRange(1, 900))
                val prio = SettlementPriority.values()[rnd.nextInt(SettlementPriority.values().size)]
                intents.add(SettlementIntent.simple("r$t-$n", from, to, amount, prio, ReasonCode.PURCHASE, Chart.COGS, Chart.REVENUE_SALES, deferrable = rnd.nextBoolean()))
            }
            f.settlement.settle(Tick(t), intents)
            assertEquals(0L, f.ledger.trialBalance())
        }
        assertEquals(supply, f.ledger.moneySupply())
        for (o in owners) assertTrue(f.ledger.cash(o).raw >= 0)
    }
}
