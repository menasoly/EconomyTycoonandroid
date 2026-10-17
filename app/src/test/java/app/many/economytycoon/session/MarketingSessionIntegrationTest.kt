package app.many.economytycoon.session

import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.marketing.Channel
import app.many.economytycoon.marketing.CreativeTier
import app.many.economytycoon.marketing.TargetSegment
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.AdvisorPresenter
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.SessionMarketingHost
import app.many.economytycoon.ui.present.Tips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Marketing as the player meets it: through the session, the screen host, the route and the reference texts. */
class MarketingSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("Ad Works", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    private fun book(s: GameSession) = s.issueNow(
        Command.CreateCampaign(
            s.selectedCompany!!, GoodId("bread"), s.economy.world.regions().sortedBy { it.id }.first().id, null,
            Channel.RADIO, TargetSegment.EVERYONE, Money.units(300), 20, CreativeTier.PROFESSIONAL, null,
        ),
    )

    @Test fun theOverviewStartsEmptyAndShowsABookedCampaign() {
        val s = start()
        assertTrue(s.marketingOverview(s.selectedCompany!!).campaigns.isEmpty())
        assertTrue(book(s) is Outcome.Ok)
        assertEquals(1, s.marketingOverview(s.selectedCompany!!).campaigns.size)
    }

    @Test fun theScreenHostBuildsAModelAndRunsActions() {
        val s = start()
        book(s)
        val host = SessionMarketingHost(s)
        val model = host.build()
        assertNotNull(model)
        assertEquals(1, model!!.list.live.size)
        assertTrue(host.applyPlan().isNotBlank())
    }

    @Test fun aPlayerCampaignRunsThroughTheClockAndLeavesNoBrokenInvariant() {
        val s = start()
        book(s)
        repeat(40) { s.simulation.advance() }
        assertTrue(app.many.economytycoon.simulation.Invariants.check(s.economy).isEmpty())
    }

    @Test fun theRouteAdviceAndReferenceTextsAreWired() {
        assertEquals(Route.Marketing, Route.parse(Route.Marketing.key))
        assertEquals(Route.Marketing, AdvisorPresenter.routeFor("mkt-wear"))
        assertNotNull(Glossary.byId("awareness"))
        assertNotNull(HelpContent.byId("marketing"))
        assertNotNull(Tips.byId("marketing"))
    }
}
