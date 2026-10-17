package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompulsoryServiceTest {
    private fun refused(r: Rig, p: Patent, at: Long = 1L) {
        val l = r.offer(a, b, listOf(p.id)).getOrThrow()
        r.book.decline(r.run(at), l.id, b).getOrThrow()
    }

    @Test fun publicHealthNeedsAnEmergency() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.WRONG_STATE, r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).rejectionOrNull()?.rule)
        r.ctx.health += us
        val req = r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        assertEquals(CompulsoryStatus.PENDING, req.status)
        assertEquals(Tick(5L + r.state.regimes.regimeOf(us).compulsory.reviewDays), req.decideAt)
    }

    @Test fun applicationFeeGoesToTheGovernment() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        val ch = r.charges().single()
        assertEquals(IpLedger.of(b), ch.payer)
        assertEquals(IpLedger.government(us), ch.payee)
    }

    @Test fun applicantNeedsCashForTheFee() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        r.ctx.cashOf[IpOwner.company(b)] = Money.ZERO
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).rejectionOrNull()?.rule)
    }

    @Test fun basicRejections() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        assertEquals(Rules.UNKNOWN_ENTITY, r.compulsory.request(r.run(5), b, PatentId(9), us, CompulsoryGround.PUBLIC_HEALTH).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, r.compulsory.request(r.run(5), b, p.id, eu, CompulsoryGround.PUBLIC_HEALTH).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, r.compulsory.request(r.run(5), a, p.id, us, CompulsoryGround.PUBLIC_HEALTH).rejectionOrNull()?.rule)
        r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        assertEquals(Rules.DUPLICATE, r.compulsory.request(r.run(6), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).rejectionOrNull()?.rule)
    }

    @Test fun licensedCompanyDoesNotNeedOne() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        r.sign(a, b, listOf(p.id))
        assertEquals(Rules.DUPLICATE, r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).rejectionOrNull()?.rule)
    }

    @Test fun nonWorkingNeedsYearsOfIdlenessAndAFailedNegotiation() {
        val r = Rig(); val p = r.granted(a, milling)
        refused(r, p, 1L)
        assertEquals(Rules.WRONG_STATE, r.compulsory.request(r.run(400), b, p.id, us, CompulsoryGround.NON_WORKING).rejectionOrNull()?.rule)
        val years = r.state.regimes.regimeOf(us).compulsory.nonWorkingYears
        val late = years * 360L + 10L
        r.compulsory.request(r.run(late), b, p.id, us, CompulsoryGround.NON_WORKING).getOrThrow()
    }

    @Test fun nonWorkingFailsWhenThePatentIsWorkedLocally() {
        val r = Rig(); val p = r.granted(a, milling)
        refused(r, p, 1L)
        r.ctx.worked += Triple(a, milling, us)
        assertEquals(Rules.WRONG_STATE, r.compulsory.request(r.run(2_000), b, p.id, us, CompulsoryGround.NON_WORKING).rejectionOrNull()?.rule)
    }

    @Test fun voluntaryNegotiationMustHaveBeenTried() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.WRONG_STATE, r.compulsory.request(r.run(2_000), b, p.id, us, CompulsoryGround.NON_WORKING).rejectionOrNull()?.rule)
    }

    @Test fun recentRefusalIsNotEnough() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.shares[a to p.field] = Rate.percent(55)
        refused(r, p, 1L)
        val days = r.state.regimes.regimeOf(us).compulsory.negotiationDays
        assertEquals(Rules.WRONG_STATE, r.compulsory.request(r.run(days - 1L), b, p.id, us, CompulsoryGround.ANTI_COMPETITIVE).rejectionOrNull()?.rule)
        r.compulsory.request(r.run(days + 1L), b, p.id, us, CompulsoryGround.ANTI_COMPETITIVE).getOrThrow()
    }

    @Test fun antiCompetitiveNeedsADominantHolder() {
        val r = Rig(); val p = r.granted(a, milling)
        refused(r, p, 1L)
        assertEquals(Rules.WRONG_STATE, r.compulsory.request(r.run(500), b, p.id, us, CompulsoryGround.ANTI_COMPETITIVE).rejectionOrNull()?.rule)
        r.ctx.shares[a to p.field] = Rate.percent(55)
        r.compulsory.request(r.run(500), b, p.id, us, CompulsoryGround.ANTI_COMPETITIVE).getOrThrow()
    }

    @Test fun governmentUseTargetsForeignHolders() {
        val r = Rig(); val p = r.granted(a, milling)
        refused(r, p, 1L)
        r.ctx.homes[a] = us
        assertEquals(Rules.WRONG_STATE, r.compulsory.request(r.run(500), b, p.id, us, CompulsoryGround.GOVERNMENT_USE).rejectionOrNull()?.rule)
        r.ctx.homes[a] = eu
        r.compulsory.request(r.run(500), b, p.id, us, CompulsoryGround.GOVERNMENT_USE).getOrThrow()
    }

    @Test fun pendingRequestIsNotDecidedEarly() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        val req = r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        r.compulsory.step(r.run(req.decideAt.value - 1))
        assertEquals(CompulsoryStatus.PENDING, req.status)
    }

    @Test fun grantedRequestCreatesACompulsoryLicenceWithCappedRoyalty() {
        for (seed in 1L..30L) {
            val r = Rig(seed); val p = r.granted(a, milling)
            r.ctx.health += us
            val req = r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
            r.compulsory.step(r.run(req.decideAt.value))
            if (req.status != CompulsoryStatus.GRANTED) continue
            val l = r.state.licenseMap.getValue(req.licenseId!!)
            val rules = r.state.regimes.regimeOf(us).compulsory
            assertTrue(l.compulsory)
            assertEquals(b, l.licensee)
            assertEquals(listOf(us), l.scope.countries)
            assertTrue(l.terms.percent.ppm <= rules.maxRoyaltyPpm)
            assertEquals(rules.termYears * 360, l.termDays)
            assertTrue(r.book.isLicensed(b, p.id, us, null, Tick(req.decideAt.value + 1)))
            assertTrue(r.events().any { it is IpEvent.CompulsoryDecided && it.granted })
            return
        }
        throw AssertionError("no seed granted")
    }

    @Test fun healthRequestsAreGrantedMostOfTheTime() {
        var granted = 0
        for (seed in 1L..60L) {
            val r = Rig(seed); val p = r.granted(a, milling)
            r.ctx.health += us
            val req = r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
            r.compulsory.step(r.run(req.decideAt.value))
            if (req.status == CompulsoryStatus.GRANTED) granted++
        }
        assertTrue("granted=$granted", granted in 48..60)
    }

    @Test fun deniedWhenThePatentIsGoneByDecisionTime() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        val req = r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        p.filingMap.getValue(us).status = FilingStatus.EXPIRED
        r.compulsory.step(r.run(req.decideAt.value))
        assertEquals(CompulsoryStatus.DENIED, req.status)
        assertTrue(req.reason.isNotBlank())
        assertTrue(r.state.licenseMap.isEmpty())
    }

    @Test fun deniedWhenTheEmergencyEnded() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        val req = r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        r.ctx.health -= us
        r.compulsory.step(r.run(req.decideAt.value))
        assertEquals(CompulsoryStatus.DENIED, req.status)
    }

    @Test fun withdrawnRequestIsNeverDecided() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        val req = r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        assertEquals(Rules.NOT_OWNER, r.compulsory.withdraw(req.id, a).rejectionOrNull()?.rule)
        r.compulsory.withdraw(req.id, b).getOrThrow()
        r.compulsory.step(r.run(req.decideAt.value))
        assertEquals(CompulsoryStatus.WITHDRAWN, req.status)
        assertEquals(Rules.WRONG_STATE, r.compulsory.withdraw(req.id, b).rejectionOrNull()?.rule)
    }

    @Test fun royaltyScalesWithRegimeAndRespectsTheCeiling() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 1_000_000L, noveltyPpm = 1_000_000L)
        r.ctx.health += us
        val run = r.run(1)
        val balanced = r.compulsory.royaltyFor(run, p, b, us)
        r.state.regimes.assign(us, RegimeKind.DEVELOPING)
        val developing = r.compulsory.royaltyFor(run, p, b, us)
        assertTrue(developing.ppm <= RegimeKind.DEVELOPING.regime.compulsory.maxRoyaltyPpm)
        assertTrue(balanced.ppm <= RegimeKind.BALANCED.regime.compulsory.maxRoyaltyPpm)
        assertTrue(developing.ppm <= balanced.ppm)
    }

    @Test fun requestsOfListsOnlyTheApplicants() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        r.compulsory.request(r.run(5), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        assertEquals(1, r.compulsory.requestsOf(b).size)
        assertTrue(r.compulsory.requestsOf(a).isEmpty())
        assertNotNull(r.state.compulsoryRequests().firstOrNull())
    }
}
