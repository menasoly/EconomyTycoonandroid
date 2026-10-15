package app.many.economytycoon.advisor

enum class Topic(val label: String) {
    PRODUCTION("Production"),
    PEOPLE("People"),
    FINANCE("Finance"),
    PROPERTY("Property"),
    RESEARCH("Research"),
    MARKET("Market"),
}

/** One suggestion. [priority] runs from 0 to 100; higher is more worth doing soon. */
data class Insight(
    val code: String,
    val topic: Topic,
    val priority: Int,
    val title: String,
    val detail: String,
) {
    init { require(priority in 0..100) { "priority out of range" } }
}

/** What the advisor knows about one facility. Money is in whole units; ratios are parts per million. */
data class FacilityFact(
    val id: Long,
    val name: String,
    val running: Boolean,
    val utilizationPpm: Long,
    val conditionPpm: Long,
    val blocked: String?,
    val headcount: Long,
    val stockValueUnits: Long,
)

/**
 * Everything the rules look at, as plain numbers. Keeping it separate from the live game means a rule can be
 * tried against any situation just by writing the numbers down.
 */
data class AdvisorFacts(
    val tick: Long = 0L,
    val cashUnits: Long = 0L,
    val netWorthUnits: Long = 0L,
    val debtUnits: Long = 0L,
    val monthRevenueUnits: Long = 0L,
    val monthNetIncomeUnits: Long = 0L,
    val dailyPayrollUnits: Long = 0L,
    val facilities: List<FacilityFact> = emptyList(),
    val vacancies: Long = 0L,
    val averageMoralePpm: Long = 700_000L,
    val cohorts: Int = 0,
    val underpaidCohorts: Int = 0,
    val industryCount: Int = 0,
    val researchPoints: Long = 0L,
    val researchIdle: Boolean = false,
    val techsUnlocked: Int = 0,
    val techsTotal: Int = 0,
    val idleParcels: Int = 0,
    /** The highest interest rate on any loan being repaid, if there is one. */
    val loanRatePpm: Long? = null,
    val investedUnits: Long = 0L,
    val listed: Boolean = false,
    val contractOffers: Int = 0,
    val netMarginPpm: Long = 0L,
    val currentRatioPpm: Long = 2_000_000L,
) {
    val headcount: Long get() = facilities.sumOf { it.headcount }
    val stockUnits: Long get() = facilities.sumOf { it.stockValueUnits }
}

/** The numbers the rules compare against. */
data class AdvisorTuning(
    val idleCashShareOfWorthPpm: Long = 400_000L,
    val idleCashFloorUnits: Long = 150_000L,
    val expensiveLoanPpm: Long = 80_000L,
    val thinMarginPpm: Long = 40_000L,
    val idleUtilizationPpm: Long = 150_000L,
    val wornConditionPpm: Long = 500_000L,
    val lowMoralePpm: Long = 450_000L,
    val overstockShareOfWorthPpm: Long = 300_000L,
    val heavyPayrollShareOfRevenuePpm: Long = 700_000L,
    val concentrationFacilities: Int = 4,
    val listingWorthUnits: Long = 2_000_000L,
    val tightLiquidityPpm: Long = 1_000_000L,
)
