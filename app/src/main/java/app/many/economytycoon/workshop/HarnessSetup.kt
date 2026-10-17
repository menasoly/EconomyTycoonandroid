package app.many.economytycoon.workshop

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.production.Facility
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.world.Country

/** Puts the player's company into a freshly built simulation: founding it and placing the starting sites. */
object HarnessSetup {
    const val PLAYER_ID = 1L

    class Founded(val company: CompanyId, val country: Country)

    /** The country a draft starts in, counted the way new games count: by id order. */
    fun country(sim: Simulation, draft: ScenarioDraft): Country? =
        sim.economy.world.countries().sortedBy { it.id }.let { it.getOrNull(draft.world.countryIndex) ?: it.firstOrNull() }

    fun found(sim: Simulation, draft: ScenarioDraft): Outcome<Founded> {
        val country = country(sim, draft) ?: return Outcome.reject(app.many.economytycoon.core.Rules.UNKNOWN_ENTITY, "the world has no country")
        val result = sim.execute(
            Actor.Player(PLAYER_ID),
            Command.FoundCompany(draft.company.name.trim(), country.id, country.capital, Money.units(draft.company.capital)),
        )
        if (result is Outcome.Rejected) return Outcome.Rejected(result.rejection)
        val id = sim.economy.companies.ownedBy(app.many.economytycoon.finance.Holder.player(PLAYER_ID)).minOfOrNull { it.id }
            ?: return Outcome.reject(app.many.economytycoon.core.Rules.UNKNOWN_ENTITY, "the company was not created")
        return Outcome.ok(Founded(id, country))
    }

    /**
     * Places the draft's starting sites the way the engine places rivals' first sites: the building is granted, not
     * built, and then given its recipe, crew and trading rules. Returns the facilities that were placed.
     */
    fun placeStarters(sim: Simulation, draft: ScenarioDraft, founded: Founded): List<Facility> {
        val e = sim.economy
        val regions = e.world.regionsOf(founded.country.id).sortedBy { it.id }
        if (regions.isEmpty()) return emptyList()
        val placed = ArrayList<Facility>()
        for (site in draft.company.starters) {
            val recipe = e.content.recipeOrNull(RecipeId(site.recipe)) ?: continue
            val region = regions[site.regionIndex.coerceIn(0, regions.size - 1)]
            val city = e.world.citiesIn(region.id).maxByOrNull { e.world.population(it.id).total }?.id
            val f = e.production.grantFacility(founded.company, recipe.facilityType, region.id, city, null, null, e.tick)
            SiteOperator.equip(sim, founded.company, f, recipe)
            placed += f
        }
        return placed
    }
}

/** Sets a facility to work through the same commands a player gives. */
object SiteOperator {
    /** Sets the recipe on the first line, staffs the site and adds trading rules. Returns how many commands were accepted. */
    fun equip(sim: Simulation, company: CompanyId, facility: Facility, recipe: RecipeDef): Int {
        val actor = Actor.Player(HarnessSetup.PLAYER_ID)
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

    /** The cheapest recipe anyone can run from day one on a plain facility, preferring wheat. */
    fun defaultRecipe(content: app.many.economytycoon.content.ContentRepository): RecipeDef? {
        val open = content.allRecipes.filter { r ->
            r.requiredTech == null && r.machines.isEmpty() &&
                content.facilityType(r.facilityType).let { it.requiredTech == null && it.extracts == null && it.maxLines > 0 }
        }
        return open.firstOrNull { it.id.key == "grow_wheat" } ?: open.minByOrNull { it.id.key }
    }
}
