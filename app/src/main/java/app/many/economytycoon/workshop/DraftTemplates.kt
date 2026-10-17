package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioDef
import app.many.economytycoon.world.WorldSize

/** A starting point for a new draft. */
class DraftTemplate(val key: String, val name: String, val summary: String, private val make: () -> ScenarioDraft) {
    fun create(id: String = key, title: String = name): ScenarioDraft = make().copy(id = id, title = title).normalised()
}

/** What a new draft holds before the author changes anything. */
object DraftDefaults {
    fun blank(id: String = "new-scenario", title: String = "New Scenario"): ScenarioDraft = ScenarioDraft(
        id = id, title = title, tagline = "", story = "", seed = 1L,
        world = WorldSettings(WorldSize.SMALL, rivals = 3, countryIndex = 0),
        company = CompanySetup("Founders Ltd", 500_000L, emptyList()),
        goals = listOf(DraftGoal(GoalKind.NET_WORTH, 1_000_000L)),
        timing = Timing(years = 5, goldDays = 1_000, silverDays = 1_500),
        tags = listOf(DifficultyTag.STANDARD),
    )

    /** A seed that differs between titles, so two new drafts do not play the same world. */
    fun seedFor(title: String): Long = title.fold(17L) { acc, ch -> (acc * 31L + ch.code) % 1_000_003L } + 1L
}

object DraftTemplates {
    val all: List<DraftTemplate> = listOf(
        DraftTemplate("blank", "Blank", "One goal, a modest purse and nothing else.") { DraftDefaults.blank() },
        DraftTemplate("family-farm", "Family farm", "A single working farm and a few years to grow it.") {
            DraftDefaults.blank().copy(
                tagline = "Grow a family farm into a business.",
                story = "A small farm, a loan-free start and a country that eats. Build it up.",
                company = CompanySetup("Family Farm Co", 250_000L, listOf(StarterSite("grow_wheat", 0))),
                goals = listOf(DraftGoal(GoalKind.FACILITIES, 4, weight = 2), DraftGoal(GoalKind.EMPLOYEES, 40, weight = 1), DraftGoal(GoalKind.MONTHLY_PROFIT, 12_000, weight = 3)),
                timing = Timing(3, 600, 900),
                tags = listOf(DifficultyTag.GENTLE),
                hints = listOf("Staff every facility before you build the next one."),
            )
        },
        DraftTemplate("sprint", "Sprint", "Two years to double your money.") {
            DraftDefaults.blank().copy(
                tagline = "Two years. Double it.",
                story = "No time to waste. The market is open and the clock is running.",
                company = CompanySetup("Quick Capital", 400_000L, listOf(StarterSite("grow_wheat", 0), StarterSite("raise_poultry", 1))),
                world = WorldSettings(WorldSize.SMALL, rivals = 4, countryIndex = 0),
                goals = listOf(DraftGoal(GoalKind.NET_WORTH, 800_000L, weight = 4), DraftGoal(GoalKind.CASH, 300_000L, weight = 1, bonus = true)),
                timing = Timing(2, 400, 600),
                tags = listOf(DifficultyTag.SPRINT, DifficultyTag.STANDARD),
            )
        },
        DraftTemplate("shoestring", "Shoestring", "Little money, tight credit and a floor under net worth.") {
            DraftDefaults.blank().copy(
                tagline = "Survive on what you have.",
                story = "The bank has said no. Grow slowly and do not fall.",
                company = CompanySetup("Shoestring & Sons", 150_000L, listOf(StarterSite("grow_potatoes", 0))),
                rules = RuleOverrides.of(RuleKey.SPENDING_PROPENSITY to 14_000L),
                goals = listOf(DraftGoal(GoalKind.DEBT_FREE, 3), DraftGoal(GoalKind.CASH, 200_000L)),
                fails = listOf(FailCondition(FailKind.NET_WORTH_BELOW, 40_000L, afterDay = 30)),
                timing = Timing(4, 700, 1_100),
                tags = listOf(DifficultyTag.HARSH),
            )
        },
        DraftTemplate("sandbox", "Sandbox", "Rich start, no rivals, a bonus goal only.") {
            DraftDefaults.blank().copy(
                tagline = "Build whatever you like.",
                story = "No rivals and plenty of money. The only goal is to see something grow.",
                world = WorldSettings(WorldSize.SMALL, rivals = 0, countryIndex = 0),
                company = CompanySetup("Sandbox Works", 2_000_000L, emptyList()),
                goals = listOf(DraftGoal(GoalKind.FACILITIES, 6, weight = 1), DraftGoal(GoalKind.NET_WORTH, 5_000_000L, weight = 1, bonus = true)),
                timing = Timing(10, 1_500, 2_500),
                tags = listOf(DifficultyTag.SANDBOX, DifficultyTag.GENTLE),
            )
        },
        DraftTemplate("land-rush", "Land rush", "Buy ground, rent it out, sell high.") {
            DraftDefaults.blank().copy(
                tagline = "Own the ground under their feet.",
                story = "Cities grow and the ground goes with them.",
                company = CompanySetup("Landmark Holdings", 700_000L, emptyList()),
                goals = listOf(DraftGoal(GoalKind.PARCELS, 5, weight = 2), DraftGoal(GoalKind.RENT_INCOME, 4_000, weight = 3)),
                events = listOf(ScriptedEvent(180, EventKind.ANNOUNCE, 0, "The city council announces a new ring road.")),
                timing = Timing(4, 800, 1_200),
                tags = listOf(DifficultyTag.PROPERTY),
            )
        },
    )

    fun byKey(key: String): DraftTemplate? = all.firstOrNull { it.key == key }

    /** A draft that plays like a built-in scenario, with a new id so both can exist. */
    fun fromDef(def: ScenarioDef, id: String = def.id + "-remix", title: String = def.title + " Remix"): ScenarioDraft = ScenarioDraft(
        id = id, title = title, tagline = def.tagline, story = def.story, seed = def.seed,
        world = WorldSettings(def.worldSize, def.aiCompanies, def.countryIndex),
        company = CompanySetup("Founders Ltd", def.capital.wholeUnits(), emptyList()),
        goals = def.goals.map { DraftGoal(it.kind, it.target) },
        fails = listOfNotNull(def.ruinBelow?.let { FailCondition(FailKind.NET_WORTH_BELOW, it) }),
        timing = Timing(def.years, def.goldDays, def.silverDays),
        tags = listOf(tagFor(def)),
        hints = def.tips,
    ).normalised()

    private fun tagFor(def: ScenarioDef): DifficultyTag = when {
        def.years <= 3 -> DifficultyTag.SPRINT
        def.years >= 10 -> DifficultyTag.MARATHON
        def.ruinBelow != null -> DifficultyTag.HARSH
        else -> DifficultyTag.STANDARD
    }

    /** A draft for every scenario that ships, in catalog order. */
    fun builtIns(): List<ScenarioDraft> = ScenarioCatalog.all.map { fromDef(it) }
}
