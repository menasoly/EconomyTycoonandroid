package app.many.economytycoon.achievements

/** Things worth counting across every game a player has ever played. */
enum class Stat(val label: String) {
    DAYS_PLAYED("Days played"),
    GAMES_STARTED("Games started"),
    SCENARIOS_WON("Scenarios won"),
    SCENARIOS_LOST("Scenarios lost"),
    TENDERS_WON("Tenders won"),
    TENDERS_DELIVERED("Tenders delivered"),
    PARCELS_SOLD("Parcels sold"),
    LEASES_SIGNED("Leases signed"),
    FUTURES_OPENED("Futures positions opened"),
    FUTURES_LIQUIDATED("Futures positions liquidated"),
    PUBLIC_OFFERINGS("Public offerings"),
    STRIKES_SUFFERED("Strikes suffered"),
}

/** Running totals for each [Stat]. Totals only grow. */
class LifetimeStats {
    private val totals = LongArray(Stat.values().size)

    operator fun get(stat: Stat): Long = totals[stat.ordinal]

    fun add(stat: Stat, amount: Long = 1L) {
        require(amount >= 0L) { "totals only grow" }
        totals[stat.ordinal] += amount
    }

    /** Stats with a non-zero total, in declaration order. */
    fun nonZero(): List<Pair<Stat, Long>> = Stat.values().mapNotNull { s -> totals[s.ordinal].takeIf { it > 0L }?.let { s to it } }

    internal fun put(stat: Stat, value: Long) { totals[stat.ordinal] = value.coerceAtLeast(0L) }

    override fun equals(other: Any?): Boolean = other is LifetimeStats && other.totals.contentEquals(totals)
    override fun hashCode(): Int = totals.contentHashCode()
}
