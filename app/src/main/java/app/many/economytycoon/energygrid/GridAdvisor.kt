package app.many.economytycoon.energygrid

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** One suggestion about a grid. Priority runs from 0 to 100; higher is more worth doing soon. */
data class GridInsight(val code: String, val grid: GridId, val priority: Int, val title: String, val detail: String) {
    init { require(priority in 0..100) { "priority out of range" } }
}

/** What the advisor knows about one agreement on the grid. */
data class PpaFact(val id: PpaId, val shortfallKwh: Long, val daysLeft: Long, val live: Boolean)

/**
 * Everything the grid rules look at, as plain numbers, so a rule can be tried against any situation by writing the
 * numbers down. Ratios are parts per million, prices per megawatt-hour, energy in kilowatt-hours.
 */
data class GridFacts(
    val grid: GridId,
    val name: String,
    val reservePpm: Long = 300_000L,
    val targetReservePpm: Long = 150_000L,
    /** Hours of the last week with unserved load. */
    val lossOfLoadHours: Int = 0,
    /** Days since a blackout last ended or began; -1 when there has been none. */
    val daysSinceBlackout: Int = -1,
    val spikeDays: Int = 0,
    val averagePrice: Money = Money.units(40),
    val priceCap: Money = Money.units(500),
    val ppas: List<PpaFact> = emptyList(),
    val carbonIntensity: Long = 0L,
    val renewableSharePpm: Long = 0L,
    val curtailedSharePpm: Long = 0L,
    val storageKw: Long = 0L,
    /** Gap between the dearest and cheapest hours of a typical day. */
    val dailySpread: Money = Money.ZERO,
    val congestedLinkHours: Int = 0,
    val capShortfall: Money = Money.ZERO,
    val capacityShortfallKw: Long = 0L,
    val units: Int = 0,
    val agedUnits: Int = 0,
) {
    companion object {
        /** Reads the facts of [grid] from the state. [peakKw] is the recent peak demand. */
        fun of(state: EnergyGridState, grid: Grid, tick: Long, peakKw: Long): GridFacts {
            val week = grid.history.takeLast(7)
            val month = grid.history.takeLast(30)
            val lastBlackout = grid.blackouts.lastOrNull()
            val sinceBlackout = if (lastBlackout == null) -1 else (tick - (if (lastBlackout.open) tick else lastBlackout.endTick)).toInt().coerceAtLeast(0)
            val made = month.sumOf { it.totalGenerationKwh }
            val curtailed = month.sumOf { it.curtailedKwh }
            val live = grid.units.filter { it.isLive && it.status != UnitStatus.MOTHBALLED }
            val aged = live.count { u ->
                val life = u.tech.profile.lifetimeTicks.toLong()
                life > 0L && (tick - u.commissioned) * 5 >= life * 4
            }
            val spread = grid.lastDay.takeIf { it.isNotEmpty() }?.let { d -> d.maxOf { it.price } - d.minOf { it.price } } ?: Money.ZERO
            return GridFacts(
                grid = grid.id, name = grid.name,
                reservePpm = GridAnalytics.reserveMarginPpm(grid, peakKw), targetReservePpm = grid.params.targetReservePpm,
                lossOfLoadHours = week.sumOf { it.shedHours }, daysSinceBlackout = sinceBlackout,
                spikeDays = GridAnalytics.priceStats(month, 30, grid.params.priceCap)?.spikeDays ?: 0,
                averagePrice = GridAnalytics.windowAveragePrice(month, 30), priceCap = grid.params.priceCap,
                ppas = state.ppasOf(grid.id).filter { it.status.live }.map { PpaFact(it.id, it.shortfallKwh + it.totalShortfallKwh, it.endsAt - tick, true) },
                carbonIntensity = GridAnalytics.carbonIntensity(month, 30),
                renewableSharePpm = GridAnalytics.renewableShare(month.fold(LongArray(Technology.values().size)) { a, d -> for (i in a.indices) a[i] += d.generationKwh[i]; a }.toList()),
                curtailedSharePpm = if (made + curtailed > 0L) mulDiv(curtailed, Rate.ONE_PPM, made + curtailed, RoundingMode.HALF_EVEN) else 0L,
                storageKw = live.filter { it.tech == Technology.STORAGE }.sumOf { it.capacityKw }, dailySpread = spread,
                congestedLinkHours = state.linksOf(grid.id).sumOf { it.congestedHours }, capShortfall = grid.capShortfall,
                capacityShortfallKw = grid.capacityRound?.shortfallKw ?: 0L, units = live.size, agedUnits = aged,
            )
        }
    }
}

/** Pure rules over [GridFacts]. Each rule yields at most one insight; [advise] sorts them by urgency. */
class GridAdvisor(private val rules: List<(GridFacts) -> GridInsight?> = DEFAULT_RULES) {
    fun all(facts: GridFacts): List<GridInsight> =
        rules.mapNotNull { it(facts) }.sortedWith(compareByDescending<GridInsight> { it.priority }.thenBy { it.code })

    fun advise(facts: GridFacts, minPriority: Int = 20): List<GridInsight> = all(facts).filter { it.priority >= minPriority }

    companion object {
        const val SPIKE_DAYS_TO_WARN = 3
        const val OLD_FLEET_SHARE_PERCENT = 30
        const val STORAGE_SPREAD_RATIO_PERCENT = 150
        const val CARBON_HEAVY_KG_PER_MWH = 500L
        const val CURTAILED_WARN_PPM = 50_000L
        const val CONGESTED_WARN_HOURS = 48
        const val PPA_EXPIRY_DAYS = 30L

        private fun pct(a: Long, b: Long): Int = if (b <= 0L) 0 else mulDiv(a, 100L, b, RoundingMode.HALF_EVEN).toInt()

        private val lowReserve = { f: GridFacts ->
            if (f.reservePpm >= f.targetReservePpm) null else {
                val short = f.reservePpm < 0L
                GridInsight(
                    "low-reserve", f.grid, if (short) 90 else 60 + pct(f.targetReservePpm - f.reservePpm, f.targetReservePpm.coerceAtLeast(1L)).coerceAtMost(25),
                    if (short) "${f.name} cannot cover its peak" else "${f.name} is short of reserve",
                    "Firm capacity leaves ${f.reservePpm / 10_000L}% over the peak where ${f.targetReservePpm / 10_000L}% is wanted. New plant or a link would help.",
                )
            }
        }

        private val recentBlackout = { f: GridFacts ->
            if (f.lossOfLoadHours <= 0) null else GridInsight(
                "recent-blackout", f.grid, (70 + f.lossOfLoadHours).coerceAtMost(95),
                "${f.name} went short on ${f.lossOfLoadHours} hours this week",
                "Customers lost supply in the last seven days. Industrial and commercial sites are the ones to protect or to back up.",
            )
        }

        private val priceSpikes = { f: GridFacts ->
            if (f.spikeDays < SPIKE_DAYS_TO_WARN) null else GridInsight(
                "price-spikes", f.grid, (40 + f.spikeDays * 2).coerceAtMost(75),
                "${f.name} had ${f.spikeDays} days of scarcity prices",
                "Peak prices reached half the cap on ${f.spikeDays} of the last 30 days. A power purchase agreement would fix the price.",
            )
        }

        private val ppaShortfall = { f: GridFacts ->
            val short = f.ppas.filter { it.shortfallKwh > 0L }
            if (short.isEmpty()) null else GridInsight(
                "ppa-shortfall", f.grid, (55 + short.size * 5).coerceAtMost(80),
                "${short.size} agreements are under-delivering",
                "The seller is falling short of the contracted volume and owes penalties plus the cost of replacement energy.",
            )
        }

        private val ppaExpiry = { f: GridFacts ->
            val ending = f.ppas.filter { it.live && it.daysLeft in 0L..PPA_EXPIRY_DAYS }
            if (ending.isEmpty()) null else GridInsight(
                "ppa-expiry", f.grid, 35,
                "${ending.size} agreements end within a month",
                "Renew them or be ready to buy at the pool price.",
            )
        }

        private val carbonHeavy = { f: GridFacts ->
            if (f.carbonIntensity < CARBON_HEAVY_KG_PER_MWH) null else GridInsight(
                "carbon-heavy", f.grid, 30 + pct(f.carbonIntensity - CARBON_HEAVY_KG_PER_MWH, 500L).coerceAtMost(20),
                "${f.name} runs at ${f.carbonIntensity} g per kWh",
                "Fossil plant makes most of the energy. Wind, sun or nuclear would lower both the intensity and fuel exposure.",
            )
        }

        private val curtailment = { f: GridFacts ->
            if (f.curtailedSharePpm < CURTAILED_WARN_PPM) null else GridInsight(
                "curtailment", f.grid, 38,
                "${f.name} wastes ${f.curtailedSharePpm / 10_000L}% of its clean energy",
                "Output is switched off when supply exceeds demand. Storage or a link to a neighbour would put it to use.",
            )
        }

        private val storageChance = { f: GridFacts ->
            val ratio = pct(f.dailySpread.raw, f.averagePrice.raw.coerceAtLeast(1L))
            if (f.storageKw > 0L || ratio < STORAGE_SPREAD_RATIO_PERCENT) null else GridInsight(
                "storage-opportunity", f.grid, 32,
                "Batteries could earn in ${f.name}",
                "The daily price swing is ${ratio}% of the average price and nobody is storing energy to flatten it.",
            )
        }

        private val congestedLink = { f: GridFacts ->
            if (f.congestedLinkHours < CONGESTED_WARN_HOURS) null else GridInsight(
                "congested-link", f.grid, 36,
                "A link of ${f.name} was full for ${f.congestedLinkHours} hours",
                "More interconnector capacity would narrow the price gap with the neighbour.",
            )
        }

        private val capLoss = { f: GridFacts ->
            if (f.capShortfall.raw <= 0L) null else GridInsight(
                "cap-loss", f.grid, 45,
                "The household price cap costs the retailer",
                "Energy is sold to homes under cost and the gap is not made up. Review the cap or the compensation rule.",
            )
        }

        private val capacityGap = { f: GridFacts ->
            if (f.capacityShortfallKw <= 0L) null else GridInsight(
                "capacity-shortfall", f.grid, 58,
                "The capacity auction fell ${f.capacityShortfallKw / 1_000L} MW short",
                "Offered firm capacity did not meet the requirement. Higher payments or new plant are needed.",
            )
        }

        private val agingFleet = { f: GridFacts ->
            if (f.units <= 0 || f.agedUnits * 100 < f.units * OLD_FLEET_SHARE_PERCENT) null else GridInsight(
                "aging-fleet", f.grid, 42,
                "${f.agedUnits} of ${f.units} units near the end of life",
                "Plan replacements before the plant fails or is forced out.",
            )
        }

        val DEFAULT_RULES: List<(GridFacts) -> GridInsight?> = listOf(
            lowReserve, recentBlackout, priceSpikes, ppaShortfall, ppaExpiry, carbonHeavy, curtailment, storageChance,
            congestedLink, capLoss, capacityGap, agingFleet,
        )
    }
}
