package app.many.economytycoon.carbon

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/** What an intensity standard makes of a company's year. */
data class IntensityAssessment(val allowed: Tonnes, val actual: Tonnes, val excess: Tonnes, val levy: Money) {
    val withinBenchmark: Boolean get() = excess.raw <= 0L
    /** Room left under the benchmark, zero when over it. */
    val headroom: Tonnes get() = (allowed - actual).coerceAtLeast(Tonnes.ZERO)
}

/** The arithmetic of carbon taxes and intensity levies. */
object Levies {

    /**
     * Carbon tax due now. Below the step's threshold nothing is owed; once the year's covered emissions pass it the
     * whole amount is taxed, and what has been assessed so far this year is netted off, so that the sum over a year is
     * exactly the rate times the covered emissions and an exempt company that grows past the threshold catches up.
     */
    fun taxDue(step: RegimeStep, year: Int, ytdCovered: Tonnes, assessed: Money): Money {
        if (!ytdCovered.isPositive || ytdCovered < step.threshold) return Money.ZERO
        val gross = ytdCovered.cost(step.rateIn(year))
        return (gross - assessed).coerceAtLeast(Money.ZERO)
    }

    /** Emissions a company may release in [year] with [output] of production under the step's benchmark. */
    fun allowed(step: RegimeStep, year: Int, output: Money): Tonnes {
        if (output.raw <= 0L) return Tonnes.ZERO
        val grams = mulDiv(step.benchmarkIn(year), output.raw, Money.SCALE)
        return Tonnes.ofGrams(grams)
    }

    /** The benchmark test for a year: covered emissions against what the output allows, with the levy on any excess. */
    fun assess(step: RegimeStep, year: Int, covered: Tonnes, output: Money): IntensityAssessment {
        if (covered < step.threshold) return IntensityAssessment(allowed(step, year, output), covered, Tonnes.ZERO, Money.ZERO)
        val allowed = allowed(step, year, output)
        val excess = (covered - allowed).coerceAtLeast(Tonnes.ZERO)
        return IntensityAssessment(allowed, covered, excess, excess.cost(step.rateIn(year)))
    }

    /** The fine for [shortfall]: the step's penalty per tonne, raised for each year in a row of non-compliance (at most five steps). */
    fun penalty(step: RegimeStep, regime: CarbonRegime, shortfall: Tonnes, priorViolations: Int): Money {
        if (!shortfall.isPositive) return Money.ZERO
        val steps = priorViolations.coerceIn(0, MAX_REPEAT_STEPS).toLong()
        val multiplierPpm = 1_000_000L + regime.repeatPenaltyStepPpm * steps
        val perTonne = Money(mulDiv(step.penalty.raw, multiplierPpm, 1_000_000L))
        return shortfall.cost(perTonne)
    }

    const val MAX_REPEAT_STEPS = 5
}
