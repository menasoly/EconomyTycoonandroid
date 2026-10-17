package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RegressionTest {
    private val d = Samples.cheap()

    private fun run(policy: String = "greedy", seed: Long = 1L, end: List<Long> = listOf(300_000L, 400_000L), netWorth: Long = 320_000L, cash: Long = 100_000L,
                    outcome: RunEnd = RunEnd.HORIZON, hash: String? = null): RunMetrics {
        val m = Samples.run(d, policy, seed, end = end, netWorth = netWorth, cash = cash, outcome = outcome)
        return if (hash == null) m else m.copy(stateHash = hash)
    }

    private val base = listOf(run("greedy", 1), run("greedy", 2), run("idle", 1, end = listOf(0L, 0L), netWorth = 300_000L))

    @Test fun capturingRecordsOneEntryPerRunInKeyOrder() {
        val b = Regression.capture("v1", d, base)
        assertEquals(listOf("greedy/1", "greedy/2", "idle/1"), b.entries.map { it.key })
        assertEquals(d.stateHash(), b.draftHash)
        assertEquals("v1", b.name)
        assertEquals(320_000L, b.entry("greedy/1")?.netWorth)
        assertNull(b.entry("nobody/9"))
    }

    @Test fun identicalRunsPass() {
        val rep = Regression.compare(Regression.capture("v1", d, base), d, base)
        assertTrue(rep.identical)
        assertTrue(rep.passed)
        assertEquals(listOf("baseline 'v1': all 3 runs identical"), rep.lines())
    }

    @Test fun aChangedNumberIsReportedWithItsSizeInPercent() {
        val now = listOf(run("greedy", 1, netWorth = 304_000L), base[1], base[2])
        val rep = Regression.compare(Regression.capture("v1", d, base), d, now)
        assertFalse(rep.passed)
        val diff = rep.of(DiffKind.CHANGED).single()
        assertEquals("greedy/1", diff.key)
        assertEquals("net worth 320,000 -> 304,000 (-5.00%)", diff.changes.single().toString())
        assertTrue(rep.lines().any { it.trim() == "net worth 320,000 -> 304,000 (-5.00%)" })
    }

    @Test fun anOutcomeChangeIsReportedInWords() {
        val now = listOf(run("greedy", 1, outcome = RunEnd.LOST), base[1], base[2])
        val diff = Regression.compare(Regression.capture("v1", d, base), d, now).of(DiffKind.CHANGED).single()
        assertTrue(diff.changes.any { it.metric == "outcome" && it.before == "Still going" && it.after == "Lost" })
    }

    @Test fun smallChangesWithinTheTolerancePass() {
        val now = listOf(run("greedy", 1, netWorth = 321_000L), base[1], base[2])
        val b = Regression.capture("v1", d, base)
        assertFalse(Regression.compare(b, d, now, tolerancePpm = 0).passed)
        val tolerant = Regression.compare(b, d, now, tolerancePpm = 10_000)
        assertTrue(tolerant.passed)
        assertEquals(1, tolerant.of(DiffKind.WITHIN_TOLERANCE).size)
    }

    @Test fun aChangeFromZeroIsNeverWithinTolerance() {
        val b = Regression.capture("v1", d, listOf(run("greedy", 1, cash = 0)))
        val rep = Regression.compare(b, d, listOf(run("greedy", 1, cash = 5)), tolerancePpm = 900_000)
        assertEquals(DiffKind.CHANGED, rep.diffs.single().kind)
    }

    @Test fun aDifferentHashWithTheSameNumbersIsHashOnly() {
        val now = listOf(run("greedy", 1, hash = "different"), base[1], base[2])
        val rep = Regression.compare(Regression.capture("v1", d, base), d, now)
        assertEquals(DiffKind.HASH_ONLY, rep.diffs.first { it.key == "greedy/1" }.kind)
        assertTrue(rep.passed)
        assertFalse(rep.identical)
    }

    @Test fun missingAndAddedRunsAreListed() {
        val now = listOf(base[0], run("scripted", 1))
        val rep = Regression.compare(Regression.capture("v1", d, base), d, now)
        assertEquals(listOf("greedy/2", "idle/1"), rep.of(DiffKind.MISSING).map { it.key })
        assertEquals(listOf("scripted/1"), rep.of(DiffKind.ADDED).map { it.key })
        assertFalse(rep.passed)
    }

    @Test fun aChangedDraftIsMentioned() {
        val other = d.copy(seed = d.seed + 1)
        val rep = Regression.compare(Regression.capture("v1", d, base), other, base)
        assertTrue(rep.draftChanged)
        assertFalse(rep.identical)
        assertTrue(rep.lines().any { it.contains("draft has changed") })
    }

    @Test fun theSummaryCountsEachKind() {
        val now = listOf(run("greedy", 1, netWorth = 1L), base[1])
        val rep = Regression.compare(Regression.capture("v1", d, base), d, now)
        assertEquals("1 identical, 1 changed, 1 missing from the new runs", rep.summary())
    }

    @Test fun goalProgressChangesAreNamedByGoalNumber() {
        val now = listOf(run("greedy", 1, end = listOf(300_000L, 450_000L)), base[1], base[2])
        val diff = Regression.compare(Regression.capture("v1", d, base), d, now).of(DiffKind.CHANGED).single()
        assertTrue(diff.changes.any { it.metric == "goal 2 progress" })
    }

    @Test fun changeLinesAreIndentedUnderTheRun() {
        val now = listOf(run("greedy", 1, netWorth = 1L), base[1], base[2])
        val lines = Regression.compare(Regression.capture("v1", d, base), d, now).lines()
        val at = lines.indexOf("greedy/1: changed")
        assertTrue(at > 0)
        assertTrue(lines[at + 1].startsWith("    "))
    }

    // ---- the file format -----------------------------------------------------------------------------

    @Test fun baselinesRoundTripThroughBytes() {
        val b = Regression.capture("v1", d, base)
        assertEquals(b, BaselineCodec.fromBytes(BaselineCodec.toBytes(b)))
    }

    @Test fun theBytesAreStable() {
        val b = Regression.capture("v1", d, base)
        assertTrue(BaselineCodec.toBytes(b).contentEquals(BaselineCodec.toBytes(Regression.capture("v1", d, base.reversed()))))
    }

    @Test fun damagedBaselinesAreRefused() {
        val bytes = BaselineCodec.toBytes(Regression.capture("v1", d, base))
        val flipped = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x55).toByte() }
        val wrongMagic = bytes.copyOf().also { it[0] = 0 }
        for (bad in listOf(bytes.copyOf(5), bytes.copyOf(bytes.size - 3), flipped, wrongMagic, ByteArray(0))) {
            try { BaselineCodec.fromBytes(bad); fail("expected refusal") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
        }
    }

    @Test fun metricChangesPrintWithOrWithoutAPercent() {
        assertEquals("cash 1 -> 2", MetricChange("cash", "1", "2", null).toString())
        assertEquals("cash 1 -> 2 (+12.34%)", MetricChange("cash", "1", "2", 123_456).toString())
    }

    @Test fun realRunsOfTheSameDraftCompareIdentical() {
        val h = WorkshopHarness(Samples.content)
        val settings = HarnessSettings(days = 12, sampleEvery = 12)
        val a = (h.run(d, PolicyKind.GREEDY, settings) as app.many.economytycoon.core.Outcome.Ok).value
        val b = (h.run(d, PolicyKind.GREEDY, settings) as app.many.economytycoon.core.Outcome.Ok).value
        val rep = Regression.compare(Regression.capture("real", d, listOf(a)), d, listOf(b))
        assertTrue(rep.lines().joinToString("\n"), rep.identical)
    }

    @Test fun aChangedRuleShowsUpAsAChangedRun() {
        val h = WorkshopHarness(Samples.content)
        val settings = HarnessSettings(days = 12, sampleEvery = 12)
        val a = (h.run(d, PolicyKind.IDLE, settings) as app.many.economytycoon.core.Outcome.Ok).value
        val changed = d.copy(events = listOf(ScriptedEvent(2, EventKind.GRANT, 50_000)))
        val b = (h.run(changed, PolicyKind.IDLE, settings) as app.many.economytycoon.core.Outcome.Ok).value
        val rep = Regression.compare(Regression.capture("real", d, listOf(a)), changed, listOf(b))
        assertTrue(rep.draftChanged)
        assertEquals(DiffKind.CHANGED, rep.diffs.single().kind)
        assertTrue(rep.diffs.single().changes.any { it.metric == "cash" })
    }
}
