package app.many.economytycoon.ui

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.LeaseOfferForm
import app.many.economytycoon.ui.model.SellParcelForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PropertyFormsTest {
    private val me = CompanyId(1)
    private val plot = ParcelId(7)

    @Test fun aSaleFormBecomesAListing() {
        val cmd = SellParcelForm(me, plot, "12500").validate().commandOrNull as Command.ListParcel
        assertEquals(plot, cmd.parcel); assertEquals(Money.units(12_500), cmd.ask)
    }

    @Test fun thePriceIsRequiredAndPositive() {
        assertNotNull(SellParcelForm(me, plot, "").validate().errorFor("ask"))
        assertNotNull(SellParcelForm(me, plot, "abc").validate().errorFor("ask"))
        assertNotNull(SellParcelForm(me, plot, "0").validate().errorFor("ask"))
    }

    @Test fun thePriceStaysInsideTheBand() {
        val form = { t: String -> SellParcelForm(me, plot, t, Money.units(600), Money.units(2_500)).validate().errorFor("ask") }
        assertNotNull(form("599")); assertNotNull(form("2501"))
        assertNull(form("600")); assertNull(form("2500"))
    }

    @Test fun aLeaseFormNeedsATermAndARent() {
        val cmd = LeaseOfferForm(me, plot, 180, "300").validate().commandOrNull as Command.OfferLease
        assertEquals(180, cmd.termDays); assertEquals(Money.units(300), cmd.monthlyRent)
        assertNotNull(LeaseOfferForm(me, plot, null, "300").validate().errorFor("term"))
        assertNotNull(LeaseOfferForm(me, plot, 100, "300").validate().errorFor("term"))
        assertNotNull(LeaseOfferForm(me, plot, 90, "").validate().errorFor("rent"))
    }

    @Test fun theRentStaysInsideTheBand() {
        val form = { t: String -> LeaseOfferForm(me, plot, 90, t, minRent = Money.units(100), maxRent = Money.units(500)).validate().errorFor("rent") }
        assertNotNull(form("99")); assertNotNull(form("501")); assertNotNull(form("0"))
        assertNull(form("100")); assertNull(form("500"))
    }
}
