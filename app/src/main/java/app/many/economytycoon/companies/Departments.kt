package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.PaymentLine
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority

enum class Department(val label: String, val expenseChart: Chart) {
    RESEARCH("Research and development", Chart.RESEARCH_EXPENSE),
    MARKETING("Marketing", Chart.MARKETING_EXPENSE),
    HR("Human resources", Chart.OTHER_EXPENSE),
    LEGAL("Legal and compliance", Chart.OTHER_EXPENSE),
    OPERATIONS("Operations excellence", Chart.MAINTENANCE_EXPENSE),
}

/**
 * Budget a company allots to each department per tick. Effects saturate: the first money matters most and
 * each extra unit adds less, with a per-department half-saturation point that scales with company size.
 */
class DepartmentBudgets {
    private val budgets = java.util.EnumMap<Department, Money>(Department::class.java)

    fun budget(d: Department): Money = budgets[d] ?: Money.ZERO
    fun set(d: Department, perTick: Money) { budgets[d] = perTick.coerceAtLeast(Money.ZERO) }
    fun total(): Money = Money(budgets.values.sumOf { it.raw })

    /**
     * Saturating effect in ppm: spend / (spend + half) scaled to [maxPpm]. [sizeScale] is the company's
     * reference spend (for example payroll), so a corner shop and a conglomerate both feel a 2% budget.
     */
    fun effectPpm(d: Department, sizeScale: Money, maxPpm: Long): Long {
        val spend = budget(d).raw
        if (spend <= 0L) return 0L
        val half = (sizeScale.raw / 20L).coerceAtLeast(1L)
        return mulDiv(maxPpm, spend, spend + half)
    }

    fun intents(company: LedgerOwner, payee: LedgerOwner, tickValue: Long): List<SettlementIntent> {
        val out = ArrayList<SettlementIntent>()
        for (d in Department.values()) {
            val b = budget(d)
            if (b.raw <= 0L) continue
            out += SettlementIntent(
                "dept:${d.name.lowercase()}:${company.id}:$tickValue", company, payee, SettlementPriority.OTHER, ReasonCode.FEE,
                listOf(PaymentLine(d.expenseChart, Chart.REVENUE_OTHER, b)), deferrable = false, memo = d.label,
            )
        }
        return out
    }

    internal fun restore(values: Map<Department, Money>) { budgets.clear(); budgets.putAll(values) }
    internal fun entries(): List<Pair<Department, Money>> = budgets.entries.map { it.key to it.value }

    fun hashInto(h: CanonicalHasher) {
        for (d in Department.values()) h.putMoney(budget(d))
    }
}

/** Accumulated results of department spending. */
class DepartmentProgress {
    /** Research points available to spend on technologies. */
    var researchPoints: Long = 0
        internal set
    /** Legal shield against fines, ppm. Builds with spend and decays slowly. */
    var compliancePpm: Long = 0
        internal set
    /** Recruiting strength, ppm. */
    var recruitingPpm: Long = 0
        internal set
    /** Operating efficiency bonus, ppm. */
    var efficiencyPpm: Long = 0
        internal set

    fun spendResearch(points: Long): Boolean {
        if (points <= 0 || points > researchPoints) return false
        researchPoints -= points
        return true
    }

    fun advance(budgets: DepartmentBudgets, sizeScale: Money) {
        researchPoints += mulDiv(budgets.budget(Department.RESEARCH).raw, 1L, 10_000L)
        compliancePpm = decayToward(compliancePpm, budgets.effectPpm(Department.LEGAL, sizeScale, 900_000L))
        recruitingPpm = decayToward(recruitingPpm, budgets.effectPpm(Department.HR, sizeScale, 600_000L))
        efficiencyPpm = decayToward(efficiencyPpm, budgets.effectPpm(Department.OPERATIONS, sizeScale, 150_000L))
    }

    private fun decayToward(current: Long, target: Long): Long = current + (target - current) / 10

    internal fun restore(points: Long, compliance: Long, recruiting: Long, efficiency: Long) {
        researchPoints = points; compliancePpm = compliance; recruitingPpm = recruiting; efficiencyPpm = efficiency
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(researchPoints).putLong(compliancePpm).putLong(recruitingPpm).putLong(efficiencyPpm)
    }
}
