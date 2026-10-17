package app.many.economytycoon.net

import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    private fun st(
        id: Long, worth: Long, income: Long = 0, player: Long? = id, active: Boolean = true, assets: Long = worth * 2, liabilities: Long = worth, name: String = "Co $id",
    ) = CompanyStatement(id, name, player?.let { PlayerId(it) }, Money.units(income * 2), Money.units(income), Money.units(worth), Money.units(worth / 2), Money.units(liabilities), Money.units(assets), active)

    private val rules = ScoreRules()
    private fun points(s: CompanyStatement, base: Long = 0) = SeasonScoring.score(s, Money.units(base), rules)

    @Test fun growthIsWorthOnePointPerThousandUnits() {
        assertEquals(5L, points(st(1, 5_000, assets = 0), 0).lines.first { it.label == "Net worth growth" }.points)
        assertEquals(-3L, points(st(1, 1_000, assets = 0), 4_000).lines.first { it.label == "Net worth growth" }.points)
    }

    @Test fun partialPointsRoundDownTowardMinusInfinity() {
        assertEquals(0L, points(st(1, 999, assets = 0)).lines[0].points)
        assertEquals(-1L, points(st(1, -1, assets = 0)).lines[0].points)
    }

    @Test fun incomeIsWorthOnePointPerTwoThousandUnits() {
        val b = points(st(1, 0, income = 6_500, assets = 0))
        assertEquals(3L, b.lines.first { it.label == "Income" }.points)
    }

    @Test fun solvencyBonusNeedsLiabilitiesAtMostHalfOfAssets() {
        assertTrue(points(st(1, 10, assets = 1000, liabilities = 500)).lines.any { it.label == "Solvency" })
        assertFalse(points(st(1, 10, assets = 1000, liabilities = 501)).lines.any { it.label == "Solvency" })
        assertFalse(points(st(1, 10, assets = 0, liabilities = 0)).lines.any { it.label == "Solvency" })
    }

    @Test fun failedCompaniesLoseFivePointsHundredOnTop() {
        val b = points(st(1, 0, active = false, assets = 100, liabilities = 0))
        assertEquals(-500L, b.total)
        assertFalse(b.lines.any { it.label == "Solvency" })
    }

    @Test fun scoreHasAFloor() {
        assertEquals(-1000L, points(st(1, -50_000_000, active = false, assets = 0)).total)
    }

    @Test fun totalIsTheSumOfItsLines() {
        val b = points(st(1, 12_345, income = 9_999, assets = 40_000, liabilities = 1_000), 2_000)
        assertEquals(b.lines.sumOf { it.points }, b.total)
    }

    @Test fun tableRanksByPointsThenNetWorthThenCompany() {
        val rows = SeasonScoring.table(listOf(st(1, 5_000), st(2, 9_000), st(3, 1_000)), emptyMap(), rules)
        assertEquals(listOf(2L, 1L, 3L), rows.map { it.company })
        assertEquals(listOf(1, 2, 3), rows.map { it.rank })
    }

    @Test fun placementBonusesGoToTheTopThree() {
        val rows = SeasonScoring.table((1L..5L).map { st(it, it * 10_000, assets = 0) }, emptyMap(), rules)
        val bonus = rows.map { r -> r.score.lines.firstOrNull { it.label == "Placing" }?.points ?: 0L }
        assertEquals(listOf(300L, 200L, 100L, 0L, 0L), bonus)
    }

    @Test fun baselinesMakeGrowthRelative() {
        val rich = st(1, 100_000, assets = 0); val newcomer = st(2, 40_000, assets = 0)
        val rows = SeasonScoring.table(listOf(rich, newcomer), mapOf(1L to Money.units(99_000), 2L to Money.units(0)), rules)
        assertEquals(2L, rows[0].company)
    }

    @Test fun tiesAreFlaggedAndBrokenByNetWorth() {
        val a = st(1, 1_000, assets = 0).copy(netIncome = Money.ZERO)
        val b = st(2, 1_900, assets = 0).copy(netIncome = Money.ZERO)
        val rows = SeasonScoring.table(listOf(a, b), emptyMap(), ScoreRules(placementBonus = emptyList()))
        assertEquals(1L, rows[0].total); assertEquals(1L, rows[1].total)
        assertEquals(listOf(2L, 1L), rows.map { it.company })
        assertTrue(rows.all { it.tied })
    }

    @Test fun computerCompaniesAppearWithoutAPlayer() {
        val rows = SeasonScoring.table(listOf(st(1, 5_000), st(2, 9_000, player = null)), emptyMap(), rules)
        assertEquals(null, rows.first { it.company == 2L }.player)
        assertEquals(PlayerId(1), rows.first { it.company == 1L }.player)
    }

    @Test fun emptyTableIsFine() {
        assertTrue(SeasonScoring.table(emptyList(), emptyMap(), rules).isEmpty())
    }

    @Test fun orderOfInputDoesNotMatter() {
        val list = (1L..8L).map { st(it, (it * 7919) % 13 * 1_000, income = it * 100) }
        assertEquals(SeasonScoring.table(list, emptyMap(), rules), SeasonScoring.table(list.reversed(), emptyMap(), rules))
    }

    @Test fun rulesRejectNonsense() {
        try { ScoreRules(wealthUnitsPerPoint = 0); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
    }

    @Test fun customRulesChangeTheScore() {
        val s = st(1, 4_000, assets = 0)
        val strict = SeasonScoring.score(s, Money.ZERO, ScoreRules(wealthUnitsPerPoint = 4_000))
        assertEquals(1L, strict.lines[0].points)
    }
}
