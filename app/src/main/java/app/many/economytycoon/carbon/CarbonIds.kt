package app.many.economytycoon.carbon

/** Identifier of a bid placed for a permit auction. */
@JvmInline value class BidId(val value: Long) : Comparable<BidId> {
    override fun compareTo(other: BidId) = value.compareTo(other.value)
    override fun toString() = "Bid#$value"
}

/** Identifier of a resting order in a country's secondary permit book. */
@JvmInline value class PermitOrderId(val value: Long) : Comparable<PermitOrderId> {
    override fun compareTo(other: PermitOrderId) = value.compareTo(other.value)
    override fun toString() = "PermitOrder#$value"
}

/** Identifier of a carbon offset project. */
@JvmInline value class OffsetProjectId(val value: Long) : Comparable<OffsetProjectId> {
    override fun compareTo(other: OffsetProjectId) = value.compareTo(other.value)
    override fun toString() = "OffsetProject#$value"
}

/** Identifier of an abatement investment at a facility. */
@JvmInline value class AbatementId(val value: Long) : Comparable<AbatementId> {
    override fun compareTo(other: AbatementId) = value.compareTo(other.value)
    override fun toString() = "Abatement#$value"
}

/** Kinds of runtime entity the package numbers. Each has its own counter, so adding a kind never shifts another. */
enum class CarbonIdKind { BID, ORDER, OFFSET_PROJECT, ABATEMENT, SETTLEMENT }

/**
 * Deterministic counters for the package's entities. They are saved with the state, never reused, and independent of
 * the world-wide allocators so the package can be added to a running save.
 */
class CarbonCounters {
    private val next = java.util.EnumMap<CarbonIdKind, Long>(CarbonIdKind::class.java)

    private fun take(kind: CarbonIdKind): Long {
        val v = next[kind] ?: 1L
        next[kind] = v + 1L
        return v
    }

    fun bid() = BidId(take(CarbonIdKind.BID))
    fun order() = PermitOrderId(take(CarbonIdKind.ORDER))
    fun offsetProject() = OffsetProjectId(take(CarbonIdKind.OFFSET_PROJECT))
    fun abatement() = AbatementId(take(CarbonIdKind.ABATEMENT))

    /** A sequence number for ledger keys; unique across the whole package. */
    fun settlement(): Long = take(CarbonIdKind.SETTLEMENT)

    /** The value [take] would hand out next for a kind. */
    fun peek(kind: CarbonIdKind): Long = next[kind] ?: 1L

    fun restore(kind: CarbonIdKind, value: Long) {
        require(value >= 1L) { "counter must be positive" }
        next[kind] = value
    }

    fun reset() { next.clear() }
}
