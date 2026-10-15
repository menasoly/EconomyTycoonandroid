package app.many.economytycoon.session

import app.many.economytycoon.achievements.Achievement
import app.many.economytycoon.achievements.AchievementEvaluator
import app.many.economytycoon.achievements.Stat
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.exchange.ExchangeEvent
import app.many.economytycoon.futures.FuturesEvent
import app.many.economytycoon.property.PropertyEvent
import app.many.economytycoon.scenarios.Goal
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.GoalMeter
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.simulation.Economy
import app.many.economytycoon.simulation.TickSummary
import app.many.economytycoon.tenders.TenderEvent
import app.many.economytycoon.unions.UnionEvent

/**
 * Turns what happens in a game into lifetime totals and achievements on the player's profile. It looks only
 * at the player's own companies, counts events as they are reported, and checks the measures every few days.
 */
class AchievementTracker(private val economy: Economy, private val playerId: Long) {
    /** The profile being kept; nothing is tracked while this is null. */
    var profile: PlayerProfile? = null

    private var sinceSaved = 0

    fun mine(): Set<CompanyId> = GoalMeter.playerCompanies(economy, playerId).toSet()

    /** Counts one tick's events and returns achievements earned by them. */
    fun onTick(summary: TickSummary, forceCheck: Boolean = false): List<Achievement> {
        val p = profile ?: return emptyList()
        p.stats.add(Stat.DAYS_PLAYED)
        sinceSaved++
        val me = mine()
        if (me.isNotEmpty()) count(summary, me, p)
        val due = forceCheck || summary.tick.value % CHECK_EVERY == 0L
        return if (due) evaluate() else emptyList()
    }

    /** Checks every achievement against the game and profile now; returns those newly unlocked. */
    fun evaluate(): List<Achievement> {
        val p = profile ?: return emptyList()
        val cache = HashMap<GoalKind, Long>()
        val earned = AchievementEvaluator.earnable(p) { kind -> cache.getOrPut(kind) { meter(kind) } }
        val day = p.stats[Stat.DAYS_PLAYED]
        return earned.filter { p.achievements.unlock(it.id, day) }
    }

    /** The player's best value for a measure right now. */
    fun meter(kind: GoalKind): Long = GoalMeter.measure(economy, playerId, Goal(kind, 1L))

    /** True once enough has changed since the last [markSaved] to be worth writing out. */
    fun shouldSave(): Boolean = sinceSaved >= SAVE_EVERY
    fun markSaved() { sinceSaved = 0 }

    private fun count(s: TickSummary, me: Set<CompanyId>, p: PlayerProfile) {
        for (ev in s.tenderEvents) when (ev) {
            is TenderEvent.Awarded -> if (ev.winner in me) p.stats.add(Stat.TENDERS_WON)
            is TenderEvent.Delivered -> if (ev.winner in me) p.stats.add(Stat.TENDERS_DELIVERED)
            else -> Unit
        }
        for (ev in s.propertyEvents) when (ev) {
            is PropertyEvent.Sold -> if (ev.seller in me) p.stats.add(Stat.PARCELS_SOLD)
            is PropertyEvent.LeaseStarted -> if (ev.landlord in me) p.stats.add(Stat.LEASES_SIGNED)
            else -> Unit
        }
        for (ev in s.futuresEvents) when (ev) {
            is FuturesEvent.Opened -> if (ev.company in me) p.stats.add(Stat.FUTURES_OPENED)
            is FuturesEvent.Liquidated -> if (ev.company in me) p.stats.add(Stat.FUTURES_LIQUIDATED)
            else -> Unit
        }
        for (ev in s.exchangeEvents) if (ev is ExchangeEvent.Listed && ev.issuer in me) p.stats.add(Stat.PUBLIC_OFFERINGS)
        for (ev in s.unionEvents) if (ev is UnionEvent.Struck && economy.unions.get(ev.union)?.company in me) p.stats.add(Stat.STRIKES_SUFFERED)
    }

    companion object {
        const val CHECK_EVERY = 5L
        const val SAVE_EVERY = 30
    }
}
