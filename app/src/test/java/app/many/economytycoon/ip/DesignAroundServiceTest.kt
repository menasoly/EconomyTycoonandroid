package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
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

class DesignAroundServiceTest {
    @Test fun broaderClaimsCostMoreTakeLongerAndSucceedLessOften() {
        val r = Rig()
        val narrow = r.granted(a, milling, breadthPpm = 200_000L)
        val broad = r.granted(a, milling, breadthPpm = 900_000L)
        val qn = r.designs.quote(narrow)
        val qb = r.designs.quote(broad)
        assertTrue(qb.cost > qn.cost)
        assertTrue(qb.days > qn.days)
        assertTrue(qb.successPpm < qn.successPpm)
    }

    @Test fun quoteStaysInBounds() {
        val r = Rig()
        for (b in listOf(0L, 500_000L, 1_000_000L)) {
            val q = r.designs.quote(r.granted(a, milling, breadthPpm = b))
            assertTrue(q.successPpm in 50_000L..950_000L)
            assertTrue(q.days >= r.params.designAround.baseDays)
            assertTrue(q.cost.raw > 0L)
        }
    }

    @Test fun startChargesTheCompanyAndSchedulesCompletion() {
        val r = Rig(); val p = r.granted(a, milling)
        val d = r.designs.start(r.run(10), b, p.id).getOrThrow()
        val q = r.designs.quote(p)
        assertEquals(DesignAroundStatus.RUNNING, d.status)
        assertEquals(Tick(10L + q.days), d.completesAt)
        val ch = r.charges().single()
        assertEquals(ChargeKind.DESIGN_AROUND_COST, ch.kind)
        assertEquals(IpLedger.of(b), ch.payer)
        assertEquals(q.cost, ch.amount)
    }

    @Test fun cannotDesignAroundYourOwnPatent() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.INVALID_ARGUMENT, r.designs.start(r.run(1), a, p.id).rejectionOrNull()?.rule)
    }

    @Test fun unknownOrDeadPatentIsRejected() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.UNKNOWN_ENTITY, r.designs.start(r.run(1), b, PatentId(77)).rejectionOrNull()?.rule)
        p.filingMap.getValue(us).status = FilingStatus.EXPIRED
        assertEquals(Rules.WRONG_STATE, r.designs.start(r.run(1), b, p.id).rejectionOrNull()?.rule)
    }

    @Test fun duplicateRunningProjectIsRejected() {
        val r = Rig(); val p = r.granted(a, milling)
        r.designs.start(r.run(1), b, p.id).getOrThrow()
        assertEquals(Rules.DUPLICATE, r.designs.start(r.run(2), b, p.id).rejectionOrNull()?.rule)
    }

    @Test fun needsCash() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.cashOf[IpOwner.company(b)] = Money.units(1)
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.designs.start(r.run(1), b, p.id).rejectionOrNull()?.rule)
        assertTrue(r.state.designAroundMap.isEmpty())
    }

    @Test fun concurrentProjectsAreLimited() {
        val r = Rig()
        val max = r.params.designAround.maxConcurrent
        repeat(max) { r.designs.start(r.run(1), b, r.granted(a, milling).id).getOrThrow() }
        assertEquals(Rules.LIMIT_EXCEEDED, r.designs.start(r.run(1), b, r.granted(a, milling).id).rejectionOrNull()?.rule)
    }

    @Test fun nothingHappensBeforeCompletionDay() {
        val r = Rig(); val p = r.granted(a, milling)
        val d = r.designs.start(r.run(1), b, p.id).getOrThrow()
        r.designs.step(r.run(d.completesAt.value - 1))
        assertEquals(DesignAroundStatus.RUNNING, d.status)
    }

    @Test fun finishingEndsWithClearanceOrFailure() {
        var worked = 0; var failed = 0
        for (seed in 1L..40L) {
            val r = Rig(seed); val p = r.granted(a, milling, breadthPpm = 300_000L)
            val d = r.designs.start(r.run(1), b, p.id).getOrThrow()
            r.designs.step(r.run(d.completesAt.value))
            if (d.status == DesignAroundStatus.SUCCEEDED) { worked++; assertTrue(r.state.hasClearance(b, p.id)) }
            else { failed++; assertEquals(DesignAroundStatus.FAILED, d.status); assertFalse(r.state.hasClearance(b, p.id)) }
            assertEquals(1, r.events().count { it is IpEvent.DesignAroundFinished })
        }
        assertTrue(worked > 0 && failed > 0)
        assertTrue(worked > failed)
    }

    @Test fun successRateTracksTheQuote() {
        var worked = 0; val n = 200
        var expected = 0L
        for (seed in 1L..n) {
            val r = Rig(seed * 7); val p = r.granted(a, milling, breadthPpm = 600_000L)
            val d = r.designs.start(r.run(1), b, p.id).getOrThrow()
            expected = d.successPpm
            r.designs.step(r.run(d.completesAt.value))
            if (d.status == DesignAroundStatus.SUCCEEDED) worked++
        }
        val share = worked * 1_000_000L / n
        assertTrue("share=$share expected=$expected", Math.abs(share - expected) < 150_000L)
    }

    @Test fun successClosesOpenDisputeAndLiftsInjunction() {
        for (seed in 1L..40L) {
            val r = Rig(seed); val p = r.granted(a, milling, breadthPpm = 200_000L)
            r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
            val disp = r.disputes.report(r.run(1), IpOwner.company(a), p.id, b, us).getOrThrow()
            val d = r.designs.start(r.run(2), b, p.id, disp.id).getOrThrow()
            r.designs.step(r.run(d.completesAt.value))
            if (d.status == DesignAroundStatus.SUCCEEDED) {
                assertEquals(DisputeResult.DESIGNED_AROUND, disp.result)
                return
            }
        }
        throw AssertionError("no seed produced a success")
    }

    @Test fun cancelStopsTheProjectOnlyForItsOwner() {
        val r = Rig(); val p = r.granted(a, milling)
        val d = r.designs.start(r.run(1), b, p.id).getOrThrow()
        assertEquals(Rules.NOT_OWNER, r.designs.cancel(r.run(2), d.id, a).rejectionOrNull()?.rule)
        r.designs.cancel(r.run(2), d.id, b).getOrThrow()
        assertEquals(DesignAroundStatus.CANCELLED, d.status)
        r.designs.step(r.run(d.completesAt.value + 5))
        assertEquals(DesignAroundStatus.CANCELLED, d.status)
        assertEquals(Rules.WRONG_STATE, r.designs.cancel(r.run(3), d.id, b).rejectionOrNull()?.rule)
    }

    @Test fun cancelledProjectFreesASlot() {
        val r = Rig()
        val ds = (1..r.params.designAround.maxConcurrent).map { r.designs.start(r.run(1), b, r.granted(a, milling).id).getOrThrow() }
        r.designs.cancel(r.run(2), ds.first().id, b).getOrThrow()
        r.designs.start(r.run(2), b, r.granted(a, milling).id).getOrThrow()
        assertEquals(r.params.designAround.maxConcurrent, r.designs.running(b).size)
    }

    @Test fun inactiveCompanyAbandonsTheProject() {
        val r = Rig(); val p = r.granted(a, milling)
        val d = r.designs.start(r.run(1), b, p.id).getOrThrow()
        r.ctx.inactive += b
        r.designs.step(r.run(d.completesAt.value))
        assertEquals(DesignAroundStatus.CANCELLED, d.status)
        assertTrue(r.events().none { it is IpEvent.DesignAroundFinished })
    }

    @Test fun alreadyClearedCompanyCannotStartAgain() {
        val r = Rig(); val p = r.granted(a, milling)
        r.state.clearanceSet.add(Clearance(b, p.id, Tick(0)))
        assertEquals(Rules.DUPLICATE, r.designs.start(r.run(1), b, p.id).rejectionOrNull()?.rule)
    }

    @Test fun pruneDropsClearancesOfDeadPatents() {
        val r = Rig(); val p = r.granted(a, milling); val q = r.granted(a, milling)
        r.state.clearanceSet.add(Clearance(b, p.id, Tick(0)))
        r.state.clearanceSet.add(Clearance(b, q.id, Tick(0)))
        p.filingMap.getValue(us).status = FilingStatus.EXPIRED
        r.designs.prune(Tick(10))
        assertEquals(listOf(q.id), r.designs.clearancesOf(b).map { it.patent })
    }

    @Test fun sameSeedSameResult() {
        fun go(): String {
            val r = Rig(99); val p = r.granted(a, milling)
            val d = r.designs.start(r.run(1), b, p.id).getOrThrow()
            r.designs.step(r.run(d.completesAt.value))
            return d.status.name + r.state.stateHash()
        }
        assertEquals(go(), go())
    }
}
