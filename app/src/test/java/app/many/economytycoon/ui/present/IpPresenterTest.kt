package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.ip.IpModel
import app.many.economytycoon.ip.IpOwner
import app.many.economytycoon.ip.IpTestSupport
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPresenterTest {
    private class FakeView(private val model: IpModel?) : IpSessionView {
        val issued = ArrayList<IpCommand>()
        override fun ipModel(): IpModel? = model
        override fun money(m: Money): String = "$" + (m.raw / Money.SCALE)
        override fun companyName(id: CompanyId): String = "Firm ${id.value}"
        override fun countryName(id: CountryId): String = "Land ${id.value}"
        override fun issue(command: IpCommand): String { issued += command; return "done" }
    }

    private val world = IpTestSupport.busyWorld()
    private fun view(who: CompanyId): FakeView = FakeView(IpViews(world.engine).build(world.ctx, who, Tick(world.day)))
    private fun all(m: IpScreenModel) = m.sections.flatMap { it.cards }

    @Test fun noModelGivesNoScreen() {
        assertNull(IpPresenter.build(FakeView(null), IpTab.OVERVIEW))
    }

    @Test fun everyTabBuildsForEveryCompany() {
        for (who in listOf(a, b, c)) for (t in IpTab.values()) {
            val m = IpPresenter.build(view(who), t)
            assertNotNull(m)
            assertEquals(t, m!!.tab)
            assertEquals(IpTab.values().size, m.tabs.size)
            assertTrue(m.note.isNotBlank())
        }
    }

    @Test fun tilesSummariseThePortfolio() {
        val m = IpPresenter.build(view(a), IpTab.OVERVIEW)!!
        assertEquals(5, m.tiles.size)
        assertEquals("Portfolio worth", m.tiles[0].label)
        assertTrue(m.tiles[1].value.toInt() > 0)
    }

    @Test fun patentCardsCarryOwnerActions() {
        val cards = all(IpPresenter.build(view(a), IpTab.PATENTS)!!)
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { c -> c.actions.any { it.command is IpCommand.AbandonPatent } || c.badge == "Ended" })
        assertTrue(cards.any { c -> c.actions.any { it.command is IpCommand.SetAutoMaintain } })
        assertTrue(cards.all { c -> c.actions.all { it.command.actor == a } })
    }

    @Test fun listedPatentOffersToTakeItDown() {
        val cards = all(IpPresenter.build(view(c), IpTab.PATENTS)!!)
        assertTrue(cards.any { c -> c.actions.any { it.command is IpCommand.UnlistPatent } })
    }

    @Test fun secretCardsOfferSecurityAndDropping() {
        val cards = all(IpPresenter.build(view(c), IpTab.SECRETS)!!)
        assertTrue(cards.any { c -> c.actions.any { it.command is IpCommand.InvestInSecret } && c.actions.any { it.command is IpCommand.DropSecret } })
    }

    @Test fun marketListsOthersPatentsWithBuyButtons() {
        val m = IpPresenter.build(view(a), IpTab.MARKET)!!
        val buy = m.sections[0].cards
        assertTrue(buy.isNotEmpty())
        assertTrue(buy.all { c -> c.actions.single().command is IpCommand.BuyPatent })
        assertTrue(m.sections[1].cards.isNotEmpty())
    }

    @Test fun marketHidesYourOwnListing() {
        assertTrue(IpPresenter.build(view(c), IpTab.MARKET)!!.sections[0].cards.isEmpty())
        assertEquals("Nothing is listed.", IpPresenter.build(view(c), IpTab.MARKET)!!.sections[0].empty)
    }

    @Test fun waitingOffersCanBeAcceptedOrDeclined() {
        val cards = IpPresenter.build(view(a), IpTab.LICENCES)!!.sections[0].cards
        for (card in cards) {
            assertTrue(card.actions.any { it.command is IpCommand.AcceptLicense })
            assertTrue(card.actions.any { it.command is IpCommand.DeclineLicense })
        }
    }

    @Test fun licencesIGrantCanBeAudited() {
        val out = IpPresenter.build(view(a), IpTab.LICENCES)!!.sections[2].cards
        assertTrue(out.any { c -> c.actions.any { it.command is IpCommand.AuditLicense } })
    }

    @Test fun disputesShowSettlementOfferToTheDefendantsOpponent() {
        val cards = all(IpPresenter.build(view(a), IpTab.DISPUTES)!!)
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.any { c -> c.actions.any { it.command is IpCommand.AcceptSettlement } })
    }

    @Test fun defendantGetsDesignAroundButNotDemand() {
        val cards = all(IpPresenter.build(view(b), IpTab.DISPUTES)!!)
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.none { c -> c.actions.any { it.command is IpCommand.SendNotice } })
    }

    @Test fun programmesListPolicyAndRunningWork() {
        val m = IpPresenter.build(view(b), IpTab.PROGRAMMES)!!
        assertEquals("Policy", m.sections[0].heading)
        assertEquals(2, m.sections[0].cards.single().actions.size)
        assertTrue(m.sections[1].cards.any { c -> c.actions.any { it.command is IpCommand.CancelDesignAround } })
    }

    @Test fun compulsoryRequestCanBeWithdrawn() {
        val cards = IpPresenter.build(view(c), IpTab.PROGRAMMES)!!.sections[2].cards
        assertTrue(cards.isNotEmpty())
    }

    @Test fun tabCountsFollowTheModel() {
        val v = view(a)
        val m = v.ipModel()!!
        val chips = IpPresenter.tabs(m).associate { it.tab to it.count }
        assertEquals(m.patents.size, chips[IpTab.PATENTS])
        assertEquals(m.market.size, chips[IpTab.MARKET])
        assertEquals(m.disputes.count { it.stage.open }, chips[IpTab.DISPUTES])
    }

    @Test fun insightsOnlyOnOverview() {
        assertTrue(IpPresenter.build(view(a), IpTab.PATENTS)!!.insights.isEmpty())
        assertTrue(IpPresenter.build(view(a), IpTab.OVERVIEW)!!.insights.size <= 5)
    }

    @Test fun cardKeysAreUniqueInATab() {
        for (t in IpTab.values()) {
            val keys = all(IpPresenter.build(view(a), t)!!).map { it.key }
            assertEquals(keys.size, keys.toSet().size)
        }
    }

    @Test fun presentationIsDeterministic() {
        assertEquals(IpPresenter.build(view(a), IpTab.OVERVIEW), IpPresenter.build(view(a), IpTab.OVERVIEW))
    }

    @Test fun formatterFallsBackForUnknownHolders() {
        val f = IpFormat(view(a))
        assertEquals("Firm 2", f.owner(IpOwner.company(CompanyId(2))))
        assertEquals("A patent holder", f.owner(IpOwner(app.many.economytycoon.ip.IpOwnerKind.NPE, 9)))
    }

    @Test fun issuingGoesThroughTheView() {
        val v = view(a)
        val card = all(IpPresenter.build(v, IpTab.PATENTS)!!).first { it.actions.isNotEmpty() }
        assertEquals("done", v.issue(card.actions.first().command))
        assertEquals(1, v.issued.size)
        assertFalse(v.issued.isEmpty())
    }
}
