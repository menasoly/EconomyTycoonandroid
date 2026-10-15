package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner

/** Sets up a new game: money, state enterprises, and the AI rivals with a first working facility each. */
class Bootstrap(private val e: Economy, private val executor: CommandExecutor) {

    fun run() {
        val tick = e.tick
        seedMoney(tick)
        e.tradeBoard.establish(tick)
        foundRivals(tick)
        e.tradeBoard.replenish(tick)
    }

    private fun seedMoney(tick: Tick) {
        val people = e.world.totalPopulation()
        val households = Money.units(people * e.config.householdCashPerCapita)
        if (households.raw > 0L) e.finance.mint(tick, e.finance.households, households, "bootstrap:households")
        val bank = Money(households.raw * e.config.bankCapitalMultiple)
        if (bank.raw > 0L) e.finance.mint(tick, e.finance.bank, bank, "bootstrap:bank")
        for (c in e.world.countries().sortedBy { it.id }) {
            e.finance.mint(tick, LedgerOwner.government(c.id), e.config.governmentTreasury, "bootstrap:treasury:${c.id.value}")
        }
    }

    private fun foundRivals(tick: Tick) {
        val countries = e.world.countries().sortedBy { it.id }
        if (countries.isEmpty()) return
        val starter = starterRecipe() ?: return
        for (i in 0 until e.config.aiCompanies) {
            val country = countries[i % countries.size]
            val name = RivalNames.pick(i)
            val capital = e.config.aiStartingCapital
            val c = e.companies.found(name, CompanyKind.AI, country.id, country.capital, Holder("ai", i.toLong()), capital, e.governmentOf(country.id), tick).getOrNull() ?: continue
            e.competitors.registerRandom(c.id, e.random)
            val regions = e.world.regionsOf(country.id).sortedBy { it.id }
            if (regions.isEmpty()) continue
            val region = regions[i / countries.size % regions.size]
            equipStarter(c.id, starter, region.id, tick)
        }
    }

    /** First recipe anyone can run from day one on a plain facility, preferring grain growing. */
    private fun starterRecipe(): RecipeDef? {
        val open = e.content.allRecipes.filter { r ->
            r.requiredTech == null && r.machines.isEmpty() &&
                e.content.facilityType(r.facilityType).let { it.requiredTech == null && it.extracts == null && it.maxLines > 0 }
        }
        return open.firstOrNull { it.id.key == "grow_wheat" } ?: open.minByOrNull { it.id.key }
    }

    private fun equipStarter(company: CompanyId, recipe: RecipeDef, region: app.many.economytycoon.core.RegionId, tick: Tick) {
        val city = e.world.citiesIn(region).maxByOrNull { e.world.population(it.id).total }?.id
        val f = e.production.grantFacility(company, recipe.facilityType, region, city, null, null, tick)
        val line = f.lines.firstOrNull() ?: return
        e.production.setRecipe(company, f.id, line.id, recipe.id)
        val actor = Actor.System
        val staff = LinkedHashMap<app.many.economytycoon.core.RoleId, Long>()
        for (l in recipe.labor) staff[l.role] = (staff[l.role] ?: 0L) + l.headcount
        for (l in e.content.facilityType(f.type).overheadStaff) staff[l.role] = (staff[l.role] ?: 0L) + l.headcount
        for ((role, n) in staff) executor.execute(actor, Command.SetStaffing(company, f.id, role, n))
        for (out in recipe.outputs) {
            e.companies.policies.setSell(company, SellRule(f.id, out.good, PriceStrategy.MARKET, 0))
        }
        for (input in recipe.inputs) {
            // Enough for several batches, bought at up to 20% over the going price.
            e.companies.policies.setBuy(company, BuyRule(f.id, input.good, Quantity(input.quantity.raw * 6), 1_200_000L))
        }
    }
}

/** Fixed pool of rival names so a seed always names its competitors the same way. */
object RivalNames {
    private val names = listOf(
        "Northwind Holdings", "Harbor & Vale", "Crestline Industries", "Ironbridge Group", "Meridian Works",
        "Silverfield Co", "Kestrel Partners", "Redwood Enterprises", "Bluepeak Ventures", "Oakmoor Trading",
        "Stonegate Corp", "Larkspur Industries", "Copperfield Group", "Windmere Holdings", "Ashford & Rowe",
        "Tidewater Works",
    )

    fun pick(index: Int): String {
        val base = names[index % names.size]
        val round = index / names.size
        return if (round == 0) base else "$base ${round + 1}"
    }
}
