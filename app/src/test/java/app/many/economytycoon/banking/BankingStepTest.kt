package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily and monthly rounds as seen through [BankingService.step]. */
class BankingStepTest {
    private val inputs = StaticBankingInputs()
    private val country = BankingFx.country
    private val me = CompanyId(7)

    private fun sector(): BankingService {
        val s = BankingService()
        s.seedRivals(country, 3, inputs, Tick(0), RandomProvider(3L))
        s.found(BankAction.FoundBank("Mine", country, me, Money.units(1_000_000), true), inputs, Tick(0), RandomProvider(1L)).getOrThrow()
        return s
    }

    private fun run(s: BankingService, days: Int, seed: Long = 9L): List<BankingStep> {
        val r = RandomProvider(seed)
        return (1..days).map { s.step(Tick(it.toLong()), inputs, r) }
    }

    @Test fun monthCloseHappensOncePerThirtyDays() {
        val steps = run(sector(), 90)
        assertEquals(3, steps.count { it.monthClosed })
        assertFalse(steps.first().monthClosed)
    }

    @Test fun booksBalanceAfterEveryMonth() {
        val s = sector()
        run(s, 95)
        for (b in s.banks()) assertEquals(Money.ZERO, b.imbalance)
    }

    @Test fun playerBankPaysNoAutomaticDividend() {
        val s = sector()
        val events = run(s, 130).flatMap { it.events }
        val mine = s.banksOf(me).single().id
        assertTrue(events.filterIsInstance<BankEvent.DividendPaid>().none { it.bank == mine })
    }

    @Test fun computerBanksPayDividendsOnQuarterCloses() {
        val events = run(sector(), 540).flatMap { it.events }.filterIsInstance<BankEvent.DividendPaid>()
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.amount.isPositive })
    }

    @Test fun intentsOnlyComeOnMonthCloseDays() {
        val steps = run(sector(), 65)
        for ((i, st) in steps.withIndex()) if (!st.monthClosed && st.intents.isNotEmpty()) {
            // daily rounds may only return intents for emergency funding and resolutions
            assertTrue("day ${i + 1}", st.events.isNotEmpty())
        }
        assertTrue(steps.filter { it.monthClosed }.all { it.intents.isNotEmpty() })
    }

    @Test fun sameSeedGivesSameHash() {
        val a = sector(); val b = sector()
        run(a, 100); run(b, 100)
        assertEquals(a.stateHash(), b.stateHash())
    }

    @Test fun differentSeedGivesDifferentHash() {
        val a = sector(); val b = sector()
        run(a, 100, seed = 1L); run(b, 100, seed = 2L)
        assertFalse(a.stateHash() == b.stateHash())
    }

    @Test fun deadSectorDoesNothing() {
        val s = BankingService()
        val steps = run(s, 40)
        assertTrue(steps.all { it.events.isEmpty() && it.intents.isEmpty() })
    }
}
