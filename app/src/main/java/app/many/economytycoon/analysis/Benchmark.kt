package app.many.economytycoon.analysis

import app.many.economytycoon.core.CompanyId

/** One company's value for one measure, with who it belongs to. */
data class PeerValue(val company: CompanyId, val name: String, val value: Long)

/** How a company stands among its peers on one measure. */
data class Standing(
    val measure: Measure,
    val value: Long,
    val peerMedian: Long,
    val best: Long,
    /** Share of peers this company is at or ahead of, in ppm, already flipped for measures where lower is better. */
    val rankPpm: Long,
    val peers: Int,
) {
    val ahead: Boolean get() = rankPpm >= 500_000L
    val quartile: Int get() = when {
        rankPpm >= 750_000L -> 1
        rankPpm >= 500_000L -> 2
        rankPpm >= 250_000L -> 3
        else -> 4
    }
}

object Benchmark {
    /** Needs at least [MIN_PEERS] others to say anything. */
    const val MIN_PEERS = 3

    fun standing(measure: Measure, self: Long, peers: List<Long>): Standing? {
        if (peers.size < MIN_PEERS) return null
        val raw = Series.rankPpm(peers, self)
        val higher = measure.band.direction == Direction.HIGHER_IS_BETTER
        val rank = if (higher) raw else 1_000_000L - raw
        val best = if (higher) peers.max() else peers.min()
        return Standing(measure, self, Series.median(peers), best, rank, peers.size)
    }

    /** Standing on every measure the company and enough peers share. */
    fun compare(self: Map<Measure, Long>, peers: List<Map<Measure, Long>>): List<Standing> =
        Measure.values().mapNotNull { m ->
            val mine = self[m] ?: return@mapNotNull null
            standing(m, mine, peers.mapNotNull { it[m] })
        }

    /** The measures where the company trails its peers most, worst first. */
    fun laggards(standings: List<Standing>, limit: Int = 3): List<Standing> =
        standings.filter { it.quartile >= 3 }.sortedBy { it.rankPpm }.take(limit)

    fun leaders(standings: List<Standing>, limit: Int = 3): List<Standing> =
        standings.filter { it.quartile == 1 }.sortedByDescending { it.rankPpm }.take(limit)
}
