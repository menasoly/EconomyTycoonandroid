package app.many.economytycoon.ui

import app.many.economytycoon.ui.nav.NavState
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.nav.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStateTest {
    @Test fun startsOnTheOverviewRoot() {
        val n = NavState()
        assertEquals(Tab.OVERVIEW, n.tab)
        assertEquals(Route.Overview, n.current)
        assertFalse(n.canGoUpInTab)
    }

    @Test fun openingARouteSwitchesToItsTabAndStacksIt() {
        val n = NavState().open(Route.FacilityDetail(7))
        assertEquals(Tab.OPERATIONS, n.tab)
        assertEquals(listOf<Route>(Route.Facilities, Route.FacilityDetail(7)), n.stack)
    }

    @Test fun reopeningTheTopRouteDoesNotRepeatIt() {
        val n = NavState().open(Route.Good("wheat")).open(Route.Good("wheat"))
        assertEquals(2, n.stack.size)
    }

    @Test fun backWalksUpThenToOverviewThenLeaves() {
        var n: NavState? = NavState().open(Route.Build(2)).open(Route.FacilityDetail(1))
        n = n!!.back(); assertEquals(Route.Build(2), n!!.current)
        n = n.back(); assertEquals(Route.Facilities, n!!.current)
        n = n.back(); assertEquals(Tab.OVERVIEW, n!!.tab)
        assertNull(n.back())
    }

    @Test fun tabsKeepTheirOwnHistory() {
        val n = NavState().open(Route.FacilityDetail(1)).select(Tab.MARKET).open(Route.Good("iron_ore")).select(Tab.OPERATIONS)
        assertEquals(Route.FacilityDetail(1), n.current)
        assertEquals(Route.Good("iron_ore"), n.select(Tab.MARKET).current)
    }

    @Test fun selectingTheCurrentTabReturnsToItsRoot() {
        val n = NavState().open(Route.Loans).select(Tab.FINANCE)
        assertEquals(Route.Finance, n.current)
        assertEquals(1, n.stack.size)
    }

    @Test fun popToStopsAtTheNamedScreen() {
        val n = NavState().open(Route.Build(1)).open(Route.FacilityDetail(5))
        assertEquals(Route.Build(1), n.popTo(Route.Build(1)).current)
        assertEquals(n, n.popTo(Route.Research))
    }

    @Test fun replaceTopSwapsWithoutGrowingTheStack() {
        val n = NavState().open(Route.Build(1)).replaceTop(Route.FacilityDetail(9))
        assertEquals(listOf<Route>(Route.Facilities, Route.FacilityDetail(9)), n.stack)
    }

    @Test fun pruneRemovesScreensForThingsThatNoLongerExist() {
        val n = NavState().open(Route.FacilityDetail(3)).open(Route.FacilityDetail(4)).prune { it != Route.FacilityDetail(3) }
        assertEquals(listOf<Route>(Route.Facilities, Route.FacilityDetail(4)).filter { it != Route.FacilityDetail(3) }, n.stack)
        assertEquals(Route.Facilities, NavState().open(Route.FacilityDetail(3)).prune { false }.current)
    }

    @Test fun stateSurvivesEncodingAndDecoding() {
        val n = NavState().open(Route.FacilityDetail(12)).select(Tab.MARKET).open(Route.Good("steel")).open(Route.Orders)
        val back = NavState.decode(n.encode())
        assertEquals(n, back)
        assertEquals(Route.Orders, back.current)
    }

    @Test fun brokenTextDecodesToAFreshState() {
        for (bad in listOf(null, "", "nonsense", "OVERVIEW#only|two", "MARKET#a|b|c|d|e", "OPERATIONS#overview|facilities|market|finance|more")) {
            val n = NavState.decode(bad)
            assertEquals(Route.root(n.tab), n.stack.first())
        }
        assertEquals(NavState(), NavState.decode("nonsense"))
    }

    @Test fun everyRouteKeyParsesBackToItself() {
        val routes = listOf<Route>(
            Route.Overview, Route.Goals, Route.Guide, Route.Advisor, Route.News, Route.Alerts, Route.Facilities, Route.FacilityDetail(5), Route.Build(3), Route.People,
            Route.Research, Route.Logistics, Route.Market, Route.Good("iron_ore"), Route.Orders, Route.Rules, Route.Contracts, Route.Tenders, Route.Government, Route.Exchange, Route.Stock(7), Route.Insurance, Route.Bonds, Route.Unions, Route.Futures, Route.Property, Route.Finance, Route.Loans,
            Route.Statements, Route.Tutorial, Route.Valuation, Route.Analysis, Route.Credit, Route.Outlook, Route.Grants, Route.Activity, Route.Agenda, Route.Export, Route.Glossary, Route.Group, Route.Sectors, Route.Trade, Route.Planner, Route.Reports, Route.Ownership, Route.MoreMenu, Route.World, Route.Region(2), Route.Rivals, Route.Rival(8),
            Route.Scenario, Route.Achievements, Route.Saves, Route.Settings, Route.Help,
        )
        for (r in routes) assertEquals(r, Route.parse(r.key))
        assertEquals(routes.size, routes.map { it.key }.toSet().size)
    }

    @Test fun malformedKeysAreNotRoutes() {
        for (k in listOf("", "facility", "facility/x", "good", "region/", "nope/1")) assertNull(Route.parse(k))
        assertTrue(Route.parse("facility/12") is Route.FacilityDetail)
    }
}
