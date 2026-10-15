package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.ListRow
import app.many.economytycoon.ui.compose.components.MeterBar
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.FeedPresenter
import app.many.economytycoon.ui.present.OverviewPresenter
import app.many.economytycoon.ui.present.Tone
import app.many.economytycoon.visualization.LineSeries
import app.many.economytycoon.visualization.DataPoint
import app.many.economytycoon.visualization.compose.LineChart

@Composable
fun OverviewScreen(ui: GameUi) {
    val model = remember(ui.revision) { OverviewPresenter.build(ui.session) }
    val guideNudge = remember(ui.revision / 5) { app.many.economytycoon.ui.present.GuidePresenter.nudge(ui.session) }
    val advice = remember(ui.revision / 5) { app.many.economytycoon.ui.present.AdvisorPresenter.build(ui.session, 1) }
    ScreenFrame(model?.companyName ?: "Overview") {
        if (model == null) {
            EmptyState("No company", "You have no company to run. Start a new game from the main menu.")
            return@ScreenFrame
        }
        ScreenList {
            item { Pill(model.standing, model.standingTone.color()) }
            item { TileGrid(model.tiles) }
            if (guideNudge == null) {
                val tip = app.many.economytycoon.ui.present.Tips.forDay(ui.session.tick.value)
                item { SectionHeader("Tip") }
                item {
                    Card(onClick = { ui.open(tip.route ?: Route.Glossary) }) {
                        Text(tip.text, style = Type.Body, color = Theme.tokens.textPrimary)
                        Text(tip.area, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            guideNudge?.let { n ->
                item { SectionHeader("First steps", action = "All steps", onAction = { ui.open(Route.Guide) }) }
                item {
                    Card(onClick = { ui.open(n.route ?: Route.Guide) }) {
                        Text(n.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Text(n.hint, style = Type.Body, color = Theme.tokens.textSecondary)
                        Text(n.position, style = Type.Caption, color = Theme.tokens.textSecondary)
                    }
                }
            }
            if (model.alerts.isNotEmpty()) {
                item { SectionHeader("Needs attention", action = "All alerts", onAction = { ui.open(Route.Alerts) }) }
                items(model.alerts.size) { i ->
                    val a = model.alerts[i]
                    Card(onClick = { FeedPresenter.routeFor(a)?.let { ui.open(it) } ?: ui.open(Route.Alerts) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Pill(a.severity.name.lowercase().replaceFirstChar { it.uppercase() }, OverviewPresenter.severityTone(a.severity).color())
                            Text(a.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        }
                        Text(a.detail, style = Type.Body, color = Theme.tokens.textSecondary)
                    }
                }
            }
            if (advice != null && advice.items.isNotEmpty()) {
                item { SectionHeader("Advice", action = "All advice", onAction = { ui.open(Route.Advisor) }) }
                item {
                    val top = advice.items.first()
                    Card(onClick = { ui.open(Route.Advisor) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Pill(top.topic, top.tone.color())
                            Text(top.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                        }
                        Text(top.detail, style = Type.Body, color = Theme.tokens.textSecondary)
                    }
                }
            }
            item { SectionHeader("Net worth") }
            item {
                Card {
                    val points = model.netWorthTrend.mapIndexed { i, v -> DataPoint(i.toDouble(), v) }
                    if (points.size < 2) Text("The chart fills in as days pass.", style = Type.Body, color = Theme.tokens.textSecondary)
                    else LineChart(listOf(LineSeries("Net worth", points, 0), LineSeries("Cash", model.cashTrend.mapIndexed { i, v -> DataPoint(i.toDouble(), v) }, 1)), xLabel = { "" })
                }
            }
            if (model.nextGoals.isNotEmpty()) {
                item { SectionHeader("Next goals", action = "All goals", onAction = { ui.open(Route.Goals) }) }
                item {
                    Card(onClick = { ui.open(Route.Goals) }) {
                        for (g in model.nextGoals) {
                            Text(g.objective.title, style = Type.Heading, color = Theme.tokens.textPrimary)
                            MeterBar(g.ppm / 1_000_000.0)
                        }
                    }
                }
            }
            item { SectionHeader("Leaderboard") }
            item {
                Card {
                    for (l in model.leaders) {
                        ListRow("${l.rank}. ${l.name}", trailing = l.netWorth, trailingTone = l.tone, onClick = if (l.player) null else ({ ui.open(Route.Rival(l.company.value)) }))
                    }
                }
            }
        }
    }
}
