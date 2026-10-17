package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankEvent
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.banking.ResolutionTool
import app.many.economytycoon.core.Money

/** A banking event in words, with a severity tone, for the news feed and alerts. */
data class BankHeadline(val text: String, val tone: Tone, val bank: BankId?, val tick: Long, val important: Boolean)

object BankEventText {
    fun headline(e: BankEvent, name: (BankId) -> String, money: (Money) -> String): BankHeadline {
        fun n(id: BankId) = name(id)
        val bank = e.subject
        val (text, tone, important) = when (e) {
            is BankEvent.Founded -> Triple("${n(e.bank)} opens for business", Tone.INFO, false)
            is BankEvent.RungChanged -> if (e.to > e.from)
                Triple("${n(e.bank)} falls to \"${e.to.label}\" in capital supervision", BankingLabels.rungTone(e.to), e.to.isBreach)
            else Triple("${n(e.bank)} rebuilds capital: now \"${e.to.label}\"", Tone.GOOD, false)
            is BankEvent.RunStageChanged -> if (e.to > e.from)
                Triple("Depositors at ${n(e.bank)} turn ${e.to.label.lowercase()}", BankingLabels.runTone(e.to), e.to >= app.many.economytycoon.banking.RunStage.RUN)
            else Triple("Calm returns at ${n(e.bank)}: ${e.to.label.lowercase()}", Tone.GOOD, false)
            is BankEvent.RatingChanged -> if (e.to < e.from)
                Triple("${n(e.bank)} is upgraded to ${e.to.label}", Tone.GOOD, false)
            else Triple("${n(e.bank)} is downgraded to ${e.to.label}", Tone.WARN, false)
            is BankEvent.DividendPaid -> Triple("${n(e.bank)} pays a dividend of ${money(e.amount)}", Tone.NEUTRAL, false)
            is BankEvent.Rumour -> Triple("Rumours spread about ${n(e.bank)}", Tone.WARN, true)
            is BankEvent.FacilityUsed -> Triple("${n(e.bank)} borrows ${money(e.marginal + e.emergency)} from the central bank", if (e.emergency.isPositive) Tone.WARN else Tone.NEUTRAL, e.emergency.isPositive)
            is BankEvent.FireSale -> Triple("${n(e.bank)} sells ${money(e.amount)} of assets in a hurry", Tone.WARN, false)
            is BankEvent.LiquidityFailure -> Triple("${n(e.bank)} cannot meet withdrawals, short by ${money(e.shortfall)}", Tone.BAD, true)
            is BankEvent.InterbankFrozen -> Triple("Banks stop lending to each other", Tone.BAD, true)
            is BankEvent.Supervised -> Triple("${n(e.bank)} placed under supervision: ${e.reason}", Tone.BAD, true)
            is BankEvent.Released -> Triple("${n(e.bank)} released from supervision", Tone.GOOD, false)
            is BankEvent.Resolved -> {
                val o = e.outcome
                val who = n(o.bank)
                val t = when (o.tool) {
                    ResolutionTool.RECAPITALISE -> "$who is rescued: creditors lose ${money(o.bailedIn)} and ${money(o.injection)} of new capital goes in"
                    ResolutionTool.MERGER -> "$who is merged into ${o.acquirer?.let(name) ?: "another bank"}"
                    ResolutionTool.LIQUIDATION -> "$who is wound up; the guarantee fund pays ${money(o.insuredPaid)} to insured depositors"
                }
                Triple(t, Tone.BAD, true)
            }
            is BankEvent.CapitalRaised -> Triple("${n(e.bank)} raises ${money(e.amount)} of ${e.instrument.label.lowercase()}", Tone.INFO, false)
            is BankEvent.LevyCollected -> Triple("Banks pay ${money(e.amount)} into the guarantee fund", Tone.NEUTRAL, false)
            is BankEvent.StressTestPublished -> Triple(
                if (e.failures == 0) "Stress test (${e.scenario}): all ${e.banks} banks pass" else "Stress test (${e.scenario}): ${e.failures} of ${e.banks} banks fail",
                if (e.failures == 0) Tone.GOOD else Tone.WARN, e.failures > 0,
            )
            is BankEvent.Strategy -> Triple("${n(e.bank)}: ${e.summary}", Tone.MUTED, false)
        }
        return BankHeadline(text, tone, bank, e.tick, important)
    }

    fun headlines(events: List<BankEvent>, name: (BankId) -> String, money: (Money) -> String, onlyImportant: Boolean = false): List<BankHeadline> =
        events.map { headline(it, name, money) }.filter { !onlyImportant || it.important }
}
