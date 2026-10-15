package app.many.economytycoon.watch

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting

/** Checks one rule against the facts and says what to tell the player, or null when it does not fire. */
object RuleEvaluator {
    fun check(entry: RuleEntry, facts: WatchFacts, names: WatchNames, money: (Money) -> String = { Formatting.money(it) }): WatchHit? {
        val message = message(entry.rule, facts, names, money) ?: return null
        return WatchHit(entry.id, facts.day, message, entry.rule.severity)
    }

    fun message(rule: WatchRule, f: WatchFacts, names: WatchNames, money: (Money) -> String): String? = when (rule) {
        is WatchRule.Price -> {
            val p = f.price(rule.region, rule.good)
            when {
                p == null -> null
                rule.above && p > rule.level -> "${names.good(rule.good)} in ${names.region(rule.region)} is ${money(p)}, above ${money(rule.level)}."
                !rule.above && p < rule.level -> "${names.good(rule.good)} in ${names.region(rule.region)} is ${money(p)}, below ${money(rule.level)}."
                else -> null
            }
        }
        is WatchRule.Cash -> if (f.cash < rule.level) "Cash is ${money(f.cash)}, below your ${money(rule.level)} limit." else null
        is WatchRule.Profit -> if (f.monthlyProfit < rule.level) "Profit over the last month is ${money(f.monthlyProfit)}, below ${money(rule.level)}." else null
        is WatchRule.Stock -> {
            val move = f.stockMovePpm(rule.issuer)
            if (move != null && Math.abs(move) >= rule.movePpm) "${names.company(rule.issuer)} shares moved ${Formatting.signedPercentPpm(move)} today." else null
        }
        is WatchRule.Loan -> f.daysToLoanPayment()?.takeIf { it in 0..rule.days.toLong() }?.let { "A loan payment is due " + whenText(it) + "." }
        is WatchRule.Delivery -> f.daysToDelivery()?.takeIf { it in 0..rule.days.toLong() }?.let { "A contract delivery is due " + whenText(it) + "." }
        is WatchRule.Idle -> if (f.idleFacilities >= rule.count) "${Formatting.plural(f.idleFacilities.toLong(), "facility")} with nothing running." else null
    }

    fun whenText(days: Long): String = when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in $days days"
    }

    /** A one-line description of the rule itself, for the list of rules. */
    fun describe(rule: WatchRule, names: WatchNames, money: (Money) -> String = { Formatting.money(it) }): String = when (rule) {
        is WatchRule.Price -> "${names.good(rule.good)} in ${names.region(rule.region)} ${if (rule.above) "above" else "below"} ${money(rule.level)}"
        is WatchRule.Cash -> "Cash below ${money(rule.level)}"
        is WatchRule.Profit -> "Monthly profit below ${money(rule.level)}"
        is WatchRule.Stock -> "${names.company(rule.issuer)} shares move ${Formatting.percentPpm(rule.movePpm)} in a day"
        is WatchRule.Loan -> "Loan payment due within ${Formatting.plural(rule.days.toLong(), "day")}"
        is WatchRule.Delivery -> "Delivery due within ${Formatting.plural(rule.days.toLong(), "day")}"
        is WatchRule.Idle -> "${rule.count} or more idle facilities"
    }
}
