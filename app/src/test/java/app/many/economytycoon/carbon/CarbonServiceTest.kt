package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.capAndTrade
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.rule
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonServiceTest {
    private val fx = CarbonTestSupport.Fixture()
    private val plants = listOf(emitter(1, acme), emitter(2, borea))

    @Test fun installingACapAndTradeRegimeOpensItsMarket() {
        val events = fx.service.installRegime(capAndTrade(), Tick(0)).value()
        assertNotNull(fx.state.market(uk))
        assertEquals(RegimeKind.CAP_AND_TRADE, fx.state.regime(uk)!!.kind)
        assertTrue(events.any { it is CarbonEvent.RegimeChanged && it.to == RegimeKind.CAP_AND_TRADE })
    }

    @Test fun aTaxRegimeOpensNoMarket() {
        fx.service.installRegime(RegimePresets.carbonTax(de, 2000, Money.units(10)), Tick(0)).value()
        assertNull(fx.state.market(de))
        assertEquals(Money.units(10), fx.service.carbonPrice(de, 2000))
    }

    @Test fun installingTheSameKindTwiceRaisesNoChangeEvent() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        val again = fx.service.installRegime(capAndTrade(cap = 9_000), Tick(0)).value()
        assertTrue(again.none { it is CarbonEvent.RegimeChanged })
        assertEquals(Tonnes.of(9_000), fx.state.regime(uk)!!.stepFor(2000)!!.cap)
    }

    @Test fun anInvalidRegimeIsRefusedAndChangesNothing() {
        val before = fx.state.stateHash()
        val bad = capAndTrade().copy(steps = emptyList())
        assertTrue(fx.service.installRegime(bad, Tick(0)) is Outcome.Rejected)
        assertEquals(before, fx.state.stateHash())
    }

    @Test fun aTickIsNeverProcessedTwice() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.step(0, plants)
        val once = fx.state.stateHash()
        val again = fx.service.advance(Tick(0), plants)
        assertEquals(once, fx.state.stateHash())
        assertTrue(again.events.isEmpty())
        assertTrue(again.settlement.payments.isEmpty())
    }

    @Test fun lastTickFollowsTheCalendar() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 12, plants)
        assertEquals(12L, fx.state.lastTick)
    }

    @Test fun anInactiveCompanyCannotCommand() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.inactive += acme
        val r = fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.METHANE_CAPTURE, Tonnes.of(10)), 1)
        assertTrue(r is Outcome.Rejected)
    }

    @Test fun aRejectedCommandLeavesTheStateUntouched() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 10, plants)
        val before = fx.state.stateHash()
        val r = fx.exec(CarbonCommand.Surrender(acme, uk, Tonnes.of(10)), 11)
        assertTrue(r is Outcome.Rejected)
        assertEquals(before, fx.state.stateHash())
    }

    @Test fun theBorderPolicyCanBeSetAndCleared() {
        fx.service.setBorderPolicy(BorderPolicy(uk, startYear = 2001)).value()
        assertNotNull(fx.state.border.policy(uk))
        fx.service.clearBorderPolicy(uk).value()
        assertNull(fx.state.border.policy(uk))
    }

    @Test fun clearingAMissingBorderPolicyIsRefused() {
        assertEquals(app.many.economytycoon.core.Rules.UNKNOWN_ENTITY, fx.service.clearBorderPolicy(uk).rule())
    }

    @Test fun priceIsZeroWhereCarbonIsNotPriced() {
        assertEquals(Money.ZERO, fx.service.carbonPrice(uk, 2000))
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        assertEquals(Money.ZERO, fx.service.carbonPrice(uk, 1999))
        assertTrue(fx.service.carbonPrice(uk, 2000).isPositive)
    }

    @Test fun replacingARegimeMidwayStillAdvances() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 200, plants)
        fx.service.installRegime(RegimePresets.carbonTax(uk, 2000, Money.units(15), threshold = Tonnes.of(100)), Tick(200)).value()
        fx.run(201, 480, plants)
        assertEquals(RegimeKind.CARBON_TAX, fx.state.regime(uk)!!.kind)
        assertTrue(fx.eventsOf<CarbonEvent.TaxCharged>().isNotEmpty())
    }

    @Test fun moneyOnlyLeavesTheServiceAsIntents() {
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 30, plants)
        fx.exec(CarbonCommand.BuyOffsets(acme, OffsetKind.METHANE_CAPTURE, Tonnes.of(50)), 30).value()
        assertTrue(fx.payments.isNotEmpty())
        assertTrue(fx.payments.all { it.total.isPositive })
    }
}
