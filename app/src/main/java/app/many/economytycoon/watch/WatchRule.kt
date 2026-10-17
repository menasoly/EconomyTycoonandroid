package app.many.economytycoon.watch

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId

/** How serious a triggered rule is; critical ones can pause the game. */
enum class Severity(val label: String) { INFO("Note"), WARNING("Warning"), CRITICAL("Critical") }

/**
 * Something the player wants to be told about. Rules are plain data: they can be saved, listed and edited, and are
 * checked against a [WatchFacts] snapshot, never against the game directly.
 */
sealed class WatchRule {
    abstract val kind: Kind
    abstract val severity: Severity

    enum class Kind(val label: String) {
        PRICE("Price"), CASH("Cash"), PROFIT("Profit"), STOCK("Share price"), LOAN("Loan payment"), DELIVERY("Delivery"), IDLE("Idle facilities"),
    }

    /** A good's price in a region goes above or below a level. */
    data class Price(val region: RegionId, val good: GoodId, val above: Boolean, val level: Money, override val severity: Severity = Severity.INFO) : WatchRule() {
        override val kind get() = Kind.PRICE
    }

    /** Cash on hand falls below a level. */
    data class Cash(val level: Money, override val severity: Severity = Severity.WARNING) : WatchRule() {
        override val kind get() = Kind.CASH
    }

    /** The last month's profit falls below a level (a negative level means a loss of that size). */
    data class Profit(val level: Money, override val severity: Severity = Severity.WARNING) : WatchRule() {
        override val kind get() = Kind.PROFIT
    }

    /** A listed company's share price moves by at least [movePpm] in a day, either way. */
    data class Stock(val issuer: CompanyId, val movePpm: Long, override val severity: Severity = Severity.INFO) : WatchRule() {
        override val kind get() = Kind.STOCK
        init { require(movePpm > 0L) { "move must be positive" } }
    }

    /** A loan payment falls due within [days] days. */
    data class Loan(val days: Int, override val severity: Severity = Severity.WARNING) : WatchRule() {
        override val kind get() = Kind.LOAN
        init { require(days in 1..90) { "days out of range" } }
    }

    /** A contract delivery falls due within [days] days. */
    data class Delivery(val days: Int, override val severity: Severity = Severity.INFO) : WatchRule() {
        override val kind get() = Kind.DELIVERY
        init { require(days in 1..90) { "days out of range" } }
    }

    /** At least [count] facilities have nothing running. */
    data class Idle(val count: Int, override val severity: Severity = Severity.WARNING) : WatchRule() {
        override val kind get() = Kind.IDLE
        init { require(count in 1..1_000) { "count out of range" } }
    }
}

/** A rule as stored: its id, whether it is switched on, and how long it stays quiet after firing. */
data class RuleEntry(val id: Long, val rule: WatchRule, val enabled: Boolean = true, val cooldownDays: Int = DEFAULT_COOLDOWN) {
    init { require(cooldownDays in 0..360) { "cooldown out of range" } }

    companion object { const val DEFAULT_COOLDOWN = 10 }
}

/** A rule that fired. */
data class WatchHit(val ruleId: Long, val day: Long, val message: String, val severity: Severity)
