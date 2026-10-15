package app.many.economytycoon.session

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Economy

data class LineRow(
    val id: LineId,
    val priority: Int,
    val enabled: Boolean,
    val recipe: RecipeId?,
    val recipeName: String?,
    val running: Boolean,
    val progressPpm: Long,
    val utilizationPpm: Long,
    val batchesCompleted: Long,
    val machines: List<String>,
    val blocked: String?,
)

data class FacilityDetail(
    val row: FacilityRow,
    val policy: SchedulePolicy,
    val qualityAssurancePpm: Long,
    val pollutionPpm: Long,
    val unmaintainedTicks: Long,
    val lines: List<LineRow>,
    val stock: List<StockRow>,
    val storageUsedPpm: Long,
    val staff: List<CohortRow>,
    val monthlyProfit: Money,
    val canAddLine: Boolean,
)

/** The full picture of one facility for its detail screen. */
class FacilityDetails(private val e: Economy, private val reports: CompanyReports, private val people: PeopleReports) {

    fun detail(id: FacilityId): FacilityDetail? {
        val f = e.production.facility(id) ?: return null
        if (f.status == FacilityStatus.DEMOLISHED) return null
        val row = reports.facilities(f.owner).firstOrNull { it.id == id } ?: return null
        val type = e.content.facilityTypeOrNull(f.type)
        val lines = f.lines.sortedBy { it.priority }.map { l ->
            val batch = l.batch
            LineRow(
                id = l.id, priority = l.priority, enabled = l.enabled, recipe = l.recipe,
                recipeName = l.recipe?.let { e.content.recipeOrNull(it)?.name },
                running = batch != null, progressPpm = batch?.progressPpm?.coerceIn(0L, 1_000_000L) ?: 0L, utilizationPpm = l.utilizationPpm,
                batchesCompleted = l.batchesCompleted, machines = l.machines.map { e.content.machineTypeOrNull(it.type)?.name ?: Formatting.title(it.type.key) },
                blocked = l.blocked.takeIf { it != app.many.economytycoon.production.Blocked.None }?.let { it.javaClass.simpleName },
            )
        }
        val capacity = f.inventory.capacity.raw
        return FacilityDetail(
            row = row, policy = f.policy, qualityAssurancePpm = f.qualityAssurancePpm, pollutionPpm = f.pollutionPpm,
            unmaintainedTicks = f.unmaintainedTicks, lines = lines, stock = reports.stock(id),
            storageUsedPpm = if (capacity <= 0L) 0L else (f.inventory.used().raw * 1_000_000L / capacity).coerceIn(0L, 1_000_000L),
            staff = people.cohorts(f.owner).filter { it.facility == id },
            monthlyProfit = e.facilityStats.profitOf(id),
            canAddLine = type != null && f.lines.size < type.maxLines,
        )
    }
}
