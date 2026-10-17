package app.many.economytycoon.urban

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanIdsTest {
    @Test fun `each kind counts on its own`() {
        val ids = UrbanIds()
        val a = ids.district()
        val b = ids.district()
        val p = ids.petition()
        assertEquals(b.value, a.value + 1)
        assertEquals(a.value, p.value)
        assertTrue(a < b)
    }

    @Test fun `a snapshot restores the counters`() {
        val ids = UrbanIds()
        repeat(3) { ids.district() }
        repeat(2) { ids.line() }
        ids.event()
        val copy = UrbanIds()
        copy.restore(ids.snapshot())
        assertEquals(ids.district(), copy.district())
        assertEquals(ids.line(), copy.line())
        assertEquals(ids.petition(), copy.petition())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a snapshot of the wrong size is refused`() {
        UrbanIds().restore(longArrayOf(1, 2, 3))
    }

    @Test fun `ids print with their kind`() {
        assertEquals("District#4", DistrictId(4).toString())
        assertEquals("Concession#9", ConcessionId(9).toString())
        assertEquals("TransitLine#2", TransitLineId(2).toString())
    }

    @Test fun `ids order by value`() {
        assertEquals(listOf(PetitionId(1), PetitionId(5), PetitionId(9)), listOf(PetitionId(9), PetitionId(1), PetitionId(5)).sorted())
    }
}
