package app.many.economytycoon.session

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.talent.TalentAdvisor
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.TalentNames
import app.many.economytycoon.talent.TalentReader
import app.many.economytycoon.talent.TalentView

/**
 * The people and skills screens' view of the running game: reads the module's models for the selected company and sends
 * orders the way every other screen does. It shows nothing until skills and education have been switched on.
 */
class TalentSessionDesk(private val session: GameSession) {
    private val e get() = session.simulation.economy

    private val names = object : TalentNames {
        override fun region(id: RegionId): String = e.world.regions().firstOrNull { it.id == id }?.name ?: "Region ${id.value}"
        override fun company(id: CompanyId): String = e.companies.company(id)?.name ?: "Company ${id.value}"
        override fun country(id: CountryId): String = e.world.countries().firstOrNull { it.id == id }?.name ?: "Country ${id.value}"
    }

    private val reader by lazy { TalentReader(e.talent, names) }

    /** True once skills and education are switched on; older saves and test worlds have no such thing. */
    val active: Boolean get() = e.talentRunner.active

    /** The selected company's teams, schools, training, offers, brand, careers and providers; null before there is a company or the module. */
    fun view(): TalentView? {
        if (!active) return null
        val company = session.selectedCompany ?: return null
        return reader.view(company, e.tick, e.talentRunner.inputs(e.tick), e.talentRunner.world)
    }

    /** The people advisor's suggestions for the selected company, most pressing first. */
    fun advice(limit: Int = Int.MAX_VALUE): List<Insight> {
        if (!active) return emptyList()
        val company = session.selectedCompany ?: return emptyList()
        return TalentAdvisor(e.talent).advise(company, e.tick, e.talentRunner.inputs(e.tick), e.talentRunner.world, limit)
    }

    /** Sends one order for the selected company and returns what to tell the player: the receipt's note or the reason for refusal. */
    fun issue(command: TalentCommand, done: String = "Done."): String {
        val company = session.selectedCompany ?: return "Choose a company first."
        return when (val r = session.issueNow(Command.Talent(company, command))) {
            is Outcome.Ok -> r.value.message.ifBlank { done }
            is Outcome.Rejected -> r.rejection.message
        }
    }
}
