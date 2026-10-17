package app.many.economytycoon.session

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.scenarios.GoalMeter
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioDef
import app.many.economytycoon.scenarios.StarterSites
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimConfig
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.world.WorldSize

enum class Difficulty(val label: String, val aiCompanies: Int, val playerCapital: Money, val summary: String) {
    RELAXED("Relaxed", 3, Money.units(800_000), "A generous start and a few gentle rivals."),
    STANDARD("Standard", 6, Money.units(500_000), "A fair start against a lively field."),
    CUTTHROAT("Cutthroat", 10, Money.units(300_000), "A lean start against many hungry rivals."),
}

data class NewGameOptions(
    val companyName: String,
    val difficulty: Difficulty = Difficulty.STANDARD,
    val worldSize: WorldSize = WorldSize.SMALL,
    val seed: Long = 1L,
    /** Position in the list of countries ordered by id. */
    val countryIndex: Int = 0,
    val playerId: Long = 1L,
    /** Id of a scenario from the catalog; when set it decides the world, rivals and capital. */
    val scenario: String? = null,
)

/** Builds a fresh game: the world and rivals from the options, then the player's first company. */
object NewGame {
    fun config(o: NewGameOptions): SimConfig {
        val def = o.scenario?.let { ScenarioCatalog.byId(it) }
        return if (def != null) SimConfig(seed = def.seed, worldSize = def.worldSize, aiCompanies = def.aiCompanies, playerStartingCapital = def.capital)
        else SimConfig(seed = o.seed, worldSize = o.worldSize, aiCompanies = o.difficulty.aiCompanies, playerStartingCapital = o.difficulty.playerCapital)
    }

    /** Options for playing [def] under [companyName]. */
    fun forScenario(def: ScenarioDef, companyName: String, playerId: Long = 1L): NewGameOptions =
        NewGameOptions(companyName, Difficulty.STANDARD, def.worldSize, def.seed, def.countryIndex, playerId, def.id)

    /** The services every new game starts with, in the order they have always been set up. */
    private fun establish(sim: Simulation): Simulation {
        // New games run on regional electricity grids; games saved without them keep the simple pool.
        sim.execute(Actor.System, Command.EstablishGrids)
        // Governments set carbon policy from their environmental stance; the first rules start two years in.
        sim.execute(Actor.System, Command.EstablishCarbonPolicies())
        // Each country gets a few computer-run banks beside the simple lender companies borrow from.
        sim.execute(Actor.System, Command.EstablishBanks)
        // The patent office opens, with a few outside investors who hold patents and press claims.
        sim.execute(Actor.System, Command.EstablishIp)
        // Towns that draw visitors get hotels, attractions and booking channels, and every region sends travellers.
        sim.execute(Actor.System, Command.EstablishTourism)
        // Every city gets a plan of districts zoned from its land, with a council, a budget and a mayor.
        sim.execute(Actor.System, Command.EstablishUrban)
        // Every region gets public schools and colleges, and people carry skills that decide how well they do their jobs.
        sim.execute(Actor.System, Command.EstablishTalent)
        return sim
    }

    fun start(
        options: NewGameOptions,
        content: ContentRepository,
        settings: GameSettings = GameSettings(),
        saves: SaveManager? = null,
    ): Outcome<GameSession> {
        val name = options.companyName.trim()
        if (name.length < 3) return Outcome.reject(Rules.INVALID_ARGUMENT, "a company name needs at least three characters")
        val def = options.scenario?.let { id -> ScenarioCatalog.byId(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown scenario") }
        val sim = establish(Simulation(config(options), content))
        val countries = sim.economy.world.countries().sortedBy { it.id }
        val country = countries.getOrNull(def?.countryIndex ?: options.countryIndex) ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "no such country")
        val hq = country.capital
        val founded = sim.execute(
            Actor.Player(options.playerId),
            Command.FoundCompany(name, country.id, hq, def?.capital ?: options.difficulty.playerCapital),
        )
        if (founded is Outcome.Rejected) return Outcome.Rejected(founded.rejection)
        if (def != null) sim.economy.scenario.begin(def.id, options.playerId, sim.tick)
        return Outcome.Ok(GameSession(sim, options.playerId, settings, saves))
    }

    /**
     * Starts a game from a scenario definition that is not in the catalog, such as one made in the workshop. [config] decides the
     * world, rivals and rules; the definition travels with the run, so saving and loading keep it.
     */
    fun startCustom(
        def: ScenarioDef,
        config: SimConfig,
        companyName: String,
        content: ContentRepository,
        settings: GameSettings = GameSettings(),
        saves: SaveManager? = null,
        playerId: Long = 1L,
    ): Outcome<GameSession> {
        val name = companyName.trim()
        if (name.length < 3) return Outcome.reject(Rules.INVALID_ARGUMENT, "a company name needs at least three characters")
        def.problems().firstOrNull()?.let { return Outcome.reject(Rules.INVALID_ARGUMENT, "the scenario is unsound: $it") }
        val sim = establish(Simulation(config, content))
        val country = sim.economy.world.countries().sortedBy { it.id }.getOrNull(def.countryIndex)
            ?: return Outcome.reject(Rules.INVALID_ARGUMENT, "no such country")
        val founded = sim.execute(Actor.Player(playerId), Command.FoundCompany(name, country.id, country.capital, def.capital))
        if (founded is Outcome.Rejected) return Outcome.Rejected(founded.rejection)
        if (def.starters.isNotEmpty()) {
            val company = GoalMeter.playerCompanies(sim.economy, playerId).firstOrNull()
                ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "the company was not created")
            StarterSites.place(sim, def.starters, company, country, playerId)
        }
        sim.economy.scenario.beginCustom(def, playerId, sim.tick)
        return Outcome.Ok(GameSession(sim, playerId, settings, saves))
    }
}
