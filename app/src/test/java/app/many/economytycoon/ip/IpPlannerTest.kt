package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import app.many.economytycoon.ip.IpTestSupport.usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPlannerTest {
    private fun planner(r: Rig) = IpPlanner(r.state, r.office, r.book, r.disputes, r.designs, r.valuer, IpTestSupport.content, r.params)

    private fun companyWhere(r: Rig, test: (IpPersonality) -> Boolean): CompanyId {
        val pl = planner(r)
        for (i in 10L..400L) if (test(pl.personality(CompanyId(i)))) return CompanyId(i)
        throw AssertionError("no company with that personality")
    }

    @Test fun personalitiesAreStableAndInRange() {
        val r = Rig()
        val pl = planner(r)
        val p1 = pl.personality(a)
        assertEquals(p1, pl.personality(a))
        for (i in 1L..40L) {
            val p = pl.personality(CompanyId(i))
            assertTrue(p.patentingPpm in 200_000L..900_000L)
            assertTrue(p.licensingPpm in 150_000L..900_000L)
            assertTrue(p.litigiousPpm in 100_000L..900_000L)
            assertTrue(p.secrecyPpm in 100_000L..900_000L)
        }
        val distinct = (1L..40L).map { pl.personality(CompanyId(it)) }.toSet()
        assertTrue(distinct.size > 30)
    }

    @Test fun personalitiesDependOnTheWorldSeed() {
        assertTrue((1L..20L).any { planner(Rig(1)).personality(CompanyId(it)) != planner(Rig(2)).personality(CompanyId(it)) })
    }

    private fun protectScene(r: Rig, co: CompanyId, sales: Long = 20_000_000L) {
        r.ctx.homes[co] = us
        r.teach(co, milling)
        r.ctx.techsOf[co] = listOf(milling)
        r.ctx.fieldSales[co to TechFields.of(IpTestSupport.content.technology(milling))] = Money.units(sales)
    }

    @Test fun profitableUnprotectedTechnologyGetsProtection() {
        val r = Rig(); protectScene(r, a)
        val cmds = planner(r).protectionMoves(r.run(10), a, planner(r).personality(a))
        assertEquals(1, cmds.size)
        assertTrue(cmds[0] is IpCommand.FilePatent || cmds[0] is IpCommand.KeepSecret)
    }

    @Test fun nothingToProtectMeansNoMoves() {
        val r = Rig(); protectScene(r, a, sales = 0L)
        assertTrue(planner(r).protectionMoves(r.run(10), a, planner(r).personality(a)).isEmpty())
    }

    @Test fun noHomeCountryMeansNoFilings() {
        val r = Rig(); protectScene(r, a); r.ctx.homes.clear()
        assertTrue(planner(r).protectionMoves(r.run(10), a, planner(r).personality(a)).isEmpty())
    }

    @Test fun alreadyProtectedTechnologyIsLeftAlone() {
        val r = Rig(); protectScene(r, a)
        r.granted(a, milling)
        assertTrue(planner(r).protectionMoves(r.run(10), a, planner(r).personality(a)).isEmpty())
        val r2 = Rig(); protectScene(r2, a)
        r2.secrets.keep(r2.run(1), a, milling).getOrThrow()
        assertTrue(planner(r2).protectionMoves(r2.run(10), a, planner(r2).personality(a)).isEmpty())
    }

    @Test fun visibleProductsPushTowardsPatents() {
        val r = Rig(); protectScene(r, a)
        r.ctx.exposureOf[a to milling] = Rate.percent(95)
        r.ctx.knowerCount[a to milling] = 60
        val cmds = planner(r).protectionMoves(r.run(10), a, IpPersonality(500_000L, 500_000L, 500_000L, 500_000L))
        assertTrue(cmds.single() is IpCommand.FilePatent)
    }

    @Test fun hiddenProcessesWithFewKnowersFavourSecrecyForSecretLovers() {
        val r = Rig(); protectScene(r, a, sales = 400_000_000L)
        r.ctx.exposureOf[a to milling] = Rate.ZERO
        r.ctx.knowerCount[a to milling] = 2
        val cmds = planner(r).protectionMoves(r.run(10), a, IpPersonality(200_000L, 500_000L, 500_000L, 900_000L))
        assertTrue(cmds.single() is IpCommand.KeepSecret)
    }

    @Test fun filingsPerRoundAreCapped() {
        val r = Rig(1L, IpParams(planner = PlannerParams(maxFilingsPerRound = 1)))
        protectScene(r, a, sales = 400_000_000L)
        r.teach(a, ovens); r.ctx.techsOf[a] = listOf(milling, ovens)
        r.ctx.fieldSales[a to TechFields.of(IpTestSupport.content.technology(ovens))] = Money.units(400_000_000L)
        val cmds = planner(r).protectionMoves(r.run(10), a, IpPersonality(900_000L, 500_000L, 500_000L, 100_000L))
        assertTrue(cmds.size <= 1)
    }

    @Test fun openLicensorOffersToUnlicensedPracticers() {
        val r = Rig()
        val co = companyWhere(r) { it.licensingPpm >= 400_000L }
        val p = r.granted(co, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        val cmds = planner(r).licensingMoves(r.run(10), co, planner(r).personality(co))
        val offer = cmds.single() as IpCommand.OfferLicense
        assertEquals(b, offer.request.licensee)
        assertEquals(listOf(p.id), offer.request.patents)
        assertEquals(RoyaltyBasis.PERCENT, offer.request.terms.basis)
    }

    @Test fun reluctantLicensorsAndLitigatorsDoNotOffer() {
        val r = Rig()
        val shy = companyWhere(r) { it.licensingPpm < 400_000L }
        r.granted(shy, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        assertTrue(planner(r).licensingMoves(r.run(10), shy, planner(r).personality(shy)).isEmpty())
        val open = companyWhere(r) { it.licensingPpm >= 400_000L }
        r.granted(open, milling)
        r.state.policyMap[open] = IpPolicy(stance = EnforcementStance.LITIGATE)
        assertTrue(planner(r).licensingMoves(r.run(10), open, planner(r).personality(open)).isEmpty())
    }

    @Test fun noRepeatOffersAndNoOffersToLicensees() {
        val r = Rig()
        val co = companyWhere(r) { it.licensingPpm >= 400_000L }
        val p = r.granted(co, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        val first = planner(r).licensingMoves(r.run(10), co, planner(r).personality(co)).single() as IpCommand.OfferLicense
        r.book.offer(r.run(10), first.request).getOrThrow()
        assertTrue(planner(r).licensingMoves(r.run(11), co, planner(r).personality(co)).isEmpty())
        r.sign(co, b, listOf(p.id), r.percent(3))
        assertTrue(planner(r).licensingMoves(r.run(12), co, planner(r).personality(co)).isEmpty())
    }

    private fun caseOf(r: Rig, owner: CompanyId): Pair<Patent, Dispute> {
        val p = r.granted(owner, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, usage(1_000, 80)))
        return p to r.disputes.report(r.run(10), IpOwner.company(owner), p.id, b, us).getOrThrow()
    }

    @Test fun claimantNoticesFirst() {
        val r = Rig(); val (_, d) = caseOf(r, a)
        val cmds = planner(r).disputeMoves(r.run(11), a, IpPersonality(500_000L, 500_000L, 500_000L, 500_000L))
        assertEquals(listOf<IpCommand>(IpCommand.SendNotice(a, d.id)), cmds)
    }

    @Test fun litigiousStanceGoesStraightToCourt() {
        val r = Rig(); val (_, d) = caseOf(r, a)
        r.state.policyMap[a] = IpPolicy(stance = EnforcementStance.LITIGATE)
        val cmds = planner(r).disputeMoves(r.run(11), a, IpPersonality(500_000L, 500_000L, 800_000L, 500_000L))
        assertEquals(listOf<IpCommand>(IpCommand.FileSuit(a, d.id)), cmds)
    }

    @Test fun unansweredNoticeBecomesASuitWhenTheCaseIsStrong() {
        val r = Rig(); val (_, d) = caseOf(r, a)
        d.claimantEvidencePpm = 1_000_000L; d.defendantEvidencePpm = 0L
        r.disputes.sendNotice(r.run(11), d.id, IpOwner.company(a)).getOrThrow()
        val me = IpPersonality(500_000L, 500_000L, 800_000L, 500_000L)
        assertTrue(planner(r).disputeMoves(r.run(12), a, me).isEmpty())
        val cmds = planner(r).disputeMoves(r.run(11L + r.params.litigation.noticeResponseDays), a, me)
        assertEquals(listOf<IpCommand>(IpCommand.FileSuit(a, d.id)), cmds)
    }

    @Test fun acceptableOffersAreAcceptedByEitherSide() {
        val r = Rig(); val (_, d) = caseOf(r, a)
        d.infringingSales = Money.units(50_000_000)
        r.disputes.offerSettlement(r.run(11), d.id, IpOwner.company(b), Money.units(40_000_000)).getOrThrow()
        val me = IpPersonality(500_000L, 500_000L, 500_000L, 500_000L)
        assertEquals(listOf<IpCommand>(IpCommand.AcceptSettlement(a, d.id)), planner(r).disputeMoves(r.run(12), a, me).filterIsInstance<IpCommand.AcceptSettlement>())
        val r2 = Rig(); val (_, d2) = caseOf(r2, a)
        d2.infringingSales = Money.units(50_000_000)
        d2.claimantEvidencePpm = 1_000_000L
        r2.disputes.offerSettlement(r2.run(11), d2.id, IpOwner.company(a), Money(1)).getOrThrow()
        assertTrue(planner(r2).disputeMoves(r2.run(12), b, me).any { it is IpCommand.AcceptSettlement })
    }

    @Test fun defendantRedesignsWhenItIsCheaperThanLosing() {
        val r = Rig(); val (p, d) = caseOf(r, a)
        d.claimantEvidencePpm = 1_000_000L; d.defendantEvidencePpm = 0L
        d.infringingSales = Money.units(500_000_000)
        val cmds = planner(r).disputeMoves(r.run(12), b, IpPersonality(500_000L, 500_000L, 500_000L, 500_000L))
        assertEquals(listOf<IpCommand>(IpCommand.StartDesignAround(b, p.id, d.id)), cmds)
    }

    @Test fun defendantWithAStrongDefenceDoesNothing() {
        val r = Rig(); val (_, d) = caseOf(r, a)
        d.claimantEvidencePpm = 0L; d.defendantEvidencePpm = 1_000_000L
        assertTrue(planner(r).disputeMoves(r.run(12), b, IpPersonality(500_000L, 500_000L, 500_000L, 500_000L)).isEmpty())
    }

    @Test fun cullDropsPatentsThatDoNotPayTheirWay() {
        val r = Rig(); val p = r.granted(a, milling)
        val cmds = planner(r).cullMoves(r.run(10), a)
        assertEquals(listOf<IpCommand>(IpCommand.AbandonPatent(a, p.id, null)), cmds)
    }

    @Test fun cullKeepsLicensedListedAndEssentialPatents() {
        val r = Rig(); val p = r.granted(a, milling); val q = r.granted(a, ovens); val s = r.granted(a, milling)
        r.sign(a, b, listOf(p.id))
        r.market.list(r.run(1), q.id, IpOwner.company(a), Money.units(1_000)).getOrThrow()
        s.standardEssential = true
        assertTrue(planner(r).cullMoves(r.run(10), a).isEmpty())
    }

    @Test fun cullKeepsPatentsWithRealSales() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.usageOf[a to milling] = usage(1_000_000, 100)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(a, usage(1_000_000, 100)))
        r.ctx.fieldSales[a to p.field] = Money.units(900_000_000)
        assertTrue(planner(r).cullMoves(r.run(10), a).isEmpty())
    }

    @Test fun litigiousFirmChallengesWeakPatentsInTheWay() {
        val r = Rig()
        val co = companyWhere(r) { it.litigiousPpm >= 600_000L }
        r.granted(b, milling, noveltyPpm = 200_000L)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(co, usage(1_000, 80)))
        val cmds = planner(r).challengeMoves(r.run(10), co, planner(r).personality(co))
        assertTrue(cmds.single() is IpCommand.ChallengePatent)
    }

    @Test fun peacefulFirmsAndStrongPatentsAreNotChallenged() {
        val r = Rig()
        val calm = companyWhere(r) { it.litigiousPpm < 600_000L }
        r.granted(b, milling, noveltyPpm = 200_000L)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(calm, usage(1_000, 80)))
        assertTrue(planner(r).challengeMoves(r.run(10), calm, planner(r).personality(calm)).isEmpty())
        val r2 = Rig()
        val keen = companyWhere(r2) { it.litigiousPpm >= 600_000L }
        r2.granted(b, milling, noveltyPpm = 900_000L)
        r2.ctx.practicers[milling to us] = mutableListOf(Practice(keen, usage(1_000, 80)))
        assertTrue(planner(r2).challengeMoves(r2.run(10), keen, planner(r2).personality(keen)).isEmpty())
    }

    @Test fun planIsDeterministic() {
        fun go(): List<IpCommand> {
            val r = Rig(); protectScene(r, a)
            caseOf(r, a)
            return planner(r).plan(r.run(40), a)
        }
        assertEquals(go(), go())
        assertFalse(go().isEmpty())
        assertEquals(Tick(40), Rig().run(40).tick)
    }
}
