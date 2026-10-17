package app.many.economytycoon.banking

import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/**
 * Credit risk parameters by rating and sector: probability of default, loss given default, risk weights and the
 * shape of the book. Everything is fixed-point and pure. Probabilities are annual unless stated.
 */
object RiskTables {
    /** Ratings a bank can lend to. [CreditRating.D] is a borrower already in default. */
    val LENDABLE: List<CreditRating> = CreditRating.values().filter { it != CreditRating.D }

    private val PD_PPM = longArrayOf(300L, 800L, 2_000L, 6_000L, 25_000L, 80_000L, 250_000L, 1_000_000L)
    private val RATING_WEIGHT_PPM = longArrayOf(600_000L, 700_000L, 800_000L, 1_000_000L, 1_300_000L, 1_600_000L, 2_000_000L, 2_000_000L)

    /** One-year probability of default of a borrower with this rating in a neutral economy. */
    fun annualPd(rating: CreditRating): Rate = Rate(PD_PPM[rating.ordinal])

    /** One-year probability of default scaled by a macro multiplier (1,000,000 is neutral), capped at certainty. */
    fun stressedPd(rating: CreditRating, multiplierPpm: Long): Rate {
        require(multiplierPpm >= 0L) { "multiplier cannot be negative" }
        return Rate(mulDiv(PD_PPM[rating.ordinal], multiplierPpm, Rate.ONE_PPM).coerceIn(0L, Rate.ONE_PPM))
    }

    /** Probability of default over [months], compounding a constant monthly hazard of one twelfth of the annual figure. */
    fun cumulativePd(annual: Rate, months: Int): Rate {
        require(months >= 0) { "months cannot be negative" }
        if (months == 0) return Rate.ZERO
        val hazard = annual.ppm.coerceIn(0L, Rate.ONE_PPM) / 12L
        var survival = Rate.ONE_PPM
        repeat(months) { survival = mulDiv(survival, Rate.ONE_PPM - hazard, Rate.ONE_PPM) }
        return Rate((Rate.ONE_PPM - survival).coerceIn(0L, Rate.ONE_PPM))
    }

    /** Loss given default with collateral at neutral prices. */
    fun baseLgd(sector: LoanSector): Rate = Rate(when (sector) {
        LoanSector.MORTGAGE -> 200_000L
        LoanSector.CONSUMER -> 600_000L
        LoanSector.SMALL_BUSINESS -> 450_000L
        LoanSector.CORPORATE -> 400_000L
        LoanSector.COMMERCIAL_PROPERTY -> 350_000L
        LoanSector.TRADE_FINANCE -> 250_000L
    })

    /** How much each point of collateral price fall adds to the loss given default. Zero for unsecured lending. */
    private fun collateralSensitivity(sector: LoanSector): Long = when (sector) {
        LoanSector.MORTGAGE -> 900_000L
        LoanSector.COMMERCIAL_PROPERTY -> 1_100_000L
        LoanSector.SMALL_BUSINESS -> 250_000L
        LoanSector.CORPORATE -> 150_000L
        LoanSector.TRADE_FINANCE -> 100_000L
        LoanSector.CONSUMER -> 0L
    }

    /**
     * Loss given default when collateral prices stand at [collateralIndexPpm] of their neutral level (1,000,000).
     * A price fall adds to the loss, a rise takes from it, within a third of the base and certainty.
     */
    fun lgd(sector: LoanSector, collateralIndexPpm: Long = Rate.ONE_PPM): Rate {
        val base = baseLgd(sector).ppm
        val shift = mulDiv(Rate.ONE_PPM - collateralIndexPpm, collateralSensitivity(sector), Rate.ONE_PPM)
        return Rate((base + shift).coerceIn(base / 3L, Rate.ONE_PPM))
    }

    /** Average original term in months. */
    fun termMonths(sector: LoanSector): Int = when (sector) {
        LoanSector.MORTGAGE -> 240
        LoanSector.CONSUMER -> 36
        LoanSector.SMALL_BUSINESS -> 60
        LoanSector.CORPORATE -> 48
        LoanSector.COMMERCIAL_PROPERTY -> 120
        LoanSector.TRADE_FINANCE -> 6
    }

    /** Share of the balance that repays each month. */
    fun monthlyRunoff(sector: LoanSector): Rate = Rate(when (sector) {
        LoanSector.MORTGAGE -> 6_000L
        LoanSector.CONSUMER -> 28_000L
        LoanSector.SMALL_BUSINESS -> 16_000L
        LoanSector.CORPORATE -> 20_000L
        LoanSector.COMMERCIAL_PROPERTY -> 10_000L
        LoanSector.TRADE_FINANCE -> 150_000L
    })

    /** Share of the book whose rate follows the market rate rather than being fixed at origination. */
    fun floatingShare(sector: LoanSector): Rate = Rate(when (sector) {
        LoanSector.MORTGAGE -> 350_000L
        LoanSector.CONSUMER -> 150_000L
        LoanSector.SMALL_BUSINESS -> 600_000L
        LoanSector.CORPORATE -> 800_000L
        LoanSector.COMMERCIAL_PROPERTY -> 500_000L
        LoanSector.TRADE_FINANCE -> 1_000_000L
    })

    /** Modified duration of a sector's book in months, for rate-sensitivity estimates. */
    fun durationMonths(sector: LoanSector): Int = (termMonths(sector) / 3).coerceIn(2, 48)

    /** Multiplier on the rating's credit spread. Secured lending is cheaper than unsecured. */
    fun spreadFactor(sector: LoanSector): Rate = Rate(when (sector) {
        LoanSector.MORTGAGE -> 700_000L
        LoanSector.CONSUMER -> 1_400_000L
        LoanSector.SMALL_BUSINESS -> 1_200_000L
        LoanSector.CORPORATE -> 1_000_000L
        LoanSector.COMMERCIAL_PROPERTY -> 1_100_000L
        LoanSector.TRADE_FINANCE -> 800_000L
    })

    private fun sectorWeight(sector: LoanSector): Long = when (sector) {
        LoanSector.MORTGAGE -> 400_000L
        LoanSector.CONSUMER -> 750_000L
        LoanSector.SMALL_BUSINESS -> 850_000L
        LoanSector.CORPORATE -> 1_000_000L
        LoanSector.COMMERCIAL_PROPERTY -> 1_100_000L
        LoanSector.TRADE_FINANCE -> 600_000L
    }

    /** Risk weight of an exposure for the capital ratio. Impaired loans carry a flat 150%. */
    fun riskWeight(sector: LoanSector, rating: CreditRating, stage: LoanStage): Rate {
        if (stage == LoanStage.IMPAIRED) return Rate.percent(150)
        val rw = mulDiv(sectorWeight(sector), RATING_WEIGHT_PPM[rating.ordinal], Rate.ONE_PPM)
        val staged = if (stage == LoanStage.WATCH) mulDiv(rw, 1_150_000L, Rate.ONE_PPM) else rw
        return Rate(staged.coerceAtMost(Rate.percent(150).ppm))
    }

    fun securityRiskWeight(kind: SecurityKind): Rate = Rate(when (kind) {
        SecurityKind.GOVERNMENT_SHORT -> 0L
        SecurityKind.GOVERNMENT_LONG -> 0L
        SecurityKind.CORPORATE_BONDS -> 600_000L
    })

    /** Applicant weights for one sector from AAA down to CCC, in whole percent (they sum to 100). */
    fun applicantMix(sector: LoanSector): LongArray = when (sector) {
        LoanSector.MORTGAGE -> longArrayOf(5, 10, 25, 30, 20, 8, 2)
        LoanSector.CONSUMER -> longArrayOf(1, 4, 15, 30, 30, 15, 5)
        LoanSector.SMALL_BUSINESS -> longArrayOf(0, 2, 10, 28, 32, 20, 8)
        LoanSector.CORPORATE -> longArrayOf(8, 15, 27, 28, 15, 5, 2)
        LoanSector.COMMERCIAL_PROPERTY -> longArrayOf(2, 6, 20, 30, 24, 13, 5)
        LoanSector.TRADE_FINANCE -> longArrayOf(6, 14, 28, 30, 15, 5, 2)
    }

    /**
     * The mix of applicants when default risk runs at [multiplierPpm] times normal: a share of each class slips one
     * notch, growing with the multiplier up to 30%. At or below normal the mix is unchanged. The weights sum to 10,000.
     */
    fun applicantMixUnder(sector: LoanSector, multiplierPpm: Long): LongArray {
        val base = applicantMix(sector).map { it * 100L }.toLongArray()
        val excess = (multiplierPpm - Rate.ONE_PPM).coerceAtLeast(0L)
        val slip = mulDiv(excess, 150_000L, Rate.ONE_PPM).coerceAtMost(300_000L)
        if (slip == 0L) return base
        val out = LongArray(base.size)
        for (i in base.indices) {
            val moving = if (i == base.size - 1) 0L else mulDiv(base[i], slip, Rate.ONE_PPM)
            out[i] += base[i] - moving
            if (i + 1 < base.size) out[i + 1] += moving
        }
        return out
    }

    /** Migration out of a stage per month, as a share of the balance, in a neutral economy for a given annual PD. */
    fun watchlistMultiple(): Long = 3L
    fun directDefaultShare(): Rate = Rate.percent(30)

    /** Monthly share of watch-listed loans that become impaired, and that return to performing. */
    fun watchToImpaired(multiplierPpm: Long): Rate = Rate(mulDiv(60_000L, multiplierPpm, Rate.ONE_PPM).coerceIn(0L, 600_000L))
    fun watchCure(multiplierPpm: Long): Rate = Rate(mulDiv(100_000L, Rate.ONE_PPM, multiplierPpm.coerceAtLeast(500_000L)).coerceIn(10_000L, 200_000L))

    /** Monthly share of impaired loans that is written off once the work-out is done. */
    fun monthlyWriteOff(): Rate = Rate.percent(8)

    /** Monthly share of impaired loans that is cured. */
    fun impairedCure(): Rate = Rate.percent(1)
}
