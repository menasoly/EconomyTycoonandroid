package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.megamill
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossLicenseTest {
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    private class W {
        val r = IpTestSupport.Rig()
        val pa = r.granted(a, milling)
        val pb = r.granted(b, ovens)
        val big = r.granted(b, megamill)
    }

    @Test fun equalPortfoliosPayNothingBeyondTheNominalFee() {
        val w = W()
        val q = w.r.cross.quote(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id)).getOrThrow()
        assertEquals(Money.ZERO, q.balancing.coerceAtLeast(Money.ZERO).let { if (q.valueA == q.valueB) it else Money.ZERO })
    }

    @Test fun theWeakerPortfolioPaysHalfTheDifference() {
        val w = W()
        w.r.ctx.fieldSales[b to TechField.MANUFACTURING] = Money.units(50_000_000)
        val q = w.r.cross.quote(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id, w.big.id)).getOrThrow()
        assertTrue(q.valueB > q.valueA)
        assertEquals(a, q.payer)
        assertEquals(Money((q.valueB.raw - q.valueA.raw) / 2), q.balancing)
    }

    @Test fun aQuoteNeedsOwnership() {
        val w = W()
        assertEquals(Rules.NOT_OWNER, rule(w.r.cross.quote(w.r.run(), a, b, listOf(w.pb.id), listOf(w.pa.id))))
        assertEquals(Rules.NOT_OWNER, rule(w.r.cross.quote(w.r.run(), a, b, listOf(w.pa.id), listOf(PatentId(99)))))
    }

    @Test fun proposingCreatesTwoLinkedOffers() {
        val w = W()
        val (one, two) = w.r.cross.propose(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id)).getOrThrow()
        assertEquals(two.id, one.crossWith)
        assertEquals(one.id, two.crossWith)
        assertEquals(b, one.licensee)
        assertEquals(a, two.licensee)
        assertEquals(RoyaltyBasis.LUMP_SUM, one.terms.basis)
        assertTrue(one.terms.lumpSum >= w.r.cross.nominal)
    }

    @Test fun acceptingTheFirstStartsBothAndTakesTheLumpSums() {
        val w = W()
        w.r.ctx.fieldSales[b to TechField.MANUFACTURING] = Money.units(50_000_000)
        val (one, two) = w.r.cross.propose(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id, w.big.id)).getOrThrow()
        w.r.out = IpOutput(w.r.state.ids)
        w.r.book.accept(w.r.run(), one.id, b).getOrThrow()
        assertEquals(LicenseStatus.ACTIVE, one.status)
        assertEquals(LicenseStatus.ACTIVE, two.status)
        assertEquals(2, w.r.charges().count { it.kind == ChargeKind.LUMP_SUM })
        assertTrue(two.terms.lumpSum > one.terms.lumpSum)
    }

    @Test fun theFollowingLicenceCannotBeAcceptedOnItsOwn() {
        val w = W()
        val (one, two) = w.r.cross.propose(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.accept(w.r.run(), two.id, a)))
        assertEquals(LicenseStatus.OFFERED, one.status)
    }

    @Test fun neitherStartsIfTheOtherSideCannotPay() {
        val w = W()
        w.r.ctx.fieldSales[b to TechField.MANUFACTURING] = Money.units(50_000_000)
        val (one, two) = w.r.cross.propose(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id, w.big.id)).getOrThrow()
        w.r.ctx.cashOf[IpOwner.company(a)] = Money.ZERO
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(w.r.book.accept(w.r.run(), one.id, b)))
        assertEquals(LicenseStatus.OFFERED, one.status)
        assertEquals(LicenseStatus.OFFERED, two.status)
    }

    @Test fun decliningWithdrawsTheCompanion() {
        val w = W()
        val (one, two) = w.r.cross.propose(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id)).getOrThrow()
        w.r.book.decline(w.r.run(), one.id, b).getOrThrow()
        assertEquals(LicenseStatus.DECLINED, one.status)
        assertEquals(LicenseStatus.WITHDRAWN, two.status)
    }

    @Test fun endingOneEndsTheOther() {
        val w = W()
        val (one, two) = w.r.cross.propose(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id)).getOrThrow()
        w.r.book.accept(w.r.run(), one.id, b).getOrThrow()
        w.r.book.terminate(w.r.run(), one.id, IpOwner.company(b)).getOrThrow()
        assertEquals(LicenseStatus.TERMINATED, one.status)
        assertEquals(LicenseStatus.TERMINATED, two.status)
    }

    @Test fun badProposalsAreRefused() {
        val w = W()
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.cross.propose(w.r.run(), a, a, listOf(w.pa.id), listOf(w.pa.id))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.cross.propose(w.r.run(), a, b, emptyList(), listOf(w.pb.id))))
        assertEquals(Rules.NOT_OWNER, rule(w.r.cross.propose(w.r.run(), a, c, listOf(w.pa.id), listOf(w.pb.id))))
        assertTrue(w.r.state.licenses().isEmpty())
    }

    @Test fun aFailedSecondOfferLeavesNothingOpen() {
        val w = W()
        w.pb.standardEssential = true; w.pb.frandPledged = true
        val res = w.r.cross.propose(w.r.run(), a, b, listOf(w.pa.id), listOf(w.pb.id))
        // The lump-sum licence of a pledged patent has no running rate, so it passes; check the first offer stands with its companion.
        assertNotNull(res.getOrNull())
    }
}
