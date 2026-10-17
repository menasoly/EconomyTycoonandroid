package app.many.economytycoon.ui.present

import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.CarbonTestSupport
import app.many.economytycoon.carbon.CompanyCarbonView
import app.many.economytycoon.carbon.MarketView
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting

/** A desk backed by a real service on the carbon test fixture, so the presenters are tried on real read models. */
internal class CarbonDeskFixture(
    val fx: CarbonTestSupport.Fixture,
    override val company: CompanyId = CarbonTestSupport.acme,
    day: Long = 470,
) : CarbonDesk {
    override var tick: Tick = Tick(day)
    val submitted = ArrayList<CarbonCommand>()
    var refusal: String? = null
    var revenue: Money = Money.units(1_000_000)

    override fun money(m: Money): String = Formatting.money(m)
    override fun countryName(country: CountryId): String = "Country ${country.value}"
    override fun facilityName(facility: FacilityId): String = "Plant ${facility.value}"
    override fun view(): CompanyCarbonView? = fx.views.company(company, tick)
    override fun market(country: CountryId): MarketView? = fx.views.market(country, tick)
    override fun revenue(): Money = revenue
    override fun submit(command: CarbonCommand): String? {
        submitted += command
        refusal?.let { return it }
        return when (val o = fx.service.execute(command, tick)) {
            is Outcome.Ok -> null
            is Outcome.Rejected -> o.rejection.message
        }
    }
}
