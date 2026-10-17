package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.finance.CreditScoring

/** The parts a bank's credit score is made of. */
enum class BankScoreFactor(val label: String) {
    CAPITAL("Capital headroom"),
    ASSET_QUALITY("Asset quality"),
    EARNINGS("Earnings"),
    LIQUIDITY("Liquidity"),
    FUNDING("Funding mix"),
    CONFIDENCE("Depositor confidence"),
    SUPERVISION("Supervisory standing"),
    SCALE("Size and diversification"),
}

data class BankScoreItem(val factor: BankScoreFactor, val points: Int, val best: Int, val worst: Int) {
    val headroom: Int get() = best - points
}

/** Outcome of a rating review: the new rating, the score, and the state of the hysteresis counter. */
data class RatingReview(
    val rating: CreditRating,
    val score: Int,
    val direction: Int,
    val count: Int,
    val items: List<BankScoreItem>,
)

/**
 * Rates a bank on capital, asset quality, earnings, liquidity, funding, confidence and the supervisor's view. The score
 * is mapped to a rating with the same thresholds as company credit, and the rating moves a notch at a time and only after
 * the target has differed for several reviews; downgrades come quicker than upgrades. A bank under resolution is rated D.
 */
object BankRatingModel {
    private fun banded(f: BankScoreFactor, x: Long, table: List<Pair<Long, Int>>) =
        BankScoreItem(f, bandOf(x, table), table.maxOf { it.second }, table.minOf { it.second })

    private fun bandOf(x: Long, table: List<Pair<Long, Int>>): Int {
        var out = table.first().second
        for ((t, v) in table) if (x >= t) out = v
        return out
    }

    private val CAPITAL = listOf(Long.MIN_VALUE to -150, -20_000L to -150, -10_000L to -80, 0L to -20, 10_000L to 30, 30_000L to 80, 60_000L to 120)
    private val NPL = listOf(Long.MIN_VALUE to 60, 10_001L to 20, 30_001L to -30, 60_001L to -90, 100_001L to -150)
    private val ROA = listOf(Long.MIN_VALUE to -100, -5_000L to -50, 0L to 0, 4_000L to 30, 8_000L to 60)
    private val LCR = listOf(Long.MIN_VALUE to -120, 800_000L to -60, 1_000_000L to 0, 1_300_000L to 20, 2_000_000L to 50)
    private val CONFIDENCE = listOf(Long.MIN_VALUE to -120, 250_000L to -80, 450_000L to -30, 700_000L to 0, 900_000L to 20)

    fun breakdown(s: BankSnapshot, capital: CapitalPosition, liquidity: LiquidityPosition): List<BankScoreItem> {
        val last = s.lastResult
        val roa = last?.returnOnAssets?.ppm ?: 0L
        val coverageAdj = if (s.loansIn(LoanStage.IMPAIRED).isPositive && s.coverageRatio.ppm < 500_000L) -30 else 0
        val npl = banded(BankScoreFactor.ASSET_QUALITY, s.nplRatio.ppm, NPL)
        val earningPoints = bandOf(roa, ROA) + if (last != null && last.costToIncome.ppm > 800_000L) -30 else 0
        val wholesale = s.wholesaleShare.ppm
        val fundingPoints = when {
            wholesale > 300_000L -> -60
            wholesale > 200_000L -> -25
            wholesale < 100_000L -> 20
            else -> 0
        }
        val supervision = when (s.rung) {
            SupervisoryRung.NORMAL -> 0
            SupervisoryRung.BUFFER -> -20
            SupervisoryRung.RESTRICTED -> -100
            SupervisoryRung.SEVERE -> -200
            SupervisoryRung.CRITICAL -> -300
        }
        val scale = if (s.totalAssets >= Money.units(20_000_000L)) 20 else 0
        return listOf(
            banded(BankScoreFactor.CAPITAL, capital.headroom.ppm, CAPITAL),
            BankScoreItem(BankScoreFactor.ASSET_QUALITY, npl.points + coverageAdj, 60, -180),
            BankScoreItem(BankScoreFactor.EARNINGS, earningPoints, 60, -130),
            banded(BankScoreFactor.LIQUIDITY, liquidity.lcr.ppm, LCR),
            BankScoreItem(BankScoreFactor.FUNDING, fundingPoints, 20, -60),
            banded(BankScoreFactor.CONFIDENCE, s.confidencePpm, CONFIDENCE),
            BankScoreItem(BankScoreFactor.SUPERVISION, supervision, 0, -300),
            BankScoreItem(BankScoreFactor.SCALE, scale, 20, 0),
        )
    }

    fun score(items: List<BankScoreItem>, base: Int): Int = (base + items.sumOf { it.points }).coerceIn(0, 1000)

    /**
     * Reviews a rating. [direction] and [count] are the hysteresis state from the last review: how many reviews in a row
     * the target has been on the same side of the current rating.
     */
    fun review(
        s: BankSnapshot, capital: CapitalPosition, liquidity: LiquidityPosition, current: CreditRating, direction: Int, count: Int, p: RatingParams,
    ): RatingReview {
        val items = breakdown(s, capital, liquidity)
        val score = score(items, p.base)
        if (s.status == BankStatus.IN_RESOLUTION || s.status == BankStatus.LIQUIDATED) return RatingReview(CreditRating.D, score, 0, 0, items)
        val target = CreditScoring.target(score)
        val dir = target.ordinal.compareTo(current.ordinal).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
        if (dir == 0) return RatingReview(current, score, 0, 0, items)
        val n = if (dir == direction) count + 1 else 1
        // A downgrade needs fewer confirmations than an upgrade.
        val needed = if (dir > 0) (p.confirmations - 1).coerceAtLeast(1) else p.confirmations + 1
        if (n >= needed) {
            val next = if (dir > 0) current.down() else current.up()
            return RatingReview(next, score, 0, 0, items)
        }
        return RatingReview(current, score, dir, n, items)
    }
}
