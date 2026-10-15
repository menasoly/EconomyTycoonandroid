package app.many.economytycoon.ui.present

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.TechRow
import app.many.economytycoon.session.TechState

data class CohortItem(
    val facility: FacilityId,
    val role: String,
    val where: String,
    val staffing: String,
    val staffingTone: Tone,
    val wage: String,
    val wageNote: String?,
    val morale: Double,
    val moraleTone: Tone,
    val flags: List<String>,
)

data class PeopleModel(val tiles: List<Tile>, val cohorts: List<CohortItem>, val warnings: List<String>)

object PeoplePresenter {
    fun build(session: GameSession): PeopleModel? {
        val company = session.selectedCompany ?: return null
        val money = session.settings::money
        val w = session.reports.people.workforce(company)
        val tiles = listOf(
            Tile("Employees", Formatting.grouped(w.headcount), note = if (w.vacancies > 0) "${w.vacancies} vacancies" else "fully staffed"),
            Tile("Payroll", money(w.dailyPayroll), note = "per day"),
            Tile("Morale", Formatting.percentPpm(w.averageMoralePpm), Tones.health(w.averageMoralePpm)),
            Tile("Skill", Formatting.percentPpm(w.averageSkillPpm)),
            Tile("Benefits", Formatting.percentPpm(w.benefitsPpm), note = "of market norm"),
            Tile("Reputation", Formatting.percentPpm(w.reputationPpm), Tones.health(w.reputationPpm), "as an employer"),
        )
        val cohorts = session.reports.people.cohorts(company).map { c ->
            val flags = ArrayList<String>()
            if (c.onStrike) flags += "On strike"
            if (c.unionized) flags += "Unionised"
            if (c.burnoutPpm > 600_000L) flags += "Burnt out"
            val staffTone = when {
                c.onStrike -> Tone.BAD
                c.headcount < c.target -> Tone.WARN
                else -> Tone.GOOD
            }
            val gap = c.marketWage.raw - c.wage.raw
            CohortItem(
                c.facility, Formatting.title(c.role), c.facilityName, "${c.headcount} of ${c.target}", staffTone, money(c.wage),
                if (gap > c.marketWage.raw / 10 && c.marketWage.raw > 0) "Market pays ${money(c.marketWage)}" else null,
                c.moralePpm / 1_000_000.0, Tones.health(c.moralePpm), flags,
            )
        }
        val warnings = ArrayList<String>()
        if (w.onStrike > 0) warnings += "${w.onStrike} workers are on strike."
        if (w.unpaidTicks > 0) warnings += "Wages have gone unpaid for ${Formatting.plural(w.unpaidTicks.toLong(), "day")}."
        if (w.vacancies > 0) warnings += "${Formatting.plural(w.vacancies, "position")} could not be filled."
        return PeopleModel(tiles, cohorts.sortedWith(compareBy({ it.where }, { it.role })), warnings)
    }
}

data class TechItem(
    val id: TechId,
    val name: String,
    val tier: Int,
    val category: String,
    val state: TechState,
    val stateLabel: String,
    val tone: Tone,
    val cost: String,
    val progress: Double,
    val missing: List<String>,
    val canStart: Boolean,
    val canQueue: Boolean,
    val canCancel: Boolean,
)

data class ResearchModel(val tiles: List<Tile>, val active: List<TechItem>, val queued: List<TechItem>, val tree: List<TechItem>, val categories: List<TechCategory?>)

object ResearchPresenter {
    fun stateLabel(s: TechState): String = when (s) {
        TechState.UNLOCKED -> "Unlocked"
        TechState.RESEARCHING -> "Researching"
        TechState.QUEUED -> "Queued"
        TechState.AVAILABLE -> "Ready to research"
        TechState.LOCKED -> "Locked"
    }

    fun tone(s: TechState): Tone = when (s) {
        TechState.UNLOCKED -> Tone.GOOD
        TechState.RESEARCHING -> Tone.INFO
        TechState.QUEUED -> Tone.INFO
        TechState.AVAILABLE -> Tone.NEUTRAL
        TechState.LOCKED -> Tone.MUTED
    }

    fun item(row: TechRow): TechItem = TechItem(
        id = row.id, name = row.name, tier = row.tier, category = Formatting.title(row.category.name.lowercase()), state = row.state, stateLabel = stateLabel(row.state),
        tone = tone(row.state), cost = Formatting.grouped(row.costPoints) + " points", progress = row.progressPpm / 1_000_000.0, missing = row.missing,
        canStart = row.state == TechState.AVAILABLE, canQueue = row.state == TechState.AVAILABLE || row.state == TechState.LOCKED && row.missing.isEmpty(),
        canCancel = row.state == TechState.RESEARCHING || row.state == TechState.QUEUED,
    )

    fun build(session: GameSession, category: TechCategory? = null, hideLocked: Boolean = false): ResearchModel? {
        val company = session.selectedCompany ?: return null
        val summary = session.reports.people.research(company)
        val tree = session.reports.people.techTree(company)
            .filter { (category == null || it.category == category) && (!hideLocked || it.state != TechState.LOCKED) }
            .sortedWith(compareBy({ it.tier }, { it.name })).map(::item)
        val tiles = listOf(
            Tile("Research points", Formatting.grouped(summary.points), note = "earned so far"),
            Tile("Unlocked", "${summary.unlocked}/${summary.total}"),
            Tile("In progress", summary.active.size.toString(), if (summary.active.isEmpty()) Tone.WARN else Tone.INFO, if (summary.active.isEmpty()) "nothing being researched" else null),
            Tile("Queued", summary.queued.size.toString()),
        )
        return ResearchModel(tiles, summary.active.map(::item), summary.queued.map(::item), tree, listOf<TechCategory?>(null) + TechCategory.values().toList())
    }
}
