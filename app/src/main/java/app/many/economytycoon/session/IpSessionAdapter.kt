package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpModel
import app.many.economytycoon.ip.IpNames
import app.many.economytycoon.ip.IpOwner
import app.many.economytycoon.ip.IpViews
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.present.IpSessionView

/** The patent screens' view of the running game: reads the module's models for the selected company and sends commands the way every other screen does. */
class IpSessionAdapter(private val session: GameSession) : IpSessionView {
    private val e get() = session.simulation.economy

    override fun ipModel(): IpModel? {
        if (!e.ipRunner.active) return null
        val company = session.selectedCompany ?: return null
        return IpViews(e.ip).build(e.ipContext, company, e.tick)
    }

    override fun money(m: Money): String = session.settings.money(m)

    override fun companyName(id: CompanyId): String = e.companies.company(id)?.name ?: "Company ${id.value}"

    override fun countryName(id: CountryId): String = e.world.countries().firstOrNull { it.id == id }?.name ?: "Country ${id.value}"

    override fun npeName(owner: IpOwner): String? = owner.npeOrNull()?.let { e.ip.state.npe(it)?.name }

    override fun issue(command: IpCommand): String =
        when (val r = session.issueNow(Command.Ip(command))) {
            is Outcome.Ok -> r.value.message.ifBlank { "Done" }
            is Outcome.Rejected -> r.rejection.message
        }

    companion object {
        /** How the news and alerts name things. */
        fun names(e: app.many.economytycoon.simulation.Economy): IpNames = IpNames(
            { id -> e.companies.company(id)?.name ?: "A company" },
            { m -> Formatting.money(m) },
            { id -> e.ip.state.patent(id)?.title ?: "a patent" },
        )
    }
}
