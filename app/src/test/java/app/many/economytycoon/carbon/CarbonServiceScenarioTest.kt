package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.capAndTrade
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonServiceScenarioTest {
    private val fx = CarbonTestSupport.Fixture()
    private val plants = listOf(emitter(1, acme), emitter(2, borea))

    @Test fun aYearOfOperationOpensTheSupplyAndHoldsFourAuctions() {
        fx.service.installRegime(capAndTrade(), app.many.economytycoon.core.Tick(0)).value()
        fx.run(0, 359, plants)
        val market = fx.state.market(uk)!!
        val supply = market.supply(2000)!!
        assertEquals(Tonnes.of(12_000), supply.cap)
        assertEquals(Tonnes.of(6_000), supply.free)
        assertEquals(4, supply.auctionsHeld)
        assertEquals(4, fx.eventsOf<CarbonEvent.AuctionCleared>().size)
        assertTrue(fx.eventsOf<CarbonEvent.PermitsAllocated>().isNotEmpty())
    }

    @Test fun theYearCloseOpensAPendingObligationAndTheDeadlineSettlesIt() {
        fx.service.installRegime(capAndTrade(), app.many.economytycoon.core.Tick(0)).value()
        fx.run(0, 361, plants)
        val acct = fx.state.compliance.account(acme, uk)!!
        assertEquals(2000, acct.pending!!.year)
        assertTrue(acct.pending!!.obligation.isPositive)
        fx.run(362, 460, plants)
        assertEquals(null, acct.pending)
        val rec = acct.recordFor(2000)!!
        assertEquals(RegimeKind.CAP_AND_TRADE, rec.regime)
        assertTrue(fx.eventsOf<CarbonEvent.TrueUp>().size >= 2)
        assertTrue(fx.state.reports.statement(acme, uk, 2000) != null)
        assertTrue(fx.state.reports.rating(acme) != null)
        assertTrue(fx.state.receipts(uk) > Money.ZERO)
    }
}
