package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.workforce.CohortKey

/** What the talent module needs to know about one workforce cohort today. */
data class CohortFact(
    val key: CohortKey,
    val headcount: Long,
    val target: Long,
    val wage: Money,
    val marketWage: Money,
    val moralePpm: Long,
    val tenureTicks: Long,
    val loadPpm: Long = Ppm.ONE,
) {
    val openings: Long get() = (target - headcount).coerceAtLeast(0L)
    val wageRatioPpm: Long get() = if (marketWage.raw <= 0L) Ppm.ONE else Ppm.ratio(wage.raw, marketWage.raw)
}

data class CompanyFact(val company: CompanyId, val cash: Money, val computerControlled: Boolean = false)

/** The day's facts from the rest of the game, gathered by the caller before [TalentSystem.advance]. */
data class TalentInputs(
    val tick: Tick,
    val cohorts: List<CohortFact>,
    val companies: List<CompanyFact> = emptyList(),
) {
    private val byKey = cohorts.associateBy { it.key }
    private val cash = companies.associate { it.company to it.cash }

    fun cohort(key: CohortKey): CohortFact? = byKey[key]
    fun cashOf(company: CompanyId): Money = cash[company] ?: Money.ZERO
    fun hasCompany(company: CompanyId): Boolean = cash.containsKey(company)
}

/**
 * Changes the talent module asks the workforce module to make. The module has already moved the matching skill
 * distributions; the integration applies the head counts and wages so the two stay in step.
 */
sealed class WorkforceEffect {
    /** [count] people leave [from] and join [to] at [newWage] (poaching, promotion, internal moves). */
    data class Transfer(val from: CohortKey, val to: CohortKey, val count: Long, val newWage: Money?) : WorkforceEffect()

    /** [count] graduates join a cohort at once, at the going wage. */
    data class Hire(val cohort: CohortKey, val count: Long) : WorkforceEffect()

    /** A cohort's wage is raised (counter-offer, retention). */
    data class RaiseWage(val cohort: CohortKey, val newWage: Money) : WorkforceEffect()

    data class Morale(val cohort: CohortKey, val deltaPpm: Long) : WorkforceEffect()

    /** Share of the cohort currently away on training, 0..1,000,000. */
    data class TrainingShare(val cohort: CohortKey, val ppm: Long) : WorkforceEffect()

    /** [count] people leave a cohort for good (retirement, a key person quitting). */
    data class Depart(val cohort: CohortKey, val count: Long) : WorkforceEffect()

    /** [count] people emigrate from an employed cohort. */
    data class Emigrate(val cohort: CohortKey, val count: Long) : WorkforceEffect()
}

/** Everything a day of talent produced. The caller posts the intents, applies the effects and publishes the events. */
data class TalentReport(
    val tick: Tick,
    val events: List<TalentEvent>,
    val intents: List<SettlementIntent>,
    val effects: List<WorkforceEffect>,
) {
    val isQuiet: Boolean get() = events.isEmpty() && intents.isEmpty() && effects.isEmpty()
}
