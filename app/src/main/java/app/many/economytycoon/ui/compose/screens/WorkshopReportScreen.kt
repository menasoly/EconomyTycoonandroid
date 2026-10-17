package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.PrimaryButton
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Spacing
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.ui.present.WorkshopNav
import app.many.economytycoon.ui.present.WorkshopReportPresenter
import app.many.economytycoon.workshop.HarnessSettings
import app.many.economytycoon.workshop.StudySpec
import app.many.economytycoon.workshop.SweepParam
import app.many.economytycoon.workshop.SweepRunner
import app.many.economytycoon.workshop.SweepSpec
import app.many.economytycoon.workshop.WorkshopSession

private data class Horizon(val label: String, val days: Int)

/** Plays the draft with four kinds of player and shows what came of it. */
@Composable
fun WorkshopReportScreen(nav: WorkshopNav, workshop: WorkshopSession) {
    var tick by remember { mutableStateOf(0) }
    val runner = workshop.runner
    val busy = runner.busy
    // Tests run on another thread; look again while one is running and once more when it ends.
    LaunchedEffect(busy, runner.completed) {
        while (runner.busy != null) { delay(250); tick++ }
        tick++
    }
    var message by remember { mutableStateOf<String?>(null) }
    val horizons = remember { listOf(Horizon("60 days", 60), Horizon("120 days", 120), Horizon("1 year", 360)) }
    var horizon by remember { mutableStateOf(horizons[0]) }
    var param by remember { mutableStateOf(SweepParam.CAPITAL) }
    val model = remember(tick) { WorkshopReportPresenter.model(workshop.lastStudy) }
    val sweep = remember(tick) { WorkshopReportPresenter.sweepLines(workshop.lastSweep) }
    val regression = remember(tick) { WorkshopReportPresenter.regressionLines(workshop.regression) }
    val compat = remember(tick) { workshop.compat() }
    ScreenFrame("Balance test", onBack = { nav.back() }) {
        ScreenList {
            item { ChoiceChips(horizons, horizon, { it.label }, { horizon = it }) }
            item {
                PrimaryButton(busy ?: "Run test", {
                    val settings = HarnessSettings(days = horizon.days, sampleEvery = maxOf(1, horizon.days / 12))
                    runner.startStudy(StudySpec(settings = settings))
                    tick++
                }, Modifier.fillMaxWidth(), enabled = busy == null)
            }
            (message ?: runner.message)?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.negative) } }
            model.empty?.let { e -> item { EmptyState("No results", e) } }
            if (model.empty == null) {
                item { Text(model.headline, style = Type.Heading, color = model.tone.color()) }
                item { TileGrid(model.tiles) }
                items(model.policies.size) { i ->
                    val p = model.policies[i]
                    Card {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.policy, style = Type.Heading, color = Theme.tokens.textPrimary)
                            Pill("net worth", p.tone.color())
                        }
                        Text(WorkshopReportPresenter.spark(p.curve), style = Type.Body, color = p.tone.color())
                        FactList(p.facts)
                    }
                }
                if (model.findings.isNotEmpty()) item { SectionHeader("What the test noticed") }
                items(model.findings.size) { i ->
                    val f = model.findings[i]
                    Card { Text(f.text, style = Type.Body, color = f.tone.color()); f.about?.let { a -> Text(a, style = Type.Caption, color = Theme.tokens.textSecondary) } }
                }
            }
            item { SectionHeader("Sweep a number") }
            item { ChoiceChips(SweepParam.values().toList(), param, { it.label }, { param = it }) }
            item {
                SecondaryButton("Sweep from half to double", {
                    val now = param.current(workshop.draft)
                    val values = SweepRunner.range(maxOf(1L, now / 2), maxOf(2L, now * 2), 4)
                    runner.startSweep(SweepSpec(param, values, settings = HarnessSettings(days = horizon.days, sampleEvery = maxOf(1, horizon.days / 6))))
                    tick++
                }, Modifier.fillMaxWidth(), enabled = busy == null)
            }
            items(sweep.size) { i -> Text(sweep[i], style = Type.Body, color = Theme.tokens.textPrimary) }
            item { SectionHeader("Against the baseline") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SecondaryButton("Record baseline", { message = (workshop.captureBaseline("baseline") as? Outcome.Rejected)?.rejection?.message ?: "Baseline recorded."; tick++ }, Modifier.weight(1f))
                    SecondaryButton("Compare", { message = (workshop.checkBaseline() as? Outcome.Rejected)?.rejection?.message; tick++ }, Modifier.weight(1f))
                }
            }
            items(regression.size) { i -> Text(regression[i], style = Type.Body, color = WorkshopReportPresenter.regressionTone(workshop.regression).color()) }
            item { SectionHeader("Content") }
            item { Text(WorkshopReportPresenter.compatHeadline(compat), style = Type.Body, color = Theme.tokens.textPrimary) }
            val rows = WorkshopReportPresenter.compatRows(compat)
            items(rows.size) { i -> Text(rows[i].text, style = Type.Caption, color = rows[i].tone.color()) }
        }
    }
}
