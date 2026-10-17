package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonLevyTest {
    private val fx = CarbonTestSupport.Fixture()

    private fun taxOf(company: CompanyId) = fx.payments.filter { it.reason == ReasonCode.TAX && it.payer.id == company.value }.fold(Money.ZERO) { a, p -> a + p.total }

    private fun tax(threshold: Long = 100) {
        fx.service.installRegime(RegimePresets.carbonTax(uk, 2000, Money.units(10), threshold = Tonnes.of(threshold)), Tick(0)).value()
    }

    @Test fun aCarbonTaxIsAssessedEveryMonthOnYearToDateEmissions() {
        tax()
        fx.run(0, 31, listOf(emitter(1, acme)))
        val charges = fx.eventsOf<CarbonEvent.TaxCharged>()
        assertEquals(1, charges.size)
        assertEquals(Tonnes.of(600), charges.single().tonnes)
        assertEquals(Money.units(6_000), charges.single().amount)
    }

    @Test fun overAYearTheTaxIsTheRateTimesTheCoveredEmissions() {
        tax()
        fx.run(0, 360, listOf(emitter(1, acme)))
        assertEquals(Money.units(72_000), taxOf(acme))
        assertEquals(12, fx.eventsOf<CarbonEvent.TaxCharged>().size)
    }

    @Test fun theTaxIsAStatutoryDeferrableCharge() {
        tax()
        fx.run(0, 31, listOf(emitter(1, acme)))
        val p = fx.payments.single { it.reason == ReasonCode.TAX }
        assertTrue(p.deferrable)
        assertEquals(app.many.economytycoon.finance.SettlementPriority.STATUTORY, p.priority)
    }

    @Test fun theYearEndRecordShowsTheTaxPaid() {
        tax()
        fx.run(0, 361, listOf(emitter(1, acme)))
        val rec = fx.state.compliance.account(acme, uk)!!.recordFor(2000)!!
        assertEquals(ComplianceStatus.TAXED, rec.status)
        assertEquals(Money.units(72_000), rec.charge)
        assertNull(fx.state.compliance.account(acme, uk)!!.pending)
        assertEquals(Money.units(72_000), fx.state.receipts(uk))
    }

    @Test fun aCompanyOverTheThresholdLateInTheYearCatchesUp() {
        tax(threshold = 100)
        fx.run(0, 360, listOf(emitter(1, cyrus, coalUnits = 3)))
        assertEquals(Tonnes.ofKg(216_000).cost(Money.units(10)), taxOf(cyrus))
        val charges = fx.eventsOf<CarbonEvent.TaxCharged>()
        assertTrue(charges.size < 12)
        assertTrue(charges.first().amount > charges.last().amount)
    }

    @Test fun aCompanyUnderTheThresholdPaysNothingAndIsExempt() {
        tax(threshold = 500)
        fx.run(0, 361, listOf(emitter(1, cyrus, coalUnits = 3)))
        assertEquals(Money.ZERO, taxOf(cyrus))
        assertEquals(ComplianceStatus.EXEMPT, fx.state.compliance.account(cyrus, uk)!!.recordFor(2000)!!.status)
    }

    @Test fun theRateEscalatesInLaterYears() {
        tax()
        fx.run(0, 360, listOf(emitter(1, acme)))
        val firstYear = taxOf(acme)
        fx.run(361, 720, listOf(emitter(1, acme)))
        val total = taxOf(acme)
        assertTrue(total - firstYear > firstYear)
    }

    @Test fun onlyCoveredIndustriesPayTheTax() {
        tax()
        fx.run(0, 100, listOf(emitter(1, acme), emitter(2, borea, family = app.many.economytycoon.content.IndustryFamily.FINANCIAL_SERVICES)))
        assertTrue(taxOf(acme).isPositive)
        assertEquals(Money.ZERO, taxOf(borea))
    }

    @Test fun anInactiveCompanyIsNotBilled() {
        tax()
        fx.inactive += acme
        fx.run(0, 100, listOf(emitter(1, acme)))
        assertEquals(Money.ZERO, taxOf(acme))
    }

    @Test fun taxReceiptsFeedTheGreenFund() {
        tax()
        fx.run(0, 31, listOf(emitter(1, acme)))
        assertEquals(Money.units(6_000), fx.state.receipts(uk))
        assertEquals(Money.units(1_800), fx.state.greenFund(uk))
    }

    // ---- intensity standard ----

    private fun standard(benchmark: Long = 4_000L) {
        fx.service.installRegime(RegimePresets.intensityStandard(uk, 2000, benchmark, levy = Money.units(40), cutPpm = 0L, threshold = Tonnes.of(100)), Tick(0)).value()
    }

    @Test fun anIntensityLevyFallsOnTheExcessOverTheBenchmarkAtYearEnd() {
        standard()
        fx.run(0, 360, listOf(emitter(1, acme)))
        val rec = fx.state.compliance.account(acme, uk)!!.recordFor(2000)!!
        assertEquals(ComplianceStatus.LEVIED, rec.status)
        assertEquals(Money.units(57_600), rec.charge)
        assertEquals(Money.units(57_600), taxOf(acme))
    }

    @Test fun aCleanCompanyUnderTheBenchmarkPaysNoLevy() {
        standard()
        fx.run(0, 360, listOf(emitter(1, borea, coalUnits = 50)))
        val rec = fx.state.compliance.account(borea, uk)!!.recordFor(2000)!!
        assertEquals(ComplianceStatus.COMPLIANT, rec.status)
        assertEquals(Money.ZERO, rec.charge)
    }

    @Test fun theBenchmarkIsMeasuredOnTheCoveredOutputOnly() {
        standard()
        fx.run(0, 360, listOf(emitter(1, acme)))
        assertEquals(Money.units(1_440_000), fx.state.inventory.company(acme, uk, 2000)!!.coveredOutput)
    }

    @Test fun nothingPendingIsLeftBehindByATaxOrLevyRegime() {
        standard()
        fx.run(0, 400, listOf(emitter(1, acme)))
        assertNull(fx.state.compliance.account(acme, uk)!!.pending)
    }
}
