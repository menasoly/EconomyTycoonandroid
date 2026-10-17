package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandServiceTest {
    private val rig = ServiceRig()
    private val svc = BrandService(rig.state, rig.taxonomy, rig.markets, 3L)
    private val co = CompanyId(1)
    private val region = RegionId(1)
    private val role = RoleId("machinist")
    private val key = TalentTestSupport.key(company = 1L, role = "machinist")

    private fun inputs(tick: Long, wage: Long = 95L, morale: Long = 650_000L, open: Long = 10L, cash: Long = 1_000_000L) =
        TalentTestSupport.inputs(tick, TalentTestSupport.fact(key, 50, target = 50 + open, wage = Money.units(wage), morale = morale), cash = cash)

    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule
    private fun launch(budget: Long = 20_000L, days: Int = 90, tick: Long = 0L) = svc.launch(co, region, role, Money.units(budget), days, Tick(tick), inputs(tick))

    @Test fun unknownCompaniesAreNeutral() {
        assertEquals(500_000L, svc.brandOf(co, region))
        assertEquals(500_000L, svc.appeal(co, region))
        assertEquals(0L, svc.hireBoostPpm(co, region, role))
    }

    @Test fun goodPayAndHappyStaffBuildAppeal() {
        for (m in 1..12) svc.monthly(Tick(30L * m), inputs(30L * m, wage = 130L, morale = 900_000L))
        assertTrue(svc.appeal(co, region) > 600_000L)
    }

    @Test fun poorPayAndLowMoraleHurtAppeal() {
        for (m in 1..12) svc.monthly(Tick(30L * m), inputs(30L * m, wage = 60L, morale = 200_000L))
        assertTrue(svc.appeal(co, region) < 400_000L)
    }

    @Test fun appealStaysInBounds() {
        for (m in 1..60) svc.monthly(Tick(30L * m), inputs(30L * m, wage = 1_000L, morale = 1_000_000L))
        assertTrue(svc.appeal(co, region) <= svc.params.maxAppealPpm)
        for (m in 61..140) svc.monthly(Tick(30L * m), inputs(30L * m, wage = 1L, morale = 0L))
        assertTrue(svc.appeal(co, region) >= svc.params.minAppealPpm)
    }

    @Test fun trainingInvestmentHelpsTheName() {
        val other = BrandService(ServiceRig().state, rig.taxonomy, rig.markets, 3L)
        for (m in 1..12) { svc.monthly(Tick(30L * m), inputs(30L * m)) { 800_000L }; other.monthly(Tick(30L * m), inputs(30L * m)) }
        assertTrue(svc.appeal(co, region) > other.appeal(co, region))
    }

    @Test fun advertisingMakesTheCompanyKnown() {
        val (c, intents) = (launch() as Outcome.Ok).value
        assertEquals(1, intents.size)
        assertEquals(Money.units(20_000), intents[0].total)
        svc.monthly(Tick(30), inputs(30))
        val a1 = svc.brand(co, region)!!.awarenessPpm
        assertTrue(a1 > 0L)
        svc.monthly(Tick(60), inputs(60))
        assertTrue(svc.brand(co, region)!!.awarenessPpm > a1)
        assertTrue(c.isActive)
    }

    @Test fun awarenessFadesWhenAdvertisingStops() {
        launch(days = 30)
        svc.monthly(Tick(30), inputs(30))
        val peak = svc.brand(co, region)!!.awarenessPpm
        for (m in 2..10) svc.monthly(Tick(30L * m), inputs(30L * m))
        assertTrue(svc.brand(co, region)!!.awarenessPpm < peak)
    }

    @Test fun biggerBudgetsBuyMoreAwarenessButWithDiminishingReturns() {
        val small = BrandService(ServiceRig().state, rig.taxonomy, rig.markets, 3L)
        val big = BrandService(ServiceRig().state, rig.taxonomy, rig.markets, 3L)
        small.launch(co, region, role, Money.units(5_000), 30, Tick(0), inputs(0)); big.launch(co, region, role, Money.units(50_000), 30, Tick(0), inputs(0))
        small.monthly(Tick(30), inputs(30)); big.monthly(Tick(30), inputs(30))
        val a = small.brand(co, region)!!.awarenessPpm; val b = big.brand(co, region)!!.awarenessPpm
        assertTrue(b > a)
        assertTrue(b < 10 * a)
    }

    @Test fun launchRulesAreChecked() {
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.launch(co, region, RoleId("astronaut"), Money.units(5_000), 60, Tick(0), inputs(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(launch(days = 5)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(launch(days = 400)))
        assertEquals(Rules.INVALID_PRICE, rule(launch(budget = 10L)))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(svc.launch(co, region, role, Money.units(20_000), 60, Tick(0), inputs(0, cash = 100L))))
        assertTrue(launch() is Outcome.Ok)
        assertEquals(Rules.DUPLICATE, rule(launch()))
    }

    @Test fun campaignsPerCompanyAreLimited() {
        val roles = listOf("machinist", "cook", "clerk", "farmhand", "driver").map { RoleId(it) }.filter { rig.taxonomy.profile(it) != null }
        var n = 0
        for (r in roles) {
            val res = svc.launch(co, region, r, Money.units(5_000), 60, Tick(0), inputs(0))
            if (res is Outcome.Ok) n++ else { assertEquals(Rules.LIMIT_EXCEEDED, rule(res)); break }
        }
        assertTrue(n <= svc.params.maxCampaignsPerCompany)
    }

    @Test fun campaignsHireForOpenPostsFromThePool() {
        val primary = rig.taxonomy.profile(role)!!.primary
        rig.markets.release(region, primary, SkillDistribution.around(500_000L, 100_000L, 20_000L))
        launch(budget = 40_000L, days = 90)
        var hired = 0L
        for (m in 1..3) {
            val r = svc.monthly(Tick(30L * m), inputs(30L * m))
            hired += r.effects.filterIsInstance<WorkforceEffect.Hire>().sumOf { it.count }
        }
        assertTrue(hired in 1L..30L)
    }

    @Test fun hiresNeverExceedOpenings() {
        val primary = rig.taxonomy.profile(role)!!.primary
        rig.markets.release(region, primary, SkillDistribution.around(500_000L, 100_000L, 5_000_000L))
        launch(budget = 100_000L, days = 90)
        val r = svc.monthly(Tick(30), inputs(30, open = 3L))
        assertTrue(r.effects.filterIsInstance<WorkforceEffect.Hire>().sumOf { it.count } <= 3L)
    }

    @Test fun nobodyIsHiredWithoutOpenings() {
        launch()
        val r = svc.monthly(Tick(30), inputs(30, open = 0L))
        assertTrue(r.effects.isEmpty())
    }

    @Test fun campaignsEndOnTime() {
        val c = (launch(days = 60) as Outcome.Ok).value.first
        svc.monthly(Tick(30), inputs(30))
        assertTrue(c.isActive)
        val r = svc.monthly(Tick(60), inputs(60))
        assertEquals(CampaignStatus.DONE, c.status)
        assertTrue(r.events.any { it is TalentEvent.CampaignEnded })
    }

    @Test fun activeCampaignsRaiseTheShareOfBestHires() {
        assertEquals(0L, svc.hireBoostPpm(co, region, role))
        launch(); svc.monthly(Tick(30), inputs(30))
        assertTrue(svc.hireBoostPpm(co, region, role) > 0L)
        assertEquals(0L, svc.hireBoostPpm(co, region, RoleId("cook")))
    }

    @Test fun cancellingStopsTheCampaign() {
        val c = (launch() as Outcome.Ok).value.first
        assertEquals(Rules.NOT_OWNER, rule(svc.cancel(c.id, CompanyId(2))))
        assertTrue(svc.cancel(c.id, co) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, rule(svc.cancel(c.id, co)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.cancel(RecruitCampaignId(55), co)))
        assertFalse(c.isActive)
    }

    @Test fun finishedCampaignsArePruned() {
        val c = (launch(days = 30) as Outcome.Ok).value.first
        svc.monthly(Tick(30), inputs(30))
        svc.monthly(Tick(30L + svc.params.keepDays + 30L), inputs(450))
        assertTrue(svc.campaign(c.id) == null)
    }

    @Test fun bigEmployersAreKnownWithoutAdvertising() {
        val big = TalentTestSupport.inputs(30, TalentTestSupport.fact(key, 3_000))
        svc.monthly(Tick(30), big)
        assertTrue(svc.brand(co, region)!!.awarenessPpm >= svc.params.fameFloorPpm - 1L)
    }

    @Test fun brandHashesChangeWithState() {
        val before = rig.state.stateHash()
        svc.monthly(Tick(30), inputs(30))
        assertTrue(before != rig.state.stateHash())
    }
}
