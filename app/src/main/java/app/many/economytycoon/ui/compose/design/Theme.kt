package app.many.economytycoon.ui.compose.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTokens = staticCompositionLocalOf { Tokens.Light }

/** Shortcut for reading the current tokens: `Theme.tokens.accent`. */
object Theme {
    val tokens: Tokens @Composable get() = LocalTokens.current
}

@Composable
fun EconomyTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val dark = forceDark ?: isSystemInDarkTheme()
    val t = if (dark) Tokens.Dark else Tokens.Light
    val scheme = if (dark) {
        darkColorScheme(primary = t.accent, onPrimary = t.onAccent, background = t.background, surface = t.surface, onSurface = t.textPrimary, onBackground = t.textPrimary, outline = t.outline, error = t.negative)
    } else {
        lightColorScheme(primary = t.accent, onPrimary = t.onAccent, background = t.background, surface = t.surface, onSurface = t.textPrimary, onBackground = t.textPrimary, outline = t.outline, error = t.negative)
    }
    CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(colorScheme = scheme, typography = Type.Material3, content = content)
    }
}
