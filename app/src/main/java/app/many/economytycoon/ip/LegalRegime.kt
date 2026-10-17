package app.many.economytycoon.ip

import app.many.economytycoon.core.CountryId

/** When a government may force a patent holder to license. All periods are in days. */
data class CompulsoryRules(
    val enabled: Boolean,
    /** Years after grant a patent may go unworked in the country before it can be taken; 0 switches the ground off. */
    val nonWorkingYears: Int,
    val publicHealth: Boolean,
    val antiCompetitive: Boolean,
    val governmentUse: Boolean,
    /** Days an applicant must have tried to licence voluntarily before asking. */
    val negotiationDays: Int,
    /** Days the authority takes to decide. */
    val reviewDays: Int,
    /** Royalty set by the authority as a share of the reasonable royalty, ppm. */
    val royaltyFactorPpm: Long,
    /** Ceiling on the percent-of-sales royalty the authority will set, ppm. */
    val maxRoyaltyPpm: Long,
    /** Years a compulsory licence runs. */
    val termYears: Int,
)

/** How a country treats patents: what they cost, how hard they are to get, how courts lean and what the state may do. */
data class LegalRegime(
    val name: String,
    val termYears: Int,
    val feeMultiplierPpm: Long,
    val examDelayMultiplierPpm: Long,
    /** Taken off every grant probability, ppm. */
    val strictnessPpm: Long,
    /** Added to the claimant's side in court, ppm; negative favours defendants. */
    val patenteeBiasPpm: Long,
    val legalCostMultiplierPpm: Long,
    val damagesMultiplierPpm: Long,
    val enhancedDamages: Boolean,
    val injunctionsForNpe: Boolean,
    /** Years added to the term of pharmaceutical patents. */
    val pharmaExtensionYears: Int,
    /** Credit on qualifying research spending, ppm. */
    val rdCreditPpm: Long,
    /** Tax rate on income from owned patents; null means the ordinary rate. */
    val patentBoxRatePpm: Long?,
    val compulsory: CompulsoryRules,
) {
    init {
        require(termYears > 0) { "patent term must be positive" }
        require(feeMultiplierPpm > 0L && examDelayMultiplierPpm > 0L && legalCostMultiplierPpm > 0L && damagesMultiplierPpm > 0L) { "multipliers must be positive" }
    }
}

/** The shipped regimes. A save stores the kind a country uses, never the numbers. */
enum class RegimeKind(val regime: LegalRegime) {
    PRO_PATENT(
        LegalRegime(
            "Pro-patent", 20, 1_300_000L, 1_100_000L, 0L, 150_000L, 1_400_000L, 1_200_000L, true, true, 3, 100_000L, 100_000L,
            CompulsoryRules(true, 0, true, false, true, 120, 90, 1_500_000L, 80_000L, 5),
        ),
    ),
    BALANCED(
        LegalRegime(
            "Balanced", 20, 1_000_000L, 1_000_000L, 30_000L, 0L, 1_000_000L, 1_000_000L, false, false, 2, 80_000L, 150_000L,
            CompulsoryRules(true, 4, true, true, true, 90, 120, 1_000_000L, 60_000L, 5),
        ),
    ),
    PATENT_SKEPTIC(
        LegalRegime(
            "Patent-sceptic", 17, 800_000L, 900_000L, 90_000L, -150_000L, 800_000L, 700_000L, false, false, 0, 50_000L, null,
            CompulsoryRules(true, 3, true, true, true, 60, 90, 800_000L, 50_000L, 4),
        ),
    ),
    DEVELOPING(
        LegalRegime(
            "Developing", 15, 500_000L, 1_600_000L, 60_000L, -250_000L, 500_000L, 500_000L, false, false, 0, 0L, null,
            CompulsoryRules(true, 3, true, true, true, 45, 60, 600_000L, 40_000L, 4),
        ),
    ),
}

/** Which regime each country uses; countries without an entry use [default]. */
class RegimeBook(var default: RegimeKind = RegimeKind.BALANCED) {
    private val overrides = java.util.TreeMap<CountryId, RegimeKind>()

    fun kindOf(country: CountryId): RegimeKind = overrides[country] ?: default
    fun regimeOf(country: CountryId): LegalRegime = kindOf(country).regime
    fun assign(country: CountryId, kind: RegimeKind) { overrides[country] = kind }
    fun clear(country: CountryId) { overrides.remove(country) }
    fun assigned(): List<Pair<CountryId, RegimeKind>> = overrides.entries.map { it.key to it.value }
    internal fun clearAll() { overrides.clear() }
}
