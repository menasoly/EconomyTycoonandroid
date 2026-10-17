package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DraftCodecTest {
    private fun corrupt(block: () -> Unit) {
        try { block(); fail("expected the data to be refused") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
    }

    @Test fun everyFieldSurvivesARoundTrip() {
        val d = Samples.full()
        assertEquals(d, DraftCodec.fromBytes(DraftCodec.toBytes(d)))
    }

    @Test fun templatesAndBuiltInsRoundTrip() {
        for (d in DraftTemplates.all.map { it.create() } + DraftTemplates.builtIns()) assertEquals(d.id, d, DraftCodec.fromBytes(DraftCodec.toBytes(d)))
    }

    @Test fun unicodeAndControlCharactersSurvive() {
        val d = Samples.minimal().copy(title = "Café ☃ 漢字", story = "line\nbreak\ttab \u0001 end")
        assertEquals(d, DraftCodec.fromBytes(DraftCodec.toBytes(d)))
    }

    @Test fun extremeNumbersSurvive() {
        val d = Samples.minimal().copy(seed = Long.MIN_VALUE, company = Samples.minimal().company.copy(capital = Long.MAX_VALUE))
        assertEquals(d, DraftCodec.fromBytes(DraftCodec.toBytes(d)))
    }

    @Test fun theBytesAreTheSameEachTime() {
        assertTrue(DraftCodec.toBytes(Samples.full()).contentEquals(DraftCodec.toBytes(Samples.full())))
    }

    @Test fun anUnnormalisedDraftIsWrittenInCanonicalOrder() {
        val d = Samples.full()
        val shuffled = d.copy(events = d.events.reversed())
        assertTrue(DraftCodec.toBytes(d).contentEquals(DraftCodec.toBytes(shuffled)))
    }

    @Test fun theFirstByteIsTheVersion() {
        // zig-zag varint of 1 is 2
        assertEquals(2, DraftCodec.toBytes(Samples.minimal())[0].toInt())
    }

    @Test fun truncatedDataIsRefused() {
        val bytes = DraftCodec.toBytes(Samples.full())
        for (cut in listOf(0, 1, 5, bytes.size / 2, bytes.size - 1)) corrupt { DraftCodec.fromBytes(bytes.copyOf(cut)) }
    }

    @Test fun strayBytesAfterTheDraftAreRefused() {
        corrupt { DraftCodec.fromBytes(DraftCodec.toBytes(Samples.minimal()) + byteArrayOf(1)) }
    }

    @Test fun aVersionOfZeroIsRefused() {
        val w = ByteWriter(); w.writeVarInt(0); w.writeBytes(ByteArray(0))
        corrupt { DraftCodec.fromBytes(w.toByteArray()) }
    }

    @Test fun anUnknownEnumValueIsRefused() {
        val w = ByteWriter()
        val body = ByteWriter()
        body.writeString("id"); body.writeString("Title"); body.writeString(""); body.writeString("")
        body.writeVarLong(1); body.writeVarInt(99) // world size 99
        w.writeVarInt(1); w.writeBytes(body.toByteArray())
        corrupt { DraftCodec.fromBytes(w.toByteArray()) }
    }

    @Test fun implausibleCountsAreRefused() {
        val d = Samples.minimal()
        // A body whose starter count is far beyond the limit.
        val body = ByteWriter()
        body.writeString(d.id); body.writeString(d.title); body.writeString(""); body.writeString("")
        body.writeVarLong(d.seed); body.writeVarInt(0); body.writeVarInt(3); body.writeVarInt(0)
        body.writeString("Founders Ltd"); body.writeVarLong(5); body.writeVarInt(1000)
        val w = ByteWriter(); w.writeVarInt(1); w.writeBytes(body.toByteArray())
        corrupt { DraftCodec.fromBytes(w.toByteArray()) }
    }

    @Test fun aNewerVersionWithExtraFieldsStillOpens() {
        val d = Samples.full()
        val plain = DraftCodec.toBytes(d)
        // Rebuild with version 2 and appended bytes inside the block.
        val r = ByteReader(plain)
        r.readVarInt()
        val body = r.readBytes()
        val w = ByteWriter(); w.writeVarInt(2); w.writeBytes(body + byteArrayOf(9, 9, 9))
        assertEquals(d, DraftCodec.fromBytes(w.toByteArray()))
    }

    @Test fun differentDraftsGiveDifferentBytes() {
        assertTrue(!DraftCodec.toBytes(Samples.full()).contentEquals(DraftCodec.toBytes(Samples.minimal())))
    }
}
