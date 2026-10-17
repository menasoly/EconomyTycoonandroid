package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankInsight

data class AdviceCard(
    val code: String,
    val topic: String,
    val title: String,
    val detail: String,
    val tone: Tone,
    val action: BankAction?,
    val actionLabel: String?,
)

object BankAdvicePresenter {
    fun tone(priority: Int): Tone = when {
        priority >= 80 -> Tone.BAD
        priority >= 50 -> Tone.WARN
        priority >= 20 -> Tone.INFO
        else -> Tone.MUTED
    }

    fun cards(insights: List<BankInsight>, limit: Int = 8): List<AdviceCard> =
        insights.sortedWith(compareByDescending<BankInsight> { it.priority }.thenBy { it.code }).take(limit).map {
            AdviceCard(it.code, it.topic.label, it.title, it.detail, tone(it.priority), it.suggestion, it.suggestion?.let { a -> BankActionText.short(a) })
        }

    fun empty(): String = "Nothing needs attention at this bank."
}
