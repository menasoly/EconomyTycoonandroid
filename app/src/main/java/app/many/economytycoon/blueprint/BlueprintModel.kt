package app.many.economytycoon.blueprint

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.production.SchedulePolicy

/** Identifies a blueprint inside one player's library. Never part of the shared text. */
@JvmInline
value class BlueprintId(val value: Long) : Comparable<BlueprintId> {
    override fun compareTo(other: BlueprintId) = value.compareTo(other.value)
    override fun toString() = "Blueprint#$value"
}

/** Structural bounds a blueprint must respect, whatever the content says. */
object BlueprintLimits {
    const val MAX_LINES = 16
    const val MAX_MACHINES_PER_LINE = 8
    const val MAX_STAFF_ROLES = 24
    const val MAX_RULES = 40
    const val MAX_STORAGE_EXPANSIONS = 8
    const val MAX_NAME = 48
    const val MAX_NOTE = 240
    const val MAX_STAFF_TARGET = 100_000L
    const val PPM = 1_000_000L
}

/** One production line of the captured facility. [priority] is the scheduling rank, lowest first. */
data class LineSpec(
    val priority: Int,
    val enabled: Boolean,
    val recipe: RecipeId?,
    val machines: List<MachineTypeId>,
) {
    init {
        require(machines.size <= BlueprintLimits.MAX_MACHINES_PER_LINE) { "a line holds at most ${BlueprintLimits.MAX_MACHINES_PER_LINE} machines" }
    }

    fun normalized(): LineSpec = copy(machines = machines.distinct().sorted())
}

/** Staffing target for one role, with optional wage and training share. */
data class StaffSpec(val role: RoleId, val target: Long, val wage: Money? = null, val trainingPpm: Long = 0L) {
    init {
        require(target in 0..BlueprintLimits.MAX_STAFF_TARGET) { "staff target out of range" }
        require(wage == null || wage.raw > 0) { "wage must be positive" }
        require(trainingPpm in 0..500_000L) { "training share out of range" }
    }
}

/** A standing sell rule detached from the facility it was captured from. */
data class SellSpec(
    val good: GoodId,
    val strategy: PriceStrategy,
    val markupPpm: Long,
    val reserve: Quantity,
    val maxPerOrder: Quantity?,
    val minMarginPpm: Long,
    val enabled: Boolean,
) {
    init {
        require(markupPpm in -900_000L..5_000_000L) { "markup out of range" }
        require(minMarginPpm >= -900_000L) { "minimum margin out of range" }
        require(reserve.raw >= 0) { "reserve cannot be negative" }
        require(maxPerOrder == null || maxPerOrder.raw > 0) { "per-order cap must be positive" }
    }

    fun toRule(facility: FacilityId) = SellRule(facility, good, strategy, markupPpm, reserve, maxPerOrder, minMarginPpm, enabled)

    companion object {
        fun of(r: SellRule) = SellSpec(r.good, r.strategy, r.markupPpm, r.reserve, r.maxPerOrder, r.minMarginPpm, r.enabled)
    }
}

/** A standing buy rule detached from its facility. */
data class BuySpec(val good: GoodId, val target: Quantity, val maxPriceMultiplePpm: Long, val enabled: Boolean) {
    init {
        require(target.raw >= 0) { "target cannot be negative" }
        require(maxPriceMultiplePpm in 100_000L..10_000_000L) { "price multiple out of range" }
    }

    fun toRule(facility: FacilityId) = BuyRule(facility, good, target, maxPriceMultiplePpm, enabled)

    companion object {
        fun of(r: BuyRule) = BuySpec(r.good, r.target, r.maxPriceMultiplePpm, r.enabled)
    }
}

/**
 * Everything about a facility's configuration that a blueprint carries. Two specs that describe the same
 * set-up are equal once [normalized], and hash alike.
 */
data class BlueprintSpec(
    val facilityType: FacilityTypeId,
    val lines: List<LineSpec>,
    val policy: SchedulePolicy = SchedulePolicy.PRIORITY,
    val qualityPpm: Long = 0L,
    val staff: List<StaffSpec> = emptyList(),
    val sells: List<SellSpec> = emptyList(),
    val buys: List<BuySpec> = emptyList(),
    val storageExpansions: Int = 0,
) {
    init {
        require(lines.size <= BlueprintLimits.MAX_LINES) { "too many lines" }
        require(qualityPpm in 0..BlueprintLimits.PPM) { "quality level out of range" }
        require(staff.size <= BlueprintLimits.MAX_STAFF_ROLES) { "too many staffed roles" }
        require(sells.size <= BlueprintLimits.MAX_RULES && buys.size <= BlueprintLimits.MAX_RULES) { "too many trading rules" }
        require(storageExpansions in 0..BlueprintLimits.MAX_STORAGE_EXPANSIONS) { "storage expansions out of range" }
    }

    /** Sorted, de-duplicated form: lines by priority (stable) renumbered 0..n-1, everything else by key. */
    fun normalized(): BlueprintSpec = copy(
        lines = lines.map { it.normalized() }.withIndex().sortedWith(compareBy({ it.value.priority }, { it.index }))
            .mapIndexed { rank, v -> v.value.copy(priority = rank) },
        staff = staff.sortedBy { it.role }.distinctBy { it.role },
        sells = sells.sortedBy { it.good }.distinctBy { it.good },
        buys = buys.sortedBy { it.good }.distinctBy { it.good },
    )

    fun hashInto(h: CanonicalHasher) {
        val n = normalized()
        h.section("blueprint.spec").putString(n.facilityType.key).putInt(n.policy.ordinal).putLong(n.qualityPpm).putInt(n.storageExpansions)
        h.putInt(n.lines.size)
        for (l in n.lines) {
            h.putInt(l.priority).putBoolean(l.enabled).putString(l.recipe?.key ?: "-").putInt(l.machines.size)
            for (m in l.machines) h.putString(m.key)
        }
        h.putInt(n.staff.size)
        for (s in n.staff) h.putString(s.role.key).putLong(s.target).putLong(s.wage?.raw ?: -1L).putLong(s.trainingPpm)
        h.putInt(n.sells.size)
        for (s in n.sells) {
            h.putString(s.good.key).putInt(s.strategy.ordinal).putLong(s.markupPpm).putQuantity(s.reserve)
                .putLong(s.maxPerOrder?.raw ?: -1L).putLong(s.minMarginPpm).putBoolean(s.enabled)
        }
        h.putInt(n.buys.size)
        for (b in n.buys) h.putString(b.good.key).putQuantity(b.target).putLong(b.maxPriceMultiplePpm).putBoolean(b.enabled)
    }

    /** Canonical hex hash of the configuration; names, notes and tags do not count. */
    fun canonicalHash(): String = CanonicalHasher().also { hashInto(it) }.finish().toHex()

    val machineCount: Int get() = lines.sumOf { it.machines.size }
    val recipes: List<RecipeId> get() = lines.mapNotNull { it.recipe }.distinct().sorted()
}

/** An immutable, shareable capture of a facility's set-up. */
data class Blueprint(
    val spec: BlueprintSpec,
    val name: String,
    val note: String = "",
    /** Content versions the blueprint was made against, e.g. `base@1.4`; empty when unknown. */
    val contentTag: String = "",
    /** In-game day the blueprint was captured. */
    val createdDay: Long = 0L,
) {
    init {
        require(name.isNotBlank()) { "a blueprint needs a name" }
        require(name.length <= BlueprintLimits.MAX_NAME) { "name is too long" }
        require(note.length <= BlueprintLimits.MAX_NOTE) { "note is too long" }
        require(createdDay >= 0) { "day cannot be negative" }
    }

    /** Hash of the configuration alone; the key the library uses to spot duplicates. */
    val hash: String by lazy(LazyThreadSafetyMode.NONE) { spec.canonicalHash() }

    /** Eight hex digits of [hash], for display. */
    val shortHash: String get() = hash.substring(0, 8)

    val facilityType: FacilityTypeId get() = spec.facilityType

    fun renamed(newName: String) = copy(name = newName.trim())
    fun withNote(newNote: String) = copy(note = newNote.trim())

    companion object {
        /** Builds a blueprint with its spec in canonical form. */
        fun of(spec: BlueprintSpec, name: String, note: String = "", contentTag: String = "", createdDay: Long = 0L): Blueprint =
            Blueprint(spec.normalized(), name.trim(), note.trim(), contentTag, createdDay)
    }
}
