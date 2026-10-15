package app.many.economytycoon.ui.compose.design

import androidx.compose.ui.graphics.Color

/** Raw colours. Screens never use these directly; they read them through [Tokens]. */
object Palette {
    val Ink900 = Color(0xFF111A22)
    val Ink800 = Color(0xFF18242E)
    val Ink700 = Color(0xFF233342)
    val Ink500 = Color(0xFF5C7184)
    val Ink300 = Color(0xFF9FB0BF)
    val Paper50 = Color(0xFFF7F9FB)
    val Paper100 = Color(0xFFEDF1F5)
    val Paper200 = Color(0xFFDDE4EA)
    val White = Color(0xFFFFFFFF)

    val Teal500 = Color(0xFF138A7D)
    val Teal300 = Color(0xFF4FC3B2)
    val Amber500 = Color(0xFFD98A12)
    val Amber300 = Color(0xFFF2B84B)
    val Red500 = Color(0xFFC8402F)
    val Red300 = Color(0xFFF08A7C)
    val Green500 = Color(0xFF2E8B4F)
    val Green300 = Color(0xFF7BCB98)
    val Blue500 = Color(0xFF2F6FB5)
    val Blue300 = Color(0xFF8DB8E8)
}

/** Meaning-based colours that change with the light or dark scheme. */
data class Tokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val info: Color,
    val dark: Boolean,
) {
    companion object {
        val Light = Tokens(
            background = Palette.Paper50, surface = Palette.White, surfaceRaised = Palette.Paper100, outline = Palette.Paper200,
            textPrimary = Palette.Ink900, textSecondary = Palette.Ink500, accent = Palette.Teal500, onAccent = Palette.White,
            positive = Palette.Green500, negative = Palette.Red500, warning = Palette.Amber500, info = Palette.Blue500, dark = false,
        )
        val Dark = Tokens(
            background = Palette.Ink900, surface = Palette.Ink800, surfaceRaised = Palette.Ink700, outline = Palette.Ink500,
            textPrimary = Palette.Paper50, textSecondary = Palette.Ink300, accent = Palette.Teal300, onAccent = Palette.Ink900,
            positive = Palette.Green300, negative = Palette.Red300, warning = Palette.Amber300, info = Palette.Blue300, dark = true,
        )
    }
}
