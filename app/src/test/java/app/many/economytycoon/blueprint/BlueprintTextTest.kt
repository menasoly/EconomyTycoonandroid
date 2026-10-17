package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintTextTest {
    private val bp = T.sample()
    private fun success(r: ParseResult) = r as ParseResult.Success
    private fun failure(r: ParseResult) = r as ParseResult.Failure

    @Test fun roundTripBothEncodings() {
        for (enc in TextEncoding.values()) {
            val text = BlueprintText.encode(bp, enc)
            assertTrue(text.startsWith("ETBP1-${enc.tag}:"))
            val r = success(BlueprintText.parse(text))
            assertEquals(bp, r.blueprint)
            assertEquals(enc, r.encoding)
        }
    }

    @Test fun base64IsShorterThanBase32() {
        assertTrue(BlueprintText.encode(bp, TextEncoding.BASE64).length < BlueprintText.encode(bp, TextEncoding.BASE32).length)
    }

    @Test fun encodingIsDeterministic() {
        assertEquals(BlueprintText.encode(bp), BlueprintText.encode(T.sample()))
    }

    @Test fun wrappedTextParsesAndHasShortLines() {
        val text = BlueprintText.encode(bp, wrap = 40)
        assertTrue(text.lines().all { it.length <= 48 })
        assertTrue(text.lines().size > 3)
        assertEquals(bp, success(BlueprintText.parse(text)).blueprint)
    }

    @Test fun sharedTextIsToleratedInChatNoise() {
        val code = BlueprintText.encode(bp, wrap = 32)
        val msg = "hey, try my bakery set-up!\n\n$code\n\nlet me know :)"
        assertEquals(bp, success(BlueprintText.parse(msg)).blueprint)
    }

    @Test fun caseSpacesAndDashesAreTolerated() {
        val code = BlueprintText.encode(bp)
        val head = code.substringBefore(':') + ":"
        val body = code.substring(head.length).substringBeforeLast(':')
        val sum = code.substringAfterLast(':')
        val mangled = head.lowercase() + body.lowercase().chunked(5).joinToString(" - ") + ":" + sum.lowercase()
        assertEquals(bp, success(BlueprintText.parse(mangled)).blueprint)
    }

    @Test fun lookAlikeLettersAreForgiven() {
        val code = BlueprintText.encode(bp)
        val head = code.substringBefore(':') + ":"
        val swapped = head + code.substring(head.length).replace('0', 'O').replace('1', 'l')
        assertEquals(bp, success(BlueprintText.parse(swapped)).blueprint)
    }

    @Test fun invisibleCharactersFromCopyingAreIgnored() {
        val code = BlueprintText.encode(bp)
        val cut = code.indexOf(':') + 10
        val noisy = code.substring(0, cut) + "​ " + code.substring(cut)
        assertEquals(bp, success(BlueprintText.parse(noisy)).blueprint)
    }

    @Test fun noHeaderAndEmptyTextAreDistinguished() {
        assertEquals(ParseErrorCode.EMPTY, failure(BlueprintText.parse("   \n")).error.code)
        assertEquals(ParseErrorCode.NO_HEADER, failure(BlueprintText.parse("hello world")).error.code)
    }

    @Test fun futureTextVersionIsReported() {
        val e = failure(BlueprintText.parse("ETBP2-B32:ABCD:0000000")).error
        assertEquals(ParseErrorCode.UNSUPPORTED_VERSION, e.code)
        assertTrue(e.message.contains("version 2"))
    }

    @Test fun unknownEncodingPointsAtTheTag() {
        val e = failure(BlueprintText.parse("ETBP1-B99:ABCD:0000000")).error
        assertEquals(ParseErrorCode.UNKNOWN_ENCODING, e.code)
        assertEquals(6, e.offset)
    }

    @Test fun badCharacterIsLocatedExactly() {
        val code = BlueprintText.encode(bp, wrap = 20)
        val at = code.indexOf('\n') + 1 + 3
        val broken = code.substring(0, at) + "#" + code.substring(at + 1)
        val e = failure(BlueprintText.parse(broken)).error
        assertEquals(ParseErrorCode.BAD_CHARACTER, e.code)
        assertEquals(at, e.offset)
        assertEquals(2, e.line)
        assertEquals(4, e.column)
        assertTrue(e.where == "line 2, column 4")
    }

    @Test fun letterUIsNotInTheAlphabet() {
        val e = failure(BlueprintText.parse("ETBP1-B32:ABUD:0000000")).error
        assertEquals(ParseErrorCode.BAD_CHARACTER, e.code)
        assertEquals(12, e.offset)
    }

    @Test fun missingEndAndChecksumAreReported() {
        val code = BlueprintText.encode(bp)
        assertEquals(ParseErrorCode.MISSING_PAYLOAD_END, failure(BlueprintText.parse(code.substringBeforeLast(':'))).error.code)
        assertEquals(ParseErrorCode.MISSING_CHECKSUM, failure(BlueprintText.parse(code.dropLast(3))).error.code)
        assertEquals(ParseErrorCode.BAD_LENGTH, failure(BlueprintText.parse("ETBP1-B32::ABCDEFG")).error.code)
    }

    @Test fun corruptedPayloadFailsTheChecksum() {
        val code = BlueprintText.encode(bp)
        val at = code.indexOf(':') + 12
        val flipped = if (code[at] == 'A') 'B' else 'A'
        val broken = code.substring(0, at) + flipped + code.substring(at + 1)
        val e = failure(BlueprintText.parse(broken)).error
        assertEquals(ParseErrorCode.CHECKSUM_MISMATCH, e.code)
        assertTrue(e.message.contains("expected"))
    }

    @Test fun truncatedPayloadIsCaught() {
        val code = BlueprintText.encode(bp)
        val body = code.substringAfter(':').substringBeforeLast(':')
        val sum = code.substringAfterLast(':')
        val e = failure(BlueprintText.parse("ETBP1-B32:" + body.dropLast(2) + ":" + sum)).error
        assertTrue(e.code == ParseErrorCode.CHECKSUM_MISMATCH || e.code == ParseErrorCode.BAD_LENGTH)
    }

    @Test fun validChecksumOverNonsenseIsCorrupt() {
        val junk = ByteArray(30) { (it * 7).toByte() }
        val text = "ETBP1-B32:${BlueprintText.base32(junk)}:${BlueprintText.checksum(junk)}"
        assertEquals(ParseErrorCode.CORRUPT, failure(BlueprintText.parse(text)).error.code)
    }

    @Test fun oversizedPayloadIsRefused() {
        val big = ByteArray(BlueprintText.MAX_PAYLOAD_BYTES + 1)
        val text = "ETBP1-B32:${BlueprintText.base32(big)}:${BlueprintText.checksum(big)}"
        assertEquals(ParseErrorCode.TOO_LARGE, failure(BlueprintText.parse(text)).error.code)
    }

    @Test fun manyBlocksAreFoundInOrder() {
        val b2 = T.sample("Second").copy(spec = T.sampleSpec().copy(qualityPpm = 100_000L))
        val text = "first:\n${BlueprintText.encode(bp)}\nthen\n${BlueprintText.encode(b2, TextEncoding.BASE64)}\nand a broken one ETBP1-B32:12$:0000000\n"
        val all = BlueprintText.parseAll(text)
        assertEquals(3, all.size)
        assertEquals("Corner bakery", success(all[0]).blueprint.name)
        assertEquals("Second", success(all[1]).blueprint.name)
        assertTrue(all[2] is ParseResult.Failure)
        assertTrue(all[0].end <= all[1].start)
        assertTrue(all[1].end <= all[2].start)
    }

    @Test fun migrationNotesTravelWithTheResult() {
        // a schema 1 payload wrapped in a valid text block
        val w = app.many.economytycoon.core.ByteWriter()
        w.writeVarInt(1); w.writeString("Old"); w.writeString(""); w.writeString(""); w.writeVarLong(0)
        w.writeString("bakery"); w.writeVarInt(0); w.writeVarLong(50)
        repeat(4) { w.writeVarInt(0) }
        val bytes = w.toByteArray()
        val r = success(BlueprintText.parse("ETBP1-B32:${BlueprintText.base32(bytes)}:${BlueprintText.checksum(bytes)}"))
        assertEquals(500_000L, r.blueprint.spec.qualityPpm)
        assertFalse(r.notes.isEmpty())
    }

    @Test fun base32AndBase64PrimitivesRoundTrip() {
        for (n in 0..40) {
            val b = ByteArray(n) { (it * 31 + n).toByte() }
            assertTrue(BlueprintText.unbase32(BlueprintText.base32(b)).contentEquals(b))
            assertTrue(BlueprintText.unbase64(BlueprintText.base64(b)).contentEquals(b))
        }
    }

    @Test fun checksumIsStable() {
        assertEquals("0000000", BlueprintText.checksum(ByteArray(0)))
        assertEquals(7, BlueprintText.checksum(byteArrayOf(1, 2, 3)).length)
        assertTrue(BlueprintText.checksum(byteArrayOf(1, 2, 3)) != BlueprintText.checksum(byteArrayOf(1, 2, 4)))
    }

    @Test fun goldenCodeForTheSampleStaysTheSame() {
        // If this fails the shared format changed; bump TextEncoding / SCHEMA and add a migration instead of editing the value.
        assertEquals(GOLDEN, BlueprintText.encode(bp))
    }

    companion object { const val GOLDEN = "ETBP1-B32:0GD46VVJDSJQ4832C5NPAWKS69A6GWK5CMG6RTBECNSJR831ENT6Y83DC5S6ETBE5R878SBKEH032BHM30664RBBCNS7J0P0SWJ041G0040H8VB1DDJNYSKCDXTQ4002040H8VB1DDJNYRKJCNGP80G8DXV6AVG4040HRVB1DDJNYWVFENS68VVNCXM082KDD5W6AWG8DXV6AVG419H62TV5E860384PG00W16GC19HPRSBJDC10000219H74SB1CG0A1386R2W040E0K86C16GC0410MXV8CNGQ9072160BX4G104:0MNW518" }
}
