package app.many.economytycoon.ui.compose.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.many.economytycoon.ui.app.AppScreen
import app.many.economytycoon.ui.compose.design.EconomyTheme
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.screens.LoadListScreen
import app.many.economytycoon.ui.compose.screens.MenuScreen
import app.many.economytycoon.ui.compose.screens.AchievementsMenuScreen
import app.many.economytycoon.ui.compose.screens.ScenariosScreen
import app.many.economytycoon.ui.compose.screens.SetupScreen

/** Root of the interface: the theme, the text-size setting and whichever top-level screen is showing. */
@Composable
fun EconomyApp(host: AppHost) {
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * host.settings.uiScalePercent / 100f)
    CompositionLocalProvider(LocalDensity provides scaled) {
        EconomyTheme {
            Box(Modifier.fillMaxSize().background(Theme.tokens.background)) {
                when (val screen = host.screen) {
                    AppScreen.Menu -> MenuScreen(host)
                    is AppScreen.Setup -> SetupScreen(host, screen.draft)
                    AppScreen.LoadList -> LoadListScreen(host)
                    is AppScreen.Scenarios -> ScenariosScreen(host, screen)
                    AppScreen.Achievements -> AchievementsMenuScreen(host)
                    is AppScreen.Playing -> GameShell(host, screen.session)
                }
            }
        }
    }
}
