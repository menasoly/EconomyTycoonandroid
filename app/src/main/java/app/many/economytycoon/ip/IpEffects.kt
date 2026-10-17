package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.ProductionModifiers

/** Where a company stands with respect to the patents covering a technology in a country. */
enum class RightsStatus(val label: String) {
    FREE("No patent in force"),
    OWNED("Owned"),
    LICENSED("Licensed"),
    CLEARED("Designed around"),
    INFRINGING("Using without a right"),
    ENJOINED("Barred by a court"),
}

/**
 * What the module does to a product made with one technology. Costs and quality are in ppm of the ordinary value:
 * a positive [costDeltaPpm] makes the product dearer, a positive [qualityDeltaPpm] makes it better. A blocked product
 * may not be sold at all.
 */
data class ProductEffects(
    val status: RightsStatus,
    val costDeltaPpm: Long,
    val qualityDeltaPpm: Long,
    val blocked: Boolean,
    val royaltyStackPpm: Long,
    val notes: List<String>,
)

/** The arithmetic of the effects, kept pure so it can be checked on its own. */
object EffectRules {
    /** The most a well-kept secret can take off a product's cost, ppm. */
    const val SECRET_EDGE_MAX_PPM = 40_000L
    const val MAX_DESIGN_PENALTY_PPM = 120_000L

    /** Cost advantage of a live secret: proportional to how well it is guarded and how little the product gives away. */
    fun secretEdgePpm(protectionPpm: Long, exposurePpm: Long): Long {
        val guarded = mulDiv(SECRET_EDGE_MAX_PPM, protectionPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        return mulDiv(guarded, (1_000_000L - exposurePpm.coerceIn(0L, 1_000_000L)), 1_000_000L)
    }

    /** Extra cost of running products that were redesigned around [n] patents. */
    fun designCostPenaltyPpm(dp: DesignAroundParams, n: Int): Long = (dp.productCostPenaltyPpm * n).coerceAtMost(MAX_DESIGN_PENALTY_PPM)

    fun designQualityPenaltyPpm(dp: DesignAroundParams, n: Int): Long = (dp.productQualityPenaltyPpm * n).coerceAtMost(MAX_DESIGN_PENALTY_PPM)

    /** Combines the statuses of several patents covering one technology into the one that matters most. */
    fun worst(statuses: Collection<RightsStatus>): RightsStatus {
        if (statuses.isEmpty()) return RightsStatus.FREE
        return listOf(RightsStatus.ENJOINED, RightsStatus.INFRINGING, RightsStatus.CLEARED, RightsStatus.LICENSED, RightsStatus.OWNED)
            .firstOrNull { it in statuses } ?: RightsStatus.FREE
    }
}

/** Reads the register, the licence book and the courts to say what a technology means for one company in one place. */
class IpEffects(
    private val state: IpState,
    private val book: LicenseBook,
    private val disputes: DisputeService,
    private val pools: PoolService,
    val params: IpParams = IpParams(),
) {
    private fun statusFor(company: CompanyId, p: Patent, country: CountryId, industry: IndustryFamily?, tick: Tick): RightsStatus = when {
        p.owner == IpOwner.company(company) -> RightsStatus.OWNED
        disputes.isEnjoined(company, p.id, country, tick) -> RightsStatus.ENJOINED
        book.isLicensed(company, p.id, country, industry, tick) || pools.coveredByPool(company, p.id) -> RightsStatus.LICENSED
        state.hasClearance(company, p.id) -> RightsStatus.CLEARED
        else -> RightsStatus.INFRINGING
    }

    private fun covering(tech: TechId, country: CountryId, tick: Tick): List<Patent> =
        state.patentMap.values.filter { it.tech == tech && it.inForce(country, tick) }

    fun rightsFor(company: CompanyId, tech: TechId, country: CountryId, industry: IndustryFamily?, tick: Tick): RightsStatus =
        EffectRules.worst(covering(tech, country, tick).map { statusFor(company, it, country, industry, tick) })

    /** The royalties the company pays on a technology as a share of sales, adding up every licence that covers it. */
    fun royaltyStackPpm(company: CompanyId, tech: TechId, country: CountryId, industry: IndustryFamily?, tick: Tick): Long {
        var sum = 0L
        for (l in state.licenseMap.values) {
            if (l.licensee != company || !l.runningAt(tick) || !l.scope.coversCountry(country) || !l.scope.coversIndustry(industry)) continue
            if (l.patents.none { id -> state.patentMap[id]?.tech == tech }) continue
            sum += book.rateOf(l).ppm
        }
        return sum
    }

    fun effects(company: CompanyId, tech: TechId, country: CountryId, industry: IndustryFamily?, tick: Tick): ProductEffects {
        val cover = covering(tech, country, tick)
        val statuses = cover.map { statusFor(company, it, country, industry, tick) }
        val status = EffectRules.worst(statuses)
        val notes = ArrayList<String>()
        var cost = 0L
        var quality = 0L
        val cleared = statuses.count { it == RightsStatus.CLEARED }
        if (cleared > 0) {
            cost += EffectRules.designCostPenaltyPpm(params.designAround, cleared)
            quality -= EffectRules.designQualityPenaltyPpm(params.designAround, cleared)
            notes += "Redesigned around $cleared patent(s): dearer and a little worse."
        }
        val secret = state.liveSecretFor(company, tech)
        if (secret != null) {
            val edge = EffectRules.secretEdgePpm(secret.protectionPpm, secret.exposurePpm)
            if (edge > 0L) { cost -= edge; notes += "Trade secret keeps costs down." }
        }
        val blocked = status == RightsStatus.ENJOINED
        if (blocked) notes += "A court order bars sales."
        if (status == RightsStatus.INFRINGING) notes += "Using a patented technology without a right invites a suit."
        return ProductEffects(status, cost, quality, blocked, royaltyStackPpm(company, tech, country, industry, tick), notes)
    }

    /** The effects on every technology in a list, for a company producing in one country. */
    fun forTechs(company: CompanyId, techs: List<TechId>, country: CountryId, industry: IndustryFamily?, tick: Tick): Map<TechId, ProductEffects> {
        val out = java.util.TreeMap<TechId, ProductEffects>(compareBy { it.key })
        for (t in techs) out[t] = effects(company, t, country, industry, tick)
        return out
    }
}

/**
 * Presents the effects to the production engine. The host says which technology a recipe leans on, where a company
 * produces and what day it is; the adapter turns the effects into the savings and bonuses production already knows.
 */
class IpProductionModifiers(
    private val effects: IpEffects,
    private val tick: () -> Tick,
    private val countryOf: (CompanyId) -> CountryId?,
    private val techOf: (RecipeDef) -> TechId? = { it.requiredTech },
) : ProductionModifiers {
    private fun of(owner: CompanyId, recipe: RecipeDef): ProductEffects? {
        val tech = techOf(recipe) ?: return null
        val country = countryOf(owner) ?: return null
        return effects.effects(owner, tech, country, null, tick())
    }

    override fun inputSavings(owner: CompanyId, recipe: RecipeDef): Rate = Rate(-(of(owner, recipe)?.costDeltaPpm ?: 0L))

    override fun qualityBonus(owner: CompanyId, recipe: RecipeDef): Rate = Rate(of(owner, recipe)?.qualityDeltaPpm ?: 0L)

    override fun throughputBonus(owner: CompanyId, recipe: RecipeDef): Rate = if (of(owner, recipe)?.blocked == true) Rate(-1_000_000L) else Rate.ZERO
}
