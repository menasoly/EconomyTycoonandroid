package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money

/** How long the cash lasts. [days] is null when cash is not shrinking. */
data class Runway(val cash: Money, val burnPerDay: Money, val days: Long?) {
    val isBurning: Boolean get() = burnPerDay.raw > 0L

    fun status(): RunwayStatus = when {
        cash.raw <= 0L -> RunwayStatus.EMPTY
        days == null -> RunwayStatus.GROWING
        days < 30L -> RunwayStatus.CRITICAL
        days < 120L -> RunwayStatus.SHORT
        else -> RunwayStatus.COMFORTABLE
    }

    companion object {
        /** From the change in cash over [periodDays]; a fall is a burn. */
        fun of(cash: Money, cashChange: Money, periodDays: Long): Runway {
            val p = periodDays.coerceAtLeast(1L)
            val burn = Money(Math.floorDiv(-cashChange.raw, p))
            if (burn.raw <= 0L) return Runway(cash, Money.ZERO, null)
            val days = if (cash.raw <= 0L) 0L else cash.raw / burn.raw
            return Runway(cash, burn, days)
        }
    }
}

enum class RunwayStatus(val label: String) {
    EMPTY("Out of cash"),
    CRITICAL("Under a month"),
    SHORT("A few months"),
    COMFORTABLE("Comfortable"),
    GROWING("Cash is growing"),
}
