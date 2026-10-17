package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CouncilTest {
    private fun council(size: Int = 9, seed: Long = 4L) = Council.seeded(size, 9, DeterministicRandom(seed))

    @Test fun `a council has an odd number of members`() {
        try { Council(emptyList()); fail() } catch (e: IllegalArgumentException) { }
        try { council(8); fail() } catch (e: IllegalArgumentException) { }
        assertEquals(9, council().seats.size)
    }

    @Test fun `majority and supermajority follow the size`() {
        val c = council(9)
        assertEquals(5, c.majority)
        assertEquals(6, c.supermajority)
        assertEquals(2, council(3).majority)
        assertEquals(2, council(3).supermajority)
        assertEquals(13, council(25).majority)
        assertEquals(17, council(25).supermajority)
    }

    @Test fun `seeded members are spread over the wards with bounded traits`() {
        val c = council()
        assertEquals((0 until 9).toList(), c.seats.map { it.ward })
        for (s in c.seats) {
            assertTrue(s.ideology in -400..400)
            assertTrue(s.developerTies in 100..800)
            assertTrue(s.integrity in 300..900)
        }
        assertEquals(9, c.seats.map { it.name }.toSet().size)
    }

    @Test fun `seeding is deterministic and seed dependent`() {
        assertEquals(council(9, 3).seats.map { it.ideology }, council(9, 3).seats.map { it.ideology })
        assertTrue(council(9, 3).seats.map { it.ideology } != council(9, 4).seats.map { it.ideology })
    }

    @Test fun `wards map into the districts for a small council`() {
        val c = Council.seeded(3, 9, DeterministicRandom(1))
        assertEquals(listOf(0, 3, 6), c.seats.map { it.ward })
    }

    @Test fun `mood drifts a quarter of the way to the anger of the districts`() {
        val c = council()
        c.updateMood(800_000L)
        assertEquals(200, c.moodAgainstGrowth)
        c.updateMood(800_000L)
        assertEquals(350, c.moodAgainstGrowth)
        repeat(60) { c.updateMood(800_000L) }
        assertTrue(c.moodAgainstGrowth in 790..800)
        repeat(100) { c.updateMood(0L) }
        assertTrue(c.moodAgainstGrowth < 5)
        c.updateMood(9_000_000L)
        assertTrue(c.moodAgainstGrowth <= 1000)
    }

    @Test fun `mean ideology and growth seats summarise the chamber`() {
        val c = council()
        assertEquals(c.seats.count { it.ideology > 0 }, c.growthSeats())
        assertEquals(c.seats.sumOf { it.ideology } / 9, c.meanIdeology())
    }

    @Test fun `an election moves members toward the voters and clears pressure`() {
        val c = council()
        val id = PetitionId(1)
        c.seats[0].pressure[id] = 40
        val rnd = DeterministicRandom(9)
        c.holdElection(Tick(1440), rnd)
        assertEquals(1, c.elections)
        assertEquals(Tick(1440), c.lastElection)
        assertEquals(0, c.seats[0].pressureFor(id))
        for (s in c.seats) assertTrue(s.ideology in -500..500)
    }

    @Test fun `an election draws a fixed number of random values`() {
        val a = DeterministicRandom(21); val b = DeterministicRandom(21)
        council().holdElection(Tick(1), a)
        repeat(9 * 2) { b.nextLong() }
        // each draw consumes exactly one raw value so the streams line up afterwards
        assertEquals(b.nextLong(), a.nextLong())
    }

    @Test fun `angry voters push the council against growth over elections`() {
        val calm = council(); val angry = council()
        angry.updateMood(1_000_000L); repeat(30) { angry.updateMood(1_000_000L) }
        repeat(6) { calm.holdElection(Tick(it.toLong()), DeterministicRandom(it.toLong())) }
        repeat(6) { angry.holdElection(Tick(it.toLong()), DeterministicRandom(it.toLong())) }
        assertTrue(angry.meanIdeology() < calm.meanIdeology())
    }

    @Test fun `public opinion nets support against opposition`() {
        assertEquals(150, PublicOpinion(400, 250).netPermille)
        assertEquals(-300, PublicOpinion(100, 400).netPermille)
    }

    @Test fun `hash covers seats and mood`() {
        fun h(c: Council) = CanonicalHasher().also { c.hashInto(it) }.finish()
        val a = council(); val b = council()
        assertEquals(h(a), h(b))
        b.updateMood(500_000L)
        assertTrue(h(a) != h(b))
        val c = council()
        c.seats[2].pressure[PetitionId(5)] = 3
        assertTrue(h(a) != h(c))
    }

    @Test fun `restore sets the chamber's own state`() {
        val c = council()
        c.restore(300, Tick(77), 4)
        assertEquals(300, c.moodAgainstGrowth)
        assertEquals(Tick(77), c.lastElection)
        assertEquals(4, c.elections)
    }
}
