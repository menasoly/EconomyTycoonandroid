package app.many.economytycoon.urban

import app.many.economytycoon.core.mulDiv

/** How one district's population mix shifted in a month. */
data class GentrificationResult(val district: DistrictId, val displaced: Long, val lowIncomeShiftPpm: Long, val backlashPpm: Long)

/**
 * Gentrification: when rents rise faster than the city can bear, lower-income households are replaced by better-off ones.
 * That displacement builds up anger (backlash) that fades slowly and that the council feels. Tenant protection and
 * housing aid slow the process; falling rents let the mix recover.
 */
object Gentrification {
    /** Rent growth over a year that the market absorbs without displacement. */
    const val TOLERATED_GROWTH_PPM = 60_000L
    const val LOW_INCOME_FLOOR_PPM = 80_000L
    const val LOW_INCOME_CEILING_PPM = 650_000L

    /** Pressure on the district's low-income households, ppm; zero when rents are calm. */
    fun pressurePpm(d: District): Long = (d.rentGrowthPpm() - TOLERATED_GROWTH_PPM).coerceAtLeast(0L)

    fun step(d: District, housingAidPermille: Int): GentrificationResult {
        val growth = d.rentGrowthPpm()
        val shield = (1000L - d.tenantProtectionPermille * 8L / 10L - housingAidPermille * 3L / 10L).coerceIn(100L, 1000L)
        var shift = 0L
        var displaced = 0L
        if (growth > TOLERATED_GROWTH_PPM && d.residents > 0L) {
            val raw = mulDiv(growth - TOLERATED_GROWTH_PPM, 50_000L, Scale.PPM) // one twentieth of the excess each month
            shift = mulDiv(raw, shield, 1000L).coerceAtMost((d.lowIncomeSharePpm - LOW_INCOME_FLOOR_PPM).coerceAtLeast(0L))
            displaced = mulDiv(d.residents, shift, Scale.PPM)
            d.lowIncomeSharePpm -= shift
            d.displacedTotal += displaced
        } else if (growth < -50_000L) {
            val back = 3_000L.coerceAtMost((LOW_INCOME_CEILING_PPM - d.lowIncomeSharePpm).coerceAtLeast(0L))
            d.lowIncomeSharePpm += back
            shift = -back
        }
        val fading = d.backlashPpm - d.backlashPpm / 20L
        d.backlashPpm = (fading + shift * 6L).coerceIn(0L, Scale.PPM)
        return GentrificationResult(d.id, displaced, shift, d.backlashPpm)
    }
}
