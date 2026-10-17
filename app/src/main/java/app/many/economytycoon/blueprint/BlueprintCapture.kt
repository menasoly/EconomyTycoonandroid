package app.many.economytycoon.blueprint

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules

/** Which parts of a facility a capture keeps. Machines, recipes and line order are always kept. */
data class CaptureOptions(
    val staffing: Boolean = true,
    val wages: Boolean = false,
    val training: Boolean = true,
    val trading: Boolean = true,
    val storage: Boolean = true,
    val schedulePolicy: Boolean = true,
    val quality: Boolean = true,
) {
    companion object { val ALL = CaptureOptions(wages = true) }
}

/** Turns a [FacilityView] into an immutable [Blueprint]. */
object BlueprintCapture {
    fun capture(
        view: FacilityView,
        name: String = view.name,
        options: CaptureOptions = CaptureOptions(),
        note: String = "",
        contentTag: String = "",
        day: Long = 0L,
    ): Outcome<Blueprint> {
        if (view.lines.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a facility without production lines has nothing to capture")
        val title = name.trim().ifBlank { view.type.key }.take(BlueprintLimits.MAX_NAME)
        val staff = if (!options.staffing) emptyList() else view.staff.map {
            it.copy(wage = if (options.wages) it.wage else null, trainingPpm = if (options.training) it.trainingPpm else 0L)
        }
        val spec = try {
            BlueprintSpec(
                facilityType = view.type,
                lines = view.lines.map { LineSpec(it.priority, it.enabled, it.recipe, it.machines) },
                policy = if (options.schedulePolicy) view.policy else app.many.economytycoon.production.SchedulePolicy.PRIORITY,
                qualityPpm = if (options.quality) view.qualityPpm else 0L,
                staff = staff,
                sells = if (options.trading) view.sells else emptyList(),
                buys = if (options.trading) view.buys else emptyList(),
                storageExpansions = if (options.storage) view.storageExpansions else 0,
            )
        } catch (e: IllegalArgumentException) {
            return Outcome.reject(Rules.LIMIT_EXCEEDED, e.message ?: "facility exceeds blueprint limits")
        }
        return Outcome.ok(Blueprint.of(spec, title, note.trim().take(BlueprintLimits.MAX_NOTE), contentTag, day))
    }
}
