package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SeasonTrackerStateTest {
    private fun statement(company: Long, worth: Long) =
        CompanyStatement(company, "Co $company", PlayerId(company), Money.units(100), Money.units(10), Money.units(worth), Money.units(worth), Money.ZERO, Money.units(worth), true)

    private fun tracker(): SeasonTracker {
        val t = SeasonTracker(SeasonLedger(), ScoreRules(), 100L)
        t.begin(listOf(statement(1, 5_000), statement(2, 7_000)))
        t.advance(100L) { listOf(statement(1, 9_000), statement(2, 8_000)) }
        return t
    }

    private fun bytes(t: SeasonTracker) = ByteWriter().also { t.write(it) }.toByteArray()

    @Test fun theClockAndBaselinesSurviveARoundTrip() {
        val t = tracker()
        val back = SeasonTracker(SeasonLedger(), ScoreRules(), 1L)
        back.read(ByteReader(bytes(t)))
        assertEquals(t.clock, back.clock)
        assertEquals(2, back.clock.number)
        assertEquals(Money.units(9_000), back.baselineOf(1))
        assertEquals(Money.units(8_000), back.baselineOf(2))
    }

    @Test fun theEncodingIsStable() {
        assertEquals(Hex.encode(bytes(tracker())), Hex.encode(bytes(tracker())))
    }

    @Test fun aLoadedTrackerScoresLikeTheOriginal() {
        val t = tracker()
        val back = SeasonTracker(SeasonLedger(), ScoreRules(), 1L)
        back.read(ByteReader(bytes(t)))
        val now = listOf(statement(1, 9_500), statement(2, 12_000))
        assertEquals(t.table(now), back.table(now))
    }

    @Test fun badVersionsAndImplausibleClocksAreRefused() {
        val good = bytes(tracker())
        val wrong = good.copyOf().also { it[0] = 7 }
        try { SeasonTracker(SeasonLedger()).read(ByteReader(wrong)); fail("accepted") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message.orEmpty().contains("version")) }
        val w = ByteWriter(); w.writeVarInt(1); w.writeVarInt(0); w.writeVarLong(0); w.writeVarLong(10); w.writeVarInt(0)
        try { SeasonTracker(SeasonLedger()).read(ByteReader(w.toByteArray())); fail("accepted") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message.orEmpty().contains("season")) }
    }

    @Test fun truncatedDataIsRefused() {
        val good = bytes(tracker())
        for (n in 0 until good.size) {
            try { SeasonTracker(SeasonLedger()).read(ByteReader(good.copyOf(n))); fail("accepted $n bytes") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
        }
    }
}
