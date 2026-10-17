package app.many.economytycoon.ui.compose.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.ui.compose.components.BottomTabs
import app.many.economytycoon.ui.compose.components.ClockBar
import app.many.economytycoon.ui.compose.components.ConfirmDialog
import app.many.economytycoon.ui.compose.components.MessageBar
import app.many.economytycoon.ui.compose.components.ProblemsDialog
import app.many.economytycoon.ui.compose.screens.AlertsScreen
import app.many.economytycoon.ui.compose.screens.AnalysisScreen
import app.many.economytycoon.ui.compose.screens.CreditScreen
import app.many.economytycoon.ui.compose.screens.OutlookScreen
import app.many.economytycoon.ui.compose.screens.BuildScreen
import app.many.economytycoon.ui.compose.screens.ExchangeScreen
import app.many.economytycoon.ui.compose.screens.ActivityScreen
import app.many.economytycoon.ui.compose.screens.AgendaScreen
import app.many.economytycoon.ui.compose.screens.ExportScreen
import app.many.economytycoon.ui.compose.screens.GlossaryScreen
import app.many.economytycoon.ui.compose.screens.EnergyGridScreen
import app.many.economytycoon.ui.compose.screens.GrantsScreen
import app.many.economytycoon.ui.compose.screens.MarketingScreen
import app.many.economytycoon.ui.compose.screens.GuideScreen
import app.many.economytycoon.ui.compose.screens.GroupScreen
import app.many.economytycoon.ui.compose.screens.SectorsScreen
import app.many.economytycoon.ui.compose.screens.PlannerScreen
import app.many.economytycoon.ui.compose.screens.TradeScreen
import app.many.economytycoon.ui.compose.screens.ManagersScreen
import app.many.economytycoon.ui.compose.screens.BlueprintsScreen
import app.many.economytycoon.ui.compose.screens.WatchScreen
import app.many.economytycoon.ui.compose.screens.ReportsScreen
import app.many.economytycoon.ui.compose.screens.InsuranceScreen
import app.many.economytycoon.ui.compose.screens.StockScreen
import app.many.economytycoon.ui.compose.screens.TendersScreen
import app.many.economytycoon.ui.compose.screens.FuturesScreen
import app.many.economytycoon.ui.compose.screens.PropertyScreen
import app.many.economytycoon.ui.compose.screens.AchievementsScreen
import app.many.economytycoon.ui.compose.screens.AdvisorScreen
import app.many.economytycoon.ui.compose.screens.ScenarioProgressScreen
import app.many.economytycoon.ui.compose.screens.UnionsScreen
import app.many.economytycoon.ui.compose.screens.BondsScreen
import app.many.economytycoon.ui.compose.screens.GovernmentScreen
import app.many.economytycoon.ui.compose.screens.FacilitiesScreen
import app.many.economytycoon.ui.compose.screens.FacilityDetailScreen
import app.many.economytycoon.ui.compose.screens.FinanceScreen
import app.many.economytycoon.ui.compose.screens.GoalsScreen
import app.many.economytycoon.ui.compose.screens.GoodScreen
import app.many.economytycoon.ui.compose.screens.HelpScreen
import app.many.economytycoon.ui.compose.screens.LoansScreen
import app.many.economytycoon.ui.compose.screens.LogisticsScreen
import app.many.economytycoon.ui.compose.screens.MarketScreen
import app.many.economytycoon.ui.compose.screens.MoreMenuScreen
import app.many.economytycoon.ui.compose.screens.NewsScreen
import app.many.economytycoon.ui.compose.screens.OrdersScreen
import app.many.economytycoon.ui.compose.screens.OverviewScreen
import app.many.economytycoon.ui.compose.screens.OwnershipScreen
import app.many.economytycoon.ui.compose.screens.PeopleScreen
import app.many.economytycoon.ui.compose.screens.RegionScreen
import app.many.economytycoon.ui.compose.screens.ResearchScreen
import app.many.economytycoon.ui.compose.screens.RivalScreen
import app.many.economytycoon.ui.compose.screens.RivalsScreen
import app.many.economytycoon.ui.compose.screens.ContractsScreen
import app.many.economytycoon.ui.compose.screens.RulesScreen
import app.many.economytycoon.ui.compose.screens.SavesScreen
import app.many.economytycoon.ui.compose.screens.SettingsScreen
import app.many.economytycoon.ui.compose.screens.FacilityProfitScreen
import app.many.economytycoon.ui.compose.screens.CashForecastScreen
import app.many.economytycoon.ui.compose.screens.StatementsScreen
import app.many.economytycoon.ui.compose.screens.TutorialScreen
import app.many.economytycoon.ui.compose.screens.ValuationScreen
import app.many.economytycoon.ui.compose.screens.WorldScreen
import app.many.economytycoon.ui.nav.Route

/** The running game: clock bar, current screen, message line and bottom tabs. */
@Composable
fun GameShell(host: AppHost, session: GameSession) {
    val ui = remember(session) { GameUi(session) }
    DisposableEffect(ui) { onDispose { ui.dispose() } }
    GameLoop(ui)

    var confirmQuit by remember { mutableStateOf(false) }
    BackHandler(enabled = true) { if (!ui.back()) confirmQuit = true }

    val alerts = remember(ui.revision / 5) { session.criticalAlerts().size }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ClockBar(session, app.many.economytycoon.session.Formatting.date(session.tick)) { session.setSpeed(it) }
        Box(Modifier.weight(1f)) {
            if (session.isGameOver()) GameOver(host) else Router(ui, host)
        }
        MessageBar(ui.message, { ui.showMessage(null) })
        BottomTabs(ui.nav.tab, { ui.select(it) }, badge = { tab -> if (tab == app.many.economytycoon.ui.nav.Tab.OVERVIEW) alerts else 0 })
    }
    if (ui.problems.isNotEmpty()) ProblemsDialog("Something went wrong", ui.problems, ui::dismissProblems)
    if (confirmQuit) ConfirmDialog("Leave this game?", "Your progress is saved first.", "Save and quit", { confirmQuit = false; host.quitToMenu(true) }, { confirmQuit = false })
}

/** Runs the simulation clock from display frames. Frames stop in the background, so no time passes there. */
@Composable
private fun GameLoop(ui: GameUi) {
    LaunchedEffect(ui) {
        var last = androidx.compose.runtime.withFrameMillis { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameMillis { it }
            ui.session.advanceBy(now - last)
            last = now
        }
    }
}

@Composable
private fun GameOver(host: AppHost) {
    app.many.economytycoon.ui.compose.components.EmptyState(
        "Game over", "Your last company has closed. You can start again from the main menu.",
        action = "Main menu", onAction = { host.quitToMenu(false) },
    )
}

@Composable
private fun Router(ui: GameUi, host: AppHost) {
    when (val r = ui.nav.current) {
        Route.Overview -> OverviewScreen(ui)
        Route.Goals -> GoalsScreen(ui)
        Route.Guide -> GuideScreen(ui)
        Route.Advisor -> AdvisorScreen(ui)
        Route.News -> NewsScreen(ui)
        Route.Alerts -> AlertsScreen(ui)
        Route.Facilities -> FacilitiesScreen(ui)
        is Route.FacilityDetail -> FacilityDetailScreen(ui, r.facility)
        is Route.Build -> BuildScreen(ui, r.region)
        Route.People -> PeopleScreen(ui)
        Route.Unions -> UnionsScreen(ui)
        Route.Research -> ResearchScreen(ui)
        Route.Logistics -> LogisticsScreen(ui)
        Route.Market -> MarketScreen(ui)
        is Route.Good -> GoodScreen(ui, r.good)
        Route.Orders -> OrdersScreen(ui)
        Route.Rules -> RulesScreen(ui)
        Route.Contracts -> ContractsScreen(ui)
        Route.Tenders -> TendersScreen(ui)
        Route.Futures -> FuturesScreen(ui)
        Route.Marketing -> MarketingScreen(ui, remember(ui.session) { app.many.economytycoon.ui.present.SessionMarketingHost(ui.session) })
        Route.Property -> PropertyScreen(ui)
        Route.Scenario -> ScenarioProgressScreen(ui)
        Route.Achievements -> AchievementsScreen(ui)
        Route.Exchange -> ExchangeScreen(ui)
        Route.Insurance -> InsuranceScreen(ui)
        Route.Bonds -> BondsScreen(ui)
        is Route.Stock -> StockScreen(ui, r.company)
        Route.Finance -> FinanceScreen(ui)
        Route.Loans -> LoansScreen(ui)
        Route.Analysis -> AnalysisScreen(ui)
        Route.Credit -> CreditScreen(ui)
        Route.Outlook -> OutlookScreen(ui)
        Route.Grants -> GrantsScreen(ui)
        Route.Activity -> ActivityScreen(ui)
        Route.Agenda -> AgendaScreen(ui)
        Route.Export -> ExportScreen(ui)
        Route.Glossary -> GlossaryScreen(ui)
        Route.Group -> GroupScreen(ui)
        Route.Sectors -> SectorsScreen(ui)
        Route.Trade -> TradeScreen(ui)
        Route.Watch -> WatchScreen(ui)
        Route.Blueprints -> BlueprintsScreen(ui)
        Route.Managers -> ManagersScreen(ui)
        Route.Planner -> PlannerScreen(ui)
        Route.Reports -> ReportsScreen(ui)
        Route.Statements -> StatementsScreen(ui)
        Route.CashForecast -> CashForecastScreen(ui)
        Route.FacilityProfit -> FacilityProfitScreen(ui)
        Route.Tutorial -> TutorialScreen(ui)
        Route.Valuation -> ValuationScreen(ui)
        Route.Ownership -> OwnershipScreen(ui)
        Route.MoreMenu -> MoreMenuScreen(ui) { host.quitToMenu(true) }
        Route.World -> WorldScreen(ui)
        is Route.Region -> RegionScreen(ui, r.region)
        Route.Government -> GovernmentScreen(ui)
        Route.Electricity -> EnergyGridScreen(ui, ui.session.energyGrids, ui.session.tick.value)
        Route.Rivals -> RivalsScreen(ui)
        is Route.Rival -> RivalScreen(ui, r.company)
        Route.Saves -> SavesScreen(ui, host)
        Route.Settings -> SettingsScreen(ui, host)
        Route.Help -> HelpScreen(ui)
    }
}
