package app.many.economytycoon.net

import app.many.economytycoon.core.Money

/**
 * How a company's statements turn into points. Every input is an integer amount of money and every step is
 * integer arithmetic, so two machines always agree on a score. Points come from growth, not size: a company
 * is judged by how much its net worth rose over the season, how much it earned and whether it ended healthy.
 */
data class ScoreRules(
    /** Whole currency units of net worth gained per point. */
    val wealthUnitsPerPoint: Long = 1_000L,
    /** Whole currency units of net income per point. */
    val incomeUnitsPerPoint: Long = 2_000L,
    /** Points for ending with liabilities at most half of assets. */
    val solvencyBonus: Long = 100L,
    /** Points taken off a company that is no longer trading. */
    val failurePenalty: Long = 500L,
    /** Points by finishing place, first place first. */
    val placementBonus: List<Long> = listOf(300L, 200L, 100L),
    /** Points are never lower than this, so one disaster cannot bury a player for good. */
    val floor: Long = -1_000L,
) {
    init {
        require(wealthUnitsPerPoint > 0 && incomeUnitsPerPoint > 0) { "points per unit must be positive" }
    }
}

data class ScoreLine(val label: String, val points: Long)

data class ScoreBreakdown(val lines: List<ScoreLine>, val total: Long)

/** One row of the table. */
data class LeaderRow(
    val rank: Int,
    val company: Long,
    val name: String,
    /** The person running the company, null for computer-controlled ones. */
    val player: PlayerId?,
    val score: ScoreBreakdown,
    val netWorth: Money,
    val netIncome: Money,
    val revenue: Money,
    val tied: Boolean,
) {
    val total: Long get() = score.total
}

object SeasonScoring {
    /** Points a company earns before placement bonuses. [baseline] is its net worth when the season began. */
    fun score(s: CompanyStatement, baseline: Money, rules: ScoreRules = ScoreRules()): ScoreBreakdown {
        val lines = ArrayList<ScoreLine>()
        val unit = Money.SCALE
        val growth = Math.floorDiv(s.netWorth.raw - baseline.raw, rules.wealthUnitsPerPoint * unit)
        lines += ScoreLine("Net worth growth", growth)
        val income = Math.floorDiv(s.netIncome.raw, rules.incomeUnitsPerPoint * unit)
        lines += ScoreLine("Income", income)
        if (s.active && s.assets.raw > 0L && s.liabilities.raw * 2 <= s.assets.raw) lines += ScoreLine("Solvency", rules.solvencyBonus)
        if (!s.active) lines += ScoreLine("Failed company", -rules.failurePenalty)
        val total = lines.sumOf { it.points }.coerceAtLeast(rules.floor)
        return ScoreBreakdown(lines, total)
    }

    /**
     * The ranked table. Placement bonuses are added after ranking by points so the order they are based on is
     * the order before the bonus; ties are broken by net worth and then by company number, and flagged.
     */
    fun table(statements: List<CompanyStatement>, baselines: Map<Long, Money>, rules: ScoreRules = ScoreRules()): List<LeaderRow> {
        val scored = statements.map { it to score(it, baselines[it.company] ?: Money.ZERO, rules) }
            .sortedWith(compareByDescending<Pair<CompanyStatement, ScoreBreakdown>> { it.second.total }
                .thenByDescending { it.first.netWorth.raw }
                .thenBy { it.first.company })
        val withBonus = scored.mapIndexed { i, (st, b) ->
            val bonus = rules.placementBonus.getOrNull(i) ?: 0L
            st to if (bonus == 0L) b else ScoreBreakdown(b.lines + ScoreLine("Placing", bonus), b.total + bonus)
        }
        val ordered = withBonus.sortedWith(compareByDescending<Pair<CompanyStatement, ScoreBreakdown>> { it.second.total }
            .thenByDescending { it.first.netWorth.raw }.thenBy { it.first.company })
        return ordered.mapIndexed { i, (st, b) ->
            val tied = (i > 0 && ordered[i - 1].second.total == b.total) || (i < ordered.size - 1 && ordered[i + 1].second.total == b.total)
            LeaderRow(i + 1, st.company, st.name, st.player, b, st.netWorth, st.netIncome, st.revenue, tied)
        }
    }
}
