package app.many.economytycoon.ui.present

import app.many.economytycoon.automation.ManagerAction
import app.many.economytycoon.automation.ManagerKind
import app.many.economytycoon.automation.ManagerSettings
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.GameSession

data class ManagerRow(val kind: ManagerKind, val label: String, val summary: String, val cadence: String, val on: Boolean)

data class ManagerLogRow(val day: String, val label: String, val note: String)

data class ManagersModel(
    val tiles: List<Tile>,
    val rows: List<ManagerRow>,
    val reserveText: String,
    val repairPercent: Int,
    val wasteUnits: Long,
    val marginPercent: Int,
    val focus: TechCategory?,
    val focuses: List<TechCategory>,
    val log: List<ManagerLogRow>,
    val empty: String,
)

/** Which jobs are handed to managers, the limits they keep to, and what they have done. */
object ManagersPresenter {
    const val EMPTY = "No manager has done anything yet. Switch one on and its work shows up here."

    fun cadence(k: ManagerKind) = if (k.everyDays == 1) "Every day" else "Every ${k.everyDays} days"

    fun row(k: ManagerKind, s: ManagerSettings) = ManagerRow(k, k.label, k.summary, cadence(k), s.isOn(k))

    fun logRow(a: ManagerAction) = ManagerLogRow("Day ${a.day}", a.kind.label, a.note)

    fun build(session: GameSession): ManagersModel {
        val s = session.managers
        val log = session.managerLog().take(30).map(::logRow)
        val onCount = s.enabled.size
        return ManagersModel(
            tiles = listOf(
                Tile("Managers on", "$onCount of ${ManagerKind.values().size}", if (onCount == 0) Tone.NEUTRAL else Tone.GOOD),
                Tile("Cash kept back", session.settings.money(s.reserve)),
                Tile("Recent actions", log.size.toString()),
            ),
            rows = ManagerKind.values().map { row(it, s) },
            reserveText = (s.reserve.raw / Money.SCALE).toString(),
            repairPercent = (s.repairBelowPpm / 10_000L).toInt(),
            wasteUnits = s.wasteUnits,
            marginPercent = (s.minMarginPpm / 10_000L).toInt(),
            focus = s.researchFocus,
            focuses = TechCategory.values().toList(),
            log = log,
            empty = EMPTY,
        )
    }

    fun toggle(session: GameSession, kind: ManagerKind) {
        session.applyManagers(session.managers.with(kind, !session.managers.isOn(kind)))
    }

    fun setAll(session: GameSession, on: Boolean) {
        session.applyManagers(session.managers.copy(enabled = if (on) ManagerKind.values().toSet() else emptySet()))
    }

    /** Sets the cash reserve from text in whole currency units; returns a message when the text is not usable. */
    fun setReserve(session: GameSession, text: String): String? {
        val units = text.trim().toLongOrNull() ?: return "Enter a whole number."
        if (units < 0L || units > 1_000_000_000L) return "Enter an amount from 0 to 1,000,000,000."
        session.applyManagers(session.managers.copy(reserve = Money.units(units)))
        return null
    }

    fun setRepairPercent(session: GameSession, percent: Int): String? {
        if (percent !in 10..100) return "Choose a value from 10 to 100."
        session.applyManagers(session.managers.copy(repairBelowPpm = percent * 10_000L))
        return null
    }

    fun setWasteUnits(session: GameSession, units: Long): String? {
        if (units !in 1L..100_000L) return "Choose a value from 1 to 100,000."
        session.applyManagers(session.managers.copy(wasteUnits = units))
        return null
    }

    fun setMarginPercent(session: GameSession, percent: Int): String? {
        if (percent !in -50..200) return "Choose a value from -50 to 200."
        session.applyManagers(session.managers.copy(minMarginPpm = percent * 10_000L))
        return null
    }

    fun setFocus(session: GameSession, focus: TechCategory?) {
        session.applyManagers(session.managers.copy(researchFocus = focus))
    }
}
