package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv

/** Services a city pays for. [monthlyPerResident] is the cost at the standard service level of 500. */
enum class Service(val label: String, val monthlyPerResident: Money) {
    POLICE("Police", Money.units(11)),
    FIRE("Fire and rescue", Money.units(6)),
    EDUCATION("Education", Money.units(34)),
    HEALTH("Health", Money.units(24)),
    SANITATION("Sanitation", Money.units(8)),
    PARKS("Parks", Money.units(5)),
    PLANNING("Planning office", Money.units(2)),
    HOUSING_AID("Housing aid", Money.units(3)),
}

enum class RevenueLine(val label: String) {
    PROPERTY_TAX("Property tax"), INCOME_TAX("Income tax"), BUSINESS_TAX("Business tax"), TRANSFERS("National transfers"),
    FARES("Transit fares"), TOLLS("Toll share"), FEES("Permits and fees"),
}

enum class ExpenseLine(val label: String) {
    SERVICES("Services"), TRANSIT("Transit operations"), DEBT_SERVICE("Debt service"), CAPITAL("Capital projects"), AVAILABILITY("Availability payments"),
}

/** Tax rates a city sets. The property tax is a share of assessed value per year; the income tax a share of wages. */
data class TaxRates(
    val propertyPerYearPpm: Long = 9_000L,
    val incomePpm: Long = 25_000L,
    /** Business tax per square metre of commercial and industrial floor area per month. */
    val businessPerSqm: Money = Money.cents(60),
) {
    init {
        require(propertyPerYearPpm in 0..MAX_PROPERTY_PPM) { "property tax out of range" }
        require(incomePpm in 0..MAX_INCOME_PPM) { "income tax out of range" }
        require(businessPerSqm.raw >= 0L) { "business tax cannot be negative" }
    }

    companion object {
        const val MAX_PROPERTY_PPM = 40_000L
        const val MAX_INCOME_PPM = 80_000L
    }
}

/** What the economy contributed to a city's accounts this month. */
data class BudgetInputs(
    val population: Long,
    val monthlyWages: Money,
    val assessedValue: Money,
    val businessFloorArea: Long,
    val transferPerResident: Money,
    val fares: Money,
    val tollShare: Money,
    val fees: Money,
    val transitOperatingCost: Money,
    val availabilityPayments: Money,
)

/** The month's accounts. Lines are ordered by their enum position. */
class BudgetReport(
    val revenue: Map<RevenueLine, Money>,
    val expenses: Map<ExpenseLine, Money>,
    val openingCash: Money,
    val closingCash: Money,
    val borrowed: Money,
    val debt: Money,
    val fundingRatioPpm: Long,
    val ratingPermille: Int,
) {
    val totalRevenue: Money get() = Money.sum(revenue.values)
    val totalExpenses: Money get() = Money.sum(expenses.values)
    val balance: Money get() = totalRevenue - totalExpenses
}

/**
 * A city's finances: tax rates, service levels, cash, debt. Each month revenue is collected and services, transit,
 * interest and agreed payments are paid. If cash runs out the city borrows up to its limit; whatever cannot be paid
 * for cuts service quality in proportion. Service quality follows the funded level with a lag.
 */
class MunicipalBudget {
    var rates: TaxRates = TaxRates()
        internal set
    var cash: Money = Money.units(2_000_000)
        internal set
    var debt: Money = Money.ZERO
        internal set
    var annualBaseRatePpm: Long = 40_000L
        internal set
    var lastRevenue: Money = Money.ZERO
        internal set
    var lastReport: BudgetReport? = null
        internal set
    var consecutiveDeficitMonths: Int = 0
        internal set

    private val levels = IntArray(Service.values().size) { 500 }
    private val quality = IntArray(Service.values().size) { 500 }

    fun level(s: Service): Int = levels[s.ordinal]
    fun quality(s: Service): Int = quality[s.ordinal]
    fun levelsList(): List<Int> = levels.toList()
    fun qualityList(): List<Int> = quality.toList()

    fun setLevel(s: Service, permille: Int): Outcome<Unit> {
        if (permille !in 0..1000) return Outcome.reject(Rules.INVALID_ARGUMENT, "service level is between 0 and 1000")
        levels[s.ordinal] = permille
        return Outcome.ok(Unit)
    }

    fun setRates(r: TaxRates) { rates = r }

    /** A one-off improvement of a service's quality from a finished public building. */
    internal fun boostQuality(s: Service, points: Int) { quality[s.ordinal] = (quality[s.ordinal] + points).coerceIn(0, 1000) }

    /** Money a service costs per resident per month at its level; 40 percent at zero, 160 percent at full. */
    fun costMultiplierPpm(level: Int): Long = 400_000L + 1_200L * level

    fun serviceCost(s: Service, population: Long): Money =
        Money(mulDiv(s.monthlyPerResident.raw * population, costMultiplierPpm(levels[s.ordinal]), Scale.PPM))

    fun totalServiceCost(population: Long): Money = Money.sum(Service.values().map { serviceCost(it, population) })

    /** Debt the city may carry: some months of its revenue. */
    fun debtLimit(params: UrbanParams): Money = Money(lastRevenue.raw * params.debtLimitMonthsOfRevenue)

    /** Credit standing, 0..1000, from the debt as a share of a year's revenue. */
    fun rating(): Int {
        val year = lastRevenue.raw * Scale.MONTHS_PER_YEAR
        if (year <= 0L) return if (debt.isPositive) 0 else 700
        val ratio = mulDiv(debt.raw, Scale.PPM, year) // 1.0 means a year of revenue owed
        return (1000L - ratio * 700L / Scale.PPM - consecutiveDeficitMonths * 15L).coerceIn(0L, 1000L).toInt()
    }

    /** Annual interest the city pays on borrowing, ppm: the base rate plus a spread for poor credit. */
    fun interestRatePpm(): Long = annualBaseRatePpm + (1000 - rating()) * 60L

    fun borrow(amount: Money, params: UrbanParams): Outcome<Money> {
        if (!amount.isPositive) return Outcome.reject(Rules.INVALID_ARGUMENT, "borrow a positive amount")
        if (debt + amount > debtLimit(params)) return Outcome.reject(Rules.CREDIT_LIMIT, "that would take the city over its debt limit")
        debt += amount
        cash += amount
        return Outcome.ok(debt)
    }

    fun repay(amount: Money): Outcome<Money> {
        if (!amount.isPositive) return Outcome.reject(Rules.INVALID_ARGUMENT, "repay a positive amount")
        if (amount > debt) return Outcome.reject(Rules.INVALID_ARGUMENT, "the city does not owe that much")
        if (amount > cash) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the city does not have that much cash")
        debt -= amount
        cash -= amount
        return Outcome.ok(debt)
    }

    /** Cash that may be spent on projects without starving services: what is left after a month of running costs. */
    fun spendable(population: Long, transitCost: Money): Money =
        Money.max(Money.ZERO, cash - totalServiceCost(population) - transitCost)

    /** Pays for a capital project out of cash; refused if the city cannot afford it. */
    fun spendCapital(amount: Money): Boolean {
        if (amount.isNegative || amount > cash) return false
        cash -= amount
        return true
    }

    /** Takes in a one-off payment, for example a concession fee. */
    fun receive(amount: Money) { if (amount.isPositive) cash += amount }

    fun settleMonth(inputs: BudgetInputs, capitalSpent: Money, params: UrbanParams): BudgetReport {
        val opening = cash
        val revenue = java.util.EnumMap<RevenueLine, Money>(RevenueLine::class.java)
        revenue[RevenueLine.PROPERTY_TAX] = Money(mulDiv(inputs.assessedValue.raw, rates.propertyPerYearPpm, Scale.PPM * Scale.MONTHS_PER_YEAR))
        revenue[RevenueLine.INCOME_TAX] = inputs.monthlyWages.times(app.many.economytycoon.core.Rate(rates.incomePpm))
        revenue[RevenueLine.BUSINESS_TAX] = rates.businessPerSqm * inputs.businessFloorArea
        revenue[RevenueLine.TRANSFERS] = inputs.transferPerResident * inputs.population
        revenue[RevenueLine.FARES] = inputs.fares
        revenue[RevenueLine.TOLLS] = inputs.tollShare
        revenue[RevenueLine.FEES] = inputs.fees
        val totalRevenue = Money.sum(revenue.values)
        lastRevenue = totalRevenue
        cash += totalRevenue

        val interest = Money(mulDiv(debt.raw, interestRatePpm(), Scale.PPM * Scale.MONTHS_PER_YEAR))
        val services = totalServiceCost(inputs.population)
        val fixed = inputs.transitOperatingCost + interest + inputs.availabilityPayments
        var borrowed = Money.ZERO
        val need = services + fixed
        if (cash < need) {
            val room = Money.max(Money.ZERO, debtLimit(params) - debt)
            val take = Money.min(need - cash, room)
            if (take.isPositive) { debt += take; cash += take; borrowed = take }
        }
        // Fixed obligations are paid first; services get whatever is left, so cuts fall on service quality.
        val paidFixed = Money.min(fixed, Money.max(Money.ZERO, cash))
        cash -= paidFixed
        val availableForServices = Money.max(Money.ZERO, cash)
        val paidServices = Money.min(services, availableForServices)
        cash -= paidServices
        val funding = if (services.raw == 0L) Scale.PPM else mulDiv(paidServices.raw, Scale.PPM, services.raw)
        for (s in Service.values()) {
            val target = mulDiv(levels[s.ordinal].toLong(), funding, Scale.PPM)
            quality[s.ordinal] = (quality[s.ordinal] + (target - quality[s.ordinal]) * 15L / 100L).toInt().coerceIn(0, 1000)
        }
        val expenses = java.util.EnumMap<ExpenseLine, Money>(ExpenseLine::class.java)
        expenses[ExpenseLine.SERVICES] = paidServices
        expenses[ExpenseLine.TRANSIT] = Money.min(inputs.transitOperatingCost, paidFixed)
        expenses[ExpenseLine.DEBT_SERVICE] = interest
        expenses[ExpenseLine.CAPITAL] = capitalSpent
        expenses[ExpenseLine.AVAILABILITY] = inputs.availabilityPayments
        val balance = totalRevenue - Money.sum(expenses.values)
        consecutiveDeficitMonths = if (balance.isNegative || funding < Scale.PPM) consecutiveDeficitMonths + 1 else 0
        val report = BudgetReport(
            revenue.toSortedMap(), expenses.toSortedMap(), opening, cash, borrowed, debt, funding, rating(),
        )
        lastReport = report
        return report
    }

    /** Effects of service quality on the surfaces of the city. */
    fun effects(): ServiceEffects = ServiceEffects(quality(Service.POLICE), quality(Service.SANITATION), quality(Service.PARKS))

    internal fun restore(
        rates: TaxRates, cash: Money, debt: Money, baseRate: Long, lastRevenue: Money, deficitMonths: Int, levels: List<Int>, quality: List<Int>,
    ) {
        require(levels.size == this.levels.size && quality.size == this.quality.size) { "wrong number of services" }
        this.rates = rates; this.cash = cash; this.debt = debt; annualBaseRatePpm = baseRate; this.lastRevenue = lastRevenue
        consecutiveDeficitMonths = deficitMonths
        for (i in levels.indices) { this.levels[i] = levels[i]; this.quality[i] = quality[i] }
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(rates.propertyPerYearPpm).putLong(rates.incomePpm).putMoney(rates.businessPerSqm)
        h.putMoney(cash).putMoney(debt).putLong(annualBaseRatePpm).putMoney(lastRevenue).putInt(consecutiveDeficitMonths)
        for (v in levels) h.putInt(v)
        for (v in quality) h.putInt(v)
    }
}
