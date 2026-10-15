package app.many.economytycoon.health

enum class HealthSeverity { NOTE, WARNING, PROBLEM }

data class HealthIssue(val code: String, val severity: HealthSeverity, val message: String)

/** Limits that decide when a sample, or a run of them, is worth reporting. */
data class HealthLimits(
    /** Share of AI companies that may be short of cash before it is a warning. */
    val strainedShareWarn: Long = 400_000L,
    val strainedShareProblem: Long = 800_000L,
    /** Share of AI facilities that should be producing. */
    val operatingShareWarn: Long = 500_000L,
    /** Share of markets stuck far from their reference price. */
    val cheapShareWarn: Long = 300_000L,
    val dearShareWarn: Long = 300_000L,
    /** Yearly price drift of the median market, either way, before it is reported. */
    val medianDriftWarnPpm: Long = 500_000L,
    /** Money supply may move by this much, per year, without a matching mint, before it is a problem. */
    val supplyGrowthWarnPpm: Long = 2_000_000L,
)

/** Turns a series of samples into the issues a developer or the balance test should look at. */
object HealthRules {
    fun check(samples: List<HealthSample>, limits: HealthLimits = HealthLimits()): List<HealthIssue> {
        if (samples.isEmpty()) return emptyList()
        val out = ArrayList<HealthIssue>()
        val last = samples.last()
        val first = samples.first()

        if (last.aiCompanies > 0) {
            when {
                last.aiStrainedShare >= limits.strainedShareProblem ->
                    out += HealthIssue("ai.broke", HealthSeverity.PROBLEM, "${pct(last.aiStrainedShare)} of rival companies cannot cover a week of costs")
                last.aiStrainedShare >= limits.strainedShareWarn ->
                    out += HealthIssue("ai.strained", HealthSeverity.WARNING, "${pct(last.aiStrainedShare)} of rival companies hold under a week of costs")
            }
            if (last.aiWithoutCash == last.aiCompanies) out += HealthIssue("ai.empty", HealthSeverity.PROBLEM, "every rival company is out of cash")
        }
        if (last.aiFacilities > 0 && last.aiOperatingShare < limits.operatingShareWarn)
            out += HealthIssue("ai.idle", HealthSeverity.WARNING, "only ${pct(last.aiOperatingShare)} of rival facilities are producing")
        if (last.cheapShare >= limits.cheapShareWarn)
            out += HealthIssue("price.floor", HealthSeverity.WARNING, "${pct(last.cheapShare)} of markets trade below half their reference price")
        if (last.dearShare >= limits.dearShareWarn)
            out += HealthIssue("price.runaway", HealthSeverity.WARNING, "${pct(last.dearShare)} of markets trade above double their reference price")
        val days = last.tick - first.tick
        if (days >= 90L) {
            val drift = (last.medianPriceRatioPpm - first.medianPriceRatioPpm) * 360L / days
            if (Math.abs(drift) > limits.medianDriftWarnPpm)
                out += HealthIssue("price.drift", HealthSeverity.WARNING, "the median price moved ${signed(drift)} a year")
            if (first.moneySupply > 0L) {
                val growth = (last.moneySupply - first.moneySupply) * 1_000_000L / first.moneySupply * 360L / days
                if (growth > limits.supplyGrowthWarnPpm) out += HealthIssue("money.growth", HealthSeverity.WARNING, "money supply grew ${signed(growth)} a year")
            }
        }
        if (last.householdCash <= 0L && last.markets > 0) out += HealthIssue("households.broke", HealthSeverity.PROBLEM, "households have no cash left to spend")
        return out
    }

    private fun pct(ppm: Long) = "${ppm / 10_000}%"
    private fun signed(ppm: Long) = (if (ppm > 0) "+" else "") + ppm / 10_000 + "%"
}
