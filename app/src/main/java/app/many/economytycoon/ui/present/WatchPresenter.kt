package app.many.economytycoon.ui.present

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.watch.EconomyWatchNames
import app.many.economytycoon.watch.RuleEntry
import app.many.economytycoon.watch.RuleEvaluator
import app.many.economytycoon.watch.Severity
import app.many.economytycoon.watch.WatchHit
import app.many.economytycoon.watch.WatchNames
import app.many.economytycoon.watch.WatchRule

data class WatchRuleRow(val id: Long, val text: String, val kind: String, val severity: String, val tone: Tone, val enabled: Boolean, val status: String)

data class WatchHitRow(val day: String, val text: String, val tone: Tone)

/** A rule the player can add in one tap. */
data class WatchPreset(val id: String, val label: String, val hint: String)

data class WatchModel(val tiles: List<Tile>, val rules: List<WatchRuleRow>, val hits: List<WatchHitRow>, val presets: List<WatchPreset>, val full: Boolean, val empty: String)

/** The watchlist: what is being watched, what has fired, and quick ways to add more. */
object WatchPresenter {
    const val EMPTY = "Nothing is being watched. Add a rule below and the game will tell you when it matters."

    val presets: List<WatchPreset> = listOf(
        WatchPreset("cash", "Cash running low", "Tell me when cash falls below a tenth of today's level."),
        WatchPreset("loss", "A losing month", "Tell me when the month's profit turns negative."),
        WatchPreset("loan", "Loan payment coming", "Tell me a week before a loan payment is due."),
        WatchPreset("delivery", "Delivery coming", "Tell me three days before a contract delivery."),
        WatchPreset("idle", "Idle facilities", "Tell me when a facility has nothing running."),
    )

    fun severityTone(s: Severity): Tone = when (s) { Severity.INFO -> Tone.INFO; Severity.WARNING -> Tone.WARN; Severity.CRITICAL -> Tone.BAD }

    fun rule(e: RuleEntry, lastFired: Long?, names: WatchNames, money: (Money) -> String): WatchRuleRow = WatchRuleRow(
        id = e.id, text = RuleEvaluator.describe(e.rule, names, money), kind = e.rule.kind.label, severity = e.rule.severity.label,
        tone = severityTone(e.rule.severity), enabled = e.enabled,
        status = when {
            !e.enabled -> "Switched off"
            lastFired == null -> "Has not fired"
            else -> "Last fired on day $lastFired"
        },
    )

    fun hit(h: WatchHit): WatchHitRow = WatchHitRow("Day ${h.day}", h.message, severityTone(h.severity))

    /** The rule a preset stands for, given where the company is now; null when it makes no sense yet. */
    fun fromPreset(id: String, cash: Money): WatchRule? = when (id) {
        "cash" -> Money(cash.raw / 10).takeIf { it.raw > 0L }?.let { WatchRule.Cash(it, Severity.WARNING) }
        "loss" -> WatchRule.Profit(Money.ZERO, Severity.WARNING)
        "loan" -> WatchRule.Loan(7, Severity.WARNING)
        "delivery" -> WatchRule.Delivery(3, Severity.INFO)
        "idle" -> WatchRule.Idle(1, Severity.WARNING)
        else -> null
    }

    fun model(entries: List<RuleEntry>, lastFired: (Long) -> Long?, hits: List<WatchHit>, names: WatchNames, money: (Money) -> String, full: Boolean): WatchModel {
        val tiles = listOf(
            Tile("Rules", entries.size.toString(), note = "${entries.count { it.enabled }} switched on"),
            Tile("Recent hits", hits.size.toString(), if (hits.any { it.severity == Severity.CRITICAL }) Tone.BAD else Tone.NEUTRAL),
        )
        return WatchModel(tiles, entries.map { rule(it, lastFired(it.id), names, money) }, hits.take(20).map { hit(it) }, presets, full, EMPTY)
    }

    fun build(session: GameSession): WatchModel {
        val names = EconomyWatchNames(session.simulation.economy)
        val book = session.watch
        return model(book.all(), book::lastFiredDay, session.watchHits(), names, session.settings::money, book.size >= app.many.economytycoon.watch.RuleBook.MAX_RULES)
    }

    /** Adds a preset rule for the selected company; the reason it could not be added otherwise. */
    fun addPreset(session: GameSession, id: String): String? {
        val cash = session.valuationInput()?.cash ?: Money.ZERO
        val rule = fromPreset(id, cash) ?: return "That rule needs some cash on hand first."
        if (session.watch.add(rule) == null) return "You are watching as much as the game allows. Remove a rule first."
        session.saveWatch()
        return null
    }

    fun addPrice(session: GameSession, region: RegionId, good: GoodId, above: Boolean, level: Money): String? {
        if (level.raw <= 0L) return "Enter a price above zero."
        if (session.watch.add(WatchRule.Price(region, good, above, level)) == null) return "You are watching as much as the game allows. Remove a rule first."
        session.saveWatch()
        return null
    }

    fun remove(session: GameSession, id: Long) { if (session.watch.remove(id)) session.saveWatch() }
    fun toggle(session: GameSession, id: Long) { session.watch.get(id)?.let { session.watch.setEnabled(id, !it.enabled); session.saveWatch() } }

    fun priceNote(session: GameSession, region: RegionId, good: GoodId): String =
        session.simulation.economy.markets.price(app.many.economytycoon.markets.MarketKey(region, good)).perUnit.let { "Now ${session.settings.money(it)}" }

    fun count(n: Int) = Formatting.plural(n.toLong(), "rule")
}
