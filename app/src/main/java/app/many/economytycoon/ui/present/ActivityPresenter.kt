package app.many.economytycoon.ui.present

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.ActivityEntry
import app.many.economytycoon.activity.ActivityLog
import app.many.economytycoon.activity.Origin
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class ActivityRow(val sequence: Long, val text: String, val area: String, val origin: String, val movesMoney: Boolean, val tone: Tone)

data class ActivityDay(val tick: Long, val label: String, val rows: List<ActivityRow>)

data class ActivityModel(
    val areas: List<ActivityArea>,
    val chosen: ActivityArea?,
    val days: List<ActivityDay>,
    val count: Int,
    val empty: String,
)

/** What has been done for the company, a day at a time, newest first. */
object ActivityPresenter {
    const val EMPTY = "Nothing yet. Orders you give will be listed here."

    fun originTone(o: Origin): Tone = when (o) {
        Origin.PLAYER -> Tone.NEUTRAL
        Origin.AI -> Tone.INFO
        Origin.SYSTEM -> Tone.MUTED
    }

    fun row(e: ActivityEntry): ActivityRow = ActivityRow(e.sequence, e.text, e.area.label, e.origin.label, e.movesMoney, originTone(e.origin))

    fun dayLabel(tick: Long, now: Long): String = when (val ago = now - tick) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> if (ago in 2L..29L) "$ago days ago · ${Formatting.date(Tick(tick))}" else Formatting.date(Tick(tick))
    }

    fun model(entries: List<ActivityEntry>, chosen: ActivityArea?, now: Long): ActivityModel {
        val shown = ActivityLog.inArea(entries, chosen)
        return ActivityModel(
            areas = ActivityLog.areasIn(entries),
            chosen = chosen,
            days = ActivityLog.byDay(shown).map { (tick, list) -> ActivityDay(tick, dayLabel(tick, now), list.map { row(it) }) },
            count = shown.size,
            empty = if (entries.isEmpty()) EMPTY else "Nothing in this area.",
        )
    }

    fun build(session: GameSession, chosen: ActivityArea?, limit: Int = 200): ActivityModel? {
        session.selectedCompany ?: return null
        return model(session.activity(limit), chosen, session.tick.value)
    }
}
