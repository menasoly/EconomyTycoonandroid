package app.many.economytycoon.net

import app.many.economytycoon.core.Outcome

/** One seated participant: the company they run and the name it was founded under. */
class SeatResult(val player: PlayerId, val company: Long, val name: String?)

/**
 * Gives each player their company when a game starts. The server and every client run exactly this routine over
 * the same seat list, so all copies of the game begin identical; a name that is already taken falls back to the
 * next candidate in a fixed order.
 */
object Seating {
    fun candidates(name: String, player: PlayerId): List<String> = listOf(name, "$name Co", "Company ${player.value}")

    fun seat(port: SimulationPort, seats: List<SeatAssignment>): List<SeatResult> = seats.map { seat ->
        var result = SeatResult(seat.player, -1L, null)
        for (name in candidates(seat.name, seat.player)) {
            val r = port.seat(seat.player, name)
            if (r is Outcome.Ok) { result = SeatResult(seat.player, r.value, name); break }
        }
        result
    }

    /** The seat list a client derives from the lobby it last saw: the players in slot order. */
    fun fromMembers(members: List<MemberInfo>): List<SeatAssignment> =
        members.filter { it.role == Role.PLAYER && it.slot >= 0 }.sortedBy { it.slot }.map { SeatAssignment(it.slot, it.id, it.name) }
}
