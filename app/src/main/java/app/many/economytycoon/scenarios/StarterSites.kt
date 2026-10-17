package app.many.economytycoon.scenarios

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.production.Facility
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.world.Country

/** Gives a company its starting sites the way the engine gives rivals theirs: granted, not built, then equipped through commands. */
object StarterSites {
    /** Places [sites] in the regions of [country] for [company]; sites whose recipe the content does not know are skipped. */
    fun place(sim: Simulation, sites: List<DefSite>, company: CompanyId, country: Country, playerId: Long): List<Facility> {
        val e = sim.economy
        val regions = e.world.regionsOf(country.id).sortedBy { it.id }
        if (regions.isEmpty()) return emptyList()
        val placed = ArrayList<Facility>()
        for (site in sites) {
            val recipe = e.content.recipeOrNull(RecipeId(site.recipe)) ?: continue
            val region = regions[site.regionIndex.coerceIn(0, regions.size - 1)]
            val city = e.world.citiesIn(region.id).maxByOrNull { e.world.population(it.id).total }?.id
            val f = e.production.grantFacility(company, recipe.facilityType, region.id, city, null, null, e.tick)
            equip(sim, company, f, recipe, playerId)
            placed += f
        }
        return placed
    }

    /** Sets the recipe on the first line, staffs the site and adds trading rules. Returns how many commands were accepted. */
    fun equip(sim: Simulation, company: CompanyId, facility: Facility, recipe: RecipeDef, playerId: Long): Int {
        val actor = Actor.Player(playerId)
        var accepted = 0
        fun run(c: Command) { if (sim.execute(actor, c) is Outcome.Ok) accepted++ }
        val line = facility.lines.firstOrNull() ?: return 0
        run(Command.SetRecipe(company, facility.id, line.id, recipe.id))
        val staff = LinkedHashMap<RoleId, Long>()
        for (l in recipe.labor) staff[l.role] = (staff[l.role] ?: 0L) + l.headcount
        for (l in sim.economy.content.facilityType(facility.type).overheadStaff) staff[l.role] = (staff[l.role] ?: 0L) + l.headcount
        for ((role, n) in staff) run(Command.SetStaffing(company, facility.id, role, n))
        for (out in recipe.outputs) run(Command.SetSellRule(company, SellRule(facility.id, out.good, PriceStrategy.MARKET, 0)))
        for (input in recipe.inputs) run(Command.SetBuyRule(company, BuyRule(facility.id, input.good, Quantity(input.quantity.raw * 6), 1_200_000L)))
        return accepted
    }
}
