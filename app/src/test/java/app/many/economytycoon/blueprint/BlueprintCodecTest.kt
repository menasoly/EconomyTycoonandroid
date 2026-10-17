package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.ByteWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintCodecTest {
    private fun ok(d: Decoded) = d as Decoded.Ok

    /** The schema 1 layout, kept here so the migration stays tested after the writer moved on. */
    private fun legacyV1(bp: Blueprint, qualityPercent: Long): ByteArray {
        val w = ByteWriter()
        val s = bp.spec
        w.writeVarInt(1)
        w.writeString(bp.name); w.writeString(bp.note); w.writeString(bp.contentTag); w.writeVarLong(bp.createdDay)
        w.writeString(s.facilityType.key); w.writeVarInt(s.policy.ordinal); w.writeVarLong(qualityPercent)
        w.writeVarInt(s.lines.size)
        for (l in s.lines) {
            w.writeVarInt(l.priority); w.writeBoolean(l.enabled); w.writeBoolean(l.recipe != null); l.recipe?.let { w.writeString(it.key) }
            w.writeVarInt(l.machines.size); l.machines.forEach { w.writeString(it.key) }
        }
        w.writeVarInt(s.staff.size)
        for (t in s.staff) { w.writeString(t.role.key); w.writeVarLong(t.target); w.writeBoolean(t.wage != null); t.wage?.let { w.writeMoney(it) } }
        w.writeVarInt(s.sells.size)
        for (x in s.sells) {
            w.writeString(x.good.key); w.writeVarInt(x.strategy.ordinal); w.writeVarLong(x.markupPpm); w.writeQuantity(x.reserve)
            w.writeBoolean(x.maxPerOrder != null); x.maxPerOrder?.let { w.writeQuantity(it) }; w.writeBoolean(x.enabled)
        }
        w.writeVarInt(s.buys.size)
        for (b in s.buys) { w.writeString(b.good.key); w.writeQuantity(b.target); w.writeVarLong(b.maxPriceMultiplePpm); w.writeBoolean(b.enabled) }
        return w.toByteArray()
    }

    @Test fun roundTripKeepsEverything() {
        val bp = T.sample()
        val d = ok(BlueprintCodec.decode(BlueprintCodec.encode(bp)))
        assertEquals(bp, d.blueprint)
        assertEquals(bp.hash, d.blueprint.hash)
        assertEquals(BlueprintCodec.SCHEMA, d.schema)
        assertTrue(d.notes.isEmpty())
    }

    @Test fun encodingIsDeterministicAndCanonical() {
        val a = BlueprintCodec.encode(T.sample())
        val b = BlueprintCodec.encode(T.sample())
        assertTrue(a.contentEquals(b))
        // an unnormalised spec with the same meaning encodes identically
        val s = T.sampleSpec()
        val shuffled = Blueprint(s.copy(staff = s.staff.reversed()), "Corner bakery", "Three lines, auto margin.", "test@1.4", 12L)
        assertTrue(a.contentEquals(BlueprintCodec.encode(shuffled)))
    }

    @Test fun minimalBlueprintRoundTrips() {
        val bp = Blueprint.of(BlueprintSpec(T.bakery, emptyList()), "Empty")
        assertEquals(bp, ok(BlueprintCodec.decode(BlueprintCodec.encode(bp))).blueprint)
    }

    @Test fun schemaOneIsMigratedWithNotes() {
        val bp = T.sample()
        val d = ok(BlueprintCodec.decode(legacyV1(bp, 30)))
        assertEquals(1, d.schema)
        assertEquals(300_000L, d.blueprint.spec.qualityPpm)
        assertEquals(0, d.blueprint.spec.storageExpansions)
        assertTrue(d.blueprint.spec.staff.all { it.trainingPpm == 0L })
        assertTrue(d.blueprint.spec.sells.all { it.minMarginPpm == 0L })
        assertEquals(bp.spec.lines, d.blueprint.spec.lines)
        assertEquals(4, d.notes.size)
        assertTrue(d.notes[0].contains("whole percent"))
    }

    @Test fun migratedBlueprintReEncodesAtTheCurrentSchema() {
        val d = ok(BlueprintCodec.decode(legacyV1(T.sample(), 30)))
        val again = ok(BlueprintCodec.decode(BlueprintCodec.encode(d.blueprint)))
        assertEquals(BlueprintCodec.SCHEMA, again.schema)
        assertEquals(d.blueprint, again.blueprint)
    }

    @Test fun newerSchemaIsRefusedPolitely() {
        val w = ByteWriter(); w.writeVarInt(BlueprintCodec.SCHEMA + 1)
        val f = BlueprintCodec.decode(w.toByteArray()) as Decoded.Failed
        assertTrue(f.message.contains("newer version"))
    }

    @Test fun truncatedAndGarbageDataFailsWithoutThrowing() {
        val bytes = BlueprintCodec.encode(T.sample())
        for (cut in listOf(0, 1, 5, bytes.size / 2, bytes.size - 1)) {
            assertTrue("cut $cut", BlueprintCodec.decode(bytes.copyOf(cut)) is Decoded.Failed)
        }
        assertTrue(BlueprintCodec.decode(ByteArray(40) { 0x7F }) is Decoded.Failed)
        val zero = ByteWriter(); zero.writeVarInt(0)
        assertTrue(BlueprintCodec.decode(zero.toByteArray()) is Decoded.Failed)
    }

    @Test fun implausibleCountsAreRejected() {
        val w = ByteWriter()
        w.writeVarInt(2); w.writeString("n"); w.writeString(""); w.writeString(""); w.writeVarLong(0)
        w.writeString("bakery"); w.writeVarInt(0); w.writeVarLong(0); w.writeVarInt(0)
        w.writeVarInt(5_000)
        val f = BlueprintCodec.decode(w.toByteArray()) as Decoded.Failed
        assertTrue(f.message.contains("implausible"))
    }

    @Test fun invalidValuesAreReportedAsInvalid() {
        val w = ByteWriter()
        w.writeVarInt(2); w.writeString("  "); w.writeString(""); w.writeString(""); w.writeVarLong(0)
        w.writeString("bakery"); w.writeVarInt(0); w.writeVarLong(0); w.writeVarInt(0)
        w.writeVarInt(0); w.writeVarInt(0); w.writeVarInt(0); w.writeVarInt(0)
        val f = BlueprintCodec.decode(w.toByteArray()) as Decoded.Failed
        assertTrue(f.message.contains("not valid"))
    }

    @Test fun unknownEnumOrdinalsAreCorruption() {
        val w = ByteWriter()
        w.writeVarInt(2); w.writeString("n"); w.writeString(""); w.writeString(""); w.writeVarLong(0)
        w.writeString("bakery"); w.writeVarInt(9)
        assertTrue(BlueprintCodec.decode(w.toByteArray()) is Decoded.Failed)
    }
}
