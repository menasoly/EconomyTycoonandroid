package app.many.economytycoon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BinaryAndHashTest {
    @Test fun varLongRoundTripsEdgeValues() {
        val values = longArrayOf(0, 1, -1, 63, 64, -64, -65, 127, 128, 300, Long.MAX_VALUE, Long.MIN_VALUE)
        val w = ByteWriter()
        for (v in values) w.writeVarLong(v)
        val r = ByteReader(w.toByteArray())
        for (v in values) assertEquals(v, r.readVarLong())
        assertEquals(0, r.remaining)
    }

    @Test fun mixedFieldsRoundTrip() {
        val w = ByteWriter()
        w.writeString("héllo ✓")
        w.writeBoolean(true)
        w.writeMoney(Money(-12345))
        w.writeQuantity(Quantity(999))
        w.writeRate(Rate.percent(12))
        w.writeFixedLong(0x0102030405060708L)
        w.writeBytes(byteArrayOf(1, 2, 3))
        val r = ByteReader(w.toByteArray())
        assertEquals("héllo ✓", r.readString())
        assertEquals(true, r.readBoolean())
        assertEquals(Money(-12345), r.readMoney())
        assertEquals(Quantity(999), r.readQuantity())
        assertEquals(Rate.percent(12), r.readRate())
        assertEquals(0x0102030405060708L, r.readFixedLong())
        assertEquals(listOf<Byte>(1, 2, 3), r.readBytes().toList())
    }

    @Test fun truncatedDataIsReportedAsCorrupt() {
        val w = ByteWriter()
        w.writeString("abcdef")
        val bytes = w.toByteArray().copyOf(3)
        assertThrows(ByteReader.CorruptDataException::class.java) { ByteReader(bytes).readString() }
    }

    @Test fun hashIsStableAndSensitive() {
        fun h(a: Long, b: String) = CanonicalHasher().section("s").putLong(a).putString(b).finish()
        assertEquals(h(1, "x"), h(1, "x"))
        assertNotEquals(h(1, "x"), h(2, "x"))
        assertNotEquals(h(1, "x"), h(1, "y"))
    }

    @Test fun hashSeparatesConcatenations() {
        val a = CanonicalHasher().putString("ab").putString("c").finish()
        val b = CanonicalHasher().putString("a").putString("bc").finish()
        assertNotEquals(a, b)
    }

    @Test fun hexRoundTrip() {
        val h = CanonicalHasher().putLong(5).finish()
        assertEquals(h, StateHash.fromHex(h.toHex()))
        assertEquals(64, h.toHex().length)
    }

    @Test fun ringBufferOverwritesOldest() {
        val ring = RingBuffer<Int>(3)
        for (i in 1..5) ring.add(i)
        assertEquals(listOf(3, 4, 5), ring.toList())
        assertEquals(5, ring.last())
        assertEquals(3, ring.first())
    }

    @Test fun sortedRegistryIteratesInKeyOrder() {
        val reg = SortedRegistry<CompanyId, String>()
        reg[CompanyId(5)] = "e"; reg[CompanyId(1)] = "a"; reg[CompanyId(3)] = "c"
        assertEquals(listOf("a", "c", "e"), reg.values.toList())
        assertEquals(CompanyId(1), reg.firstKey())
        assertEquals(CompanyId(5), reg.lastKey())
    }

    @Test fun idAllocatorsAreIndependentAndRestorable() {
        val a = IdAllocators()
        assertEquals(CompanyId(1), a.company())
        assertEquals(CompanyId(2), a.company())
        assertEquals(FacilityId(1), a.facility())
        val snap = a.snapshot()
        val b = IdAllocators()
        b.restore(snap)
        assertEquals(CompanyId(3), b.company())
        assertEquals(FacilityId(2), b.facility())
    }
}

class CalendarTest {
    @Test fun dateConversionRoundTrips() {
        for (t in listOf(0L, 1, 29, 30, 359, 360, 1000, 5000)) {
            val d = Calendar.date(Tick(t))
            assertEquals(Tick(t), Calendar.tickOf(d))
        }
    }

    @Test fun yearAndMonthBoundaries() {
        assertEquals(GameDate(2000, 1, 1), Calendar.date(Tick(0)))
        assertEquals(GameDate(2000, 2, 1), Calendar.date(Tick(30)))
        assertEquals(GameDate(2001, 1, 1), Calendar.date(Tick(360)))
        assertEquals(true, Calendar.isYearStart(Tick(720)))
        assertEquals(false, Calendar.isMonthStart(Tick(31)))
    }

    @Test fun seasonsFollowMonths() {
        assertEquals(Season.WINTER, Calendar.season(Tick(0)))
        assertEquals(Season.SPRING, Calendar.season(Tick(60)))
        assertEquals(Season.SUMMER, Calendar.season(Tick(150)))
        assertEquals(Season.AUTUMN, Calendar.season(Tick(250)))
    }

    @Test fun outcomeMapsAndRejects() {
        val ok = Outcome.ok(2).map { it * 3 }
        assertEquals(6, ok.getOrNull())
        val rejected: Outcome<Int> = Outcome.reject(Rules.INSUFFICIENT_FUNDS, "no cash")
        assertEquals(Rules.INSUFFICIENT_FUNDS, rejected.rejectionOrNull()?.rule)
    }
}
