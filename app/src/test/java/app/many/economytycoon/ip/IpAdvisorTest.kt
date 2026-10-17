package app.many.economytycoon.ip

import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.EngineRig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpAdvisorTest {
    private fun codes(f: IpFacts) = IpAdvisor.advise(f).map { it.code }

    @Test fun quietWorldGivesNoAdvice() {
        assertTrue(IpAdvisor.advise(IpFacts()).isEmpty())
    }

    @Test fun unprotectedTechnologiesAreFlagged() {
        val f = IpFacts(unprotected = listOf(UnprotectedFact("Milling", 50_000, Protection.PATENT), UnprotectedFact("Ovens", 10_000, Protection.SECRET)))
        val i = IpAdvisor.advise(f).single()
        assertEquals("ip-unprotected", i.code)
        assertTrue(i.title.startsWith("2 technologies"))
        assertTrue(i.detail.contains("Milling"))
        assertTrue(i.detail.contains("patent"))
        assertEquals(Topic.RESEARCH, i.topic)
    }

    @Test fun secretAdviceIsWordedForSecrets() {
        val f = IpFacts(unprotected = listOf(UnprotectedFact("Ovens", 10_000, Protection.SECRET)))
        assertTrue(IpAdvisor.advise(f).single().detail.contains("secret"))
        val g = IpFacts(unprotected = listOf(UnprotectedFact("Ovens", 10_000, Protection.NEITHER)))
        assertTrue(IpAdvisor.advise(g).single().detail.contains("Neither"))
    }

    @Test fun renewalsOutsideTheWindowAreIgnored() {
        assertTrue(codes(IpFacts(renewals = listOf(RenewalFact("Milling", 200, 900, false, true)))).isEmpty())
    }

    @Test fun renewalWithoutAutoPayIsRemindedAndUnaffordableOnesAreUrgent() {
        assertEquals(listOf("ip-renewal"), codes(IpFacts(renewals = listOf(RenewalFact("Milling", 20, 900, false, true)))))
        assertTrue(codes(IpFacts(renewals = listOf(RenewalFact("Milling", 20, 900, true, true)))).isEmpty())
        val urgent = IpAdvisor.advise(IpFacts(renewals = listOf(RenewalFact("Milling", 20, 900, true, false)))).single()
        assertEquals("ip-renewal-unpaid", urgent.code)
        assertEquals(Topic.FINANCE, urgent.topic)
        assertTrue(urgent.priority >= 80)
    }

    @Test fun expiryWarningHasAWindow() {
        assertTrue(codes(IpFacts(expiring = listOf(ExpiringFact("Milling", 2_000, true)))).isEmpty())
        assertEquals(listOf("ip-expiring"), codes(IpFacts(expiring = listOf(ExpiringFact("Milling", 100, true)))))
    }

    @Test fun unlicensedUsersAreNamedByTheBiggest() {
        val i = IpAdvisor.advise(IpFacts(unlicensedUsers = listOf(UserFact("Milling", b, 100), UserFact("Milling", IpTestSupport.c, 9_000)))).single()
        assertEquals("ip-unlicensed", i.code)
        assertTrue(i.detail.contains("Company 3"))
    }

    @Test fun likelyLossesAreUrgent() {
        val c = CaseFact(DisputeId(1), "Milling", DisputeStage.TRIAL, 800_000L, 90_000L, true, false, DisputeKind.INFRINGEMENT)
        val i = IpAdvisor.advise(IpFacts(casesAgainst = listOf(c))).single()
        assertEquals("ip-case-losing", i.code)
        assertTrue(i.detail.contains("settlement"))
        val noZone = IpAdvisor.advise(IpFacts(casesAgainst = listOf(c.copy(zoneOfAgreement = false)))).single()
        assertTrue(noZone.detail.contains("designing around"))
    }

    @Test fun weakCasesAgainstYouAreNotAlarming() {
        val c = CaseFact(DisputeId(1), "Milling", DisputeStage.TRIAL, 200_000L, 90_000L, true, false, DisputeKind.INFRINGEMENT)
        assertTrue(codes(IpFacts(casesAgainst = listOf(c))).isEmpty())
    }

    @Test fun demandFromAHolderThatMakesNothingGetsItsOwnAdvice() {
        val c = CaseFact(DisputeId(1), "Milling", DisputeStage.NOTICE, 300_000L, 9_000L, true, true, DisputeKind.INFRINGEMENT)
        assertEquals(listOf("ip-npe-demand"), codes(IpFacts(casesAgainst = listOf(c))))
    }

    @Test fun theftAccusationIsMentioned() {
        val c = CaseFact(DisputeId(1), "x", DisputeStage.FILED, 300_000L, 9_000L, false, false, DisputeKind.MISAPPROPRIATION)
        assertEquals(listOf("ip-theft-case"), codes(IpFacts(casesAgainst = listOf(c))))
    }

    @Test fun settlementSuggestedOnlyOnceInCourtWithRoomToDeal() {
        val early = CaseFact(DisputeId(1), "Milling", DisputeStage.NOTICE, 500_000L, 9_000L, true, false, DisputeKind.INFRINGEMENT)
        assertTrue(codes(IpFacts(casesBy = listOf(early))).isEmpty())
        assertEquals(listOf("ip-settle"), codes(IpFacts(casesBy = listOf(early.copy(stage = DisputeStage.DISCOVERY)))))
        assertTrue(codes(IpFacts(casesBy = listOf(early.copy(stage = DisputeStage.DISCOVERY, zoneOfAgreement = false)))).isEmpty())
    }

    @Test fun secretsLikelyToLeakAreFlagged() {
        assertEquals(listOf("ip-secret-risk"), codes(IpFacts(secretsAtRisk = listOf(SecretFact("Milling", 100, 200_000L, 40)))))
        assertTrue(codes(IpFacts(secretsAtRisk = listOf(SecretFact("Milling", 4_000, 800_000L, 3)))).isEmpty())
    }

    @Test fun arrearsAndAudits() {
        assertEquals(listOf("ip-arrears"), codes(IpFacts(arrears = listOf(ArrearsFact(b, 4_000, 1)))))
        assertEquals(listOf("ip-audit"), codes(IpFacts(auditsDue = listOf(AuditFact(LicenseId(1), b, 400, 50_000)))))
        assertTrue(codes(IpFacts(auditsDue = listOf(AuditFact(LicenseId(1), b, 400, 100)))).isEmpty())
        assertTrue(codes(IpFacts(auditsDue = listOf(AuditFact(LicenseId(1), b, 30, 50_000)))).isEmpty())
    }

    @Test fun offersIdleAndBurdenAndCredit() {
        assertEquals(listOf("ip-offers"), codes(IpFacts(offersWaiting = 2)))
        assertTrue(codes(IpFacts(idlePatents = 1)).isEmpty())
        assertEquals(listOf("ip-idle"), codes(IpFacts(idlePatents = 3)))
        assertEquals(listOf("ip-royalty-burden"), codes(IpFacts(royaltyBurdenPpm = 200_000L)))
        assertEquals(listOf("ip-credit"), codes(IpFacts(researchSpendUnits = 10, creditPpm = 80_000L)))
        assertTrue(codes(IpFacts(researchSpendUnits = 0, creditPpm = 80_000L)).isEmpty())
    }

    @Test fun adviceIsOrderedByPriorityThenCode() {
        val f = IpFacts(
            offersWaiting = 1, idlePatents = 5, royaltyBurdenPpm = 300_000L,
            renewals = listOf(RenewalFact("Milling", 5, 900, true, false)),
        )
        val list = IpAdvisor.advise(f)
        assertEquals(list.sortedWith(compareByDescending<app.many.economytycoon.advisor.Insight> { it.priority }.thenBy { it.code }), list)
        assertEquals("ip-renewal-unpaid", list.first().code)
    }

    @Test fun customRuleSetsAreHonoured() {
        val f = IpFacts(offersWaiting = 1, idlePatents = 5)
        assertEquals(listOf("ip-offers"), IpAdvisor.advise(f, rules = listOf(IpAdvisor.offers)).map { it.code })
    }

    @Test fun everyInsightIsWellFormed() {
        val f = IpFacts(
            unprotected = listOf(UnprotectedFact("Milling", 1, Protection.PATENT)), renewals = listOf(RenewalFact("M", 1, 1, false, true)),
            expiring = listOf(ExpiringFact("M", 1, false)), unlicensedUsers = listOf(UserFact("M", b, 1)),
            casesAgainst = listOf(CaseFact(DisputeId(1), "M", DisputeStage.TRIAL, 900_000L, 1, true, false, DisputeKind.INFRINGEMENT)),
            casesBy = listOf(CaseFact(DisputeId(2), "M", DisputeStage.TRIAL, 900_000L, 1, true, false, DisputeKind.INFRINGEMENT)),
            secretsAtRisk = listOf(SecretFact("M", 1, 1, 1)), arrears = listOf(ArrearsFact(b, 1, 1)),
            auditsDue = listOf(AuditFact(LicenseId(1), b, 999, 999_999)), offersWaiting = 1, idlePatents = 4, royaltyBurdenPpm = 900_000L, researchSpendUnits = 1, creditPpm = 1,
        )
        val list = IpAdvisor.advise(f)
        assertEquals(IpAdvisor.all.size, list.map { it.code }.toSet().size)
        assertTrue(list.all { it.title.isNotBlank() && it.detail.isNotBlank() && it.priority in 0..100 })
    }

    // ---- facts built from a running engine ----

    private fun world(): EngineRig {
        val r = EngineRig()
        r.ctx.homes[a] = us
        r.teach(a, milling)
        r.ctx.fieldSales[a to TechFields.of(IpTestSupport.content.technology(milling))] = Money.units(30_000_000)
        return r
    }

    private fun factsOf(r: EngineRig, who: app.many.economytycoon.core.CompanyId = a): IpFacts =
        IpAdvisor.facts(r.engine, IpRun(Tick(r.day), r.ctx, Wallet(r.ctx), IpOutput(r.engine.state.ids)), who)

    @Test fun factsSeeUnprotectedTechnology() {
        val f = factsOf(world())
        assertEquals(1, f.unprotected.size)
        assertTrue(f.unprotected[0].advantageUnits > 0L)
    }

    @Test fun factsSeeRenewalsExpiryAndUsers() {
        val r = world()
        r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        r.advanceTo(20)
        val f = factsOf(r)
        assertEquals(1, f.expiring.size)
        assertTrue(f.expiring[0].daysLeft > 1_000L)
        assertEquals(1, f.unlicensedUsers.size)
        assertEquals(b, f.unlicensedUsers[0].user)
        assertTrue(f.renewals.isNotEmpty())
        assertTrue(f.unprotected.isEmpty())
    }

    @Test fun factsSeeCasesOnBothSides() {
        val r = world()
        val p = r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        r.exec(IpCommand.ReportInfringement(a, p.id, b, us)).outcome.getOrThrow()
        assertEquals(1, factsOf(r, a).casesBy.size)
        assertEquals(1, factsOf(r, b).casesAgainst.size)
        assertTrue(factsOf(r, b).casesBy.isEmpty())
    }

    @Test fun factsSeeLicensingState() {
        val r = world()
        val p = r.granted(a, milling)
        r.exec(IpCommand.OfferLicense(a, OfferRequest(IpOwner.company(a), b, listOf(p.id), RoyaltyTerms(RoyaltyBasis.PERCENT, percent = app.many.economytycoon.core.Rate.percent(3))))).outcome.getOrThrow()
        assertEquals(1, factsOf(r, b).offersWaiting)
        val l = r.engine.state.licenseMap.values.single()
        r.exec(IpCommand.AcceptLicense(b, l.id)).outcome.getOrThrow()
        l.arrearsForTest(Money.units(500))
        assertEquals(1, factsOf(r, a).arrears.size)
        assertEquals(0, factsOf(r, a).idlePatents)
        assertFalse(factsOf(r, b).offersWaiting > 0)
    }

    private fun License.arrearsForTest(m: Money) { this.arrears = m }

    @Test fun factsSeeSecretRisk() {
        val r = world()
        r.exec(IpCommand.KeepSecret(a, milling)).outcome.getOrThrow()
        r.ctx.knowerCount[a to milling] = 80
        r.engine.state.secretsOf(a).first().knowers = 80
        r.engine.state.secretsOf(a).first().exposurePpm = 900_000L
        val f = factsOf(r)
        assertEquals(1, f.secretsAtRisk.size)
        assertTrue(f.secretsAtRisk[0].halfLifeDays < 360L)
        assertTrue(IpAdvisor.advise(f).any { it.code == "ip-secret-risk" })
    }
}
