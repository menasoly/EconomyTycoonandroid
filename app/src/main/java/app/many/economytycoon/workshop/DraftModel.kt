package app.many.economytycoon.workshop

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.simulation.SimConfig
import app.many.economytycoon.world.WorldSize

/** Hard structural limits of a draft. Going past one is refused by the editor; softer limits are validation findings. */
object DraftLimits {
    const val MAX_GOALS = 12
    const val MAX_EVENTS = 40
    const val MAX_HINTS = 10
    const val MAX_STARTERS = 6
    const val MAX_FAILS = 6
    const val MAX_TAGS = 6
    const val MAX_PACKS = 12

    val TITLE = 3..40
    const val MAX_TAGLINE = 80
    const val MAX_STORY = 600
    const val MAX_HINT = 140
    const val MAX_EVENT_TEXT = 80
    val COMPANY_NAME = 3..32
    const val MAX_RIVALS = 16
    val YEARS = 1..40
    val WEIGHT = 1..10
}

/** How hard the author means the scenario to be, and what sort of play it asks for. */
enum class DifficultyTag(val label: String) {
    GENTLE("Gentle"), STANDARD("Standard"), HARSH("Harsh"), PUZZLE("Puzzle"), SPRINT("Sprint"), MARATHON("Marathon"),
    SANDBOX("Sandbox"), TRADE("Trade"), PROPERTY("Property"), FINANCE("Finance");

    /** The tag as written in the text form. */
    val key: String get() = name.lowercase()

    companion object {
        fun byKey(key: String): DifficultyTag? = values().firstOrNull { it.key == key.lowercase() }
    }
}

/** The world a scenario is played in. [countryIndex] counts the countries ordered by id, as new games do. */
data class WorldSettings(val size: WorldSize = WorldSize.SMALL, val rivals: Int = 3, val countryIndex: Int = 0)

/** A facility the player's company owns from day zero, running [recipe] in the [regionIndex]th region of the home country. */
data class StarterSite(val recipe: String, val regionIndex: Int = 0)

data class CompanySetup(
    val name: String = "Founders Ltd",
    /** Whole currency units. */
    val capital: Long = 500_000L,
    val starters: List<StarterSite> = emptyList(),
)

/** Engine settings an author may change. Each key has a range; a key left out keeps the engine default. */
enum class RuleKey(val label: String, val min: Long, val max: Long, private val read: (SimConfig) -> Long, private val write: (SimConfig, Long) -> SimConfig) {
    HOUSEHOLD_CASH("Cash per inhabitant", 10L, 2_000L, { it.householdCashPerCapita }, { c, v -> c.copy(householdCashPerCapita = v) }),
    BANK_MULTIPLE("Bank capital multiple", 1L, 10L, { it.bankCapitalMultiple }, { c, v -> c.copy(bankCapitalMultiple = v) }),
    TREASURY("Government treasury", 0L, 50_000_000L, { it.governmentTreasury.wholeUnits() }, { c, v -> c.copy(governmentTreasury = app.many.economytycoon.core.Money.units(v)) }),
    SPENDING_PROPENSITY("Household spending per tick (ppm)", 2_000L, 100_000L, { it.spendingPropensityPpm }, { c, v -> c.copy(spendingPropensityPpm = v) }),
    AI_CAPITAL("Rival starting capital", 50_000L, 20_000_000L, { it.aiStartingCapital.wholeUnits() }, { c, v -> c.copy(aiStartingCapital = app.many.economytycoon.core.Money.units(v)) }),
    IMPORT_MARKUP("Import markup (ppm)", 0L, 1_000_000L, { it.importMarkupPpm }, { c, v -> c.copy(importMarkupPpm = v) }),
    CONSUMER_IMPORT_MARKUP("Consumer import markup (ppm)", 0L, 1_500_000L, { it.consumerImportMarkupPpm }, { c, v -> c.copy(consumerImportMarkupPpm = v) }),
    PUBLIC_SPENDING("Public spending per inhabitant (raw)", 0L, 5_000L, { it.publicSpendingPerCapita.raw }, { c, v -> c.copy(publicSpendingPerCapita = app.many.economytycoon.core.Money(v)) });

    /** The key as written in the text form. */
    val key: String get() = name.lowercase()

    fun engineDefault(): Long = read(SimConfig(seed = 0L))
    fun applyTo(config: SimConfig, value: Long): SimConfig = write(config, value)
    fun inRange(value: Long): Boolean = value in min..max

    companion object {
        fun byKey(key: String): RuleKey? = values().firstOrNull { it.key == key.lowercase() }
    }
}

/** The engine settings a draft changes, in key order. */
class RuleOverrides private constructor(private val map: Map<RuleKey, Long>) {
    constructor() : this(emptyMap())

    val size: Int get() = map.size
    fun isEmpty() = map.isEmpty()
    operator fun get(key: RuleKey): Long? = map[key]
    fun entries(): List<Pair<RuleKey, Long>> = map.entries.map { it.key to it.value }
    fun valueOf(key: RuleKey): Long = map[key] ?: key.engineDefault()

    fun with(key: RuleKey, value: Long): RuleOverrides = RuleOverrides((map + (key to value)).toSortedMap(compareBy { it.ordinal }))
    fun without(key: RuleKey): RuleOverrides = RuleOverrides(map - key)

    /** The engine configuration these overrides give on top of [base]. */
    fun applyTo(base: SimConfig): SimConfig = entries().fold(base) { cfg, (k, v) -> k.applyTo(cfg, v) }

    override fun equals(other: Any?) = other is RuleOverrides && other.map == map
    override fun hashCode() = map.hashCode()
    override fun toString() = entries().joinToString(prefix = "RuleOverrides(", postfix = ")") { "${it.first.key}=${it.second}" }

    companion object {
        fun of(vararg pairs: Pair<RuleKey, Long>): RuleOverrides = pairs.fold(RuleOverrides()) { acc, (k, v) -> acc.with(k, v) }
    }
}

enum class EventKind(val label: String) {
    /** Cash paid into the player's company. */
    GRANT("Grant"),
    /** Cash taken from the player's company, never more than it holds. */
    LEVY("Levy"),
    /** Cash paid to every rival company. */
    RIVAL_GRANT("Rival windfall"),
    /** A message to the player and nothing else. */
    ANNOUNCE("Announcement");

    val key: String get() = name.lowercase()
    val hasAmount: Boolean get() = this != ANNOUNCE

    companion object {
        fun byKey(key: String): EventKind? = values().firstOrNull { it.key == key.lowercase() }
    }
}

/** Something that happens on [day] (counted from the start of the game). [amount] is in whole units and zero for announcements. */
data class ScriptedEvent(val day: Int, val kind: EventKind, val amount: Long = 0L, val text: String = "")

/**
 * One target of the scenario. A [bonus] goal is not needed to win; it only counts towards the score. The [weight]
 * says how much progress on this goal matters when progress is combined into one number.
 */
data class DraftGoal(val kind: GoalKind, val target: Long, val weight: Int = 1, val bonus: Boolean = false)

enum class FailKind(val label: String) {
    /** The game is lost when the company's net worth is under the threshold. */
    NET_WORTH_BELOW("Net worth below"),
    /** The game is lost when cash is under the threshold, after the grace day. */
    CASH_BELOW("Cash below"),
    /** The game is lost when the company runs fewer facilities than the threshold, after the grace day. */
    FACILITIES_BELOW("Facilities below");

    val key: String get() = name.lowercase()

    companion object {
        fun byKey(key: String): FailKind? = values().firstOrNull { it.key == key.lowercase() }
    }
}

/** An extra way to lose. [afterDay] is a grace period in days from the start. Only the first net-worth condition fits a [app.many.economytycoon.scenarios.ScenarioDef]. */
data class FailCondition(val kind: FailKind, val threshold: Long, val afterDay: Int = 0)

data class Timing(val years: Int = 5, val goldDays: Int = 1_000, val silverDays: Int = 1_500) {
    val deadlineDays: Int get() = years * 360
}

/**
 * A scenario being written. Everything an author can set lives here as plain values, so any draft - sound or not -
 * can be held, edited, saved and shared; whether it can be played is a separate question answered by the validator.
 */
data class ScenarioDraft(
    val id: String,
    val title: String,
    val tagline: String = "",
    val story: String = "",
    val seed: Long = 1L,
    val world: WorldSettings = WorldSettings(),
    val company: CompanySetup = CompanySetup(),
    val rules: RuleOverrides = RuleOverrides(),
    val events: List<ScriptedEvent> = emptyList(),
    val goals: List<DraftGoal> = emptyList(),
    val fails: List<FailCondition> = emptyList(),
    val timing: Timing = Timing(),
    val tags: List<DifficultyTag> = emptyList(),
    val hints: List<String> = emptyList(),
    /** Content packs the scenario was written for, as "id:major.minor". */
    val packs: List<String> = emptyList(),
) {
    val deadlineDays: Int get() = timing.deadlineDays
    val requiredGoals: List<DraftGoal> get() = goals.filter { !it.bonus }

    /** The same draft in canonical order: events by day, tags and packs sorted and without repeats. */
    fun normalised(): ScenarioDraft = copy(
        events = events.withIndex().sortedWith(compareBy({ it.value.day }, { it.index })).map { it.value },
        tags = tags.distinct().sortedBy { it.ordinal },
        packs = packs.distinct().sorted(),
    )

    fun hashInto(h: CanonicalHasher) {
        h.section("workshop.draft")
        h.putString(id).putString(title).putString(tagline).putString(story).putLong(seed)
        h.putInt(world.size.ordinal).putInt(world.rivals).putInt(world.countryIndex)
        h.putString(company.name).putLong(company.capital).putInt(company.starters.size)
        for (s in company.starters) h.putString(s.recipe).putInt(s.regionIndex)
        h.putInt(rules.size)
        for ((k, v) in rules.entries()) h.putInt(k.ordinal).putLong(v)
        h.putInt(events.size)
        for (e in events) h.putInt(e.day).putInt(e.kind.ordinal).putLong(e.amount).putString(e.text)
        h.putInt(goals.size)
        for (g in goals) h.putInt(g.kind.ordinal).putLong(g.target).putInt(g.weight).putBoolean(g.bonus)
        h.putInt(fails.size)
        for (f in fails) h.putInt(f.kind.ordinal).putLong(f.threshold).putInt(f.afterDay)
        h.putInt(timing.years).putInt(timing.goldDays).putInt(timing.silverDays)
        h.putInt(tags.size)
        for (t in tags) h.putInt(t.ordinal)
        h.putInt(hints.size)
        for (t in hints) h.putString(t)
        h.putInt(packs.size)
        for (p in packs) h.putString(p)
    }

    /** Hex digest of the normalised draft; two drafts with the same hash play identically. */
    fun stateHash(): String = CanonicalHasher().also { normalised().hashInto(it) }.finish().toHex()

    companion object {
        /** Whether [id] is a valid scenario id: lowercase letters, digits and dashes, as the built-in scenarios use. */
        fun validId(id: String): Boolean = id.isNotEmpty() && id.length <= 40 && id.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*"))
    }
}
