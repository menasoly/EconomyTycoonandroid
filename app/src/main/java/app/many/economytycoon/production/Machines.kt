package app.many.economytycoon.production

import app.many.economytycoon.content.MachineTypeDef
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.MachineId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** An installed machine. Condition falls with use and recovers with maintenance. */
class Machine(val id: MachineId, val type: MachineTypeId, val installed: Tick) {
    var conditionPpm: Long = 1_000_000L
        internal set
    var batchesRun: Long = 0
        internal set
    var brokenUntil: Long = -1
        internal set
    var breakdowns: Int = 0
        internal set

    fun isBroken(tick: Tick) = tick.value < brokenUntil

    /** Output factor from condition: full speed down to 80%, then falling to 60% for a wrecked machine. */
    val conditionFactorPpm: Long get() = if (conditionPpm >= 800_000L) 1_000_000L else 600_000L + conditionPpm / 2

    internal fun restore(condition: Long, batches: Long, brokenUntil: Long, breakdowns: Int) {
        conditionPpm = condition; batchesRun = batches; this.brokenUntil = brokenUntil; this.breakdowns = breakdowns
    }
}

object MachineWear {
    /** Applies the wear of [batches] finished batches. */
    fun wear(m: Machine, def: MachineTypeDef, batches: Long) {
        m.batchesRun += batches
        m.conditionPpm = (m.conditionPpm - def.wearPerBatchPpm * batches).coerceAtLeast(0L)
    }

    /** Chance per tick of a breakdown, ppm. Zero above 50% condition, rising steeply as it falls. */
    fun breakdownChancePpm(m: Machine): Long {
        val c = m.conditionPpm
        if (c >= 500_000L) return 0L
        val deficit = 500_000L - c // 0..500k
        return mulDiv(deficit, deficit, 500_000L) / 20L + deficit / 100L // up to ~30,000
    }

    fun rollBreakdown(m: Machine, rnd: DeterministicRandom, tick: Tick): Boolean {
        if (m.isBroken(tick)) return false
        if (!rnd.chance(Rate(breakdownChancePpm(m)))) return false
        m.breakdowns++
        m.brokenUntil = tick.value + 3 + rnd.nextInt(6)
        return true
    }

    /** Restores condition by spending [budget]; returns the condition recovered. Cost is 1% of price per 1% restored. */
    fun repair(m: Machine, def: MachineTypeDef, budget: Money): Long {
        if (def.cost.raw <= 0 || budget.raw <= 0) return 0L
        val maxRestore = 1_000_000L - m.conditionPpm
        val restore = mulDiv(budget.raw, 1_000_000L, def.cost.raw).coerceAtMost(maxRestore)
        m.conditionPpm += restore
        return restore
    }

    /** Cost to bring a machine fully back to new. */
    fun fullRepairCost(m: Machine, def: MachineTypeDef): Money = Money(mulDiv(def.cost.raw, 1_000_000L - m.conditionPpm, 1_000_000L))
}
