package app.many.economytycoon.banking

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BankingCodecTest {
    private val inputs = BankingFx.calm
    private val country = BankingFx.country

    private fun busy(days: Long = 100L): Pair<BankingService, RandomProvider> {
        val s = BankingService(); val r = RandomProvider(8L)
        s.seedRivals(country, 3, inputs, Tick(0), r)
        s.found(BankAction.FoundBank("Mine", country, CompanyId(4), Money.units(2_000_000), true), inputs, Tick(0), r)
        for (t in 1L..days) s.step(Tick(t), inputs, r)
        s.apply(BankAction.RunStressTest(s.bankIds().last(), "adverse"), Tick(days), inputs, CompanyId(4))
        s.hideAdvice("stress-due", Tick(days), 40)
        return s to r
    }

    private fun bytes(s: BankingService): ByteArray = ByteWriter().also { BankingCodec.write(s, it) }.toByteArray()

    private fun load(b: ByteArray): BankingService = BankingService().also { BankingCodec.read(ByteReader(b), it) }

    @Test fun emptyServiceRoundTrips() {
        val s = BankingService()
        val copy = load(bytes(s))
        assertEquals(s.stateHash(), copy.stateHash())
        assertTrue(copy.bankIds().isEmpty())
    }

    @Test fun busySectorRoundTripsWithTheSameHash() {
        val (s, _) = busy()
        val copy = load(bytes(s))
        assertEquals(s.stateHash(), copy.stateHash())
        assertEquals(s.bankIds(), copy.bankIds())
        for (id in s.bankIds()) assertEquals(s.bank(id), copy.bank(id))
    }

    @Test fun everythingTheScreensNeedSurvives() {
        val (s, _) = busy()
        val copy = load(bytes(s))
        assertEquals(s.fund(country)!!.balance, copy.fund(country)!!.balance)
        assertEquals(s.market(country), copy.market(country))
        assertEquals(s.lastStress(s.bankIds().last()), copy.lastStress(s.bankIds().last()))
        assertEquals(s.hiddenAdvice(), copy.hiddenAdvice())
        assertEquals(s.systemicStress(country), copy.systemicStress(country))
        assertEquals(s.strategyOf(s.bankIds().first()), copy.strategyOf(s.bankIds().first()))
    }

    @Test fun savingIsDeterministic() {
        val (s, _) = busy()
        assertTrue(bytes(s).contentEquals(bytes(s)))
        val (t, _) = busy()
        assertTrue(bytes(s).contentEquals(bytes(t)))
    }

    @Test fun savingAndLoadingTwiceIsAFixedPoint() {
        val (s, _) = busy()
        val once = bytes(s)
        assertTrue(once.contentEquals(bytes(load(once))))
    }

    @Test fun aRestoredSectorKeepsRunningIdentically() {
        val (s, _) = busy(60)
        val copy = load(bytes(s))
        val ra = RandomProvider(77L); val rb = RandomProvider(77L)
        for (t in 61L..150L) { s.step(Tick(t), inputs, ra); copy.step(Tick(t), inputs, rb) }
        assertEquals(s.stateHash(), copy.stateHash())
    }

    @Test fun loadingReplacesWhatWasThere() {
        val (s, _) = busy(40)
        val target = BankingService()
        target.seedRivals(country, 5, inputs, Tick(0), RandomProvider(1L))
        BankingCodec.read(ByteReader(bytes(s)), target)
        assertEquals(s.stateHash(), target.stateHash())
    }

    @Test fun hashChangesWhenAnythingChanges() {
        val (s, r) = busy(40)
        val h0 = s.stateHash()
        for (t in 41L..60L) s.step(Tick(t), inputs, r)
        val h1 = s.stateHash()
        assertFalse(h0 == h1)
        s.hideAdvice("x", Tick(61), 5)
        assertFalse(h1 == s.stateHash())
        s.rumour(s.bankIds().first(), 100_000L, Tick(62))
        assertTrue(s.stateHash() != h1)
    }

    @Test fun differentWorldsHashDifferently() {
        val a = BankingService(); val b = BankingService()
        a.seedRivals(country, 3, inputs, Tick(0), RandomProvider(1L))
        b.seedRivals(country, 3, inputs, Tick(0), RandomProvider(2L))
        assertFalse(a.stateHash() == b.stateHash())
    }

    @Test fun unknownVersionIsRejected() {
        val out = ByteWriter(); out.writeVarInt(BankingCodec.VERSION + 1)
        try { load(out.toByteArray()); fail("expected a corrupt data error") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("version")) }
    }

    @Test fun truncatedDataIsRejected() {
        val (s, _) = busy(40)
        val b = bytes(s)
        try { load(b.copyOf(b.size / 2)); fail("expected an error") } catch (e: ByteReader.CorruptDataException) { assertTrue(true) }
    }

    @Test fun anUnbalancedSaveIsRefused() {
        val (s, _) = busy(40)
        s.book.banks.values.first().reserves += Money.units(1)
        try { load(bytes(s)); fail("expected a corrupt data error") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("balance")) }
    }

    @Test fun staleBankCounterIsRefused() {
        val (s, _) = busy(10)
        s.book.nextBankId = 1L
        try { load(bytes(s)); fail("expected a corrupt data error") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("counter")) }
    }

    @Test fun stressRecordForAMissingBankIsRefused() {
        val (s, _) = busy(10)
        s.book.stress[BankId(999)] = StressRecord(1, "adverse", Rate.ZERO, Rate.ZERO, Rate.ZERO, Money.ZERO, Rate.ZERO, true)
        s.book.nextBankId = 1_000L
        try { load(bytes(s)); fail("expected a corrupt data error") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("stress")) }
    }

    @Test fun resolvedBanksAreKeptInTheSave() {
        val (s, r) = busy(40)
        val victim = s.bankIds().first()
        val b = s.book.banks.getValue(victim)
        val loss = b.snapshot().bookEquity.times(Rate.percent(160))
        b.reserves -= loss; b.retained -= loss
        for (t in 41L..46L) s.step(Tick(t), inputs, r)
        assertFalse(s.bank(victim)!!.status.isOperating)
        val copy = load(bytes(s))
        assertEquals(s.bank(victim), copy.bank(victim))
        assertEquals(s.stateHash(), copy.stateHash())
    }
}
