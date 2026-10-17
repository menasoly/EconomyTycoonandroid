package app.many.economytycoon.blueprint

import app.many.economytycoon.blueprint.BlueprintTestSupport as T
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.persistence.MemorySaveStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingApplyTest {
    private fun choice(id: Long, name: String, type: FacilityTypeId = T.bakery) = FacilityChoice(FacilityId(id), type, name, "Bakery", "R", 1, true)
    private fun hash(b: PendingApplyBook) = CanonicalHasher().also { b.hashInto(it) }.finish().toHex()

    @Test fun addAssignsIdsAndKeepsKnownFacilitiesSorted() {
        val b = PendingApplyBook()
        val p = b.add(T.sample(), T.region, "North", 5, listOf(FacilityId(9), FacilityId(2)))!!
        assertEquals(1L, p.id)
        assertEquals(listOf(2L, 9L), p.knownFacilities)
        assertEquals(T.bakery, p.type)
        assertEquals(2L, b.add(T.sample(), T.region, "South", 6, emptyList())!!.id)
    }

    @Test fun readyMatchesByNameTypeAndNewness() {
        val b = PendingApplyBook()
        b.add(T.sample(), T.region, "North", 5, listOf(FacilityId(1)))
        val facilities = listOf(choice(1, "North"), choice(2, "South"), choice(3, "North", FacilityTypeId("farm")), choice(4, "North"))
        val ready = b.ready(facilities)
        assertEquals(listOf(FacilityId(4)), ready.map { it.facility })
    }

    @Test fun oneFacilityFulfilsOnlyOneRequest() {
        val b = PendingApplyBook()
        b.add(T.sample(), T.region, "North", 5, emptyList())
        b.add(T.sample("Other"), T.region, "North", 6, emptyList())
        val ready = b.ready(listOf(choice(8, "North")))
        assertEquals(1, ready.size)
        assertEquals(1L, ready.single().pending.id)
        assertEquals(2, b.ready(listOf(choice(8, "North"), choice(9, "North"))).size)
    }

    @Test fun capacityAndRemoval() {
        val b = PendingApplyBook(2)
        b.add(T.sample(), T.region, "A", 1, emptyList()); b.add(T.sample(), T.region, "B", 1, emptyList())
        assertNull(b.add(T.sample(), T.region, "C", 1, emptyList()))
        assertTrue(b.remove(1))
        assertFalse(b.remove(1))
        assertEquals(1, b.size)
    }

    @Test fun expiryDropsOldOnes() {
        val b = PendingApplyBook()
        b.add(T.sample(), T.region, "Old", 1, emptyList()); b.add(T.sample(), T.region, "New", 300, emptyList())
        val gone = b.expire(day = 380)
        assertEquals(listOf("Old"), gone.map { it.facilityName })
        assertEquals(listOf("New"), b.all().map { it.facilityName })
    }

    @Test fun optionsRoundTripThroughTheRequest() {
        val b = PendingApplyBook()
        val p = b.add(T.sample(), T.region, "N", 1, emptyList(), ApplyOptions(staffing = false, storage = false))!!
        val o = p.options()
        assertFalse(o.staffing); assertTrue(o.trading); assertFalse(o.storage); assertTrue(o.schedule)
    }

    @Test fun bytesRoundTrip() {
        val b = PendingApplyBook()
        b.add(T.sample(), RegionId(4), "North", 5, listOf(FacilityId(3)), ApplyOptions(trading = false))
        b.add(T.sample("Second").copy(spec = T.sampleSpec().copy(qualityPpm = 1L)), T.region, "South", 6, emptyList())
        val back = PendingApplyBook.fromBytes(b.toBytes())
        assertEquals(b.all(), back.all())
        assertEquals(hash(b), hash(back))
        assertEquals(3L, back.add(T.sample(), T.region, "Third", 7, emptyList())!!.id)
    }

    @Test fun unreadableBytesGiveAnEmptyBook() {
        assertEquals(0, PendingApplyBook.fromBytes(null).size)
        assertEquals(0, PendingApplyBook.fromBytes(ByteArray(0)).size)
        assertEquals(0, PendingApplyBook.fromBytes(ByteWriter().also { it.writeVarInt(9) }.toByteArray()).size)
        val bytes = PendingApplyBook().also { it.add(T.sample(), T.region, "N", 1, emptyList()) }.toBytes()
        assertEquals(0, PendingApplyBook.fromBytes(bytes.copyOf(bytes.size / 2)).size)
    }

    @Test fun storeSavesAndResets() {
        val mem = MemorySaveStore()
        val store = PendingApplyStore(mem)
        val b = PendingApplyBook().also { it.add(T.sample(), T.region, "N", 1, emptyList()) }
        store.save(b)
        assertEquals(1, store.load().size)
        store.reset()
        assertEquals(0, store.load().size)
    }
}
