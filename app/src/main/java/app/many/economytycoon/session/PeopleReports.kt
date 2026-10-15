package app.many.economytycoon.session

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Economy

data class CohortRow(
    val facility: FacilityId,
    val facilityName: String,
    val role: String,
    val headcount: Long,
    val target: Long,
    val wage: Money,
    val marketWage: Money,
    val moralePpm: Long,
    val skillPpm: Long,
    val burnoutPpm: Long,
    val unionized: Boolean,
    val onStrike: Boolean,
)

data class WorkforceSummary(
    val company: CompanyId,
    val headcount: Long,
    val vacancies: Long,
    val dailyPayroll: Money,
    val averageMoralePpm: Long,
    val averageSkillPpm: Long,
    val onStrike: Long,
    val unpaidTicks: Int,
    val benefitsPpm: Long,
    val reputationPpm: Long,
)

data class TechRow(
    val id: TechId,
    val name: String,
    val category: TechCategory,
    val tier: Int,
    val costPoints: Long,
    val minimumTicks: Long,
    val state: TechState,
    val progressPpm: Long,
    val missing: List<String>,
    val licenseFee: Money,
)

enum class TechState { UNLOCKED, RESEARCHING, QUEUED, AVAILABLE, LOCKED }

class ResearchSummary(
    val company: CompanyId,
    val points: Long,
    val unlocked: Int,
    val total: Int,
    val active: List<TechRow>,
    val queued: List<TechRow>,
)

/** Employment and research views for one company. */
class PeopleReports(private val e: Economy) {

    fun workforce(company: CompanyId): WorkforceSummary {
        val s = e.workforce.stats(company, e.tick)
        return WorkforceSummary(
            company, s.headcount, s.vacancies, s.dailyPayroll, s.averageMoralePpm, s.averageSkillPpm, s.onStrike, s.unpaidTicks,
            e.workforce.benefits(company), e.workforce.reputation(company),
        )
    }

    fun cohorts(company: CompanyId): List<CohortRow> =
        e.workforce.cohortsOf(company).sortedWith(compareBy({ it.key.facility }, { it.key.role })).map { c ->
            val facility = e.production.facility(c.key.facility)
            val role = e.catalog.role(c.key.role)
            CohortRow(
                facility = c.key.facility, facilityName = facility?.name?.ifBlank { null } ?: "Facility ${c.key.facility.value}",
                role = role?.name ?: Formatting.title(c.key.role.key), headcount = c.headcount, target = c.target, wage = c.wage,
                marketWage = e.laborMarkets.marketWage(app.many.economytycoon.workforce.MarketKey(c.key.region, c.key.role)),
                moralePpm = c.moralePpm, skillPpm = c.skillPpm, burnoutPpm = c.burnoutPpm, unionized = c.unionized,
                onStrike = c.onStrike(e.tick),
            )
        }

    fun research(company: CompanyId): ResearchSummary {
        val state = e.research.of(company)
        val active = state.projects().map { row(company, it.tech) }
        val queued = state.queued().map { row(company, it) }
        return ResearchSummary(
            company, e.companies.progress(company).researchPoints, state.unlockedTechs().size, e.content.allTechnologies.size, active, queued,
        )
    }

    fun techTree(company: CompanyId): List<TechRow> =
        e.content.allTechnologies.map { row(company, it.id) }.sortedWith(compareBy({ it.category.ordinal }, { it.tier }, { it.name }))

    fun row(company: CompanyId, id: TechId): TechRow {
        val def = e.content.technology(id)
        val state = e.research.of(company)
        val project = state.project(id)
        val status = when {
            state.isUnlocked(id) -> TechState.UNLOCKED
            project != null -> TechState.RESEARCHING
            id in state.queued() -> TechState.QUEUED
            e.research.missingPrerequisites(company, id).isEmpty() -> TechState.AVAILABLE
            else -> TechState.LOCKED
        }
        return TechRow(
            id = id, name = def.name, category = def.category, tier = def.tier, costPoints = e.research.costPoints(def),
            minimumTicks = e.research.minimumTicks(def), state = status,
            progressPpm = when (status) { TechState.UNLOCKED -> 1_000_000L; TechState.RESEARCHING -> project!!.progressPpm; else -> 0L },
            missing = e.research.missingPrerequisites(company, id).map { e.content.technology(it).name },
            licenseFee = e.research.licenseFee(def),
        )
    }

    fun unlockedAt(company: CompanyId, tech: TechId): Tick? = e.research.of(company).unlockedAt(tech)
}
