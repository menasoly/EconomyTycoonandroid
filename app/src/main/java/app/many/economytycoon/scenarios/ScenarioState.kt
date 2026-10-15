package app.many.economytycoon.scenarios

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Tick

enum class ScenarioStatus { ACTIVE, WON, LOST }

enum class LossReason(val label: String) {
    DEADLINE("Time ran out"),
    NO_COMPANY("You have no company left"),
    RUINED("The losses were too deep"),
}

/** The scenario this game is being played as, and how it stands. */
class ScenarioRun(val id: String, val playerId: Long, val startedAt: Tick) {
    var status: ScenarioStatus = ScenarioStatus.ACTIVE
        internal set
    var finishedAt: Tick? = null
        internal set
    var reason: LossReason? = null
        internal set
    var medal: Medal = Medal.NONE
        internal set
    /** Bit i is set once goal i has been reached at some point. */
    var reachedMask: Long = 0L
        internal set

    val isActive get() = status == ScenarioStatus.ACTIVE
    fun reached(index: Int) = reachedMask and (1L shl index) != 0L
    fun daysPlayed(now: Tick): Long = ((finishedAt ?: now).value - startedAt.value).coerceAtLeast(0L)

    internal fun restore(status: ScenarioStatus, finishedAt: Tick?, reason: LossReason?, medal: Medal, mask: Long) {
        this.status = status; this.finishedAt = finishedAt; this.reason = reason; this.medal = medal; reachedMask = mask
    }

    fun hashInto(h: CanonicalHasher) {
        h.putString(id).putLong(playerId).putLong(startedAt.value).putInt(status.ordinal).putLong(finishedAt?.value ?: -1L)
        h.putInt(reason?.ordinal ?: -1).putInt(medal.ordinal).putLong(reachedMask)
    }
}

sealed class ScenarioEvent {
    abstract val tick: Tick
    data class GoalReached(val scenario: String, val index: Int, override val tick: Tick) : ScenarioEvent()
    data class Won(val scenario: String, val days: Long, val medal: Medal, override val tick: Tick) : ScenarioEvent()
    data class Lost(val scenario: String, val reason: LossReason, override val tick: Tick) : ScenarioEvent()
}

/** Holds the one scenario run a game can have. Games started without a scenario have none. */
class ScenarioService {
    var run: ScenarioRun? = null
        private set

    fun begin(id: String, playerId: Long, tick: Tick): ScenarioRun {
        require(ScenarioCatalog.byId(id) != null) { "unknown scenario $id" }
        return ScenarioRun(id, playerId, tick).also { run = it }
    }

    fun def(): ScenarioDef? = run?.let { ScenarioCatalog.byId(it.id) }

    fun markReached(r: ScenarioRun, index: Int) { r.reachedMask = r.reachedMask or (1L shl index) }

    fun win(r: ScenarioRun, tick: Tick, medal: Medal) {
        r.status = ScenarioStatus.WON; r.finishedAt = tick; r.medal = medal
    }

    fun lose(r: ScenarioRun, tick: Tick, reason: LossReason) {
        r.status = ScenarioStatus.LOST; r.finishedAt = tick; r.reason = reason
    }

    fun hashInto(h: CanonicalHasher) {
        val r = run
        h.putBoolean(r != null)
        r?.hashInto(h)
    }

    internal fun restore(r: ScenarioRun?) { run = r }
}
