package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonTest {
    private fun st(id: Long, worth: Long, player: Long? = id) =
        CompanyStatement(id, "Co $id", player?.let { PlayerId(it) }, Money.units(worth), Money.units(worth / 4), Money.units(worth), Money.units(worth / 2), Money.ZERO, Money.units(worth), true)

    // ---- clock ----

    @Test fun clockReportsProgress() {
        val c = SeasonClock(1, 100, 360)
        assertEquals(460L, c.endTick)
        assertEquals(0L, c.elapsed(50))
        assertEquals(180L, c.elapsed(280))
        assertEquals(500_000L, c.progressPpm(280))
        assertEquals(360L, c.remaining(0).coerceAtMost(360))
        assertEquals(0L, c.remaining(460))
        assertTrue(c.over(460)); assertTrue(!c.over(459))
    }

    @Test fun nextSeasonStartsWhereTheLastEnded() {
        val n = SeasonClock(3, 720, 360).next()
        assertEquals(4, n.number); assertEquals(1080L, n.startTick)
    }

    @Test fun invalidClocksAreRefused() {
        for (bad in listOf({ SeasonClock(0, 0, 10) }, { SeasonClock(1, -1, 10) }, { SeasonClock(1, 0, 0) })) {
            try { bad(); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
        }
    }

    // ---- rating ----

    @Test fun equalRatingsExpectHalf() {
        assertEquals(500_000, Rating.expectedPpm(1000, 1000))
    }

    @Test fun expectedScoresAreComplementary() {
        for (d in listOf(0, 13, 50, 120, 333, 799, 800, 5000)) {
            assertEquals(1_000_000, Rating.expectedPpm(1000 + d, 1000) + Rating.expectedPpm(1000, 1000 + d))
        }
    }

    @Test fun expectedScoreRisesWithTheLead() {
        var last = 0
        for (d in 0..800 step 10) { val e = Rating.expectedPpm(1000 + d, 1000); assertTrue(e >= last); last = e }
        assertEquals(990_099, Rating.expectedPpm(1800, 1000))
        assertEquals(Rating.expectedPpm(1800, 1000), Rating.expectedPpm(3000, 1000))
    }

    @Test fun winnerGainsAndLoserLoses() {
        val r = Rating.update(mapOf(PlayerId(1) to 1000, PlayerId(2) to 1000), listOf(PlayerId(1), PlayerId(2)))
        assertEquals(1016, r[PlayerId(1)])
        assertEquals(984, r[PlayerId(2)])
    }

    @Test fun upsetsMoveRatingsMore() {
        val strong = PlayerId(1); val weak = PlayerId(2)
        val upset = Rating.update(mapOf(strong to 1400, weak to 1000), listOf(weak, strong))
        val expected = Rating.update(mapOf(strong to 1400, weak to 1000), listOf(strong, weak))
        assertTrue(upset.getValue(weak) - 1000 > expected.getValue(strong) - 1400)
    }

    @Test fun ratingTotalIsConservedUpToRounding() {
        val order = (1L..5L).map { PlayerId(it) }
        val before = order.associateWith { 1000 + it.value.toInt() * 37 }
        val after = Rating.update(before, order)
        val drift = after.values.sum() - before.values.sum()
        assertTrue("drift $drift", drift in -order.size..0)
    }

    @Test fun singlePlayerKeepsTheirRating() {
        assertEquals(mapOf(PlayerId(1) to 1234), Rating.update(mapOf(PlayerId(1) to 1234), listOf(PlayerId(1))))
    }

    // ---- ledger ----

    private fun rowsOf(tracker: SeasonTracker, vararg s: CompanyStatement) = tracker.table(s.toList())

    @Test fun closingASeasonRecordsResultsAndRatings() {
        val ledger = SeasonLedger()
        val tracker = SeasonTracker(ledger, lengthTicks = 100)
        val stmts = listOf(st(1, 50_000), st(2, 20_000), st(3, 90_000, player = null))
        tracker.begin(emptyList())
        assertNull(tracker.advance(99) { stmts })
        val result = tracker.advance(100) { stmts } ?: error("season should close")
        assertEquals(1, result.season)
        assertEquals(3, result.rows.size)
        assertEquals(2, tracker.clock.number)
        assertEquals(100L, tracker.clock.startTick)
        assertEquals(1, ledger.titles(PlayerId(1)))
        assertEquals(0, ledger.titles(PlayerId(2)))
        assertEquals(1, ledger.bestFinish(PlayerId(1)))
        assertEquals(2, ledger.bestFinish(PlayerId(2)))
        assertTrue(ledger.rating(PlayerId(1)) > Rating.START)
        assertTrue(ledger.rating(PlayerId(2)) < Rating.START)
        assertEquals(PlayerId(1), ledger.ratingTable().first().first)
    }

    @Test fun computerCompaniesDoNotAffectRatings() {
        val ledger = SeasonLedger()
        ledger.close(1, 10, SeasonScoring.table(listOf(st(1, 100), st(2, 5, player = null)), emptyMap()))
        assertEquals(Rating.START, ledger.rating(PlayerId(1)))
        assertEquals(0, ledger.titles(PlayerId(1)))
    }

    @Test fun aSeasonCannotBeClosedTwice() {
        val ledger = SeasonLedger()
        ledger.close(1, 10, emptyList())
        try { ledger.close(1, 20, emptyList()); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
    }

    @Test fun baselinesResetEachSeason() {
        val ledger = SeasonLedger()
        val tracker = SeasonTracker(ledger, lengthTicks = 10)
        tracker.begin(listOf(st(1, 1_000)))
        assertEquals(Money.units(1_000), tracker.baselineOf(1))
        tracker.advance(10) { listOf(st(1, 8_000)) }
        assertEquals(Money.units(8_000), tracker.baselineOf(1))
        assertEquals(Money.ZERO, tracker.baselineOf(99))
    }

    @Test fun liveTableUsesBaselines() {
        val tracker = SeasonTracker(SeasonLedger())
        tracker.begin(listOf(st(1, 10_000), st(2, 0)))
        val rows = rowsOf(tracker, st(1, 10_500), st(2, 4_000))
        assertEquals(2L, rows.first().company)
    }

    @Test fun ledgerRoundTripsThroughItsCodec() {
        val ledger = SeasonLedger()
        val tracker = SeasonTracker(ledger, lengthTicks = 10)
        tracker.begin(emptyList())
        tracker.advance(10) { listOf(st(1, 5_000), st(2, 9_000), st(3, 100, player = null)) }
        tracker.advance(20) { listOf(st(1, 15_000), st(2, 9_500)) }
        val w = ByteWriter(); ledger.write(w)
        val copy = SeasonLedger(); copy.read(ByteReader(w.toByteArray()))
        assertEquals(ledger.results(), copy.results())
        assertEquals(ledger.ratingTable(), copy.ratingTable())
        assertEquals(ledger.titles(PlayerId(2)), copy.titles(PlayerId(2)))
        val w2 = ByteWriter(); copy.write(w2)
        assertArrayEquals(w.toByteArray(), w2.toByteArray())
    }

    @Test fun truncatedLedgerIsRefusedAndKeepsOldContents() {
        val ledger = SeasonLedger(); ledger.close(1, 5, SeasonScoring.table(listOf(st(1, 100)), emptyMap()))
        val w = ByteWriter(); ledger.write(w)
        val bytes = w.toByteArray()
        val target = SeasonLedger(); target.close(7, 1, emptyList())
        for (n in 0 until bytes.size) {
            try { target.read(ByteReader(bytes.copyOf(n))); org.junit.Assert.fail("cut at $n") } catch (e: ByteReader.CorruptDataException) { assertTrue(true) }
        }
        assertEquals(1, target.results().size)
        assertEquals(7, target.results().single().season)
    }

    @Test fun scoringTheSameSeasonTwiceGivesTheSameLedger() {
        fun run(): ByteArray {
            val ledger = SeasonLedger(); val t = SeasonTracker(ledger, lengthTicks = 10)
            t.begin(emptyList())
            for (s in 1..4) t.advance(s * 10L) { (1L..4L).map { st(it, (it * 31 + s * 7) % 50 * 1_000) } }
            return ByteWriter().also { ledger.write(it) }.toByteArray()
        }
        assertArrayEquals(run(), run())
    }
}
