package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.efficiency
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolServiceTest {
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule
    private val terms = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(3))

    private class W {
        val r = IpTestSupport.Rig()
        val pa = r.granted(a, milling, breadthPpm = 700_000L)
        val pb = r.granted(b, ovens, breadthPpm = 600_000L)
        val weak = r.granted(c, efficiency, breadthPpm = 300_000L)
    }

    private fun pool(w: W): PatentPool = w.r.pools.form(w.r.run(), a, "Mill Standard", TechField.MANUFACTURING, terms, 100_000L).getOrThrow()

    @Test fun formingMakesTheAdministratorAMember() {
        val w = W()
        val p = pool(w)
        assertTrue(p.open)
        assertEquals(listOf(a), p.members.map { it.company })
        assertEquals(listOf(p), w.r.state.pools())
        assertTrue(w.r.events().any { it is IpEvent.PoolFormed })
    }

    @Test fun poolsAreValidated() {
        val w = W()
        val run = w.r.run()
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.pools.form(run, a, " ", TechField.MANUFACTURING, terms, 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.pools.form(run, a, "X", TechField.MANUFACTURING, terms, 300_000L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.pools.form(run, a, "X", TechField.MANUFACTURING, RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(5)), 0)))
        assertEquals(Rules.REGULATION, rule(w.r.pools.form(run, a, "X", TechField.MANUFACTURING, RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(9)), 0)))
        pool(w)
        assertEquals(Rules.NAME_TAKEN, rule(w.r.pools.form(run, b, "mill standard", TechField.MANUFACTURING, terms, 0)))
        w.r.ctx.inactive.add(c)
        assertEquals(Rules.WRONG_STATE, rule(w.r.pools.form(run, c, "Y", TechField.MANUFACTURING, terms, 0)))
    }

    @Test fun joiningWithAnEssentialPatentMarksItStandardEssential() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        assertTrue(w.pb.standardEssential && w.pb.frandPledged)
        assertEquals(listOf(w.pb.id), p.member(b)!!.patents)
        assertEquals(p, w.r.pools.poolOf(w.pb.id))
        assertEquals(listOf(p), w.r.pools.poolsOf(b))
    }

    @Test fun onlyEssentialPatentsAreAccepted() {
        val w = W()
        val p = pool(w)
        assertEquals(Rules.REGULATION, rule(w.r.pools.join(w.r.run(), p.id, c, listOf(w.weak.id))))
        val farOff = w.r.granted(c, app.many.economytycoon.core.TechId("megamill"), breadthPpm = 900_000L)
        farOff.let { }
        val mismatched = PatentPool(PoolId(99), "Finance", a, TechField.FINANCIAL_METHODS, Tick(0), terms, 0)
        assertFalse(w.r.pools.isEssential(w.pa, mismatched))
        assertTrue(w.r.pools.isEssential(w.pa, p))
    }

    @Test fun joiningRulesAreChecked() {
        val w = W()
        val p = pool(w)
        assertEquals(Rules.NOT_OWNER, rule(w.r.pools.join(w.r.run(), p.id, c, listOf(w.pb.id))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.r.pools.join(w.r.run(), p.id, b, listOf(PatentId(99)))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.pools.join(w.r.run(), p.id, b, emptyList())))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.r.pools.join(w.r.run(), PoolId(50), b, listOf(w.pb.id))))
        w.r.pools.join(w.r.run(), p.id, a, listOf(w.pa.id)).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(w.r.pools.join(w.r.run(), p.id, a, listOf(w.pa.id))))
    }

    @Test fun anExclusivelyLicensedPatentCannotJoin() {
        val w = W()
        val p = pool(w)
        val paid = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(2), minimumPerPeriod = Money.units(100))
        w.r.sign(b, c, listOf(w.pb.id), paid, LicenseKind.EXCLUSIVE)
        assertEquals(Rules.WRONG_STATE, rule(w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id))))
    }

    @Test fun membersCanLeaveButTheAdministratorCannot() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, rule(w.r.pools.leave(w.r.run(), p.id, a)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.r.pools.leave(w.r.run(), p.id, c)))
        w.r.pools.leave(w.r.run(), p.id, b).getOrThrow()
        assertNull(p.member(b))
        // The pledge stays with the patent.
        assertTrue(w.pb.frandPledged)
        assertNull(w.r.pools.poolOf(w.pb.id))
    }

    @Test fun membersMayUsePoolPatentsFreely() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, a, listOf(w.pa.id)).getOrThrow()
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        assertTrue(w.r.pools.coveredByPool(b, w.pa.id))
        assertTrue(w.r.pools.coveredByPool(a, w.pb.id))
        assertFalse(w.r.pools.coveredByPool(c, w.pa.id))
    }

    @Test fun theBundleCoversEveryPoolPatentAtPoolTerms() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, a, listOf(w.pa.id)).getOrThrow()
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        val l = w.r.pools.offerBundle(w.r.run(), p.id, c).getOrThrow()
        assertEquals(listOf(w.pa.id, w.pb.id), l.patents)
        assertEquals(terms, l.terms)
        assertEquals(p.id, l.poolId)
        assertTrue(l.frand)
        assertEquals(LicenseKind.NON_EXCLUSIVE, l.kind)
        assertEquals(IpOwner.company(a), l.licensor)
    }

    @Test fun anEmptyPoolOrAMemberGetsNoBundle() {
        val w = W()
        val p = pool(w)
        assertEquals(Rules.WRONG_STATE, rule(w.r.pools.offerBundle(w.r.run(), p.id, c)))
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(w.r.pools.offerBundle(w.r.run(), p.id, b)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.r.pools.offerBundle(w.r.run(), PoolId(70), c)))
    }

    @Test fun onlyTheAdministratorOffersPoolLicences() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        val bad = w.r.book.offer(w.r.run(), OfferRequest(IpOwner.company(b), c, listOf(w.pb.id), terms, poolId = p.id))
        assertEquals(Rules.NOT_OWNER, rule(bad))
    }

    @Test fun poolRoyaltiesAreSharedByWeightAfterTheAdministratorsCut() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, a, listOf(w.pa.id)).getOrThrow()
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        val offer = w.r.pools.offerBundle(w.r.run(), p.id, c).getOrThrow()
        w.r.book.accept(w.r.run(), offer.id, c).getOrThrow()
        w.r.ctx.usageOf[c to milling] = IpTestSupport.usage(1_000, 100)
        w.r.out = IpOutput(w.r.state.ids)
        w.r.book.step(w.r.run(90))
        // 3% of 100,000 is 3,000; the administrator keeps 10%.
        val royalty = w.r.charges().single { it.kind == ChargeKind.ROYALTY }
        assertEquals(Money.units(3_000), royalty.amount)
        assertEquals(IpLedger.of(a), royalty.payee)
        assertEquals(Money.units(300), p.adminEarned)
        val toB = w.r.charges().single { it.kind == ChargeKind.POOL_DISTRIBUTION }
        assertEquals(IpLedger.of(b), toB.payee)
        assertEquals(Money.units(3_000), p.collected)
        val shares = p.members.sumOf { it.earned.raw } + p.adminEarned.raw
        assertEquals(p.collected.raw, shares)
    }

    @Test fun weightsFavourBroaderPatents() {
        val w = W()
        assertTrue(PoolPayout.weight(w.pa) > PoolPayout.weight(w.pb))
    }

    @Test fun aPoolWithNoWeightKeepsEverythingForTheAdministrator() {
        val w = W()
        val p = pool(w)
        w.r.out = IpOutput(w.r.state.ids)
        PoolPayout.distribute(w.r.state, w.r.run(), p, Money.units(1_000), "x")
        assertEquals(Money.units(1_000), p.adminEarned)
        assertTrue(w.r.charges().isEmpty())
    }

    @Test fun dissolvingEndsPoolLicencesAndOffers() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        val active = w.r.book.accept(w.r.run(), w.r.pools.offerBundle(w.r.run(), p.id, c).getOrThrow().id, c).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(w.r.pools.dissolve(w.r.run(), p.id, b)))
        w.r.pools.dissolve(w.r.run(5), p.id, a).getOrThrow()
        assertFalse(p.open)
        assertEquals(LicenseStatus.TERMINATED, active.status)
        assertEquals(Rules.WRONG_STATE, rule(w.r.pools.dissolve(w.r.run(), p.id, a)))
        assertEquals(Rules.WRONG_STATE, rule(w.r.pools.join(w.r.run(), p.id, c, listOf(w.weak.id))))
        assertNull(w.r.pools.poolOf(w.pb.id))
    }

    @Test fun closingTheAdministratorDissolvesAndClosingAMemberLeaves() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        w.r.pools.companyClosed(w.r.run(), b)
        assertNull(p.member(b))
        w.r.pools.companyClosed(w.r.run(), a)
        assertFalse(p.open)
    }

    @Test fun shareOfFollowsContributedWeight() {
        val w = W()
        val p = pool(w)
        w.r.pools.join(w.r.run(), p.id, a, listOf(w.pa.id)).getOrThrow()
        w.r.pools.join(w.r.run(), p.id, b, listOf(w.pb.id)).getOrThrow()
        val sa = w.r.pools.shareOf(p, a, Tick(0))
        val sb = w.r.pools.shareOf(p, b, Tick(0))
        assertTrue(sa > sb)
        assertTrue(Math.abs(sa + sb - 1_000_000L) <= 2)
        assertEquals(0L, w.r.pools.shareOf(p, c, Tick(0)))
    }
}
