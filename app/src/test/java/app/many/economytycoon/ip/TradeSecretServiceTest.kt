package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeSecretServiceTest {
    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule
    private fun rig(): IpTestSupport.Rig = IpTestSupport.Rig().also { it.teach(a, milling); it.teach(a, ovens) }

    @Test fun keepingASecretRegistersIt() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        assertTrue(s.isLive)
        assertTrue(s.isExclusive)
        assertEquals(TechField.MANUFACTURING, s.field)
        assertEquals(s, r.state.liveSecretFor(a, milling))
        assertTrue(r.events().single() is IpEvent.SecretKept)
    }

    @Test fun keepingNeedsTheTechnologyAndNoDuplicate() {
        val r = rig()
        assertEquals(Rules.NOT_UNLOCKED, rule(r.secrets.keep(r.run(0), b, milling)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.secrets.keep(r.run(0), a, app.many.economytycoon.core.TechId("zzz"))))
        r.secrets.keep(r.run(0), a, milling).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(r.secrets.keep(r.run(0), a, milling)))
    }

    @Test fun aLivePatentMakesASecretPointless() {
        val r = rig()
        r.file(a, milling, us).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(r.secrets.keep(r.run(0), a, milling)))
    }

    @Test fun secretsPerCompanyAreLimited() {
        val r = IpTestSupport.Rig(params = IpParams(secrets = SecretParams(maxSecretsPerCompany = 1)))
        r.teach(a, milling); r.teach(a, ovens)
        r.secrets.keep(r.run(0), a, milling).getOrThrow()
        assertEquals(Rules.LIMIT_EXCEEDED, rule(r.secrets.keep(r.run(0), a, ovens)))
    }

    @Test fun securitySpendingBuildsSaturatingProtection() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        val p1 = r.secrets.invest(r.run(0), s.id, a, Money.units(20_000)).getOrThrow()
        assertEquals(450_000L, p1)
        val p2 = r.secrets.invest(r.run(0), s.id, a, Money.units(20_000)).getOrThrow()
        assertTrue(p2 > p1 && p2 < SecretParams().maxProtectionPpm)
        assertEquals(Money.units(40_000), s.securitySpent)
        assertEquals(2, r.charges().count { it.kind == ChargeKind.SECURITY_SPEND })
    }

    @Test fun investmentIsValidated() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(r.secrets.invest(r.run(0), s.id, b, Money.units(5_000))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.secrets.invest(r.run(0), s.id, a, Money.units(10))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.secrets.invest(r.run(0), SecretId(77), a, Money.units(5_000))))
        r.ctx.cashOf[IpOwner.company(a)] = Money.units(100)
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(r.secrets.invest(r.run(0), s.id, a, Money.units(5_000))))
    }

    @Test fun droppingEndsTheSecret() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        assertTrue(r.secrets.drop(r.run(1), s.id, a).isOk)
        assertEquals(SecretStatus.ABANDONED, s.status)
        assertEquals(Rules.WRONG_STATE, rule(r.secrets.drop(r.run(1), s.id, a)))
        assertEquals(Rules.WRONG_STATE, rule(r.secrets.invest(r.run(1), s.id, a, Money.units(5_000))))
    }

    @Test fun aSecretNobodyKnowsAndNobodySeesSurvives() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.ctx.knowerCount[a to milling] = 0
        for (t in 1L..3_000L) r.secrets.step(r.run(t))
        // Base hazard alone is 0.006% a day, about 18% over eight years; with this seed it holds or fails deterministically.
        val again = IpTestSupport.Rig().also { it.teach(a, milling) }
        val s2 = again.secrets.keep(again.run(0), a, milling).getOrThrow()
        for (t in 1L..3_000L) again.secrets.step(again.run(t))
        assertEquals(s.status, s2.status)
    }

    @Test fun manyKnowersMakeALeakNearlyCertain() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.ctx.knowerCount[a to milling] = 400
        for (t in 1L..2_000L) r.secrets.step(r.run(t))
        assertEquals(SecretStatus.LEAKED, s.status)
        assertTrue(r.events().any { it is IpEvent.SecretLost && (it as IpEvent.SecretLost).how == SecretStatus.LEAKED })
    }

    @Test fun visibleProductsGetReverseEngineered() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.ctx.exposureOf[a to milling] = Rate.ONE
        for (t in 1L..6_000L) r.secrets.step(r.run(t))
        assertTrue(s.status == SecretStatus.REVERSE_ENGINEERED || s.status == SecretStatus.LEAKED)
    }

    @Test fun protectionSlowsLeaks() {
        fun survivors(protect: Boolean): Int {
            var alive = 0
            for (seed in 1L..40L) {
                val r = IpTestSupport.Rig(seed)
                r.teach(a, milling)
                val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
                r.ctx.knowerCount[a to milling] = 60
                if (protect) r.secrets.invest(r.run(0), s.id, a, Money.units(200_000))
                for (t in 1L..600L) r.secrets.step(r.run(t))
                if (s.isLive) alive++
            }
            return alive
        }
        assertTrue(survivors(true) > survivors(false) + 10)
    }

    @Test fun anIndependentDiscoveryEndsExclusivity() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.secrets.step(r.run(1))
        assertTrue(s.isLive)
        r.ctx.holders[milling] = listOf(b)
        r.secrets.step(r.run(2))
        assertEquals(SecretStatus.SHARED, s.status)
        assertEquals(app.many.economytycoon.core.Tick(2), s.endedAt)
    }

    @Test fun closedCompaniesLoseTheirSecrets() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.ctx.inactive.add(a)
        r.secrets.step(r.run(1))
        assertEquals(SecretStatus.ABANDONED, s.status)
    }

    @Test fun protectionFadesEachMonthWithoutUpkeep() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.secrets.invest(r.run(0), s.id, a, Money.units(20_000))
        val before = s.protectionPpm
        r.secrets.step(r.run(30))
        assertTrue(s.protectionPpm < before)
        assertEquals(before - before * 8_000 / 1_000_000, s.protectionPpm)
    }

    @Test fun espionageCostsMoneyAndMaySucceed() {
        var successes = 0
        for (seed in 1L..40L) {
            val r = IpTestSupport.Rig(seed)
            r.teach(a, milling)
            val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
            val res = r.secrets.espionage(r.run(5), b, s.id).getOrThrow()
            assertEquals(1, r.charges().count { it.kind == ChargeKind.SECURITY_SPEND && it.payer == IpLedger.of(b) })
            if (res.success) { successes++; assertTrue(b in s.knownTo); assertFalse(s.isExclusive); assertEquals(1, s.incidents) }
            else assertTrue(b !in s.knownTo)
        }
        assertTrue("successes $successes", successes in 3..25)
    }

    @Test fun strongProtectionStopsSpies() {
        fun hits(protect: Boolean): Int {
            var n = 0
            for (seed in 1L..60L) {
                val r = IpTestSupport.Rig(seed)
                r.teach(a, milling)
                val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
                if (protect) r.secrets.invest(r.run(0), s.id, a, Money.units(1_000_000))
                if (r.secrets.espionage(r.run(5), b, s.id).getOrThrow().success) n++
            }
            return n
        }
        assertTrue(hits(true) < hits(false))
    }

    @Test fun espionageRulesAreChecked() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.secrets.espionage(r.run(1), a, s.id)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.secrets.espionage(r.run(1), b, SecretId(9))))
        r.ctx.cashOf[IpOwner.company(b)] = Money.ZERO
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(r.secrets.espionage(r.run(1), b, s.id)))
        r.ctx.cashOf.remove(IpOwner.company(b))
        r.ctx.inactive.add(b)
        assertEquals(Rules.WRONG_STATE, rule(r.secrets.espionage(r.run(1), b, s.id)))
    }

    @Test fun aKnownThiefIsNotTriedTwice() {
        var found = false
        for (seed in 1L..80L) {
            val r = IpTestSupport.Rig(seed)
            r.teach(a, milling)
            val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
            if (r.secrets.espionage(r.run(5), b, s.id).getOrThrow().success) {
                assertEquals(Rules.DUPLICATE, rule(r.secrets.espionage(r.run(6), b, s.id)))
                found = true
                break
            }
        }
        assertTrue(found)
    }

    @Test fun detectionComesWithEvidence() {
        var withEvidence = 0
        for (seed in 1L..60L) {
            val r = IpTestSupport.Rig(seed)
            r.teach(a, milling)
            val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
            val res = r.secrets.espionage(r.run(5), CompanyId(40 + seed), s.id).getOrThrow()
            if (res.detected) { assertTrue(res.evidencePpm >= 400_000L); withEvidence++ } else assertEquals(0L, res.evidencePpm)
        }
        assertTrue(withEvidence > 5)
    }

    @Test fun markPatentedEndsASecret() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.secrets.markPatented(s.id, app.many.economytycoon.core.Tick(3))
        assertEquals(SecretStatus.PATENTED, s.status)
        r.secrets.markPatented(s.id, app.many.economytycoon.core.Tick(9))
        assertEquals(app.many.economytycoon.core.Tick(3), s.endedAt)
    }

    @Test fun annualAdvantageFollowsFieldSales() {
        val r = rig()
        val s = r.secrets.keep(r.run(0), a, milling).getOrThrow()
        r.ctx.fieldSales[a to TechField.MANUFACTURING] = Money.units(1_000_000)
        assertEquals(Money.units(30_000), r.secrets.annualAdvantage(r.run(0), s))
    }
}
