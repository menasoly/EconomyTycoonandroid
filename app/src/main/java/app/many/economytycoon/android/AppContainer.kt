package app.many.economytycoon.android

import android.content.Context
import app.many.economytycoon.content.ShippedContent
import app.many.economytycoon.persistence.FileSaveStore
import app.many.economytycoon.persistence.ProfileStore
import app.many.economytycoon.persistence.SaveManager
import app.many.economytycoon.session.SettingsStore
import app.many.economytycoon.ui.app.AppController
import app.many.economytycoon.ui.app.AppEnvironment
import app.many.economytycoon.ui.compose.app.AppHost
import java.io.File

/**
 * Builds the app's long-lived objects once per process. Saves live in `filesDir/saves` and preferences in
 * `filesDir/prefs`, so listing saves never meets the settings file.
 */
object AppContainer {
    @Volatile private var host: AppHost? = null

    fun host(context: Context): AppHost = host ?: synchronized(this) {
        host ?: build(context.applicationContext).also { host = it }
    }

    private fun build(context: Context): AppHost {
        val saves = SaveManager(FileSaveStore(File(context.filesDir, "saves")))
        val settings = SettingsStore(FileSaveStore(File(context.filesDir, "prefs")))
        val profile = ProfileStore(FileSaveStore(File(context.filesDir, "profile")))
        val rules = app.many.economytycoon.watch.WatchStore(FileSaveStore(File(context.filesDir, "watch")))
        val managers = app.many.economytycoon.automation.ManagerStore(FileSaveStore(File(context.filesDir, "managers")))
        val env = AppEnvironment(ShippedContent.repository, saves, settings, profile = profile, rules = rules, managers = managers)
        return AppHost(AppController(env))
    }
}
