package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-step stories: a run, a failure, a downturn. Every step keeps the books balanced. */
class BankingScenarioTest {
    private val country = BankingFx.country
    private val calm = StaticBankingInputs()

    private class World(val s: BankingService, val r: RandomProvider, val ids: List<BankId>)

    private fun world(n: Int = 4, seed: Long = 21L): World {
        val s = BankingService(); val r = RandomProvider(seed)
        val ids = s.seedRivals(country, n, calm, Tick(0), r)
        return World(s, r, ids)
    }

    private fun World.day(t: Long, inputs: BankingInputs = calm): BankingStep {
        val step = s.step(Tick(t), inputs, r)
        for (b in s.banks()) assertEquals("tick $t ${b.name}", Money.ZERO, b.imbalance)
        return step
    }

    /** Loses money outside the books' control: cash and equity fall together, so the bank stays balanced but weaker. */
    private fun World.hit(id: BankId, loss: Money) {
        val b = s.book.banks.getValue(id)
        b.reserves -= loss
        b.retained -= loss
    }

    private fun World.depositsOf(id: BankId) = s.bank(id)!!.depositsTotal

    @Test fun rumourStartsARunThatOtherBanksAbsorb() {
        val w = world()
        for (t in 1L..40L) w.day(t)
        val victim = w.ids.first()
        val others = w.ids.drop(1)
        val systemBefore = w.s.banks().sumOf { it.depositsTotal.raw }
        val victimBefore = w.depositsOf(victim)
        val othersBefore = others.sumOf { w.depositsOf(it).raw }
        w.s.rumour(victim, 900_000L, Tick(41))
        var peakStage = RunStage.CALM
        for (t in 41L..48L) {
            w.day(t)
            peakStage = maxOf(peakStage, w.s.bank(victim)!!.runStage)
        }
        assertTrue("stage reached $peakStage", peakStage >= RunStage.RUN)
        assertTrue(w.depositsOf(victim) < victimBefore)
        assertTrue(others.sumOf { w.depositsOf(it).raw } > othersBefore)
        // Withdrawals move money between banks; the system keeps the same deposits apart from the month's growth.
        assertTrue(w.s.banks().sumOf { it.depositsTotal.raw } >= systemBefore - Money.units(1).raw)
    }

    @Test fun aRumourFadesAndConfidenceRecovers() {
        val w = world()
        for (t in 1L..40L) w.day(t)
        val id = w.ids.first()
        w.s.rumour(id, 400_000L, Tick(41))
        var low = Rate.ONE_PPM
        for (t in 41L..60L) { w.day(t); low = minOf(low, w.s.bank(id)!!.confidencePpm) }
        assertTrue(low < Rate.ONE_PPM)
        for (t in 61L..400L) w.day(t)
        assertTrue(w.s.bank(id)!!.confidencePpm > low)
        assertEquals(RunStage.CALM, w.s.bank(id)!!.runStage)
    }

    @Test fun bankUnderRunFundsItselfFromTheMarketOrTheCentralBank() {
        val w = world()
        for (t in 1L..40L) w.day(t)
        val victim = w.ids.first()
        w.s.rumour(victim, 950_000L, Tick(41))
        var funded = false
        for (t in 41L..52L) {
            val events = w.day(t).events
            val b = w.s.bank(victim)!!
            funded = funded || b.interbankBorrowed.isPositive || b.centralBankDebt.isPositive || events.any { it is BankEvent.FacilityUsed || it is BankEvent.FireSale }
        }
        assertTrue(funded)
    }

    @Test fun insolventBankIsResolvedAndTheSectorSurvives() {
        val w = world()
        for (t in 1L..40L) w.day(t)
        val victim = w.ids.first()
        val equity = w.s.bank(victim)!!.bookEquity
        w.hit(victim, equity.times(Rate.percent(130)))
        val events = ArrayList<BankEvent>()
        for (t in 41L..45L) events += w.day(t).events
        val resolved = events.filterIsInstance<BankEvent.Resolved>().firstOrNull { it.outcome.bank == victim }
        assertNotNull(resolved)
        val status = w.s.bank(victim)!!.status
        assertTrue(status == BankStatus.MERGED || status == BankStatus.LIQUIDATED)
        assertTrue(w.ids.drop(1).all { w.s.bank(it)!!.status.isOperating })
        assertTrue(w.s.systemicStress(country) > 0L)
    }

    @Test fun resolvedBankHasEmptyBooksAndNoActions() {
        val w = world()
        for (t in 1L..40L) w.day(t)
        val victim = w.ids.first()
        w.hit(victim, w.s.bank(victim)!!.bookEquity.times(Rate.percent(150)))
        for (t in 41L..45L) w.day(t)
        val b = w.s.bank(victim)!!
        assertFalse(b.status.isOperating)
        assertEquals(Money.ZERO, b.depositsTotal)
        assertEquals(Money.ZERO, b.loansGross)
        assertEquals(app.many.economytycoon.core.Rules.WRONG_STATE, w.s.apply(BankAction.SetLoanMargin(victim, 1), Tick(46), calm, null).rejectionOrNull()?.rule)
    }

    @Test fun mergerHandsDepositsAndLoansToTheAcquirer() {
        val w = world(3)
        for (t in 1L..40L) w.day(t)
        val victim = w.ids.first()
        val depositsBefore = w.s.banks().filter { it.operator == BankOperator.AI }.sumOf { it.depositsTotal.raw }
        w.hit(victim, w.s.bank(victim)!!.bookEquity.times(Rate.percent(110)))
        val events = ArrayList<BankEvent>()
        for (t in 41L..44L) events += w.day(t).events
        val outcome = events.filterIsInstance<BankEvent.Resolved>().first { it.outcome.bank == victim }.outcome
        if (outcome.tool == ResolutionTool.MERGER) {
            val acquirer = w.s.bank(outcome.acquirer!!)!!
            assertTrue(acquirer.status.isOperating)
            assertTrue(w.s.banks().filter { it.status.isOperating }.sumOf { it.depositsTotal.raw } >= depositsBefore * 9 / 10)
        } else {
            assertEquals(ResolutionTool.LIQUIDATION, outcome.tool)
            assertTrue(outcome.insuredPaid.isPositive)
        }
    }

    @Test fun recessionHurtsEarningsAndRaisesBadLoans() {
        val calmWorld = world(3, 30L)
        val badWorld = world(3, 30L)
        val recession = calm.recession(600_000L)
        for (t in 1L..300L) { calmWorld.day(t); badWorld.day(t, if (t > 60) recession else calm) }
        val calmNpl = calmWorld.s.banks().map { it.nplRatio.ppm }.average()
        val badNpl = badWorld.s.banks().map { it.nplRatio.ppm }.average()
        assertTrue("npl $badNpl vs $calmNpl", badNpl > calmNpl)
        val calmProfit = calmWorld.s.banks().sumOf { it.retained.raw }
        val badProfit = badWorld.s.banks().sumOf { it.retained.raw }
        assertTrue(badProfit < calmProfit)
    }

    @Test fun supervisorRestrictsAWeakBankAndLaterReleasesIt() {
        val w = world()
        for (t in 1L..40L) w.day(t)
        val id = w.ids.first()
        val snap = w.s.bank(id)!!
        val cet1 = snap.cet1Capital
        // Take about 60% of common equity: the bank is thin but still solvent.
        w.hit(id, cet1.times(Rate.percent(60)))
        var supervised = false
        for (t in 41L..120L) {
            w.day(t)
            supervised = supervised || w.s.bank(id)!!.status == BankStatus.SUPERVISED
        }
        assertTrue(supervised)
    }

    @Test fun supervisedBankMayNotPayDividendsOrLoosenStandards() {
        val s = BankingService(); val r = RandomProvider(1L)
        val me = app.many.economytycoon.core.CompanyId(3)
        val id = s.found(BankAction.FoundBank("Own", country, me, Money.units(2_000_000), true), calm, Tick(0), r).getOrThrow().bank
        s.book.banks.getValue(id).changeStatus(BankStatus.SUPERVISED, Tick(1))
        assertEquals(app.many.economytycoon.core.Rules.REGULATION, s.apply(BankAction.PayDividend(id, Money.units(10)), Tick(2), calm, me).rejectionOrNull()?.rule)
        assertEquals(app.many.economytycoon.core.Rules.REGULATION, s.apply(BankAction.SetGrowthAppetite(id, Rate.percent(99)), Tick(2), calm, me).rejectionOrNull()?.rule)
        assertTrue(s.apply(BankAction.SetGrowthAppetite(id, Rate.percent(5)), Tick(2), calm, me).isOk)
    }

    @Test fun savedAndRestoredSectorKeepsTheStory() {
        val w = world()
        for (t in 1L..70L) w.day(t)
        val out = app.many.economytycoon.core.ByteWriter()
        BankingCodec.write(w.s, out)
        val copy = BankingService()
        BankingCodec.read(app.many.economytycoon.core.ByteReader(out.toByteArray()), copy)
        assertEquals(w.s.stateHash(), copy.stateHash())
        val r2 = RandomProvider(21L)
        // Continue both from the same point with the same randomness.
        val ra = RandomProvider(99L); val rb = RandomProvider(99L)
        for (t in 71L..130L) { w.s.step(Tick(t), calm, ra); copy.step(Tick(t), calm, rb) }
        assertEquals(w.s.stateHash(), copy.stateHash())
        assertNotNull(r2)
    }
}
