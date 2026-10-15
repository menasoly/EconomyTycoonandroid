package app.many.economytycoon.ui.app

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.persistence.IncompatibleContentException
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.persistence.SaveNotFoundException
import app.many.economytycoon.persistence.MemorySaveStore
import app.many.economytycoon.persistence.ProfileStore
import app.many.economytycoon.persistence.SaveSlot
import app.many.economytycoon.scenarios.PlayerProfile
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioEvent
import app.many.economytycoon.session.Difficulty
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.GameSettings
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.session.NewGameOptions
import app.many.economytycoon.session.SettingsStore
import app.many.economytycoon.world.WorldSize

/** What the app needs from its surroundings. The Android side supplies real storage; tests supply memory. */
class AppEnvironment(
    val content: ContentRepository,
    val saves: SaveManager,
    val settings: SettingsStore,
    val playerId: Long = 1L,
    val profile: ProfileStore = ProfileStore(MemorySaveStore()),
    val rules: app.many.economytycoon.watch.WatchStore = app.many.economytycoon.watch.WatchStore(MemorySaveStore()),
)

/** The screen the whole app is showing, before any in-game navigation. */
sealed class AppScreen {
    object Menu : AppScreen()
    data class Setup(val draft: NewGameDraft) : AppScreen()
    object LoadList : AppScreen()
    /** The scenario list; [selected] is the one whose details are showing. */
    data class Scenarios(val selected: String? = null, val companyName: String = "") : AppScreen()
    /** Everything the player has earned across games. */
    object Achievements : AppScreen()
    data class Playing(val session: GameSession) : AppScreen()
}

/** Choices on the new-game screen, kept as the player typed them until they start. */
data class NewGameDraft(
    val companyName: String = "",
    val difficulty: Difficulty = Difficulty.STANDARD,
    val worldSize: WorldSize = WorldSize.SMALL,
    val seedText: String = "",
    val countryIndex: Int = 0,
) {
    /** A blank seed means "pick one for me"; anything else must be a whole number. */
    val seedOrNull: Long? get() = seedText.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()

    fun problems(): List<String> {
        val out = ArrayList<String>()
        val name = companyName.trim()
        if (name.length < 3) out += "Give your company a name of at least three characters."
        if (name.length > 40) out += "Company names can be at most 40 characters."
        if (seedText.isNotBlank() && seedOrNull == null) out += "The world seed must be a whole number."
        if (countryIndex !in 0 until worldSize.countries) out += "Pick a country."
        return out
    }

    fun isValid(): Boolean = problems().isEmpty()
}

/** Why a load failed, in words the player can act on. */
sealed class LoadFailure(val message: String) {
    class Missing(slot: String) : LoadFailure("The save '$slot' no longer exists.")
    class Damaged(detail: String) : LoadFailure("The save is damaged and no backup could be read. ($detail)")
    class WrongContent(detail: String) : LoadFailure("The save needs different game content. ($detail)")
}

/**
 * Moves between the menu, the new-game setup, the save list and a running game. No drawing happens here, so
 * every transition and failure message is tested on the JVM.
 */
class AppController(private val env: AppEnvironment, private val entropy: () -> Long = { System.nanoTime() }) {
    var screen: AppScreen = AppScreen.Menu
        private set
    var settings: GameSettings = env.settings.load()
        private set
    var profile: PlayerProfile = env.profile.load()
        private set
    var lastFailure: LoadFailure? = null
        private set
    var recoveredFromBackup: Boolean = false
        private set

    fun showMenu() { screen = AppScreen.Menu; lastFailure = null }

    fun openSetup() { screen = AppScreen.Setup(NewGameDraft(seedText = "")); lastFailure = null }

    fun updateDraft(draft: NewGameDraft) {
        if (screen is AppScreen.Setup) screen = AppScreen.Setup(draft)
    }

    fun openAchievements() { screen = AppScreen.Achievements; lastFailure = null }

    fun openScenarios() { screen = AppScreen.Scenarios(profile.unlocked().lastOrNull()?.id); lastFailure = null }

    fun selectScenario(id: String) {
        val s = screen as? AppScreen.Scenarios ?: return
        if (profile.isUnlocked(id)) screen = s.copy(selected = id)
    }

    fun updateScenarioName(name: String) {
        val s = screen as? AppScreen.Scenarios ?: return
        screen = s.copy(companyName = name)
    }

    /** Starts the selected scenario; null on success, otherwise what is wrong. */
    fun startScenario(): List<String>? {
        val s = screen as? AppScreen.Scenarios ?: return listOf("Not on the scenario screen.")
        val def = s.selected?.let { ScenarioCatalog.byId(it) } ?: return listOf("Choose a scenario.")
        if (!profile.isUnlocked(def.id)) return listOf("Win the scenario before this one to unlock it.")
        val name = s.companyName.trim()
        if (name.length < 3) return listOf("Give your company a name of at least three characters.")
        if (name.length > 40) return listOf("Company names can be at most 40 characters.")
        return when (val r = NewGame.start(NewGame.forScenario(def, name, env.playerId), env.content, settings, env.saves)) {
            is Outcome.Ok -> { attach(r.value); noteNewGame(r.value); screen = AppScreen.Playing(r.value); null }
            is Outcome.Rejected -> listOf(r.rejection.message)
        }
    }

    /** Keeps scenario results in the profile as they happen. */
    private fun noteNewGame(session: GameSession) {
        profile.stats.add(app.many.economytycoon.achievements.Stat.GAMES_STARTED)
        session.achievements.evaluate()
        env.profile.save(profile)
    }

    private fun attach(session: GameSession) {
        session.achievements.profile = profile
        session.profileSink = { env.profile.save(profile) }
        session.watch = env.rules.load()
        session.watchSink = { env.rules.save(session.watch) }
        session.scenarioSink = { ev ->
            when (ev) {
                is ScenarioEvent.Won -> profile.won(ev.scenario, ev.medal, ev.days)
                is ScenarioEvent.Lost -> profile.lost(ev.scenario)
                is ScenarioEvent.GoalReached -> Unit
            }
            if (ev !is ScenarioEvent.GoalReached) env.profile.save(profile)
        }
    }

    fun openLoadList() { screen = AppScreen.LoadList; lastFailure = null }

    fun slots(): List<SaveSlot> = env.saves.list()

    /** The save to offer under "Continue": the most recently written, readable one. */
    fun continueSlot(): SaveSlot? = env.saves.list().firstOrNull { !it.damaged }

    /** Starts the game described by the current draft; null on success, otherwise the reasons it cannot start. */
    fun begin(): List<String>? {
        val setup = screen as? AppScreen.Setup ?: return listOf("Not on the setup screen.")
        val draft = setup.draft
        draft.problems().takeIf { it.isNotEmpty() }?.let { return it }
        val options = NewGameOptions(
            companyName = draft.companyName.trim(), difficulty = draft.difficulty, worldSize = draft.worldSize,
            seed = draft.seedOrNull ?: entropy(), countryIndex = draft.countryIndex, playerId = env.playerId,
        )
        return when (val r = NewGame.start(options, env.content, settings, env.saves)) {
            is Outcome.Ok -> { attach(r.value); noteNewGame(r.value); screen = AppScreen.Playing(r.value); null }
            is Outcome.Rejected -> listOf(r.rejection.message)
        }
    }

    fun load(slot: String): Boolean {
        lastFailure = null
        return try {
            val loaded = env.saves.load(slot, env.content)
            recoveredFromBackup = loaded.recoveredFromBackup
            val resumed = GameSession.resume(loaded.simulation, env.playerId, settings, env.saves)
            attach(resumed)
            screen = AppScreen.Playing(resumed)
            true
        } catch (e: SaveNotFoundException) {
            lastFailure = LoadFailure.Missing(slot); false
        } catch (e: IncompatibleContentException) {
            lastFailure = LoadFailure.WrongContent(e.message.orEmpty()); false
        } catch (e: ByteReader.CorruptDataException) {
            lastFailure = LoadFailure.Damaged(e.message.orEmpty()); false
        }
    }

    fun continueGame(): Boolean = continueSlot()?.let { load(it.id) } ?: false

    /** Leaves the running game. With [save] set, writes a slot first so nothing is lost. */
    fun quitToMenu(save: Boolean = true) {
        val playing = screen as? AppScreen.Playing
        if (playing != null && save) playing.session.save(QUIT_SLOT, "Last session")
        if (playing != null) env.profile.save(profile)
        showMenu()
    }

    fun deleteSlot(slot: String) { env.saves.delete(slot) }

    fun renameSlot(slot: String, label: String) { env.saves.rename(slot, label) }

    fun updateSettings(next: GameSettings) {
        settings = next
        env.settings.save(next)
    }

    companion object {
        const val QUIT_SLOT = "last-session"
    }
}
