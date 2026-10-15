package app.many.economytycoon.ui.present

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.session.BuildOption
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.simulation.Command

data class DepositItem(val id: DepositId, val title: String, val detail: String, val tone: Tone, val canSurvey: Boolean, val canClaim: Boolean, val yours: Boolean)

data class BuildItem(
    val type: FacilityTypeId,
    val name: String,
    val family: String,
    val cost: String,
    val summary: String,
    val outputs: String,
    val blockers: List<String>,
    val canBuild: Boolean,
    val deposits: List<DepositItem>,
)

data class BuildModel(val region: String, val cash: String, val families: List<IndustryFamily?>, val items: List<BuildItem>)

object BuildPresenter {
    fun familyLabel(f: IndustryFamily?): String = f?.let { Formatting.title(it.name.lowercase()) } ?: "All"

    fun item(session: GameSession, o: BuildOption): BuildItem {
        val money = session.settings::money
        val deposits = o.deposits.map { d ->
            DepositItem(
                d.id, "${d.good} deposit", (if (d.surveyed) "${Formatting.quantity(d.remaining)} left · grade ${Formatting.percentPpm(d.gradePpm)}" else "Not yet surveyed") + if (d.ownedByYou) " · yours" else "",
                if (d.ownedByYou) Tone.GOOD else Tone.NEUTRAL, canSurvey = !d.surveyed, canClaim = !d.ownedByYou && d.surveyed, yours = d.ownedByYou,
            )
        }
        return BuildItem(
            o.type, o.name, familyLabel(o.family), money(o.cost),
            "${Formatting.duration(o.buildTicks.toLong())} to build · ${o.lines} production lines · upkeep ${money(o.upkeepPerTick)} a day",
            if (o.outputs.isEmpty()) "Makes nothing directly" else "Makes " + o.outputs.joinToString(", "),
            o.blockers, o.canBuild, deposits,
        )
    }

    fun build(session: GameSession, region: RegionId, family: IndustryFamily? = null, onlyBuildable: Boolean = false): BuildModel? {
        val company = session.selectedCompany ?: return null
        val regionRow = session.economy.world.regions().firstOrNull { it.id == region } ?: return null
        val cash = session.reports.company.headline(company)?.cash ?: return null
        val items = session.reports.planner.options(company, region)
            .filter { (family == null || it.family == family) && (!onlyBuildable || it.canBuild) }.map { item(session, it) }
        return BuildModel(regionRow.name, session.settings.money(cash), listOf<IndustryFamily?>(null) + IndustryFamily.values().toList(), items)
    }

    /** The command for building [type] in [region], or null when blocked. */
    fun command(session: GameSession, type: FacilityTypeId, region: RegionId, name: String): Command.Build? {
        val company = session.selectedCompany ?: return null
        val option = session.reports.planner.options(company, region).firstOrNull { it.type == type } ?: return null
        return session.reports.planner.buildCommand(company, option, region, name.trim())
    }

    fun surveyCommand(session: GameSession, deposit: DepositId): Command? = session.selectedCompany?.let { Command.SurveyDeposit(it, deposit) }

    fun claimCommand(session: GameSession, deposit: DepositId): Command? = session.selectedCompany?.let { Command.ClaimDeposit(it, deposit) }
}
