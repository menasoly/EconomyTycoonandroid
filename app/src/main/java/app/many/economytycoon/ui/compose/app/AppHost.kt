package app.many.economytycoon.ui.compose.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.many.economytycoon.persistence.SaveSlot
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.ui.app.AppController
import app.many.economytycoon.ui.app.AppScreen
import app.many.economytycoon.ui.app.LoadFailure
import app.many.economytycoon.ui.app.NewGameDraft

/**
 * Makes the plain [AppController] observable. Every action goes through here so the root composable
 * recomposes when the controller moves to another screen.
 */
@Stable
class AppHost(val controller: AppController) {
    var revision: Int by mutableStateOf(0)
        private set

    val screen: AppScreen get() { revision; return controller.screen }
    val settings: GameSettings get() { revision; return controller.settings }
    val lastFailure: LoadFailure? get() { revision; return controller.lastFailure }
    val recoveredFromBackup: Boolean get() { revision; return controller.recoveredFromBackup }

    private inline fun <T> bump(block: () -> T): T { val r = block(); revision++; return r }

    fun showMenu() = bump { controller.showMenu() }
    fun openSetup() = bump { controller.openSetup() }
    fun openLoadList() = bump { controller.openLoadList() }
    fun openScenarios() = bump { controller.openScenarios() }
    fun openAchievements() = bump { controller.openAchievements() }
    fun selectScenario(id: String) = bump { controller.selectScenario(id) }
    fun updateScenarioName(name: String) = bump { controller.updateScenarioName(name) }
    fun startScenario(): List<String>? = bump { controller.startScenario() }
    val profile get() = run { revision; controller.profile }
    fun updateDraft(d: NewGameDraft) = bump { controller.updateDraft(d) }
    fun begin(): List<String>? = bump { controller.begin() }
    fun load(slot: String): Boolean = bump { controller.load(slot) }
    fun continueGame(): Boolean = bump { controller.continueGame() }
    fun quitToMenu(save: Boolean = true) = bump { controller.quitToMenu(save) }
    fun deleteSlot(slot: String) = bump { controller.deleteSlot(slot) }
    fun renameSlot(slot: String, label: String) = bump { controller.renameSlot(slot, label) }
    fun updateSettings(next: GameSettings) = bump { controller.updateSettings(next) }
    /** Writes the running game to the 'last session' slot without leaving it; used when the app goes to the background. */
    fun saveQuietly() {
        val playing = controller.screen as? AppScreen.Playing ?: return
        playing.session.save(AppController.QUIT_SLOT, "Last session")
    }

    fun slots(): List<SaveSlot> { revision; return controller.slots() }
    fun canContinue(): Boolean { revision; return controller.continueSlot() != null }
}
