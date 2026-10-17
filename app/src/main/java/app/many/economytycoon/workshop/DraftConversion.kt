package app.many.economytycoon.workshop

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.scenarios.Goal
import app.many.economytycoon.scenarios.ScenarioDef
import app.many.economytycoon.session.Difficulty
import app.many.economytycoon.session.NewGameOptions
import app.many.economytycoon.simulation.SimConfig

/** Everything needed to start a game from a draft, as plain values. Nothing here has been registered anywhere. */
data class NewGameParams(
    /** Options for the new-game screen; the scenario field is empty because a draft is not in the catalog. */
    val options: NewGameOptions,
    /** The engine configuration, with the draft's rule overrides applied. */
    val config: SimConfig,
    val capital: Money,
)

/** A draft turned into the game's own types, with a note for everything that did not fit. */
data class Conversion(val def: ScenarioDef, val params: NewGameParams, val notes: List<String>) {
    /** True when nothing the author wrote was left out. */
    val lossless: Boolean get() = notes.isEmpty()
}

object DraftConverter {
    /** The engine configuration a draft plays under. Does not check the draft. */
    fun config(draft: ScenarioDraft): SimConfig = draft.rules.applyTo(
        SimConfig(
            seed = draft.seed, worldSize = draft.world.size, aiCompanies = draft.world.rivals,
            playerStartingCapital = Money.units(draft.company.capital),
        ),
    )

    fun params(draft: ScenarioDraft): NewGameParams = NewGameParams(
        NewGameOptions(draft.company.name, Difficulty.STANDARD, draft.world.size, draft.seed, draft.world.countryIndex, 1L, null),
        config(draft), Money.units(draft.company.capital),
    )

    /**
     * Turns a sound draft into a scenario definition and new-game parameters. A draft with errors is refused,
     * naming the first one. Parts the definition cannot hold are listed in the notes.
     */
    fun convert(draft: ScenarioDraft, content: ContentRepository? = null): Outcome<Conversion> {
        val report = DraftValidator.validate(draft, content)
        report.errors.firstOrNull()?.let { first ->
            val more = if (report.errors.size > 1) " (and ${report.errors.size - 1} more)" else ""
            return Outcome.reject(Rules.INVALID_ARGUMENT, "${first.path}: ${first.message}$more")
        }
        val d = draft.normalised()
        val ruin = d.fails.firstOrNull { it.kind == FailKind.NET_WORTH_BELOW }
        val def = ScenarioDef(
            id = d.id, title = d.title, tagline = d.tagline, story = d.story,
            capital = Money.units(d.company.capital), aiCompanies = d.world.rivals, worldSize = d.world.size,
            seed = d.seed, countryIndex = d.world.countryIndex,
            goals = d.requiredGoals.map { Goal(it.kind, it.target) },
            years = d.timing.years, goldDays = d.timing.goldDays, silverDays = d.timing.silverDays,
            ruinBelow = ruin?.threshold, tips = d.hints,
        )
        val problems = def.problems()
        if (problems.isNotEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "the definition is unsound: ${problems.first()}")
        return Outcome.ok(Conversion(def, params(d), notes(d, ruin)))
    }

    private fun notes(d: ScenarioDraft, ruin: FailCondition?): List<String> {
        val out = ArrayList<String>()
        val bonus = d.goals.count { it.bonus }
        if (bonus > 0) out += "$bonus bonus goal${if (bonus == 1) "" else "s"} left out; the definition only has goals that must be met"
        if (d.goals.any { it.weight != 1 }) out += "goal weights are not part of the definition"
        val extraFails = d.fails.count { it != ruin }
        if (extraFails > 0) out += "$extraFails failure condition${if (extraFails == 1) "" else "s"} left out; the definition has only one net worth floor"
        if (ruin != null && ruin.afterDay > 0) out += "the grace period of ${ruin.afterDay} days on the net worth floor is not kept"
        if (d.events.isNotEmpty()) out += "${d.events.size} scripted event${if (d.events.size == 1) "" else "s"} left out"
        if (d.company.starters.isNotEmpty()) out += "${d.company.starters.size} starting site${if (d.company.starters.size == 1) "" else "s"} left out"
        if (!d.rules.isEmpty()) out += "${d.rules.size} rule override${if (d.rules.size == 1) "" else "s"} left out; they are applied in the configuration instead"
        if (d.tags.isNotEmpty()) out += "difficulty tags are not part of the definition"
        return out
    }
}
