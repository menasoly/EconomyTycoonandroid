package app.many.economytycoon.ui.present

import app.many.economytycoon.competitors.Archetype
import app.many.economytycoon.companies.OfferState
import app.many.economytycoon.persistence.SaveSlot
import app.many.economytycoon.persistence.SlotKind
import app.many.economytycoon.session.Alert
import app.many.economytycoon.session.NewsKind
import app.many.economytycoon.session.Objectives
import app.many.economytycoon.session.Severity
import app.many.economytycoon.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedAndRivalsPresenterTest {
    private val session by lazy { PresentSupport.session("Chronicle Works").also { s -> repeat(50) { s.step() } } }

    @Test fun alertsRouteToTheScreenTheyAreAbout() {
        fun r(code: String, subject: Long = 0) = FeedPresenter.routeFor(Alert(code, subject, Severity.INFO, "t", "d"))
        assertEquals(Route.FacilityDetail(7), r("facility.condition", 7))
        assertEquals(Route.Facilities, r("facility.shutdown", 0))
        assertEquals(Route.Finance, r("cash.runway")); assertEquals(Route.Finance, r("loan.missed", 2)); assertEquals(Route.Finance, r("distress")); assertEquals(Route.Finance, r("insolvency"))
        assertEquals(Route.People, r("strike")); assertEquals(Route.People, r("vacancies")); assertEquals(Route.People, r("wages.unpaid"))
        assertEquals(Route.Research, r("research.idle")); assertEquals(Route.Rivals, r("offer.takeover", 3))
        assertNull(r("something.else"))
        assertEquals(Route.Insurance, r("insurance.none", 4)); assertEquals(Route.Insurance, r("insurance.lapsed", 2))
        assertEquals(Route.Tenders, r("tender.late", 5)); assertEquals(Route.Exchange, r("portfolio.loss", 5)); assertEquals(Route.Contracts, r("contract.offer", 1))
        assertEquals(Route.Property, r("property.rent", 3)); assertEquals(Route.Property, r("property.lease", 3)); assertEquals(Route.Property, r("property.listing", 3)); assertEquals(Route.Futures, r("futures.call", 3)); assertEquals(Route.Futures, r("futures.expiring", 3)); assertEquals(Route.Unions, r("union.demand", 3)); assertEquals(Route.Unions, r("union.strike", 3)); assertEquals(Route.Bonds, r("bond.open", 2)); assertEquals(Route.Bonds, r("bond.matures", 2)); assertEquals(Route.Government, r("law.vote", 4))
        assertEquals(Route.FacilityDetail(9), r("fine.issued", 9)); assertEquals(Route.Facilities, r("fine.closed", 0))
    }

    @Test fun newsIsNewestFirstAndFilterable() {
        val all = FeedPresenter.news(session, limit = 500)
        val market = FeedPresenter.news(session, NewsKind.MARKET, 500)
        assertTrue(market.all { it.kind == NewsKind.MARKET })
        assertTrue(market.size <= all.size)
        assertTrue(FeedPresenter.news(session, limit = 3).size <= 3)
    }

    @Test fun goalsAreGroupedAndCounted() {
        val groups = FeedPresenter.goals(session)
        assertEquals(Objectives.groups(), groups.map { it.name })
        assertEquals(Objectives.all.size, groups.sumOf { it.goals.size })
        for (g in groups) { assertEquals(g.goals.count { it.done }, g.doneCount); assertTrue(g.goals.all { it.progress in 0.0..1.0 }) }
    }

    @Test fun alertItemsCarryTonesFromSeverity() {
        for (a in FeedPresenter.alerts(session)) assertEquals(OverviewPresenter.severityTone(a.severity), a.tone)
    }

    @Test fun rivalsExcludeThePlayerAndSortByNetWorth() {
        val m = RivalsPresenter.build(session)
        assertTrue(m.rivals.isNotEmpty())
        assertFalse(m.rivals.any { it.name == "Chronicle Works" })
        val worths = session.reports.rivals.rivals(session.selectedCompany).map { it.headline.netWorth.raw }
        assertEquals(worths.sortedDescending(), worths)
        assertTrue(m.incoming.isEmpty() && m.outgoing.isEmpty())
    }

    @Test fun rivalDetailHasFactsAndIndustries() {
        val first = RivalsPresenter.build(session).rivals.first()
        val d = RivalsPresenter.detail(session, first.id)!!
        assertEquals(7, d.facts.size)
        assertEquals(first.name, d.item.name)
        assertNull(RivalsPresenter.detail(session, session.selectedCompany!!))
    }

    @Test fun labelsCoverArchetypesAndOfferStates() {
        for (a in Archetype.values()) assertTrue(RivalsPresenter.archetypeLabel(a).isNotBlank())
        assertEquals("Unknown style", RivalsPresenter.archetypeLabel(null))
        assertEquals("Pending", RivalsPresenter.offerState(OfferState.PENDING))
    }

    @Test fun ownershipOfAFreshCompanyIsEmpty() {
        val m = OwnershipPresenter.build(session)!!
        assertTrue(m.holdings.isEmpty())
        assertNull(m.controller)
    }

    @Test fun saveListShowsNewestFirstWithBadges() {
        val slots = listOf(
            SaveSlot("a", "Old run", SlotKind.MANUAL, 100, 360, 5, 2_048),
            SaveSlot("b", "Auto", SlotKind.AUTO, 300, 400, 5, 1_500_000),
            SaveSlot("c", "", SlotKind.MANUAL, 200, 10, 5, 12, damaged = true, hasBackup = true),
            SaveSlot("d", "Broken", SlotKind.MANUAL, 50, 10, 5, 12, damaged = true),
        )
        val items = SavesPresenter.items(slots)
        assertEquals(listOf("b", "c", "a", "d"), items.map { it.id })
        assertEquals("Autosave", items[0].badge); assertFalse(items[0].canRename)
        assertEquals("Damaged, backup available", items[1].badge); assertTrue(items[1].canLoad); assertEquals("c", items[1].title)
        assertTrue(items[2].canRename && items[2].badge == null)
        assertFalse(items[3].canLoad); assertEquals(Tone.BAD, items[3].tone)
    }

    @Test fun sizesReadNaturally() {
        assertEquals("12 B", SavesPresenter.size(12)); assertEquals("2 KB", SavesPresenter.size(2_048)); assertEquals("1.4 MB", SavesPresenter.size(1_500_000))
    }
}
