package app.many.economytycoon.talent

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.talent.TalentTestSupport.fact
import app.many.economytycoon.talent.TalentTestSupport.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationServiceTest {
    private val world = FixedTalentWorld()
        .region(1L, country = 1L, working = 50_000L, wage = Money.units(60))
        .region(2L, country = 2L, working = 50_000L, wage = Money.units(120))
    private val rig = ServiceRig(world)
    private val svc = MigrationService(rig.state, rig.taxonomy, rig.markets, rig.skills)
    private val home = CountryId(1)
    private val rich = CountryId(2)
    private val eng = DefaultTaxonomy.ENGINEERING

    private fun deposit(region: Long, skill: SkillId, level: Long, n: Long) = rig.markets.deposit(RegionId(region), skill, SkillDistribution.single(level, n))
    private fun review(tick: Long = 30L, vararg facts: CohortFact) = svc.review(Tick(tick), world, TalentTestSupport.inputs(tick, *facts))
    private fun pool(region: Long, skill: SkillId = eng) = rig.markets.poolSize(RegionId(region), skill)

    @Test fun attractivenessFollowsPay() {
        assertTrue(svc.attractiveness(world, rich) > svc.attractiveness(world, home))
        assertTrue(Math.abs(svc.attractiveness(world, home) * 2 - svc.attractiveness(world, rich)) < 4_000L)
    }

    @Test fun livingConditionsLiftOrDullTheAppeal() {
        val base = svc.attractiveness(world, rich)
        world.r(2).happiness = 1_000_000L
        world.stability[rich] = 1_000_000L
        assertTrue(svc.attractiveness(world, rich) > base)
        world.r(2).happiness = 100_000L
        world.stability[rich] = 100_000L
        assertTrue(svc.attractiveness(world, rich) < base)
    }

    @Test fun nothingHappensOffTheReviewDay() {
        deposit(1L, eng, 800_000L, 1_000L)
        assertEquals(MigrationOutcome.NONE, review(tick = 31L))
        assertEquals(MigrationOutcome.NONE, review(tick = 0L))
        assertEquals(1_000L, pool(1L))
    }

    @Test fun peopleWaitUntilTheGapBeatsTheCostOfMoving() {
        world.r(2).wage = Money.units(66)
        deposit(1L, eng, 800_000L, 1_000L)
        review()
        assertEquals(1_000L, pool(1L))
        assertEquals(0L, pool(2L))
    }

    @Test fun aBigGapSendsPeopleAbroadWhoArriveInTheDestinationPool() {
        deposit(1L, eng, 800_000L, 2_000L)
        review()
        val left = 2_000L - pool(1L)
        assertTrue(left > 0L)
        assertEquals(left, pool(2L))
        assertEquals(left, svc.ledger(home).out)
        assertEquals(left, svc.ledger(rich).arrived)
        assertEquals(left, svc.diasporaOf(home))
        assertEquals(left, svc.diasporaIn(rich))
        assertEquals(left, svc.ledger(rich).visasUsed)
    }

    @Test fun peopleAreNeitherCreatedNorLostByMoving() {
        deposit(1L, eng, 800_000L, 3_000L)
        deposit(1L, DefaultTaxonomy.DESIGN, 600_000L, 1_000L)
        repeat(6) { review(30L * (it + 1)) }
        val total = pool(1L) + pool(2L) + pool(1L, DefaultTaxonomy.DESIGN) + pool(2L, DefaultTaxonomy.DESIGN)
        assertEquals(4_000L, total)
    }

    @Test fun arrivalsAreRecognisedAtAHaircut() {
        deposit(1L, eng, 800_000L, 2_000L)
        review()
        val arrivedMean = rig.markets.pool(RegionId(2), eng).mean()
        assertTrue(arrivedMean < 800_000L)
        assertTrue(arrivedMean >= 800_000L - svc.params.recognitionLossPpm - SkillDistribution.WIDTH)
    }

    @Test fun theBestLeaveFirst() {
        val g = SkillDistribution(); g.addAt(300_000L, 1_000L); g.addAt(900_000L, 1_000L)
        rig.markets.deposit(RegionId(1), eng, g)
        val before = g.mean()
        review()
        val stayed = rig.markets.pool(RegionId(1), eng).mean()
        val went = rig.markets.pool(RegionId(2), eng).mean()
        assertTrue(went > stayed)
        assertTrue(stayed < before)
    }

    @Test fun scarceSkillsAreMoreMobileThanBasicOnes() {
        deposit(1L, eng, 900_000L, 5_000L)
        deposit(1L, DefaultTaxonomy.GENERAL, 150_000L, 5_000L)
        review()
        val expertLeft = 5_000L - pool(1L)
        val basicLeft = 5_000L - pool(1L, DefaultTaxonomy.GENERAL)
        assertTrue(expertLeft > basicLeft * 2)
    }

    @Test fun aQuotaCapsHowManyMayArrive() {
        svc.setPolicy(rich, Ppm.ONE, 1_000L, false)
        deposit(1L, eng, 800_000L, 100_000L)
        review()
        // 50,000 working people x 0.1% a year
        assertEquals(50L, pool(2L))
        review(60L)
        assertEquals(50L, pool(2L))
        assertEquals(50L, svc.ledger(rich).visasUsed)
    }

    @Test fun theQuotaResetsWithTheYear() {
        svc.setPolicy(rich, Ppm.ONE, 1_000L, false)
        deposit(1L, eng, 800_000L, 100_000L)
        review()
        svc.rollYear()
        assertEquals(0L, svc.ledger(rich).visasUsed)
        review(60L)
        assertEquals(100L, pool(2L))
    }

    @Test fun aClosedDoorStopsEveryone() {
        svc.setPolicy(rich, 0L, 8_000L, false)
        deposit(1L, eng, 800_000L, 2_000L)
        review()
        assertEquals(2_000L, pool(1L))
    }

    @Test fun aSkilledOnlyPolicyTurnsAwayBasicWorkers() {
        svc.setPolicy(rich, Ppm.ONE, 100_000L, true)
        deposit(1L, DefaultTaxonomy.GENERAL, 200_000L, 3_000L)
        deposit(1L, eng, 800_000L, 3_000L)
        review()
        assertEquals(3_000L, pool(1L, DefaultTaxonomy.GENERAL))
        assertTrue(pool(1L) < 3_000L)
    }

    @Test fun halfOpenDoorsAdmitAProportion() {
        deposit(1L, eng, 800_000L, 4_000L)
        review()
        val open = pool(2L)
        val w2 = FixedTalentWorld().region(1L, country = 1L, working = 50_000L, wage = Money.units(60)).region(2L, country = 2L, working = 50_000L, wage = Money.units(120))
        val rig2 = ServiceRig(w2)
        val svc2 = MigrationService(rig2.state, rig2.taxonomy, rig2.markets, rig2.skills)
        svc2.setPolicy(rich, 500_000L, 8_000L, false)
        rig2.markets.deposit(RegionId(1), eng, SkillDistribution.single(800_000L, 4_000L))
        svc2.review(Tick(30), w2, TalentTestSupport.inputs(30L))
        assertTrue(rig2.markets.poolSize(RegionId(2), eng) < open)
        assertTrue(rig2.markets.poolSize(RegionId(2), eng) > 0L)
    }

    @Test fun bigWavesAreAnnounced() {
        deposit(1L, eng, 800_000L, 20_000L)
        val out = review()
        val wave = out.events.filterIsInstance<TalentEvent.MigrationWave>().single()
        assertEquals(home, wave.from)
        assertEquals(rich, wave.to)
        assertEquals(eng, wave.skill)
        assertTrue(wave.heads >= svc.params.waveThreshold)
    }

    @Test fun smallTricklesAreNotAnnounced() {
        deposit(1L, eng, 800_000L, 50L)
        assertTrue(review().events.isEmpty())
    }

    @Test fun employedPeopleLeaveToo() {
        val k = key(role = "engineer", region = 1L)
        val f = fact(k, 400L, wage = Money.units(100), marketWage = Money.units(160))
        rig.skills.reconcile(f, world)
        val out = review(30L, f)
        val e = out.effects.filterIsInstance<WorkforceEffect.Emigrate>().single()
        assertEquals(k, e.cohort)
        assertTrue(e.count > 0L)
        assertEquals(400L - e.count, rig.skills.get(k)!!.dist(eng)!!.total)
        assertEquals(400L - e.count, rig.skills.get(k)!!.dist(DefaultTaxonomy.ANALYSIS)!!.total)
        assertEquals(e.count, pool(2L))
    }

    @Test fun underpaidStaffAreMoreLikelyToGo() {
        val k1 = key(company = 1L, role = "engineer", region = 1L); val k2 = key(company = 2L, role = "engineer", region = 1L)
        val happy = fact(k1, 1_000L, wage = Money.units(160), marketWage = Money.units(160))
        val sour = fact(k2, 1_000L, wage = Money.units(100), marketWage = Money.units(160))
        rig.skills.reconcile(happy, world); rig.skills.reconcile(sour, world)
        val out = review(30L, happy, sour).effects.filterIsInstance<WorkforceEffect.Emigrate>()
        val a = out.single { it.cohort == k1 }.count
        val b = out.single { it.cohort == k2 }.count
        assertTrue(b > a)
    }

    @Test fun peopleInTheRichCountryDoNotLeaveForThePoorOne() {
        val k = key(role = "engineer", region = 2L)
        val f = fact(k, 400L)
        rig.skills.reconcile(f, world)
        assertTrue(review(30L, f).effects.isEmpty())
        assertEquals(400L, rig.skills.get(k)!!.dist(eng)!!.total)
    }

    @Test fun diasporaReturnsWhenHomeCatchesUp() {
        deposit(1L, eng, 800_000L, 4_000L)
        review()
        val abroad = svc.diasporaOf(home)
        assertTrue(abroad > 0L)
        world.r(1).wage = Money.units(125)
        val before = pool(1L)
        repeat(6) { review(60L + 30L * it) }
        assertTrue(pool(1L) > before)
        assertTrue(svc.diasporaOf(home) < abroad)
        assertTrue(svc.ledger(home).returned > 0L)
    }

    @Test fun returneesBringBackMoreSkill() {
        deposit(1L, eng, 800_000L, 4_000L)
        review()
        world.r(1).wage = Money.units(125)
        val abroadMean = rig.markets.pool(RegionId(2), eng).mean()
        review(60L)
        val homeMean = rig.markets.pool(RegionId(1), eng).mean()
        assertTrue(homeMean >= abroadMean - 1L)
    }

    @Test fun nobodyReturnsWhileHomeIsStillPoor() {
        deposit(1L, eng, 800_000L, 4_000L)
        review()
        val abroad = svc.diasporaOf(home)
        val back = svc.ledger(home).returned
        review(60L)
        assertEquals(back, svc.ledger(home).returned)
        assertTrue(svc.diasporaOf(home) >= abroad)
    }

    @Test fun flowFiguresFadeEachYear() {
        deposit(1L, eng, 900_000L, 9_000L)
        review()
        val out = svc.ledger(home).out
        svc.rollYear()
        assertEquals(out * 2 / 3, svc.ledger(home).out)
    }

    @Test fun brainDrainIsTheSkilledOutflowAgainstTheWorkingPopulation() {
        deposit(1L, eng, 900_000L, 9_000L)
        review()
        assertEquals(Ppm.ratio(svc.ledger(home).skilledOut, 50_000L), svc.brainDrainPpm(world, home))
        assertTrue(svc.brainDrainPpm(world, home) > 0L)
        assertEquals(0L, svc.brainDrainPpm(world, rich))
        assertTrue(svc.ledger(home).net > 0L && svc.ledger(rich).net < 0L)
        assertTrue(svc.ledger(home).skilledShareOfOutflow > 0L)
    }

    @Test fun aSingleCountryWorldHasNoMigration() {
        val solo = FixedTalentWorld().region(1L).region(2L)
        val r = ServiceRig(solo)
        val s = MigrationService(r.state, r.taxonomy, r.markets, r.skills)
        r.markets.deposit(RegionId(1), eng, SkillDistribution.single(800_000L, 100L))
        assertEquals(MigrationOutcome.NONE, s.review(Tick(30), solo, TalentTestSupport.inputs(30L)))
    }

    @Test fun wageGapIsRelativeToTheOrigin() {
        assertTrue(svc.wageGapPpm(world, home, rich) > 900_000L)
        assertTrue(svc.wageGapPpm(world, rich, home) < 0L)
    }

    @Test fun policiesDefaultToOpenWithTheConfiguredQuota() {
        val p = svc.policy(CountryId(7))
        assertEquals(Ppm.ONE, p.opennessPpm)
        assertEquals(svc.params.defaultQuotaPpm, p.quotaPpm)
        assertFalse(p.skilledOnly)
        svc.setPolicy(CountryId(7), 2_000_000L, -5L, true)
        assertEquals(Ppm.ONE, svc.policy(CountryId(7)).opennessPpm)
        assertEquals(0L, svc.policy(CountryId(7)).quotaPpm)
    }

    @Test fun sameInputsGiveTheSameHash() {
        fun run(): TalentState {
            val w = FixedTalentWorld().region(1L, country = 1L, working = 50_000L, wage = Money.units(60)).region(2L, country = 2L, working = 50_000L, wage = Money.units(120))
            val r = ServiceRig(w); val s = MigrationService(r.state, r.taxonomy, r.markets, r.skills)
            r.markets.deposit(RegionId(1), eng, SkillDistribution.around(600_000L, 200_000L, 5_000L))
            for (m in 1L..12L) s.review(Tick(30 * m), w, TalentTestSupport.inputs(30 * m))
            return r.state
        }
        assertEquals(run().stateHash(), run().stateHash())
    }
}
