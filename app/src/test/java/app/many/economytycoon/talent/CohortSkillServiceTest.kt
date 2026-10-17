package app.many.economytycoon.talent

import app.many.economytycoon.core.RegionId
import app.many.economytycoon.talent.TalentTestSupport.fact
import app.many.economytycoon.talent.TalentTestSupport.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CohortSkillServiceTest {
    private val rig = ServiceRig()
    private val svc = rig.skills
    private val machinist = key(role = "machinist")
    private val machining = DefaultTaxonomy.MACHINING

    @Test fun growingACohortCreatesItsPicture() {
        val change = svc.reconcile(fact(machinist, 40L), rig.world)
        assertEquals(HeadcountChange(40L, 0L), change)
        val set = svc.get(machinist)!!
        assertEquals(setOf(DefaultTaxonomy.GENERAL, machining), set.skills().toSet())
        assertEquals(40L, set.dist(machining)!!.total)
        assertEquals(40L, set.dist(DefaultTaxonomy.GENERAL)!!.total)
    }

    @Test fun hiresComeFromThePoolWhenItHasPeople() {
        rig.markets.deposit(RegionId(1), machining, SkillDistribution.single(775_000L, 30L))
        svc.reconcile(fact(machinist, 20L), rig.world)
        assertEquals(10L, rig.markets.poolSize(RegionId(1), machining))
        assertTrue(svc.meanLevel(machinist)!! > 700_000L)
    }

    @Test fun generousEmployersGetTheBestOfThePool() {
        val g = SkillDistribution(); g.addAt(200_000L, 100L); g.addAt(800_000L, 100L)
        rig.markets.deposit(RegionId(1), machining, g)
        val rich = key(company = 1L); val stingy = key(company = 2L)
        svc.reconcile(fact(rich, 50L, wage = app.many.economytycoon.core.Money.units(130), marketWage = app.many.economytycoon.core.Money.units(100)), rig.world)
        svc.reconcile(fact(stingy, 50L, wage = app.many.economytycoon.core.Money.units(80), marketWage = app.many.economytycoon.core.Money.units(100)), rig.world)
        assertTrue(svc.meanLevel(rich)!! > svc.meanLevel(stingy)!!)
    }

    @Test fun anEmptyPoolStillStaffsTheCohortWithLocalPeople() {
        svc.reconcile(fact(machinist, 25L), rig.world)
        val mean = svc.meanLevel(machinist)!!
        assertTrue(Math.abs(mean - rig.markets.genericLevel(rig.world, RegionId(1), machining)) < 40_000L)
    }

    @Test fun educatedRegionsHireBetterPeople() {
        rig.world.r(1).education = 800_000L
        rig.world.r(2).education = 100_000L
        svc.reconcile(fact(key(region = 1L), 60L), rig.world)
        svc.reconcile(fact(key(region = 2L), 60L), rig.world)
        assertTrue(svc.meanLevel(key(region = 1L))!! > svc.meanLevel(key(region = 2L))!!)
    }

    @Test fun leaversGoBackToThePoolWithTheirSkills() {
        svc.reconcile(fact(machinist, 100L), rig.world)
        val mean = svc.meanLevel(machinist)!!
        val change = svc.reconcile(fact(machinist, 60L), rig.world)
        assertEquals(HeadcountChange(0L, 40L), change)
        assertEquals(60L, svc.get(machinist)!!.dist(machining)!!.total)
        assertEquals(60L, svc.get(machinist)!!.dist(DefaultTaxonomy.GENERAL)!!.total)
        assertEquals(40L, rig.markets.poolSize(RegionId(1), machining))
        assertTrue(Math.abs(rig.markets.pool(RegionId(1), machining).mean() - mean) < 60_000L)
    }

    @Test fun anUnchangedHeadcountChangesNothing() {
        svc.reconcile(fact(machinist, 30L), rig.world)
        val h = rig.state.stateHash()
        assertEquals(HeadcountChange(0L, 0L), svc.reconcile(fact(machinist, 30L), rig.world))
        assertEquals(h, rig.state.stateHash())
    }

    @Test fun rolesWithoutAProfileAreIgnored() {
        val odd = key(role = "astronaut")
        assertEquals(HeadcountChange(0L, 0L), svc.reconcile(fact(odd, 10L), rig.world))
        assertNull(svc.get(odd))
        assertNull(svc.productivity(odd))
        assertNull(svc.skillPpm(odd))
    }

    @Test fun certificatesDiluteWhenNewPeopleJoin() {
        svc.reconcile(fact(machinist, 50L), rig.world)
        svc.get(machinist)!!.certified[machining] = 400_000L
        svc.reconcile(fact(machinist, 100L), rig.world)
        assertEquals(200_000L, svc.get(machinist)!!.certifiedShare(machining))
    }

    @Test fun workingTeachesUpToTheCeiling() {
        svc.reconcile(fact(key(role = "laborer"), 100L), rig.world)
        val k = key(role = "laborer")
        val before = svc.meanLevel(k)!!
        repeat(24) { svc.learnMonthly(fact(k, 100L)) }
        val after = svc.meanLevel(k)!!
        assertTrue(after >= before)
        assertTrue(after <= SkillDynamics.ceilingFor(250_000L, svc.dynamics) + 50_000L)
    }

    @Test fun contentPeopleLearnFasterThanUnhappyOnes() {
        val a = key(company = 1L); val b = key(company = 2L)
        for (k in listOf(a, b)) {
            svc.reconcile(fact(k, 100L), rig.world)
            val d = svc.get(k)!!.dist(machining)!!; d.clear(); d.addAt(250_000L, 100L)
        }
        repeat(12) { svc.learnMonthly(fact(a, 100L, morale = 900_000L)); svc.learnMonthly(fact(b, 100L, morale = 100_000L)) }
        assertTrue(svc.meanLevel(a)!! > svc.meanLevel(b)!!)
    }

    @Test fun movingTakesTheBestAndKeepsEveryone() {
        val from = key(company = 1L, role = "machinist"); val to = key(company = 2L, role = "machinist")
        rig.markets.deposit(RegionId(1), machining, SkillDistribution.around(500_000L, 250_000L, 200L))
        svc.reconcile(fact(from, 100L), rig.world)
        svc.reconcile(fact(to, 10L), rig.world)
        val fromMean = svc.meanLevel(from)!!
        val moved = svc.move(rig.world, from, to, 20L, TakePolicy.BEST_FIRST)
        assertEquals(20L, moved)
        assertEquals(80L, svc.get(from)!!.dist(machining)!!.total)
        assertEquals(30L, svc.get(to)!!.dist(machining)!!.total)
        assertEquals(80L, svc.get(from)!!.dist(DefaultTaxonomy.GENERAL)!!.total)
        assertEquals(30L, svc.get(to)!!.dist(DefaultTaxonomy.GENERAL)!!.total)
        assertTrue(svc.meanLevel(from)!! < fromMean)
    }

    @Test fun movingCanRespectALevelFloor() {
        val from = key(company = 1L); val to = key(company = 2L)
        svc.reconcile(fact(from, 50L), rig.world)
        val d = svc.get(from)!!.dist(machining)!!; d.clear(); d.addAt(200_000L, 45L); d.addAt(900_000L, 5L)
        svc.reconcile(fact(to, 0L), rig.world)
        assertEquals(5L, svc.move(rig.world, from, to, 30L, TakePolicy.BEST_FIRST, minLevel = 800_000L))
    }

    @Test fun movingBetweenDifferentRolesFillsMissingSkillsWithLocalLevels() {
        val from = key(role = "machinist"); val to = key(company = 2L, role = "electrician")
        svc.reconcile(fact(from, 30L), rig.world)
        svc.reconcile(fact(to, 0L), rig.world)
        assertEquals(10L, svc.move(rig.world, from, to, 10L, TakePolicy.PROPORTIONAL))
        val set = svc.get(to)!!
        assertEquals(setOf(DefaultTaxonomy.ELECTRICAL, DefaultTaxonomy.MAINTENANCE), set.skills().toSet())
        assertEquals(10L, set.dist(DefaultTaxonomy.ELECTRICAL)!!.total)
        assertEquals(10L, set.dist(DefaultTaxonomy.MAINTENANCE)!!.total)
    }

    @Test fun movingFromAnUnknownCohortDoesNothing() {
        assertEquals(0L, svc.move(rig.world, key(company = 9L), key(company = 8L), 5L, TakePolicy.PROPORTIONAL))
    }

    @Test fun retiringACohortSendsEveryoneBackToTheirPool() {
        svc.reconcile(fact(machinist, 40L), rig.world)
        svc.retire(machinist, emptySet())
        assertNull(svc.get(machinist))
        assertEquals(40L, rig.markets.poolSize(RegionId(1), machining))
        svc.retire(machinist, emptySet())
    }

    @Test fun retiringSkipsCohortsStillReported() {
        svc.reconcile(fact(machinist, 40L), rig.world)
        svc.retire(machinist, setOf(machinist))
        assertNotNull(svc.get(machinist))
    }

    @Test fun productivityComparesTheCohortWithItsRole() {
        svc.reconcile(fact(machinist, 40L), rig.world)
        val set = svc.get(machinist)!!
        set.dists.getValue(machining).also { it.clear(); it.addAt(575_000L, 40L) }
        set.dists.getValue(DefaultTaxonomy.GENERAL).also { it.clear(); it.addAt(425_000L, 40L) }
        val p = svc.productivity(machinist)!!
        // band midpoints sit a hair above the requirement, so the cohort is a touch over par
        assertTrue(p.multiplierPpm in Ppm.ONE..1_060_000L)
        assertEquals(Ppm.ONE, p.matchPpm)
        assertEquals(SkillMatch.equivalentSkillPpm(p.multiplierPpm), svc.skillPpm(machinist))
    }

    @Test fun keysOfACompanyAreFound() {
        svc.reconcile(fact(key(company = 1L, role = "machinist"), 5L), rig.world)
        svc.reconcile(fact(key(company = 1L, role = "driver"), 5L), rig.world)
        svc.reconcile(fact(key(company = 2L, role = "driver"), 5L), rig.world)
        assertEquals(2, svc.keysOf(app.many.economytycoon.core.CompanyId(1)).size)
        assertEquals(3, svc.all().size)
    }
}
