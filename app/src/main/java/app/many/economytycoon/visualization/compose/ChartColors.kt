package app.many.economytycoon.visualization.compose

import androidx.compose.ui.graphics.Color
import app.many.economytycoon.visualization.Palette as ChartPalette

/** Converts the pure ARGB ints from the visualization package into Compose colours. */
fun argb(value: Int): Color = Color(value)

fun slotColor(slot: Int): Color = argb(ChartPalette.slot(slot))
