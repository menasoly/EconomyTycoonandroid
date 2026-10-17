package app.many.economytycoon.session

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.carbon.CarbonAdvice
import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.CompanyCarbonView
import app.many.economytycoon.carbon.MarketView
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.simulation.CarbonCommandMap
import app.many.economytycoon.ui.present.CarbonDesk

/** The carbon screens' view of the running game for one company: reads the package's read models and sends commands the way every other screen does. */
class CarbonSessionDesk(private val session: GameSession, override val company: CompanyId) : CarbonDesk {
    private val e get() = session.simulation.economy

    override val tick: Tick get() = e.tick

    override fun money(m: Money): String = session.settings.money(m)

    override fun countryName(country: CountryId): String = e.world.countries().firstOrNull { it.id == country }?.name ?: "Country ${country.value}"

    override fun facilityName(facility: FacilityId): String =
        e.production.facility(facility)?.let { f -> f.name.ifBlank { e.content.facilityTypeOrNull(f.type)?.name ?: f.type.key.replace('_', ' ') } } ?: "Facility ${facility.value}"

    override fun view(): CompanyCarbonView? = if (e.carbonRunner.active) e.carbonViews.company(company, tick) else null

    override fun market(country: CountryId): MarketView? = if (e.carbonRunner.active) e.carbonViews.market(country, tick) else null

    /** What the company took in over the last year, which the advisor sets the cost of carbon against. */
    override fun revenue(): Money {
        val now = e.tick
        val from = Tick((now.value - YEAR_DAYS).coerceAtLeast(0L))
        return e.finance.statements.income(LedgerOwner.company(company), from, now).revenue
    }

    override fun submit(command: CarbonCommand): String? =
        when (val r = session.issueNow(CarbonCommandMap.toCommand(command))) {
            is Outcome.Ok -> null
            is Outcome.Rejected -> r.rejection.message
        }

    /** The carbon advisor's suggestions for the company; none while no government prices carbon. */
    fun insights(): List<Insight> = view()?.takeIf { it.accounts.isNotEmpty() }?.let { CarbonAdvice.insights(it, revenue()) }.orEmpty()

    private companion object {
        const val YEAR_DAYS = 360L
    }
}
