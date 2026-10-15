package app.many.economytycoon.ui

import app.many.economytycoon.core.BondId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.BondAction
import app.many.economytycoon.ui.model.BondForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BondFormTest {
    private val me = CompanyId(1)
    private fun form(action: BondAction = BondAction.SUBSCRIBE, units: String = "10", available: Long? = 100, cost: Money? = Money.units(1_000), cash: Money? = Money.units(50_000)) =
        BondForm(me, BondId(3), action, units, available, cost, cash)

    @Test fun eachActionBuildsItsCommand() {
        assertEquals(Command.SubscribeBond(me, BondId(3), 10), form(BondAction.SUBSCRIBE).validate().commandOrNull)
        assertEquals(Command.TradeBond(me, BondId(3), 10, true), form(BondAction.BUY).validate().commandOrNull)
        assertEquals(Command.TradeBond(me, BondId(3), 10, false), form(BondAction.SELL).validate().commandOrNull)
    }

    @Test fun unitsMustBeAPositiveWholeNumber() {
        assertNotNull(form(units = "").validate().errorFor("units"))
        assertNotNull(form(units = "abc").validate().errorFor("units"))
        assertNotNull(form(units = "0").validate().errorFor("units"))
        assertNotNull(form(units = "2.5").validate().errorFor("units"))
    }

    @Test fun unitsCannotExceedWhatIsAvailable() {
        assertNotNull(form(units = "101").validate().errorFor("units"))
        assertNull(form(units = "100", cash = Money.units(1_000_000)).validate().errorFor("units"))
        assertNull(form(units = "100000", available = null, cash = null).validate().errorFor("units"))
    }

    @Test fun theMessageNamesTheSourceOfTheLimit() {
        assertEquals(true, form(BondAction.SUBSCRIBE, "500").validate().errorFor("units")!!.contains("left in this issue"))
        assertEquals(true, form(BondAction.BUY, "500").validate().errorFor("units")!!.contains("dealer"))
        assertEquals(true, form(BondAction.SELL, "500").validate().errorFor("units")!!.contains("You hold"))
    }

    @Test fun buyingMustBeAffordableButSellingNeedsNoCash() {
        assertNotNull(form(BondAction.SUBSCRIBE, "60").validate().errorFor("units"))
        assertNotNull(form(BondAction.BUY, "60").validate().errorFor("units"))
        assertNull(form(BondAction.SELL, "60", cash = Money.ZERO).validate().errorFor("units"))
        assertNull(form(BondAction.SUBSCRIBE, "50").validate().errorFor("units"))
    }
}
