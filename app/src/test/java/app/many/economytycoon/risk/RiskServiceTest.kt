package app.many.economytycoon.risk

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.world.ClimateZone
import app.many.economytycoon.world.Point
import app.many.economytycoon.world.Region
import app.many.economytycoon.world.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskServiceTest {
    private val service = RiskService(IdAllocators())
    private val me = CompanyId(1)

    private fun region(terrain: Terrain = Terrain.HILLS, climate: ClimateZone = ClimateZone.TEMPERATE, coastal: Boolean = false) =
        Region(RegionId(1), CountryId(1), "Testland", Point(0, 0), terrain, climate, 1_000L, 500_000L, coastal)

    private fun sell(facility: Long = 1, level: CoverLevel = CoverLevel.STANDARD, coverage: Long = 800_000L, tick: Long = 0) =
        service.sell(me, FacilityId(facility), RegionId(1), level, coverage, Deductible.MEDIUM, Money.units(100_000), Money.units(300), Tick(tick))

    @Test fun eachCoverLevelIncludesTheOneBelow() {
        assertTrue(CoverLevel.BASIC.perils.all { it in CoverLevel.STANDARD.perils })
        assertTrue(CoverLevel.STANDARD.perils.all { it in CoverLevel.FULL.perils })
        assertEquals(Peril.values().toSet(), CoverLevel.FULL.perils)
    }

    @Test fun coastsFloodAndMountainsShake() {
        val inland = region(Terrain.PLAINS)
        val coast = region(Terrain.COAST, coastal = true)
        val peaks = region(Terrain.MOUNTAINS)
        assertTrue(service.regionFactorPpm(coast, Peril.FLOOD) > service.regionFactorPpm(inland, Peril.FLOOD) / 2)
        assertTrue(service.regionFactorPpm(coast, Peril.STORM) > service.regionFactorPpm(inland, Peril.STORM))
        assertTrue(service.regionFactorPpm(peaks, Peril.QUAKE) > service.regionFactorPpm(inland, Peril.QUAKE))
        assertEquals(1_000_000L, service.regionFactorPpm(region(Terrain.FOREST), Peril.QUAKE))
    }

    @Test fun aridPlacesRarelyFloodAndOftenBurn() {
        val dry = region(Terrain.DESERT, ClimateZone.ARID)
        val mild = region(Terrain.DESERT, ClimateZone.TEMPERATE)
        assertTrue(service.regionFactorPpm(dry, Peril.FLOOD) < service.regionFactorPpm(mild, Peril.FLOOD))
        assertTrue(service.regionFactorPpm(dry, Peril.FIRE) > service.regionFactorPpm(mild, Peril.FIRE))
    }

    @Test fun dailyChancesAreTheYearlyChanceSpreadOverTheYear() {
        val r = region()
        for (p in Peril.values()) {
            val perDay = service.chancePerTickPpm(r, p)
            val yearly = p.annualPpm * service.regionFactorPpm(r, p) / 1_000_000L
            assertTrue("$p", perDay * 360 in (yearly - 360)..yearly)
        }
    }

    @Test fun neglectAndPollutionMakeFireLikelier() {
        val clean = service.fireMultiplePpm(1_000_000L, 0L)
        assertEquals(1_000_000L, clean)
        assertTrue(service.fireMultiplePpm(1_000_000L, 800_000L) > clean)
        assertTrue(service.fireMultiplePpm(300_000L, 0L) > clean)
    }

    @Test fun widerCoverCostsMore() {
        val r = region(Terrain.MOUNTAINS)
        val basic = service.annualRatePpm(r, CoverLevel.BASIC, 800_000L, Deductible.MEDIUM)
        val standard = service.annualRatePpm(r, CoverLevel.STANDARD, 800_000L, Deductible.MEDIUM)
        val full = service.annualRatePpm(r, CoverLevel.FULL, 800_000L, Deductible.MEDIUM)
        assertTrue(basic < standard && standard < full)
    }

    @Test fun moreCoverAndASmallerExcessCostMore() {
        val r = region()
        assertTrue(service.annualRatePpm(r, CoverLevel.FULL, 1_000_000L, Deductible.MEDIUM) > service.annualRatePpm(r, CoverLevel.FULL, 600_000L, Deductible.MEDIUM))
        assertTrue(service.annualRatePpm(r, CoverLevel.FULL, 800_000L, Deductible.LOW) > service.annualRatePpm(r, CoverLevel.FULL, 800_000L, Deductible.HIGH))
    }

    @Test fun aRunDownPollutingBuildingPaysMore() {
        val r = region()
        val good = service.annualRatePpm(r, CoverLevel.BASIC, 800_000L, Deductible.MEDIUM, 1_000_000L, 0L)
        val bad = service.annualRatePpm(r, CoverLevel.BASIC, 800_000L, Deductible.MEDIUM, 300_000L, 900_000L)
        assertTrue(bad > good)
    }

    @Test fun premiumsScaleWithValueAndSurcharge() {
        val rate = 20_000L
        val base = service.premiumFor(Money.units(120_000), rate)
        assertEquals((service.premiumFor(Money.units(240_000), rate).raw / 2).toDouble(), base.raw.toDouble(), 2.0)
        assertTrue(service.premiumFor(Money.units(120_000), rate, 300_000L) > base)
        // 2% a year of 120,000 over 30 days of 360.
        assertEquals(Money.units(200), base)
    }

    @Test fun aPolicyStartsActiveAndPaidForAMonth() {
        val p = (sell(tick = 5) as Outcome.Ok).value
        assertTrue(p.isActive)
        assertEquals(Tick(35), p.paidThrough)
        assertEquals(Money.units(300), p.premiumsPaid)
        assertEquals(p, service.activeFor(FacilityId(1)))
        assertEquals(1, service.policiesOf(me).size)
    }

    @Test fun aFacilityCanOnlyBeInsuredOnce() {
        sell()
        val again = sell() as Outcome.Rejected
        assertEquals(Rules.WRONG_STATE, again.rejection.rule)
    }

    @Test fun coverageMustStayInRange() {
        assertEquals(Rules.INVALID_ARGUMENT, (sell(coverage = 400_000L) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_ARGUMENT, (sell(coverage = 1_100_000L) as Outcome.Rejected).rejection.rule)
        assertTrue(sell(coverage = 500_000L) is Outcome.Ok)
    }

    @Test fun aCompanyHasALimitOnPolicies() {
        val small = RiskService(IdAllocators(), RiskParams(maxPoliciesPerCompany = 2))
        for (i in 1L..2L) assertTrue(small.sell(me, FacilityId(i), RegionId(1), CoverLevel.BASIC, 800_000L, Deductible.LOW, Money.units(1), Money.units(1), Tick(0)) is Outcome.Ok)
        val third = small.sell(me, FacilityId(3), RegionId(1), CoverLevel.BASIC, 800_000L, Deductible.LOW, Money.units(1), Money.units(1), Tick(0)) as Outcome.Rejected
        assertEquals(Rules.LIMIT_EXCEEDED, third.rejection.rule)
    }

    @Test fun onlyTheHolderCanCancelAndOnlyOnce() {
        val p = (sell() as Outcome.Ok).value
        assertEquals(Rules.NOT_OWNER, (service.cancel(CompanyId(2), p.id, Tick(3)) as Outcome.Rejected).rejection.rule)
        assertTrue(service.cancel(me, p.id, Tick(3)) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, (service.cancel(me, p.id, Tick(4)) as Outcome.Rejected).rejection.rule)
        assertNull(service.activeFor(FacilityId(1)))
        assertEquals(PolicyStatus.CANCELLED, p.status)
        assertEquals(Tick(3), p.endedAt)
    }

    @Test fun aCancelledFacilityCanBeInsuredAgain() {
        val p = (sell() as Outcome.Ok).value
        service.cancel(me, p.id, Tick(1))
        assertTrue(sell(tick = 2) is Outcome.Ok)
    }

    @Test fun lapsingEndsCoverButOnlyOnce() {
        val p = (sell() as Outcome.Ok).value
        service.lapse(p, Tick(40))
        assertEquals(PolicyStatus.LAPSED, p.status)
        service.lapse(p, Tick(50))
        assertEquals(Tick(40), p.endedAt)
        assertFalse(p.covers(Peril.FIRE))
    }

    @Test fun aPolicyOnlyCoversItsLevel() {
        val p = (sell(level = CoverLevel.BASIC) as Outcome.Ok).value
        assertTrue(p.covers(Peril.FIRE) && p.covers(Peril.STORM))
        assertFalse(p.covers(Peril.FLOOD) || p.covers(Peril.QUAKE))
    }

    @Test fun claimsRaiseTheSurchargeAndBillingLowersIt() {
        val p = (sell() as Outcome.Ok).value
        service.paidOut(p, Money.units(1_000), Tick(10))
        assertEquals(service.params.claimSurchargePpm, p.surchargePpm)
        assertEquals(1, p.claimCount)
        assertEquals(Tick(10), p.lastClaim)
        service.billed(p, Money.units(100_000), Money.units(300), Tick(30))
        assertTrue(p.surchargePpm < service.params.claimSurchargePpm)
        assertEquals(Tick(60), p.paidThrough)
    }

    @Test fun theDeductibleIsAShareOfInsuredValue() {
        val p = (sell() as Outcome.Ok).value
        assertEquals(Money.units(5_000), p.deductibleAmount)
    }

    @Test fun endingEveryPolicyOfACompanyLeavesOthersAlone() {
        sell(1); sell(2)
        val other = (service.sell(CompanyId(2), FacilityId(9), RegionId(1), CoverLevel.BASIC, 800_000L, Deductible.LOW, Money.units(1), Money.units(1), Tick(0)) as Outcome.Ok).value
        service.endAllOf(me, Tick(5))
        assertTrue(service.activePoliciesOf(me).isEmpty())
        assertTrue(other.isActive)
    }

    @Test fun historiesKeepOnlyTheLatestEntries() {
        val small = RiskService(IdAllocators(), RiskParams(disasterHistory = 3, claimHistory = 2))
        for (i in 1L..6L) {
            small.record(Disaster(app.many.economytycoon.core.EventId(i), Peril.STORM, RegionId(1), Tick(i), 100_000L, 1, Money.units(i), Money.ZERO))
            small.record(Claim(app.many.economytycoon.core.EventId(i), Peril.STORM, null, me, FacilityId(1), Tick(i), Money.units(i), Money.ZERO, Money.ZERO, Money.ZERO))
        }
        assertEquals(listOf(4L, 5L, 6L), small.disasters().map { it.id.value })
        assertEquals(listOf(5L, 6L), small.claims().map { it.disaster.value })
        assertNotNull(small.claimsOf(me).firstOrNull())
    }

    @Test fun uninsuredClaimsAreFlaggedAsSuch() {
        val c = Claim(app.many.economytycoon.core.EventId(1), Peril.FIRE, null, me, FacilityId(1), Tick(1), Money.units(3), Money.units(2), Money.ZERO, Money.ZERO)
        assertFalse(c.insured)
        assertEquals(Money.units(5), c.loss)
    }
}
