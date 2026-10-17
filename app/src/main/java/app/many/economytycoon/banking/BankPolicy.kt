package app.many.economytycoon.banking

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating

/**
 * The levers of a bank's management. The player sets them for a bank they run; a computer-controlled bank has them
 * set by its planner. Every lever is checked against what the supervisor allows before it takes effect.
 */
data class BankPolicy(
    /** Premium paid over the market deposit rate, in basis points, per product. May be negative. */
    val depositPremiumBps: Map<DepositProduct, Long> = DepositProduct.values().associateWith { 0L },
    /** Extra margin added to every loan quote, in basis points. */
    val loanMarginBps: Long = 0L,
    /** Worst borrower rating the bank will lend to. */
    val minRating: CreditRating = CreditRating.B,
    /** How much of its capacity the bank offers to each sector, 0 (closed) to 1,000,000. */
    val sectorAppetite: Map<LoanSector, Rate> = LoanSector.values().associateWith { Rate.percent(70) },
    /** The most of the loan book one sector may make up. */
    val maxSectorShare: Rate = Rate.percent(45),
    /** CET1 the bank wants over the requirement including buffers. */
    val capitalCushion: Rate = Rate.percent(3),
    /** Liquidity coverage the bank wants. */
    val targetLcr: Rate = Rate.percent(130),
    /** Share of the distributable profit paid out. */
    val dividendPayout: Rate = Rate.percent(40),
    /** Share of the monthly growth cap the bank is willing to use. */
    val growthAppetite: Rate = Rate.percent(50),
    /** Share of assets the bank wants to keep in securities. */
    val securitiesShare: Rate = Rate.percent(22),
    /** Largest share of funding taken in the wholesale market. */
    val wholesaleCap: Rate = Rate.percent(15),
) {
    init {
        require(DepositProduct.values().all { depositPremiumBps.containsKey(it) }) { "a premium is needed for every product" }
        require(LoanSector.values().all { sectorAppetite.containsKey(it) }) { "an appetite is needed for every sector" }
        require(minRating != CreditRating.D) { "a bank cannot lend to borrowers in default" }
    }

    fun premiumOf(product: DepositProduct): Long = depositPremiumBps.getValue(product)
    fun appetiteOf(sector: LoanSector): Rate = sectorAppetite.getValue(sector)

    fun withPremium(product: DepositProduct, bps: Long): BankPolicy = copy(depositPremiumBps = depositPremiumBps + (product to bps))
    fun withAppetite(sector: LoanSector, appetite: Rate): BankPolicy = copy(sectorAppetite = sectorAppetite + (sector to appetite))

    fun hashInto(h: CanonicalHasher) {
        for (p in DepositProduct.values()) h.putLong(premiumOf(p))
        h.putLong(loanMarginBps).putInt(minRating.ordinal)
        for (s in LoanSector.values()) h.putRate(appetiteOf(s))
        h.putRate(maxSectorShare).putRate(capitalCushion).putRate(targetLcr).putRate(dividendPayout).putRate(growthAppetite)
            .putRate(securitiesShare).putRate(wholesaleCap)
    }

    companion object {
        /** A policy that follows a strategy's temperament. */
        fun from(strategy: BankStrategy): BankPolicy = BankPolicy(
            depositPremiumBps = DepositProduct.values().associateWith { (strategy.depositAggression.ppm - 500_000L) / 2_500L },
            minRating = when {
                strategy.riskAppetite.ppm >= 750_000L -> CreditRating.CCC
                strategy.riskAppetite.ppm >= 500_000L -> CreditRating.B
                strategy.riskAppetite.ppm >= 250_000L -> CreditRating.BB
                else -> CreditRating.BBB
            },
            sectorAppetite = strategy.sectorBias,
            capitalCushion = strategy.capitalCushion,
            targetLcr = Rate.percent(100) + strategy.liquidityCushion,
            dividendPayout = strategy.payout,
            growthAppetite = strategy.growthAppetite,
            securitiesShare = strategy.securitiesShare,
            wholesaleCap = strategy.wholesaleReliance,
        )
    }
}
