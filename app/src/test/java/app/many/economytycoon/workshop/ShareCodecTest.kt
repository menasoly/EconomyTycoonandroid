package app.many.economytycoon.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ShareCodecTest {
    private fun decoded(text: String): ShareResult.Decoded {
        val r = ShareCodec.decode(text)
        assertTrue("expected a decode but got $r", r is ShareResult.Decoded)
        return r as ShareResult.Decoded
    }

    private fun failed(text: String): ShareError {
        val r = ShareCodec.decode(text)
        assertTrue("expected a failure but got $r", r is ShareResult.Failed)
        return (r as ShareResult.Failed).error
    }

    @Test fun aDraftSurvivesAShare() {
        val d = Samples.full()
        assertEquals(d, decoded(ShareCodec.encode(d)).draft)
    }

    @Test fun everyTemplateAndBuiltInSurvives() {
        for (d in DraftTemplates.all.map { it.create() } + DraftTemplates.builtIns()) assertEquals(d.id, d, decoded(ShareCodec.encode(d)).draft)
    }

    @Test fun theCodeStartsWithTheMarkerAndVersion() {
        assertTrue(ShareCodec.encode(Samples.minimal()).startsWith("ETW1-"))
    }

    @Test fun theCodeUsesOnlyTheAlphabetDashesColonsAndNewlines() {
        val text = ShareCodec.encode(Samples.full()).removePrefix("ETW1-")
        assertTrue(text.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ-:\n" })
    }

    @Test fun linesAreAtMostEightGroupsAndEndWithACheckSymbol() {
        val lines = ShareCodec.encode(Samples.full()).lines()
        assertTrue(lines.size >= 2)
        for (l in lines) {
            assertTrue(l, l.matches(Regex("(ETW1-)?([0-9A-Z]{1,5}-){0,7}[0-9A-Z]{1,5}:[0-9A-Z]")))
        }
    }

    @Test fun compressionKeepsTheCodeShortForRepetitiveDrafts() {
        val big = Samples.full().copy(story = "word ".repeat(100).trim(), hints = List(10) { "A hint that repeats itself again and again." })
        val raw = DraftCodec.toBytes(big).size
        val code = ShareCodec.encode(big).filter { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" }.length - 1
        assertTrue("code $code symbols for $raw bytes", code * 5 / 8 < raw)
        assertEquals(big, decoded(ShareCodec.encode(big)).draft)
    }

    @Test fun theSameDraftAlwaysGivesTheSameCode() {
        assertEquals(ShareCodec.encode(Samples.full()), ShareCodec.encode(Samples.full()))
        assertTrue(ShareCodec.encode(Samples.full()) != ShareCodec.encode(Samples.minimal()))
    }

    // ---- tolerance ------------------------------------------------------------------------------

    @Test fun caseSpacingAndDashesDoNotMatter() {
        val d = Samples.full()
        val code = ShareCodec.encode(d)
        assertEquals(d, decoded(code.lowercase()).draft)
        assertEquals(d, decoded(code.replace("-", " ")).draft)
        assertEquals(d, decoded(code.replace("-", "")).draft)
    }

    @Test fun lookAlikeLettersAreRead() {
        val d = Samples.minimal()
        val code = ShareCodec.encode(d)
        val swapped = code.removePrefix("ETW1-").replace('0', 'O').replace('1', 'l')
        assertEquals(d, decoded("ETW1-$swapped").draft)
    }

    @Test fun textAroundTheCodeIsIgnored() {
        val d = Samples.full()
        val msg = "Hey, try my scenario! Here it is:\n\n${ShareCodec.encode(d)}\n\nTell me how it goes."
        assertEquals(d, decoded(msg).draft)
    }

    @Test fun quotesAndBackticksAroundTheCodeAreIgnored() {
        val d = Samples.minimal()
        assertEquals(d, decoded("`${ShareCodec.encode(d)}`").draft)
        assertEquals(d, decoded("\"${ShareCodec.encode(d)}\"").draft)
    }

    @Test fun windowsLineEndingsAreAccepted() {
        val d = Samples.full()
        assertEquals(d, decoded(ShareCodec.encode(d).replace("\n", "\r\n")).draft)
    }

    @Test fun missingLineChecksAreAcceptedWithAWarning() {
        val d = Samples.full()
        val bare = ShareCodec.encode(d).lines().joinToString("\n") { it.substringBefore(':') }
        val r = decoded(bare)
        assertEquals(d, r.draft)
        assertEquals(1, r.warnings.size)
        assertTrue(r.warnings[0], r.warnings[0].contains("no check symbol"))
    }

    @Test fun aCleanCodeHasNoWarnings() {
        assertEquals(emptyList<String>(), decoded(ShareCodec.encode(Samples.full())).warnings)
    }

    // ---- errors with positions -----------------------------------------------------------------

    @Test fun textWithoutTheMarkerIsNotACode() {
        val e = failed("hello world")
        assertEquals(TextPosition(1, 1), e.at)
        assertTrue(e.message, e.message.contains("ETW1-"))
    }

    @Test fun aNewerVersionIsNamedAsSuch() {
        val e = failed("ETW9-ABCDE")
        assertTrue(e.message, e.message.contains("newer version"))
        assertEquals(1, e.at.line)
        assertEquals(4, e.at.column)
    }

    @Test fun anIllegalCharacterIsPointedAt() {
        val code = ShareCodec.encode(Samples.full()).lines()
        val broken = (listOf(code[0].replaceFirst("-", "-#")) + code.drop(1)).joinToString("\n")
        // '#' is not part of the alphabet
        val e = failed(broken.replace("#", "!"))
        assertEquals(1, e.at.line)
        assertTrue(e.message, e.message.contains("'!'"))
        assertEquals(6, e.at.column)
    }

    @Test fun aMistypedLineIsFoundByItsCheckSymbol() {
        val lines = ShareCodec.encode(Samples.full()).lines().toMutableList()
        assertTrue(lines.size >= 3)
        val l = lines[1]
        val idx = l.indexOfFirst { it.isLetterOrDigit() }
        val other = if (l[idx] == 'A') 'B' else 'A'
        lines[1] = l.substring(0, idx) + other + l.substring(idx + 1)
        val e = failed(lines.joinToString("\n"))
        assertEquals(2, e.at.line)
        assertTrue(e.message, e.message.contains("line 2"))
        assertEquals(lines[1].indexOf(':') + 2, e.at.column)
    }

    @Test fun aCutOffCodeFailsTheChecksum() {
        val lines = ShareCodec.encode(Samples.full()).lines()
        val e = failed(lines.dropLast(1).joinToString("\n"))
        assertTrue(e.message, e.message.contains("checksum") || e.message.contains("damaged") || e.message.contains("short"))
    }

    @Test fun changingACharacterWithoutLineChecksFailsTheChecksum() {
        val bare = ShareCodec.encode(Samples.full()).lines().joinToString("\n") { it.substringBefore(':') }
        val i = bare.indexOf('-') + 2
        val other = if (bare[i] == 'A') 'B' else 'A'
        val e = failed(bare.substring(0, i) + other + bare.substring(i + 1))
        assertTrue(e.message, e.message.contains("checksum") || e.message.contains("damaged"))
    }

    @Test fun aMissingCheckSymbolAfterTheColonIsReported() {
        val e = failed("ETW1-ABCDE:")
        assertTrue(e.message, e.message.contains("check symbol"))
        assertEquals(1, e.at.line)
    }

    @Test fun noDataAfterTheVersionIsReported() {
        val e = failed("ETW1-")
        assertTrue(e.message, e.message.contains("no data") || e.message.contains("too short"))
    }

    @Test fun aBlankLineEndsTheCode() {
        val d = Samples.full()
        val text = ShareCodec.encode(d) + "\n\nThanks! (this part has non-code characters: ###)"
        assertEquals(d, decoded(text).draft)
    }

    @Test fun errorsReadAsLineAndColumn() {
        assertEquals("line 3, column 9", TextPosition(3, 9).toString())
        assertEquals("line 1, column 1: nope", ShareError(TextPosition(1, 1), "nope").toString())
    }

    @Test fun decodedDraftOrNullIsHandy() {
        assertNotNull(ShareCodec.decode(ShareCodec.encode(Samples.minimal())).draftOrNull())
        assertFalse(ShareCodec.decode("nothing here").draftOrNull() != null)
    }

    @Test fun aDamagedDraftInsideAValidWrapperIsReported() {
        // Wrap garbage with a correct checksum by encoding bytes that are not a draft.
        val garbage = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val crc = java.util.zip.CRC32().also { it.update(garbage) }.value
        val all = garbage + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        val sb = StringBuilder()
        var buf = 0; var bits = 0
        for (b in all) { buf = (buf shl 8) or (b.toInt() and 0xFF); bits += 8; while (bits >= 5) { sb.append(alphabet[(buf ushr (bits - 5)) and 31]); bits -= 5 }; buf = buf and ((1 shl bits) - 1) }
        if (bits > 0) sb.append(alphabet[(buf shl (5 - bits)) and 31])
        val e = failed("ETW1-$sb")
        assertTrue(e.message, e.message.contains("damaged"))
    }
}
