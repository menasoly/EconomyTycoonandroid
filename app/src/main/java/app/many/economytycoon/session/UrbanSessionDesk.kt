package app.many.economytycoon.session

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.urban.InsightSeverity
import app.many.economytycoon.urban.OverlayKind
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.UrbanOverlay
import app.many.economytycoon.urban.UrbanReader
import app.many.economytycoon.urban.UrbanView

/**
 * The city planning screens' view of the running game: reads the module's models and sends orders the way every other
 * screen does. It shows nothing until the cities have been planned.
 */
class UrbanSessionDesk(private val session: GameSession) {
    private val e get() = session.simulation.economy

    /** True once the cities have plans; older saves and test worlds have none. */
    val active: Boolean get() = e.urbanRunner.active

    fun view(): UrbanView = UrbanReader.view(e.urbanService, e.tick)

    fun overlay(city: CityId, kind: OverlayKind): UrbanOverlay? = UrbanReader.overlay(e.urbanService, city, kind, e.tick)

    /** Sends one order for the selected company and returns what to tell the player: the receipt's message or the reason for refusal. */
    fun issue(command: UrbanCommand): String {
        val company = session.selectedCompany ?: return "Choose a company first."
        return when (val r = session.issueNow(Command.Urban(company, onBehalfOf(company, command)))) {
            is Outcome.Ok -> r.value.message.ifBlank { "Done" }
            is Outcome.Rejected -> r.rejection.message
        }
    }

    /** A withdrawal that names no applicant, for a petition the company itself filed, is made in the company's name. */
    private fun onBehalfOf(company: CompanyId, command: UrbanCommand): UrbanCommand {
        if (command !is UrbanCommand.WithdrawPetition || command.applicant != null) return command
        val filed = e.urban.plan(command.city)?.petitions?.get(command.petition)
        return if (filed?.applicant == company) command.copy(applicant = company) else command
    }

    /** The city the selected company's headquarters stands in, which its player governs, if it has a plan. */
    fun governedCity(): CityId? {
        if (!active) return null
        val company = session.selectedCompany ?: return null
        val city = e.companies.company(company)?.hq ?: return null
        return city.takeIf { e.urbanRunner.governs(company, it) && e.urban.has(it) }
    }

    /** The planner's warnings about the city the selected company governs, as advisor suggestions. */
    fun insights(): List<Insight> {
        val city = governedCity() ?: return emptyList()
        return e.urbanService.advice(city, e.tick)
            .filter { it.severity == InsightSeverity.WARNING || it.severity == InsightSeverity.CRITICAL }
            .map { i ->
                val priority = if (i.severity == InsightSeverity.CRITICAL) 85 else 60
                Insight("urban.${i.category.name.lowercase()}.${i.district ?: "city"}", Topic.PROPERTY, priority, i.title, i.detail)
            }
    }
}
