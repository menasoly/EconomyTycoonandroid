package app.many.economytycoon.visualization.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.visualization.Argb
import app.many.economytycoon.visualization.Rect
import app.many.economytycoon.visualization.Treemap

/** Rectangles sized by value, for showing how a total divides between many parts. */
@Composable
fun TreemapView(items: List<Pair<String, Double>>, modifier: Modifier = Modifier, height: Int = 200) {
    val t = Theme.tokens
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val cells = Treemap.layout(items, Rect(0.0, 0.0, size.width.toDouble(), size.height.toDouble()))
        for (c in cells) {
            val color = slotColor(c.colorSlot)
            val topLeft = Offset(c.rect.x.toFloat() + 1f, c.rect.y.toFloat() + 1f)
            val cellSize = Size((c.rect.width - 2.0).toFloat().coerceAtLeast(0f), (c.rect.height - 2.0).toFloat().coerceAtLeast(0f))
            drawRect(color, topLeft, cellSize)
            if (cellSize.width > 48.dp.toPx() && cellSize.height > 24.dp.toPx()) {
                val textColor = Color(Argb.onColor(argbOf(color)))
                val layout = measurer.measure(
                    c.label, TextStyle(fontSize = 11.sp, color = textColor), overflow = TextOverflow.Ellipsis, softWrap = false,
                    constraints = Constraints(maxWidth = (cellSize.width - 8.dp.toPx()).toInt().coerceAtLeast(1)),
                )
                drawText(layout, topLeft = Offset(topLeft.x + 4.dp.toPx(), topLeft.y + 4.dp.toPx()))
            }
        }
        if (cells.isEmpty()) drawRect(t.surfaceRaised, Offset.Zero, size)
    }
}

private fun argbOf(c: Color): Int {
    val a = (c.alpha * 255).toInt(); val r = (c.red * 255).toInt(); val g = (c.green * 255).toInt(); val b = (c.blue * 255).toInt()
    return Argb.of(r, g, b, a)
}
