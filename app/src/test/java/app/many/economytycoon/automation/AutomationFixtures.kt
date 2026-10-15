package app.many.economytycoon.automation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.production.Facility
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation

/** A bare world with one funded player company, for exercising managers against the real economy. */
class AutomationRig(capital: Money = Money.units(900_000)) {
    val sim = Simulation(SimTestSupport.config(5, 0), SimTestSupport.content, bootstrap = false)
    val e = sim.economy
    val country = e.world.countries().first()
    val me = Actor.Player(1)
    val company: CompanyId = CompanyId((sim.execute(me, Command.FoundCompany("Auto Works", country.id, country.capital, capital)) as Outcome.Ok).value.created!!)
    val region = e.world.regionsOf(country.id).first().id

    fun farm(): Facility = e.production.grantFacility(company, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
    fun ctx(settings: ManagerSettings) = AutomationContext(e, company, settings)
    fun on(vararg k: ManagerKind) = ManagerSettings(enabled = k.toSet(), reserve = Money.ZERO)
    fun run(planned: List<Planned>) = planned.map { sim.execute(me, it.command) }
}
