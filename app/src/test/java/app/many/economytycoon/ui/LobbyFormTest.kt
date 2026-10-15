package app.many.economytycoon.ui

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegulationId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.LobbyForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LobbyFormTest {
    private val me = CompanyId(1)
    private fun form(support: Boolean? = true, amount: String = "5000", min: Money? = Money.units(1_000), cash: Money? = Money.units(50_000)) =
        LobbyForm(me, RegulationId(4), support, amount, min, cash)

    @Test fun aCompleteFormBecomesACommand() {
        val cmd = form().validate().commandOrNull as Command.LobbyLaw
        assertEquals(RegulationId(4), cmd.law); assertEquals(true, cmd.support); assertEquals(Money.units(5_000), cmd.amount)
        assertEquals(false, (form(support = false).validate().commandOrNull as Command.LobbyLaw).support)
    }

    @Test fun aSideMustBeChosen() {
        assertNotNull(form(support = null).validate().errorFor("side"))
    }

    @Test fun theAmountMustBeNumericAndPositive() {
        assertNotNull(form(amount = "").validate().errorFor("amount"))
        assertNotNull(form(amount = "lots").validate().errorFor("amount"))
        assertNotNull(form(amount = "0").validate().errorFor("amount"))
    }

    @Test fun theAmountMustReachTheFloorAndStayWithinCash() {
        assertNotNull(form(amount = "999").validate().errorFor("amount"))
        assertNull(form(amount = "1000").validate().errorFor("amount"))
        assertNotNull(form(amount = "50001").validate().errorFor("amount"))
        assertNull(form(amount = "50000").validate().errorFor("amount"))
        assertNull(form(min = null, cash = null, amount = "1").validate().errorFor("amount"))
    }
}
