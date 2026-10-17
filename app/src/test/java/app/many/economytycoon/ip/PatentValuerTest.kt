package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.megamill
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.eu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatentValuerTest {
    private fun rigWithSales(sales: Long = 20_000_000): Pair<IpTestSupport.Rig, Patent> {
        val r = IpTestSupport.Rig()
        val p = r.granted(a, milling)
        r.ctx.fieldSales[a to TechField.MANUFACTURING] = Money.units(sales)
        return r to p
    }

    @Test fun aPatentOnABigMarketIsWorthMore() {
        val (small, ps) = rigWithSales(1_000_000)
        val (big, pb) = rigWithSales(100_000_000)
        assertTrue(big.valuer.value(pb, big.run()).value > small.valuer.value(ps, small.run()).value)
    }

    @Test fun incomeFollowsTheMarketAndTheRoyaltyRate() {
        val (r, p) = rigWithSales(20_000_000)
        val v = r.valuer.value(p, r.run())
        assertEquals(Money.units(20_000_000), v.marketSales)
        assertTrue(v.income > Money.ZERO)
        assertEquals(Royalty.reasonablePercent(LicenseParams(), 120_000L, 600_000L, 700_000L).ppm, v.royaltyRatePpm)
        assertTrue(v.remainingYears in 19..20)
    }

    @Test fun practisingCompaniesCountAsMarketToo() {
        val r = IpTestSupport.Rig()
        val p = r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, IpTestSupport.usage(1_000, 5_000)), Practice(c, IpTestSupport.usage(500, 5_000)))
        assertEquals(Money.units(5_000_000 + 2_500_000), r.valuer.marketSales(p, r.run()))
    }

    @Test fun noMarketLeavesOnlyTheCostFloor() {
        val r = IpTestSupport.Rig()
        val p = r.granted(a, milling)
        val v = r.valuer.value(p, r.run())
        assertEquals(Money.ZERO, v.income)
        assertTrue(v.cost > Money.ZERO)
        assertTrue(v.value > Money.ZERO)
    }

    @Test fun aDeadPatentIsWorthNothing() {
        val (r, p) = rigWithSales()
        for (f in p.filings) f.status = FilingStatus.EXPIRED
        val v = r.valuer.value(p, r.run())
        assertEquals(Money.ZERO, v.value)
        assertEquals(0, v.remainingYears)
    }

    @Test fun valueFallsAsTheTermRunsDown() {
        val (r, p) = rigWithSales()
        val early = r.valuer.value(p, r.run(0)).value
        val late = r.valuer.value(p, r.run(5_400)).value
        assertTrue(late < early)
    }

    @Test fun costApproachIsWrittenDownByAge() {
        val (r, p) = rigWithSales(0)
        val fresh = r.valuer.value(p, r.run(0)).cost
        val aged = r.valuer.value(p, r.run(3_600)).cost
        assertTrue(Math.abs(fresh.raw / 2 - aged.raw) <= fresh.raw / 100)
    }

    @Test fun narrowNovelPatentsHoldUpBetter() {
        val r = IpTestSupport.Rig()
        val strong = r.granted(a, milling, noveltyPpm = 900_000L, breadthPpm = 300_000L)
        val weak = r.granted(b, milling, noveltyPpm = 300_000L, breadthPpm = 900_000L)
        assertTrue(r.valuer.validityPpm(strong) > r.valuer.validityPpm(weak))
        assertTrue(r.valuer.validityPpm(weak) >= 300_000L)
    }

    @Test fun forwardCitationsAddAPremium() {
        val (r, p) = rigWithSales()
        val base = r.valuer.value(p, r.run())
        r.state.forwardCites[p.id] = 3
        val cited = r.valuer.value(p, r.run())
        assertEquals(120_000L, cited.premiumPpm)
        assertTrue(cited.value > base.value)
        r.state.forwardCites[p.id] = 100
        assertEquals(400_000L, r.valuer.citationPremiumPpm(p.id))
    }

    @Test fun standardEssentialPatentsCarryAPremium() {
        val (r, p) = rigWithSales()
        val base = r.valuer.value(p, r.run()).value
        p.standardEssential = true
        assertTrue(r.valuer.value(p, r.run()).value > base)
        assertEquals(250_000L, r.valuer.value(p, r.run()).premiumPpm)
    }

    @Test fun pendingApplicationsAreDiscountedByTheirGrantChance() {
        val r = IpTestSupport.Rig()
        r.teach(a, milling)
        r.ctx.fieldSales[a to TechField.MANUFACTURING] = Money.units(50_000_000)
        val pending = r.office.file(r.run(0), FilingRequest(a, milling, listOf(us), BreadthChoice.STANDARD), 0).getOrThrow()
        val granted = r.granted(b, milling, noveltyPpm = pending.noveltyPpm, breadthPpm = pending.claimedBreadthPpm)
        r.ctx.fieldSales[b to TechField.MANUFACTURING] = Money.units(50_000_000)
        assertTrue(r.valuer.value(pending, r.run()).income < r.valuer.value(granted, r.run()).income)
    }

    @Test fun portfolioSumsAndSortsPatents() {
        val r = IpTestSupport.Rig()
        val p1 = r.granted(a, milling)
        val p2 = r.granted(a, megamill, breadthPpm = 800_000L)
        r.ctx.fieldSales[a to TechField.MANUFACTURING] = Money.units(30_000_000)
        val pf = r.valuer.portfolio(IpOwner.company(a), r.run())
        assertEquals(2, pf.patents.size)
        assertEquals(Money.sum(pf.patents.map { it.value }), pf.total)
        assertTrue(pf.patents[0].value >= pf.patents[1].value)
        assertEquals(pf.total, pf.byField.getValue(TechField.MANUFACTURING))
        p1.let { }; p2.let { }
    }

    @Test fun deadPatentsLeaveThePortfolio() {
        val r = IpTestSupport.Rig()
        val p1 = r.granted(a, milling)
        r.granted(a, ovens)
        for (f in p1.filings) f.status = FilingStatus.LAPSED
        assertEquals(1, r.valuer.portfolio(IpOwner.company(a), r.run()).patents.size)
    }

    @Test fun licensingIncomeAnnualisesTheLastPeriod() {
        val r = IpTestSupport.Rig()
        val p = r.granted(a, milling)
        val l = r.sign(a, b, listOf(p.id), r.percent(5))
        r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        r.book.step(r.run(90))
        assertEquals(Money.units(5_000 * 4), r.valuer.licensingIncome(IpOwner.company(a)))
        assertEquals(Money.ZERO, r.valuer.licensingIncome(IpOwner.company(b)))
        assertEquals(l.lastDue, Money.units(5_000))
    }

    @Test fun balancingPaymentIsHalfTheGap() {
        val r = IpTestSupport.Rig()
        assertEquals(Money.units(30), r.valuer.balancingPayment(Money.units(100), Money.units(40)))
        assertEquals(Money.units(-30), r.valuer.balancingPayment(Money.units(40), Money.units(100)))
    }

    @Test fun fireSalePricesAreAFractionOfValue() {
        val (r, p) = rigWithSales()
        val v = r.valuer.value(p, r.run())
        assertEquals(v.value.times(Rate(350_000L)), r.valuer.fireSalePrice(v))
    }

    @Test fun remainingShareDropsWithAge() {
        val (r, p) = rigWithSales()
        assertEquals(Rate.ONE.ppm, r.valuer.remainingShare(p, Tick(0)).ppm)
        assertEquals(500_000L, r.valuer.remainingShare(p, Tick(3_600)).ppm)
        assertEquals(0L, r.valuer.remainingShare(p, Tick(8_000)).ppm)
    }

    @Test fun anAbsentMarketInOtherCountriesDoesNotCount() {
        val r = IpTestSupport.Rig()
        val p = r.granted(a, milling, listOf(us))
        r.ctx.practicers[milling to eu] = mutableListOf(Practice(b, IpTestSupport.usage(1_000, 1_000)))
        assertEquals(Money.ZERO, r.valuer.marketSales(p, r.run()))
    }
}
