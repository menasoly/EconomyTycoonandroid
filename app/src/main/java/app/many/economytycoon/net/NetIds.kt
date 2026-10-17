package app.many.economytycoon.net

/** A participant in a multiplayer session. Positive for people; the same number is used as the simulation's player actor id. */
@JvmInline
value class PlayerId(val value: Long) : Comparable<PlayerId> {
    override fun compareTo(other: PlayerId): Int = value.compareTo(other.value)
    override fun toString() = "P$value"

    companion object {
        /** Stands for the server itself in committed data, never a person. */
        val SERVER = PlayerId(0L)
    }
}

/** One hosted game. Part of every signature so a message cannot be replayed into another session. */
@JvmInline
value class SessionId(val value: Long) {
    override fun toString() = "S$value"
}

/** An address on a [Transport]; the transport decides what it stands for. */
@JvmInline
value class EndpointId(val value: Int) : Comparable<EndpointId> {
    override fun compareTo(other: EndpointId): Int = value.compareTo(other.value)
    override fun toString() = "E$value"
}

/** A trade offer held in escrow. */
@JvmInline
value class TradeId(val value: Long) : Comparable<TradeId> {
    override fun compareTo(other: TradeId): Int = value.compareTo(other.value)
    override fun toString() = "T$value"
}

/** Whether a member takes part in the economy or only watches it. */
enum class Role { PLAYER, SPECTATOR }
