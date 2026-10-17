package app.many.economytycoon.ip

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

enum class Protection(val label: String) {
    PATENT("File a patent"),
    SECRET("Keep it secret"),
    NEITHER("Neither is worth it"),
}

/** What a company knows when it chooses between a patent and a secret for one technology. */
data class ChoiceInput(
    /** What exclusivity is worth per year while it lasts. */
    val annualAdvantage: Money,
    val grantChancePpm: Long,
    val grantedBreadthPpm: Long,
    val patentTermYears: Int,
    val examinationDays: Int,
    /** Fees of filing plus renewals over the expected life. */
    val patentLifetimeCost: Money,
    val knowers: Int,
    val exposurePpm: Long,
    val protectionPpm: Long,
    val discountRatePpm: Long,
)

data class ChoiceAdvice(
    val patentValue: Money,
    val secretValue: Money,
    val choice: Protection,
    val secretHazardPpm: Long,
    val secretHalfLifeDays: Long,
    val reasons: List<String>,
)

/**
 * Compares the two ways of protecting an invention. A patent is certain to last its term once granted but may be
 * refused, costs fees, and tells rivals how to copy the idea with small changes. A secret costs nothing and lasts
 * as long as it stays hidden, which for a widely known or visible technology is not long.
 */
object IpChoice {
    fun advise(input: ChoiceInput, secrets: SecretParams, office: OfficeParams): ChoiceAdvice {
        val hazard = SecretRules.dailyHazardPpm(secrets, input.knowers, input.exposurePpm, input.protectionPpm)
        val secretValue = secretValue(input, hazard, secrets)
        val patentValue = patentValue(input, secrets)
        val reasons = ArrayList<String>()
        val halfLife = if (hazard <= 0L) Long.MAX_VALUE / 4 else (693_147L / hazard)
        if (input.exposurePpm >= 600_000L) reasons += "Customers can see how the product is made, so a secret will be worked out."
        if (input.knowers >= 25) reasons += "Many people know it, which makes a leak likely."
        if (input.grantChancePpm < 400_000L) reasons += "The office is likely to refuse this application."
        if (input.grantedBreadthPpm >= 700_000L) reasons += "Broad claims would keep rivals well away."
        if (halfLife < Calendar.DAYS_PER_YEAR) reasons += "At this rate the secret is more likely gone than not within a year."
        val choice = when {
            patentValue <= Money.ZERO && secretValue <= Money.ZERO -> Protection.NEITHER
            patentValue >= secretValue -> Protection.PATENT
            else -> Protection.SECRET
        }
        return ChoiceAdvice(patentValue, secretValue, choice, hazard, halfLife, reasons)
    }

    private fun discount(valuePerYear: Money, years: Int, rate: Long, survivalByYear: (Int) -> Long): Money {
        var total = 0L
        var factor = 1_000_000L
        for (y in 1..years) {
            factor = mulDiv(factor, 1_000_000L, 1_000_000L + rate)
            total += mulDiv(mulDiv(valuePerYear.raw, survivalByYear(y), 1_000_000L), factor, 1_000_000L)
        }
        return Money(total)
    }

    fun secretValue(input: ChoiceInput, hazardPpm: Long, secrets: SecretParams): Money =
        discount(input.annualAdvantage, secrets.valueHorizonYears, input.discountRatePpm) { y -> SecretRules.survivalPpm(hazardPpm, y * Calendar.DAYS_PER_YEAR) }

    fun patentValue(input: ChoiceInput, secrets: SecretParams): Money {
        val waitYears = (input.examinationDays + Calendar.DAYS_PER_YEAR - 1) / Calendar.DAYS_PER_YEAR
        val inForce = (input.patentTermYears - waitYears).coerceAtLeast(0)
        // The claims only cover the part of the market their breadth reaches, and disclosure lets rivals invent around.
        val reach = input.grantedBreadthPpm.coerceIn(100_000L, 1_000_000L)
        val advantage = Money(mulDiv(input.annualAdvantage.raw, reach, 1_000_000L))
        val gross = discount(advantage, inForce, input.discountRatePpm) { y ->
            (1_000_000L - secrets.disclosureErosionPpm * y).coerceAtLeast(300_000L)
        }
        val expected = Money(mulDiv(gross.raw, input.grantChancePpm, 1_000_000L))
        return expected - input.patentLifetimeCost
    }
}
