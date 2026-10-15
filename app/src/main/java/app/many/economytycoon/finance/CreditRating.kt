package app.many.economytycoon.finance

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

enum class CreditRating(val label: String, val spreadBps: Long, val limitMultiple: Long) {
    AAA("AAA", 40, 8), AA("AA", 70, 6), A("A", 110, 5), BBB("BBB", 170, 4),
    BB("BB", 280, 3), B("B", 450, 2), CCC("CCC", 800, 1), D("D", 1500, 0);

    val spread: Rate get() = Rate.basisPoints(spreadBps)
    val investmentGrade: Boolean get() = ordinal <= BBB.ordinal

    /** Rating one notch better, or this one at the top. */
    fun up(): CreditRating = values()[(ordinal - 1).coerceAtLeast(0)]
    fun down(): CreditRating = values()[(ordinal + 1).coerceAtMost(values().size - 1)]
}

/** Inputs for scoring a borrower. All derived from statements and payment history. */
data class CreditInputs(
    val ratios: Ratios,
    val ageTicks: Long,
    val missedPayments: Int,
    val arrears: Money,
    val revenue: Money,
    val netWorth: Money,
)

/** The parts a credit score is made of. */
enum class ScoreFactor(val label: String) {
    INTEREST_COVER("Interest cover"),
    LIQUIDITY("Short-term liquidity"),
    GEARING("Debt against equity"),
    MARGIN("Profit margin"),
    EQUITY("Equity cushion"),
    AGE("Years trading"),
    PAYMENTS("Payment record"),
    ARREARS("Overdue bills"),
    NET_WORTH("Net worth"),
}

/** What one factor adds to or takes from the score, and the most and least it could. */
data class ScoreItem(val factor: ScoreFactor, val points: Int, val best: Int, val worst: Int) {
    /** Points still to be won from this factor. */
    val headroom: Int get() = best - points
}

object CreditScoring {
    const val BASE = 500

    private val COVER = listOf(0L to -150, 1_000_000L to -60, 1_500_000L to 0, 3_000_000L to 50, 6_000_000L to 100)
    private val LIQUIDITY = listOf(0L to -80, 800_000L to -40, 1_200_000L to 0, 2_000_000L to 40, 3_000_000L to 70)
    private val GEARING = listOf(-100_000_000L to -120, -3_000_000L to -80, -1_500_000L to -30, -600_000L to 30, 1L to 70)
    private val MARGIN = listOf(-1_000_000L to -80, 0L to -30, 50_000L to 10, 150_000L to 40, 300_000L to 60)
    private val EQUITY = listOf(-1_000_000L to -100, 0L to -60, 200_000L to -10, 400_000L to 30, 650_000L to 50)
    private const val AGE_YEARS_COUNTED = 5L
    private const val AGE_POINTS_PER_YEAR = 8
    private const val MISSED_PENALTY = 70
    private const val ARREARS_CAP = 120
    private const val NEGATIVE_WORTH_PENALTY = 100

    /** Each factor's contribution to the score, before the base of [BASE] and the 0..1000 limits. Pure and deterministic. */
    fun breakdown(i: CreditInputs): List<ScoreItem> {
        val r = i.ratios
        fun banded(f: ScoreFactor, x: Long, table: List<Pair<Long, Int>>) = ScoreItem(f, band(x, table), table.maxOf { it.second }, table.minOf { it.second })
        val arrearsPoints = if (i.arrears.isPositive && i.revenue.isPositive) {
            -(Rate.ratio(i.arrears.raw, i.revenue.raw).ppm / 10_000L).toInt().coerceAtMost(ARREARS_CAP)
        } else 0
        return listOf(
            banded(ScoreFactor.INTEREST_COVER, r.interestCoverage.ppm, COVER),
            banded(ScoreFactor.LIQUIDITY, r.currentRatio.ppm, LIQUIDITY),
            banded(ScoreFactor.GEARING, -r.debtToEquity.ppm, GEARING),
            banded(ScoreFactor.MARGIN, r.netMargin.ppm, MARGIN),
            banded(ScoreFactor.EQUITY, r.equityRatio.ppm, EQUITY),
            ScoreItem(ScoreFactor.AGE, (minOf(i.ageTicks, 360L * AGE_YEARS_COUNTED) / 360L * AGE_POINTS_PER_YEAR).toInt(), (AGE_YEARS_COUNTED * AGE_POINTS_PER_YEAR).toInt(), 0),
            ScoreItem(ScoreFactor.PAYMENTS, -i.missedPayments * MISSED_PENALTY, 0, minOf(-3 * MISSED_PENALTY, -i.missedPayments * MISSED_PENALTY)),
            ScoreItem(ScoreFactor.ARREARS, arrearsPoints, 0, -ARREARS_CAP),
            ScoreItem(ScoreFactor.NET_WORTH, if (i.netWorth.raw < 0) -NEGATIVE_WORTH_PENALTY else 0, 0, -NEGATIVE_WORTH_PENALTY),
        )
    }

    /** Score 0..1000 from ratios and history. Pure and deterministic. */
    fun score(i: CreditInputs): Int = (BASE + breakdown(i).sumOf { it.points }).coerceIn(0, 1000)

    /** Piecewise-constant lookup: takes the value of the highest threshold at or below [x]. */
    private fun band(x: Long, table: List<Pair<Long, Int>>): Int {
        var out = table.first().second
        for ((t, v) in table) if (x >= t) out = v
        return out
    }

    /** The lowest score that earns each rating, best first. */
    val thresholds: List<Pair<Int, CreditRating>> = listOf(
        880 to CreditRating.AAA, 790 to CreditRating.AA, 700 to CreditRating.A, 610 to CreditRating.BBB,
        520 to CreditRating.BB, 420 to CreditRating.B, 280 to CreditRating.CCC,
    )

    fun target(score: Int): CreditRating = thresholds.firstOrNull { score >= it.first }?.second ?: CreditRating.D
}

/**
 * Tracks a rating with hysteresis. The rating moves one notch at a time and only after the target has
 * differed for [confirmations] consecutive reviews, which prevents flapping around a boundary.
 */
class RatingTracker(initial: CreditRating = CreditRating.BBB, private val confirmations: Int = 2) {
    var rating: CreditRating = initial
        private set
    var lastScore: Int = 500
        private set
    private var pendingDirection = 0
    private var pendingCount = 0

    fun review(inputs: CreditInputs): CreditRating {
        val score = CreditScoring.score(inputs)
        lastScore = score
        val target = CreditScoring.target(score)
        val direction = target.ordinal.compareTo(rating.ordinal).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
        // A forced default overrides hysteresis.
        if (inputs.missedPayments >= 3) { rating = CreditRating.D; pendingDirection = 0; pendingCount = 0; return rating }
        if (direction == 0) { pendingDirection = 0; pendingCount = 0; return rating }
        if (direction == pendingDirection) pendingCount++ else { pendingDirection = direction; pendingCount = 1 }
        // Downgrades are quicker than upgrades.
        val needed = if (direction > 0) confirmations - 1 else confirmations + 1
        if (pendingCount >= needed.coerceAtLeast(1)) {
            rating = if (direction > 0) rating.down() else rating.up()
            pendingDirection = 0
            pendingCount = 0
        }
        return rating
    }

    internal fun restore(rating: CreditRating, score: Int, direction: Int, count: Int) {
        this.rating = rating; lastScore = score; pendingDirection = direction; pendingCount = count
    }

    internal val pendingDirectionValue get() = pendingDirection
    internal val pendingCountValue get() = pendingCount
}

/** Credit terms offered at a given rating. */
data class CreditTerms(val rate: Rate, val limit: Money)

object CreditPolicy {
    /** Lenders also lend against equity: one tenth of net worth per notch of the rating's multiple. */
    fun terms(rating: CreditRating, baseRate: Rate, ebitdaAnnual: Money, existingDebt: Money, netWorth: Money = Money.ZERO): CreditTerms {
        val fromEarnings = ebitdaAnnual.times(rating.limitMultiple)
        val fromEquity = Money(netWorth.coerceAtLeast(Money.ZERO).raw * rating.limitMultiple / 10L)
        val capacity = if (fromEarnings > fromEquity) fromEarnings else fromEquity
        val room = (capacity - existingDebt).coerceAtLeast(Money.ZERO)
        return CreditTerms(baseRate + rating.spread, room)
    }
}
