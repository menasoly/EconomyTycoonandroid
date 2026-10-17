package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.capAndTrade
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.emitter
import app.many.economytycoon.carbon.CarbonTestSupport.rule
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.carbon.CarbonTestSupport.value
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonComplianceTest {
    private val fx = CarbonTestSupport.Fixture()
    private val plants = listOf(emitter(1, acme), emitter(2, borea))
    private val market get() = fx.state.market(uk)!!
    private fun account(c: app.many.economytycoon.core.CompanyId = acme) = fx.state.compliance.account(c, uk)!!

    /** No free permits and no auctions: companies hold only what the test gives them. */
    private fun bare() = capAndTrade(free = 0L).let { it.copy(steps = listOf(it.steps.single().copy(cap = Tonnes.of(1)))) }

    private fun install(regime: CarbonRegime = capAndTrade()) { fx.service.installRegime(regime, Tick(0)).value() }

    private fun give(company: app.many.economytycoon.core.CompanyId, tonnes: Long, vintage: Int = 2000, cost: Long = 0) =
        market.holdings.add(company, vintage, Tonnes.of(tonnes), Money.units(cost))

    @Test fun theYearClosesIntoAPendingObligationWithADeadline() {
        install(bare())
        give(acme, 10_000)
        fx.run(0, 360, plants)
        val p = account().pending!!
        assertEquals(2000, p.year)
        assertEquals(Tonnes.of(7_200), p.verified)
        assertEquals(Tonnes.of(7_200), p.obligation)
        assertEquals(Tick(450), p.deadline)
    }

    @Test fun permitsOnHandAreHandedInAtTheDeadlineAndTheCompanyIsCompliant() {
        install(bare())
        give(acme, 10_000, cost = 200_000)
        fx.run(0, 450, plants)
        val rec = account().recordFor(2000)!!
        assertEquals(ComplianceStatus.COMPLIANT, rec.status)
        assertEquals(Tonnes.of(7_200), rec.surrendered)
        assertEquals(Tonnes.ZERO, rec.shortfall)
        assertEquals(Money.ZERO, rec.penalty)
        assertNull(account().pending)
        assertEquals(Tonnes.of(2_800), market.holdings.total(acme))
        assertEquals(0, account().violations)
    }

    @Test fun surrenderedPermitsAreWrittenOffAtCost() {
        install(bare())
        give(acme, 10_000, cost = 100_000)
        fx.run(0, 450, plants)
        val written = fx.adjustments.filter { it.memo.startsWith("permits surrendered") }
        assertEquals(1, written.size)
        assertEquals(Money.units(72_000), written.single().entries.first { it.amount.isPositive }.amount)
    }

    @Test fun aShortfallIsFinedAndCarriedIntoNextYear() {
        install(bare())
        give(acme, 5_200)
        fx.run(0, 450, plants)
        val rec = account().recordFor(2000)!!
        assertEquals(ComplianceStatus.SHORTFALL, rec.status)
        assertEquals(Tonnes.of(2_000), rec.shortfall)
        assertEquals(Money.units(300_000), rec.penalty)
        assertEquals(1, account().violations)
        assertEquals(Tonnes.of(2_000), account().carry)
        val fine = fx.payments.single { it.reason == ReasonCode.FEE && it.payer.id == acme.value }
        assertEquals(Money.units(300_000), fine.total)
        assertTrue(fine.deferrable)
    }

    @Test fun theCarriedShortfallIsAddedToNextYearsObligation() {
        install(bare())
        give(acme, 5_200)
        fx.run(0, 725, plants)
        assertEquals(Tonnes.of(9_200), account().pending!!.obligation)
    }

    @Test fun aRepeatedShortfallIsFinedMoreHeavily() {
        install(bare())
        give(acme, 5_200)
        fx.run(0, 450, plants)
        val first = account().recordFor(2000)!!.penalty
        fx.run(451, 810, plants)
        val second = account().recordFor(2001)!!
        assertTrue(second.penalty.raw * 1000 / second.shortfall.raw * 1000 > first.raw * 1000 / account().recordFor(2000)!!.shortfall.raw * 1000)
        assertEquals(2, account().violations)
    }

    @Test fun whenTheRegimeDoesNotMakeGoodTheShortfallIsForgotten() {
        install(bare().copy(makeGood = false))
        give(acme, 5_200)
        fx.run(0, 450, plants)
        assertEquals(Tonnes.ZERO, account().carry)
    }

    @Test fun surrenderingEarlyByCommandReducesWhatIsOwed() {
        install(bare())
        give(acme, 10_000)
        fx.run(0, 361, plants)
        fx.exec(CarbonCommand.Surrender(acme, uk, Tonnes.of(2_000)), 361).value()
        assertEquals(Tonnes.of(5_200), account().pending!!.outstanding)
        assertEquals(Tonnes.of(8_000), market.holdings.total(acme))
    }

    @Test fun surrenderIsRefusedWithoutAPendingYearOrWithTooManyOrTooFewPermits() {
        install(bare())
        give(acme, 1_000)
        fx.run(0, 100, plants)
        assertEquals(Rules.WRONG_STATE, fx.exec(CarbonCommand.Surrender(acme, uk, Tonnes.of(10)), 100).rule())
        fx.run(101, 361, plants)
        assertEquals(Rules.INVALID_QUANTITY, fx.exec(CarbonCommand.Surrender(acme, uk, Tonnes.of(8_000)), 361).rule())
        assertEquals(Rules.INSUFFICIENT_STOCK, fx.exec(CarbonCommand.Surrender(acme, uk, Tonnes.of(2_000)), 361).rule())
        assertEquals(Rules.INVALID_QUANTITY, fx.exec(CarbonCommand.Surrender(acme, uk, Tonnes.ZERO), 361).rule())
    }

    @Test fun nextYearsPermitsCannotBeUsedForThisYearWithoutBorrowing() {
        install(bare())
        fx.run(0, 361, plants)
        give(acme, 10_000, vintage = 2001)
        assertEquals(Rules.INSUFFICIENT_STOCK, fx.exec(CarbonCommand.Surrender(acme, uk, Tonnes.of(100)), 361).rule())
    }

    @Test fun borrowingTakesNextYearsPermitsUpToTheLimitAndCarriesInterest() {
        install(bare())
        give(acme, 5_200)
        fx.run(0, 361, plants)
        give(acme, 10_000, vintage = 2001)
        fx.exec(CarbonCommand.SetPolicy(acme, uk, autoOffsets = false, autoBorrow = true), 361).value()
        fx.run(362, 450, plants)
        val rec = account().recordFor(2000)!!
        assertEquals(Tonnes.of(720), rec.borrowed)
        assertEquals(Tonnes.of(1_280), rec.shortfall)
        assertEquals(Tonnes.of(1_280) + Tonnes.ofKg(57_600), account().carry)
        assertEquals(Tonnes.of(9_280), market.holdings.held(acme, 2001, 2001))
    }

    @Test fun borrowingNeedsBothThePolicyAndARegimeThatAllowsIt() {
        install(bare().copy(borrowLimitPpm = 0L))
        give(acme, 5_200)
        fx.run(0, 361, plants)
        give(acme, 10_000, vintage = 2001)
        fx.exec(CarbonCommand.SetPolicy(acme, uk, autoOffsets = false, autoBorrow = true), 361).value()
        fx.run(362, 450, plants)
        assertEquals(Tonnes.ZERO, account().recordFor(2000)!!.borrowed)
    }

    @Test fun creditsCoverUpToTheRegimesLimitWhenTheCompanyAllowsIt() {
        install(bare())
        give(acme, 5_200)
        fx.state.offsets.credit(acme, OffsetKind.METHANE_CAPTURE, Tonnes.of(5_000), Money.units(40_000))
        fx.run(0, 450, plants)
        val rec = account().recordFor(2000)!!
        assertEquals(Tonnes.of(720), rec.offsets)
        assertEquals(Tonnes.of(1_280), rec.shortfall)
        assertEquals(Tonnes.of(4_280), fx.state.offsets.held(acme, OffsetKind.METHANE_CAPTURE))
        assertEquals(Tonnes.of(720), fx.state.offsets.retiredBy(acme, uk, 2000))
    }

    @Test fun creditsOfAKindTheRegimeDoesNotAcceptAreNotUsed() {
        install(bare())
        give(acme, 5_200)
        fx.state.offsets.credit(acme, OffsetKind.DIRECT_AIR_CAPTURE, Tonnes.of(5_000), Money.units(40_000))
        fx.run(0, 450, plants)
        assertEquals(Tonnes.ZERO, account().recordFor(2000)!!.offsets)
    }

    @Test fun switchingAutomaticOffsetsOffKeepsCreditsInHand() {
        install(bare())
        give(acme, 5_200)
        fx.state.offsets.credit(acme, OffsetKind.METHANE_CAPTURE, Tonnes.of(5_000), Money.units(40_000))
        fx.exec(CarbonCommand.SetPolicy(acme, uk, autoOffsets = false, autoBorrow = false)).value()
        fx.run(0, 450, plants)
        assertEquals(Tonnes.ZERO, account().recordFor(2000)!!.offsets)
    }

    @Test fun retiringCreditsByCommandIsCappedByTheLimit() {
        install(bare())
        fx.state.offsets.credit(acme, OffsetKind.METHANE_CAPTURE, Tonnes.of(5_000), Money.units(40_000))
        fx.run(0, 361, plants)
        assertEquals(Rules.LIMIT_EXCEEDED, fx.exec(CarbonCommand.RetireOffsets(acme, uk, OffsetKind.METHANE_CAPTURE, Tonnes.of(800), true), 361).rule())
        fx.exec(CarbonCommand.RetireOffsets(acme, uk, OffsetKind.METHANE_CAPTURE, Tonnes.of(700), true), 361).value()
        assertEquals(Tonnes.of(7_200 - 700), account().pending!!.outstanding)
        assertEquals(Tonnes.of(20), fx.service.offsetAllowance(fx.state.regime(uk)!!, account().pending!!))
    }

    @Test fun voluntaryRetirementNeedsNoPendingYear() {
        fx.state.offsets.credit(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(100), Money.units(1_000))
        install()
        fx.exec(CarbonCommand.RetireOffsets(acme, uk, OffsetKind.CLEAN_COOKING, Tonnes.of(40), false)).value()
        assertEquals(Tonnes.of(60), fx.state.offsets.held(acme, OffsetKind.CLEAN_COOKING))
        assertEquals(Tonnes.of(40), fx.state.offsets.retiredBy(acme, uk, 2000))
        assertEquals(Rules.WRONG_STATE, fx.exec(CarbonCommand.RetireOffsets(acme, uk, OffsetKind.CLEAN_COOKING, Tonnes.of(40), true)).rule())
        assertEquals(Rules.INSUFFICIENT_STOCK, fx.exec(CarbonCommand.RetireOffsets(acme, uk, OffsetKind.CLEAN_COOKING, Tonnes.of(400), false)).rule())
    }

    @Test fun smallEmittersBelowTheThresholdAreExempt() {
        install(capAndTrade().let { it.copy(steps = listOf(it.steps.single().copy(threshold = Tonnes.of(50_000)))) })
        fx.run(0, 361, plants)
        assertNull(account().pending)
        assertEquals(ComplianceStatus.EXEMPT, account().recordFor(2000)!!.status)
        assertEquals(Tonnes.ZERO, market.holdings.total(acme))
    }

    @Test fun uncoveredIndustriesAreOnlyReportedOn() {
        install()
        fx.run(0, 361, listOf(emitter(1, cyrus, family = app.many.economytycoon.content.IndustryFamily.FOOD)))
        assertNull(fx.state.compliance.account(cyrus, uk))
        assertEquals(ComplianceStatus.UNREGULATED, fx.state.reports.statement(cyrus, uk, 2000)!!.status)
    }

    @Test fun theYearClosesAnAnnualStatementAndRating() {
        install(bare())
        give(acme, 10_000)
        fx.run(0, 361, plants)
        val s = fx.state.reports.statement(acme, uk, 2000)!!
        assertEquals(Tonnes.of(7_200), s.direct)
        assertEquals(RegimeKind.CAP_AND_TRADE, s.regime)
        assertEquals(5_000L, s.intensityGrams)
        assertNotNull(fx.state.reports.rating(acme))
        assertTrue(fx.eventsOf<CarbonEvent.RatingChanged>().any { it.company == acme })
    }

    @Test fun theDeadlineRefilesTheStatementWithTheOutcome() {
        install(bare())
        give(acme, 5_200)
        fx.run(0, 450, plants)
        val s = fx.state.reports.statement(acme, uk, 2000)!!
        assertEquals(ComplianceStatus.SHORTFALL, s.status)
        assertEquals(Tonnes.of(2_000), s.shortfall)
        assertEquals(Money.units(300_000), s.charges)
    }

    @Test fun aFollowingYearsStatementShowsTheChangeInFootprint() {
        install(bare())
        give(acme, 100_000)
        fx.run(0, 359, plants)
        fx.run(360, 720, listOf(emitter(1, acme, coalUnits = 50), emitter(2, borea)))
        val s = fx.state.reports.statement(acme, uk, 2001)!!
        assertEquals(-500_000L, s.changePpm)
    }

    @Test fun expiredVintagesLeaveTheHoldingsAndAreWrittenOff() {
        install(bare())
        give(acme, 100, vintage = 2000, cost = 5_000)
        fx.run(0, 1_441, emptyList())
        assertEquals(Tonnes.ZERO, market.holdings.total(acme))
        assertEquals(Tonnes.of(100), market.expired)
        assertTrue(fx.eventsOf<CarbonEvent.PermitsExpired>().any { it.tonnes == Tonnes.of(100) })
        assertTrue(fx.adjustments.any { it.memo == "permits expired" })
    }

    @Test fun withoutBankingPermitsLapseAfterTheComplianceYear() {
        install(bare().copy(bankingAllowed = false))
        give(acme, 100, vintage = 2000)
        fx.run(0, 361, emptyList())
        assertEquals(Tonnes.of(100), market.holdings.total(acme))
        fx.run(362, 722, emptyList())
        assertEquals(Tonnes.ZERO, market.holdings.total(acme))
    }

    @Test fun anInactiveCompanyIsNotFinedAndSettlesQuietly() {
        install(bare())
        fx.run(0, 361, plants)
        fx.inactive += acme
        fx.run(362, 450, listOf(emitter(2, borea)))
        assertEquals(ComplianceStatus.SHORTFALL, account().recordFor(2000)!!.status)
        assertTrue(fx.payments.none { it.reason == ReasonCode.FEE && it.payer.id == acme.value })
    }

    @Test fun anUnregulatedAccountSettlesIfTheRegimeIsRemoved() {
        install(bare())
        fx.run(0, 361, plants)
        fx.service.installRegime(CarbonRegime.none(uk), Tick(362)).value()
        fx.run(362, 450, plants)
        assertNull(account().pending)
        assertEquals(ComplianceStatus.UNREGULATED, account().recordFor(2000)!!.status)
    }

    @Test fun theReserveWithholdsPermitsWhenTooManyAreInCirculation() {
        install(capAndTrade())
        fx.run(0, 5, plants)
        give(acme, 30_000)
        fx.run(6, 361, plants)
        val e = fx.eventsOf<CarbonEvent.ReserveAdjusted>().first { it.year == 2001 }
        assertTrue(e.withheld.isPositive)
        assertTrue(market.lastCirculation >= Tonnes.of(30_000))
        assertEquals(e.withheld, market.supply(2001)!!.withheld)
    }

    @Test fun reviewHappensOncePerYear() {
        install(capAndTrade())
        fx.run(0, 400, plants)
        assertEquals(2001, market.reviewedYear)
        val before = fx.eventsOf<CarbonEvent.ReserveAdjusted>().size
        fx.run(401, 500, plants)
        assertEquals(before, fx.eventsOf<CarbonEvent.ReserveAdjusted>().size)
    }
}
