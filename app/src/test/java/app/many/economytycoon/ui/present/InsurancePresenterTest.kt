package app.many.economytycoon.ui.present

import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.risk.Peril
import app.many.economytycoon.risk.PolicyStatus
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsurancePresenterTest {
    private fun session(): app.many.economytycoon.session.GameSession {
        val s = PresentSupport.session("Cover Works", 21L)
        val e = s.economy
        val me = s.selectedCompany!!
        val region = e.world.regions().first().id
        val farm = e.production.grantFacility(me, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        farm.inventory.add(GoodId("wheat"), Quantity.units(500), Money.units(10_000))
        e.finance.mint(e.tick, e.companies.company(me)!!.owner, Money.units(500_000), "test:capital")
        return s
    }

    @Test fun aCompanyWithFacilitiesStartsUninsured() {
        val s = session()
        val m = InsurancePresenter.build(s)!!
        assertEquals(4, m.tiles.size)
        assertTrue(m.policies.isEmpty() && m.claims.isEmpty())
        assertTrue(m.uninsured.isNotEmpty())
        assertTrue(m.uninsured.first().from.startsWith("from "))
        assertEquals(Tone.WARN, m.tiles[3].tone)
    }

    @Test fun buyingMovesAFacilityFromUninsuredToPolicies() {
        val s = session()
        val before = InsurancePresenter.build(s)!!
        val id = before.uninsured.first().id
        val r = s.issueNow(Command.BuyPolicy(s.selectedCompany!!, id, CoverLevel.STANDARD, 800_000L, Deductible.MEDIUM))
        assertTrue(r.toString(), r.isOk)
        val after = InsurancePresenter.build(s)!!
        assertEquals(before.uninsured.size - 1, after.uninsured.size)
        val card = after.policies.single()
        assertEquals("Covered", card.status)
        assertEquals(Tone.GOOD, card.statusTone)
        assertTrue(card.active)
        assertTrue(card.cover.startsWith("Standard cover"))
        assertEquals("No claims", card.claims)
        assertTrue(card.premium.endsWith("/ month"))
    }

    @Test fun cancelledPoliciesStayInTheListAsHistory() {
        val s = session()
        val me = s.selectedCompany!!
        val id = InsurancePresenter.build(s)!!.uninsured.first().id
        s.issueNow(Command.BuyPolicy(me, id, CoverLevel.BASIC, 800_000L, Deductible.LOW))
        val policy = s.economy.risk.activePoliciesOf(me).single()
        s.issueNow(Command.CancelPolicy(me, policy.id))
        val card = InsurancePresenter.build(s)!!.policies.single()
        assertEquals("Cancelled", card.status)
        assertFalse(card.active)
        assertTrue(card.renews.startsWith("Ended"))
    }

    @Test fun aStrikeAppearsInTheClaimHistory() {
        val s = session()
        val me = s.selectedCompany!!
        val f = s.economy.production.facilitiesOf(me).first()
        s.economy.riskRunner.strike(Peril.FIRE, f.region, 600_000L, listOf(f), s.tick, null, false)
        val claim = InsurancePresenter.build(s)!!.claims.single()
        assertTrue(claim.title.startsWith("Fire at"))
        assertEquals("Nothing", claim.paid)
        assertEquals("Not insured", claim.note)
        assertEquals(Tone.BAD, claim.tone)
    }

    @Test fun anInsuredClaimShowsWhatWasRepaid() {
        val s = session()
        val me = s.selectedCompany!!
        val f = s.economy.production.facilitiesOf(me).first()
        s.issueNow(Command.BuyPolicy(me, f.id, CoverLevel.FULL, 1_000_000L, Deductible.LOW))
        s.economy.riskRunner.strike(Peril.FIRE, f.region, 700_000L, listOf(f), s.tick, null, false)
        val claim = InsurancePresenter.build(s)!!.claims.single()
        assertEquals("Repaid", claim.note)
        assertEquals(Tone.GOOD, claim.tone)
        assertEquals("1 claim, " + s.settings.money(s.economy.risk.policiesOf(me).single().claimsPaid) + " paid", InsurancePresenter.build(s)!!.policies.single().claims)
    }

    @Test fun quotesExplainWhatYouGet() {
        val s = session()
        val id = InsurancePresenter.build(s)!!.uninsured.first().id
        val q = InsurancePresenter.quote(s, id, CoverLevel.FULL, Deductible.LOW, 900_000L)!!
        assertNull(q.problem)
        assertEquals(4, q.chances.size)
        assertTrue(q.perils.contains("earthquake"))
        assertTrue(q.premiumMoney.raw > 0)
    }

    @Test fun cheaperExcessMeansADearerQuote() {
        val s = session()
        val id = InsurancePresenter.build(s)!!.uninsured.first().id
        val low = InsurancePresenter.quote(s, id, CoverLevel.STANDARD, Deductible.LOW, 800_000L)!!
        val high = InsurancePresenter.quote(s, id, CoverLevel.STANDARD, Deductible.HIGH, 800_000L)!!
        assertTrue(low.premiumMoney > high.premiumMoney)
    }

    @Test fun anInsuredFacilityQuotesWithAProblem() {
        val s = session()
        val me = s.selectedCompany!!
        val id = InsurancePresenter.build(s)!!.uninsured.first().id
        s.issueNow(Command.BuyPolicy(me, id, CoverLevel.BASIC, 800_000L, Deductible.LOW))
        assertNotNull(InsurancePresenter.quote(s, id, CoverLevel.BASIC, Deductible.LOW, 800_000L)!!.problem)
        assertTrue(InsurancePresenter.insurable(s).none { it.first == id })
    }

    @Test fun noFacilityNoQuote() {
        assertNull(InsurancePresenter.quote(session(), null, CoverLevel.BASIC, Deductible.LOW, 800_000L))
    }

    @Test fun everyStatusHasLabelAndTone() {
        for (st in PolicyStatus.values()) {
            assertTrue(InsurancePresenter.statusLabel(st).isNotBlank())
            InsurancePresenter.statusTone(st)
        }
    }
}
