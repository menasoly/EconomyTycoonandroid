package app.many.economytycoon.ip

import app.many.economytycoon.core.Rules
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardsServiceTest {
    private fun svc(r: Rig) = StandardsService(r.state, r.params)

    @Test fun declaringMakesThePatentEssentialAndPledged() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 700_000L)
        svc(r).declare(p.id, IpOwner.company(a), true).getOrThrow()
        assertTrue(p.standardEssential)
        assertTrue(p.frandPledged)
    }

    @Test fun essentialPatentsMustCarryThePledge() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 700_000L)
        assertEquals(Rules.REGULATION, svc(r).declare(p.id, IpOwner.company(a), false).rejectionOrNull()?.rule)
        assertFalse(p.standardEssential)
    }

    @Test fun onlyOwnerDeclaresAndOnlyOnce() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 700_000L)
        assertEquals(Rules.NOT_OWNER, svc(r).declare(p.id, IpOwner.company(b), true).rejectionOrNull()?.rule)
        svc(r).declare(p.id, IpOwner.company(a), true).getOrThrow()
        assertEquals(Rules.DUPLICATE, svc(r).declare(p.id, IpOwner.company(a), true).rejectionOrNull()?.rule)
    }

    @Test fun narrowClaimsCannotBeEssential() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 200_000L)
        assertEquals(Rules.INVALID_ARGUMENT, svc(r).declare(p.id, IpOwner.company(a), true).rejectionOrNull()?.rule)
    }

    @Test fun deadAndUnknownPatentsAreRefused() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 700_000L)
        assertEquals(Rules.UNKNOWN_ENTITY, svc(r).declare(PatentId(9), IpOwner.company(a), true).rejectionOrNull()?.rule)
        p.filingMap.getValue(us).status = FilingStatus.EXPIRED
        assertEquals(Rules.WRONG_STATE, svc(r).declare(p.id, IpOwner.company(a), true).rejectionOrNull()?.rule)
    }

    @Test fun pledgeIsIrrevocableAndAllowedWithoutDeclaration() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 200_000L)
        svc(r).pledge(p.id, IpOwner.company(a)).getOrThrow()
        assertTrue(p.frandPledged)
        assertFalse(p.standardEssential)
        assertEquals(Rules.DUPLICATE, svc(r).pledge(p.id, IpOwner.company(a)).rejectionOrNull()?.rule)
        assertEquals(Rules.NOT_OWNER, svc(r).pledge(p.id, IpOwner.company(b)).rejectionOrNull()?.rule)
    }

    @Test fun essentialListFiltersByFieldAndLife() {
        val r = Rig(); val p = r.granted(a, milling, breadthPpm = 700_000L); val q = r.granted(b, ovens, breadthPpm = 700_000L)
        svc(r).declare(p.id, IpOwner.company(a), true).getOrThrow()
        svc(r).declare(q.id, IpOwner.company(b), true).getOrThrow()
        assertEquals(setOf(p.id), svc(r).essentialIn(p.field).filter { it.field == p.field }.map { it.id }.toSet().intersect(setOf(p.id)))
        p.filingMap.getValue(us).status = FilingStatus.EXPIRED
        assertTrue(svc(r).essentialIn(p.field).none { it.id == p.id })
    }
}
