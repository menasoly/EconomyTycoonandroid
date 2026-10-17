package app.many.economytycoon.energygrid

/** Identifier of one regional electricity grid. */
@JvmInline value class GridId(val value: Long) : Comparable<GridId> {
    override fun compareTo(other: GridId) = value.compareTo(other.value)
    override fun toString() = "Grid#$value"
}

/** Identifier of a generation or storage unit. */
@JvmInline value class UnitId(val value: Long) : Comparable<UnitId> {
    override fun compareTo(other: UnitId) = value.compareTo(other.value)
    override fun toString() = "Unit#$value"
}

/** Identifier of a metered load connected to a grid. */
@JvmInline value class LoadId(val value: Long) : Comparable<LoadId> {
    override fun compareTo(other: LoadId) = value.compareTo(other.value)
    override fun toString() = "Load#$value"
}

/** Identifier of a power purchase agreement. */
@JvmInline value class PpaId(val value: Long) : Comparable<PpaId> {
    override fun compareTo(other: PpaId) = value.compareTo(other.value)
    override fun toString() = "Ppa#$value"
}

/** Identifier of an interconnector between two grids. */
@JvmInline value class LinkId(val value: Long) : Comparable<LinkId> {
    override fun compareTo(other: LinkId) = value.compareTo(other.value)
    override fun toString() = "Link#$value"
}

/** Identifier of a recorded blackout. */
@JvmInline value class BlackoutId(val value: Long) : Comparable<BlackoutId> {
    override fun compareTo(other: BlackoutId) = value.compareTo(other.value)
    override fun toString() = "Blackout#$value"
}

/**
 * Next-id counters of the energy grid module. They live in the saved state so that ids are never reused after a
 * load, and adding an entity kind never shifts the ids of another.
 */
class GridCounters(
    var nextGrid: Long = 1L,
    var nextUnit: Long = 1L,
    var nextLoad: Long = 1L,
    var nextPpa: Long = 1L,
    var nextLink: Long = 1L,
    var nextBlackout: Long = 1L,
) {
    fun grid() = GridId(nextGrid++)
    fun unit() = UnitId(nextUnit++)
    fun load() = LoadId(nextLoad++)
    fun ppa() = PpaId(nextPpa++)
    fun link() = LinkId(nextLink++)
    fun blackout() = BlackoutId(nextBlackout++)
}
