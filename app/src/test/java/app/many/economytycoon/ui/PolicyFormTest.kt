package app.many.economytycoon.ui

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.PolicyForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PolicyFormTest {
    private val me = CompanyId(1)
    private fun form(facility: FacilityId? = FacilityId(4), coverage: String = "80", cash: Money? = null, premium: Money? = null) =
        PolicyForm(me, facility, CoverLevel.STANDARD, Deductible.MEDIUM, coverage, cash = cash, premium = premium)

    @Test fun aCompleteFormBecomesAPolicyPurchase() {
        val cmd = form().validate().commandOrNull as Command.BuyPolicy
        assertEquals(FacilityId(4), cmd.facility)
        assertEquals(800_000L, cmd.coveragePpm)
        assertEquals(CoverLevel.STANDARD, cmd.level)
        assertEquals(Deductible.MEDIUM, cmd.deductible)
    }

    @Test fun aFacilityMustBeChosen() {
        assertNotNull(form(facility = null).validate().errorFor("facility"))
    }

    @Test fun coverageStaysInsideTheAllowedRange() {
        assertNotNull(form(coverage = "49").validate().errorFor("coverage"))
        assertNotNull(form(coverage = "101").validate().errorFor("coverage"))
        assertNotNull(form(coverage = "abc").validate().errorFor("coverage"))
        assertNotNull(form(coverage = "").validate().errorFor("coverage"))
        assertNull(form(coverage = "50").validate().errorFor("coverage"))
        assertNull(form(coverage = "100").validate().errorFor("coverage"))
    }

    @Test fun thePremiumMustBeAffordable() {
        assertNotNull(form(cash = Money.units(100), premium = Money.units(250)).validate().errorFor("premium"))
        assertNull(form(cash = Money.units(300), premium = Money.units(250)).validate().errorFor("premium"))
    }
}
