package app.many.economytycoon.scenarios

import app.many.economytycoon.core.Money
import app.many.economytycoon.world.WorldSize

/** The scenarios that ship with the game, easiest first. */
object ScenarioCatalog {
    private fun m(units: Long) = Money.units(units)

    val all: List<ScenarioDef> = listOf(
        ScenarioDef(
            "first-harvest", "First Harvest", "Turn a small farm into a going concern.",
            "A modest inheritance and a few fields. Build up a working business, hire a crew and show the bank a profit.",
            m(600_000), 2, WorldSize.SMALL, 11L, 0,
            listOf(Goal(GoalKind.FACILITIES, 3), Goal(GoalKind.EMPLOYEES, 40), Goal(GoalKind.MONTHLY_PROFIT, 15_000)),
            2, 420, 600,
            tips = listOf("Staff every facility before you build the next one.", "Keep wages near the market rate to hold on to people."),
        ),
        ScenarioDef(
            "million-club", "The Million Club", "Make your first million.",
            "Your name is not yet known. Ten years of trading ahead of you and a rival who started a year earlier.",
            m(400_000), 4, WorldSize.SMALL, 23L, 0,
            listOf(Goal(GoalKind.NET_WORTH, 1_000_000)),
            6, 1_080, 1_620,
            tips = listOf("Reinvest early; idle cash earns nothing.", "A second industry protects you when one market turns."),
        ),
        ScenarioDef(
            "lean-times", "Lean Times", "Survive on a shoestring.",
            "Credit is tight and capital is thin. Grow without borrowing and finish debt free.",
            m(250_000), 5, WorldSize.SMALL, 37L, 0,
            listOf(Goal(GoalKind.DEBT_FREE, 4), Goal(GoalKind.CASH, 250_000)),
            5, 900, 1_350, ruinBelow = -100_000L,
            tips = listOf("Loans are tempting. Count the interest before you sign.", "Sell what you make as soon as it is made."),
        ),
        ScenarioDef(
            "industrial-age", "The Industrial Age", "Build a real industrial group.",
            "Three industries, a dozen sites and a payroll to match. Governments like a company that employs.",
            m(800_000), 5, WorldSize.MEDIUM, 41L, 0,
            listOf(Goal(GoalKind.FACILITIES, 12), Goal(GoalKind.INDUSTRIES, 3), Goal(GoalKind.EMPLOYEES, 250)),
            8, 1_500, 2_200,
            tips = listOf("Supply your own inputs where you can.", "Watch wage demands: big payrolls attract unions."),
        ),
        ScenarioDef(
            "tech-pioneer", "Tech Pioneer", "Win the race for new technology.",
            "Research is slow and costly, but whoever gets there first sets the prices.",
            m(700_000), 4, WorldSize.MEDIUM, 53L, 2,
            listOf(Goal(GoalKind.TECHNOLOGIES, 8), Goal(GoalKind.MONTHLY_PROFIT, 40_000)),
            7, 1_400, 2_000,
            tips = listOf("Queue several projects so labs never sit idle.", "License technology you cannot afford to research."),
        ),
        ScenarioDef(
            "land-baron", "Land Baron", "Make money from the ground under your feet.",
            "Cities grow and land follows. Buy early, rent smartly and sell at the top.",
            m(900_000), 4, WorldSize.SMALL, 67L, 0,
            listOf(Goal(GoalKind.PARCELS, 6), Goal(GoalKind.RENT_INCOME, 6_000), Goal(GoalKind.NET_WORTH, 1_500_000)),
            6, 1_200, 1_800,
            tips = listOf("Rent asked near the fair level finds a tenant fastest.", "Listings priced under appraisal go quickly."),
        ),
        ScenarioDef(
            "money-man", "Money Man", "Let the markets work for you.",
            "Bonds, futures and shares. Take the time to understand each before you commit.",
            m(1_200_000), 6, WorldSize.MEDIUM, 71L, 1,
            listOf(Goal(GoalKind.INVESTED, 800_000), Goal(GoalKind.CREDIT_RANK, 5), Goal(GoalKind.NET_WORTH, 2_500_000)),
            8, 1_500, 2_300,
            tips = listOf("Hedge stock you hold before the price moves.", "Margin calls close positions without warning."),
        ),
        ScenarioDef(
            "hostile-takeover", "Hostile Takeover", "Buy your way to the top.",
            "A crowded field and a lot of cash. Gather controlling stakes in the companies that matter.",
            m(2_000_000), 8, WorldSize.MEDIUM, 83L, 0,
            listOf(Goal(GoalKind.COMPANIES, 2), Goal(GoalKind.NET_WORTH, 6_000_000)),
            8, 1_700, 2_400,
            tips = listOf("List your own company to raise money for the buyout.", "Rivals defend what they are worth; pick weak ones."),
        ),
        ScenarioDef(
            "market-leader", "Number One", "End up the richest company in the world.",
            "Everybody else is trying the same thing. There is only room for one at the top.",
            m(1_000_000), 9, WorldSize.LARGE, 97L, 0,
            listOf(Goal(GoalKind.MARKET_LEADER, 1), Goal(GoalKind.NET_WORTH, 10_000_000)),
            12, 2_500, 3_600,
            tips = listOf("Grow steadily; the leader changes hands often.", "Do not neglect cash: rich companies fail on liquidity."),
        ),
        ScenarioDef(
            "tycoon", "Tycoon", "The long game: a hundred million.",
            "Fifteen years, a hostile field and no rescue. Reach a hundred million and hold a place among the best credit risks.",
            m(500_000), 10, WorldSize.LARGE, 101L, 0,
            listOf(Goal(GoalKind.NET_WORTH, 100_000_000), Goal(GoalKind.CREDIT_RANK, 7), Goal(GoalKind.EMPLOYEES, 1_000)),
            15, 3_600, 4_800, ruinBelow = -500_000L,
            tips = listOf("Compounding does the work. Keep reinvesting.", "A strong rating makes borrowing cheap when you need it."),
        ),
    )

    fun byId(id: String): ScenarioDef? = all.firstOrNull { it.id == id }
    fun require(id: String): ScenarioDef = byId(id) ?: error("unknown scenario $id")

    /** The scenario to try after [id], if any. */
    fun next(id: String): ScenarioDef? = all.getOrNull(all.indexOfFirst { it.id == id } + 1)
}
