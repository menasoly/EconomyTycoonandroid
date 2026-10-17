package app.many.economytycoon.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.many.economytycoon.ui.compose.app.GameUi
import app.many.economytycoon.ui.compose.components.Card
import app.many.economytycoon.ui.compose.components.ChoiceChips
import app.many.economytycoon.ui.compose.components.EmptyState
import app.many.economytycoon.ui.compose.components.FactList
import app.many.economytycoon.ui.compose.components.Pill
import app.many.economytycoon.ui.compose.components.ScreenFrame
import app.many.economytycoon.ui.compose.components.ScreenList
import app.many.economytycoon.ui.compose.components.SecondaryButton
import app.many.economytycoon.ui.compose.components.SectionHeader
import app.many.economytycoon.ui.compose.components.TileGrid
import app.many.economytycoon.ui.compose.design.Theme
import app.many.economytycoon.ui.compose.design.Type
import app.many.economytycoon.ui.compose.design.color
import app.many.economytycoon.urban.CityView
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.UrbanView

import app.many.economytycoon.ui.present.UrbanBudgetPresenter

/** A city's money: revenue, spending, service levels, projects and concessions. [issue] runs a command and returns its message. */
@Composable
fun UrbanBudgetScreen(ui: GameUi, view: UrbanView, issue: (UrbanCommand) -> String) {
    var selected by remember { mutableStateOf<CityView?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    ScreenFrame("City budget", onBack = { ui.back() }) {
        if (view.cities.isEmpty()) {
            EmptyState("No cities", "No city has a plan yet.")
            return@ScreenFrame
        }
        val current = view.cities.firstOrNull { it.city == selected?.city } ?: view.cities.first()
        val model = remember(current) { UrbanBudgetPresenter.build(current) }
        ScreenList {
            item { ChoiceChips<CityView>(view.cities, current, { it.name }, { selected = it }) }
            item { TileGrid(model.tiles) }
            message?.let { m -> item { Text(m, style = Type.Body, color = Theme.tokens.textSecondary) } }
            item { SectionHeader("Taxes") }
            item {
                Card {
                    FactList(model.taxFacts)
                    SecondaryButton("Raise income tax", { message = issue(UrbanBudgetPresenter.incomeTax(current.city, current.budget.rates, true)) }, Modifier.fillMaxWidth())
                    SecondaryButton("Lower income tax", { message = issue(UrbanBudgetPresenter.incomeTax(current.city, current.budget.rates, false)) }, Modifier.fillMaxWidth())
                    SecondaryButton("Raise property tax", { message = issue(UrbanBudgetPresenter.propertyTax(current.city, current.budget.rates, true)) }, Modifier.fillMaxWidth())
                    SecondaryButton("Lower property tax", { message = issue(UrbanBudgetPresenter.propertyTax(current.city, current.budget.rates, false)) }, Modifier.fillMaxWidth())
                }
            }
            item { SectionHeader("Revenue") }
            items(model.revenue.size) { i -> Text(model.revenue[i].label + ": " + model.revenue[i].amount, style = Type.Body, color = Theme.tokens.textPrimary) }
            item { SectionHeader("Spending") }
            items(model.expenses.size) { i -> Text(model.expenses[i].label + ": " + model.expenses[i].amount, style = Type.Body, color = Theme.tokens.textPrimary) }
            item { SectionHeader("Services") }
            items(model.services.size) { i ->
                val s = model.services[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.label, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill((s.quality / 10).toString() + " of 100", s.tone.color())
                    }
                    Text("Funded at " + (s.level / 10) + " percent, " + s.cost + " a month", style = Type.Caption, color = Theme.tokens.textSecondary)
                    if (s.canRaise) SecondaryButton("Fund more", { message = issue(UrbanBudgetPresenter.raiseService(current.city, s)) }, Modifier.fillMaxWidth())
                    if (s.canCut) SecondaryButton("Cut back", { message = issue(UrbanBudgetPresenter.cutService(current.city, s)) }, Modifier.fillMaxWidth())
                }
            }
            if (model.projects.isNotEmpty()) item { SectionHeader("Capital projects") }
            items(model.projects.size) { i ->
                val p = model.projects[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(p.status, p.tone.color())
                    }
                    Text(p.kind + ", " + p.phase + ", " + p.progress + " done", style = Type.Caption, color = Theme.tokens.textSecondary)
                    Text(p.budget, style = Type.Body, color = Theme.tokens.textSecondary)
                    p.note?.let { Text(it, style = Type.Caption, color = Theme.tokens.textSecondary) }
                }
            }
            if (model.concessions.isNotEmpty()) item { SectionHeader("Concessions") }
            items(model.concessions.size) { i ->
                val c = model.concessions[i]
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.name, style = Type.Heading, color = Theme.tokens.textPrimary)
                        Pill(c.status, c.tone.color())
                    }
                    Text(c.kind + ": " + c.terms, style = Type.Caption, color = Theme.tokens.textSecondary)
                    FactList(c.facts)
                }
            }
        }
    }
}
