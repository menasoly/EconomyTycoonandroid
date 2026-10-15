package app.many.economytycoon.consolidation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupStatementsTest {
    private val root = GroupFixture.member(1, "Parent", 1_000_000)
    private val wholly = GroupFixture.member(2, "Wholly", 1_000_000, GroupFixture.income(sales = 6_000))
    private val half = GroupFixture.member(3, "Half", 500_000, GroupFixture.income(sales = 30_000, cogs = 8_000, wages = 4_000, tax = 1_000))

    @Test fun aGroupOfOneIsTheCompanyItself() {
        val g = GroupStatements.of(CompanyId(1), listOf(root))!!
        assertEquals(root.income.netIncome, g.income.netIncome)
        assertEquals(root.balance.totalAssets, g.balance.totalAssets)
        assertTrue(g.subsidiaries.isEmpty())
    }

    @Test fun subsidiariesCountInProportionToOwnership() {
        val g = GroupStatements.of(CompanyId(1), listOf(root, wholly, half))!!
        // 10,000 + 6,000 + half of 30,000.
        assertEquals(Money.units(31_000), g.income.revenue)
        assertEquals(3, g.size)
        assertEquals(2, g.subsidiaries.size)
    }

    @Test fun groupAssetsAddUpTheSameWay() {
        val g = GroupStatements.of(CompanyId(1), listOf(root, wholly, half))!!
        assertEquals(Money.units(25_000 + 25_000 + 12_500), g.balance.totalAssets)
        assertTrue(g.balance.balances)
    }

    @Test fun theRootMustComeFirst() {
        assertNull(GroupStatements.of(CompanyId(1), listOf(wholly, root)))
        assertNull(GroupStatements.of(CompanyId(1), emptyList()))
    }

    @Test fun contributionsAreRankedLargestFirst() {
        val g = GroupStatements.of(CompanyId(1), listOf(root, wholly, half))!!
        val by = g.revenueBy()
        assertEquals("Half", by.first().name)
        assertEquals(by.map { it.amount.raw }.sortedDescending(), by.map { it.amount.raw })
        assertEquals(g.income.revenue, by.fold(Money.ZERO) { a, c -> a + c.amount })
    }

    @Test fun subsidiaryTotalsLeaveOutTheRoot() {
        val g = GroupStatements.of(CompanyId(1), listOf(root, wholly, half))!!
        assertEquals(Money.units(21_000), g.subsidiaryRevenue)
        assertEquals(g.income.netIncome - root.income.netIncome, g.subsidiaryNetIncome)
    }

    @Test fun revenueSharesAddToOneWhole() {
        val g = GroupStatements.of(CompanyId(1), listOf(root, wholly, half))!!
        val total = listOf(1L, 2L, 3L).sumOf { g.revenueShare(CompanyId(it)) }
        assertTrue(total in 999_997L..1_000_000L)
        assertEquals(0L, g.revenueShare(CompanyId(99)))
    }

    @Test fun aSubsidiaryThatCarriesTheGroupIsFlagged() {
        val big = GroupFixture.member(4, "Giant", 1_000_000, GroupFixture.income(sales = 100_000))
        val g = GroupStatements.of(CompanyId(1), listOf(root, big))!!
        assertEquals("Giant", g.dominantMember()?.name)
        assertNull(GroupStatements.of(CompanyId(1), listOf(root, wholly))!!.dominantMember(900_000L))
    }

    @Test fun aGroupWithoutRevenueHasNoShares() {
        val idle = GroupFixture.member(1, "Idle", 1_000_000, GroupFixture.income(sales = 0, cogs = 0, wages = 0, tax = 0, interest = 0))
        val g = GroupStatements.of(CompanyId(1), listOf(idle))!!
        assertEquals(0L, g.revenueShare(CompanyId(1)))
        assertNotNull(g.rootAlone)
        assertFalse(g.rootAlone!!.shareOfRevenue.isPositive)
    }
}
