package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NpeServiceTest {
    private fun npe(r: Rig, strategy: NpeStrategy): Npe = r.state.npeMap.values.first { it.strategy == strategy }

    /** A patent held by [n] and a company b selling products that use it. */
    private fun scene(r: Rig, n: Npe): Patent {
        val p = r.granted(a, milling)
        p.owner = n.owner
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(2_000, 90)))
        return p
    }

    @Test fun seedCreatesTheRosterOnce() {
        val r = Rig()
        r.npes.seed()
        assertEquals(3, r.state.npeMap.size)
        assertEquals(NpeStrategy.values().toSet(), r.state.npeMap.values.map { it.strategy }.toSet())
        assertTrue(r.state.npeMap.values.all { it.chest == Money.units(r.params.npe.startingChestUnits) })
        val hash = r.state.stateHash()
        r.npes.seed()
        assertEquals(hash, r.state.stateHash())
    }

    @Test fun nothingHappensOffTheMonthBoundary() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.DEMAND_LETTERS)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        r.npes.step(r.run(31))
        assertEquals(DisputeStage.DETECTED, d.stage)
    }

    @Test fun demandLetterGoesToTheInfringer() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.DEMAND_LETTERS)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        r.npes.step(r.run(30))
        assertEquals(DisputeStage.NOTICE, d.stage)
        assertEquals(1, n.demandsSent)
        assertTrue(d.demand.raw > 0L)
        val ev = r.events().filterIsInstance<IpEvent.DemandReceived>().single()
        assertEquals(b, ev.target)
        assertEquals(d.demand, ev.demand)
    }

    @Test fun demandsPerMonthAreCapped() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.DEMAND_LETTERS)
        repeat(5) { scene(r, n).also { p -> r.disputes.report(r.run(5), n.owner, p.id, b, us) } }
        r.npes.step(r.run(30))
        assertEquals(r.params.npe.maxDemandsPerMonth, n.demandsSent)
    }

    @Test fun demandIsPricedBelowTheCostOfADefence() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.DEMAND_LETTERS)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        val e = r.disputes.estimate(r.run(5), d)
        val demand = r.npes.demandFor(d, r.run(5))
        assertTrue(demand <= Money.max(e.defendantCostToGo, Money.units(r.params.litigation.minDamagesUnits)))
        assertTrue(demand.raw >= Money.units(r.params.litigation.minDamagesUnits).raw)
    }

    @Test fun litigatorFilesWithoutWarning() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.LITIGATOR)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        r.npes.step(r.run(30))
        assertEquals(DisputeStage.FILED, d.stage)
        assertTrue(n.spent.raw > 0L)
    }

    @Test fun unansweredDemandEscalatesToCourt() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.DEMAND_LETTERS)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        d.claimantEvidencePpm = 1_000_000L; d.defendantEvidencePpm = 0L
        r.npes.step(r.run(30))
        assertEquals(DisputeStage.NOTICE, d.stage)
        r.npes.step(r.run(60))
        assertEquals(DisputeStage.NOTICE, d.stage)
        r.npes.step(r.run(120))
        assertEquals(DisputeStage.FILED, d.stage)
    }

    @Test fun weakCasesAreNotEscalated() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.DEMAND_LETTERS)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        d.claimantEvidencePpm = 0L; d.defendantEvidencePpm = 1_000_000L
        r.npes.step(r.run(30)); r.npes.step(r.run(120))
        assertEquals(DisputeStage.NOTICE, d.stage)
    }

    @Test fun reasonableOfferIsAcceptedAndLowballIsNot() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.DEMAND_LETTERS)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        d.infringingSales = Money.units(20_000_000)
        r.disputes.offerSettlement(r.run(6), d.id, IpOwner.company(b), Money(1)).getOrThrow()
        r.npes.step(r.run(30))
        assertTrue(d.isOpen)
        val e = r.disputes.estimate(r.run(30), d)
        r.disputes.offerSettlement(r.run(31), d.id, IpOwner.company(b), e.awardIfWon).getOrThrow()
        r.npes.step(r.run(60))
        assertEquals(DisputeResult.SETTLED, d.result)
        assertEquals(e.awardIfWon, n.earned)
    }

    @Test fun buyerTakesCheapListingsOnly() {
        val r = Rig(); r.npes.seed()
        val p = r.granted(a, milling)
        val value = r.valuer.value(p, r.run(1)).value
        r.market.list(r.run(1), p.id, IpOwner.company(a), Money(value.raw * 2)).getOrThrow()
        r.npes.step(r.run(30))
        assertEquals(IpOwner.company(a), p.owner)
        r.market.list(r.run(31), p.id, IpOwner.company(a), Money(value.raw / 10)).getOrThrow()
        r.npes.step(r.run(60))
        assertTrue(p.owner.isNpe)
    }

    @Test fun holderStopsBuyingAtItsPatentCap() {
        val r = Rig(1L, IpParams(npe = NpeParams(maxPatents = 1))); r.npes.seed()
        val p1 = r.granted(a, milling); val p2 = r.granted(a, milling)
        for (p in listOf(p1, p2)) r.market.list(r.run(1), p.id, IpOwner.company(a), Money.units(100)).getOrThrow()
        r.npes.step(r.run(30))
        val held = r.state.npeMap.values.map { r.state.patentsOf(it.owner).size }
        assertTrue(held.all { it <= 1 })
    }

    @Test fun chestIsNeverOverdrawn() {
        val r = Rig(); r.npes.seed()
        val n = npe(r, NpeStrategy.LITIGATOR)
        n.chest = Money.units(10)
        val p = scene(r, n)
        val d = r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
        r.npes.step(r.run(30))
        assertEquals(DisputeStage.DETECTED, d.stage)
        assertTrue(n.chest.raw >= 0L)
    }

    @Test fun lastActiveMonthIsRecorded() {
        val r = Rig(); r.npes.seed()
        r.npes.step(r.run(90))
        assertTrue(r.state.npeMap.values.all { it.lastActiveMonth == 3L })
    }

    @Test fun sameSeedSameBehaviour() {
        fun go(): String {
            val r = Rig(5); r.npes.seed()
            val n = npe(r, NpeStrategy.LITIGATOR)
            val p = scene(r, n)
            r.disputes.report(r.run(5), n.owner, p.id, b, us).getOrThrow()
            for (t in 30L..400L step 30L) { r.npes.step(r.run(t)); r.disputes.step(r.run(t)) }
            return r.state.stateHash().toString()
        }
        assertEquals(go(), go())
        assertFalse(go().isEmpty())
    }
}
