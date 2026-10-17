package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.marketing.ChannelReturn
import app.many.economytycoon.session.Formatting

data class ChannelRow(
    val label: String,
    val blurb: String,
    val facts: List<Fact>,
    val tone: Tone,
    val verdict: String,
    val best: Boolean,
)

data class ChannelComparisonModel(val title: String, val rows: List<ChannelRow>, val note: String)

/** Puts the channels side by side for one product in one place, best return on the money first. */
object ChannelComparisonPresenter {
    fun verdict(returnPpm: Long, marginalPpm: Long): String = when {
        returnPpm <= 0L -> "Brings in nothing at this size"
        marginalPpm >= 1_500_000L -> "Strong: the next unit of money still earns well"
        marginalPpm >= 1_000_000L -> "Pays for itself"
        returnPpm >= 1_000_000L -> "Worth it up to here, but nearly saturated"
        else -> "Costs more than it brings in"
    }

    fun tone(returnPpm: Long, marginalPpm: Long): Tone = when {
        returnPpm <= 0L -> Tone.MUTED
        marginalPpm >= 1_000_000L -> Tone.GOOD
        returnPpm >= 1_000_000L -> Tone.WARN
        else -> Tone.BAD
    }

    fun build(title: String, rows: List<ChannelReturn>, money: (Money) -> String): ChannelComparisonModel {
        val ranked = rows.sortedWith(compareByDescending<ChannelReturn> { it.value.returnPpm }.thenBy { it.channel.ordinal })
        val topReturn = ranked.firstOrNull()?.value?.returnPpm ?: 0L
        val out = ranked.map { r ->
            val v = r.value
            ChannelRow(
                label = r.channel.label,
                blurb = r.channel.blurb,
                facts = listOf(
                    Fact("Daily spend", money(r.dailySpend)),
                    Fact("Reach", Formatting.percentPpm(r.reachPpm)),
                    Fact("Awareness it settles at", Formatting.percentPpm(v.awarenessPpm)),
                    Fact("Loyalty it settles at", Formatting.percentPpm(v.loyaltyPpm)),
                    Fact("Extra revenue a day", money(v.incrementalRevenue)),
                    Fact("Extra profit a day", money(v.incrementalProfit), Tones.bySign(v.incrementalProfit.raw)),
                    Fact("Return", Formatting.ratioTimes(v.returnPpm), hint = "Gross profit for each unit spent"),
                    Fact("Fit with the audience", Formatting.percentPpm(r.fitPpm)),
                ),
                tone = tone(v.returnPpm, r.marginalReturnPpm),
                verdict = verdict(v.returnPpm, r.marginalReturnPpm),
                best = v.returnPpm > 0L && v.returnPpm == topReturn,
            )
        }
        val note = if (ranked.isEmpty()) "Nothing to compare." else "Each channel is shown running alone at the spend given. Mixing channels reaches people any one of them misses."
        return ChannelComparisonModel(title, out, note)
    }
}
