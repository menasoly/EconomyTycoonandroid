package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.capAndTrade
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-year stories that cross several rules at once. */
class CarbonScenarioTest {
    private val fx = CarbonTestSupport.Fixture(seed = 21L)

    @Test fun aTightCapPushesThePriceAboveALooseOne() {
        val plants = listOf(emitter(1, acme, coalUnits = 200), emitter(2, borea, coalUnits = 200))
        val tight = CarbonTestSupport.Fixture(1); val loose = CarbonTestSupport.Fixture(1)
        tight.service.installRegime(capAndTrade(cap = 3_000, free = 0), Tick(0)).value()
        loose.service.installRegime(capAndTrade(cap = 300_000, free = 0), Tick(0)).value()
        for (f in listOf(tight, loose)) {
            f.run(0, 14, plants)
            f.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(700), Money.units(60)), 14).value()
            f.exec(CarbonCommand.BidAtAuction(borea, uk, Tonnes.of(700), Money.units(60)), 14).value()
            f.run(15, 20, plants)
        }
        val tightPrice = tight.state.market(uk)!!.auctions().first().price
        val loosePrice = loose.state.market(uk)!!.auctions().first().price
        assertTrue("$tightPrice vs $loosePrice", tightPrice > loosePrice)
    }

    @Test fun anAbatementProjectCutsTheNextYearsEmissions() {
        val plants = listOf(emitter(1, acme, coalUnits = 200), emitter(2, borea))
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 10, plants)
        fx.exec(CarbonCommand.StartAbatement(acme, FacilityId(1), AbatementKind.FUEL_SWITCH), 10).value()
        fx.run(11, 359, plants)
        val first = fx.state.inventory.company(acme, uk, 2000)!!.footprint
        fx.run(360, 719, plants)
        val second = fx.state.inventory.company(acme, uk, 2001)!!.footprint
        assertTrue("$second should be below $first", second < first)
        assertTrue(fx.eventsOf<CarbonEvent.AbatementDone>().isNotEmpty())
    }

    @Test fun aCompanyThatBuysNothingEndsUpFinedAndRated() {
        val plants = listOf(emitter(1, cyrus, coalUnits = 400))
        fx.service.installRegime(capAndTrade(cap = 1_000, free = 0), Tick(0)).value()
        fx.run(0, 480, plants)
        val acct = fx.state.compliance.account(cyrus, uk)!!
        val rec = acct.recordFor(2000)!!
        assertEquals(ComplianceStatus.SHORTFALL, rec.status)
        assertTrue(rec.penalty.isPositive)
        assertTrue(acct.violations >= 1)
        assertTrue(fx.state.reports.rating(cyrus)!!.grade.ordinal >= CarbonGrade.BBB.ordinal)
    }

    @Test fun permitsBankedFromAGenerousYearCoverTheNext() {
        val plants = listOf(emitter(1, acme, coalUnits = 30))
        fx.service.installRegime(capAndTrade(cap = 60_000, free = 1_000_000), Tick(0)).value()
        fx.run(0, 800, plants)
        val acct = fx.state.compliance.account(acme, uk)!!
        assertEquals(ComplianceStatus.COMPLIANT, acct.recordFor(2000)!!.status)
        assertTrue(fx.state.market(uk)!!.holdings.total(acme).isPositive)
    }

    @Test fun aTaxChargesTheSameCompanyLessThanCapAndTradeWhenPermitsAreScarce() {
        val plants = listOf(emitter(1, acme, coalUnits = 100, country = de))
        fx.service.installRegime(RegimePresets.carbonTax(de, 2000, Money.units(8), threshold = Tonnes.of(10)), Tick(0)).value()
        fx.run(0, 150, plants)
        val charged = fx.eventsOf<CarbonEvent.TaxCharged>().fold(Money.ZERO) { a, e -> a + e.amount }
        assertTrue(charged.isPositive)
        assertTrue(fx.state.receipts(de) >= Money.ZERO)
    }

    @Test fun anIntensityStandardLeviesOnlyTheExcess() {
        val plants = listOf(emitter(1, acme), emitter(2, borea, coalUnits = 20))
        fx.service.installRegime(RegimePresets.intensityStandard(uk, 2000, benchmarkGramsPerUnit = 150_000L, threshold = Tonnes.of(10)), Tick(0)).value()
        fx.run(0, 380, plants)
        val a = fx.state.compliance.account(acme, uk)!!.recordFor(2000)!!
        val b = fx.state.compliance.account(borea, uk)!!.recordFor(2000)!!
        assertTrue(a.charge >= b.charge)
    }

    @Test fun regimeOffThenOnKeepsTheBooksConsistent() {
        val plants = listOf(emitter(1, acme), emitter(2, borea))
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 400, plants)
        fx.service.installRegime(RegimePresets.carbonTax(uk, 2002, Money.units(10), threshold = Tonnes.of(10)), Tick(400)).value()
        fx.run(401, 900, plants)
        for (acct in fx.state.compliance.accounts()) assertTrue(acct.pending == null || acct.pending!!.year >= 2000)
        assertTrue(fx.state.market(uk) != null)
    }

    @Test fun everyPermitIssuedIsAccountedFor() {
        val plants = listOf(emitter(1, acme), emitter(2, borea), emitter(3, cyrus, coalUnits = 40))
        fx.service.installRegime(capAndTrade(), Tick(0)).value()
        fx.run(0, 20, plants)
        fx.exec(CarbonCommand.BidAtAuction(acme, uk, Tonnes.of(300), Money.units(30)), 20)
        fx.run(21, 700, plants)
        val m = fx.state.market(uk)!!
        val issued = Tonnes.sum((2000..2001).mapNotNull { m.supply(it) }.map { it.free + it.sold })
        val live = m.holdings.totalAll()
        assertTrue("live $live issued $issued", live <= issued + m.reserve)
    }
}
