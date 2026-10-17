package app.many.economytycoon.tourism

import app.many.economytycoon.core.Season
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.world.WeatherKind

/** One driver's contribution to the appeal of a destination, kept so screens can show where the score comes from. */
data class DriverScore(val label: String, val valuePpm: Long, val weight: Long)

/**
 * Everything about a destination on a given stay date that is not a lasting attribute: effective drivers (public
 * works plus attractions), the event, safety and weather conditions of the date, and a per-night levy that makes the
 * place feel dearer.
 */
data class AppealContext(
    val season: Season,
    val drivers: DestinationDrivers,
    /** 0..1,000,000 strength of a festival or conference drawing people on the date. */
    val eventPpm: Long = 0L,
    /** Drop in perceived safety from scares and strikes, 0..1,000,000. */
    val safetyDropPpm: Long = 0L,
    /** Drop in the pleasantness of the weather from storms and heatwaves, 0..1,000,000. */
    val weatherPenaltyPpm: Long = 0L,
    /** Extra price level from taxes that tourists feel, ppm of the reference price. */
    val taxPriceAddPpm: Long = 0L,
)

/** The appeal of one destination for one segment on one date. */
data class Appeal(
    val segment: VisitorSegment,
    /** Weighted driver score before price, 0..1,000,000 (marketing can push it up to 1,200,000). */
    val scorePpm: Long,
    /** 1,000,000 at the reference price; lower when the destination is dear. */
    val priceFactorPpm: Long,
    val parts: List<DriverScore>,
) {
    /** Score after price: what visitors weigh against other destinations. */
    val valuePpm: Long get() = mulDiv(scorePpm, priceFactorPpm, 1_000_000L)
}

object Attractiveness {
    const val MIN_PRICE_FACTOR_PPM = 250_000L
    const val MAX_PRICE_FACTOR_PPM = 1_600_000L

    /** Marketing at full strength lifts the score by this share. */
    const val MARKETING_LIFT_PPM = 200_000L

    /** Penalty on the pleasantness of weather caused by an event, before scaling by its severity. */
    fun weatherPenaltyPpm(kind: WeatherKind): Long = when (kind) {
        WeatherKind.STORM -> 500_000L
        WeatherKind.FLOOD -> 550_000L
        WeatherKind.HEATWAVE -> 300_000L
        WeatherKind.COLD_SNAP -> 250_000L
        WeatherKind.DROUGHT -> 100_000L
        WeatherKind.PEST_OUTBREAK -> 60_000L
    }

    /** The price factor for a place whose price level is [priceLevelPpm] and a segment with the given sensitivity. */
    fun priceFactorPpm(priceLevelPpm: Long, sensitivityPpm: Long): Long {
        val over = priceLevelPpm - 1_000_000L
        val f = 1_000_000L - mulDiv(sensitivityPpm, over, 1_000_000L)
        return f.coerceIn(MIN_PRICE_FACTOR_PPM, MAX_PRICE_FACTOR_PPM)
    }

    fun appeal(d: Destination, segment: VisitorSegment, ctx: AppealContext): Appeal {
        val p = segment.profile
        val dr = ctx.drivers
        val climate = mulDiv(Seasonality.comfortPpm(d.climate, ctx.season, segment), 1_000_000L - ctx.weatherPenaltyPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        val safety = mulDiv(dr.safetyPpm, 1_000_000L - ctx.safetyDropPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        val parts = listOf(
            DriverScore("Climate", climate, p.climateWeight),
            DriverScore("Infrastructure", dr.infrastructurePpm, p.infrastructureWeight),
            DriverScore("Safety", safety, p.safetyWeight),
            DriverScore("Culture", dr.culturePpm, p.cultureWeight),
            DriverScore("Nature", dr.naturePpm, p.natureWeight),
            DriverScore("Nightlife", dr.nightlifePpm, p.nightlifeWeight),
            DriverScore("Events", ctx.eventPpm.coerceIn(0L, 1_000_000L), p.eventWeight),
            DriverScore("Reputation", d.reputationPpm, p.reputationWeight),
        )
        var weighted = 0L
        var weights = 0L
        for (part in parts) { weighted += part.valuePpm * part.weight; weights += part.weight }
        val base = if (weights <= 0L) 0L else weighted / weights
        val lift = mulDiv(base, mulDiv(d.marketingPpm[segment.ordinal], MARKETING_LIFT_PPM, 1_000_000L), 1_000_000L)
        val price = priceFactorPpm(d.priceLevelPpm + ctx.taxPriceAddPpm, p.priceSensitivityPpm)
        return Appeal(segment, base + lift, price, parts + DriverScore("Marketing", d.marketingPpm[segment.ordinal], 0L))
    }

    /** Mean value over the four segments, weighted equally; used for the daily record. */
    fun meanValuePpm(d: Destination, ctx: AppealContext): Long =
        VisitorSegment.values().sumOf { appeal(d, it, ctx).valuePpm } / VisitorSegment.COUNT

    /**
     * Combines a lasting driver with the pull of attractions: the driver closes a share of its gap to 1,000,000 for each
     * attraction, so many attractions give diminishing returns and never exceed the maximum.
     */
    fun boosted(baseDriverPpm: Long, boostsPpm: List<Long>): Long {
        var gap = 1_000_000L - baseDriverPpm.coerceIn(0L, 1_000_000L)
        for (b in boostsPpm) gap = mulDiv(gap, 1_000_000L - b.coerceIn(0L, 1_000_000L), 1_000_000L)
        return 1_000_000L - gap
    }
}
