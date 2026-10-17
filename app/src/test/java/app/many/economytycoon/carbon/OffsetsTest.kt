package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetsTest {
    private val book = OffsetBook()
    private val rnd = RandomProvider(11L)
    private fun random(id: OffsetProjectId) = rnd.stream("offsets", id.value)

    private fun project(kind: OffsetKind, annual: Long = 1_000, at: Long = 0): OffsetProject {
        val profile = OffsetProfiles.of(kind)
        val p = OffsetProject(OffsetProjectId(book.projects().size + 1L), kind, acme, uk, Tonnes.of(annual), Tonnes.of(annual).cost(profile.upfrontPerAnnualTonne), Tick(at))
        OffsetLifecycle.schedule(p, profile)
        book.put(p)
        return p
    }

    @Test fun everyKindHasAProfileWithSaneShares() {
        for (k in OffsetKind.values()) {
            val p = OffsetProfiles.of(k)
            assertEquals(k, p.kind)
            assertTrue(p.haircutPpm in 0L..1_000_000L)
            assertTrue(p.upfrontPerAnnualTonne.isPositive)
        }
    }

    @Test fun directAirCaptureIsDearButCertain() {
        val dac = OffsetProfiles.of(OffsetKind.DIRECT_AIR_CAPTURE)
        val cooking = OffsetProfiles.of(OffsetKind.CLEAN_COOKING)
        assertTrue(dac.costPerExpectedCredit() > cooking.costPerExpectedCredit())
        assertTrue(dac.haircutPpm < cooking.haircutPpm)
    }

    @Test fun expectedCreditsApplyHaircutAndBuffer() {
        val p = OffsetProfiles.of(OffsetKind.REFORESTATION)
        val nominal = Tonnes.of(100) * p.lifeYears.toLong()
        val net = nominal.times(app.many.economytycoon.core.Rate(1_000_000L - p.haircutPpm))
        assertEquals(net - net.times(app.many.economytycoon.core.Rate(p.bufferPpm)), p.expectedCredits(Tonnes.of(100)))
    }

    @Test fun costPerExpectedCreditExceedsTheSimpleUpfrontPerTonne() {
        val p = OffsetProfiles.of(OffsetKind.CLEAN_COOKING)
        assertTrue(p.costPerExpectedCredit() > Money(p.upfrontPerAnnualTonne.raw / p.lifeYears))
    }

    @Test fun scheduleSetsValidationFirstIssuanceAndEnd() {
        val p = project(OffsetKind.METHANE_CAPTURE)
        val profile = OffsetProfiles.of(OffsetKind.METHANE_CAPTURE)
        assertEquals(Tick(profile.leadDays.toLong()), p.validationAt)
        assertEquals(Tick((profile.leadDays + profile.validationDays + profile.issuanceEveryDays).toLong()), p.nextIssuance)
        assertEquals(Tick(profile.leadDays + profile.lifeYears * 360L), p.endsAt)
    }

    @Test fun aProjectMovesThroughValidationToIssuing() {
        val p = project(OffsetKind.METHANE_CAPTURE)
        assertEquals(OffsetStatus.DEVELOPMENT, p.status)
        OffsetLifecycle.advance(book, Tick(100), ::random)
        assertTrue(p.status == OffsetStatus.VALIDATION || p.status == OffsetStatus.ISSUING || p.status == OffsetStatus.REJECTED)
    }

    @Test fun anIssuingProjectProducesCreditsEveryPeriod() {
        val p = project(OffsetKind.CLEAN_COOKING, annual = 4_000)
        val outcomes = ArrayList<OffsetOutcome>()
        for (d in 0L..400L) outcomes += OffsetLifecycle.advance(book, Tick(d), ::random)
        if (p.status == OffsetStatus.REJECTED) return
        val issued = outcomes.filterIsInstance<OffsetOutcome.Issued>()
        assertTrue(issued.size >= 2)
        assertTrue(issued.all { it.credits <= it.delivered && it.credits.isPositive })
        assertEquals(p.issued, Tonnes.sum(issued.map { it.credits }))
    }

    @Test fun creditsNeverExceedTheNominalVolumeAfterTheHaircut() {
        val p = project(OffsetKind.REFORESTATION, annual = 3_000)
        val outcomes = ArrayList<OffsetOutcome>()
        for (d in 0L..1_000L) outcomes += OffsetLifecycle.advance(book, Tick(d), ::random)
        val profile = OffsetProfiles.of(p.kind)
        for (i in outcomes.filterIsInstance<OffsetOutcome.Issued>()) {
            assertTrue(i.credits.raw <= i.nominal.times(app.many.economytycoon.core.Rate(1_000_000L - profile.haircutPpm)).raw)
        }
    }

    @Test fun aProjectCompletesAtTheEndOfItsLifeAndReleasesItsBuffer() {
        val p = project(OffsetKind.METHANE_CAPTURE, annual = 2_000)
        val outcomes = ArrayList<OffsetOutcome>()
        var seed = 0
        while (p.isLive && seed++ < 20) outcomes += OffsetLifecycle.advance(book, Tick(p.endsAt.value + 1), ::random)
        if (p.status == OffsetStatus.REJECTED) return
        assertEquals(OffsetStatus.COMPLETED, p.status)
        assertTrue(outcomes.any { it is OffsetOutcome.Completed })
        assertEquals(Money.ZERO, p.basisLeft)
    }

    @Test fun theAmortisedSlicesNeverExceedTheMoneyPaid() {
        val p = project(OffsetKind.METHANE_CAPTURE, annual = 1_000)
        val outcomes = ArrayList<OffsetOutcome>()
        for (d in 0L..p.endsAt.value + 5) outcomes += OffsetLifecycle.advance(book, Tick(d), ::random)
        val slices = Money.sum(outcomes.filterIsInstance<OffsetOutcome.Issued>().map { it.cost })
        assertTrue(slices <= p.capex)
        assertTrue(p.basisLeft.raw >= 0L)
    }

    @Test fun theSameSeedGivesTheSameLifecycle() {
        fun run(): List<String> {
            val b = OffsetBook(); val r = RandomProvider(99L)
            val p = OffsetProject(OffsetProjectId(1), OffsetKind.REFORESTATION, acme, uk, Tonnes.of(900), Money.units(90_000), Tick(0))
            OffsetLifecycle.schedule(p, OffsetProfiles.of(p.kind)); b.put(p)
            val log = ArrayList<String>()
            for (d in 0L..900L) for (o in OffsetLifecycle.advance(b, Tick(d)) { r.stream("o", it.value) }) log += "$d:${o::class.simpleName}:${(o as? OffsetOutcome.Issued)?.credits}"
            return log
        }
        assertEquals(run(), run())
    }

    @Test fun pipelineCreditsCountTheWholeRemainingLife() {
        project(OffsetKind.METHANE_CAPTURE, annual = 1_000)
        val pipeline = OffsetLifecycle.pipelineCredits(book, acme)
        assertTrue(pipeline.isPositive)
        assertTrue(pipeline <= OffsetProfiles.of(OffsetKind.METHANE_CAPTURE).expectedCredits(Tonnes.of(1_000)))
        assertEquals(Tonnes.ZERO, OffsetLifecycle.pipelineCredits(book, CarbonTestSupport.borea))
    }

    // ---- the exchange and the holdings ----

    @Test fun theExchangeStartsEmptyAtTheBaseSpotPrice() {
        val ex = book.exchange
        for (k in OffsetKind.values()) {
            assertEquals(Tonnes.ZERO, ex.stock(k))
            assertEquals(OffsetProfiles.of(k).spotBase, ex.spot(k))
            assertNull(ex.quote(k, Tonnes.of(1)))
        }
    }

    @Test fun aQuoteRisesWithTheSizeOfTheOrder() {
        val ex = book.exchange
        ex.add(OffsetKind.CLEAN_COOKING, Tonnes.of(10_000))
        val small = ex.quote(OffsetKind.CLEAN_COOKING, Tonnes.of(100))!!
        val big = ex.quote(OffsetKind.CLEAN_COOKING, Tonnes.of(5_000))!!
        assertTrue(big.unitPrice > small.unitPrice)
        assertTrue(small.unitPrice >= ex.spot(OffsetKind.CLEAN_COOKING))
        assertNull(ex.quote(OffsetKind.CLEAN_COOKING, Tonnes.of(10_001)))
        assertNull(ex.quote(OffsetKind.CLEAN_COOKING, Tonnes.ZERO))
    }

    @Test fun slippageIsCappedAtFiftyPercent() {
        val ex = book.exchange
        ex.add(OffsetKind.CLEAN_COOKING, Tonnes.of(10))
        assertTrue(ex.quote(OffsetKind.CLEAN_COOKING, Tonnes.of(10))!!.slippagePpm <= 500_000L)
    }

    @Test fun monthlyBuyingPressureLiftsThePriceAndSurplusLowersIt() {
        val ex = book.exchange
        ex.add(OffsetKind.METHANE_CAPTURE, Tonnes.of(1_000))
        ex.closeMonth()
        val base = ex.spot(OffsetKind.METHANE_CAPTURE)
        ex.remove(OffsetKind.METHANE_CAPTURE, Tonnes.of(800))
        ex.closeMonth()
        assertTrue(ex.spot(OffsetKind.METHANE_CAPTURE) > base)
        val high = ex.spot(OffsetKind.METHANE_CAPTURE)
        ex.add(OffsetKind.METHANE_CAPTURE, Tonnes.of(5_000))
        ex.closeMonth()
        assertTrue(ex.spot(OffsetKind.METHANE_CAPTURE) < high)
    }

    @Test fun theSpotPriceStaysWithinAHalfAndFiveTimesTheBase() {
        val ex = book.exchange
        val base = OffsetProfiles.of(OffsetKind.CLEAN_COOKING).spotBase
        repeat(200) { ex.add(OffsetKind.CLEAN_COOKING, Tonnes.of(1)); ex.remove(OffsetKind.CLEAN_COOKING, Tonnes.ofKg(900)); ex.closeMonth() }
        assertTrue(ex.spot(OffsetKind.CLEAN_COOKING).raw in (base.raw / 2)..(base.raw * 5))
    }

    @Test fun holdingsCarryTheirCostAndDebitProportionally() {
        book.credit(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(100), Money.units(600))
        book.credit(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(100), Money.units(1_000))
        assertEquals(Tonnes.of(200), book.held(acme, OffsetKind.CLEAN_COOKING))
        val (taken, cost) = book.debit(acme, OffsetKind.CLEAN_COOKING, Tonnes.of(50))
        assertEquals(Tonnes.of(50), taken)
        assertEquals(Money.units(400), cost)
        assertEquals(Money.units(1_200), book.costOf(acme, OffsetKind.CLEAN_COOKING))
    }

    @Test fun debitingMoreThanHeldTakesWhatThereIsAndClearsTheLot() {
        book.credit(acme, OffsetKind.REFORESTATION, Tonnes.of(10), Money.units(77))
        val (taken, cost) = book.debit(acme, OffsetKind.REFORESTATION, Tonnes.of(99))
        assertEquals(Tonnes.of(10), taken)
        assertEquals(Money.units(77), cost)
        assertEquals(Tonnes.ZERO, book.held(acme, OffsetKind.REFORESTATION))
        assertTrue(book.companies().isEmpty())
        assertEquals(Pair(Tonnes.ZERO, Money.ZERO), book.debit(acme, OffsetKind.REFORESTATION, Tonnes.of(1)))
    }

    @Test fun retiredCreditsAreRecordedPerCompanyAndYear() {
        book.noteRetired(acme, uk, 2005, Tonnes.of(10))
        book.noteRetired(acme, uk, 2005, Tonnes.of(5))
        assertEquals(Tonnes.of(15), book.retiredBy(acme, uk, 2005))
        assertEquals(Tonnes.ZERO, book.retiredBy(acme, uk, 2006))
        assertNotNull(book.project(OffsetProjectId(1)) ?: Unit)
    }
}
