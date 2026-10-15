package app.many.economytycoon.macro

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId

enum class RateMode { RULE, MANUAL }

/** Inputs of one policy decision. */
class PolicyInputs(val inflationPpm: Long, val outputGapPpm: Long, val unemploymentPpm: Long)

/**
 * A central bank per country following a Taylor-type rule: the neutral real rate plus inflation, leaning
 * against deviations of inflation from target and of output from potential. Moves are limited to a step per
 * review and the rate stays between a floor and a ceiling.
 */
class CentralBank(val country: CountryId, private val params: MacroParams, startRatePpm: Long = 40_000L) {
    var ratePpm: Long = startRatePpm
        private set
    var mode: RateMode = RateMode.RULE
        private set
    private var manualTarget: Long = startRatePpm

    /** Rate the rule would set with no step limit. */
    fun ruleRate(input: PolicyInputs): Long {
        val inflationGap = input.inflationPpm - params.inflationTargetPpm
        val raw = params.neutralRealRatePpm + input.inflationPpm + inflationGap / 2 + input.outputGapPpm / 2
        return raw.coerceIn(params.minPolicyRatePpm, params.maxPolicyRatePpm)
    }

    fun setManual(targetPpm: Long) {
        manualTarget = targetPpm.coerceIn(params.minPolicyRatePpm, params.maxPolicyRatePpm)
        mode = RateMode.MANUAL
    }

    fun useRule() { mode = RateMode.RULE }

    /** Reviews the rate. Returns the change in ppm. */
    fun review(input: PolicyInputs): Long {
        val target = if (mode == RateMode.MANUAL) manualTarget else ruleRate(input)
        val step = (target - ratePpm).coerceIn(-params.maxRateStepPpm, params.maxRateStepPpm)
        ratePpm = (ratePpm + step).coerceIn(params.minPolicyRatePpm, params.maxPolicyRatePpm)
        return step
    }

    internal fun restore(rate: Long, mode: RateMode, manual: Long) { ratePpm = rate; this.mode = mode; manualTarget = manual }
    internal val manualValue get() = manualTarget

    fun hashInto(h: CanonicalHasher) { h.putLong(country.value).putLong(ratePpm).putInt(mode.ordinal).putLong(manualTarget) }
}
