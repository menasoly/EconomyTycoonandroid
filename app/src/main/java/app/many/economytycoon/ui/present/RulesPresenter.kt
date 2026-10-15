package app.many.economytycoon.ui.present

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class RuleItem(
    val facility: FacilityId,
    val good: GoodId,
    val sell: Boolean,
    val title: String,
    val summary: String,
    val enabled: Boolean,
)

data class RulesModel(val sells: List<RuleItem>, val buys: List<RuleItem>)

object RulesPresenter {
    fun strategyLabel(s: PriceStrategy): String = when (s) {
        PriceStrategy.MARKET -> "Market price"
        PriceStrategy.COST_PLUS -> "Cost plus"
        PriceStrategy.UNDERCUT -> "Undercut"
        PriceStrategy.PREMIUM -> "Premium"
    }

    private fun facilityName(session: GameSession, id: FacilityId): String {
        val f = session.economy.production.facility(id) ?: return "Facility ${id.value}"
        return f.name.ifBlank { session.economy.content.facilityTypeOrNull(f.type)?.name ?: Formatting.title(f.type.key) }
    }

    private fun goodName(session: GameSession, good: GoodId) = session.economy.content.good(good).name

    fun sellItem(session: GameSession, r: SellRule): RuleItem {
        val markup = Formatting.signedPercentPpm(r.markupPpm)
        val parts = ArrayList<String>()
        parts += when (r.strategy) {
            PriceStrategy.UNDERCUT -> "${strategyLabel(r.strategy)} by ${Formatting.percentPpm(r.markupPpm)}"
            else -> "${strategyLabel(r.strategy)} $markup"
        }
        if (r.reserve.raw > 0) parts += "keep ${Formatting.quantity(r.reserve)}"
        r.maxPerOrder?.let { parts += "at most ${Formatting.quantity(it)} per order" }
        if (r.minMarginPpm != 0L) parts += "margin at least ${Formatting.percentPpm(r.minMarginPpm)}"
        return RuleItem(r.facility, r.good, true, "Sell ${goodName(session, r.good)} from ${facilityName(session, r.facility)}", parts.joinToString(" · "), r.enabled)
    }

    fun buyItem(session: GameSession, r: BuyRule): RuleItem =
        RuleItem(
            r.facility, r.good, false, "Buy ${goodName(session, r.good)} for ${facilityName(session, r.facility)}",
            "Keep ${Formatting.quantity(r.target)} in stock · pay up to ${Formatting.percentPpm(r.maxPriceMultiplePpm)} of market", r.enabled,
        )

    fun build(session: GameSession): RulesModel? {
        val company = session.selectedCompany ?: return null
        val book = session.economy.companies.policies
        val live: (FacilityId) -> Boolean = { session.economy.production.facility(it) != null }
        return RulesModel(
            book.sellRules(company).filter { live(it.facility) }.sortedWith(compareBy({ it.facility }, { it.good })).map { sellItem(session, it) },
            book.buyRules(company).filter { live(it.facility) }.sortedWith(compareBy({ it.facility }, { it.good })).map { buyItem(session, it) },
        )
    }
}
