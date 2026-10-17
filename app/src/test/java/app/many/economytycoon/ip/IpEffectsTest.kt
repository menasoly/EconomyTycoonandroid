package app.many.economytycoon.ip

import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpEffectsTest {
    private fun fx(r: Rig) = IpEffects(r.state, r.book, r.disputes, r.pools, r.params)
    private val now = Tick(100)

    @Test fun secretEdgeScalesWithProtectionAndShrinksWithExposure() {
        assertEquals(0L, EffectRules.secretEdgePpm(0L, 0L))
        assertEquals(EffectRules.SECRET_EDGE_MAX_PPM, EffectRules.secretEdgePpm(1_000_000L, 0L))
        assertEquals(0L, EffectRules.secretEdgePpm(1_000_000L, 1_000_000L))
        assertTrue(EffectRules.secretEdgePpm(800_000L, 200_000L) < EffectRules.secretEdgePpm(800_000L, 0L))
        assertTrue(EffectRules.secretEdgePpm(900_000L, 0L) > EffectRules.secretEdgePpm(300_000L, 0L))
    }

    @Test fun designPenaltiesAreCapped() {
        val dp = DesignAroundParams()
        assertEquals(dp.productCostPenaltyPpm * 2, EffectRules.designCostPenaltyPpm(dp, 2))
        assertEquals(EffectRules.MAX_DESIGN_PENALTY_PPM, EffectRules.designCostPenaltyPpm(dp, 10_000))
        assertEquals(EffectRules.MAX_DESIGN_PENALTY_PPM, EffectRules.designQualityPenaltyPpm(dp, 10_000))
        assertEquals(0L, EffectRules.designCostPenaltyPpm(dp, 0))
    }

    @Test fun worstStatusPrecedence() {
        assertEquals(RightsStatus.FREE, EffectRules.worst(emptyList()))
        assertEquals(RightsStatus.ENJOINED, EffectRules.worst(listOf(RightsStatus.OWNED, RightsStatus.ENJOINED, RightsStatus.INFRINGING)))
        assertEquals(RightsStatus.INFRINGING, EffectRules.worst(listOf(RightsStatus.LICENSED, RightsStatus.INFRINGING)))
        assertEquals(RightsStatus.CLEARED, EffectRules.worst(listOf(RightsStatus.LICENSED, RightsStatus.CLEARED)))
        assertEquals(RightsStatus.LICENSED, EffectRules.worst(listOf(RightsStatus.OWNED, RightsStatus.LICENSED)))
    }

    @Test fun unpatentedTechnologyIsFree() {
        val r = Rig()
        val e = fx(r).effects(b, milling, us, null, now)
        assertEquals(RightsStatus.FREE, e.status)
        assertEquals(0L, e.costDeltaPpm)
        assertFalse(e.blocked)
    }

    @Test fun ownerLicenseeAndStrangerAreTreatedDifferently() {
        val r = Rig(); val p = r.granted(a, milling)
        r.sign(a, b, listOf(p.id), r.percent(4))
        val f = fx(r)
        assertEquals(RightsStatus.OWNED, f.rightsFor(a, milling, us, null, now))
        assertEquals(RightsStatus.LICENSED, f.rightsFor(b, milling, us, null, now))
        assertEquals(RightsStatus.INFRINGING, f.rightsFor(c, milling, us, null, now))
        assertEquals(RightsStatus.FREE, f.rightsFor(c, milling, eu, null, now))
    }

    @Test fun royaltyStackAddsEveryCoveringLicence() {
        val r = Rig(); val p = r.granted(a, milling); val q = r.granted(c, milling)
        r.sign(a, b, listOf(p.id), r.percent(4))
        r.sign(c, b, listOf(q.id), r.percent(3))
        assertEquals(70_000L, fx(r).royaltyStackPpm(b, milling, us, null, now))
        assertEquals(0L, fx(r).royaltyStackPpm(a, milling, us, null, now))
        assertEquals(0L, fx(r).royaltyStackPpm(b, ovens, us, null, now))
    }

    @Test fun clearanceCostsAlittleCostAndQuality() {
        val r = Rig(); val p = r.granted(a, milling)
        r.state.clearanceSet.add(Clearance(b, p.id, Tick(1)))
        val e = fx(r).effects(b, milling, us, null, now)
        assertEquals(RightsStatus.CLEARED, e.status)
        assertEquals(r.params.designAround.productCostPenaltyPpm, e.costDeltaPpm)
        assertEquals(-r.params.designAround.productQualityPenaltyPpm, e.qualityDeltaPpm)
        assertTrue(e.notes.isNotEmpty())
    }

    @Test fun injunctionBlocksTheProduct() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        val d = r.disputes.report(r.run(10), IpOwner.company(a), p.id, b, us).getOrThrow()
        d.injunctionUntil = Tick(500)
        val e = fx(r).effects(b, milling, us, null, now)
        assertEquals(RightsStatus.ENJOINED, e.status)
        assertTrue(e.blocked)
        assertFalse(fx(r).effects(b, milling, us, null, Tick(600)).blocked)
    }

    @Test fun liveSecretLowersCost() {
        val r = Rig()
        r.teach(a, milling)
        val s = r.secrets.keep(r.run(1), a, milling).getOrThrow()
        s.protectionPpm = 800_000L
        s.exposurePpm = 0L
        val e = fx(r).effects(a, milling, us, null, now)
        assertTrue(e.costDeltaPpm < 0L)
        assertEquals(-EffectRules.secretEdgePpm(800_000L, 0L), e.costDeltaPpm)
    }

    @Test fun forTechsReturnsEntriesInIdOrder() {
        val r = Rig()
        val m = fx(r).forTechs(a, listOf(ovens, milling), us, null, now)
        assertEquals(listOf("milling", "ovens"), m.keys.map { it.key })
    }

    @Test fun moneyFromRoyaltyDoesNotTouchEffects() {
        val r = Rig(); val p = r.granted(a, milling)
        r.sign(a, b, listOf(p.id), r.percent(4))
        val e = fx(r).effects(b, milling, us, null, now)
        assertEquals(0L, e.costDeltaPpm)
    }
}
