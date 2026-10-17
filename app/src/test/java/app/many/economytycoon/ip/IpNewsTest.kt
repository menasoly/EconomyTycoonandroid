package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpNewsTest {
    private val names = IpNames({ "Co${it.value}" }, { "$${it.raw / Money.SCALE}" }, { "P${it.value}" })
    private val t = Tick(77)

    @Test fun grantAndRefusalHeadlines() {
        val r = Rig(); val p = r.granted(a, milling)
        val g = IpNews.headline(IpEvent.PatentGranted(p.id, us, false, t), r.state, names)!!
        assertTrue(g.text.contains("Co1") && g.text.contains(p.title))
        assertEquals(NewsWeight.MINOR, g.weight)
        assertEquals(77L, g.tick)
        assertTrue(IpNews.headline(IpEvent.PatentGranted(p.id, us, true, t), r.state, names)!!.text.contains("narrower"))
        assertTrue(IpNews.headline(IpEvent.PatentRefused(p.id, us, t), r.state, names)!!.text.contains("refuses"))
    }

    @Test fun revocationAndSaleAreBigNews() {
        val r = Rig(); val p = r.granted(a, milling)
        val rev = IpNews.headline(IpEvent.PatentRevoked(p.id, us, null, t), r.state, names)!!
        assertEquals(NewsWeight.MAJOR, rev.weight)
        val sale = IpNews.headline(IpEvent.PatentSold(p.id, IpOwner.company(a), IpOwner.company(b), Money.units(500), t), r.state, names)!!
        assertEquals("Co2 buys the patent on ${p.title} from Co1 for $500", sale.text)
        assertEquals(listOf(b, a), sale.companies)
    }

    @Test fun scriptedHoldersAreNamed() {
        val r = Rig(); r.npes.seed()
        val n = r.state.npeMap.values.first()
        val p = r.granted(a, milling)
        val ev = IpEvent.PatentSold(p.id, IpOwner.company(a), n.owner, Money.units(10), t)
        assertTrue(IpNews.headline(ev, r.state, names)!!.text.contains(n.name))
    }

    @Test fun routineEventsGiveNoHeadline() {
        val r = Rig(); val p = r.granted(a, milling)
        assertNull(IpNews.headline(IpEvent.MaintenancePaid(p.id, us, Money.units(1), t), r.state, names))
        assertNull(IpNews.headline(IpEvent.LicenseSigned(LicenseId(1), t), r.state, names))
        assertNull(IpNews.headline(IpEvent.EspionageAttempt(SecretId(1), b, false, false, t), r.state, names))
        assertNull(IpNews.headline(IpEvent.DesignAroundFinished(DesignAroundId(1), b, p.id, false, t), r.state, names))
    }

    @Test fun detectedEspionageIsNews() {
        val r = Rig()
        val e = IpNews.headline(IpEvent.EspionageAttempt(SecretId(1), b, true, true, t), r.state, names)!!
        assertTrue(e.text.contains("Co2") && e.text.contains("stealing"))
        assertTrue(IpNews.headline(IpEvent.EspionageAttempt(SecretId(1), b, false, true, t), r.state, names)!!.text.contains("trying to steal"))
    }

    private fun caseWorld(): Pair<Rig, Dispute> {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.practicers[milling to us] = mutableListOf(Practice(b, IpTestSupport.usage(1_000, 80)))
        return r to r.disputes.report(r.run(5), IpOwner.company(a), p.id, b, us).getOrThrow()
    }

    @Test fun disputeHeadlines() {
        val (r, d) = caseWorld()
        assertTrue(IpNews.headline(IpEvent.InfringementDetected(d.id, d.patent!!, d.defendant, t), r.state, names)!!.text.contains("accuses Co2"))
        val j = IpNews.headline(IpEvent.JudgmentGiven(d.id, true, Money.units(900), true, t), r.state, names)!!
        assertTrue(j.text.contains("Co1 wins") && j.text.contains("$900") && j.text.contains("barring"))
        assertTrue(IpNews.headline(IpEvent.JudgmentGiven(d.id, false, Money.ZERO, false, t), r.state, names)!!.text.contains("Co2 wins"))
        val s = IpNews.headline(IpEvent.DisputeClosed(d.id, DisputeResult.SETTLED, Money.units(40), t), r.state, names)!!
        assertTrue(s.text.contains("settle"))
        assertNull(IpNews.headline(IpEvent.DisputeClosed(d.id, DisputeResult.LAPSED, Money.ZERO, t), r.state, names))
    }

    @Test fun headlinesSkipsNothingElse() {
        val (r, d) = caseWorld()
        val list = IpNews.headlines(listOf(IpEvent.LicenseSigned(LicenseId(1), t), IpEvent.InfringementDetected(d.id, d.patent!!, d.defendant, t)), r.state, names)
        assertEquals(1, list.size)
    }

    @Test fun alertsReachOnlyTheCompaniesConcerned() {
        val (r, d) = caseWorld()
        val ev = IpEvent.InfringementDetected(d.id, d.patent!!, d.defendant, t)
        assertNotNull(IpNews.alertFor(ev, b, r.state, names))
        assertNull(IpNews.alertFor(ev, a, r.state, names))
        assertNull(IpNews.alertFor(ev, c, r.state, names))
        val j = IpEvent.JudgmentGiven(d.id, true, Money.ZERO, false, t)
        assertEquals("You won your patent case.", IpNews.alertFor(j, a, r.state, names))
        assertEquals("You lost your patent case.", IpNews.alertFor(j, b, r.state, names))
        assertNull(IpNews.alertFor(j, c, r.state, names))
    }

    @Test fun ownerAlerts() {
        val r = Rig(); val p = r.granted(a, milling)
        assertTrue(IpNews.alertFor(IpEvent.PatentLapsed(p.id, us, t), a, r.state, names)!!.contains("lapsed"))
        assertNull(IpNews.alertFor(IpEvent.PatentLapsed(p.id, us, t), b, r.state, names))
        assertTrue(IpNews.alertFor(IpEvent.PatentRevoked(p.id, us, null, t), a, r.state, names)!!.contains("revoked"))
        assertTrue(IpNews.alertFor(IpEvent.PatentGranted(p.id, us, false, t), a, r.state, names)!!.contains("granted"))
        assertTrue(IpNews.alertFor(IpEvent.TaxCreditPaid(a, Money.units(9), t), a, r.state, names)!!.contains("$9"))
        assertNull(IpNews.alertFor(IpEvent.TaxCreditPaid(a, Money.units(9), t), b, r.state, names))
    }

    @Test fun licenceOfferAlertGoesToTheLicensee() {
        val r = Rig(); val p = r.granted(a, milling)
        val l = r.offer(a, b, listOf(p.id)).getOrThrow()
        assertNotNull(IpNews.alertFor(IpEvent.LicenseOffered(l.id, t), b, r.state, names))
        assertNull(IpNews.alertFor(IpEvent.LicenseOffered(l.id, t), a, r.state, names))
    }

    @Test fun compulsoryAndDesignAlerts() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.health += us
        val req = r.compulsory.request(r.run(1), b, p.id, us, CompulsoryGround.PUBLIC_HEALTH).getOrThrow()
        assertTrue(IpNews.alertFor(IpEvent.CompulsoryDecided(req.id, true, t), b, r.state, names)!!.contains("granted"))
        assertTrue(IpNews.alertFor(IpEvent.CompulsoryDecided(req.id, false, t), b, r.state, names)!!.contains("refused"))
        assertNull(IpNews.alertFor(IpEvent.CompulsoryDecided(req.id, false, t), a, r.state, names))
        assertTrue(IpNews.alertFor(IpEvent.DesignAroundFinished(DesignAroundId(1), b, p.id, true, t), b, r.state, names)!!.contains("worked"))
        assertEquals(CountryId(1), us)
        assertEquals(CompanyId(1), a)
    }
}
