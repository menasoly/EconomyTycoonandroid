package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IpTaxTest {
    @Test fun creditIsAShareOfQualifyingSpend() {
        val reg = RegimeKind.BALANCED.regime
        assertEquals(Money.units(8_000), IpTaxRules.researchCredit(reg, Money.units(100_000)))
        assertEquals(Money.ZERO, IpTaxRules.researchCredit(reg, Money(-5)))
    }

    @Test fun patentBoxOnlyHelpsWhereItExists() {
        val income = Money.units(1_000_000)
        assertEquals(Money.units(100_000), IpTaxRules.patentBoxRelief(RegimeKind.PRO_PATENT.regime, Rate.percent(20), income))
        assertEquals(Money.units(50_000), IpTaxRules.patentBoxRelief(RegimeKind.BALANCED.regime, Rate.percent(20), income))
        assertEquals(Money.ZERO, IpTaxRules.patentBoxRelief(RegimeKind.DEVELOPING.regime, Rate.percent(20), income))
        assertEquals(Money.ZERO, IpTaxRules.patentBoxRelief(RegimeKind.PRO_PATENT.regime, Rate.percent(8), income))
        assertEquals(Money.ZERO, IpTaxRules.patentBoxRelief(RegimeKind.PRO_PATENT.regime, Rate.percent(20), Money.ZERO))
    }

    private fun world(): Rig {
        val r = Rig()
        r.granted(a, milling)
        r.ctx.homes[a] = us
        r.ctx.rdSpend[a] = Money.units(500_000)
        return r
    }

    @Test fun creditPaidAtYearStartFromTheGovernment() {
        val r = world()
        r.tax.step(r.run(360))
        val ch = r.charges().single()
        assertEquals(ChargeKind.TAX_CREDIT, ch.kind)
        assertEquals(IpLedger.government(us), ch.payer)
        assertEquals(IpLedger.of(a), ch.payee)
        assertEquals(Money.units(40_000), ch.amount)
        assertTrue(r.events().any { it is IpEvent.TaxCreditPaid })
    }

    @Test fun creditIsPaidOncePerYear() {
        val r = world()
        r.tax.step(r.run(360))
        r.tax.step(r.run(360))
        assertEquals(1, r.charges().size)
        r.tax.step(r.run(720))
        assertEquals(2, r.charges().size)
        assertEquals(1L, r.state.creditYearMap[a])
    }

    @Test fun nothingOutsideYearStartsOrAtTickZero() {
        val r = world()
        r.tax.step(r.run(0))
        r.tax.step(r.run(359))
        r.tax.step(r.run(361))
        assertTrue(r.charges().isEmpty())
    }

    @Test fun noSpendNoCreditButYearIsRecorded() {
        val r = world()
        r.ctx.rdSpend.clear()
        r.tax.step(r.run(360))
        assertTrue(r.charges().isEmpty())
        assertEquals(0L, r.state.creditYearMap[a])
    }

    @Test fun companiesWithoutAHomeOrInactiveGetNothing() {
        val r = world()
        r.ctx.homes.clear()
        r.tax.step(r.run(360))
        assertTrue(r.charges().isEmpty())
        r.ctx.homes[a] = us
        r.ctx.inactive += a
        r.tax.step(r.run(360))
        assertTrue(r.charges().isEmpty())
    }

    @Test fun creditFollowsTheHomeCountryRegime() {
        val r = world()
        r.state.regimes.assign(us, RegimeKind.PRO_PATENT)
        r.tax.step(r.run(360))
        assertEquals(Money.units(50_000), r.charges().single().amount)
        val r2 = world()
        r2.ctx.homes[a] = eu
        r2.state.regimes.assign(eu, RegimeKind.DEVELOPING)
        r2.tax.step(r2.run(360))
        assertTrue(r2.charges().isEmpty())
    }

    @Test fun knownCompaniesCoverHoldersLicenseesAndPolicies() {
        val r = Rig(); val p = r.granted(a, milling)
        r.state.policyMap[b] = IpPolicy()
        assertEquals(listOf(a, b), r.tax.knownCompanies())
        r.sign(a, IpTestSupport.c, listOf(p.id))
        assertEquals(listOf(a, b, IpTestSupport.c), r.tax.knownCompanies())
    }

    @Test fun patentBoxSavingUsesTheHomeRegime() {
        val r = world()
        r.state.regimes.assign(us, RegimeKind.PRO_PATENT)
        assertEquals(Money.units(10_000), r.tax.patentBoxSaving(r.run(10), a, Money.units(100_000)))
        r.ctx.homes.clear()
        assertEquals(Money.ZERO, r.tax.patentBoxSaving(r.run(10), a, Money.units(100_000)))
        assertEquals(Tick(10), r.run(10).tick)
    }
}
