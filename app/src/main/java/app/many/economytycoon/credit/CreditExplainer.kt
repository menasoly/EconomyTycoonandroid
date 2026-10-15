package app.many.economytycoon.credit

import app.many.economytycoon.finance.CreditInputs
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.CreditScoring
import app.many.economytycoon.finance.ScoreFactor
import app.many.economytycoon.finance.ScoreItem

/** How a factor stands. */
enum class FactorState { STRONG, FAIR, WEAK }

/** One factor with what it does to the score and what could be done about it. */
class FactorView(val item: ScoreItem, val state: FactorState, val advice: String) {
    val factor: ScoreFactor get() = item.factor
    val points: Int get() = item.points
    val headroom: Int get() = item.headroom

    /** How far up its range the factor sits, 0 at its worst and 1,000,000 at its best. */
    val fillPpm: Long get() {
        val span = item.best - item.worst
        return if (span <= 0) 1_000_000L else (item.points - item.worst).toLong() * 1_000_000L / span
    }
}

/** The lowest score that earns [rating], and how far the company is from it. */
class Rung(val rating: CreditRating, val score: Int, val gap: Int)

/**
 * Explains a credit score: the rating the numbers earn, the points each factor adds or removes, how far it is to
 * the next rating up and how close the company is to dropping one, and which factors have most to give.
 */
class CreditExplanation(
    val inputs: CreditInputs,
    val heldRating: CreditRating,
    val score: Int,
    val earned: CreditRating,
    val factors: List<FactorView>,
) {
    /** The held rating moves one step at a time, so it can lag the rating the score now earns. */
    val willRise: Boolean get() = earned.ordinal < heldRating.ordinal
    val willFall: Boolean get() = earned.ordinal > heldRating.ordinal

    /** The next rating above the earned one, with the points still needed; null at the top. */
    val nextUp: Rung? get() {
        val better = CreditScoring.thresholds.lastOrNull { it.second.ordinal < earned.ordinal } ?: return null
        return Rung(better.second, better.first, (better.first - score).coerceAtLeast(0))
    }

    /** Points the score can fall before the earned rating drops; null at the bottom. */
    val cushion: Int? get() {
        val own = CreditScoring.thresholds.firstOrNull { it.second == earned } ?: return null
        return score - own.first
    }

    /** Factors with the most points still to win, biggest first; only those that can improve. */
    fun levers(limit: Int = 3): List<FactorView> = factors.filter { it.headroom > 0 && it.state != FactorState.STRONG }.sortedByDescending { it.headroom }.take(limit)

    /** Factors costing the most points against the middle of their range. */
    fun drags(limit: Int = 3): List<FactorView> = factors.filter { it.points < 0 }.sortedBy { it.points }.take(limit)

    val maxPossibleGain: Int get() = factors.sumOf { it.headroom }

    /** True when the company can only move up by reaching a new score, because nothing can be done about a factor. */
    val atCeiling: Boolean get() = earned == CreditRating.AAA && nextUp == null
}

object CreditExplainer {
    private val ADVICE: Map<ScoreFactor, String> = mapOf(
        ScoreFactor.INTEREST_COVER to "Earn more before interest, or borrow less, so that profit covers interest several times over.",
        ScoreFactor.LIQUIDITY to "Hold more cash and stock against what you owe within the year; repay short-term debt first.",
        ScoreFactor.GEARING to "Cut debt against equity: repay loans, or keep profit in the company instead of paying it all out.",
        ScoreFactor.MARGIN to "Raise prices, cut costs or drop idle lines so that more of each sale is kept as profit.",
        ScoreFactor.EQUITY to "Grow equity as a share of assets by keeping profit, or by funding growth with less debt.",
        ScoreFactor.AGE to "Only time helps here: each year of trading adds a few points, up to five years.",
        ScoreFactor.PAYMENTS to "Pay every instalment on time. Each missed payment costs a lot, and three means default.",
        ScoreFactor.ARREARS to "Clear overdue bills; what you owe late is weighed against your revenue.",
        ScoreFactor.NET_WORTH to "Net worth below zero costs points. Return to a positive balance sheet to remove the penalty.",
    )

    fun adviceFor(f: ScoreFactor): String = ADVICE.getValue(f)

    /** A factor in the top third of its range is strong, the bottom third weak. */
    fun stateOf(item: ScoreItem): FactorState {
        val span = item.best - item.worst
        if (span <= 0) return FactorState.STRONG
        val fill = (item.points - item.worst).toLong() * 3L / span
        return when {
            item.points >= item.best || fill >= 2L -> FactorState.STRONG
            fill >= 1L -> FactorState.FAIR
            else -> FactorState.WEAK
        }
    }

    fun explain(inputs: CreditInputs, held: CreditRating): CreditExplanation {
        val score = CreditScoring.score(inputs)
        val views = CreditScoring.breakdown(inputs).map { FactorView(it, stateOf(it), adviceFor(it.factor)) }
        return CreditExplanation(inputs, held, score, CreditScoring.target(score), views)
    }
}
