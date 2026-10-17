package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.world.WorldSize

/**
 * One change an author can make to a draft. An edit is a pure function from draft to draft: it either gives the
 * changed draft or refuses with a reason. Edits refuse only what cannot be held in a draft at all (an index that does
 * not exist, a list that is full); whether a value makes a good scenario is for the validator to say.
 * Edits that share a [coalesceKey] are merged into one undo step when they follow each other, as typing does.
 */
sealed class DraftEdit(val label: String, val coalesceKey: String? = null) {
    abstract fun apply(d: ScenarioDraft): Outcome<ScenarioDraft>

    protected fun bad(message: String): Outcome<Nothing> = Outcome.reject(Rules.INVALID_ARGUMENT, message)
    protected fun full(what: String, max: Int): Outcome<Nothing> = Outcome.reject(Rules.LIMIT_EXCEEDED, "a draft holds at most $max $what")

    protected fun <T> List<T>.checked(index: Int, what: String): Outcome<T> =
        if (index in indices) Outcome.ok(this[index]) else Outcome.reject(Rules.UNKNOWN_ENTITY, "there is no $what ${index + 1}")

    protected fun <T> List<T>.replacedAt(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
    protected fun <T> List<T>.removedAt(index: Int): List<T> = toMutableList().also { it.removeAt(index) }

    data class SetId(val id: String) : DraftEdit("Change id", "id") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(id = id))
    }

    data class SetTitle(val title: String) : DraftEdit("Change title", "title") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(title = title))
    }

    data class SetTagline(val tagline: String) : DraftEdit("Change tagline", "tagline") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(tagline = tagline))
    }

    data class SetStory(val story: String) : DraftEdit("Change story", "story") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(story = story))
    }

    data class SetSeed(val seed: Long) : DraftEdit("Change seed", "seed") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(seed = seed))
    }

    data class SetWorldSize(val size: WorldSize) : DraftEdit("Change world size") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(world = d.world.copy(size = size)))
    }

    data class SetRivals(val rivals: Int) : DraftEdit("Change rivals", "rivals") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(world = d.world.copy(rivals = rivals)))
    }

    data class SetCountry(val index: Int) : DraftEdit("Change country") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(world = d.world.copy(countryIndex = index)))
    }

    data class SetCompanyName(val name: String) : DraftEdit("Rename company", "company.name") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(company = d.company.copy(name = name)))
    }

    data class SetCapital(val capital: Long) : DraftEdit("Change capital", "capital") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(company = d.company.copy(capital = capital)))
    }

    data class AddStarter(val site: StarterSite) : DraftEdit("Add starting site") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            if (d.company.starters.size >= DraftLimits.MAX_STARTERS) full("starting sites", DraftLimits.MAX_STARTERS)
            else Outcome.ok(d.copy(company = d.company.copy(starters = d.company.starters + site)))
    }

    data class UpdateStarter(val index: Int, val site: StarterSite) : DraftEdit("Change starting site") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            d.company.starters.checked(index, "starting site").map { d.copy(company = d.company.copy(starters = d.company.starters.replacedAt(index, site))) }
    }

    data class RemoveStarter(val index: Int) : DraftEdit("Remove starting site") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            d.company.starters.checked(index, "starting site").map { d.copy(company = d.company.copy(starters = d.company.starters.removedAt(index))) }
    }

    data class SetRule(val key: RuleKey, val value: Long) : DraftEdit("Change ${key.label.lowercase()}", "rule.${key.key}") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(rules = d.rules.with(key, value)))
    }

    data class ClearRule(val key: RuleKey) : DraftEdit("Reset ${key.label.lowercase()}") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(rules = d.rules.without(key)))
    }

    data class AddGoal(val goal: DraftGoal) : DraftEdit("Add goal") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            if (d.goals.size >= DraftLimits.MAX_GOALS) full("goals", DraftLimits.MAX_GOALS) else Outcome.ok(d.copy(goals = d.goals + goal))
    }

    data class UpdateGoal(val index: Int, val goal: DraftGoal) : DraftEdit("Change goal", "goal.$index") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.goals.checked(index, "goal").map { d.copy(goals = d.goals.replacedAt(index, goal)) }
    }

    data class RemoveGoal(val index: Int) : DraftEdit("Remove goal") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.goals.checked(index, "goal").map { d.copy(goals = d.goals.removedAt(index)) }
    }

    data class MoveGoal(val from: Int, val to: Int) : DraftEdit("Reorder goals") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> {
            if (from !in d.goals.indices) return Outcome.reject(Rules.UNKNOWN_ENTITY, "there is no goal ${from + 1}")
            if (to !in d.goals.indices) return Outcome.reject(Rules.UNKNOWN_ENTITY, "there is no place ${to + 1} in the goal list")
            val list = d.goals.toMutableList()
            list.add(to, list.removeAt(from))
            return Outcome.ok(d.copy(goals = list))
        }
    }

    data class AddEvent(val event: ScriptedEvent) : DraftEdit("Add event") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            if (d.events.size >= DraftLimits.MAX_EVENTS) full("events", DraftLimits.MAX_EVENTS) else Outcome.ok(d.copy(events = d.events + event).normalised())
    }

    data class UpdateEvent(val index: Int, val event: ScriptedEvent) : DraftEdit("Change event", "event.$index") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.events.checked(index, "event").map { d.copy(events = d.events.replacedAt(index, event)).normalised() }
    }

    data class RemoveEvent(val index: Int) : DraftEdit("Remove event") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.events.checked(index, "event").map { d.copy(events = d.events.removedAt(index)) }
    }

    data class AddFail(val fail: FailCondition) : DraftEdit("Add failure condition") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            if (d.fails.size >= DraftLimits.MAX_FAILS) full("failure conditions", DraftLimits.MAX_FAILS) else Outcome.ok(d.copy(fails = d.fails + fail))
    }

    data class UpdateFail(val index: Int, val fail: FailCondition) : DraftEdit("Change failure condition", "fail.$index") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.fails.checked(index, "failure condition").map { d.copy(fails = d.fails.replacedAt(index, fail)) }
    }

    data class RemoveFail(val index: Int) : DraftEdit("Remove failure condition") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.fails.checked(index, "failure condition").map { d.copy(fails = d.fails.removedAt(index)) }
    }

    data class SetTiming(val timing: Timing) : DraftEdit("Change timing", "timing") {
        override fun apply(d: ScenarioDraft) = Outcome.ok(d.copy(timing = timing))
    }

    data class AddTag(val tag: DifficultyTag) : DraftEdit("Add tag") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = when {
            tag in d.tags -> Outcome.reject(Rules.DUPLICATE, "the draft already has the ${tag.label} tag")
            d.tags.size >= DraftLimits.MAX_TAGS -> full("tags", DraftLimits.MAX_TAGS)
            else -> Outcome.ok(d.copy(tags = d.tags + tag).normalised())
        }
    }

    data class RemoveTag(val tag: DifficultyTag) : DraftEdit("Remove tag") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            if (tag !in d.tags) Outcome.reject(Rules.UNKNOWN_ENTITY, "the draft does not have the ${tag.label} tag") else Outcome.ok(d.copy(tags = d.tags - tag))
    }

    data class AddHint(val text: String) : DraftEdit("Add hint") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            if (d.hints.size >= DraftLimits.MAX_HINTS) full("hints", DraftLimits.MAX_HINTS) else Outcome.ok(d.copy(hints = d.hints + text))
    }

    data class UpdateHint(val index: Int, val text: String) : DraftEdit("Change hint", "hint.$index") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.hints.checked(index, "hint").map { d.copy(hints = d.hints.replacedAt(index, text)) }
    }

    data class RemoveHint(val index: Int) : DraftEdit("Remove hint") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = d.hints.checked(index, "hint").map { d.copy(hints = d.hints.removedAt(index)) }
    }

    data class AddPack(val pack: String) : DraftEdit("Require content pack") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> = when {
            pack in d.packs -> Outcome.reject(Rules.DUPLICATE, "the draft already requires $pack")
            d.packs.size >= DraftLimits.MAX_PACKS -> full("content packs", DraftLimits.MAX_PACKS)
            else -> Outcome.ok(d.copy(packs = d.packs + pack).normalised())
        }
    }

    data class RemovePack(val pack: String) : DraftEdit("Drop content pack") {
        override fun apply(d: ScenarioDraft): Outcome<ScenarioDraft> =
            if (pack !in d.packs) Outcome.reject(Rules.UNKNOWN_ENTITY, "the draft does not require $pack") else Outcome.ok(d.copy(packs = d.packs - pack))
    }

    /** Swaps in a whole draft, for imports and templates. */
    data class ReplaceAll(val draft: ScenarioDraft, val reason: String = "Replace draft") : DraftEdit(reason) {
        override fun apply(d: ScenarioDraft) = Outcome.ok(draft.normalised())
    }
}
