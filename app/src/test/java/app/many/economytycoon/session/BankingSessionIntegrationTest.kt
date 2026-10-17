package app.many.economytycoon.session

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.ui.model.FoundBankForm
import app.many.economytycoon.ui.model.FormResult
import app.many.economytycoon.ui.nav.Route
import app.many.economytycoon.ui.present.Glossary
import app.many.economytycoon.ui.present.HelpContent
import app.many.economytycoon.ui.present.Tips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The banking screens as the player meets them: through a real session, the routes, the form and the reference texts. */
class BankingSessionIntegrationTest {
    private fun start(): GameSession =
        (NewGame.start(NewGameOptions("Vault Holdings", Difficulty.RELAXED, seed = 6L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false), null) as Outcome.Ok).value

    @Test fun aNewGameHasBanksAndTheOverviewReadsThem() {
        val s = start()
        assertTrue(s.hasBanking)
        val overview = s.bankingOverview()
        assertNotNull(overview)
        assertTrue(overview!!.rivals.size in 3..5)
        assertTrue(overview.mine.isEmpty())
        assertTrue(overview.canFound)
    }

    @Test fun aDetailAdviceAndAStressTestComeFromTheRealBanks() {
        val s = start()
        s.step()
        val id = s.simulation.economy.banking.banks().first().id
        val detail = s.bankDetail(id)
        assertNotNull(detail)
        assertFalse(detail!!.canManage)
        s.bankAdvice(id)
        val result = s.bankStress("adverse")
        assertNotNull(result)
        assertTrue(result!!.rows.isNotEmpty())
        assertNull(s.bankStress("no-such-scenario"))
        assertNull(s.bankDetail(BankId(999_999L)))
    }

    @Test fun foundingThroughTheFormMakesABankThePlayerCanManage() {
        val s = start()
        val company = s.selectedCompany!!
        val country = s.simulation.economy.companies.company(company)!!.country
        val form = FoundBankForm(company, country, "Vault Bank", "300000", Money.units(500_000), Money.units(1_000_000)).validate()
        assertTrue(form is FormResult.Errors)
        val cash = s.simulation.economy.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(company))
        val ready = FoundBankForm(company, country, "Vault Bank", "600000", Money.units(500_000), cash).validate()
        assertTrue(ready is FormResult.Ready)
        assertTrue(s.issueNow((ready as FormResult.Ready).command) is Outcome.Ok)
        val mine = s.bankingOverview()!!.mine
        assertEquals(1, mine.size)
        val detail = s.bankDetail(mine.first().id)!!
        assertTrue(detail.canManage)
        assertTrue(s.issueNow(Command.BankDecision(company, BankAction.SetLoanMargin(mine.first().id, 15L))) is Outcome.Ok)
    }

    @Test fun aYearOfPlayKeepsNewsAlertsAndAdviceWorking() {
        val s = start()
        val company = s.selectedCompany!!
        val country = s.simulation.economy.companies.company(company)!!.country
        s.issueNow(Command.FoundBank(company, "Vault Bank", country, Money.units(600_000)))
        while (s.simulation.tick.value < 400L) s.step()
        assertNotNull(s.reports.alerts.scan(company))
        assertTrue(s.news.items().isNotEmpty())
        val id = s.bankingOverview()!!.mine.first().id
        s.bankAdvice(id).firstOrNull()?.let { s.hideBankAdvice(it.code) }
        assertTrue(Invariants.check(s.simulation.economy).isEmpty())
        assertTrue(s.simulation.economy.finance.invariants().isEmpty())
    }

    @Test fun theRoutesAndReferenceTextsAreWired() {
        for (r in listOf(Route.Banking, Route.BankStress, Route.FoundBank, Route.BankDetail(7))) assertEquals(r, Route.parse(r.key))
        assertTrue(Glossary.search("stress test").any { it.id == "stress-test" })
        assertNotNull(Glossary.byId("cet1"))
        assertNotNull(HelpContent.byId("finance"))
        assertNotNull(Tips.byId("banking"))
        assertTrue(Glossary.terms.flatMap { it.seeAlso }.all { id -> Glossary.terms.any { it.id == id } })
        assertEquals(Glossary.terms.size, Glossary.terms.map { it.id }.toSet().size)
    }
}
