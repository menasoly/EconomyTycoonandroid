package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.production.Facility

/**
 * The state's import desk. Goods that nothing in the game produces (water, say) and a short list of
 * basic farm and plant inputs are always on sale in every region at a premium to the going price, so a
 * young economy never stalls for want of a first supplier. Local producers undercut it as they appear.
 */
class TradeBoard(private val e: Economy) {
    /** Goods the board stocks: consumed by some recipe, produced by none, plus [SimConfig.importAlso]. */
    val importedGoods: List<GoodId> by lazy {
        val consumed = e.content.allRecipes.flatMap { r -> r.inputs.map { it.good } }.toSet()
        val unproduced = consumed.filter { g -> e.content.recipesProducing(g).isEmpty() }
        val extra = e.config.importAlso.map { GoodId(it) }.filter { e.content.goodOrNull(it) != null }
        (unproduced + extra).distinct().sortedBy { it.key }
    }

    /** Everyday goods households ask for. The board also sells these, at a larger premium, as foreign supply. */
    val consumerGoods: List<GoodId> by lazy { e.content.allDemandProfiles.map { it.good }.sortedBy { it.key } }

    /** Every good a depot holds. */
    val stockedGoods: List<GoodId> by lazy { (importedGoods + consumerGoods).distinct().sortedBy { it.key } }

    private fun markupFor(good: GoodId): Long = if (good in consumerGoods) e.config.consumerImportMarkupPpm else e.config.importMarkupPpm

    /** All the boards of a country, in the order they were founded. */
    fun boardsOf(country: CountryId): List<CompanyId> =
        e.companies.ofKind(CompanyKind.STATE)
            .filter { it.country == country && it.name.contains(NAME_SUFFIX) }
            .sortedBy { it.id }.map { it.id }

    fun boardOf(country: CountryId): CompanyId? = boardsOf(country).firstOrNull()

    /** Goods a board of this country carries: the order limit per party caps how many it can quote each tick. */
    private fun batches(country: CountryId): List<List<GoodId>> {
        val regions = e.world.regionsOf(country).size.coerceAtLeast(1)
        val per = (ORDERS_PER_TICK / regions).coerceIn(1, 40)
        return stockedGoods.chunked(per)
    }

    fun depotOf(region: RegionId): Facility? {
        val country = e.world.region(region).country
        val board = boardOf(country) ?: return null
        return e.production.facilitiesOf(board).firstOrNull { it.region == region }
    }

    /** Which board carries [good] in [country], or null when none does. */
    fun carrierOf(country: CountryId, good: GoodId): CompanyId? {
        val idx = batches(country).indexOfFirst { good in it }
        return if (idx < 0) null else boardsOf(country).getOrNull(idx)
    }

    /** Founds the boards and their depots. Idempotent: countries that already have them are skipped. */
    fun establish(tick: Tick) {
        val depotType = e.content.allFacilityTypes
            .filter { it.requiredTech == null && it.extracts == null && it.maxLines == 0 }
            .minWithOrNull(compareBy({ it.upkeepPerTick.raw }, { it.id.key }))
            ?: e.content.allFacilityTypes.filter { it.requiredTech == null && it.extracts == null }.minByOrNull { it.upkeepPerTick.raw }
            ?: return
        for (country in e.world.countries().sortedBy { it.id }) {
            if (boardsOf(country.id).isNotEmpty()) continue
            for ((n, goods) in batches(country.id).withIndex()) {
                val name = if (n == 0) "${country.name} $NAME_SUFFIX" else "${country.name} $NAME_SUFFIX ${n + 1}"
                val board = e.companies.found(
                    name, CompanyKind.STATE, country.id, country.capital, Holder("state", country.id.value),
                    e.config.tradeBoardCapital, e.governmentOf(country.id), tick,
                ).getOrNull() ?: continue
                e.companies.policies.setDividend(board.id, DividendPolicy(1_000_000L, 10, 30))
                for (region in e.world.regionsOf(country.id)) {
                    val depot = e.production.grantFacility(board.id, depotType.id, region.id, null, null, null, tick, "Import depot")
                    depot.inventory.capacity = Quantity.units(50_000_000)
                    for (g in goods) {
                        // Cost-plus keeps the asking price anchored to the reference price instead of chasing the market.
                        e.companies.policies.setSell(board.id, SellRule(depot.id, g, PriceStrategy.COST_PLUS, markupFor(g)))
                    }
                }
            }
        }
    }

    /** Tops every depot back up to its target level. Imported stock is booked at the reference price. */
    fun replenish(tick: Tick) {
        for (country in e.world.countries().sortedBy { it.id }) {
            for ((n, board) in boardsOf(country.id).withIndex()) {
                if (e.companies.company(board)?.isActive != true) continue
                val goods = batches(country.id).getOrNull(n) ?: continue
                val owner = app.many.economytycoon.finance.LedgerOwner.company(board)
                for (region in e.world.regionsOf(country.id)) {
                    val depot = e.production.facilitiesOf(board).firstOrNull { it.region == region.id } ?: continue
                    val people = e.world.citiesIn(region.id).sumOf { e.world.population(it.id).total }
                    val target = Quantity.units((people / e.config.importPeoplePerUnit).coerceAtLeast(500L))
                    var delta = Money.ZERO
                    for (g in goods) {
                        val have = depot.inventory.quantity(g)
                        if (have >= target) continue
                        val add = target - have
                        val price = e.content.good(g).referencePrice
                        val stored = depot.inventory.add(g, add, price.valueOf(add))
                        delta += price.valueOf(stored)
                    }
                    if (delta.raw > 0L) e.finance.adjustInventory("import:${board.value}:${region.id.value}:${tick.value}", tick, owner, delta)
                }
            }
        }
    }

    companion object {
        const val NAME_SUFFIX = "Trade Board"
        /** Orders one party may place per tick under the market's rate limit, with a margin. */
        const val ORDERS_PER_TICK = 48
    }
}
