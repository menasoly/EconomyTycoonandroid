package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseBookTest {
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule

    private class W {
        val r = IpTestSupport.Rig()
        val p = r.granted(a, milling, listOf(us, eu))
        val q = r.granted(a, ovens, listOf(us))
    }

    private fun step(r: IpTestSupport.Rig, t: Long) = r.book.step(r.run(t))

    @Test fun offeringCreatesAnOpenOffer() {
        val w = W()
        val l = w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        assertEquals(LicenseStatus.OFFERED, l.status)
        assertEquals(Tick(30), l.offerExpires)
        assertEquals(listOf(w.p.id), l.patents)
        assertTrue(w.r.events().any { it is IpEvent.LicenseOffered })
        assertNull(l.startsAt)
    }

    @Test fun offersAreValidated() {
        val w = W()
        val r = w.r
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.offer(a, b, emptyList())))
        assertEquals(Rules.DUPLICATE, rule(r.offer(a, b, listOf(w.p.id, w.p.id))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.offer(a, a, listOf(w.p.id))))
        assertEquals(Rules.NOT_OWNER, rule(r.offer(b, c, listOf(w.p.id))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.offer(a, b, listOf(PatentId(99)))))
        r.ctx.inactive.add(c)
        assertEquals(Rules.WRONG_STATE, rule(r.offer(a, c, listOf(w.p.id))))
    }

    @Test fun termsMustMatchTheirBasis() {
        val w = W()
        val r = w.r
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.offer(a, b, listOf(w.p.id), RoyaltyTerms(RoyaltyBasis.PERCENT))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.offer(a, b, listOf(w.p.id), RoyaltyTerms(RoyaltyBasis.PER_UNIT))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.offer(a, b, listOf(w.p.id), RoyaltyTerms(RoyaltyBasis.LUMP_SUM))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.offer(a, b, listOf(w.p.id), RoyaltyTerms(RoyaltyBasis.HYBRID, percent = Rate.percent(1)))))
    }

    @Test fun royaltiesAboveTheLegalCeilingAreRefused() {
        val w = W()
        assertEquals(Rules.REGULATION, rule(w.r.offer(a, b, listOf(w.p.id), w.r.percent(26))))
        assertTrue(w.r.offer(a, b, listOf(w.p.id), w.r.percent(25)).isOk)
    }

    @Test fun termsOfTheLicenceAreBounded() {
        val w = W()
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.offer(a, b, listOf(w.p.id), termDays = 30)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.offer(a, b, listOf(w.p.id), termDays = 21 * 360)))
        assertTrue(w.r.offer(a, b, listOf(w.p.id), termDays = 90).isOk)
    }

    @Test fun exclusivityHasToBePaidFor() {
        val w = W()
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.offer(a, b, listOf(w.p.id), w.r.percent(5), LicenseKind.EXCLUSIVE)))
        val paid = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), minimumPerPeriod = Money.units(1_000))
        assertTrue(w.r.offer(a, b, listOf(w.p.id), paid, LicenseKind.EXCLUSIVE).isOk)
    }

    @Test fun aScopeCountryMustBeFiled() {
        val w = W()
        val scope = LicenseScope.of(emptyList(), listOf(app.many.economytycoon.core.CountryId(9)))
        assertEquals(Rules.WRONG_STATE, rule(w.r.offer(a, b, listOf(w.p.id), scope = scope)))
        assertTrue(w.r.offer(a, b, listOf(w.p.id), scope = LicenseScope.of(emptyList(), listOf(eu))).isOk)
    }

    @Test fun identicalOpenOffersAreRefused() {
        val w = W()
        w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(w.r.offer(a, b, listOf(w.p.id))))
    }

    @Test fun openOffersAreCapped() {
        val r = IpTestSupport.Rig(params = IpParams(licensing = LicenseParams(maxOffersPerLicensor = 1)))
        val p = r.granted(a, milling)
        r.offer(a, b, listOf(p.id)).getOrThrow()
        assertEquals(Rules.LIMIT_EXCEEDED, rule(r.offer(a, c, listOf(p.id))))
    }

    @Test fun acceptingStartsTheLicenceAndChargesTheSigningAmount() {
        val w = W()
        val terms = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), upfront = Money.units(2_000))
        val l = w.r.sign(a, b, listOf(w.p.id), terms)
        assertEquals(LicenseStatus.ACTIVE, l.status)
        assertEquals(Tick(0), l.startsAt)
        assertEquals(Tick(1_800), l.endsAt)
        val ch = w.r.charges().single { it.kind == ChargeKind.LUMP_SUM }
        assertEquals(Money.units(2_000), ch.amount)
        assertEquals(IpLedger.of(b), ch.payer)
        assertEquals(IpLedger.of(a), ch.payee)
        assertEquals(Money.units(2_000), l.paidTotal)
    }

    @Test fun aLumpSumLicencePaysOnceAtSigning() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(40_000)))
        assertEquals(Money.units(40_000), l.paidTotal)
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 90)
        assertTrue(w.r.charges().isEmpty())
        assertEquals(Money.units(40_000), l.paidTotal)
    }

    @Test fun onlyTheLicenseeAcceptsAndOnlyOpenOffers() {
        val w = W()
        val l = w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(w.r.book.accept(w.r.run(), l.id, c)))
        w.r.book.accept(w.r.run(), l.id, b).getOrThrow()
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.accept(w.r.run(), l.id, b)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.r.book.accept(w.r.run(), LicenseId(77), b)))
    }

    @Test fun anExpiredOfferCannotBeAccepted() {
        val w = W()
        val l = w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.accept(w.r.run(31), l.id, b)))
        assertEquals(LicenseStatus.WITHDRAWN, l.status)
    }

    @Test fun offersLapseOnTheirOwn() {
        val w = W()
        val l = w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        step(w.r, 29); assertEquals(LicenseStatus.OFFERED, l.status)
        step(w.r, 30); assertEquals(LicenseStatus.WITHDRAWN, l.status)
    }

    @Test fun aLicenseeWhoCannotPayDoesNotSign() {
        val w = W()
        val terms = RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(40_000))
        val l = w.r.offer(a, b, listOf(w.p.id), terms).getOrThrow()
        w.r.ctx.cashOf[IpOwner.company(b)] = Money.units(10)
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(w.r.book.accept(w.r.run(), l.id, b)))
        assertEquals(LicenseStatus.OFFERED, l.status)
    }

    @Test fun anOfferDiesIfThePatentChangesHands() {
        val w = W()
        val l = w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        w.p.owner = IpOwner.company(c)
        assertEquals(Rules.NOT_OWNER, rule(w.r.book.accept(w.r.run(), l.id, b)))
        assertEquals(LicenseStatus.WITHDRAWN, l.status)
    }

    @Test fun decliningAndWithdrawingEndTheOffer() {
        val w = W()
        val l1 = w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(w.r.book.decline(w.r.run(), l1.id, c)))
        w.r.book.decline(w.r.run(), l1.id, b).getOrThrow()
        assertEquals(LicenseStatus.DECLINED, l1.status)
        val l2 = w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(w.r.book.withdraw(w.r.run(), l2.id, IpOwner.company(b))))
        w.r.book.withdraw(w.r.run(), l2.id, IpOwner.company(a)).getOrThrow()
        assertEquals(LicenseStatus.WITHDRAWN, l2.status)
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.withdraw(w.r.run(), l2.id, IpOwner.company(a))))
    }

    @Test fun anExclusiveLicenceShutsOutEveryoneElse() {
        val w = W()
        val paid = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), minimumPerPeriod = Money.units(1_000))
        w.r.sign(a, b, listOf(w.p.id), paid, LicenseKind.EXCLUSIVE)
        assertEquals(Rules.WRONG_STATE, rule(w.r.offer(a, c, listOf(w.p.id))))
        assertNotNull(w.r.book.exclusiveHolder(w.p.id, us, null, Tick(1)))
        assertFalse(w.r.book.ownerMayPractise(IpOwner.company(a), w.p.id, us, null, Tick(1)))
        assertTrue(w.r.book.ownerMayPractise(IpOwner.company(b), w.p.id, us, null, Tick(1)))
    }

    @Test fun anExclusiveLicenceCanBeLimitedToACountry() {
        val w = W()
        val paid = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), minimumPerPeriod = Money.units(1_000))
        w.r.sign(a, b, listOf(w.p.id), paid, LicenseKind.EXCLUSIVE, LicenseScope.of(emptyList(), listOf(us)))
        assertTrue(w.r.offer(a, c, listOf(w.p.id), scope = LicenseScope.of(emptyList(), listOf(eu))).isOk)
        assertEquals(Rules.WRONG_STATE, rule(w.r.offer(a, CompanyId(8), listOf(w.p.id), scope = LicenseScope.of(emptyList(), listOf(us)))))
        assertNull(w.r.book.exclusiveHolder(w.p.id, eu, null, Tick(1)))
    }

    @Test fun existingLicenseesBlockALaterExclusive() {
        val w = W()
        w.r.sign(a, b, listOf(w.p.id))
        val paid = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), minimumPerPeriod = Money.units(1_000))
        assertEquals(Rules.WRONG_STATE, rule(w.r.offer(a, c, listOf(w.p.id), paid, LicenseKind.EXCLUSIVE)))
    }

    @Test fun aSoleLicenceStopsFurtherLicensingButNotTheOwnersUse() {
        val w = W()
        val paid = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), minimumPerPeriod = Money.units(1_000))
        w.r.sign(a, b, listOf(w.p.id), paid, LicenseKind.SOLE)
        assertEquals(Rules.WRONG_STATE, rule(w.r.offer(a, c, listOf(w.p.id))))
        assertTrue(w.r.book.ownerMayPractise(IpOwner.company(a), w.p.id, us, null, Tick(1)))
    }

    @Test fun nonExclusiveLicencesCoexistButNotForTheSameLicensee() {
        val w = W()
        w.r.sign(a, b, listOf(w.p.id))
        assertTrue(w.r.offer(a, c, listOf(w.p.id)).isOk)
        assertEquals(Rules.WRONG_STATE, rule(w.r.offer(a, b, listOf(w.p.id), scope = LicenseScope.of(emptyList(), listOf(eu)))))
    }

    @Test fun licensingChecksCoverPatentCountryAndIndustry() {
        val w = W()
        w.r.sign(a, b, listOf(w.p.id), scope = LicenseScope.of(listOf(IndustryFamily.FOOD), listOf(us)))
        assertTrue(w.r.book.isLicensed(b, w.p.id, us, IndustryFamily.FOOD, Tick(5)))
        assertFalse(w.r.book.isLicensed(b, w.p.id, eu, IndustryFamily.FOOD, Tick(5)))
        assertFalse(w.r.book.isLicensed(b, w.p.id, us, IndustryFamily.MINING, Tick(5)))
        assertFalse(w.r.book.isLicensed(b, w.q.id, us, IndustryFamily.FOOD, Tick(5)))
        assertFalse(w.r.book.isLicensed(c, w.p.id, us, IndustryFamily.FOOD, Tick(5)))
        assertFalse(w.r.book.isLicensed(b, w.p.id, us, IndustryFamily.FOOD, Tick(1_800)))
    }

    @Test fun royaltiesAreChargedEveryPeriodOnWhatWasSold() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 89)
        assertTrue(w.r.charges().isEmpty())
        step(w.r, 90)
        val ch = w.r.charges().single()
        assertEquals(ChargeKind.ROYALTY, ch.kind)
        assertEquals(Money.units(5_000), ch.amount)
        assertEquals(IpLedger.of(b), ch.payer)
        assertEquals(Tick(90), l.lastSettledAt)
        assertEquals(Money.units(5_000), l.lastDue)
        assertEquals(Money.units(100_000), l.lastSales)
        step(w.r, 135); assertEquals(1, w.r.charges().size)
        step(w.r, 180); assertEquals(2, w.r.charges().size)
    }

    @Test fun theMinimumIsOwedEvenWithoutSales() {
        val w = W()
        val terms = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), minimumPerPeriod = Money.units(3_000))
        val l = w.r.sign(a, b, listOf(w.p.id), terms)
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 90)
        assertEquals(Money.units(3_000), w.r.charges().single().amount)
        assertEquals(Money.units(3_000), l.dueTrue)
    }

    @Test fun twoPatentsInOneProductAreChargedOnce() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id, w.q.id), w.r.percent(5))
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.ctx.usageOf[b to ovens] = IpTestSupport.usage(900, 100)
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 90)
        assertEquals(Money.units(5_000), w.r.charges().single().amount)
        assertEquals(Money.units(100_000), l.lastSales)
    }

    @Test fun anUnpaidRoyaltyBecomesArrearsAndTwoMissesBreachTheLicence() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.ctx.cashOf[IpOwner.company(b)] = Money.ZERO
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 90)
        assertEquals(Money.units(5_000), l.arrears)
        assertEquals(1, l.strikes)
        assertTrue(l.isActive)
        step(w.r, 180)
        assertEquals(LicenseStatus.BREACHED, l.status)
        assertEquals(Money.units(10_000), l.arrears)
    }

    @Test fun payingBackClearsArrearsAndStrikes() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.ctx.cashOf[IpOwner.company(b)] = Money.ZERO
        step(w.r, 90)
        w.r.ctx.cashOf.remove(IpOwner.company(b))
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 180)
        assertEquals(Money.ZERO, l.arrears)
        assertEquals(0, l.strikes)
        assertEquals(Money.units(10_000), w.r.charges().single().amount)
    }

    @Test fun aLicenceRunsOutAtTheEndOfItsTerm() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5), termDays = 100)
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 90); assertTrue(l.isActive)
        step(w.r, 100)
        assertEquals(LicenseStatus.EXPIRED, l.status)
        assertEquals(2, w.r.charges().size)
        assertTrue(w.r.events().any { it is IpEvent.LicenseEnded })
    }

    @Test fun royaltiesStopWhenTheLastPatentDies() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        for (f in w.p.filings) f.status = FilingStatus.EXPIRED
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 10)
        assertEquals(LicenseStatus.EXPIRED, l.status)
    }

    @Test fun aClosedLicenseeEndsTheLicence() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id))
        w.r.ctx.inactive.add(b)
        step(w.r, 1)
        assertEquals(LicenseStatus.TERMINATED, l.status)
    }

    @Test fun theLicenseeCanLeaveForAShareOfTheMinimums() {
        val w = W()
        val terms = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(5), minimumPerPeriod = Money.units(1_000))
        val l = w.r.sign(a, b, listOf(w.p.id), terms, termDays = 900)
        w.r.out = IpOutput(w.r.state.ids)
        val fee = w.r.book.terminate(w.r.run(0), l.id, IpOwner.company(b)).getOrThrow()
        // Ten periods of 1,000 remain; a quarter of that is due.
        assertEquals(Money.units(2_500), fee)
        assertEquals(LicenseStatus.TERMINATED, l.status)
        assertEquals(Money.units(2_500), w.r.charges().single().amount)
    }

    @Test fun theLicensorMayEndOnlyABreachedLicence() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id))
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.terminate(w.r.run(0), l.id, IpOwner.company(a))))
        l.strikes = 1
        assertTrue(w.r.book.terminate(w.r.run(0), l.id, IpOwner.company(a)).isOk)
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.terminate(w.r.run(0), l.id, IpOwner.company(a))))
        val other = w.r.sign(a, b, listOf(w.q.id))
        assertEquals(Rules.NOT_OWNER, rule(w.r.book.terminate(w.r.run(0), other.id, IpOwner.company(c))))
    }

    @Test fun royaltiesToANonPractisingEntityFeedItsChest() {
        val w = W()
        val npe = Npe(w.r.state.ids.npe(), "Holdco", NpeStrategy.DEMAND_LETTERS, 500_000, 300_000)
        w.r.state.npeMap[npe.id] = npe
        w.p.owner = npe.owner
        val l = w.r.book.offer(w.r.run(), OfferRequest(npe.owner, b, listOf(w.p.id), w.r.percent(5))).getOrThrow()
        w.r.book.accept(w.r.run(), l.id, b).getOrThrow()
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.out = IpOutput(w.r.state.ids)
        step(w.r, 90)
        assertEquals(Money.units(5_000), npe.chest)
        assertEquals(Money.units(5_000), npe.earned)
        assertEquals(IpLedger.households(), w.r.charges().single().payee)
    }

    // ---- audits ----

    private fun cheater(): W {
        val w = W()
        w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        w.r.ctx.honesty[b] = Rate.percent(60)
        for (t in listOf(90L, 180L, 270L)) step(w.r, t)
        return w
    }

    @Test fun underReportingShavesTheRoyaltyPaid() {
        val w = cheater()
        val l = w.r.state.licenses().single()
        assertEquals(Money.units(15_000 * 6 / 10), l.dueReported)
        assertEquals(Money.units(15_000), l.dueTrue)
    }

    @Test fun anAuditRecoversTheShortfallPlusAPenaltyAndCosts() {
        var done = false
        for (seed in 1L..12L) {
            val w = cheater()
            val l = w.r.state.licenses().single()
            // Re-roll the audit by moving the clock; the detection chance is high so one of a few days works.
            for (day in 300L..320L) {
                val res = w.r.book.audit(w.r.run(day + 400 * 0), l.id, IpOwner.company(a))
                if (res is Outcome.Ok && res.value.found) {
                    assertEquals(Money.units(6_000), res.value.underpaid)
                    assertEquals(Money.units(1_800), res.value.penalty)
                    assertTrue(res.value.costShifted)
                    assertEquals(l.dueTrue, l.auditedTrue)
                    assertEquals(Money.units(7_800), l.recovered)
                    done = true
                    break
                }
                l.lastAuditAt = Tick(day - 400)
            }
            if (done) break
        }
        assertTrue(done)
    }

    @Test fun anHonestLicenseeOwesNothingAfterAnAudit() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        step(w.r, 90)
        var found: AuditResult? = null
        for (day in 100L..140L) {
            val res = w.r.book.audit(w.r.run(day), l.id, IpOwner.company(a))
            if (res is Outcome.Ok && res.value.found) { found = res.value; break }
            l.lastAuditAt = Tick(day - 400)
        }
        assertEquals(Money.ZERO, found?.underpaid)
        assertEquals(Money.ZERO, found?.penalty)
    }

    @Test fun auditsAreRateLimitedAndOnlyForTheLicensor() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        assertEquals(Rules.NOT_OWNER, rule(w.r.book.audit(w.r.run(5), l.id, IpOwner.company(b))))
        w.r.book.audit(w.r.run(5), l.id, IpOwner.company(a)).getOrThrow()
        assertEquals(Rules.RATE_LIMITED, rule(w.r.book.audit(w.r.run(6), l.id, IpOwner.company(a))))
        assertTrue(w.r.book.audit(w.r.run(400), l.id, IpOwner.company(a)).isOk)
    }

    @Test fun anAuditCostsTheLicensor() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        w.r.out = IpOutput(w.r.state.ids)
        w.r.book.audit(w.r.run(5), l.id, IpOwner.company(a)).getOrThrow()
        val ch = w.r.charges().first { it.kind == ChargeKind.AUDIT_COST }
        assertEquals(Money.units(6_000), ch.amount)
        assertEquals(IpLedger.of(a), ch.payer)
    }

    @Test fun lumpSumLicencesCannotBeAudited() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(1_000)))
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.audit(w.r.run(5), l.id, IpOwner.company(a))))
    }

    // ---- FRAND ----

    private fun essential(w: W) { w.p.standardEssential = true; w.p.frandPledged = true }

    @Test fun aPledgedPatentCannotBeLicensedExclusively() {
        val w = W(); essential(w)
        val paid = RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate.percent(2), minimumPerPeriod = Money.units(1_000))
        assertEquals(Rules.REGULATION, rule(w.r.offer(a, b, listOf(w.p.id), paid, LicenseKind.EXCLUSIVE)))
    }

    @Test fun aPledgedPatentCannotBeLicensedAboveItsFairShare() {
        val w = W(); essential(w)
        assertEquals(Rules.REGULATION, rule(w.r.offer(a, b, listOf(w.p.id), w.r.percent(8))))
        assertTrue(w.r.offer(a, b, listOf(w.p.id), w.r.percent(5)).isOk)
    }

    @Test fun twoHoldersShareTheCeiling() {
        val w = W(); essential(w)
        val other = w.r.granted(c, ovens, listOf(us))
        other.standardEssential = true; other.frandPledged = true
        other.field.let { }
        // Same field is needed for a shared ceiling: ovens is also manufacturing in the fixtures.
        assertEquals(Rules.REGULATION, rule(w.r.offer(a, b, listOf(w.p.id), w.r.percent(3))))
        assertTrue(w.r.offer(a, b, listOf(w.p.id), w.r.percent(2)).isOk)
    }

    @Test fun nonDiscriminationComparesWithOtherLicensees() {
        val w = W(); essential(w)
        w.r.sign(a, b, listOf(w.p.id), w.r.percent(2))
        assertEquals(Rules.REGULATION, rule(w.r.offer(a, c, listOf(w.p.id), w.r.percent(4))))
        assertTrue(w.r.offer(a, c, listOf(w.p.id), RoyaltyTerms(RoyaltyBasis.PERCENT, percent = Rate(2_100))).isOk)
    }

    @Test fun aFrandRequestIsAnsweredAtOrBelowTheFairRate() {
        val w = W(); essential(w)
        val l = w.r.book.requestFrand(w.r.run(), b, w.p.id).getOrThrow()
        assertTrue(l.frand)
        assertEquals(LicenseKind.NON_EXCLUSIVE, l.kind)
        assertTrue(l.terms.percent.ppm <= 50_000L)
        assertTrue(w.r.book.isWillingLicensee(b, w.p.id))
        assertFalse(w.r.book.isWillingLicensee(c, w.p.id))
    }

    @Test fun frandRequestsNeedAPledge() {
        val w = W()
        assertEquals(Rules.WRONG_STATE, rule(w.r.book.requestFrand(w.r.run(), b, w.p.id)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(w.r.book.requestFrand(w.r.run(), b, PatentId(55))))
        essential(w)
        assertEquals(Rules.INVALID_ARGUMENT, rule(w.r.book.requestFrand(w.r.run(), a, w.p.id)))
    }

    // ---- ownership ----

    @Test fun sellingOnePatentOfABundleIsDetected() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id, w.q.id))
        assertEquals(l, w.r.book.splitsABundle(IpOwner.company(a), setOf(w.p.id)))
        assertNull(w.r.book.splitsABundle(IpOwner.company(a), setOf(w.p.id, w.q.id)))
    }

    @Test fun licencesFollowThePatentToItsNewOwner() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id))
        val open = w.r.offer(a, c, listOf(w.p.id)).getOrThrow()
        w.r.book.transferLicensor(w.r.run(), IpOwner.company(a), IpOwner.company(c), setOf(w.p.id))
        assertEquals(IpOwner.company(c), l.licensor)
        assertEquals(LicenseStatus.WITHDRAWN, open.status)
    }

    @Test fun closingACompanyEndsItsLicencesAndOffers() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id))
        val open = w.r.offer(a, c, listOf(w.p.id)).getOrThrow()
        w.r.book.companyClosed(w.r.run(), b)
        assertEquals(LicenseStatus.TERMINATED, l.status)
        w.r.book.companyClosed(w.r.run(), a)
        assertEquals(LicenseStatus.WITHDRAWN, open.status)
    }

    @Test fun theRateOfALicenceComesFromItsLastPeriod() {
        val w = W()
        val l = w.r.sign(a, b, listOf(w.p.id), w.r.percent(5))
        assertEquals(Rate.percent(5), w.r.book.rateOf(l))
        w.r.ctx.usageOf[b to milling] = IpTestSupport.usage(1_000, 100)
        step(w.r, 90)
        assertEquals(Rate.percent(5), w.r.book.rateOf(l))
    }

    @Test fun licencesOfOneCompanyAreListedFromBothSides() {
        val w = W()
        w.r.sign(a, b, listOf(w.p.id))
        assertEquals(1, w.r.state.licensesIn(b).size)
        assertEquals(1, w.r.state.licensesOut(IpOwner.company(a)).size)
        assertEquals(1, w.r.state.licensesOf(a).size)
        assertEquals(0, w.r.state.licensesOf(c).size)
    }

    @Test fun theStateHashChangesWithLicenceActivity() {
        val w = W()
        val h0 = w.r.state.stateHash()
        w.r.offer(a, b, listOf(w.p.id)).getOrThrow()
        assertTrue(h0 != w.r.state.stateHash())
    }
}
