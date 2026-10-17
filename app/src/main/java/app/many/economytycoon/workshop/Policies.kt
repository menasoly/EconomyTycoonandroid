package app.many.economytycoon.workshop

import app.many.economytycoon.competitors.Archetype
import app.many.economytycoon.competitors.Personalities
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Economy
import app.many.economytycoon.simulation.Simulation

/** What a policy can see and use while it plays a draft headless. */
class PolicyContext(val sim: Simulation, val company: CompanyId, val draft: ScenarioDraft) {
    val economy: Economy get() = sim.economy
    val day: Int get() = sim.tick.value.toInt()
    val actor: Actor get() = Actor.Player(HarnessSetup.PLAYER_ID)
    fun command(c: Command): Outcome<*> = sim.execute(actor, c)
}

/** A fixed, repeatable way of playing. A policy decides before each tick; it may not look at wall-clock time or draw its own random numbers. */
interface PlayerPolicy {
    val name: String
    fun attach(ctx: PolicyContext) {}
    fun beforeTick(ctx: PolicyContext) {}
}

/** Does nothing at all: the starting sites, if any, run on their own. A scenario an idle company can win is broken. */
class IdlePolicy : PlayerPolicy {
    override val name: String get() = PolicyKind.IDLE.key
}

/**
 * Hands the company to the engine's own rival planner with a fixed personality, so the policy plays by the same rules
 * the computer-run companies do, with the same caution or greed.
 */
class PlannerPolicy(private val kind: PolicyKind, private val archetype: Archetype) : PlayerPolicy {
    override val name: String get() = kind.key
    override fun attach(ctx: PolicyContext) {
        ctx.economy.competitors.register(ctx.company, Personalities.of(archetype))
    }
}

/** One step of a scripted player. Steps run on [day] in the order given. */
sealed class ScriptStep(val day: Int) {
    /** Orders a new facility for [recipe] in the [region]th region; once built it is staffed and set to work. */
    class Build(day: Int, val recipe: String, val region: Int = 0) : ScriptStep(day)
    class Borrow(day: Int, val amount: Long, val termDays: Int) : ScriptStep(day)
    class Repay(day: Int, val amount: Long) : ScriptStep(day)
    class Research(day: Int, val tech: String) : ScriptStep(day)

    override fun toString(): String = when (this) {
        is Build -> "day $day: build $recipe in region $region"
        is Borrow -> "day $day: borrow $amount for $termDays days"
        is Repay -> "day $day: repay $amount"
        is Research -> "day $day: research $tech"
    }
}

/**
 * Plays a written script, and between steps keeps new facilities working: a finished building without a recipe gets
 * the recipe of the step that ordered it. What the script cannot do (cash too short, an unknown recipe) is skipped and
 * listed in [skipped] with the reason, so a bad script shows up in the report instead of silently doing nothing.
 */
class ScriptedPolicy(private val script: List<ScriptStep>) : PlayerPolicy {
    override val name: String get() = PolicyKind.SCRIPTED.key
    private val steps = script.sortedBy { it.day }
    private var next = 0
    private val orderedRecipes = ArrayDeque<String>()
    val skipped = ArrayList<String>()

    override fun beforeTick(ctx: PolicyContext) {
        while (next < steps.size && steps[next].day <= ctx.day) { run(ctx, steps[next]); next++ }
        equipNew(ctx)
    }

    private fun run(ctx: PolicyContext, step: ScriptStep) {
        val e = ctx.economy
        val result: Outcome<*>? = when (step) {
            is ScriptStep.Build -> {
                val recipe = e.content.recipeOrNull(RecipeId(step.recipe))
                if (recipe == null) { skipped += "$step: no such recipe"; null }
                else {
                    val regions = e.world.regionsOf(HarnessSetup.country(ctx.sim, ctx.draft)?.id ?: return).sortedBy { it.id }
                    val region = regions.getOrNull(step.region.coerceIn(0, (regions.size - 1).coerceAtLeast(0)))
                    if (region == null) { skipped += "$step: no such region"; null }
                    else {
                        val city = e.world.citiesIn(region.id).maxByOrNull { e.world.population(it.id).total }?.id
                        ctx.command(Command.Build(ctx.company, recipe.facilityType, region.id, city)).also { if (it is Outcome.Ok) orderedRecipes.addLast(step.recipe) }
                    }
                }
            }
            is ScriptStep.Borrow -> ctx.command(Command.Borrow(ctx.company, Money.units(step.amount), step.termDays))
            is ScriptStep.Repay -> ctx.command(Command.Repay(ctx.company, null, Money.units(step.amount)))
            is ScriptStep.Research -> ctx.command(Command.StartResearch(ctx.company, TechId(step.tech)))
        }
        if (result is Outcome.Rejected) skipped += "$step: ${result.rejection.message}"
    }

    private fun equipNew(ctx: PolicyContext) {
        if (orderedRecipes.isEmpty()) return
        val e = ctx.economy
        for (f in e.production.facilitiesOf(ctx.company).sortedBy { it.id }) {
            if (orderedRecipes.isEmpty()) return
            if (f.status != FacilityStatus.OPERATING) continue
            val line = f.lines.firstOrNull() ?: continue
            if (line.recipe != null) continue
            val recipe = e.content.recipeOrNull(RecipeId(orderedRecipes.first())) ?: run { orderedRecipes.removeFirst(); return }
            if (recipe.facilityType != f.type) continue
            orderedRecipes.removeFirst()
            SiteOperator.equip(ctx.sim, ctx.company, f, recipe)
        }
    }

    companion object {
        /**
         * A sensible script for a draft: grow the first starting recipe (or the engine's default) with two more sites,
         * and borrow a fifth of the capital early to pay for them.
         */
        fun defaultFor(draft: ScenarioDraft, content: app.many.economytycoon.content.ContentRepository): List<ScriptStep> {
            val recipe = draft.company.starters.firstOrNull()?.recipe ?: SiteOperator.defaultRecipe(content)?.id?.key ?: return emptyList()
            val borrow = (draft.company.capital / 5).coerceAtLeast(10_000L)
            return listOf(
                ScriptStep.Borrow(1, borrow, 180),
                ScriptStep.Build(2, recipe, 0),
                ScriptStep.Build(30, recipe, 1),
                ScriptStep.Repay(150, borrow),
            )
        }
    }
}

/** The policies the harness knows by name. */
enum class PolicyKind(val label: String, val summary: String) {
    IDLE("Idle", "Does nothing, to see whether the scenario plays itself."),
    GREEDY("Greedy expander", "Borrows and builds as fast as the rival planner would."),
    CONSERVATIVE("Conservative", "Builds slowly, keeps a large cash cushion and avoids debt."),
    SCRIPTED("Scripted player", "Follows a written build order.");

    val key: String get() = name.lowercase()

    fun create(draft: ScenarioDraft, content: app.many.economytycoon.content.ContentRepository): PlayerPolicy = when (this) {
        IDLE -> IdlePolicy()
        GREEDY -> PlannerPolicy(this, Archetype.EXPANDER)
        CONSERVATIVE -> PlannerPolicy(this, Archetype.CONSERVATIVE)
        SCRIPTED -> ScriptedPolicy(ScriptedPolicy.defaultFor(draft, content))
    }

    companion object {
        fun byKey(key: String): PolicyKind? = values().firstOrNull { it.key == key.lowercase() }
    }
}
