package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.de
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonViewsTest {
    private fun busy(days: Long = 470) = CarbonTestSupport.Fixture().also { CarbonTestSupport.busy(it, days) }

    @Test fun anUnknownCompanyHasAnEmptyView() {
        val fx = busy()
        val v = fx.views.company(CompanyId(99), Tick(470))
        assertTrue(v.accounts.isEmpty())
        assertNull(v.rating)
        assertEquals(Tonnes.ZERO, v.footprint)
        assertTrue(!v.hasPending)
    }

    @Test fun aCompanyViewListsEveryCountryItEmitsIn() {
        val fx = busy()
        val v = fx.views.company(acme, Tick(470))
        assertEquals(setOf(uk, de), v.accounts.map { it.country }.toSet())
        assertEquals(RegimeKind.CAP_AND_TRADE, v.account(uk)!!.kind)
        assertEquals(RegimeKind.CARBON_TAX, v.account(de)!!.kind)
    }

    @Test fun accountsAreOrderedByCountry() {
        val v = busy().views.company(acme, Tick(470))
        assertEquals(v.accounts.map { it.country }.sortedBy { it.value }, v.accounts.map { it.country })
    }

    @Test fun theFootprintIsTheSumOfTheAccounts() {
        val v = busy().views.company(acme, Tick(470))
        assertEquals(Tonnes.sum(v.accounts.map { it.footprint }), v.footprint)
        assertTrue(v.footprint.isPositive)
    }

    @Test fun projectedCoveredScalesToTheYear() {
        val fx = busy(days = 90)
        val a = fx.views.company(borea, Tick(90)).account(uk)!!
        assertTrue(a.projectedCovered > a.covered)
        val expected = a.covered.raw * 360 / a.daysObserved
        assertTrue(Math.abs(expected - a.projectedCovered.raw) <= 1L)
    }

    @Test fun pendingAppearsAfterTheYearCloses() {
        val fx = busy(days = 365)
        val p = fx.views.company(cyrus, Tick(365)).account(uk)!!.pending
        assertNotNull(p)
        assertEquals(2000, p!!.year)
        assertTrue(p.daysLeft in 0..fx.state.regime(uk)!!.surrenderWindowDays)
    }

    @Test fun theProjectedShortfallNeverGoesNegative() {
        val fx = busy(days = 365)
        for (c in listOf(acme, borea, cyrus)) {
            val p = fx.views.company(c, Tick(365)).account(uk)?.pending ?: continue
            assertTrue(!p.projectedShortfall.isNegative)
        }
    }

    @Test fun optionsAreSortedByCostPerTonne() {
        val opts = busy(days = 10).views.options(acme)
        assertTrue(opts.isNotEmpty())
        assertEquals(opts.map { it.costPerTonne }.sorted(), opts.map { it.costPerTonne })
    }

    @Test fun macCurveCumulatesAndRisesInCost() {
        val fx = busy(days = 10)
        val curve = fx.views.macCurve(acme, uk)
        val pts = curve.points
        assertTrue(pts.isNotEmpty())
        for (i in 1 until pts.size) {
            assertTrue(pts[i].cumulative >= pts[i - 1].cumulative)
            assertTrue(pts[i].cost >= pts[i - 1].cost)
        }
        assertEquals(pts.last().cumulative, curve.totalAbatement)
    }

    @Test fun theMacCurveOnlyHoldsFacilitiesInThatCountry() {
        val fx = busy(days = 10)
        val here = fx.views.macCurve(acme, de).points.map { it.option.facility }
        assertTrue(here.all { it == FacilityId(4) })
    }

    @Test fun theMarketViewCarriesSupplyAndPrice() {
        val m = busy().views.market(uk, Tick(470))!!
        assertEquals(RegimeKind.CAP_AND_TRADE, m.kind)
        assertNotNull(m.supply)
        assertTrue(m.price.isPositive)
        assertTrue(m.auctions.isNotEmpty())
        assertEquals(2001, m.year)
    }

    @Test fun aTaxCountryHasAViewButNoAuction() {
        val m = busy().views.market(de, Tick(470))!!
        assertEquals(RegimeKind.CARBON_TAX, m.kind)
        assertNull(m.nextAuction)
        assertNull(m.supply)
    }

    @Test fun noRegimeNoMarketView() {
        assertNull(busy().views.market(app.many.economytycoon.core.CountryId(55), Tick(470)))
    }

    @Test fun theNextAuctionLiesAheadWithinAYear() {
        val fx = busy(days = 30)
        val next = fx.views.market(uk, Tick(30))!!.nextAuction!!
        assertTrue(next.value >= 30)
        assertTrue(next.value < 30 + 360)
    }

    @Test fun nationalSummaryMatchesTheInventory() {
        val fx = busy()
        val n = fx.views.national(uk, 2000)!!
        assertEquals(fx.state.inventory.national(uk, 2000)!!.direct, n.direct)
        assertTrue(n.coveragePpm in 0..1_000_000L)
        assertEquals(n.coveragePpm, fx.views.coveragePpm(uk, 2000))
    }

    @Test fun theLeagueRanksCleanestFirst() {
        val fx = busy()
        val rows = fx.views.league(uk, 2000)
        assertTrue(rows.size >= 2)
        assertEquals(rows.map { it.intensityGrams }.sorted(), rows.map { it.intensityGrams })
    }

    @Test fun peerComparisonNeedsPeers() {
        val fx = busy()
        assertNull(fx.views.intensityVersusPeers(CompanyId(99), uk, 2000))
        val r = fx.views.intensityVersusPeers(acme, uk, 2000)
        if (r != null) assertTrue(r > 0L)
    }

    @Test fun readingNeverChangesState() {
        val fx = busy()
        val before = fx.state.stateHash()
        repeat(3) {
            fx.views.company(acme, Tick(470)); fx.views.market(uk, Tick(470)); fx.views.national(uk, 2000)
            fx.views.league(uk, 2000); fx.views.macCurve(acme, uk)
        }
        assertEquals(before, fx.state.stateHash())
    }

    @Test fun offsetsHeldShowOnlyPositiveKinds() {
        val v = busy().views.company(borea, Tick(470))
        assertTrue(v.offsetsHeld.values.all { it.isPositive })
        assertEquals(Tonnes.sum(v.offsetsHeld.values), v.offsetsTotal)
    }
}
