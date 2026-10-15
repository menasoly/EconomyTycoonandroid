package app.many.economytycoon.ui.present

import app.many.economytycoon.advisor.Advisor
import app.many.economytycoon.advisor.AdvisorFacts
import app.many.economytycoon.advisor.AdvisorRules
import app.many.economytycoon.advisor.FacilityFact
import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorPresenterTest {
    @Test fun everySuggestionCodeLeadsSomewhere() {
        val worst = AdvisorFacts(
            tick = 1, cashUnits = 900_000, netWorthUnits = 3_000_000, monthRevenueUnits = 100_000, monthNetIncomeUnits = -1, dailyPayrollUnits = 5_000,
            facilities = List(5) { FacilityFact(it.toLong(), "F$it", true, 0, 100_000, if (it == 0) "x" else null, 10, 500_000) },
            vacancies = 5, averageMoralePpm = 100_000, underpaidCohorts = 2, industryCount = 1, researchIdle = true, idleParcels = 1, contractOffers = 2,
            netMarginPpm = -10_000, currentRatioPpm = 100_000, loanRatePpm = 90_000, debtUnits = 1_000,
        )
        val codes = AdvisorRules.all.flatMap { it.check(worst, app.many.economytycoon.advisor.AdvisorTuning()) }.map { it.code }
        assertTrue(codes.size >= 14)
        for (c in codes) assertTrue(c, AdvisorPresenter.routeFor(c) != null)
        assertNull(AdvisorPresenter.routeFor("unheard-of"))
    }

    @Test fun routesMatchTheTopic() {
        assertEquals(Route.People, AdvisorPresenter.routeFor("understaffed"))
        assertEquals(Route.Property, AdvisorPresenter.routeFor("idle-land"))
        assertEquals(Route.Contracts, AdvisorPresenter.routeFor("contract-offers"))
        assertEquals(Route.Exchange, AdvisorPresenter.routeFor("go-public"))
        assertEquals(Route.Research, AdvisorPresenter.routeFor("idle-research"))
    }

    @Test fun tonesGrowWithPriority() {
        assertEquals(Tone.BAD, AdvisorPresenter.tone(90)); assertEquals(Tone.BAD, AdvisorPresenter.tone(70))
        assertEquals(Tone.WARN, AdvisorPresenter.tone(69)); assertEquals(Tone.WARN, AdvisorPresenter.tone(50))
        assertEquals(Tone.INFO, AdvisorPresenter.tone(49)); assertEquals(Tone.INFO, AdvisorPresenter.tone(35))
        assertEquals(Tone.NEUTRAL, AdvisorPresenter.tone(34))
    }

    @Test fun itemsCarryTheActionLabel() {
        val i = AdvisorPresenter.item(Insight("idle-land", Topic.PROPERTY, 35, "Land idle", "d"))
        assertEquals("Property", i.topic); assertEquals("Open property", i.actionLabel); assertEquals(Route.Property, i.route)
        val none = AdvisorPresenter.item(Insight("mystery", Topic.MARKET, 10, "t", "d"))
        assertNull(none.route); assertNull(none.actionLabel)
    }

    @Test fun headlinesReadByCount() {
        assertEquals("Nothing needs your attention.", AdvisorPresenter.headline(0, null))
        val top = Insight("a", Topic.MARKET, 40, "Do this", "d")
        assertEquals("One suggestion: Do this", AdvisorPresenter.headline(1, top))
        assertEquals("3 suggestions. Most pressing: Do this", AdvisorPresenter.headline(3, top))
    }

    @Test fun hiddenSuggestionsAreCountedNotShown() {
        val facts = AdvisorFacts(tick = 5)
        val adv = Advisor()
        assertEquals(1, AdvisorPresenter.build(adv, facts).items.size)
        adv.hide("no-facilities", 5)
        val m = AdvisorPresenter.build(adv, facts)
        assertTrue(m.items.isEmpty()); assertEquals(1, m.hidden); assertEquals("Nothing needs your attention.", m.headline)
    }

    @Test fun theListIsCappedButTheHeadlineCountsAll() {
        val rules = List(5) { n -> app.many.economytycoon.advisor.AdvisorRule { _, _ -> listOf(Insight("c$n", Topic.MARKET, 40 + n, "T$n", "d")) } }
        val m = AdvisorPresenter.build(Advisor(rules), AdvisorFacts(), limit = 2)
        assertEquals(listOf("c4", "c3"), m.items.map { it.code })
        assertTrue(m.headline.startsWith("5 suggestions"))
    }

    @Test fun aSessionGetsAModel() {
        val s = PresentSupport.session("Advice Co")
        val m = AdvisorPresenter.build(s)!!
        assertEquals("no-facilities", m.items.first().code)
        assertEquals(Topic.values().map { it.label }, AdvisorPresenter.topics())
    }
}
