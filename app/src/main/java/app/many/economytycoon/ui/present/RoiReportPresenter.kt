package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.Channel
import app.many.economytycoon.marketing.RoiReport
import app.many.economytycoon.marketing.RoiVerdict
import app.many.economytycoon.session.Formatting

data class RoiChannelRow(val channel: String, val spend: String, val share: String)

data class RoiReportModel(
    val headline: String,
    val tone: Tone,
    val tiles: List<Tile>,
    val facts: List<Fact>,
    val channels: List<RoiChannelRow>,
    val advice: String,
)

/** The look-back report: what the advertising cost, what it lifted, and whether it paid. */
object RoiReportPresenter {
    fun headline(r: RoiReport, money: (Money) -> String): String = when (r.verdict) {
        RoiVerdict.TOO_EARLY -> "Too early to judge: only ${Formatting.plural(r.days.toLong(), "day")} of results."
        RoiVerdict.PAID_BACK -> "Paid for itself, with ${money(r.netProfit)} to spare" + (r.paybackDay?.let { " from day $it" } ?: "") + "."
        RoiVerdict.PAYING_BACK -> "Earning back ${Formatting.percentPpm(if (r.spend.raw <= 0L) 0L else r.incrementalProfit.raw * 1_000_000L / r.spend.raw)} of its cost so far."
        RoiVerdict.LOSING -> "Lost ${money(-r.netProfit)} over ${Formatting.plural(r.days.toLong(), "day")}."
    }

    fun advice(r: RoiReport): String = when (r.verdict) {
        RoiVerdict.TOO_EARLY -> "Give it at least a week before changing anything."
        RoiVerdict.PAID_BACK -> "Keep it going, and consider more on the channels with the best return."
        RoiVerdict.PAYING_BACK -> "It may yet pay back. Watch the lift for another week before deciding."
        RoiVerdict.LOSING -> "Cut the budget, change channel or creative, or stop. Awareness only pays if it turns into sales."
    }

    fun build(r: RoiReport, spendByChannel: Map<Channel, Money>, money: (Money) -> String): RoiReportModel {
        val total = spendByChannel.values.sumOf { it.raw }
        val channels = spendByChannel.map { (ch, m) ->
            RoiChannelRow(ch.label, money(m), Formatting.percentPpm(if (total <= 0L) 0L else m.raw * 1_000_000L / total))
        }
        val tiles = listOf(
            Tile("Spent", money(r.spend)),
            Tile("Extra profit", Formatting.signedMoney(r.incrementalProfit), Tones.bySign(r.incrementalProfit.raw)),
            Tile("Net result", Formatting.signedMoney(r.netProfit), Tones.bySign(r.netProfit.raw)),
            Tile("Lift in sales", Formatting.signedPercentPpm(r.liftPpm), Tones.bySign(r.liftPpm)),
        )
        val facts = listOf(
            Fact("Days measured", r.days.toString()),
            Fact("Sales before", "${money(r.baselinePerDay)} a day"),
            Fact("Revenue since", money(r.actualRevenue)),
            Fact("Extra revenue", Formatting.signedMoney(r.incrementalRevenue), Tones.bySign(r.incrementalRevenue.raw)),
            Fact("Return on spend", Formatting.signedPercentPpm(r.roiPpm), Tones.bySign(r.roiPpm)),
            Fact("Paid back", r.paybackDay?.let { "on day $it" } ?: "not yet"),
        )
        return RoiReportModel(headline(r, money), MarketingPresenter.roiTone(r.verdict), tiles, facts, channels, advice(r))
    }
}
