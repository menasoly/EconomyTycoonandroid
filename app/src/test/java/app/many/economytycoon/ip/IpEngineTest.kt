package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.EngineRig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpEngineTest {
    private fun patentOf(r: EngineRig, company: app.many.economytycoon.core.CompanyId = a): Patent {
        r.teach(company, milling)
        r.exec(IpCommand.FilePatent(company, milling, listOf(us))).outcome.getOrThrow()
        return r.engine.state.patentsOf(company).first()
    }

    /** Waits for the office to rule and, if the patent was refused, tries again with another seed-independent technology. */
    private fun grantedPatent(seed: Long, company: app.many.economytycoon.core.CompanyId = a): Pair<EngineRig, Patent> {
        for (s in seed..seed + 40) {
            val r = EngineRig(s)
            val p = patentOf(r, company)
            r.advanceTo(700)
            if (p.filingMap.getValue(us).status == FilingStatus.GRANTED) return r to p
        }
        throw AssertionError("no seed produced a grant")
    }

    @Test fun emptyWorldProducesNothing() {
        val r = EngineRig()
        r.engine.start()
        r.advanceTo(400)
        assertTrue(r.charges.isEmpty())
        assertTrue(r.events.isEmpty())
    }

    @Test fun startSeedsTheScriptedHoldersOnce() {
        val r = EngineRig()
        r.engine.start(); r.engine.start()
        assertEquals(3, r.engine.state.npeMap.size)
    }

    @Test fun filingThroughTheEngineChargesFeesAndRecordsTheSale() {
        val r = EngineRig()
        r.teach(a, milling)
        val res = r.exec(IpCommand.FilePatent(a, milling, listOf(us)))
        assertTrue(res.outcome.isOk)
        assertEquals(1, res.step.charges.size)
        assertEquals(ChargeKind.FILING_FEE, res.step.charges[0].kind)
        assertEquals(1, r.engine.state.patentMap.size)
    }

    @Test fun inactiveCompaniesCannotCommand() {
        val r = EngineRig()
        r.teach(a, milling)
        r.ctx.inactive += a
        assertEquals(Rules.WRONG_STATE, r.exec(IpCommand.FilePatent(a, milling, listOf(us))).outcome.rejectionOrNull()?.rule)
    }

    @Test fun rejectedCommandsChangeNothing() {
        val r = EngineRig()
        val before = r.hash()
        val res = r.exec(IpCommand.FilePatent(a, milling, listOf(us)))
        assertFalse(res.outcome.isOk)
        assertEquals(before, r.hash())
        assertTrue(res.step.charges.isEmpty())
    }

    @Test fun filingMovesASecretIntoThePatent() {
        val r = EngineRig()
        r.teach(a, milling)
        val s = r.exec(IpCommand.KeepSecret(a, milling)).outcome.getOrThrow()
        assertTrue(s.contains("secret"))
        val secret = r.engine.state.secretsOf(a).single()
        r.exec(IpCommand.FilePatent(a, milling, listOf(us))).outcome.getOrThrow()
        assertEquals(SecretStatus.PATENTED, secret.status)
    }

    @Test fun patentLifecycleRunsThroughTheDailySchedule() {
        val (r, p) = grantedPatent(1)
        assertEquals(FilingStatus.GRANTED, p.filingMap.getValue(us).status)
        assertTrue(r.events.any { it is IpEvent.PatentGranted })
        assertTrue(r.charges.any { it.kind == ChargeKind.MAINTENANCE_FEE } || p.autoMaintain.not())
    }

    @Test fun unpaidFeesEventuallyLapseThePatent() {
        val (r, p) = grantedPatent(1)
        r.ctx.cashOf[IpOwner.company(a)] = Money.ZERO
        r.advanceTo(1_600)
        assertFalse(p.filingMap.getValue(us).status == FilingStatus.GRANTED)
        assertTrue(r.events.any { it is IpEvent.PatentLapsed })
    }

    @Test fun licensingFlowThroughCommands() {
        val (r, p) = grantedPatent(1)
        val offer = IpCommand.OfferLicense(a, OfferRequest(IpOwner.company(a), b, listOf(p.id), RoyaltyTerms(RoyaltyBasis.PERCENT, percent = app.many.economytycoon.core.Rate.percent(4))))
        assertEquals(Rules.NOT_OWNER, r.exec(IpCommand.OfferLicense(c, offer.request)).outcome.rejectionOrNull()?.rule)
        r.exec(offer).outcome.getOrThrow()
        val l = r.engine.state.licenseMap.values.single()
        r.exec(IpCommand.AcceptLicense(b, l.id)).outcome.getOrThrow()
        assertTrue(l.isActive)
        r.ctx.usageOf[b to milling] = usage(10_000, 100)
        r.advanceTo(r.day + 200)
        assertTrue(r.charges.any { it.kind == ChargeKind.ROYALTY && it.payer == IpLedger.of(b) })
    }

    @Test fun acceptingALicenceClosesAnOpenDisputeBetweenTheParties() {
        val (r, p) = grantedPatent(1)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        r.exec(IpCommand.ReportInfringement(a, p.id, b, us)).outcome.getOrThrow()
        val d = r.engine.state.disputeMap.values.single()
        r.exec(IpCommand.OfferLicense(a, OfferRequest(IpOwner.company(a), b, listOf(p.id), RoyaltyTerms(RoyaltyBasis.PERCENT, percent = app.many.economytycoon.core.Rate.percent(2))))).outcome.getOrThrow()
        r.exec(IpCommand.AcceptLicense(b, r.engine.state.licenseMap.values.single().id)).outcome.getOrThrow()
        assertEquals(DisputeResult.LICENSED, d.result)
    }

    @Test fun disputeFromSightingToSettlement() {
        val (r, p) = grantedPatent(1)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        r.exec(IpCommand.ReportInfringement(a, p.id, b, us)).outcome.getOrThrow()
        val d = r.engine.state.disputeMap.values.single()
        r.exec(IpCommand.SendNotice(a, d.id)).outcome.getOrThrow()
        r.exec(IpCommand.OfferSettlement(b, d.id, Money.units(25_000))).outcome.getOrThrow()
        val res = r.exec(IpCommand.AcceptSettlement(a, d.id))
        assertTrue(res.outcome.isOk)
        assertEquals(DisputeResult.SETTLED, d.result)
        assertTrue(r.engine.effects.rightsFor(b, milling, us, null, Tick(r.day + 1)) == RightsStatus.LICENSED)
    }

    @Test fun courtCaseRunsOnTheDailyClock() {
        val (r, p) = grantedPatent(1)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        r.exec(IpCommand.ReportInfringement(a, p.id, b, us)).outcome.getOrThrow()
        val d = r.engine.state.disputeMap.values.single()
        d.claimantEvidencePpm = 1_000_000L; d.defendantEvidencePpm = 0L
        r.exec(IpCommand.FileSuit(a, d.id)).outcome.getOrThrow()
        r.ctx.practicers[milling to us] = mutableListOf()
        r.advanceTo(r.day + 400)
        assertEquals(DisputeResult.CLAIMANT_WON, d.result)
        assertTrue(r.charges.any { it.kind == ChargeKind.DAMAGES })
        assertTrue(r.events.any { it is IpEvent.JudgmentGiven })
    }

    @Test fun secretTheftNoticedBecomesACase() {
        for (seed in 1L..80L) {
            val r = EngineRig(seed)
            r.teach(a, milling)
            r.exec(IpCommand.KeepSecret(a, milling)).outcome.getOrThrow()
            val sid = r.engine.state.secretsOf(a).single().id
            r.engine.state.secretMap.getValue(sid).protectionPpm = 0L
            var hit = false
            for (i in 1..10) {
                r.day += 1
                val res = r.exec(IpCommand.StealSecret(b, sid))
                if (res.outcome.getOrNull()?.contains("traced") == true) { hit = true; break }
                if (r.engine.state.secretMap.getValue(sid).knownTo.contains(b)) break
            }
            if (hit) {
                val d = r.engine.state.disputeMap.values.single()
                assertEquals(DisputeKind.MISAPPROPRIATION, d.kind)
                assertEquals(IpOwner.company(b), d.defendant)
                return
            }
        }
        throw AssertionError("no seed produced a traced theft")
    }

    @Test fun designAroundThroughCommands() {
        val (r, p) = grantedPatent(1)
        r.exec(IpCommand.StartDesignAround(b, p.id)).outcome.getOrThrow()
        val d = r.engine.state.designAroundMap.values.single()
        r.advanceTo(d.completesAt.value + 1)
        assertTrue(d.status != DesignAroundStatus.RUNNING)
        assertTrue(r.events.any { it is IpEvent.DesignAroundFinished })
    }

    @Test fun marketThroughCommands() {
        val (r, p) = grantedPatent(1)
        r.exec(IpCommand.ListPatent(a, p.id, Money.units(30_000))).outcome.getOrThrow()
        assertEquals(Rules.NOT_OWNER, r.exec(IpCommand.UnlistPatent(b, p.id)).outcome.rejectionOrNull()?.rule)
        r.exec(IpCommand.BuyPatent(b, p.id)).outcome.getOrThrow()
        assertEquals(IpOwner.company(b), p.owner)
    }

    @Test fun policyCommandStoresThePolicy() {
        val r = EngineRig()
        r.exec(IpCommand.SetPolicy(a, IpPolicy(stance = EnforcementStance.LITIGATE, monitoringPpm = 900_000L))).outcome.getOrThrow()
        assertEquals(EnforcementStance.LITIGATE, r.engine.state.policy(a).stance)
    }

    @Test fun computerCompaniesActMonthly() {
        val r = EngineRig()
        r.ctx.ai += a
        r.ctx.homes[a] = us
        r.teach(a, milling)
        r.ctx.fieldSales[a to TechFields.of(IpTestSupport.content.technology(milling))] = Money.units(30_000_000)
        r.advanceTo(29)
        assertTrue(r.engine.state.patentMap.isEmpty() && r.engine.state.secretMap.isEmpty())
        r.advanceTo(30)
        assertTrue(r.engine.state.patentMap.isNotEmpty() || r.engine.state.secretMap.isNotEmpty())
    }

    @Test fun distressedCompaniesSellTheirPatents() {
        val (r, p) = grantedPatent(1)
        r.engine.start()
        r.ctx.distressed += a
        r.advanceTo(r.day + 31)
        assertTrue(p.owner.isNpe)
        assertTrue(r.events.any { it is IpEvent.PatentSold })
    }

    @Test fun closingACompanyWindsEverythingUp() {
        val (r, p) = grantedPatent(1)
        r.teach(a, ovens)
        r.exec(IpCommand.KeepSecret(a, ovens)).outcome.getOrThrow()
        r.exec(IpCommand.OfferLicense(a, OfferRequest(IpOwner.company(a), b, listOf(p.id), RoyaltyTerms(RoyaltyBasis.PERCENT, percent = app.many.economytycoon.core.Rate.percent(3))))).outcome.getOrThrow()
        r.ctx.inactive += a
        r.absorb(r.engine.companyClosed(Tick(r.day), r.ctx, a))
        assertTrue(r.engine.state.secretsOf(a).none { it.isLive })
        assertTrue(r.engine.state.licenseMap.values.none { it.isOffer })
        assertFalse(p.isLive)
    }

    @Test fun yearlyCreditAppearsInTheStepResult() {
        val r = EngineRig()
        r.teach(a, milling)
        r.exec(IpCommand.KeepSecret(a, milling)).outcome.getOrThrow()
        r.ctx.homes[a] = us
        r.ctx.rdSpend[a] = Money.units(1_000_000)
        r.advanceTo(361)
        assertTrue(r.charges.any { it.kind == ChargeKind.TAX_CREDIT })
    }

    @Test fun everyChargeKeyIsUniqueAndAmountsArePositive() {
        val (r, p) = grantedPatent(1)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        r.exec(IpCommand.ReportInfringement(a, p.id, b, us)).outcome.getOrThrow()
        val d = r.engine.state.disputeMap.values.single()
        r.exec(IpCommand.FileSuit(a, d.id)).outcome.getOrThrow()
        r.advanceTo(r.day + 500)
        assertEquals(r.charges.size, r.charges.map { it.key }.toSet().size)
        assertTrue(r.charges.all { it.amount.raw > 0L })
        assertNotNull(r.charges.firstOrNull())
    }

    @Test fun sameInputsSameStateHash() {
        fun go(): String {
            val (r, p) = grantedPatent(1)
            r.engine.start()
            r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
            r.exec(IpCommand.ReportInfringement(a, p.id, b, us))
            r.advanceTo(r.day + 300)
            return r.hash()
        }
        assertEquals(go(), go())
    }

    @Test fun describerCoversEveryCommand() {
        val sample = listOf<IpCommand>(
            IpCommand.FilePatent(a, milling, listOf(us)), IpCommand.AbandonPatent(a, PatentId(1)), IpCommand.SetAutoMaintain(a, PatentId(1), true),
            IpCommand.PayMaintenance(a, PatentId(1), us), IpCommand.DeclareEssential(a, PatentId(1), true), IpCommand.KeepSecret(a, milling),
            IpCommand.InvestInSecret(a, SecretId(1), Money.units(5)), IpCommand.DropSecret(a, SecretId(1)), IpCommand.StealSecret(a, SecretId(1)),
            IpCommand.AcceptLicense(a, LicenseId(1)), IpCommand.DeclineLicense(a, LicenseId(1)), IpCommand.WithdrawOffer(a, LicenseId(1)),
            IpCommand.TerminateLicense(a, LicenseId(1)), IpCommand.RequestFrandLicense(a, PatentId(1)), IpCommand.AuditLicense(a, LicenseId(1)),
            IpCommand.ProposeCrossLicense(a, b, listOf(PatentId(1)), listOf(PatentId(2))), IpCommand.JoinPool(a, PoolId(1), listOf(PatentId(1))),
            IpCommand.LeavePool(a, PoolId(1)), IpCommand.DissolvePool(a, PoolId(1)), IpCommand.OfferPool(a, PoolId(1), b),
            IpCommand.ReportInfringement(a, PatentId(1), b, us), IpCommand.SendNotice(a, DisputeId(1)), IpCommand.FileSuit(a, DisputeId(1)),
            IpCommand.Appeal(a, DisputeId(1)), IpCommand.WithdrawSuit(a, DisputeId(1)), IpCommand.OfferSettlement(a, DisputeId(1), Money.units(1)),
            IpCommand.AcceptSettlement(a, DisputeId(1)), IpCommand.ChallengePatent(a, PatentId(1), us), IpCommand.StartDesignAround(a, PatentId(1)),
            IpCommand.CancelDesignAround(a, DesignAroundId(1)), IpCommand.RequestCompulsory(a, PatentId(1), us, CompulsoryGround.PUBLIC_HEALTH),
            IpCommand.WithdrawCompulsory(a, CompulsoryId(1)), IpCommand.ListPatent(a, PatentId(1), Money.units(1)), IpCommand.UnlistPatent(a, PatentId(1)),
            IpCommand.BuyPatent(a, PatentId(1)), IpCommand.SetPolicy(a, IpPolicy()),
        )
        val lines = sample.map { IpCommandDescriber.describe(it) }
        assertTrue(lines.all { it.isNotBlank() })
        assertEquals(lines.size, lines.toSet().size)
    }
}
