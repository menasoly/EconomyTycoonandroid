package app.many.economytycoon.tutorial

enum class Lesson(val label: String) {
    BASICS("Getting your bearings"),
    PRODUCTION("Making things"),
    MONEY("Handling money"),
    GROWTH("Growing the company"),
}

/** One thing to learn: what to read, what to do, and how the game can tell it is done. */
data class TutorialStep(
    val id: String,
    val lesson: Lesson,
    val title: String,
    val body: String,
    val goal: Goal,
    /** Route key of a screen that helps with this step, if any. */
    val screen: String? = null,
)

/** The ordered steps of the tutorial. Order matters: each step assumes the ones before it. */
object TutorialScript {
    val steps: List<TutorialStep> = listOf(
        TutorialStep("welcome", Lesson.BASICS, "Welcome", "You run a company in a living economy. Prices move with supply and demand, rivals compete with you, and every day the clock advances. This guide walks through the first things worth doing.", Goal.Acknowledge),
        TutorialStep("market", Lesson.BASICS, "Look at the market", "Every good has a price in every region. Cheap goods in one place can sell for more in another. Open the market to see what things cost.", Goal.Visit("market"), "market"),
        TutorialStep("trade", Lesson.BASICS, "Find a trade", "The trade finder lists goods that sell for more somewhere else, after freight and tariffs. It is a quick way to see where money can be made.", Goal.Visit("trade"), "trade"),
        TutorialStep("planner", Lesson.PRODUCTION, "Plan a supply chain", "Before you build, the planner shows what a product needs: which buildings, how many staff and what to buy in. Pick any product and try a few quantities.", Goal.Visit("planner"), "planner"),
        TutorialStep("build", Lesson.PRODUCTION, "Build your first facility", "Facilities turn inputs into goods. Pick a type that suits your region, choose a site and start building. It takes a few days to finish.", Goal.Reach(Gauge.FACILITIES, 1), "build"),
        TutorialStep("hire", Lesson.PRODUCTION, "Hire your first staff", "A facility cannot run without people. Open the people screen and hire for the roles it needs. Wages come out of cash every week.", Goal.Reach(Gauge.EMPLOYEES, 1), "people"),
        TutorialStep("running", Lesson.PRODUCTION, "Get a facility running", "Once the building is finished and staffed, it starts producing. Check that it has the inputs it needs and somewhere to sell what it makes.", Goal.Reach(Gauge.RUNNING, 1), "facilities"),
        TutorialStep("revenue", Lesson.MONEY, "Make your first sales", "Goods you produce sell on the local market. Revenue is what comes in; it is not yet profit.", Goal.Reach(Gauge.REVENUE, 1), "finance"),
        TutorialStep("statements", Lesson.MONEY, "Read your statements", "The income statement shows revenue and costs, the balance sheet shows what you own and owe, and the cash flow shows where money moved.", Goal.Visit("statements"), "statements"),
        TutorialStep("credit", Lesson.MONEY, "Check your credit", "Banks lend according to your credit rating. Borrowing can fund growth, but interest is a cost that continues whether or not you are earning.", Goal.Visit("credit"), "credit"),
        TutorialStep("profit", Lesson.MONEY, "Turn a profit", "Profit is revenue less every cost: materials, wages, energy, upkeep and interest. If it is negative, the advisor can suggest where to look.", Goal.Reach(Gauge.PROFIT, 1), "advisor"),
        TutorialStep("research", Lesson.GROWTH, "Start researching", "Technology makes production cheaper or unlocks new products. Research takes time and money, so pick what your business needs.", Goal.Visit("research"), "research"),
        TutorialStep("contract", Lesson.GROWTH, "Sign a supply contract", "Contracts fix a price and a quantity with another company. They steady your income and your costs, and they bind you to deliver.", Goal.Reach(Gauge.CONTRACTS, 1), "contracts"),
        TutorialStep("vehicle", Lesson.GROWTH, "Buy a vehicle", "Your own vehicles move goods between regions. Freight is a cost on every shipment, and owning the trucks can lower it.", Goal.Reach(Gauge.VEHICLES, 1), "logistics"),
        TutorialStep("value", Lesson.GROWTH, "Value your company", "The valuation screen estimates what the company is worth by several methods. It matters when you sell shares or consider buying a rival.", Goal.Visit("valuation"), "valuation"),
        TutorialStep("survive", Lesson.GROWTH, "Keep going for three months", "A company is judged over time. Keep it solvent for ninety days and you have learned the basics.", Goal.Reach(Gauge.DAY, 90)),
        TutorialStep("float", Lesson.GROWTH, "Go public", "Listing on the exchange sells shares to the public and raises money. It also puts a price on your company that rises and falls with your results.", Goal.Listed, "exchange"),
    )

    val ids: List<String> get() = steps.map { it.id }

    fun step(id: String): TutorialStep? = steps.firstOrNull { it.id == id }

    fun byLesson(): Map<Lesson, List<TutorialStep>> = steps.groupBy { it.lesson }
}
