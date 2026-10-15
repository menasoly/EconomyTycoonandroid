package app.many.economytycoon.ui.present

import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.session.FacilityDetail
import app.many.economytycoon.session.FacilityRow
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

enum class FacilitySort(val label: String) { NAME("Name"), CONDITION("Condition"), UTILIZATION("Utilization"), HEADCOUNT("Staff"), STOCK("Stock value") }

data class FacilityListItem(
    val id: FacilityId,
    val title: String,
    val subtitle: String,
    val status: String,
    val statusTone: Tone,
    val condition: Double,
    val conditionTone: Tone,
    val utilization: Double,
    val blocked: String?,
    val stockValue: String,
)

data class FacilityListModel(val items: List<FacilityListItem>, val tiles: List<Tile>, val hiddenByFilter: Int)

object FacilitiesPresenter {
    fun statusLabel(s: FacilityStatus): String = when (s) {
        FacilityStatus.OPERATING -> "Operating"
        FacilityStatus.SHUTDOWN -> "Shut down"
        FacilityStatus.DEMOLISHED -> "Demolished"
    }

    fun statusTone(r: FacilityRow): Tone = when {
        r.status == FacilityStatus.SHUTDOWN -> Tone.MUTED
        r.blockedReason != null && r.activeLines == 0 -> Tone.BAD
        r.blockedReason != null -> Tone.WARN
        r.status == FacilityStatus.OPERATING -> Tone.GOOD
        else -> Tone.NEUTRAL
    }

    fun list(session: GameSession, sort: FacilitySort = FacilitySort.NAME, query: String = "", onlyProblems: Boolean = false): FacilityListModel {
        val company = session.selectedCompany ?: return FacilityListModel(emptyList(), emptyList(), 0)
        val all = session.reports.company.facilities(company)
        val q = query.trim().lowercase()
        val matched = all.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.typeName.lowercase().contains(q) || it.region.lowercase().contains(q) }
        val shown = matched.filter { !onlyProblems || it.blockedReason != null || it.conditionPpm < 400_000L }
        val sorted = when (sort) {
            FacilitySort.NAME -> shown.sortedBy { it.name.lowercase() }
            FacilitySort.CONDITION -> shown.sortedBy { it.conditionPpm }
            FacilitySort.UTILIZATION -> shown.sortedByDescending { it.utilizationPpm }
            FacilitySort.HEADCOUNT -> shown.sortedByDescending { it.headcount }
            FacilitySort.STOCK -> shown.sortedByDescending { it.stockValue.raw }
        }
        val money = session.settings::money
        val items = sorted.map { r ->
            FacilityListItem(
                id = r.id, title = r.name, subtitle = "${r.typeName} · ${r.region}", status = statusLabel(r.status), statusTone = statusTone(r),
                condition = r.conditionPpm / 1_000_000.0, conditionTone = Tones.health(r.conditionPpm), utilization = r.utilizationPpm / 1_000_000.0,
                blocked = r.blockedReason, stockValue = money(r.stockValue),
            )
        }
        val operating = all.count { it.status == FacilityStatus.OPERATING }
        val tiles = listOf(
            Tile("Facilities", all.size.toString(), note = "$operating operating"),
            Tile("Staff", Formatting.grouped(all.sumOf { it.headcount })),
            Tile("Stock value", money(app.many.economytycoon.core.Money(all.sumOf { it.stockValue.raw }))),
            Tile("Needing attention", all.count { it.blockedReason != null || it.conditionPpm < 400_000L }.toString(), if (all.any { it.blockedReason != null }) Tone.WARN else Tone.GOOD),
        )
        return FacilityListModel(items, tiles, all.size - shown.size)
    }

    fun policyLabel(p: SchedulePolicy): String = when (p) {
        SchedulePolicy.PRIORITY -> "By line priority"
        SchedulePolicy.AUTO_MARGIN -> "Best margin first"
    }
}

data class LineItem(
    val id: Long,
    val title: String,
    val state: String,
    val tone: Tone,
    val progress: Double,
    val utilization: String,
    val machines: String,
    val batches: String,
)

data class FacilityDetailModel(
    val id: FacilityId,
    val title: String,
    val subtitle: String,
    val facts: List<Fact>,
    val lines: List<LineItem>,
    val stock: List<Fact>,
    val staff: List<Fact>,
    val canAddLine: Boolean,
    val operating: Boolean,
    val policy: String,
    val roles: List<RoleChoice>,
    val recipes: List<RecipeChoice>,
    val type: app.many.economytycoon.core.FacilityTypeId,
    val maintenanceHint: String,
)

/** A job the facility needs people for, with the current and legal-minimum wage for the staffing form. */
data class RoleChoice(val role: app.many.economytycoon.core.RoleId, val name: String, val headcount: Long, val target: Long, val needed: Long, val minimumWageText: String)

data class RecipeChoice(val id: app.many.economytycoon.core.RecipeId, val name: String, val inputs: String, val outputs: String, val duration: String, val locked: Boolean, val lockNote: String?)

object FacilityDetailPresenter {
    fun build(session: GameSession, id: FacilityId): FacilityDetailModel? {
        val d = session.reports.details.detail(id) ?: return null
        val money = session.settings::money
        val row = d.row
        val facts = listOf(
            Fact("Status", FacilitiesPresenter.statusLabel(row.status), FacilitiesPresenter.statusTone(row)),
            Fact("Condition", Formatting.percentPpm(row.conditionPpm), Tones.health(row.conditionPpm)),
            Fact("Utilization", Formatting.percentPpm(row.utilizationPpm)),
            Fact("Storage used", Formatting.percentPpm(d.storageUsedPpm), if (d.storageUsedPpm > 900_000L) Tone.WARN else Tone.NEUTRAL),
            Fact("Quality assurance", Formatting.percentPpm(d.qualityAssurancePpm)),
            Fact("Pollution", Formatting.percentPpm(d.pollutionPpm), if (d.pollutionPpm > 500_000L) Tone.WARN else Tone.NEUTRAL),
            Fact("Profit, 30 days", money(d.monthlyProfit), Tones.bySign(d.monthlyProfit.raw)),
        ) + (if (d.unmaintainedTicks > 0) listOf(Fact("Without maintenance", Formatting.duration(d.unmaintainedTicks), Tone.WARN)) else emptyList())
        val lines = d.lines.mapIndexed { i, l ->
            val state = when {
                !l.enabled -> "Switched off"
                l.blocked != null -> "Blocked: ${Formatting.title(l.blocked.lowercase())}"
                l.running -> "Running"
                l.recipe == null -> "No recipe"
                else -> "Idle"
            }
            val tone = when {
                !l.enabled || l.recipe == null -> Tone.MUTED
                l.blocked != null -> Tone.WARN
                l.running -> Tone.GOOD
                else -> Tone.NEUTRAL
            }
            LineItem(
                l.id.value, "${i + 1}. ${l.recipeName ?: "Empty line"}", state, tone, l.progressPpm / 1_000_000.0, Formatting.percentPpm(l.utilizationPpm),
                if (l.machines.isEmpty()) "No machines" else l.machines.groupingBy { it }.eachCount().entries.joinToString(", ") { (n, c) -> if (c > 1) "$c× $n" else n },
                Formatting.plural(l.batchesCompleted, "batch") + " done",
            )
        }
        val stock = d.stock.map { s ->
            Fact(s.name, Formatting.quantity(s.quantity), hint = if (s.reserved.raw > 0) "${Formatting.quantity(s.reserved)} reserved · ${money(s.value)}" else money(s.value))
        }
        val staff = d.staff.map { c ->
            Fact(c.role, "${c.headcount}/${c.target}", if (c.onStrike) Tone.BAD else if (c.headcount < c.target) Tone.WARN else Tone.NEUTRAL, "Wage ${money(c.wage)} · morale ${Formatting.percentPpm(c.moralePpm)}")
        }
        val facility = session.economy.production.facility(id)!!
        val content = session.economy.content
        val owner = facility.owner
        val needed = java.util.TreeMap<app.many.economytycoon.core.RoleId, Long>()
        content.facilityTypeOrNull(facility.type)?.overheadStaff?.forEach { needed.merge(it.role, it.headcount.toLong(), Long::plus) }
        for (l in facility.lines) {
            val recipe = l.recipe?.let { content.recipeOrNull(it) } ?: continue
            if (l.enabled) recipe.labor.forEach { needed.merge(it.role, it.headcount.toLong(), Long::plus) }
        }
        val cohorts = session.economy.workforce.cohortsAt(id)
        val roleKeys = (needed.keys + cohorts.map { it.key.role }).toSortedSet()
        val minimum = session.economy.laborMarkets.minimumWage(facility.region)
        val roles = roleKeys.map { role ->
            val c = cohorts.firstOrNull { it.key.role == role }
            RoleChoice(role, session.economy.catalog.roleOrNull(role)?.name ?: Formatting.title(role.key), c?.headcount ?: 0L, c?.target ?: 0L, needed[role] ?: 0L, Formatting.money(minimum))
        }
        val recipes = session.reports.planner.recipes(owner, facility.type).map { r ->
            RecipeChoice(r.id, r.name, r.inputs.joinToString(", ").ifEmpty { "nothing" }, r.outputs.joinToString(", ").ifEmpty { "nothing" }, Formatting.duration(r.durationTicks.toLong()), r.locked, r.techName?.takeIf { r.locked }?.let { "Needs $it" })
        }
        val hint = if (d.unmaintainedTicks > 30) "Overdue: maintenance has not been paid for ${Formatting.duration(d.unmaintainedTicks)}." else "Spending on maintenance slows wear."
        return FacilityDetailModel(
            id = id, title = row.name, subtitle = "${row.typeName} · ${row.region}", facts = facts, lines = lines, stock = stock, staff = staff,
            canAddLine = d.canAddLine, operating = row.status == FacilityStatus.OPERATING, policy = FacilitiesPresenter.policyLabel(d.policy),
            roles = roles, recipes = recipes, type = facility.type, maintenanceHint = hint,
        )
    }
}
