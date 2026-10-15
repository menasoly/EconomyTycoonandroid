package app.many.economytycoon.macro

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DeterministicRandom

/**
 * Latent confidence of households and firms. It drifts back toward a neutral level, is pushed by random
 * news and by recent growth, and is depressed by high interest rates. Phases follow from its level and slope.
 */
class BusinessCycle(val country: CountryId) {
    var confidencePpm: Long = 550_000L
        private set
    private var previous: Long = 550_000L
    var phase: CyclePhase = CyclePhase.EXPANSION
        private set
    /** Decaying one-off impact in confidence points, set by events. */
    private var shock: Long = 0L

    /** Applies a lasting confidence shock; positive is good news. */
    fun shock(ppm: Long) { shock += ppm }

    fun step(random: DeterministicRandom, growthPpm: Long, policyRatePpm: Long, neutralRatePpm: Long): CyclePhase {
        previous = confidencePpm
        val noise = random.nextInRange(-30_000L, 30_000L)
        val pull = (500_000L - confidencePpm) * 8 / 100
        val momentum = growthPpm / 4
        val drag = -(policyRatePpm - neutralRatePpm) / 3
        val impact = shock / 4
        shock -= impact
        confidencePpm = (confidencePpm + pull + noise + momentum + drag + impact).coerceIn(50_000L, 950_000L)
        phase = classify(confidencePpm, confidencePpm - previous)
        return phase
    }

    fun slope(): Long = confidencePpm - previous

    /** Household demand multiplier implied by confidence, ppm. */
    fun demandShiftPpm(): Long = (1_000_000L + (confidencePpm - 500_000L) / 5).coerceIn(850_000L, 1_150_000L)

    private fun classify(level: Long, slope: Long): CyclePhase = when {
        level >= 600_000L && slope >= 0 -> CyclePhase.EXPANSION
        level >= 600_000L -> CyclePhase.PEAK
        level <= 400_000L && slope <= 0 -> CyclePhase.CONTRACTION
        level <= 400_000L -> CyclePhase.TROUGH
        slope >= 0 -> CyclePhase.EXPANSION
        else -> CyclePhase.CONTRACTION
    }

    internal fun restore(confidence: Long, previous: Long, phase: CyclePhase, shock: Long) {
        confidencePpm = confidence; this.previous = previous; this.phase = phase; this.shock = shock
    }
    internal val previousValue get() = previous
    internal val shockValue get() = shock

    fun hashInto(h: CanonicalHasher) { h.putLong(country.value).putLong(confidencePpm).putLong(previous).putInt(phase.ordinal).putLong(shock) }
}
