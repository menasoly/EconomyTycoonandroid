package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatentOfficeTest {
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    private fun granted(rig: IpTestSupport.Rig, tech: app.many.economytycoon.core.TechId = milling, company: app.many.economytycoon.core.CompanyId = a): Patent {
        // Try seeds-by-company until the examination grants; keeps tests independent of one lucky roll.
        val p = rig.file(company, tech, us).getOrThrow()
        runUntil(rig, p.filing(us)!!.examinationDue.value)
        return p
    }

    private fun runUntil(rig: IpTestSupport.Rig, day: Long) {
        var t = rig.tick
        while (t <= day) { rig.office.step(rig.run(t)); t++ }
        rig.tick = day
    }

    @Test fun filingCreatesAPendingFilingPerCountryAndChargesFees() {
        val rig = IpTestSupport.Rig()
        val p = rig.file(a, milling, us, eu).getOrThrow()
        assertEquals(2, p.filings.size)
        assertTrue(p.filings.all { it.status == FilingStatus.PENDING })
        assertEquals(2, rig.charges().size)
        assertTrue(rig.charges().all { it.kind == ChargeKind.FILING_FEE })
        assertEquals(p.totalFees, Money.sum(rig.charges().map { it.amount }))
        assertEquals(IpOwner.company(a), p.owner)
        assertTrue(rig.events().single() is IpEvent.PatentFiled)
    }

    @Test fun feesGoToTheGovernmentOfEachCountry() {
        val rig = IpTestSupport.Rig()
        rig.file(a, milling, us, eu).getOrThrow()
        val payees = rig.charges().map { it.payee }.toSet()
        assertEquals(setOf(IpLedger.government(us), IpLedger.government(eu)), payees)
    }

    @Test fun anUnresearchedTechnologyCannotBeFiled() {
        val rig = IpTestSupport.Rig()
        assertEquals(Rules.NOT_UNLOCKED, rule(rig.office.file(rig.run(0), FilingRequest(a, milling, listOf(us)), 0)))
    }

    @Test fun anUnknownTechnologyIsRefused() {
        val rig = IpTestSupport.Rig()
        assertEquals(Rules.UNKNOWN_ENTITY, rule(rig.office.file(rig.run(0), FilingRequest(a, app.many.economytycoon.core.TechId("nope"), listOf(us)), 0)))
    }

    @Test fun requestsAreValidated() {
        val rig = IpTestSupport.Rig()
        rig.teach(a, milling)
        assertEquals(Rules.INVALID_ARGUMENT, rule(rig.office.file(rig.run(0), FilingRequest(a, milling, emptyList()), 0)))
        assertEquals(Rules.DUPLICATE, rule(rig.office.file(rig.run(0), FilingRequest(a, milling, listOf(us, us)), 0)))
        val seven = (1L..7L).map { app.many.economytycoon.core.CountryId(it) }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(rig.office.file(rig.run(0), FilingRequest(a, milling, seven), 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(rig.office.file(rig.run(0), FilingRequest(a, milling, listOf(us), claims = 0), 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(rig.office.file(rig.run(0), FilingRequest(a, milling, listOf(us), claims = 41), 0)))
        assertTrue(rig.state.patents().isEmpty())
        assertTrue(rig.charges().isEmpty())
    }

    @Test fun theSameCompanyCannotPatentATechnologyTwice() {
        val rig = IpTestSupport.Rig()
        rig.file(a, milling, us).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(rig.file(a, milling, eu)))
        assertNotNull(rig.file(b, milling, us).getOrNull())
    }

    @Test fun anOwnerWithoutCashCannotFile() {
        val rig = IpTestSupport.Rig()
        rig.ctx.cashOf[IpOwner.company(a)] = Money.units(100)
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(rig.file(a, milling, us)))
        assertTrue(rig.state.patents().isEmpty())
        assertTrue(rig.charges().isEmpty())
    }

    @Test fun cashPromisedInOneFilingIsNotSpentTwice() {
        val rig = IpTestSupport.Rig()
        rig.ctx.cashOf[IpOwner.company(a)] = Money.units(10_000)
        val run = rig.run(0)
        rig.teach(a, milling, ovens)
        assertTrue(rig.office.file(run, FilingRequest(a, milling, listOf(us)), 0).isOk)
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(rig.office.file(run, FilingRequest(a, ovens, listOf(us)), 0)))
    }

    @Test fun pendingApplicationsAreLimited() {
        val params = IpParams(office = OfficeParams(maxPendingPerCompany = 1))
        val rig = IpTestSupport.Rig(params = params)
        rig.file(a, milling, us).getOrThrow()
        assertEquals(Rules.LIMIT_EXCEEDED, rule(rig.file(a, ovens, us)))
    }

    @Test fun quotingChangesNothing() {
        val rig = IpTestSupport.Rig()
        rig.teach(a, milling)
        val before = rig.state.stateHash()
        val q = rig.office.quote(FilingRequest(a, milling, listOf(us, eu)), Tick(0), 0).getOrThrow()
        assertEquals(before, rig.state.stateHash())
        assertEquals(2, q.perCountry.size)
        assertEquals(Money.sum(q.perCountry.map { it.fee }), q.fees)
        assertTrue(q.perCountry.all { it.grantChancePpm in 50_000L..950_000L })
    }

    @Test fun aQuoteMatchesTheFilingThatFollows() {
        val rig = IpTestSupport.Rig()
        rig.teach(a, ovens)
        val req = FilingRequest(a, ovens, listOf(us), BreadthChoice.BROAD, 14)
        val q = rig.office.quote(req, Tick(0), 1).getOrThrow()
        val p = rig.office.file(rig.run(0), req, 1).getOrThrow()
        assertEquals(q.noveltyPpm, p.noveltyPpm)
        assertEquals(q.fees, p.totalFees)
    }

    @Test fun nothingHappensBeforeTheExaminationDate() {
        val rig = IpTestSupport.Rig()
        val p = rig.file(a, milling, us).getOrThrow()
        val due = p.filing(us)!!.examinationDue.value
        rig.out = IpOutput(rig.state.ids)
        for (t in 0 until due) rig.office.step(rig.run(t))
        assertEquals(FilingStatus.PENDING, p.filing(us)!!.status)
        assertTrue(rig.events().isEmpty())
    }

    @Test fun examinationEndsInAGrantOrARefusal() {
        val rig = IpTestSupport.Rig()
        val p = granted(rig)
        val status = p.filing(us)!!.status
        assertTrue(status == FilingStatus.GRANTED || status == FilingStatus.REJECTED)
        assertNotNull(p.filing(us)!!.let { if (it.status == FilingStatus.GRANTED) it.grantedAt else it.endedAt })
    }

    @Test fun theSameSeedGivesTheSameOutcome() {
        fun outcome(seed: Long): List<FilingStatus> {
            val rig = IpTestSupport.Rig(seed)
            rig.teach(a, milling, IpTestSupport.ovens, IpTestSupport.efficiency)
            val ps = listOf(milling, IpTestSupport.ovens, IpTestSupport.efficiency).map { rig.office.file(rig.run(0), FilingRequest(a, it, listOf(us, eu)), 0).getOrThrow() }
            runUntil(rig, 1_000)
            return ps.flatMap { p -> p.filings.map { it.status } }
        }
        assertEquals(outcome(5), outcome(5))
    }

    @Test fun differentSeedsEventuallyDisagree() {
        fun statuses(seed: Long): List<FilingStatus> {
            val rig = IpTestSupport.Rig(seed)
            rig.teach(a, milling, IpTestSupport.ovens, IpTestSupport.efficiency, IpTestSupport.megamill)
            val ps = listOf(milling, IpTestSupport.ovens, IpTestSupport.efficiency, IpTestSupport.megamill).map {
                rig.office.file(rig.run(0), FilingRequest(a, it, listOf(us, eu), BreadthChoice.BROAD), 0).getOrThrow()
            }
            runUntil(rig, 1_000)
            return ps.flatMap { p -> p.filings.map { it.status } }
        }
        assertTrue((1L..12L).map { statuses(it) }.toSet().size > 1)
    }

    @Test fun grantFractionTracksTheGrantProbability() {
        val rig = IpTestSupport.Rig(3)
        var grantedCount = 0
        var expectedPpm = 0L
        var variance = 0.0
        val n = 200
        for (i in 1L..n) {
            val co = app.many.economytycoon.core.CompanyId(100 + i)
            rig.teach(co, milling)
            val p = rig.office.file(rig.run(0), FilingRequest(co, milling, listOf(us), BreadthChoice.NARROW), 0).getOrThrow()
            val chance = PatentRules.grantProbability(OfficeParams(), RegimeKind.BALANCED.regime, p.noveltyPpm, p.claimedBreadthPpm)
            expectedPpm += chance
            variance += (chance / 1e6) * (1 - chance / 1e6)
            if (rig.state.dice.rate("exam", p.id.value, us.value).ppm < chance) grantedCount++
        }
        val expected = expectedPpm / 1e6
        assertTrue("granted $grantedCount expected $expected", Math.abs(grantedCount - expected) <= 4 * Math.sqrt(variance))
    }

    @Test fun aGrantSetsExpiryAndTheFirstRenewalDate() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val f = p.filing(us)!!
        assertEquals(Tick(f.filedAt.value + 20 * 360), f.expiresAt)
        assertEquals(Tick(f.grantedAt!!.value + 360), f.nextFeeDue)
        assertTrue(f.feesPaid > Money.ZERO)
    }

    @Test fun grantedBreadthIsAtMostWhatWasClaimed() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        assertTrue(p.grantedBreadthPpm() <= p.claimedBreadthPpm)
        assertTrue(p.grantedBreadthPpm() > 0L)
    }

    /** Files across companies until one family is granted; the rig stays deterministic. */
    private fun grantedPatent(rig: IpTestSupport.Rig, company: app.many.economytycoon.core.CompanyId = a, tech: app.many.economytycoon.core.TechId = milling): Patent {
        var co = company.value
        while (true) {
            val c = app.many.economytycoon.core.CompanyId(co)
            rig.teach(c, tech)
            val p = rig.office.file(rig.run(rig.tick), FilingRequest(c, tech, listOf(us), BreadthChoice.NARROW), 3).getOrThrow()
            runUntil(rig, p.filing(us)!!.examinationDue.value)
            if (p.filing(us)!!.status == FilingStatus.GRANTED) return p
            co += 1000
        }
    }

    @Test fun autoRenewalPaysAnEscalatingFeeEachYear() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val f = p.filing(us)!!
        rig.out = IpOutput(rig.state.ids)
        runUntil(rig, f.grantedAt!!.value + 3 * 360 + 5)
        val fees = rig.events().filterIsInstance<IpEvent.MaintenancePaid>().map { it.fee }
        assertEquals(3, fees.size)
        assertTrue(fees[0] < fees[1] && fees[1] < fees[2])
        assertEquals(3, f.feeYearsPaid)
    }

    @Test fun anOwnerWhoCannotPayLosesThePatentAfterTheGraceDays() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val f = p.filing(us)!!
        rig.ctx.cashOf[p.owner] = Money.ZERO
        rig.out = IpOutput(rig.state.ids)
        val due = f.nextFeeDue!!.value
        runUntil(rig, due + 59)
        assertEquals(FilingStatus.GRANTED, f.status)
        runUntil(rig, due + 60)
        assertEquals(FilingStatus.LAPSED, f.status)
        assertTrue(rig.events().any { it is IpEvent.PatentLapsed })
        assertNull(f.nextFeeDue)
    }

    @Test fun payingInTheGracePeriodSavesThePatent() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val f = p.filing(us)!!
        val owner = p.owner
        rig.ctx.cashOf[owner] = Money.ZERO
        val due = f.nextFeeDue!!.value
        runUntil(rig, due + 10)
        assertNotNull(f.graceUntil)
        rig.ctx.cashOf[owner] = Money.units(1_000_000)
        runUntil(rig, due + 11)
        assertEquals(FilingStatus.GRANTED, f.status)
        assertNull(f.graceUntil)
        assertEquals(Tick(due + 360), f.nextFeeDue)
    }

    @Test fun manualPatentsOnlyRenewWhenThePlayerPays() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val f = p.filing(us)!!
        assertTrue(rig.office.setAutoMaintain(p.id, p.inventor, false).isOk)
        val due = f.nextFeeDue!!.value
        runUntil(rig, due + 30)
        assertEquals(0, f.feeYearsPaid)
        val paid = rig.office.payMaintenance(rig.run(due + 31), p.id, us, p.inventor).getOrThrow()
        assertEquals(PatentRules.maintenanceFee(OfficeParams(), RegimeKind.BALANCED.regime, 1), paid)
        assertEquals(1, f.feeYearsPaid)
    }

    @Test fun manualRenewalOpensOnlyShortlyBeforeTheDueDate() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val f = p.filing(us)!!
        rig.office.setAutoMaintain(p.id, p.inventor, false)
        assertEquals(Rules.WRONG_STATE, rule(rig.office.payMaintenance(rig.run(f.grantedAt!!.value + 10), p.id, us, p.inventor)))
    }

    @Test fun onlyTheOwnerMayPayOrAbandon() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        assertEquals(Rules.NOT_OWNER, rule(rig.office.payMaintenance(rig.run(), p.id, us, b)))
        assertEquals(Rules.NOT_OWNER, rule(rig.office.abandon(rig.run(), p.id, null, b)))
        assertEquals(Rules.NOT_OWNER, rule(rig.office.setAutoMaintain(p.id, b, false)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(rig.office.abandon(rig.run(), PatentId(999), null, a)))
    }

    @Test fun patentsExpireAtTheEndOfTheTerm() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val f = p.filing(us)!!
        val exp = f.expiresAt!!.value
        rig.out = IpOutput(rig.state.ids)
        runUntil(rig, exp - 1)
        assertTrue(f.inForceAt(Tick(exp - 1)))
        runUntil(rig, exp)
        assertEquals(FilingStatus.EXPIRED, f.status)
        assertFalse(f.inForceAt(Tick(exp)))
        assertTrue(rig.events().any { it is IpEvent.PatentExpired })
        assertEquals(PatentStanding.ENDED, p.standing())
    }

    @Test fun abandoningEndsEveryLiveFiling() {
        val rig = IpTestSupport.Rig()
        val p = rig.file(a, milling, us, eu).getOrThrow()
        assertTrue(rig.office.abandon(rig.run(), p.id, null, a).isOk)
        assertTrue(p.filings.all { it.status == FilingStatus.ABANDONED })
        assertEquals(PatentStanding.ENDED, p.standing())
        assertEquals(Rules.WRONG_STATE, rule(rig.office.abandon(rig.run(), p.id, null, a)))
    }

    @Test fun abandoningOneCountryLeavesTheOther() {
        val rig = IpTestSupport.Rig()
        val p = rig.file(a, milling, us, eu).getOrThrow()
        rig.office.abandon(rig.run(), p.id, eu, a).getOrThrow()
        assertEquals(FilingStatus.ABANDONED, p.filing(eu)!!.status)
        assertEquals(FilingStatus.PENDING, p.filing(us)!!.status)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(rig.office.abandon(rig.run(), p.id, app.many.economytycoon.core.CountryId(9), a)))
    }

    @Test fun anApplicationIsNotPriorArtUntilItIsPublished() {
        val rig = IpTestSupport.Rig()
        rig.file(a, milling, us).getOrThrow()
        assertEquals(0L, rig.office.priorArtAffinity(TechField.MANUFACTURING, Tick(100), null))
        assertEquals(1_000_000L, rig.office.priorArtAffinity(TechField.MANUFACTURING, Tick(540), null))
        assertEquals(600_000L, rig.office.priorArtAffinity(TechField.ADVANCED_MATERIALS, Tick(540), null))
    }

    @Test fun laterPatentsCiteEarlierPublishedOnesInCloseFields() {
        val rig = IpTestSupport.Rig()
        val first = rig.file(a, milling, us).getOrThrow()
        rig.teach(b, ovens)
        val second = rig.office.file(rig.run(600), FilingRequest(b, ovens, listOf(us)), 0).getOrThrow()
        assertEquals(listOf(first.id), second.cites)
        assertEquals(1, rig.state.forwardCitations(first.id))
        assertEquals(0, rig.state.forwardCitations(second.id))
    }

    @Test fun anUnpublishedPatentIsNotCited() {
        val rig = IpTestSupport.Rig()
        rig.file(a, milling, us).getOrThrow()
        rig.teach(b, ovens)
        val second = rig.office.file(rig.run(100), FilingRequest(b, ovens, listOf(us)), 0).getOrThrow()
        assertTrue(second.cites.isEmpty())
    }

    @Test fun aPublishedPatentOnTheSameTechnologyHurtsRivalNovelty() {
        val rig = IpTestSupport.Rig()
        rig.file(a, milling, us).getOrThrow()
        rig.teach(b, milling)
        val early = rig.office.quote(FilingRequest(b, milling, listOf(us)), Tick(100), 1).getOrThrow().noveltyPpm
        val late = rig.office.quote(FilingRequest(b, milling, listOf(us)), Tick(700), 1).getOrThrow().noveltyPpm
        assertTrue(late < early - 200_000)
    }

    @Test fun backlogSlowsLaterApplicationsInTheSameOffice() {
        val rig = IpTestSupport.Rig()
        val first = rig.file(a, milling, us).getOrThrow()
        rig.teach(b, milling)
        val second = rig.office.file(rig.run(0), FilingRequest(b, milling, listOf(us)), 0).getOrThrow()
        val d1 = first.filing(us)!!.examinationDue.value
        val d2 = second.filing(us)!!.examinationDue.value
        assertTrue("first $d1 second $d2", d2 - d1 in -80L..80L && rig.office.pendingIn(us) == 2)
    }

    @Test fun regimesChangeFeesAndDelays() {
        val rig = IpTestSupport.Rig()
        rig.state.regimes.assign(eu, RegimeKind.DEVELOPING)
        rig.state.regimes.assign(us, RegimeKind.PRO_PATENT)
        val q = rig.run(0).let { rig.teach(a, milling); rig.office.quote(FilingRequest(a, milling, listOf(us, eu)), Tick(0), 0).getOrThrow() }
        val usQ = q.perCountry.first { it.country == us }
        val euQ = q.perCountry.first { it.country == eu }
        assertTrue(usQ.fee > euQ.fee)
        assertTrue(euQ.examinationDays > usQ.examinationDays)
    }

    @Test fun pharmaceuticalFilingsLiveLonger() {
        val rig = IpTestSupport.Rig()
        rig.teach(a, milling)
        rig.teach(b, milling)
        val plain = rig.office.file(rig.run(0), FilingRequest(a, milling, listOf(us)), 0).getOrThrow()
        val pharma = rig.office.file(rig.run(0), FilingRequest(b, milling, listOf(us), industry = app.many.economytycoon.content.IndustryFamily.PHARMACEUTICALS), 0).getOrThrow()
        assertFalse(plain.pharma)
        assertTrue(pharma.pharma)
        assertTrue(rig.office.remainingDays(pharma, Tick(0)) > rig.office.remainingDays(plain, Tick(0)))
    }

    @Test fun revokingEndsOneCountry() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        rig.out = IpOutput(rig.state.ids)
        rig.office.revoke(p.id, us, null, rig.run())
        assertEquals(FilingStatus.REVOKED, p.filing(us)!!.status)
        assertTrue(rig.events().single() is IpEvent.PatentRevoked)
        rig.office.revoke(p.id, us, null, rig.run())
        assertEquals(1, rig.events().size)
    }

    @Test fun remainingDaysShrinksAndEndsAtZero() {
        val rig = IpTestSupport.Rig()
        val p = grantedPatent(rig)
        val now = rig.tick
        assertTrue(rig.office.remainingDays(p, Tick(now)) > rig.office.remainingDays(p, Tick(now + 100)))
        assertEquals(0L, rig.office.remainingDays(p, Tick(now + 100_000)))
        rig.office.abandon(rig.run(), p.id, null, p.inventor)
        assertEquals(0L, rig.office.remainingDays(p, Tick(now)))
    }
}
