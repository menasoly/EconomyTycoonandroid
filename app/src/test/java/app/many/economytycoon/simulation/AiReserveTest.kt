package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReserveTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val reserve = AiReserve(e)

    private fun ai(): CompanyId {
        val country = e.world.countries().first()
        return CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival Works", country.id, null, Money.units(900_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
    }

    @Test fun aCompanyWithNothingRunningStillKeepsTheMinimum() {
        val id = ai()
        assertEquals(Money.ZERO, reserve.dailyCost(id))
        assertEquals(AiReserve.MINIMUM, reserve.floor(id))
    }

    @Test fun theMinimumCanBeRaisedOrLowered() {
        val id = ai()
        assertEquals(Money.units(5), reserve.floor(id, minimum = Money.units(5)))
    }

    @Test fun theFloorNeverFallsBelowTheMinimum() {
        val id = ai()
        assertTrue(reserve.floor(id, days = 1).raw >= AiReserve.MINIMUM.raw)
    }

    @Test fun anUnknownCompanyCostsNothing() {
        assertEquals(Money.ZERO, reserve.dailyCost(CompanyId(9_999)))
    }
}
