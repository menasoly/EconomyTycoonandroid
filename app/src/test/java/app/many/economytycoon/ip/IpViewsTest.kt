package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.EngineRig
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpViewsTest {
    private fun model(r: EngineRig, who: app.many.economytycoon.core.CompanyId = a): IpModel = IpViews(r.engine).build(r.ctx, who, Tick(r.day))

    @Test fun emptyWorldGivesAnEmptyModel() {
        val r = EngineRig()
        val m = model(r)
        assertTrue(m.patents.isEmpty() && m.secrets.isEmpty() && m.licensesIn.isEmpty() && m.licensesOut.isEmpty() && m.disputes.isEmpty())
        assertEquals(Money.ZERO, m.portfolioValue)
        assertTrue(m.insights.isEmpty())
        assertEquals(IpPolicy(), m.policy)
    }

    @Test fun patentRowsCarryFilingsValueAndFees() {
        val r = IpTestSupport.busyWorld()
        val m = model(r, a)
        val row = m.patents.first { it.title.contains("Milling", ignoreCase = true) }
        assertEquals(setOf(us, eu), row.filings.map { it.country }.toSet())
        assertTrue(row.filings.all { it.nextFee != null && it.nextFee!!.raw > 0L })
        assertTrue(row.value.raw > 0L)
        assertTrue(row.remainingDays > 1_000L)
        assertEquals(1, row.licensedOut)
        assertEquals(PatentStanding.ACTIVE, row.standing)
        assertTrue(row.validityPpm in 300_000L..950_000L)
    }

    @Test fun essentialAndListedFlagsShowUp() {
        val r = IpTestSupport.busyWorld()
        assertTrue(model(r, a).patents.any { it.essential && it.frandPledged })
        assertTrue(model(r, c).patents.any { it.askingPrice != null })
    }

    @Test fun licencesAppearOnBothSides() {
        val r = IpTestSupport.busyWorld()
        val out = model(r, a).licensesOut
        assertEquals(1, out.size)
        assertEquals(LicenseSide.LICENSOR, out[0].side)
        assertEquals(IpOwner.company(c), out[0].counterparty)
        assertEquals(LicenseKind.SOLE, out[0].kind)
        val inn = model(r, c).licensesIn
        assertEquals(1, inn.size)
        assertEquals(LicenseSide.LICENSEE, inn[0].side)
        assertEquals(IpOwner.company(a), inn[0].counterparty)
        assertTrue(inn[0].daysLeft!! > 0L)
    }

    @Test fun offersWaitingAreSeparateFromRunningLicences() {
        val r = IpTestSupport.busyWorld()
        val po = r.engine.state.patentsOf(b).first()
        r.exec(IpCommand.OfferLicense(b, OfferRequest(IpOwner.company(b), a, listOf(po.id), RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(8_000))))).outcome.getOrThrow()
        val m = model(r, a)
        assertEquals(1, m.offersToMe.size)
        assertTrue(m.offersToMe.all { it.status == LicenseStatus.OFFERED })
        assertTrue(m.licensesIn.none { it.status == LicenseStatus.OFFERED })
    }

    @Test fun disputesShowPerspectiveEstimateAndOffer() {
        val r = IpTestSupport.busyWorld()
        val mine = model(r, a).disputes.single()
        assertTrue(mine.iAmClaimant)
        assertEquals(DisputeStage.NOTICE, mine.stage)
        assertNotNull(mine.estimate)
        assertEquals(IpOwner.company(b), mine.offerBy)
        assertEquals(Money.units(1_234), mine.offerAmount)
        val theirs = model(r, b).disputes.single()
        assertFalse(theirs.iAmClaimant)
        assertEquals(mine.id, theirs.id)
        assertTrue(model(r, c).disputes.isEmpty())
    }

    @Test fun closedDisputesHaveNoEstimate() {
        val r = IpTestSupport.busyWorld()
        val d = r.engine.state.disputeMap.values.first()
        r.exec(IpCommand.WithdrawSuit(a, d.id)).outcome.getOrThrow()
        val row = model(r, a).disputes.single()
        assertNull(row.estimate)
        assertEquals(DisputeResult.WITHDRAWN, row.result)
    }

    @Test fun secretsShowHazardAndThieves() {
        val r = IpTestSupport.busyWorld()
        val s = model(r, c).secrets.single()
        assertEquals(SecretStatus.ACTIVE, s.status)
        assertEquals(listOf(b), s.stolenBy)
        assertTrue(s.protectionPpm > 0L)
        assertTrue(s.hazardPpm > 0L && s.halfLifeDays > 0L)
        assertTrue(s.name.isNotBlank())
    }

    @Test fun programmesAreListedForTheirOwner() {
        val r = IpTestSupport.busyWorld()
        assertEquals(1, model(r, b).designs.size)
        assertTrue(model(r, a).designs.isEmpty())
        assertEquals(1, model(r, c).compulsory.size)
        assertEquals(CompulsoryGround.PUBLIC_HEALTH, model(r, c).compulsory[0].ground)
        assertTrue(model(r, c).compulsory[0].daysToDecision > 0L)
    }

    @Test fun marketHidesOwnListingsAndSortsByPrice() {
        val r = IpTestSupport.busyWorld()
        assertTrue(model(r, c).market.isEmpty())
        val seen = model(r, a).market
        assertEquals(1, seen.size)
        assertEquals(Money.units(5_000), seen[0].price)
        assertEquals(IpOwner.company(c), seen[0].seller)
        r.engine.state.patentMap.values.first { it.owner == IpOwner.company(b) }.let { p ->
            r.exec(IpCommand.ListPatent(b, p.id, Money.units(100))).outcome.getOrThrow()
        }
        val both = model(r, a).market
        assertEquals(listOf(Money.units(100), Money.units(5_000)), both.map { it.price })
    }

    @Test fun scriptedHoldersAreVisible() {
        val m = model(IpTestSupport.busyWorld(), a)
        assertEquals(3, m.npes.size)
        assertTrue(m.npes.all { it.chest.raw > 0L })
    }

    @Test fun unlicensedUseShowsSuggestedTerms() {
        val r = IpTestSupport.busyWorld()
        r.ctx.practicers[ovens to us] = mutableListOf(Practice(a, usage(300, 50)))
        val m = model(r, b)
        val u = m.unlicensedUse.single()
        assertEquals(a, u.user)
        assertEquals(RoyaltyBasis.PERCENT, u.suggested.basis)
        assertTrue(u.suggested.percent.ppm in 1_000L..r.engine.params.licensing.maxPercentPpm)
    }

    @Test fun unprotectedTechnologiesComeWithAdvice() {
        val r = EngineRig()
        r.ctx.homes[a] = us
        r.teach(a, milling)
        r.ctx.fieldSales[a to TechFields.of(IpTestSupport.content.technology(milling))] = Money.units(30_000_000)
        val row = model(r).unprotected.single()
        assertEquals(milling, row.tech)
        assertTrue(row.fees.raw > 0L && row.grantChancePpm > 0L)
        assertEquals(listOf(us), row.countries)
        assertTrue(model(r).insights.any { it.code == "ip-unprotected" })
    }

    @Test fun portfolioAndIncomeTotalsAreConsistent() {
        val r = IpTestSupport.busyWorld()
        val m = model(r, a)
        assertTrue(m.portfolioValue.raw > 0L)
        assertEquals(m.royaltiesReceived, model(r, a).royaltiesReceived)
        assertTrue(m.royaltiesPaid.raw >= 0L)
    }

    @Test fun buildingTheModelDoesNotChangeTheState() {
        val r = IpTestSupport.busyWorld()
        val before = r.hash()
        model(r, a); model(r, b); model(r, c)
        assertEquals(before, r.hash())
    }

    @Test fun poolRowsShowMembershipAndShare() {
        val r = IpTestSupport.busyWorld()
        val pm = r.engine.state.patentsOf(a).first()
        val run = IpRun(Tick(r.day), r.ctx, Wallet(r.ctx), IpOutput(r.engine.state.ids))
        val pool = r.engine.pools.form(run, a, "Mills", pm.field, RoyaltyTerms(RoyaltyBasis.PERCENT, percent = app.many.economytycoon.core.Rate.percent(2)), 100_000L).getOrThrow()
        r.engine.pools.join(run, pool.id, a, listOf(pm.id)).getOrThrow()
        val row = model(r, a).pools.single()
        assertTrue(row.iAmMember)
        assertEquals(1_000_000L, row.myShareOfIncomePpm)
        assertEquals(20_000L, row.ratePpm)
        assertTrue(model(r, c).pools.isEmpty())
    }
}
