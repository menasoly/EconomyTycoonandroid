package app.many.economytycoon.energygrid

import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

enum class OutageKind(val label: String) { FORCED("Forced outage"), MAINTENANCE("Maintenance"), DEFERRED("Maintenance postponed") }

/** A unit going out of service, or maintenance being put off, on a given day. */
data class OutageEvent(val grid: GridId, val unit: UnitId, val kind: OutageKind, val start: Long, val until: Long) {
    val days: Long get() = until - start
}

data class OutageParams(
    /** Maintenance may be postponed this many times in a row while the grid is tight. */
    val maxDeferrals: Int = 4,
    val deferTicks: Int = 7,
    /** A grid whose spare firm capacity is below this share of the peak does not release units for maintenance. */
    val tightReservePpm: Long = 150_000L,
    /** Efficiency a thermal unit loses each day it runs, in ppm of its efficiency; maintenance wins it back. */
    val wearPpmPerDay: Long = 20L,
    val maxForcedChancePpm: Long = 100_000L,
)

/** The rules for when units fail and when they go in for maintenance. Randomness is passed in, never created. */
object OutageModel {
    /**
     * Chance of a forced outage today, in ppm: the technology's base rate scaled by the weather's risk and by age, which
     * doubles the rate at the end of the unit's life.
     */
    fun forcedChancePpm(tech: Technology, ageTicks: Long, riskPpm: Long, params: OutageParams = OutageParams()): Long {
        val life = tech.profile.lifetimeTicks.toLong()
        val ageFactor = Rate.ONE_PPM + mulDiv(ageTicks.coerceIn(0L, life), Rate.ONE_PPM, life, RoundingMode.DOWN)
        val weathered = mulDiv(tech.profile.forcedOutagePpmPerDay, riskPpm, Rate.ONE_PPM, RoundingMode.HALF_EVEN)
        return mulDiv(weathered, ageFactor, Rate.ONE_PPM, RoundingMode.HALF_EVEN).coerceAtMost(params.maxForcedChancePpm)
    }

    /**
     * Rolls today's forced outage for one unit. Exactly two draws are taken whatever the answer, so one unit failing does
     * not shift the draws of the units after it. Returns the length of the outage in days, or 0 when nothing happens.
     */
    fun rollForced(chancePpm: Long, tech: Technology, rnd: DeterministicRandom): Int {
        val hit = rnd.nextLong(Rate.ONE_PPM) < chancePpm
        val span = rnd.nextInt(tech.profile.forcedOutageTicks.coerceAtLeast(1))
        return if (hit) (tech.profile.forcedOutageTicks / 2 + span).coerceAtLeast(1) else 0
    }

    fun maintenanceDue(tech: Technology, lastMaintenance: Long, tick: Long): Boolean = tick - lastMaintenance >= tech.profile.maintenanceEveryTicks

    /**
     * Whether a unit that is due may go in for maintenance now. [spareFirmKw] is the firm capacity the grid would have
     * above its peak without this unit; the unit goes when that stays above the tight-reserve line, or when it has
     * already been postponed as many times as allowed.
     */
    fun mayStartMaintenance(spareFirmKw: Long, peakKw: Long, deferrals: Int, params: OutageParams = OutageParams()): Boolean {
        if (deferrals >= params.maxDeferrals) return true
        val line = mulDiv(peakKw, params.tightReservePpm, Rate.ONE_PPM, RoundingMode.UP)
        return spareFirmKw >= line
    }
}
