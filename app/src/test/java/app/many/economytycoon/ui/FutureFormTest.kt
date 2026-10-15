package app.many.economytycoon.ui

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.futures.Side
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.ui.model.FutureForm
import app.many.economytycoon.ui.model.MarginForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FutureFormTest {
    private val me = CompanyId(1)
    private fun form(good: GoodId? = GoodId("wheat"), side: Side? = Side.SHORT, text: String = "5", tenor: Int? = 60, need: Money? = null, cash: Money? = null) =
        FutureForm(me, good, side, text, tenor, cashNeeded = need, cash = cash)

    @Test fun aCompleteFormBecomesAnOpenCommand() {
        val cmd = form().validate().commandOrNull as Command.OpenFuture
        assertEquals(GoodId("wheat"), cmd.good); assertEquals(Side.SHORT, cmd.side); assertEquals(5L, cmd.contracts); assertEquals(60, cmd.tenor)
    }

    @Test fun everyChoiceIsRequired() {
        assertNotNull(form(good = null).validate().errorFor("good"))
        assertNotNull(form(side = null).validate().errorFor("side"))
        assertNotNull(form(tenor = null).validate().errorFor("tenor"))
        assertNotNull(form(text = "").validate().errorFor("contracts"))
    }

    @Test fun onlyListedTenorsAreAccepted() {
        assertNotNull(form(tenor = 45).validate().errorFor("tenor"))
        assertNull(form(tenor = 90).validate().errorFor("tenor"))
    }

    @Test fun contractsStayWithinTheLimit() {
        assertNotNull(form(text = "0").validate().errorFor("contracts"))
        assertNotNull(form(text = "41").validate().errorFor("contracts"))
        assertNotNull(form(text = "abc").validate().errorFor("contracts"))
        assertNull(form(text = "40").validate().errorFor("contracts"))
    }

    @Test fun theCashCheckExplainsTheShortfall() {
        val err = form(need = Money.units(500), cash = Money.units(100)).validate().errorFor("contracts")
        assertNotNull(err)
        assertNull(form(need = Money.units(100), cash = Money.units(500)).validate().errorFor("contracts"))
    }

    @Test fun marginMustBeAPositiveAmountYouCanAfford() {
        val ok = MarginForm(me, 4, "250.50", Money.units(1_000)).validate().commandOrNull as Command.AddMargin
        assertEquals(4L, ok.position); assertEquals(Money.cents(25_050), ok.amount)
        assertNotNull(MarginForm(me, 4, "", Money.units(1_000)).validate().errorFor("amount"))
        assertNotNull(MarginForm(me, 4, "0", Money.units(1_000)).validate().errorFor("amount"))
        assertNotNull(MarginForm(me, 4, "2000", Money.units(1_000)).validate().errorFor("amount"))
    }
}
