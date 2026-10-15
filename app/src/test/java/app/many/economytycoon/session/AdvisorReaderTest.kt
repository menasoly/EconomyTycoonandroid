package app.many.economytycoon.session

import app.many.economytycoon.advisor.Advisor
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorReaderTest {
    private fun session(): GameSession =
        (NewGame.start(NewGameOptions("Advised Co", Difficulty.RELAXED, seed = 5L), SimTestSupport.content, GameSettings(autosave = false, pauseOnCritical = false)) as Outcome.Ok).value

    @Test fun aFreshCompanyHasCashAndNoFacilities() {
        val s = session()
        val f = s.adviceFacts()!!
        assertTrue(f.cashUnits > 0L && f.facilities.isEmpty())
        assertEquals(s.tick.value, f.tick)
        assertEquals(0L, f.debtUnits)
        assertFalse(f.listed)
        assertEquals(0, f.idleParcels)
    }

    @Test fun theFirstAdviceIsToBuild() {
        val s = session()
        val top = s.advisor.advise(s.adviceFacts()!!).first()
        assertEquals("no-facilities", top.code)
    }

    @Test fun anUnknownCompanyReadsAsNothing() {
        val s = session()
        assertNull(AdvisorReader(s.economy, s.reports).read(app.many.economytycoon.core.CompanyId(424_242L)))
        assertNotNull(AdvisorReader(s.economy, s.reports).read(s.playerCompanies().first()))
    }

    @Test fun ownedLandThatIsNotUsedCountsAsIdle() {
        val s = session()
        val e = s.economy
        val me = s.playerCompanies().first()
        val cheapest = e.world.land.all().filter { it.forSale && it.city != null }.minByOrNull { e.propertyRunner.appraise(it).raw }!!
        val owner = e.companies.require(me).owner
        e.finance.mint(e.tick, owner, Money.units(5_000_000), "land fund")
        assertTrue(s.issueNow(app.many.economytycoon.simulation.Command.BuyParcel(me, cheapest.id)) is Outcome.Ok)
        assertEquals(1, s.adviceFacts()!!.idleParcels)
        assertTrue("idle-land" in s.advisor.advise(s.adviceFacts()!!).map { it.code })
    }

    @Test fun hidingAdviceNotifiesListenersAndSticks() {
        val s = session()
        var changes = 0
        s.addListener { _, c -> if (c == SessionChange.ADVICE) changes++ }
        s.hideAdvice("no-facilities")
        assertEquals(1, changes)
        assertFalse("no-facilities" in s.advisor.advise(s.adviceFacts()!!).map { it.code })
        s.showHiddenAdvice()
        assertEquals(2, changes)
        assertTrue("no-facilities" in s.advisor.advise(s.adviceFacts()!!).map { it.code })
    }

    @Test fun hiddenAdviceReturnsWhenTheTimeIsUp() {
        val s = session()
        s.hideAdvice("no-facilities", days = 3)
        repeat(4) { s.step() }
        assertTrue("no-facilities" in s.advisor.advise(s.adviceFacts()!!).map { it.code })
        assertTrue(Advisor.DEFAULT_HIDE_DAYS > 3)
    }
}
