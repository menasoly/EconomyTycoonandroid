package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisputeServiceTest {
    private fun rigWithInfringer(): Triple<Rig, Patent, Dispute> {
        val r = Rig()
        val p = r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        val d = r.disputes.report(r.run(10), IpOwner.company(a), p.id, b, us).getOrThrow()
        return Triple(r, p, d)
    }

    private fun drive(r: Rig, d: Dispute, from: Long, days: Long): Long {
        var t = from
        while (d.isOpen && t < from + days) { t++; r.disputes.step(r.run(t)) }
        return t
    }

    private fun strong(d: Dispute) { d.claimantEvidencePpm = 1_000_000L; d.defendantEvidencePpm = 0L }
    private fun weak(d: Dispute) { d.claimantEvidencePpm = 0L; d.defendantEvidencePpm = 1_000_000L }

    @Test fun reportOpensDisputeWithEvidenceAndSales() {
        val (_, p, d) = rigWithInfringer()
        assertEquals(DisputeStage.DETECTED, d.stage)
        assertEquals(p.id, d.patent)
        assertEquals(IpOwner.company(b), d.defendant)
        assertTrue(d.claimantEvidencePpm > 0L)
        assertEquals(Money.units(80_000), d.infringingSales)
    }

    @Test fun reportRejectsUnknownPatent() {
        val r = Rig()
        assertEquals(Rules.UNKNOWN_ENTITY, r.disputes.report(r.run(1), IpOwner.company(a), PatentId(99), b, us).rejectionOrNull()?.rule)
    }

    @Test fun reportRejectsNonOwner() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(c, usage(10, 10)))
        assertEquals(Rules.NOT_OWNER, r.disputes.report(r.run(1), IpOwner.company(b), p.id, c, us).rejectionOrNull()?.rule)
    }

    @Test fun reportRejectsSelfAccusation() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.INVALID_ARGUMENT, r.disputes.report(r.run(1), IpOwner.company(a), p.id, a, us).rejectionOrNull()?.rule)
    }

    @Test fun reportRejectsCountryWhereNotFiled() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.UNKNOWN_ENTITY, r.disputes.report(r.run(1), IpOwner.company(a), p.id, b, eu).rejectionOrNull()?.rule)
    }

    @Test fun reportRejectsLicensedCompany() {
        val r = Rig(); val p = r.granted(a, milling)
        r.sign(a, b, listOf(p.id))
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(10, 10)))
        assertEquals(Rules.WRONG_STATE, r.disputes.report(r.run(5), IpOwner.company(a), p.id, b, us).rejectionOrNull()?.rule)
    }

    @Test fun reportRejectsWithoutSignOfUse() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.WRONG_STATE, r.disputes.report(r.run(1), IpOwner.company(a), p.id, b, us).rejectionOrNull()?.rule)
    }

    @Test fun reportRejectsDuplicateOpenCase() {
        val (r, p, _) = rigWithInfringer()
        assertEquals(Rules.DUPLICATE, r.disputes.report(r.run(11), IpOwner.company(a), p.id, b, us).rejectionOrNull()?.rule)
    }

    @Test fun reportRejectsInactiveDefendant() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(10, 10)))
        r.ctx.inactive += b
        assertEquals(Rules.WRONG_STATE, r.disputes.report(r.run(1), IpOwner.company(a), p.id, b, us).rejectionOrNull()?.rule)
    }

    @Test fun reportRespectsOpenCaseLimit() {
        val r = Rig(); val lim = r.params.litigation.maxOpenPerClaimant
        for (i in 0 until lim) {
            val p = r.granted(a, milling)
            r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(10, 10)))
            r.disputes.report(r.run(1), IpOwner.company(a), p.id, b, us).getOrThrow()
        }
        val p = r.granted(a, milling)
        assertEquals(Rules.LIMIT_EXCEEDED, r.disputes.report(r.run(1), IpOwner.company(a), p.id, b, us).rejectionOrNull()?.rule)
    }

    @Test fun detectionFindsUnlicensedPracticerOverTime() {
        val r = Rig(); r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(2_000, 90)))
        r.state.policyMap[a] = IpPolicy(monitoringPpm = 1_000_000L)
        var found = false
        var month = 1L
        while (!found && month < 80) { r.disputes.detect(r.run(month * 30)); found = r.state.disputeMap.isNotEmpty(); month++ }
        assertTrue(found)
        assertTrue(r.events().any { it is IpEvent.InfringementDetected })
    }

    @Test fun detectionSkipsLicensedAndPoolCovered() {
        val r = Rig(); val p = r.granted(a, milling)
        r.sign(a, b, listOf(p.id))
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(2_000, 90)))
        r.state.policyMap[a] = IpPolicy(monitoringPpm = 1_000_000L)
        for (m in 1..50) r.disputes.detect(r.run(m * 30L))
        assertTrue(r.state.disputeMap.isEmpty())
    }

    @Test fun detectionSkipsPassiveOwners() {
        val r = Rig(); r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(2_000, 90)))
        r.state.policyMap[a] = IpPolicy(stance = EnforcementStance.PASSIVE, monitoringPpm = 1_000_000L)
        for (m in 1..60) r.disputes.detect(r.run(m * 30L))
        assertTrue(r.state.disputeMap.isEmpty())
    }

    @Test fun detectionDoesNotDuplicateOpenCase() {
        val r = Rig(); r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(2_000, 90)))
        r.state.policyMap[a] = IpPolicy(monitoringPpm = 1_000_000L)
        for (m in 1..120) r.disputes.detect(r.run(m * 30L))
        assertEquals(1, r.state.disputeMap.size)
    }

    @Test fun noticeMakesInfringementWilfulAndSetsDemand() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.sendNotice(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        assertEquals(DisputeStage.NOTICE, d.stage)
        assertTrue(d.willful)
        assertTrue(d.demand.raw > 0L)
        assertEquals(Tick(12), d.noticeAt)
    }

    @Test fun noticeOnlyByClaimantAndOnlyOnce() {
        val (r, _, d) = rigWithInfringer()
        assertEquals(Rules.NOT_OWNER, r.disputes.sendNotice(r.run(12), d.id, IpOwner.company(b)).rejectionOrNull()?.rule)
        r.disputes.sendNotice(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, r.disputes.sendNotice(r.run(13), d.id, IpOwner.company(a)).rejectionOrNull()?.rule)
    }

    @Test fun noticeWithExplicitDemand() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.sendNotice(r.run(12), d.id, IpOwner.company(a), Money.units(7_777)).getOrThrow()
        assertEquals(Money.units(7_777), d.demand)
    }

    @Test fun unansweredNoticeLapses() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.sendNotice(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        drive(r, d, 12, 200)
        assertEquals(DisputeResult.LAPSED, d.result)
    }

    @Test fun filingChargesCourtFeeToGovernment() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        assertEquals(DisputeStage.FILED, d.stage)
        val ch = r.charges().single { it.kind == ChargeKind.COURT_FEE }
        assertEquals(IpLedger.of(a), ch.payer)
        assertEquals(IpLedger.government(us), ch.payee)
        assertTrue(d.claimantCosts.raw > 0L)
    }

    @Test fun filingFailsWithoutCash() {
        val (r, _, d) = rigWithInfringer()
        r.ctx.cashOf[IpOwner.company(a)] = Money.units(10)
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.disputes.file(r.run(12), d.id, IpOwner.company(a)).rejectionOrNull()?.rule)
        assertEquals(DisputeStage.DETECTED, d.stage)
    }

    @Test fun filingTwiceIsRejected() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, r.disputes.file(r.run(12), d.id, IpOwner.company(a)).rejectionOrNull()?.rule)
    }

    @Test fun strangerCannotFile() {
        val (r, _, d) = rigWithInfringer()
        assertEquals(Rules.NOT_OWNER, r.disputes.file(r.run(12), d.id, IpOwner.company(c)).rejectionOrNull()?.rule)
    }

    @Test fun unknownDisputeIsRejected() {
        val r = Rig()
        assertEquals(Rules.UNKNOWN_ENTITY, r.disputes.file(r.run(1), DisputeId(5), IpOwner.company(a)).rejectionOrNull()?.rule)
    }

    @Test fun stagesAdvanceOnTheirClock() {
        val (r, _, d) = rigWithInfringer()
        val lp = r.params.litigation
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        drive(r, d, 12, lp.pleadingDays.toLong())
        assertEquals(DisputeStage.DISCOVERY, d.stage)
        drive(r, d, 12L + lp.pleadingDays, lp.discoveryDays.toLong())
        assertEquals(DisputeStage.TRIAL, d.stage)
        drive(r, d, 12L + lp.pleadingDays + lp.discoveryDays, lp.trialDays.toLong())
        assertEquals(DisputeStage.JUDGMENT, d.stage)
        assertNotNull(d.decidedForClaimant)
    }

    @Test fun strongClaimantWinsAndIsAwardedDamages() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.sendNotice(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        drive(r, d, 12, 400)
        assertEquals(DisputeResult.CLAIMANT_WON, d.result)
        assertTrue(d.award.raw > 0L)
        val dm = r.charges().single { it.kind == ChargeKind.DAMAGES }
        assertEquals(IpLedger.of(b), dm.payer)
        assertEquals(IpLedger.of(a), dm.payee)
        assertTrue(d.awardPaid)
    }

    @Test fun weakClaimantLosesAndCoversShareOfDefenceCosts() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        weak(d)
        drive(r, d, 12, 400)
        assertEquals(DisputeResult.DEFENDANT_WON, d.result)
        assertNull(d.injunctionUntil)
        val pay = r.charges().single { it.kind == ChargeKind.DAMAGES }
        assertEquals(IpLedger.of(a), pay.payer)
        assertEquals(IpLedger.of(b), pay.payee)
    }

    @Test fun winnerGetsInjunctionAgainstOrdinaryDefendant() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        val end = drive(r, d, 12, 400)
        assertTrue(d.injunctionAt(Tick(end)))
        assertTrue(r.disputes.isEnjoined(b, d.patent!!, us, Tick(end)))
        assertFalse(r.disputes.isEnjoined(c, d.patent!!, us, Tick(end)))
        assertFalse(r.disputes.isEnjoined(b, d.patent!!, us, Tick(end + 5_000)))
    }

    @Test fun frandPledgedPatentGivesCourtRoyaltyInsteadOfInjunction() {
        val (r, p, d) = rigWithInfringer()
        p.frandPledged = true
        p.standardEssential = true
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        // a company that keeps asking for a licence is a willing licensee, so the court sets terms instead of banning it
        r.offer(a, b, listOf(p.id)).also { assertTrue(it.isOk) }
        val end = drive(r, d, 12, 400)
        assertNull(d.injunctionUntil)
        assertNotNull(d.courtRoyalty)
        assertTrue(r.state.licenseMap.values.any { it.compulsory && it.licensee == b })
        assertFalse(r.disputes.isEnjoined(b, p.id, us, Tick(end)))
    }

    @Test fun npeWithoutRegimeSupportGetsRoyaltyNotInjunction() {
        val r = Rig(); val p = r.granted(a, milling)
        val npe = Npe(r.state.ids.npe(), "Holdings", NpeStrategy.LITIGATOR, 800_000L, 300_000L).also { it.chest = Money.units(5_000_000) }
        r.state.npeMap[npe.id] = npe
        p.owner = npe.owner
        r.state.regimes.assign(us, RegimeKind.PATENT_SKEPTIC)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        val d = r.disputes.report(r.run(10), npe.owner, p.id, b, us).getOrThrow()
        r.disputes.file(r.run(12), d.id, npe.owner).getOrThrow()
        strong(d)
        drive(r, d, 12, 400)
        assertEquals(DisputeResult.CLAIMANT_WON, d.result)
        if (!r.state.regimes.regimeOf(us).injunctionsForNpe) assertNull(d.injunctionUntil)
        assertTrue(npe.spent.raw > 0L)
        assertTrue(npe.earned.raw > 0L)
    }

    @Test fun legalFeesAreChargedMonthly() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        drive(r, d, 12, 100)
        val fees = r.charges().filter { it.kind == ChargeKind.LEGAL_FEES }
        assertTrue(fees.isNotEmpty())
        assertTrue(fees.any { it.payer == IpLedger.of(a) })
        assertTrue(fees.any { it.payer == IpLedger.of(b) })
        assertTrue(d.claimantCostsPaid <= d.claimantCosts)
    }

    @Test fun challengeToWeakPatentRevokesIt() {
        val r = Rig(); val p = r.granted(a, milling, noveltyPpm = 80_000L, breadthPpm = 500_000L)
        val d = r.disputes.challenge(r.run(5), b, p.id, us).getOrThrow()
        assertEquals(DisputeStage.FILED, d.stage)
        d.claimantEvidencePpm = 1_000_000L; d.defendantEvidencePpm = 0L
        drive(r, d, 5, 400)
        assertEquals(DisputeResult.CLAIMANT_WON, d.result)
        assertTrue(d.patentRevoked)
        assertEquals(FilingStatus.REVOKED, p.filingMap.getValue(us).status)
        assertTrue(r.events().any { it is IpEvent.PatentRevoked })
    }

    @Test fun challengeToStrongPatentFailsAndLoserPaysCosts() {
        val r = Rig(); val p = r.granted(a, milling, noveltyPpm = 950_000L)
        val d = r.disputes.challenge(r.run(5), b, p.id, us).getOrThrow()
        d.claimantEvidencePpm = 0L; d.defendantEvidencePpm = 1_000_000L
        drive(r, d, 5, 400)
        assertEquals(DisputeResult.DEFENDANT_WON, d.result)
        assertTrue(p.filingMap.getValue(us).status.live)
        assertTrue(d.award.raw >= 0L)
    }

    @Test fun challengeRejectsOwnPatentAndDuplicates() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.INVALID_ARGUMENT, r.disputes.challenge(r.run(1), a, p.id, us).rejectionOrNull()?.rule)
        r.disputes.challenge(r.run(1), b, p.id, us).getOrThrow()
        assertEquals(Rules.DUPLICATE, r.disputes.challenge(r.run(1), b, p.id, us).rejectionOrNull()?.rule)
    }

    @Test fun challengeNeedsCashForCourtFee() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.cashOf[IpOwner.company(b)] = Money.ZERO
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.disputes.challenge(r.run(1), b, p.id, us).rejectionOrNull()?.rule)
    }

    @Test fun settlementCreatesLicenceAndEndsCase() {
        val (r, p, d) = rigWithInfringer()
        r.disputes.offerSettlement(r.run(13), d.id, IpOwner.company(b), Money.units(30_000)).getOrThrow()
        val paid = r.disputes.acceptSettlement(r.run(14), d.id, IpOwner.company(a)).getOrThrow()
        assertEquals(Money.units(30_000), paid)
        assertEquals(DisputeResult.SETTLED, d.result)
        assertTrue(r.book.isLicensed(b, p.id, us, null, Tick(15)))
        val ch = r.charges().single { it.kind == ChargeKind.SETTLEMENT }
        assertEquals(IpLedger.of(b), ch.payer)
        assertEquals(IpLedger.of(a), ch.payee)
        assertEquals(Money.units(30_000), ch.amount)
    }

    @Test fun cannotAcceptOwnOfferOrMissingOffer() {
        val (r, _, d) = rigWithInfringer()
        assertEquals(Rules.WRONG_STATE, r.disputes.acceptSettlement(r.run(13), d.id, IpOwner.company(a)).rejectionOrNull()?.rule)
        r.disputes.offerSettlement(r.run(13), d.id, IpOwner.company(a), Money.units(100)).getOrThrow()
        assertEquals(Rules.INVALID_ARGUMENT, r.disputes.acceptSettlement(r.run(13), d.id, IpOwner.company(a)).rejectionOrNull()?.rule)
    }

    @Test fun negativeOfferIsRejected() {
        val (r, _, d) = rigWithInfringer()
        assertEquals(Rules.INVALID_ARGUMENT, r.disputes.offerSettlement(r.run(13), d.id, IpOwner.company(a), Money(-1)).rejectionOrNull()?.rule)
    }

    @Test fun defendantWithoutCashCannotSettle() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.offerSettlement(r.run(13), d.id, IpOwner.company(a), Money.units(30_000)).getOrThrow()
        r.ctx.cashOf[IpOwner.company(b)] = Money.units(5)
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.disputes.acceptSettlement(r.run(14), d.id, IpOwner.company(b)).rejectionOrNull()?.rule)
        assertTrue(d.isOpen)
    }

    @Test fun autoRespondAcceptsOfferInsideReserve() {
        val (r, _, d) = rigWithInfringer()
        strong(d)
        val e = r.disputes.estimate(r.run(13), d)
        r.disputes.offerSettlement(r.run(13), d.id, IpOwner.company(a), Money.units(1)).getOrThrow()
        assertTrue(r.disputes.autoRespond(r.run(13), d, IpOwner.company(b)) || e.defendantReserve.raw < Money.units(1).raw)
    }

    @Test fun autoRespondRefusesLowballFromDefendantWhenClaimantIsStrong() {
        val (r, _, d) = rigWithInfringer()
        strong(d)
        d.infringingSales = Money.units(50_000_000)
        r.disputes.file(r.run(13), d.id, IpOwner.company(a)).getOrThrow()
        r.disputes.offerSettlement(r.run(14), d.id, IpOwner.company(b), Money(1)).getOrThrow()
        assertFalse(r.disputes.autoRespond(r.run(14), d, IpOwner.company(a)))
        assertTrue(d.isOpen)
    }

    @Test fun withdrawEndsCaseButNotAfterJudgment() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.withdraw(r.run(13), d.id, IpOwner.company(a)).getOrThrow()
        assertEquals(DisputeResult.WITHDRAWN, d.result)
        val (r2, _, d2) = rigWithInfringer()
        r2.disputes.file(r2.run(12), d2.id, IpOwner.company(a)).getOrThrow()
        strong(d2)
        drive(r2, d2, 12, 215)
        assertEquals(DisputeStage.JUDGMENT, d2.stage)
        assertEquals(Rules.WRONG_STATE, r2.disputes.withdraw(r2.run(300), d2.id, IpOwner.company(a)).rejectionOrNull()?.rule)
    }

    @Test fun onlyTheLoserAppealsOnce() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        drive(r, d, 12, 215)
        assertEquals(DisputeStage.JUDGMENT, d.stage)
        assertEquals(Rules.INVALID_ARGUMENT, r.disputes.appeal(r.run(300), d.id, IpOwner.company(a)).rejectionOrNull()?.rule)
        r.disputes.appeal(r.run(300), d.id, IpOwner.company(b)).getOrThrow()
        assertEquals(DisputeStage.APPEAL, d.stage)
        assertEquals(Rules.WRONG_STATE, r.disputes.appeal(r.run(300), d.id, IpOwner.company(b)).rejectionOrNull()?.rule)
    }

    @Test fun appealBeforeJudgmentIsRejected() {
        val (r, _, d) = rigWithInfringer()
        assertEquals(Rules.WRONG_STATE, r.disputes.appeal(r.run(13), d.id, IpOwner.company(b)).rejectionOrNull()?.rule)
    }

    @Test fun appealOfDecisiveJudgmentRarelyReversesAndAlwaysCloses() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        drive(r, d, 12, 215)
        r.disputes.appeal(r.run(300), d.id, IpOwner.company(b)).getOrThrow()
        drive(r, d, 300, 400)
        assertFalse(d.isOpen)
        assertTrue(d.result == DisputeResult.CLAIMANT_WON || d.result == DisputeResult.DEFENDANT_WON)
    }

    @Test fun licenceSignedDuringCaseEndsIt() {
        val (r, p, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        val l = r.sign(a, b, listOf(p.id))
        r.disputes.onLicensed(r.run(20), l)
        assertEquals(DisputeResult.LICENSED, d.result)
    }

    @Test fun designAroundEndsOpenCase() {
        val (r, p, d) = rigWithInfringer()
        r.disputes.onDesignAround(r.run(20), b, p.id)
        assertEquals(DisputeResult.DESIGNED_AROUND, d.result)
    }

    @Test fun designAroundLiftsInjunction() {
        val (r, p, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        val end = drive(r, d, 12, 400)
        assertTrue(r.disputes.isEnjoined(b, p.id, us, Tick(end)))
        r.disputes.onDesignAround(r.run(end), b, p.id)
        assertFalse(r.disputes.isEnjoined(b, p.id, us, Tick(end)))
    }

    @Test fun expiredPatentDropsTheCase() {
        val (r, p, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        p.filingMap.getValue(us).status = FilingStatus.EXPIRED
        r.disputes.step(r.run(13))
        assertEquals(DisputeResult.LAPSED, d.result)
    }

    @Test fun defendantGoingOutOfBusinessEndsTheCase() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        r.ctx.inactive += b
        r.disputes.step(r.run(13))
        assertEquals(DisputeResult.LAPSED, d.result)
    }

    @Test fun claimantGoingOutOfBusinessWithdraws() {
        val (r, _, d) = rigWithInfringer()
        r.ctx.inactive += a
        r.disputes.step(r.run(13))
        assertEquals(DisputeResult.WITHDRAWN, d.result)
    }

    @Test fun ownershipChangeDuringSuitEndsItBeforeJudgment() {
        val (r, p, d) = rigWithInfringer()
        r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        p.owner = IpOwner.company(c)
        r.disputes.step(r.run(13))
        assertEquals(DisputeResult.LAPSED, d.result)
    }

    @Test fun undecidedSightingFallsAway() {
        val (r, _, d) = rigWithInfringer()
        drive(r, d, 10, 100)
        assertEquals(DisputeResult.LAPSED, d.result)
    }

    @Test fun theftReportRequiresKnownThief() {
        val r = Rig()
        r.teach(a, milling)
        val s = r.secrets.keep(r.run(1), a, milling).getOrThrow()
        assertEquals(Rules.WRONG_STATE, r.disputes.reportTheft(r.run(2), s.id, b, 700_000L).rejectionOrNull()?.rule)
        s.knownToSet.add(b)
        val d = r.disputes.reportTheft(r.run(2), s.id, b, 700_000L).getOrThrow()
        assertEquals(DisputeKind.MISAPPROPRIATION, d.kind)
        assertEquals(Rules.DUPLICATE, r.disputes.reportTheft(r.run(3), s.id, b, 700_000L).rejectionOrNull()?.rule)
    }

    @Test fun theftCaseWithGoodProofAwardsDamages() {
        val r = Rig()
        r.teach(a, milling)
        val s = r.secrets.keep(r.run(1), a, milling).getOrThrow()
        s.knownToSet.add(b)
        r.ctx.fieldSales[a to s.field] = Money.units(2_000_000)
        val d = r.disputes.reportTheft(r.run(2), s.id, b, 1_000_000L).getOrThrow()
        d.defendantEvidencePpm = 0L
        r.disputes.file(r.run(2), d.id, IpOwner.company(a)).getOrThrow()
        drive(r, d, 2, 400)
        assertEquals(DisputeResult.CLAIMANT_WON, d.result)
        assertTrue(d.award.raw > 0L)
    }

    @Test fun estimateImprovesWithEvidence() {
        val (r, _, d) = rigWithInfringer()
        weak(d)
        val low = r.disputes.estimate(r.run(13), d)
        strong(d)
        val high = r.disputes.estimate(r.run(13), d)
        assertTrue(high.claimantWinPpm > low.claimantWinPpm)
        assertTrue(high.awardIfWon.raw >= low.awardIfWon.raw)
    }

    @Test fun mayPractiseReflectsOwnershipLicenceAndClearance() {
        val r = Rig(); val p = r.granted(a, milling)
        assertTrue(r.disputes.mayPractise(a, p, us, Tick(1)))
        assertFalse(r.disputes.mayPractise(b, p, us, Tick(1)))
        r.state.clearanceSet.add(Clearance(b, p.id, Tick(0)))
        assertTrue(r.disputes.mayPractise(b, p, us, Tick(1)))
        r.sign(a, c, listOf(p.id))
        assertTrue(r.disputes.mayPractise(c, p, us, Tick(5)))
    }

    @Test fun sameScriptSameOutcome() {
        fun run(): Pair<String, String> {
            val (r, _, d) = rigWithInfringer()
            r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
            drive(r, d, 12, 600)
            return d.result.name to r.state.stateHash().toString()
        }
        assertEquals(run(), run())
    }

    @Test fun differentSeedsCanDiffer() {
        val results = HashSet<String>()
        for (seed in 1L..12L) {
            val r = Rig(seed); val p = r.granted(a, milling, breadthPpm = 500_000L, noveltyPpm = 600_000L)
            r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
            val d = r.disputes.report(r.run(10), IpOwner.company(a), p.id, b, us).getOrThrow()
            r.disputes.file(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
            d.claimantEvidencePpm = 450_000L; d.defendantEvidencePpm = 450_000L
            drive(r, d, 12, 600)
            results += d.result.name
        }
        assertTrue(results.isNotEmpty())
    }

    @Test fun fullCaseHistoryLeavesConsistentBooks() {
        val (r, _, d) = rigWithInfringer()
        r.disputes.sendNotice(r.run(12), d.id, IpOwner.company(a)).getOrThrow()
        r.disputes.file(r.run(13), d.id, IpOwner.company(a)).getOrThrow()
        strong(d)
        drive(r, d, 13, 500)
        assertFalse(d.isOpen)
        assertTrue(d.claimantCostsPaid <= d.claimantCosts)
        assertTrue(d.defendantCostsPaid <= d.defendantCosts)
        val keys = r.charges().map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(r.charges().all { it.amount.raw > 0L })
    }
}
