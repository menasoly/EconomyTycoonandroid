package app.many.economytycoon.visualization.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.visualization.AxisLabels
import app.many.economytycoon.visualization.ChartBounds
import app.many.economytycoon.visualization.DataPoint
import app.many.economytycoon.visualization.Downsample
import app.many.economytycoon.visualization.LineSeries
import app.many.economytycoon.visualization.LinearScale

/**
 * Draws one or more series on shared axes. Touching the chart shows the nearest value of the first series;
 * [xLabel] turns an x value into text such as a date.
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    height: Int = 180,
    includeZero: Boolean = false,
    xLabel: (Double) -> String = { AxisLabels.compact(it) },
    yLabel: (Double) -> String = { AxisLabels.compact(it) },
    maxPoints: Int = 160,
) {
    val t = Theme.tokens
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = t.textSecondary)
    val shown = remember(series, maxPoints) { series.map { LineSeries(it.name, Downsample.lttb(it.points, maxPoints), it.colorSlot) } }
    val bounds = remember(shown, includeZero) { ChartBounds.of(shown, includeZero) }
    var touched by remember { mutableStateOf<DataPoint?>(null) }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(height.dp).pointerInput(shown) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val p = event.changes.firstOrNull()
                        val lead = shown.firstOrNull { !it.isEmpty }
                        touched = if (p != null && p.pressed && lead != null) {
                            val left = 44.dp.toPx(); val plotW = size.width - left - 8.dp.toPx()
                            val span = (bounds.maxX - bounds.minX).takeIf { it > 0.0 } ?: 1.0
                            val wx = bounds.minX + span * ((p.position.x - left) / plotW).coerceIn(0f, 1f)
                            lead.points.minByOrNull { kotlin.math.abs(it.x - wx) }
                        } else null
                    }
                }
            },
        ) {
            val left = 44.dp.toPx(); val right = 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = 20.dp.toPx()
            val plotW = size.width - left - right; val plotH = size.height - top - bottom
            val x = LinearScale(bounds.minX, bounds.maxX.takeIf { it > bounds.minX } ?: (bounds.minX + 1.0), left.toDouble(), (left + plotW).toDouble())
            val y = LinearScale(bounds.axis.min, bounds.axis.max, (top + plotH).toDouble(), top.toDouble())
            for (tick in bounds.axis.ticks) {
                val py = y.toPixel(tick).toFloat()
                drawLine(t.outline, Offset(left, py), Offset(left + plotW, py), strokeWidth = 1f)
                val label = measurer.measure(yLabel(tick), labelStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 4.dp.toPx(), py - label.size.height / 2f))
            }
            val first = measurer.measure(xLabel(bounds.minX), labelStyle)
            drawText(first, topLeft = Offset(left, size.height - first.size.height))
            val lastLabel = measurer.measure(xLabel(bounds.maxX), labelStyle)
            drawText(lastLabel, topLeft = Offset(left + plotW - lastLabel.size.width, size.height - lastLabel.size.height))
            for (s in shown) {
                if (s.isEmpty) continue
                val path = Path()
                s.points.forEachIndexed { i, p ->
                    val px = x.toPixel(p.x).toFloat(); val py = y.toPixel(p.y).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, slotColor(s.colorSlot), style = Stroke(width = 2.dp.toPx()))
            }
            val lead = shown.firstOrNull { !it.isEmpty }
            val point = touched
            if (point != null && lead != null) {
                val px = x.toPixel(point.x).toFloat(); val py = y.toPixel(point.y).toFloat()
                drawLine(t.textSecondary, Offset(px, top), Offset(px, top + plotH), strokeWidth = 1f)
                drawCircle(slotColor(lead.colorSlot), 4.dp.toPx(), Offset(px, py))
            }
        }
        val lead = shown.firstOrNull { !it.isEmpty }
        if (lead != null) {
            val readout: DataPoint? = touched ?: lead.last
            Row(Modifier.fillMaxWidth().padding(top = Spacing.XS)) {
                for (s in shown) {
                    Text("● ${s.name}", style = Type.Caption, color = slotColor(s.colorSlot), modifier = Modifier.padding(end = Spacing.M))
                }
                if (readout != null) Text("${xLabel(readout.x)}: ${yLabel(readout.y)}", style = Type.Caption, color = t.textSecondary)
            }
        }
    }
}
