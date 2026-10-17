package app.many.economytycoon.talent

import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillMarketServiceTest {
    private val state = TalentState()
    private val svc = SkillMarketService(state, TalentTestSupport.taxonomy)
    private val world = FixedTalentWorld.standard()
    private val r1 = RegionId(1)
    private val eng = DefaultTaxonomy.ENGINEERING
    private val key = PoolKey(r1, eng)

    private fun month(tick: Long, openings: Long = 0L, employed: Long = 0L, events: MutableList<TalentEvent> = ArrayList()): List<TalentEvent> {
        svc.monthly(Tick(tick), mapOf(key to openings), mapOf(key to employed), events)
        return events
    }

    @Test fun depositsBuildThePoolAndCountAsArrivals() {
        svc.deposit(r1, eng, SkillDistribution.single(700_000L, 40L))
        assertEquals(40L, svc.poolSize(r1, eng))
        assertEquals(40L, svc.poolSize(r1))
        assertEquals(40L, svc.market(r1, eng)!!.inflowYear)
        svc.release(r1, eng, SkillDistribution.single(700_000L, 10L))
        assertEquals(50L, svc.poolSize(r1, eng))
        assertEquals(40L, svc.market(r1, eng)!!.inflowYear)
    }

    @Test fun poolViewsAreCopies() {
        svc.deposit(r1, eng, SkillDistribution.single(700_000L, 5L))
        svc.pool(r1, eng).addAt(100_000L, 100L)
        assertEquals(5L, svc.poolSize(r1, eng))
        assertTrue(svc.pool(RegionId(9), eng).isEmpty)
    }

    @Test fun hiringTakesTheBestFirstForGenerousEmployers() {
        val group = SkillDistribution(); group.addAt(300_000L, 50L); group.addAt(850_000L, 50L)
        svc.deposit(r1, eng, group)
        val picky = svc.hire(world, r1, eng, 40L, bestShare = Ppm.ONE)
        assertEquals(40L, picky.total)
        assertEquals(40L, picky.count(17))
        val average = svc.hire(world, r1, eng, 20L, bestShare = 0L)
        assertEquals(20L, average.total)
        assertTrue(average.count(6) > 0L)
        assertEquals(40L, svc.poolSize(r1, eng))
    }

    @Test fun anEmptyPoolStillSuppliesLocalWorkers() {
        val got = svc.hire(world, r1, eng, 30L, 500_000L)
        assertEquals(30L, got.total)
        assertTrue(Math.abs(svc.genericLevel(world, r1, eng) - got.mean()) < 40_000L)
        assertEquals(0L, svc.poolSize(r1, eng))
    }

    @Test fun aShortPoolIsToppedUpWithGenericHires() {
        svc.deposit(r1, eng, SkillDistribution.single(900_000L, 5L))
        val got = svc.hire(world, r1, eng, 25L, Ppm.ONE)
        assertEquals(25L, got.total)
        assertEquals(5L, got.count(18))
        assertEquals(0L, svc.poolSize(r1, eng))
    }

    @Test fun genericLevelsRiseWithRegionalEducation() {
        world.r(1).education = 200_000L
        val low = svc.genericLevel(world, r1, eng)
        world.r(1).education = 800_000L
        val high = svc.genericLevel(world, r1, eng)
        assertTrue(high > low)
        assertTrue(svc.genericLevel(world, r1, DefaultTaxonomy.GENERAL) < svc.genericLevel(world, r1, eng) + 100_000L)
        assertEquals(300_000L, svc.genericLevel(world, r1, DefaultTaxonomy.SAFETY))
    }

    @Test fun requiredLevelIsTheStrictestRoleThatReliesOnTheSkill() {
        assertEquals(700_000L, svc.requiredLevel(DefaultTaxonomy.ENGINEERING))
        assertEquals(0L, svc.requiredLevel(DefaultTaxonomy.COMMUNICATION))
    }

    @Test fun idlePeopleForgetAndSomeLeaveThePool() {
        svc.deposit(r1, eng, SkillDistribution.single(700_000L, 1_000L))
        val before = svc.pool(r1, eng).mean()
        repeat(12) { month(30L * (it + 1)) }
        val after = svc.pool(r1, eng)
        assertTrue(after.mean() < before)
        assertTrue(after.total < 1_000L && after.total > 600L)
    }

    @Test fun unmetDemandPushesWagePremiumsUp() {
        svc.deposit(r1, eng, SkillDistribution.single(800_000L, 20L))
        repeat(12) { month(30L * (it + 1), openings = 200L, employed = 400L) }
        val m = svc.market(r1, eng)!!
        assertTrue(m.tightnessPpm > 1_500_000L)
        assertTrue(m.premiumPpm > 1_100_000L)
        assertTrue(svc.premium(r1, eng) == m.premiumPpm)
    }

    @Test fun aGlutOfSkilledPeopleLowersThePremium() {
        svc.deposit(r1, eng, SkillDistribution.single(800_000L, 5_000L))
        repeat(12) { month(30L * (it + 1), openings = 5L, employed = 100L) }
        assertTrue(svc.premium(r1, eng) < Ppm.ONE)
        assertTrue(svc.premium(r1, eng) >= svc.params.minPremiumPpm)
    }

    @Test fun premiaStayWithinTheirBounds() {
        repeat(60) { month(30L * (it + 1), openings = 100_000L, employed = 1_000L) }
        assertTrue(svc.premium(r1, eng) > 1_500_000L)
        assertTrue(svc.premium(r1, eng) <= svc.params.maxPremiumPpm)
    }

    @Test fun aShortageIsRaisedOnceAndEasesWhenSupplyReturns() {
        val events = ArrayList<TalentEvent>()
        repeat(6) { month(30L * (it + 1), openings = 300L, employed = 300L, events = events) }
        val raised = events.filterIsInstance<TalentEvent.ShortageRaised>()
        assertEquals(1, raised.size)
        assertEquals(ShortageKind.QUANTITY, raised[0].kind)
        assertEquals(1, svc.shortages().size)
        svc.deposit(r1, eng, SkillDistribution.single(850_000L, 50_000L))
        repeat(12) { month(300L + 30L * it, openings = 0L, employed = 300L, events = events) }
        assertEquals(1, events.filterIsInstance<TalentEvent.ShortageEased>().size)
        assertTrue(svc.shortages().isEmpty())
    }

    @Test fun peopleWhoLackTheSkillAreAQualityShortage() {
        svc.deposit(r1, eng, SkillDistribution.single(300_000L, 400L))
        val events = ArrayList<TalentEvent>()
        repeat(3) { month(30L * (it + 1), openings = 50L, employed = 100L, events = events) }
        val m = svc.market(r1, eng)!!
        assertTrue(m.shortage == ShortageKind.QUALITY || m.shortage == ShortageKind.BOTH)
        assertTrue(m.qualityGapPpm > 300_000L)
    }

    @Test fun noDemandMeansNoShortage() {
        svc.deposit(r1, eng, SkillDistribution.single(300_000L, 10L))
        month(30L, openings = 0L, employed = 0L)
        assertEquals(ShortageKind.NONE, svc.market(r1, eng)!!.shortage)
    }

    @Test fun shortagesAreRankedWorstFirst() {
        svc.deposit(r1, eng, SkillDistribution.single(850_000L, 25L))
        val s1 = PoolKey(r1, eng); val s2 = PoolKey(r1, DefaultTaxonomy.PROGRAMMING)
        repeat(8) { svc.monthly(Tick(30L * (it + 1)), mapOf(s1 to 100L, s2 to 600L), mapOf(s1 to 100L, s2 to 600L), ArrayList()) }
        val rows = svc.shortages(r1)
        assertEquals(2, rows.size)
        assertEquals(DefaultTaxonomy.PROGRAMMING, rows[0].skill)
        assertTrue(rows[0].severity >= rows[1].severity)
        assertTrue(svc.shortages(RegionId(2)).isEmpty())
    }

    @Test fun skillsNobodyPracticesAsATradeHaveNoMarket() {
        svc.monthly(Tick(30), mapOf(PoolKey(r1, DefaultTaxonomy.COMMUNICATION) to 10L), emptyMap(), ArrayList())
        assertNull(svc.market(r1, DefaultTaxonomy.COMMUNICATION))
    }

    @Test fun skilledPeopleEarnMoreThanTheRoleMinimum() {
        val above = svc.skillWageIndex(800_000L, 600_000L)
        val at = svc.skillWageIndex(600_000L, 600_000L)
        val below = svc.skillWageIndex(300_000L, 600_000L)
        assertEquals(Ppm.ONE, at)
        assertTrue(above > at && at > below)
        assertEquals(svc.params.minSkillWagePpm, svc.skillWageIndex(0L, 1_000_000L).coerceAtLeast(svc.params.minSkillWagePpm))
        assertTrue(svc.skillWageIndex(1_000_000L, 0L) <= svc.params.maxSkillWagePpm)
    }

    @Test fun wageIndexCombinesSkillAndScarcity() {
        val profile = TalentTestSupport.taxonomy.profiles().first { it.primary == eng }
        val base = svc.wageIndexFor(profile, r1, profile.requirements.first().requiredPpm)
        repeat(12) { month(30L * (it + 1), openings = 500L, employed = 500L) }
        assertTrue(svc.wageIndexFor(profile, r1, profile.requirements.first().requiredPpm) > base)
    }

    @Test fun rollingTheYearFadesArrivalCounts() {
        svc.deposit(r1, eng, SkillDistribution.single(700_000L, 300L))
        svc.rollYear()
        assertEquals(200L, svc.market(r1, eng)!!.inflowYear)
    }

    @Test fun theMarketsHashWithTheState() {
        val before = state.stateHash()
        svc.deposit(r1, eng, SkillDistribution.single(700_000L, 3L))
        assertTrue(before != state.stateHash())
    }
}
