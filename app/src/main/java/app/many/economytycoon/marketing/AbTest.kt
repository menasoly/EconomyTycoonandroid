package app.many.economytycoon.marketing

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.isqrt
import app.many.economytycoon.core.mulDiv

enum class AbOutcome(val label: String) {
    RUNNING("Running"),
    A_WINS("Current creative wins"),
    B_WINS("New creative wins"),
    INCONCLUSIVE("No clear winner"),
    CANCELLED("Cancelled"),
}

/**
 * A split test of the creative a campaign runs against an alternative. Half the audience sees each. The result is
 * drawn from a stream derived from the world seed and the test id, so the same game always gives the same verdict.
 */
class AbTest(
    val id: AbTestId,
    val campaign: CampaignId,
    val qualityA: Long,
    val qualityB: Long,
    val startTick: Tick,
    val days: Int,
) {
    var outcome: AbOutcome = AbOutcome.RUNNING
        internal set
    var impressions: Long = 0L
        internal set
    var responsesA: Long = 0L
        internal set
    var responsesB: Long = 0L
        internal set
    /** The z-score of the gap in hundredths; positive favours B. */
    var zHundredths: Long = 0L
        internal set
    var decidedAt: Tick? = null
        internal set

    val endTick: Tick get() = startTick + days.toLong()
    val isRunning: Boolean get() = outcome == AbOutcome.RUNNING

    /** The quality the campaign runs at while the test is on: the audience is split evenly. */
    val blendedQuality: Long get() = (qualityA + qualityB) / 2L

    internal fun restore(outcome: AbOutcome, impressions: Long, a: Long, b: Long, z: Long, decidedAt: Tick?) {
        this.outcome = outcome; this.impressions = impressions; responsesA = a; responsesB = b; zHundredths = z; this.decidedAt = decidedAt
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(campaign.value).putLong(qualityA).putLong(qualityB).putLong(startTick.value).putInt(days)
            .putInt(outcome.ordinal).putLong(impressions).putLong(responsesA).putLong(responsesB).putLong(zHundredths).putLong(decidedAt?.value ?: -1L)
    }
}

object AbTesting {
    /** Share of people who respond to an advertisement of middling quality that is completely fresh. */
    const val BASE_RESPONSE_PPM = 18_000L

    /** Chance one viewer responds to a creative of [qualityPpm]. */
    fun responseRatePpm(qualityPpm: Long, params: MarketingParams): Long =
        Ppm.mul(BASE_RESPONSE_PPM, ReachModel.creativeMultiplierPpm(qualityPpm, params))

    fun seedFor(worldSeed: Long, test: AbTestId): Long = RandomProvider.deriveSeed(worldSeed, DEFAULT_STREAM, test.value)

    /**
     * Plays the test out. Each arm gets half the impressions; the number of responses is its expected count plus a
     * normal-ish draw scaled to the binomial spread. A winner needs the gap to reach the configured confidence.
     */
    fun resolve(test: AbTest, worldSeed: Long, params: MarketingParams): AbOutcome {
        val rnd = DeterministicRandom(seedFor(worldSeed, test.id))
        val perArm = test.impressions / 2L
        val pA = responseRatePpm(test.qualityA, params)
        val pB = responseRatePpm(test.qualityB, params)
        val a = drawResponses(perArm, pA, rnd.nextGaussianPpm())
        val b = drawResponses(perArm, pB, rnd.nextGaussianPpm())
        test.responsesA = a
        test.responsesB = b
        test.zHundredths = zHundredths(perArm, a, b)
        test.outcome = when {
            perArm <= 0L || a + b < params.abMinResponses -> AbOutcome.INCONCLUSIVE
            test.zHundredths >= params.abConfidenceHundredths -> AbOutcome.B_WINS
            test.zHundredths <= -params.abConfidenceHundredths -> AbOutcome.A_WINS
            else -> AbOutcome.INCONCLUSIVE
        }
        return test.outcome
    }

    private fun drawResponses(n: Long, pPpm: Long, gaussPpm: Long): Long {
        if (n <= 0L) return 0L
        val mean = mulDiv(n, pPpm, Ppm.ONE)
        val variance = mulDiv(mulDiv(n, pPpm, Ppm.ONE), Ppm.ONE - pPpm, Ppm.ONE)
        val sd = isqrt(variance.coerceAtLeast(0L))
        return (mean + mulDiv(sd, gaussPpm, Ppm.ONE)).coerceIn(0L, n)
    }

    /** The two-proportion z-score in hundredths. Positive means arm B did better. */
    fun zHundredths(perArm: Long, a: Long, b: Long): Long {
        if (perArm <= 0L) return 0L
        val pooled = a + b
        val total = 2L * perArm
        // variance of the difference in counts under the pooled rate: 2 * n * p * (1 - p)
        val variance = mulDiv(mulDiv(2L * perArm, pooled, total), total - pooled, total)
        val sd = isqrt(variance.coerceAtLeast(1L))
        return mulDiv(b - a, 100L, sd)
    }

    /** Impressions needed per arm before a given relative gap in response can show up at the configured confidence. */
    fun impressionsNeeded(qualityA: Long, qualityB: Long, params: MarketingParams): Long {
        val pA = responseRatePpm(qualityA, params)
        val pB = responseRatePpm(qualityB, params)
        val gap = Math.abs(pB - pA)
        if (gap <= 0L) return Long.MAX_VALUE
        val p = (pA + pB) / 2L
        // n >= 2 p (1 - p) z^2 / gap^2, with z in hundredths and p, gap in ppm
        val z = params.abConfidenceHundredths
        val pq = mulDiv(p, Ppm.ONE - p, Ppm.ONE)
        return mulDiv(mulDiv(2L * pq, z * z, 10_000L), Ppm.ONE, (gap * gap).coerceAtLeast(1L))
    }

    const val DEFAULT_STREAM = "marketing.abtest"
}
