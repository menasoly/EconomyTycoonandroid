package app.many.economytycoon.consolidation

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalingTest {
    @Test fun halfOfAStatementHasHalfOfEveryLine() {
        val i = GroupFixture.income()
        val half = i.scaled(500_000)
        assertEquals(Money.units(5_000), half.revenue)
        assertEquals(Money.units(2_000), half.cogs)
        assertEquals(Money.units(1_000), half.wages)
        assertEquals(i.netIncome.raw / 2, half.netIncome.raw)
    }

    @Test fun fullShareChangesNothing() {
        val i = GroupFixture.income()
        assertEquals(i, i.scaled(1_000_000))
        val b = GroupFixture.balance()
        assertEquals(b, b.scaled(1_000_000))
    }

    @Test fun noShareLeavesNothing() {
        val z = GroupFixture.income().scaled(0)
        assertEquals(Money.ZERO, z.revenue)
        assertEquals(Money.ZERO, z.netIncome)
        assertEquals(Money.ZERO, GroupFixture.balance().scaled(0).totalAssets)
    }

    @Test fun addingStatementsAddsEveryLine() {
        val a = GroupFixture.income(sales = 1_000, cogs = 100, wages = 50, tax = 10, interest = 5)
        val b = GroupFixture.income(sales = 2_000, cogs = 300, wages = 70, tax = 20, interest = 6)
        val sum = a + b
        assertEquals(Money.units(3_000), sum.revenue)
        assertEquals(Money.units(400), sum.cogs)
        assertEquals(a.netIncome + b.netIncome, sum.netIncome)
    }

    @Test fun aScaledBalanceSheetStillBalances() {
        val b = GroupFixture.balance()
        for (ppm in listOf(1L, 333_333L, 500_000L, 999_999L)) {
            val s = b.scaled(ppm)
            // Rounding each line can leave a unit of a ten-thousandth either way.
            assertTrue("$ppm", kotlin.math.abs(s.totalAssets.raw - s.totalLiabilities.raw - s.totalEquity.raw) <= 8L)
        }
    }

    @Test fun addedBalanceSheetsBalanceExactlyWhenTheParts_do() {
        val sum = GroupFixture.balance() + GroupFixture.balance(cash = 1_000, fixed = 4_000, debt = 1_000, capital = 4_000)
        assertTrue(sum.balances)
        assertEquals(Money.units(30_000), sum.totalAssets)
    }

    @Test fun largeAmountsDoNotOverflow() {
        val big = GroupFixture.balance(cash = 4_000_000_000_000, fixed = 0, debt = 0, capital = 4_000_000_000_000)
        assertEquals(Money.units(1_000_000_000_000), big.scaled(250_000).cash)
    }
}
