package app.many.economytycoon.workforce

import app.many.economytycoon.core.mulDiv

data class MoraleInputs(
    /** Contract wage divided by market wage, ppm. */
    val wageRatioPpm: Long,
    /** Consecutive ticks wages have been late. */
    val unpaidTicks: Int,
    val loadPpm: Long,
    /** Extra benefits spend as a share of wages, ppm. */
    val benefitsPpm: Long,
    /** Share of the cohort laid off recently, ppm. */
    val layoffShockPpm: Long,
    val tenureTicks: Long,
    val onStrike: Boolean,
)

/** Pure formulas for morale, burnout, productivity and attrition. All fixed-point. */
object Morale {
    const val ONE = 1_000_000L

    fun target(i: MoraleInputs): Long {
        var t = 550_000L
        t += ((i.wageRatioPpm - ONE) / 2).coerceIn(-250_000L, 200_000L)
        t += (i.benefitsPpm).coerceAtMost(100_000L)
        t -= (i.unpaidTicks * 30_000L).coerceAtMost(400_000L)
        t -= ((i.loadPpm - 1_050_000L).coerceAtLeast(0L) * 8 / 10).coerceAtMost(250_000L)
        t -= (i.layoffShockPpm * 3 / 2).coerceAtMost(150_000L)
        t += (i.tenureTicks / 360L * 20_000L).coerceAtMost(80_000L)
        if (i.onStrike) t -= 100_000L
        return t.coerceIn(0L, ONE)
    }

    /** Morale moves 8% of the way to its target each tick, at least one unit. */
    fun step(current: Long, target: Long): Long {
        val delta = target - current
        if (delta == 0L) return current
        val move = delta * 8 / 100
        val applied = if (move == 0L) (if (delta > 0) 1L else -1L) else move
        return (current + applied).coerceIn(0L, ONE)
    }

    fun burnoutStep(burnout: Long, loadPpm: Long, morale: Long): Long {
        val rise = if (loadPpm > 1_100_000L) (loadPpm - 1_100_000L) / 50L else 0L
        val fall = if (loadPpm <= 1_000_000L) 1_500L + (morale / 1_000L) else 0L
        return (burnout + rise - fall).coerceIn(0L, ONE)
    }

    /** Output per head relative to a reference worker, ppm: 0.6..1.4 from skill, 0.5..1.2 from morale. */
    fun productivity(skillPpm: Long, moralePpm: Long, burnoutPpm: Long, onStrike: Boolean): Long {
        if (onStrike) return 0L
        val skillFactor = 600_000L + mulDiv(800_000L, skillPpm, ONE)
        val moraleFactor = 500_000L + mulDiv(700_000L, moralePpm, ONE)
        val burnoutFactor = ONE - mulDiv(400_000L, burnoutPpm, ONE)
        return mulDiv(mulDiv(skillFactor, moraleFactor, ONE), burnoutFactor, ONE)
    }

    /** Share of the cohort expected to quit each tick, ppm. Baseline is 12% a year. */
    fun quitRatePpm(wageRatioPpm: Long, moralePpm: Long, burnoutPpm: Long): Long {
        val wageGap = (ONE - wageRatioPpm).coerceAtLeast(0L)
        val lowMorale = (600_000L - moralePpm).coerceAtLeast(0L)
        val mult = (ONE + wageGap * 3 + lowMorale * 5 + burnoutPpm).coerceIn(400_000L, 8_000_000L)
        return mulDiv(333L, mult, ONE)
    }

    /** Relative appeal of an employer for applicants given pay and reputation, ppm (about 1,000,000 at parity). */
    fun appeal(wageRatioPpm: Long, reputationPpm: Long): Long {
        val w = wageRatioPpm.coerceIn(400_000L, 1_800_000L)
        val wageWeight = mulDiv(w, w, ONE)
        return mulDiv(wageWeight, 800_000L + reputationPpm / 5L, ONE).coerceAtLeast(10_000L)
    }

    /** Skill gained per tick when training: a full-time trainee gains a tier in about 2x the training time. */
    fun skillGain(trainingTicks: Int, intensityPpm: Long): Long =
        mulDiv(ONE, intensityPpm, ONE) / (trainingTicks.coerceAtLeast(1) * 2L)
}
