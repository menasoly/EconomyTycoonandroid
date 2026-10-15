package app.many.economytycoon.ui

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.TenderId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.BidForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BidFormTest {
    private val me = CompanyId(1)
    private fun form(facility: FacilityId? = FacilityId(3), price: String = "12.50", floor: Money? = Money.units(5), ceiling: Money? = Money.units(20)) =
        BidForm(me, TenderId(9), facility, price, floor, ceiling)

    @Test fun aCompleteFormBecomesABid() {
        val cmd = form().validate().commandOrNull as Command.BidTender
        assertEquals(TenderId(9), cmd.tender)
        assertEquals(FacilityId(3), cmd.facility)
        assertEquals(Money.cents(1_250), cmd.unitPrice)
    }

    @Test fun aFacilityMustBeChosen() {
        assertNotNull(form(facility = null).validate().errorFor("facility"))
    }

    @Test fun thePriceMustBeNumericAndPositive() {
        assertNotNull(form(price = "").validate().errorFor("price"))
        assertNotNull(form(price = "abc").validate().errorFor("price"))
        assertNotNull(form(price = "0").validate().errorFor("price"))
    }

    @Test fun thePriceMustStayInTheBand() {
        assertNotNull(form(price = "4.99").validate().errorFor("price"))
        assertNotNull(form(price = "20.01").validate().errorFor("price"))
        assertNull(form(price = "5").validate().errorFor("price"))
        assertNull(form(price = "20").validate().errorFor("price"))
    }

    @Test fun noBandMeansAnyPositivePrice() {
        assertNull(form(price = "999", floor = null, ceiling = null).validate().errorFor("price"))
    }
}
