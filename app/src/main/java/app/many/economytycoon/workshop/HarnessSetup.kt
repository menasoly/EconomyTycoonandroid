package app.many.economytycoon.workshop

import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.production.Facility
import app.many.economytycoon.scenarios.DefSite
import app.many.economytycoon.scenarios.StarterSites
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
    fun placeStarters(sim: Simulation, draft: ScenarioDraft, founded: Founded): List<Facility> =
        StarterSites.place(sim, draft.company.starters.map { DefSite(it.recipe, it.regionIndex) }, founded.company, founded.country, PLAYER_ID)
}

/** Sets a facility to work through the same commands a player gives. */
object SiteOperator {
    /** Sets the recipe on the first line, staffs the site and adds trading rules. Returns how many commands were accepted. */
    fun equip(sim: Simulation, company: CompanyId, facility: Facility, recipe: RecipeDef): Int =
        StarterSites.equip(sim, company, facility, recipe, HarnessSetup.PLAYER_ID)

    /** The cheapest recipe anyone can run from day one on a plain facility, preferring wheat. */
    fun defaultRecipe(content: app.many.economytycoon.content.ContentRepository): RecipeDef? {
        val open = content.allRecipes.filter { r ->
            r.requiredTech == null && r.machines.isEmpty() &&
                content.facilityType(r.facilityType).let { it.requiredTech == null && it.extracts == null && it.maxLines > 0 }
        }
        return open.firstOrNull { it.id.key == "grow_wheat" } ?: open.minByOrNull { it.id.key }
    }
}
