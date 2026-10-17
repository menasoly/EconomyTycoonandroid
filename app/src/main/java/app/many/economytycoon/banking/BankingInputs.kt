package app.many.economytycoon.banking

import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating

/** Monetary conditions of a country. Rates are annual; the signed ones may be negative. */
interface MonetaryView {
    /** The central bank's policy rate. */
    fun policyRate(country: CountryId): Rate
    fun inflation(country: CountryId): Rate
    fun outputGap(country: CountryId): Rate
    fun unemployment(country: CountryId): Rate
}

/** Government debt as seen by a bank buying it. */
interface SovereignView {
    /** Yield on government debt with [tenorMonths] to run. */
    fun sovereignYield(country: CountryId, tenorMonths: Int): Rate
    fun sovereignRating(country: CountryId): CreditRating
}

/** What households and firms want from banks. */
interface CreditDemandView {
    /** New borrowing wanted this month in a sector, at normal prices. */
    fun loanDemand(country: CountryId, sector: LoanSector): Money

    /**
     * How much more likely borrowers in a sector are to default than in a normal economy, as a multiple in ppm
     * (1,000,000 is normal, 2,000,000 is twice as likely).
     */
    fun defaultMultiplier(country: CountryId, sector: LoanSector): Long

    /** Collateral prices against their neutral level in ppm (1,000,000 is neutral). */
    fun collateralIndex(country: CountryId): Long
}

/** Money looking for a home. */
interface SavingsView {
    /** Net new money households (or companies) want to put into bank accounts this month; negative when they draw down. */
    fun savingsFlow(country: CountryId, depositor: Depositor): Money
}

/** Everything the banking sector reads from the rest of the game. Implemented by an adapter over the simulation. */
interface BankingInputs : MonetaryView, SovereignView, CreditDemandView, SavingsView

/** Fixed inputs for tests and for starting scenarios. Anything not set falls back to a calm economy. */
data class StaticBankingInputs(
    val policy: Rate = Rate.percent(4),
    val inflationRate: Rate = Rate.percent(2),
    val gap: Rate = Rate.ZERO,
    val joblessRate: Rate = Rate.percent(5),
    val shortYield: Rate = Rate.percent(4),
    val longYield: Rate = Rate.percent(5),
    val sovereign: CreditRating = CreditRating.AA,
    val demand: Map<LoanSector, Money> = LoanSector.values().associateWith { Money.units(8_000_000) },
    val multipliers: Map<LoanSector, Long> = emptyMap(),
    val collateral: Long = Rate.ONE_PPM,
    val householdSavings: Money = Money.units(6_000_000),
    val corporateSavings: Money = Money.units(3_000_000),
) : BankingInputs {
    override fun policyRate(country: CountryId) = policy
    override fun inflation(country: CountryId) = inflationRate
    override fun outputGap(country: CountryId) = gap
    override fun unemployment(country: CountryId) = joblessRate

    override fun sovereignYield(country: CountryId, tenorMonths: Int): Rate {
        val t = tenorMonths.coerceIn(0, 120)
        val span = longYield.ppm - shortYield.ppm
        return Rate(shortYield.ppm + span * t / 120L)
    }

    override fun sovereignRating(country: CountryId) = sovereign
    override fun loanDemand(country: CountryId, sector: LoanSector) = demand[sector] ?: Money.ZERO
    override fun defaultMultiplier(country: CountryId, sector: LoanSector) = multipliers[sector] ?: Rate.ONE_PPM
    override fun collateralIndex(country: CountryId) = collateral

    override fun savingsFlow(country: CountryId, depositor: Depositor) =
        if (depositor == Depositor.HOUSEHOLD) householdSavings else corporateSavings

    /** The same economy in a downturn: wider output gap, higher unemployment, more defaults and falling collateral. */
    fun recession(severityPpm: Long = 500_000L): StaticBankingInputs = copy(
        gap = Rate(-severityPpm / 10L),
        joblessRate = Rate(joblessRate.ppm + severityPpm / 10L),
        multipliers = LoanSector.values().associateWith { Rate.ONE_PPM + severityPpm * 2L },
        collateral = (Rate.ONE_PPM - severityPpm / 2L).coerceAtLeast(300_000L),
        demand = demand.mapValues { Money(it.value.raw / 2L) },
    )
}
