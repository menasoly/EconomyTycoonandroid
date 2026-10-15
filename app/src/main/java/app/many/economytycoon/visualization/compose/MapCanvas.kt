package app.many.economytycoon.visualization.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.visualization.ColorRamp
import app.many.economytycoon.visualization.HitTest
import app.many.economytycoon.visualization.MapLayers
import app.many.economytycoon.visualization.MapMarker
import app.many.economytycoon.visualization.MapViewport
import app.many.economytycoon.visualization.MarkerKind
import app.many.economytycoon.visualization.WorldMapModel

/** Pan, pinch-zoom and tap over a [WorldMapModel]. [onSelect] gets the tapped marker, or null for empty ground. */
@Composable
fun MapCanvas(
    model: WorldMapModel,
    layers: MapLayers,
    selected: MapMarker?,
    onSelect: (MapMarker?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Theme.tokens
    val measurer = rememberTextMeasurer()
    var viewport by remember { mutableStateOf(MapViewport.fitting(model.size, 1.0, 1.0)) }

    Canvas(
        modifier.fillMaxSize()
            .onSizeChanged { viewport = viewport.resized(it.width.toDouble(), it.height.toDouble()) }
            .pointerInput(model) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport = viewport.pan(pan.x.toDouble(), pan.y.toDouble()).zoomAt(centroid.x.toDouble(), centroid.y.toDouble(), zoom.toDouble())
                }
            }
            .pointerInput(model, layers) {
                detectTapGestures(onDoubleTap = { p -> viewport = viewport.zoomAt(p.x.toDouble(), p.y.toDouble(), 2.0) }) { p ->
                    val visible = model.visibleAt(viewport.zoom, layers)
                    val hit = HitTest.nearest(viewport, visible, { it.x to it.y }, p.x.toDouble(), p.y.toDouble(), 28.0)
                    onSelect(hit)
                }
            },
    ) {
        drawRect(if (t.dark) Color(0xFF0E1620) else Color(0xFFE6EEF3))
        val tl = viewport.toPixel(0.0, 0.0); val br = viewport.toPixel(model.size, model.size)
        drawRect(if (t.dark) Color(0xFF16232F) else Color(0xFFF3F6F8), Offset(tl.x.toFloat(), tl.y.toFloat()), androidx.compose.ui.geometry.Size((br.x - tl.x).toFloat(), (br.y - tl.y).toFloat()))

        for (r in model.routesFor(layers)) {
            val a = viewport.toPixel(r.fromX, r.fromY); val b = viewport.toPixel(r.toX, r.toY)
            val base = routeColor(r.mode)
            val color = if (!r.open) t.negative.copy(alpha = 0.5f) else base.copy(alpha = 0.35f + 0.5f * (r.loadPpm / 1_000_000f))
            drawLine(color, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = (1 + r.loadPpm / 500_000f).dp.toPx())
        }

        val heat = ColorRamp.heat
        for (m in model.visibleAt(viewport.zoom, layers)) {
            val p = viewport.toPixel(m.x, m.y)
            val c = Offset(p.x.toFloat(), p.y.toFloat())
            val fill = m.heat?.let { argb(heat.at(it)) } ?: markerColor(m.kind, t.accent)
            val radius = markerRadius(m).dp.toPx()
            when (m.kind) {
                MarkerKind.REGION -> drawCircle(fill.copy(alpha = 0.18f), radius, c)
                MarkerKind.FACILITY -> drawSquare(fill, c, radius)
                MarkerKind.WAREHOUSE -> drawDiamond(fill, c, radius)
                MarkerKind.DEPOSIT -> drawDiamond(fill, c, radius * 0.8f)
                MarkerKind.CITY -> drawCircle(fill, radius, c)
            }
            if (m === selected || (selected != null && selected.kind == m.kind && selected.id == m.id)) {
                drawCircle(t.textPrimary, radius + 4.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            }
            if (viewport.zoom >= 2.5 && m.kind == MarkerKind.CITY || m.kind == MarkerKind.REGION) {
                val label = measurer.measure(m.name, TextStyle(fontSize = 10.sp, color = t.textPrimary))
                drawText(label, topLeft = Offset(c.x + radius + 3.dp.toPx(), c.y - label.size.height / 2f))
            }
        }
    }
}

private fun markerRadius(m: MapMarker): Float = when (m.kind) {
    MarkerKind.REGION -> 20f + 30f * m.weight.toFloat()
    MarkerKind.CITY -> 3f + 6f * m.weight.toFloat()
    MarkerKind.FACILITY -> 6f
    MarkerKind.WAREHOUSE -> 5f
    MarkerKind.DEPOSIT -> 5f
}

private fun markerColor(kind: MarkerKind, accent: Color): Color = when (kind) {
    MarkerKind.FACILITY -> accent
    MarkerKind.WAREHOUSE -> Color(0xFF7E57C2)
    MarkerKind.DEPOSIT -> Color(0xFFB8860B)
    else -> Color(0xFF607D8B)
}

private fun routeColor(mode: TransportMode): Color = when (mode) {
    TransportMode.ROAD -> Color(0xFF8D8D8D)
    TransportMode.RAIL -> Color(0xFF6D4C41)
    TransportMode.SEA -> Color(0xFF2F6FB5)
    TransportMode.AIR -> Color(0xFF9C27B0)
}

private fun DrawScope.drawSquare(color: Color, c: Offset, r: Float) {
    drawRect(color, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2))
}

private fun DrawScope.drawDiamond(color: Color, c: Offset, r: Float) {
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
    }
    drawPath(p, color)
}
