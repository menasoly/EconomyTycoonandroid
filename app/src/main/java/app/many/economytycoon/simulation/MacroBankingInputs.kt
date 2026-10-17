package app.many.economytycoon.simulation

import app.many.economytycoon.banking.BankingInputs
import app.many.economytycoon.banking.Depositor
import app.many.economytycoon.banking.LoanSector
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.macro.GdpKind

/**
 * What the banking sector reads of the rest of the economy: the central bank's rate and the latest review of each
 * country, the state's bond curve and stability, and the money flowing through the national accounts. Read only; every
 * value is derived with integer arithmetic from state that is saved, so a replay sees the same numbers.
 */
class MacroBankingInputs(private val e: Economy) : BankingInputs {
    private fun latest(country: CountryId) = e.macro.latest(country)

    private fun stability(country: CountryId): Long = e.world.countries().firstOrNull { it.id == country }?.stabilityPpm ?: NEUTRAL_STABILITY

    override fun policyRate(country: CountryId) = Rate(e.macro.policyRatePpm(country))
    override fun inflation(country: CountryId) = latest(country)?.let { Rate(it.inflationPpm) } ?: Rate.percent(2)
    override fun outputGap(country: CountryId) = latest(country)?.let { Rate(it.outputGapPpm) } ?: Rate.ZERO
    override fun unemployment(country: CountryId) = latest(country)?.let { Rate(it.unemploymentPpm) } ?: Rate.percent(5)

    override fun sovereignYield(country: CountryId, tenorMonths: Int): Rate {
        val stability = stability(country)
        val days = tenorMonths.coerceIn(0, 120) * DAYS_PER_MONTH
        return Rate(e.bonds.marketYieldPpm(e.macro.policyRatePpm(country), stability, days).coerceAtLeast(0L))
    }

    override fun sovereignRating(country: CountryId): CreditRating {
        val stability = stability(country)
        return when {
            stability >= 920_000L -> CreditRating.AAA
            stability >= 860_000L -> CreditRating.AA
            stability >= 800_000L -> CreditRating.A
            stability >= 740_000L -> CreditRating.BBB
            stability >= 640_000L -> CreditRating.BB
            stability >= 500_000L -> CreditRating.B
            else -> CreditRating.CCC
        }
    }

    /**
     * Unemployment above or below its normal level, limited to a few points either way: a new economy starts with few jobs,
     * and that is no reason for every borrower to default.
     */
    private fun unemploymentGap(unemploymentPpm: Long): Long = (unemploymentPpm - NORMAL_UNEMPLOYMENT).coerceIn(-MAX_GAP_BELOW, MAX_GAP_ABOVE)

    /** The output of the last reviewed month, or of the month still open before the first review. */
    private fun monthlyOutput(country: CountryId): Money = latest(country)?.gdp ?: e.macro.peek(country)?.gdp?.openGdp() ?: Money.ZERO

    /** Share of the month's output that went to investment, from the month still open; a typical share before there is data. */
    private fun investmentShare(country: CountryId): Long {
        val gdp = e.macro.peek(country)?.gdp ?: return TYPICAL_INVESTMENT_SHARE
        val total = gdp.openGdp().raw
        if (total <= 0L) return TYPICAL_INVESTMENT_SHARE
        return mulDiv(gdp.open(GdpKind.INVESTMENT).raw, Rate.ONE_PPM, total).coerceIn(50_000L, 400_000L)
    }

    private fun consumptionShare(country: CountryId): Long {
        val gdp = e.macro.peek(country)?.gdp ?: return TYPICAL_CONSUMPTION_SHARE
        val total = gdp.openGdp().raw
        if (total <= 0L) return TYPICAL_CONSUMPTION_SHARE
        return mulDiv(gdp.open(GdpKind.CONSUMPTION).raw, Rate.ONE_PPM, total).coerceIn(200_000L, 900_000L)
    }

    override fun loanDemand(country: CountryId, sector: LoanSector): Money {
        val output = monthlyOutput(country)
        if (output.raw <= 0L) return Money.ZERO
        // Dearer money cuts borrowing: a fifth of demand is lost for every ten points of the policy rate.
        val rate = e.macro.policyRatePpm(country)
        val rateFactor = (Rate.ONE_PPM - rate * 2L).coerceIn(400_000L, 1_100_000L)
        val base = when (sector) {
            LoanSector.MORTGAGE -> mulDiv(mulDiv(output.raw, MORTGAGE_PPM, Rate.ONE_PPM), collateralIndex(country), Rate.ONE_PPM)
            LoanSector.CONSUMER -> mulDiv(mulDiv(output.raw, CONSUMER_PPM, Rate.ONE_PPM), consumptionShare(country), TYPICAL_CONSUMPTION_SHARE)
            LoanSector.SMALL_BUSINESS -> mulDiv(mulDiv(output.raw, SMALL_BUSINESS_PPM, Rate.ONE_PPM), investmentShare(country), TYPICAL_INVESTMENT_SHARE)
            LoanSector.CORPORATE -> mulDiv(mulDiv(output.raw, CORPORATE_PPM, Rate.ONE_PPM), investmentShare(country), TYPICAL_INVESTMENT_SHARE)
            LoanSector.COMMERCIAL_PROPERTY -> mulDiv(mulDiv(output.raw, COMMERCIAL_PPM, Rate.ONE_PPM), collateralIndex(country), Rate.ONE_PPM)
            LoanSector.TRADE_FINANCE -> mulDiv(output.raw, TRADE_PPM, Rate.ONE_PPM)
        }
        return Money(mulDiv(base, rateFactor, Rate.ONE_PPM).coerceAtLeast(0L))
    }

    override fun defaultMultiplier(country: CountryId, sector: LoanSector): Long {
        val snap = latest(country) ?: return Rate.ONE_PPM
        val excess = unemploymentGap(snap.unemploymentPpm)
        val sensitivity = when (sector) {
            LoanSector.CONSUMER -> 14L
            LoanSector.SMALL_BUSINESS -> 12L
            LoanSector.MORTGAGE -> 8L
            else -> 10L
        }
        return (Rate.ONE_PPM + excess * sensitivity).coerceIn(600_000L, 4_000_000L)
    }

    /** Collateral prices against their neutral level. Land has no price index of its own, so the business cycle stands in for it. */
    override fun collateralIndex(country: CountryId): Long {
        val snap = latest(country) ?: return Rate.ONE_PPM
        return (Rate.ONE_PPM + snap.outputGapPpm * 2L).coerceIn(500_000L, 1_300_000L)
    }

    override fun savingsFlow(country: CountryId, depositor: Depositor): Money {
        val output = monthlyOutput(country)
        if (output.raw <= 0L) return Money.ZERO
        val snap = latest(country)
        return when (depositor) {
            Depositor.HOUSEHOLD -> {
                // Households save less while jobs are scarce.
                val propensity = HOUSEHOLD_SAVING_PPM - (snap?.let { unemploymentGap(it.unemploymentPpm) * 2L / 10L } ?: 0L)
                Money(mulDiv(output.raw, propensity, Rate.ONE_PPM))
            }
            Depositor.CORPORATE -> {
                val retained = CORPORATE_SAVING_PPM + (snap?.let { it.outputGapPpm.coerceIn(-200_000L, 200_000L) / 10L } ?: 0L)
                Money(mulDiv(output.raw, retained, Rate.ONE_PPM))
            }
        }
    }

    private companion object {
        const val DAYS_PER_MONTH = 30
        const val NEUTRAL_STABILITY = 800_000L
        const val NORMAL_UNEMPLOYMENT = 50_000L
        const val MAX_GAP_BELOW = 30_000L
        const val MAX_GAP_ABOVE = 100_000L
        const val TYPICAL_INVESTMENT_SHARE = 180_000L
        const val TYPICAL_CONSUMPTION_SHARE = 600_000L
        const val MORTGAGE_PPM = 30_000L
        const val CONSUMER_PPM = 25_000L
        const val SMALL_BUSINESS_PPM = 20_000L
        const val CORPORATE_PPM = 30_000L
        const val COMMERCIAL_PPM = 10_000L
        const val TRADE_PPM = 15_000L
        const val HOUSEHOLD_SAVING_PPM = 40_000L
        const val CORPORATE_SAVING_PPM = 25_000L
    }
}
