package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerServiceTest {
    private val rig = ServiceRig()
    private val svc = CareerService(rig.state, rig.taxonomy, rig.skills)
    private val co = CompanyId(1)
    private val low = TalentTestSupport.key(role = "machinist")
    private val high = TalentTestSupport.key(role = "engineer")

    private fun rung(key: app.many.economytycoon.workforce.CohortKey, level: Long = 400_000L, tenure: Long = 360L, raise: Long = 100_000L) = Rung(key, tenure, level, raise)

    private fun inputs(tick: Long = 100L, lowHeads: Long = 100, highHeads: Long = 10, highTarget: Long = 20, tenure: Long = 720L) = TalentTestSupport.inputs(
        tick,
        TalentTestSupport.fact(low, lowHeads, wage = Money.units(95), tenure = tenure),
        TalentTestSupport.fact(high, highHeads, target = highTarget, wage = Money.units(160), tenure = 360L),
    )

    private fun ladder(vararg rungs: Rung = arrayOf(rung(low, 0L, 0L, 0L), rung(high))): CareerLadder =
        (svc.create(co, "Shop floor to engineering", rungs.toList()) as Outcome.Ok).value

    private fun seeded(i: TalentInputs = inputs()) { for (f in i.cohorts) rig.skills.reconcile(f, rig.world) }
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    @Test fun laddersNeedSensibleRungs() {
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.create(co, " ", listOf(rung(low), rung(high)))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.create(co, "x", listOf(rung(low)))))
        assertEquals(Rules.NOT_OWNER, rule(svc.create(CompanyId(9), "x", listOf(rung(low), rung(high)))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.create(co, "x", listOf(rung(low), rung(TalentTestSupport.key(role = "astronaut"))))))
        assertEquals(Rules.DUPLICATE, rule(svc.create(co, "x", listOf(rung(low), rung(low)))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(svc.create(co, "x", listOf(rung(low), rung(high, level = 2_000_000L)))))
        assertTrue(svc.create(co, "ok", listOf(rung(low), rung(high))) is Outcome.Ok)
    }

    @Test fun laddersPerCompanyAreLimited() {
        repeat(svc.params.maxLaddersPerCompany) { ladder() }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(svc.create(co, "one more", listOf(rung(low), rung(high)))))
    }

    @Test fun reviewsPromoteIntoOpenPosts() {
        seeded(); val l = ladder()
        val r = svc.advance(Tick(100), rig.world, inputs())
        val t = r.effects.filterIsInstance<WorkforceEffect.Transfer>().single()
        assertEquals(low, t.from); assertEquals(high, t.to)
        assertTrue(t.count in 1L..10L)
        assertEquals(t.count, l.promotions)
        assertTrue(r.events.single() is TalentEvent.Promoted)
        assertTrue(r.effects.any { it is WorkforceEffect.Morale && it.deltaPpm > 0L })
    }

    @Test fun promotionsNeverExceedOpenings() {
        seeded(inputs(highHeads = 18)); ladder()
        val r = svc.advance(Tick(100), rig.world, inputs(highHeads = 18))
        assertTrue(r.effects.filterIsInstance<WorkforceEffect.Transfer>().sumOf { it.count } <= 2L)
    }

    @Test fun noOpeningNoPromotion() {
        seeded(inputs(highHeads = 20)); ladder()
        val r = svc.advance(Tick(100), rig.world, inputs(highHeads = 20))
        assertTrue(r.effects.isEmpty())
    }

    @Test fun tenureIsRequired() {
        seeded(); ladder(rung(low, 0L, 0L, 0L), rung(high, tenure = 2_000L))
        assertTrue(svc.advance(Tick(100), rig.world, inputs(tenure = 100L)).effects.isEmpty())
    }

    @Test fun skillIsRequired() {
        seeded(); ladder(rung(low, 0L, 0L, 0L), rung(high, level = 999_999L))
        assertTrue(svc.advance(Tick(100), rig.world, inputs()).effects.isEmpty())
    }

    @Test fun theBestAreTheOnesWhoMoveUp() {
        seeded()
        val primary = rig.taxonomy.profile(low.role)!!.primary
        val before = rig.skills.get(low)!!.dist(primary)!!.mean()
        ladder()
        svc.advance(Tick(100), rig.world, inputs())
        assertTrue(rig.skills.get(low)!!.dist(primary)!!.mean() <= before)
        assertTrue(rig.skills.get(high)!!.heads(rig.taxonomy.profile(high.role)!!.primary) > 10L)
    }

    @Test fun skillPicturesStayInStepWithHeadcounts() {
        seeded(); ladder()
        val r = svc.advance(Tick(100), rig.world, inputs())
        val n = r.effects.filterIsInstance<WorkforceEffect.Transfer>().sumOf { it.count }
        assertEquals(100L - n, rig.skills.get(low)!!.heads(rig.taxonomy.profile(low.role)!!.primary))
        assertEquals(10L + n, rig.skills.get(high)!!.heads(rig.taxonomy.profile(high.role)!!.primary))
    }

    @Test fun reviewsKeepTheirInterval() {
        seeded(); ladder()
        assertFalse(svc.advance(Tick(100), rig.world, inputs()).effects.isEmpty())
        assertTrue(svc.advance(Tick(110), rig.world, inputs(110)).effects.isEmpty())
        assertFalse(svc.advance(Tick(100 + svc.params.reviewIntervalDays.toLong()), rig.world, inputs(190)).effects.isEmpty())
    }

    @Test fun disabledLaddersAreSkipped() {
        seeded(); val l = ladder()
        assertTrue(svc.setEnabled(l.id, co, false) is Outcome.Ok)
        assertTrue(svc.advance(Tick(100), rig.world, inputs()).effects.isEmpty())
        assertEquals(Rules.NOT_OWNER, rule(svc.setEnabled(l.id, CompanyId(2), true)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.setEnabled(LadderId(99), co, true)))
    }

    @Test fun removingALadder() {
        val l = ladder()
        assertEquals(Rules.NOT_OWNER, rule(svc.remove(l.id, CompanyId(2))))
        assertTrue(svc.remove(l.id, co) is Outcome.Ok)
        assertNull(svc.ladder(l.id))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(svc.remove(l.id, co)))
    }

    @Test fun previewsExplainWhyNobodyMovesOrWhatWouldHappen() {
        seeded(); val l = ladder()
        val p = svc.preview(l, inputs()).single()
        assertNull(p.blockedBy)
        assertTrue(p.planned > 0L && p.planned <= p.openings && p.planned <= p.eligible)
        assertTrue(p.newWage >= Money.units(160))
        assertTrue(p.extraWagePerDay.raw > 0L)
        val full = svc.preview(l, inputs(highHeads = 20)).single()
        assertEquals("no opening above", full.blockedBy)
        assertEquals(0L, full.planned)
        val missing = svc.preview(l, TalentTestSupport.inputs(100)).single()
        assertNotNull(missing.blockedBy)
    }

    @Test fun theCohortBelowKeepsSomeone() {
        seeded(inputs(lowHeads = 1)); val l = ladder()
        assertEquals(0L, svc.preview(l, inputs(lowHeads = 1)).single().planned)
    }

    @Test fun threeRungLaddersFillFromTheTopDown() {
        val mid = TalentTestSupport.key(role = "maintenance_tech")
        if (rig.taxonomy.profile(mid.role) == null) return
        val i = TalentTestSupport.inputs(
            100,
            TalentTestSupport.fact(low, 100, tenure = 720L), TalentTestSupport.fact(mid, 20, target = 20, tenure = 720L), TalentTestSupport.fact(high, 10, target = 15, tenure = 720L),
        )
        seeded(i)
        ladder(rung(low, 0L, 0L, 0L), rung(mid, 0L, 0L, 0L), rung(high, 0L, 0L, 0L))
        val r = svc.advance(Tick(100), rig.world, i)
        assertTrue(r.effects.filterIsInstance<WorkforceEffect.Transfer>().isNotEmpty())
    }

    @Test fun reviewsAreDeterministic() {
        fun run(): app.many.economytycoon.core.StateHash {
            val r = ServiceRig(); val s = CareerService(r.state, r.taxonomy, r.skills)
            for (f in inputs().cohorts) r.skills.reconcile(f, r.world)
            s.create(co, "n", listOf(rung(low, 0L, 0L, 0L), rung(high)))
            repeat(4) { s.advance(Tick(100L + 90L * it), r.world, inputs(100L + 90L * it)) }
            return r.state.stateHash()
        }
        assertEquals(run(), run())
    }
}
