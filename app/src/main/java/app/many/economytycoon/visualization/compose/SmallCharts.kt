package app.many.economytycoon.visualization.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.visualization.Gauge
import app.many.economytycoon.visualization.PieLayout
import app.many.economytycoon.visualization.Sparkline

/** A tiny trend line with no axes, for lists. */
@Composable
fun SparklineView(values: List<Double>, modifier: Modifier = Modifier, color: Color = Theme.tokens.accent) {
    val norm = remember(values) { Sparkline.normalise(values) }
    Canvas(modifier.size(width = 72.dp, height = 24.dp)) {
        if (norm.size < 2) return@Canvas
        val path = Path()
        norm.forEachIndexed { i, v ->
            val px = size.width * i / (norm.size - 1)
            val py = size.height - (v.toFloat() * (size.height - 4.dp.toPx())) - 2.dp.toPx()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A donut chart with a legend underneath. */
@Composable
fun DonutChart(items: List<Pair<String, Double>>, modifier: Modifier = Modifier, centerText: String? = null, valueText: (Double) -> String = { "" }) {
    val t = Theme.tokens
    val slices = remember(items) { PieLayout.slices(items) }
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Canvas(Modifier.size(160.dp)) {
            val stroke = 28.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (slices.isEmpty()) drawArc(t.outline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            for (s in slices) {
                drawArc(slotColor(s.colorSlot), (s.startDegrees - 90.0).toFloat(), (s.sweepDegrees - 0.8).coerceAtLeast(0.1).toFloat(), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
            if (centerText != null) {
                val layout = measurer.measure(centerText, TextStyle(fontSize = 14.sp, color = t.textPrimary))
                drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f))
            }
        }
        for (s in slices) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = slotColor(s.colorSlot), style = Type.Caption)
                Text(s.label, style = Type.Body, color = t.textPrimary, modifier = Modifier.weight(1f).padding(start = Spacing.S))
                Text("${valueText(s.value)}  ${(s.share * 100).toInt()}%", style = Type.Caption, color = t.textSecondary)
            }
        }
    }
}

/** A row of labelled horizontal bars scaled to the largest value. */
@Composable
fun BarList(items: List<Pair<String, Double>>, modifier: Modifier = Modifier, valueText: (Double) -> String, colorOf: (Int) -> Color = { slotColor(it) }) {
    val t = Theme.tokens
    val top = items.maxOfOrNull { kotlin.math.abs(it.second) }?.coerceAtLeast(1e-9) ?: 1.0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        items.forEachIndexed { i, (label, value) ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, style = Type.Body, color = t.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(valueText(value), style = Type.Figure, color = t.textSecondary)
                }
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        drawRoundRect(t.surfaceRaised, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                        val w = (size.width * (kotlin.math.abs(value) / top)).toFloat()
                        drawRoundRect(colorOf(i), size = Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    }
                }
            }
        }
    }
}

/** A 270-degree dial for a value between [min] and [max]. */
@Composable
fun GaugeView(value: Double, min: Double, max: Double, label: String, valueText: String, modifier: Modifier = Modifier, color: Color = Theme.tokens.accent) {
    val t = Theme.tokens
    val fraction = Gauge.fractionOf(value, min, max)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = 12.dp.toPx(); val inset = stroke / 2f
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(t.surfaceRaised, Gauge.START_DEGREES.toFloat(), Gauge.SWEEP_DEGREES.toFloat(), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, Gauge.START_DEGREES.toFloat(), Gauge.sweepFor(fraction).toFloat(), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(valueText, style = Type.Figure, color = t.textPrimary)
        }
        Text(label, style = Type.Label, color = t.textSecondary)
    }
}
