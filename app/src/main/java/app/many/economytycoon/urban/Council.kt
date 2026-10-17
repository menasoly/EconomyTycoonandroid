package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Tick

/** A member of the city council. Ideology runs from -500 (protect what exists) to +500 (let the city grow). */
class CouncilSeat(
    val index: Int,
    val name: String,
    ideology: Int,
    /** The district this member represents. */
    val ward: Int,
    /** Closeness to builders and landlords, permille. */
    val developerTies: Int,
    /** Resistance to lobbying, permille. */
    val integrity: Int,
) {
    var ideology: Int = ideology
        internal set
    /** Lobbying points working on this member for the petition they are weighing, by petition id. */
    internal val pressure = java.util.TreeMap<PetitionId, Int>()

    fun pressureFor(p: PetitionId): Int = pressure[p] ?: 0

    fun hashInto(h: CanonicalHasher) {
        h.putInt(index).putString(name).putInt(ideology).putInt(ward).putInt(developerTies).putInt(integrity)
        h.putInt(pressure.size)
        for ((k, v) in pressure) h.putLong(k.value).putInt(v)
    }
}

/** How the neighbourhood feels about a petition, permille of residents. */
data class PublicOpinion(val supportPermille: Int, val oppositionPermille: Int) {
    val netPermille: Int get() = supportPermille - oppositionPermille
}

/**
 * The city council: an odd number of members, each with a ward, who vote on petitions. Public anger raised by
 * displacement weighs on every vote to build more, and elections pull the council toward the mood of the voters.
 */
class Council(val seats: List<CouncilSeat>) {
    init { require(seats.size % 2 == 1) { "a council has an odd number of seats" } }

    /** Anger among voters about growth, permille; fed by backlash and fading over time. */
    var moodAgainstGrowth: Int = 0
        internal set
    var lastElection: Tick = Tick(0)
        internal set
    var elections: Int = 0
        internal set

    val majority: Int get() = seats.size / 2 + 1
    val supermajority: Int get() = (seats.size * 2 + 2) / 3

    fun seat(i: Int): CouncilSeat = seats[i]

    /** Mean ideology of the council, a quick read of how friendly it is to building. */
    fun meanIdeology(): Int = seats.sumOf { it.ideology } / seats.size

    /** Seats that would favour growth in the abstract. */
    fun growthSeats(): Int = seats.count { it.ideology > 0 }

    /**
     * Lets the mood drift toward the anger the districts feel. Changes slowly: a quarter of the gap each month.
     */
    fun updateMood(meanBacklashPpm: Long) {
        val target = (meanBacklashPpm / 1000L).toInt().coerceIn(0, 1000)
        moodAgainstGrowth += (target - moodAgainstGrowth) / 4
    }

    /**
     * An election: each member is replaced with probability one in three by someone new, and the rest move toward the mood
     * of the voters. All random draws are taken from [random] in seat order, always the same number per seat.
     */
    fun holdElection(now: Tick, random: DeterministicRandom) {
        val voterLean = (200 - moodAgainstGrowth).coerceIn(-500, 500)
        for (s in seats) {
            val replace = random.nextInt(3) == 0
            val fresh = random.nextInRange(-400L, 400L).toInt()
            s.ideology = if (replace) fresh else (s.ideology + (voterLean - s.ideology) / 3).coerceIn(-500, 500)
            s.pressure.clear()
        }
        lastElection = now
        elections++
    }

    internal fun restore(mood: Int, lastElection: Tick, elections: Int) {
        moodAgainstGrowth = mood; this.lastElection = lastElection; this.elections = elections
    }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(seats.size)
        for (s in seats) s.hashInto(h)
        h.putInt(moodAgainstGrowth).putLong(lastElection.value).putInt(elections)
    }

    companion object {
        private val SURNAMES = listOf(
            "Alder", "Brandt", "Castell", "Dumont", "Ellery", "Farrow", "Garza", "Hollis", "Ivanek", "Jansen", "Kovac", "Lindqvist",
            "Marlowe", "Novak", "Okafor", "Pereira", "Quill", "Rasmussen", "Santoro", "Thorne", "Ueda", "Varga", "Whitlock", "Xu", "Yilmaz",
        )

        /** A fresh council with seats spread over the districts; ideologies and ties are drawn from [random]. */
        fun seeded(size: Int, districtCount: Int, random: DeterministicRandom): Council {
            val seats = (0 until size).map { i ->
                CouncilSeat(
                    index = i,
                    name = SURNAMES[i % SURNAMES.size],
                    ideology = random.nextInRange(-400L, 400L).toInt(),
                    ward = i * districtCount / size,
                    developerTies = random.nextInRange(100L, 800L).toInt(),
                    integrity = random.nextInRange(300L, 900L).toInt(),
                )
            }
            return Council(seats)
        }
    }
}
