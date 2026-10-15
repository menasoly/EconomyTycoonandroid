package app.many.economytycoon.workforce

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RoleId

/** Education tier a role needs; supply of each tier is derived from the local education level. */
enum class SkillTier(val minEducationPpm: Long) {
    BASIC(0), TRAINED(300_000), SPECIALIST(550_000), EXPERT(750_000);
}

/** Workforce view of a job. Built by the simulation from content definitions. */
data class WorkRole(
    val id: RoleId,
    val name: String,
    val tier: SkillTier,
    val baseWage: Money,
    val trainingTicks: Int,
    /** Ticks between posting a vacancy and the hire starting work. */
    val timeToFillTicks: Int = 10,
)

class RoleCatalog(roles: List<WorkRole>) {
    private val byId = roles.associateBy { it.id }
    fun role(id: RoleId): WorkRole = byId[id] ?: error("unknown work role $id")
    fun roleOrNull(id: RoleId): WorkRole? = byId[id]
    fun all(): List<WorkRole> = byId.values.sortedBy { it.id }

    companion object {
        fun of(vararg roles: WorkRole) = RoleCatalog(roles.toList())
    }
}
