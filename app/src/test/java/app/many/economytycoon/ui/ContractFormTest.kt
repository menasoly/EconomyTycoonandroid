package app.many.economytycoon.ui

import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.AcceptForm
import app.many.economytycoon.ui.model.ContractForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ContractFormTest {
    private val me = CompanyId(1)
    private val them = CompanyId(2)
    private val farm = FacilityId(5)
    private val wheat = GoodId("wheat")

    private fun form(
        partner: CompanyId? = them, facility: FacilityId? = farm, good: GoodId? = wheat, quantity: String = "20", price: String = "21.50",
        interval: String = "5", deliveries: String = "12", side: ContractSide = ContractSide.SELLER,
    ) = ContractForm(me, partner, side, facility, good, quantity, price, interval, deliveries)

    @Test fun aCompleteFormBecomesAProposal() {
        val cmd = form().validate().commandOrNull as Command.ProposeContract
        assertEquals(them, cmd.counterparty)
        assertEquals(Quantity.units(20), cmd.quantity)
        assertEquals(Money.cents(2_150), cmd.unitPrice)
        assertEquals(5, cmd.intervalTicks)
        assertEquals(12, cmd.deliveries)
        assertEquals(ContractSide.SELLER, cmd.side)
    }

    @Test fun missingChoicesAreReportedOnTheirFields() {
        assertNotNull(form(partner = null).validate().errorFor("partner"))
        assertNotNull(form(facility = null).validate().errorFor("facility"))
        assertNotNull(form(good = null).validate().errorFor("good"))
    }

    @Test fun youCannotContractWithYourself() {
        assertNotNull(form(partner = me).validate().errorFor("partner"))
    }

    @Test fun numbersMustBeSensible() {
        assertNotNull(form(quantity = "0").validate().errorFor("quantity"))
        assertNotNull(form(quantity = "abc").validate().errorFor("quantity"))
        assertNotNull(form(price = "0").validate().errorFor("price"))
        assertNotNull(form(interval = "0").validate().errorFor("interval"))
        assertNotNull(form(interval = "91").validate().errorFor("interval"))
        assertNotNull(form(deliveries = "0").validate().errorFor("deliveries"))
        assertNotNull(form(deliveries = "361").validate().errorFor("deliveries"))
        assertNull(form(interval = "90", deliveries = "360").validate().errorFor("interval"))
    }

    @Test fun thePriceMustStayInsideTheAllowedBand() {
        val banded = form(price = "100").copy(minPrice = Money.units(4), maxPrice = Money.units(40))
        assertNotNull(banded.validate().errorFor("price"))
        assertNotNull(banded.copy(priceText = "2").validate().errorFor("price"))
        assertNotNull(banded.copy(priceText = "20").validate().commandOrNull)
    }

    @Test fun aSellerCannotPromiseMoreThanItHasToStartWith() {
        val seller = form(quantity = "50").copy(stockOnHand = Quantity.units(10))
        assertNotNull(seller.validate().errorFor("quantity"))
        assertNotNull(seller.copy(side = ContractSide.BUYER).validate().commandOrNull)
    }

    @Test fun acceptingNeedsAFacility() {
        assertNotNull(AcceptForm(me, ContractId(3), null).validate().errorFor("facility"))
        val cmd = AcceptForm(me, ContractId(3), farm).validate().commandOrNull as Command.AcceptContract
        assertEquals(ContractId(3), cmd.contract)
        assertEquals(farm, cmd.facility)
    }
}
