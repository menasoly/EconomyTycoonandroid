package app.many.economytycoon.ui

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.BuybackForm
import app.many.economytycoon.ui.model.ListingForm
import app.many.economytycoon.ui.model.TradeForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExchangeFormTest {
    private val me = CompanyId(1)
    private val other = CompanyId(2)

    @Test fun aPurchaseBecomesBuyShares() {
        val cmd = TradeForm(me, other, true, "250").validate().commandOrNull as Command.BuyShares
        assertEquals(other, cmd.issuer)
        assertEquals(250L, cmd.shares)
    }

    @Test fun aSaleBecomesSellShares() {
        val cmd = TradeForm(me, other, false, "40", available = 100).validate().commandOrNull as Command.SellShares
        assertEquals(40L, cmd.shares)
    }

    @Test fun sharesMustBePositiveWholeAndWithinWhatIsAvailable() {
        assertNotNull(TradeForm(me, other, true, "0").validate().errorFor("shares"))
        assertNotNull(TradeForm(me, other, true, "").validate().errorFor("shares"))
        assertNotNull(TradeForm(me, other, true, "2.5").validate().errorFor("shares"))
        assertNotNull(TradeForm(me, other, false, "101", available = 100).validate().errorFor("shares"))
        assertNull(TradeForm(me, other, false, "100", available = 100).validate().errorFor("shares"))
    }

    @Test fun aCompanyCannotBuyItsOwnStockOnTheOpenMarket() {
        assertNotNull(TradeForm(me, me, true, "5").validate().errorFor("shares"))
    }

    @Test fun listingReadsAPercentageIntoParts() {
        val cmd = ListingForm(me, "30").validate().commandOrNull as Command.ListCompany
        assertEquals(300_000L, cmd.floatPpm)
    }

    @Test fun listingHonoursTheAllowedRange() {
        assertNotNull(ListingForm(me, "2").validate().errorFor("float"))
        assertNotNull(ListingForm(me, "60").validate().errorFor("float"))
        assertNotNull(ListingForm(me, "abc").validate().errorFor("float"))
        assertNull(ListingForm(me, "5").validate().errorFor("float"))
        assertNull(ListingForm(me, "49").validate().errorFor("float"))
    }

    @Test fun buybackCannotExceedWhatThePublicHolds() {
        assertEquals(Command.BuybackShares(me, 10L), BuybackForm(me, "10", 50).validate().commandOrNull)
        assertNotNull(BuybackForm(me, "51", 50).validate().errorFor("shares"))
        assertNotNull(BuybackForm(me, "0", 50).validate().errorFor("shares"))
    }
}
