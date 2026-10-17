package app.many.economytycoon.production

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick

enum class FacilityStatus { OPERATING, SHUTDOWN, DEMOLISHED }

/** Why a line did not (fully) run this tick. Surfaced to the player and the AI as bottleneck signals. */
sealed class Blocked {
    object None : Blocked()
    object NoRecipe : Blocked()
    object NoLabor : Blocked()
    object NoEnergy : Blocked()
    object OutputFull : Blocked()
    object Broken : Blocked()
    object Strike : Blocked()
    object Shutdown : Blocked()
    object NoDeposit : Blocked()
    object MissingMachine : Blocked()
    data class MissingInput(val good: GoodId) : Blocked()

    val code: String
        get() = when (this) {
            None -> "none"; NoRecipe -> "no_recipe"; NoLabor -> "no_labor"; NoEnergy -> "no_energy"; OutputFull -> "output_full"
            Broken -> "broken"; Strike -> "strike"; Shutdown -> "shutdown"; NoDeposit -> "no_deposit"; MissingMachine -> "missing_machine"
            is MissingInput -> "missing_input:${good.key}"
        }

    companion object {
        fun parse(code: String): Blocked = when {
            code == "none" -> None; code == "no_recipe" -> NoRecipe; code == "no_labor" -> NoLabor; code == "no_energy" -> NoEnergy
            code == "output_full" -> OutputFull; code == "broken" -> Broken; code == "strike" -> Strike; code == "shutdown" -> Shutdown
            code == "no_deposit" -> NoDeposit; code == "missing_machine" -> MissingMachine
            code.startsWith("missing_input:") -> MissingInput(GoodId(code.removePrefix("missing_input:")))
            else -> None
        }
    }
}

/** A batch in progress on a line. */
class ActiveBatch(val recipe: RecipeId, var progressPpm: Long, val inputValue: app.many.economytycoon.core.Money, val yieldPpm: Long, val conversionCost: app.many.economytycoon.core.Money)

class ProductionLine(val id: LineId, var priority: Int) {
    var recipe: RecipeId? = null
        internal set
    var batch: ActiveBatch? = null
        internal set
    var blocked: Blocked = Blocked.None
        internal set
    val machines = ArrayList<Machine>()
    var batchesCompleted: Long = 0
        internal set
    var utilizationPpm: Long = 0
        internal set
    var enabled: Boolean = true
        internal set
    internal val carry = LongArray(2)

    internal fun restore(recipe: RecipeId?, enabled: Boolean, completed: Long, blocked: Blocked, util: Long, batch: ActiveBatch?) {
        this.recipe = recipe; this.enabled = enabled; batchesCompleted = completed; this.blocked = blocked; utilizationPpm = util; this.batch = batch
    }

    fun hasMachine(type: app.many.economytycoon.core.MachineTypeId) = machines.any { it.type == type }

    internal fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(recipe?.key ?: "-").putInt(priority).putBoolean(enabled).putLong(batchesCompleted)
        val b = batch
        if (b != null) h.putString(b.recipe.key).putLong(b.progressPpm).putMoney(b.inputValue).putLong(b.yieldPpm).putMoney(b.conversionCost) else h.putInt(-1)
        for (m in machines) h.putLong(m.id.value).putLong(m.conditionPpm).putLong(m.brokenUntil)
    }
}

class Facility(
    val id: FacilityId,
    val type: FacilityTypeId,
    owner: CompanyId,
    val region: RegionId,
    val city: CityId?,
    val parcel: ParcelId?,
    val built: Tick,
    val inventory: Inventory,
) {
    var owner: CompanyId = owner
        internal set
    var name: String = ""
    var status: FacilityStatus = FacilityStatus.OPERATING
        internal set
    var conditionPpm: Long = 1_000_000L
        internal set
    /** Accumulated unmanaged waste harm; above 1,000,000 the authorities intervene. */
    var pollutionPpm: Long = 0
        internal set
    var policy: SchedulePolicy = SchedulePolicy.PRIORITY
        internal set
    var qualityAssurancePpm: Long = 0
        internal set
    val lines = ArrayList<ProductionLine>()
    var depositId: app.many.economytycoon.core.DepositId? = null
        internal set
    var unmaintainedTicks: Long = 0
        internal set
    var energyUsed: app.many.economytycoon.core.Quantity = app.many.economytycoon.core.Quantity.ZERO
        internal set

    /** Share of today's working hours with power, in ppm. Set by a blackout and put back to full when the day's production has run. */
    var powerFactorPpm: Long = 1_000_000L
        internal set

    /** Well-kept buildings run at full speed; below 80% condition output falls toward 70%. */
    val conditionFactorPpm: Long get() = if (conditionPpm >= 800_000L) 1_000_000L else 700_000L + conditionPpm * 3 / 8

    internal fun restoreState(status: FacilityStatus, condition: Long, pollution: Long, policy: SchedulePolicy, qa: Long, deposit: app.many.economytycoon.core.DepositId?, unmaintained: Long) {
        this.status = status; conditionPpm = condition; pollutionPpm = pollution; this.policy = policy
        qualityAssurancePpm = qa; depositId = deposit; unmaintainedTicks = unmaintained
    }

    fun line(id: LineId): ProductionLine? = lines.firstOrNull { it.id == id }
    fun isOperating() = status == FacilityStatus.OPERATING

    internal fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(type.key).putLong(owner.value).putInt(status.ordinal).putLong(conditionPpm).putLong(pollutionPpm)
            .putInt(policy.ordinal).putLong(qualityAssurancePpm).putLong(depositId?.value ?: -1L).putLong(unmaintainedTicks)
        inventory.hashInto(h)
        for (l in lines) l.hashInto(h)
    }
}

/** How a facility decides which recipe each line runs. */
enum class SchedulePolicy { PRIORITY, AUTO_MARGIN }
