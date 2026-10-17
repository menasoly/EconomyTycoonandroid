package app.many.economytycoon.blueprint

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.workforce.Cohort

/** One production line as the player sees it today. */
data class LineView(
    val id: LineId,
    val priority: Int,
    val enabled: Boolean,
    val recipe: RecipeId?,
    val machines: List<MachineTypeId>,
    /** True while a batch is in progress; the recipe cannot be changed until it ends. */
    val running: Boolean,
)

/** Everything configurable about one facility, detached from the live simulation. */
data class FacilityView(
    val id: FacilityId,
    val type: FacilityTypeId,
    val name: String,
    val region: RegionId,
    val operating: Boolean,
    val lines: List<LineView>,
    val policy: SchedulePolicy,
    val qualityPpm: Long,
    val staff: List<StaffSpec>,
    val sells: List<SellSpec>,
    val buys: List<BuySpec>,
    val storageCapacity: Quantity,
    val storageExpansions: Int,
) {
    /** The configuration as a spec, without any capture options applied. */
    fun toSpec(): BlueprintSpec = BlueprintSpec(
        facilityType = type,
        lines = lines.sortedWith(compareBy({ it.priority }, { it.id })).map { LineSpec(it.priority, it.enabled, it.recipe, it.machines) },
        policy = policy, qualityPpm = qualityPpm, staff = staff, sells = sells, buys = buys, storageExpansions = storageExpansions,
    ).normalized()
}

/** Builds [FacilityView]s from the live production objects; the only place that touches simulation classes. */
object FacilityViewReader {
    /** Storage expansions grow capacity by a quarter each time (see the production service). */
    private const val EXPANSION_PPM = 250_000L

    fun read(
        f: Facility,
        content: ContentRepository,
        sells: List<SellRule> = emptyList(),
        buys: List<BuyRule> = emptyList(),
        cohorts: List<Cohort> = emptyList(),
    ): FacilityView? {
        if (f.status == FacilityStatus.DEMOLISHED) return null
        val lines = f.lines.map { l ->
            LineView(l.id, l.priority, l.enabled, l.recipe, l.machines.map { it.type }.distinct().sorted(), l.batch != null)
        }
        val staff = cohorts.filter { it.key.facility == f.id }.sortedBy { it.key.role }.map {
            StaffSpec(it.key.role, it.target.coerceIn(0L, BlueprintLimits.MAX_STAFF_TARGET), it.wage.takeIf { w -> w.raw > 0 }, it.trainingPpm.coerceIn(0L, 500_000L))
        }
        val base = content.facilityTypeOrNull(f.type)?.storageCapacity ?: f.inventory.capacity
        return FacilityView(
            id = f.id, type = f.type, name = f.name, region = f.region, operating = f.isOperating(), lines = lines,
            policy = f.policy, qualityPpm = f.qualityAssurancePpm.coerceIn(0L, BlueprintLimits.PPM), staff = staff,
            sells = sells.filter { it.facility == f.id }.map { SellSpec.of(it) },
            buys = buys.filter { it.facility == f.id }.map { BuySpec.of(it) },
            storageCapacity = f.inventory.capacity, storageExpansions = expansionsOf(base, f.inventory.capacity),
        )
    }

    /** How many 25% expansions turn [base] into [current]; stops at the limit. */
    fun expansionsOf(base: Quantity, current: Quantity): Int {
        var cap = base.raw
        var n = 0
        while (cap < current.raw && n < BlueprintLimits.MAX_STORAGE_EXPANSIONS) {
            cap += mulDiv(cap, EXPANSION_PPM, BlueprintLimits.PPM)
            n++
        }
        return n
    }
}
