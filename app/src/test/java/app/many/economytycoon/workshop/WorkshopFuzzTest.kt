package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Many different drafts, made from a fixed seed, pushed through every codec and the text form. */
class WorkshopFuzzTest {
    private val words = listOf("farm", "mill", "grain", "port", "bank", "iron", "salt", "wool", "rail", "tea", "harbour", "o'brien", "café", "\"quoted\"", "back\\slash", "100%", "a#b", "x // y", "tab\there", "line one\nline two")

    private fun text(r: Random, max: Int): String = (0 until r.nextInt(0, 4)).joinToString(" ") { words[r.nextInt(words.size)] }.take(max)

    private fun draft(r: Random, n: Int): ScenarioDraft {
        val goals = List(r.nextInt(1, 5)) { DraftGoal(GoalKind.values()[r.nextInt(GoalKind.values().size)], r.nextLong(1, 5_000_000), r.nextInt(1, 6), r.nextInt(4) == 0) }
        val events = List(r.nextInt(0, 6)) {
            val kind = EventKind.values()[r.nextInt(EventKind.values().size)]
            ScriptedEvent(r.nextInt(1, 1_000), kind, if (kind.hasAmount) r.nextLong(1, 400_000) else 0L, if (kind == EventKind.ANNOUNCE) text(r, 80).ifEmpty { "news" } else "")
        }
        var rules = RuleOverrides()
        for (k in RuleKey.values()) if (r.nextInt(3) == 0) rules = rules.with(k, r.nextLong(k.min, k.max + 1))
        return ScenarioDraft(
            id = "fuzz-$n", title = "Fuzz ${text(r, 30)} $n".trim(), tagline = text(r, 80), story = text(r, 300), seed = r.nextLong(),
            world = WorldSettings(WorldSize.values()[r.nextInt(WorldSize.values().size)], r.nextInt(0, 9), r.nextInt(0, 3)),
            company = CompanySetup(name = "Co ${text(r, 20)} $n".take(32), capital = r.nextLong(1_000, 3_000_000)),
            rules = rules, events = events, goals = goals,
            fails = List(r.nextInt(0, 3)) { FailCondition(FailKind.values()[r.nextInt(FailKind.values().size)], r.nextLong(0, 100_000), r.nextInt(0, 400)) },
            timing = Timing(r.nextInt(1, 20), r.nextInt(100, 1_500), r.nextInt(100, 3_000)),
            tags = DifficultyTag.values().filter { r.nextInt(4) == 0 },
            hints = List(r.nextInt(0, 4)) { text(r, 100).ifEmpty { "hint" } },
            packs = List(r.nextInt(0, 3)) { "pack${r.nextInt(5)}:${r.nextInt(3)}.${r.nextInt(9)}" },
        ).normalised()
    }

    private val drafts: List<ScenarioDraft> = Random(20240607).let { r -> List(60) { draft(r, it) } }

    @Test fun binaryRoundTrip() {
        for (d in drafts) assertEquals(d.id, d, DraftCodec.fromBytes(DraftCodec.toBytes(d)))
    }

    @Test fun shareTextRoundTrip() {
        for (d in drafts) assertEquals(d.id, d, (ShareCodec.decode(ShareCodec.encode(d)) as ShareResult.Decoded).draft)
    }

    @Test fun writtenFormRoundTrip() {
        for (d in drafts) {
            val parse = DraftParser.parse(DraftPrinter.print(d))
            assertTrue("${d.id}: ${parse.errors}", parse.ok)
            assertEquals(d.id, d, parse.draft)
        }
    }

    @Test fun printingIsAFixedPoint() {
        for (d in drafts) {
            val once = DraftPrinter.print(d)
            assertEquals(d.id, once, DraftPrinter.print(requireNotNull(DraftParser.parse(once).draft)))
        }
    }

    @Test fun hashesAreStableAndDistinct() {
        val hashes = drafts.map { it.stateHash() }
        assertEquals(hashes, drafts.map { DraftCodec.fromBytes(DraftCodec.toBytes(it)).stateHash() })
        assertEquals(drafts.size, hashes.distinct().size)
    }

    @Test fun shareTextSurvivesRetyping() {
        val r = Random(7)
        for (d in drafts.take(20)) {
            val code = ShareCodec.encode(d)
            val messy = code.lowercase().replace("-", if (r.nextBoolean()) " " else "").replace("\n", "\r\n  ")
            val result = ShareCodec.decode("here you go:\n\n  \"$messy\"")
            assertEquals(d.id, d, result.draftOrNull())
        }
    }

    @Test fun anySingleCorruptionIsCaughtOrHarmless() {
        val r = Random(11)
        for (d in drafts.take(15)) {
            val code = ShareCodec.encode(d)
            repeat(12) {
                val at = r.nextInt(code.length)
                val c = code[at]
                if (c !in "0123456789ABCDEFGHJKMNPQRSTVWXYZ") return@repeat
                val swap = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".filter { it != c }.let { it[r.nextInt(it.length)] }
                val broken = code.substring(0, at) + swap + code.substring(at + 1)
                val res = ShareCodec.decode(broken)
                // Either refused with a position, or (when only the optional line check was hit) the very same draft.
                if (res is ShareResult.Decoded) assertEquals(d, res.draft) else assertTrue((res as ShareResult.Failed).error.at.line >= 1)
            }
        }
    }

    @Test fun truncatedBinaryNeverEscapesAsAnotherException() {
        for (d in drafts.take(10)) {
            val bytes = DraftCodec.toBytes(d)
            for (cut in listOf(0, 1, 2, bytes.size / 3, bytes.size / 2, bytes.size - 1)) {
                try { DraftCodec.fromBytes(bytes.copyOf(cut)); org.junit.Assert.fail("cut at $cut was accepted") } catch (e: ByteReader.CorruptDataException) { continue }
            }
        }
    }

    @Test fun editorUndoReturnsToTheStartAfterManyEdits() {
        val r = Random(3)
        val start = drafts[0]
        val editor = DraftEditor(start, historyLimit = 500)
        repeat(80) {
            when (r.nextInt(5)) {
                0 -> editor.apply(DraftEdit.SetSeed(r.nextLong()))
                1 -> editor.apply(DraftEdit.SetCapital(r.nextLong(1, 9_000_000)))
                2 -> editor.apply(DraftEdit.AddGoal(DraftGoal(GoalKind.CASH, r.nextLong(1, 99_999))))
                3 -> editor.apply(DraftEdit.AddEvent(ScriptedEvent(r.nextInt(1, 900), EventKind.GRANT, 1_000)))
                else -> editor.apply(DraftEdit.SetTitle("Title ${r.nextInt(1000)}"))
            }
        }
        val end = editor.draft
        while (editor.canUndo) editor.undo()
        assertEquals(start, editor.draft)
        while (editor.canRedo) editor.redo()
        assertEquals(end, editor.draft)
    }

    @Test fun validationNeverThrowsOnRandomDrafts() {
        for (d in drafts) assertNotNull(DraftValidator.validate(d, Samples.content).summary())
    }

    @Test fun diffOfADraftWithItselfIsEmptyAndWithAnotherIsNot() {
        for (i in 0 until drafts.size - 1) {
            assertTrue(DraftDiff.changes(drafts[i], drafts[i]).isEmpty())
            assertTrue(DraftDiff.changes(drafts[i], drafts[i + 1]).isNotEmpty())
        }
    }

    @Test fun bundlesOfRandomDraftsRoundTrip() {
        val some = drafts.take(10)
        assertEquals(some, ShareBundle.decode(ShareBundle.encode(some)).drafts)
    }

    @Test fun conversionIsAPureFunctionOfTheDraft() {
        for (d in drafts.take(10)) {
            val a = app.many.economytycoon.workshop.DraftConverter.config(d)
            val b = app.many.economytycoon.workshop.DraftConverter.config(DraftCodec.fromBytes(DraftCodec.toBytes(d)))
            assertEquals(a, b)
        }
    }
}
