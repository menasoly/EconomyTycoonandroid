package app.many.economytycoon.achievements

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.Medal

/** The achievements that ship with the game. */
object AchievementCatalog {
    private fun meter(id: String, title: String, desc: String, c: Category, t: Tier, kind: GoalKind, target: Long) =
        Achievement(id, title, desc, c, t, Check.Meter(kind, target))

    private fun total(id: String, title: String, desc: String, c: Category, t: Tier, stat: Stat, target: Long) =
        Achievement(id, title, desc, c, t, Check.Total(stat, target))

    private fun medals(id: String, title: String, desc: String, t: Tier, medal: Medal, count: Int) =
        Achievement(id, title, desc, Category.CAMPAIGN, t, Check.Medals(medal, count))

    val all: List<Achievement> = listOf(
        meter("six-figures", "Six Figures", "Reach a net worth of 100 thousand.", Category.WEALTH, Tier.BRONZE, GoalKind.NET_WORTH, 100_000),
        meter("first-million", "First Million", "Reach a net worth of 1 million.", Category.WEALTH, Tier.SILVER, GoalKind.NET_WORTH, 1_000_000),
        meter("ten-million", "Eight Figures", "Reach a net worth of 10 million.", Category.WEALTH, Tier.GOLD, GoalKind.NET_WORTH, 10_000_000),
        meter("rainy-day", "Rainy Day Fund", "Hold 500 thousand in cash.", Category.WEALTH, Tier.SILVER, GoalKind.CASH, 500_000),
        meter("black-ink", "In the Black", "Earn 25 thousand profit in a month.", Category.WEALTH, Tier.SILVER, GoalKind.MONTHLY_PROFIT, 25_000),
        meter("number-one", "Top of the Table", "Become the richest company.", Category.WEALTH, Tier.GOLD, GoalKind.MARKET_LEADER, 1),

        meter("open-for-business", "Open for Business", "Run a facility.", Category.GROWTH, Tier.BRONZE, GoalKind.FACILITIES, 1),
        meter("small-chain", "Small Chain", "Run 5 facilities.", Category.GROWTH, Tier.BRONZE, GoalKind.FACILITIES, 5),
        meter("sprawl", "Sprawl", "Run 25 facilities.", Category.GROWTH, Tier.SILVER, GoalKind.FACILITIES, 25),
        meter("big-employer", "Big Employer", "Employ 500 people.", Category.GROWTH, Tier.SILVER, GoalKind.EMPLOYEES, 500),
        meter("company-town", "Company Town", "Employ 2000 people.", Category.GROWTH, Tier.GOLD, GoalKind.EMPLOYEES, 2_000),
        meter("diversified", "Diversified", "Operate in 3 industries.", Category.GROWTH, Tier.SILVER, GoalKind.INDUSTRIES, 3),
        meter("laboratory", "Laboratory", "Unlock 5 technologies.", Category.GROWTH, Tier.BRONZE, GoalKind.TECHNOLOGIES, 5),
        meter("frontier", "At the Frontier", "Unlock 15 technologies.", Category.GROWTH, Tier.GOLD, GoalKind.TECHNOLOGIES, 15),

        meter("good-credit", "Good Credit", "Earn a credit rating 3 steps above the bottom.", Category.FINANCE, Tier.BRONZE, GoalKind.CREDIT_RANK, 3),
        meter("portfolio", "Portfolio", "Have 500 thousand in bonds and futures.", Category.FINANCE, Tier.SILVER, GoalKind.INVESTED, 500_000),
        meter("empire-builder", "Empire Builder", "Control another company.", Category.FINANCE, Tier.SILVER, GoalKind.COMPANIES, 1),
        total("going-public", "Going Public", "List a company on the exchange.", Category.FINANCE, Tier.BRONZE, Stat.PUBLIC_OFFERINGS, 1),
        total("margin-call", "Margin Call", "Have a futures position liquidated.", Category.FINANCE, Tier.BRONZE, Stat.FUTURES_LIQUIDATED, 1),
        total("hedger", "Hedger", "Open 10 futures positions.", Category.FINANCE, Tier.SILVER, Stat.FUTURES_OPENED, 10),

        meter("landowner", "Landowner", "Own 5 parcels of land.", Category.PROPERTY, Tier.BRONZE, GoalKind.PARCELS, 5),
        meter("rentier", "Rentier", "Collect 5 thousand a month in rent.", Category.PROPERTY, Tier.SILVER, GoalKind.RENT_INCOME, 5_000),
        total("landlord", "Landlord", "Sign 5 leases.", Category.PROPERTY, Tier.BRONZE, Stat.LEASES_SIGNED, 5),
        total("flipper", "Flipper", "Sell 3 parcels.", Category.PROPERTY, Tier.SILVER, Stat.PARCELS_SOLD, 3),

        total("public-supplier", "Public Supplier", "Win a government tender.", Category.COMMERCE, Tier.BRONZE, Stat.TENDERS_WON, 1),
        total("trusted-contractor", "Trusted Contractor", "Deliver 10 tenders.", Category.COMMERCE, Tier.SILVER, Stat.TENDERS_DELIVERED, 10),
        total("picket-line", "Picket Line", "Live through 3 strikes.", Category.COMMERCE, Tier.BRONZE, Stat.STRIKES_SUFFERED, 3),

        medals("first-win", "First Win", "Win a scenario.", Tier.BRONZE, Medal.BRONZE, 1),
        medals("campaigner", "Campaigner", "Win 5 scenarios.", Tier.SILVER, Medal.BRONZE, 5),
        medals("silver-lining", "Silver Lining", "Win 3 scenarios with at least a silver medal.", Tier.SILVER, Medal.SILVER, 3),
        medals("gold-standard", "Gold Standard", "Win 3 scenarios with a gold medal.", Tier.GOLD, Medal.GOLD, 3),
        medals("completionist", "Completionist", "Win every scenario.", Tier.GOLD, Medal.BRONZE, app.many.economytycoon.scenarios.ScenarioCatalog.all.size),

        total("one-year", "One Year In", "Play for 360 days in all.", Category.ENDURANCE, Tier.BRONZE, Stat.DAYS_PLAYED, 360),
        total("veteran", "Veteran", "Play for 3600 days in all.", Category.ENDURANCE, Tier.SILVER, Stat.DAYS_PLAYED, 3_600),
        total("lifer", "Lifer", "Play for 18000 days in all.", Category.ENDURANCE, Tier.GOLD, Stat.DAYS_PLAYED, 18_000),
        total("regular", "Regular", "Start 10 games.", Category.ENDURANCE, Tier.BRONZE, Stat.GAMES_STARTED, 10),
    )

    private val index = all.associateBy { it.id }

    fun byId(id: String): Achievement? = index[id]
    fun require(id: String): Achievement = index[id] ?: throw NoSuchElementException("no achievement $id")
    fun inCategory(c: Category): List<Achievement> = all.filter { it.category == c }
    val maxPoints: Int get() = all.sumOf { it.points }

    /** Problems with the list itself; empty when it is sound. */
    fun problems(): List<String> = problemsIn(all)

    fun problemsIn(list: List<Achievement>): List<String> {
        val out = ArrayList<String>()
        for (a in list) {
            if (!a.id.matches(Regex("[a-z0-9-]+"))) out += "${a.id}: id must be lowercase letters, digits and dashes"
            if (a.title.length !in 3..30) out += "${a.id}: title must be three to thirty characters"
            if (a.description.isBlank()) out += "${a.id}: needs a description"
            when (val c = a.check) {
                is Check.Meter -> if (c.target <= 0L) out += "${a.id}: meter target must be positive"
                is Check.Total -> if (c.target <= 0L) out += "${a.id}: total target must be positive"
                is Check.Medals -> {
                    if (c.count <= 0 || c.count > app.many.economytycoon.scenarios.ScenarioCatalog.all.size) out += "${a.id}: medal count out of range"
                    if (c.medal == Medal.NONE) out += "${a.id}: a medal check needs a real medal"
                }
            }
        }
        for ((id, n) in list.groupingBy { it.id }.eachCount()) if (n > 1) out += "$id appears $n times"
        for ((t, n) in list.groupingBy { it.title }.eachCount()) if (n > 1) out += "title '$t' appears $n times"
        return out
    }
}
