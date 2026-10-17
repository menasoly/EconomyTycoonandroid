package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.PaymentLine
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementPriority

/**
 * Turns marketing money into settlement intents. Nothing here posts: the finance layer receives the intents and
 * decides. Advertisers pay the agency (stood in for by households, the same payee other service charges use);
 * the books show the cost as marketing expense and the agency's income as service revenue. A refund runs backwards.
 *
 * Keys are unique per campaign, day and purpose (one-off charges also carry their amount), so a retry can never charge twice.
 */
class MarketingLedger(val agency: LedgerOwner = LedgerOwner.households()) {

    fun deposit(c: Campaign, amount: Money, tick: Tick): SettlementIntent? = charge("dep", c.id, c.company, amount, tick, "booking deposit")

    fun daily(c: Campaign, amount: Money, tick: Tick): SettlementIntent? = charge("spd", c.id, c.company, amount, tick, "${c.channel.label} campaign")

    fun creative(c: Campaign, amount: Money, tick: Tick): SettlementIntent? = charge("fx", c.id, c.company, amount, tick, "new creative")

    fun splitTest(c: Campaign, amount: Money, tick: Tick): SettlementIntent? = charge("ab", c.id, c.company, amount, tick, "split test")

    fun refund(c: Campaign, amount: Money, tick: Tick): SettlementIntent? {
        if (amount.raw <= 0L) return null
        return SettlementIntent(
            key(REFUND, c.id, tick), agency, LedgerOwner.company(c.company), SettlementPriority.OTHER, ReasonCode.FEE,
            listOf(PaymentLine(Chart.REVENUE_SERVICES, Chart.MARKETING_EXPENSE, amount)), deferrable = false, memo = "campaign refund",
        )
    }

    private fun charge(kind: String, id: CampaignId, company: CompanyId, amount: Money, tick: Tick, memo: String): SettlementIntent? {
        if (amount.raw <= 0L) return null
        return SettlementIntent(
            if (kind == DAILY) key(kind, id, tick) else key(kind, id, tick) + ":" + amount.raw, LedgerOwner.company(company), agency, SettlementPriority.OTHER, ReasonCode.FEE,
            listOf(PaymentLine(Chart.MARKETING_EXPENSE, Chart.REVENUE_SERVICES, amount)), deferrable = false, memo = memo,
        )
    }

    companion object {
        const val PREFIX = "mkt"
        const val DAILY = "spd"
        const val REFUND = "ref"

        fun key(kind: String, id: CampaignId, tick: Tick) = "$PREFIX:$kind:${id.value}:${tick.value}"

        /** The campaign a daily-spend key belongs to, or null for any other key. */
        fun dailyCampaign(key: String): CampaignId? {
            val parts = key.split(':')
            if (parts.size != 4 || parts[0] != PREFIX || parts[1] != DAILY) return null
            return parts[2].toLongOrNull()?.let { CampaignId(it) }
        }
    }
}
