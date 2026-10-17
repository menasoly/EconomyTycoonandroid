package app.many.economytycoon.urban

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.isqrt

/** One payment made to push a petition. The record is kept even for failed campaigns: it can come back as a scandal. */
data class LobbyEffort(
    val petition: PetitionId,
    val company: CompanyId,
    /** The member targeted, or null for a campaign aimed at the whole council. */
    val seat: Int?,
    val amount: Money,
    val points: Int,
    val at: Tick,
)

/**
 * Lets other parts of the game add to or subtract from a member's inclination on a petition, for example a company's
 * standing with the city or a rival's counter-campaign. The council asks every hook on every vote.
 */
fun interface LobbyingHook {
    /** Extra points (positive favours the petition) for [seat] on [petition]; zero for no opinion. */
    fun influence(petition: Petition, seat: CouncilSeat, now: Tick): Int
}

object NoLobbying : LobbyingHook {
    override fun influence(petition: Petition, seat: CouncilSeat, now: Tick) = 0
}

/** The rules of lobbying: what money buys and when it becomes a scandal. */
object Lobbying {
    /** Points bought by a payment: grows with the square root of the amount, so the second million buys less. */
    fun points(amount: Money): Int = (isqrt(amount.wholeUnits().coerceAtLeast(0L) / 50L) * 8L).toInt()

    /** Points that reach one member from an effort: all of it if targeted, a fifth of it if aimed at the whole council. */
    fun pointsFor(effort: LobbyEffort, seat: Int): Int = when (effort.seat) {
        null -> effort.points / 5
        seat -> effort.points
        else -> 0
    }

    /** Effective pull on a member: honest members are harder to move, and ties to builders make them easier. */
    fun effective(points: Int, seat: CouncilSeat): Int = points * (1000 - seat.integrity + seat.developerTies / 2) / 1000

    /**
     * Chance, ppm, that the campaign comes out as a scandal at the vote: grows with spending relative to the legal cap and
     * with how many honest members were approached.
     */
    fun scandalRiskPpm(total: Money, cap: Money, meanIntegrity: Int): Long {
        if (cap.raw <= 0L || total.raw <= 0L) return 0L
        val spent = (total.raw * 1_000L / cap.raw).coerceIn(0L, 2_000L)
        if (spent < 400L) return 0L
        return ((spent - 400L) * meanIntegrity / 1000L * 500L).coerceIn(0L, 900_000L)
    }
}
