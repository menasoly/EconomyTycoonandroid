package app.many.economytycoon.energygrid

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridCommandsTest {
    private val stranger = CompanyId(999)

    private fun exec(w: GridWorld, c: GridCommand, tick: Long = 0L) = GridCommandExecutor(w.service).execute(c, Tick(tick))

    @Test fun aCompanyConnectsAndDisconnectsItsFacility() {
        val w = GridWorld()
        val c = exec(w, GridCommand.ConnectFacility(w.buyer, w.grid.id, FacilityId(77), CustomerClass.COMMERCIAL, 5_000L))
        assertTrue(c.ok)
        assertEquals(5_000L, w.grid.connectedBaseKw())
        assertTrue(exec(w, GridCommand.DisconnectFacility(w.buyer, FacilityId(77))).ok)
        assertEquals(0L, w.grid.connectedBaseKw())
    }

    @Test fun nobodyDisconnectsSomeoneElsesFacility() {
        val w = GridWorld()
        exec(w, GridCommand.ConnectFacility(w.buyer, w.grid.id, FacilityId(77), CustomerClass.COMMERCIAL, 5_000L))
        val r = exec(w, GridCommand.DisconnectFacility(stranger, FacilityId(77)))
        assertFalse(r.ok)
        assertEquals(Rules.NOT_OWNER, r.rejection?.rule)
        assertEquals(5_000L, w.grid.connectedBaseKw())
    }

    @Test fun anUnknownFacilityIsRefused() {
        val w = GridWorld()
        assertEquals(Rules.UNKNOWN_ENTITY, exec(w, GridCommand.DisconnectFacility(w.buyer, FacilityId(5))).rejection?.rule)
    }

    @Test fun theOwnerChangesClassAndDraw() {
        val w = GridWorld()
        val l = w.facility(10_000L)
        assertTrue(exec(w, GridCommand.Reclassify(w.buyer, l.id, CustomerClass.INTERRUPTIBLE)).ok)
        assertEquals(CustomerClass.INTERRUPTIBLE, w.grid.load(l.id)?.customer)
        assertTrue(exec(w, GridCommand.SetDraw(w.buyer, l.id, 12_000L)).ok)
        assertEquals(12_000L, w.grid.load(l.id)?.baseKw)
        assertEquals(Rules.NOT_OWNER, exec(w, GridCommand.SetDraw(stranger, l.id, 1L)).rejection?.rule)
        assertEquals(Rules.NOT_OWNER, exec(w, GridCommand.Reclassify(stranger, l.id, CustomerClass.COMMERCIAL)).rejection?.rule)
    }

    @Test fun orderingAUnitReturnsThePaymentToPost() {
        val w = GridWorld()
        val r = exec(w, GridCommand.OrderUnit(w.owner, w.grid.id, Technology.GAS, 50_000L))
        assertTrue(r.ok)
        assertEquals(1, r.intents.size)
        assertTrue(r.intents.single().total.raw > 0L)
        assertEquals(1, w.grid.units.count { it.status == UnitStatus.BUILDING })
    }

    @Test fun unbuildableTechnologyIsRefusedBeforeTheService() {
        val w = GridWorld()
        val r = exec(w, GridCommand.OrderUnit(w.owner, w.grid.id, Technology.HYDRO, 50_000L))
        assertEquals(Rules.NOT_UNLOCKED, r.rejection?.rule)
        assertTrue(w.grid.units.isEmpty())
    }

    @Test fun onlyTheOwnerRunsAUnit() {
        val w = GridWorld()
        val u = w.unit(Technology.GAS, 50_000L, owner = w.owner)
        assertEquals(Rules.NOT_OWNER, exec(w, GridCommand.Mothball(stranger, u.id)).rejection?.rule)
        assertEquals(UnitStatus.AVAILABLE, u.status)
        assertTrue(exec(w, GridCommand.Mothball(w.owner, u.id)).ok)
        assertEquals(UnitStatus.MOTHBALLED, u.status)
        assertTrue(exec(w, GridCommand.Restart(w.owner, u.id), 5L).ok)
        assertTrue(exec(w, GridCommand.RetireUnit(w.owner, u.id)).ok)
        assertEquals(UnitStatus.RETIRED, u.status)
    }

    @Test fun theOperatorsOwnUnitsCannotBeRetiredByACompany() {
        val w = GridWorld()
        val u = w.unit(Technology.COAL, 50_000L)
        assertEquals(Rules.NOT_OWNER, exec(w, GridCommand.RetireUnit(w.owner, u.id)).rejection?.rule)
    }

    @Test fun unknownUnitsAreRefused() {
        val w = GridWorld()
        assertEquals(Rules.UNKNOWN_ENTITY, exec(w, GridCommand.RetireUnit(w.owner, UnitId(404))).rejection?.rule)
    }

    @Test fun anAgreementGoesThroughOfferAcceptAndTermination() {
        val w = GridWorld()
        w.unit(Technology.COAL, 100_000L, owner = w.owner)
        w.facility(20_000L)
        val offer = GridCommand.OfferPpa(w.owner, w.grid.id, w.buyer, PpaProfile.BASELOAD, 30_000L, 0L, null, Money.units(50), 0L, 90, 100_000L)
        assertTrue(exec(w, offer).ok)
        val id = w.state.ppas.single().id
        assertEquals(Rules.NOT_OWNER, exec(w, GridCommand.AcceptPpa(w.owner, id)).rejection?.rule)
        assertTrue(exec(w, GridCommand.AcceptPpa(w.buyer, id)).ok)
        w.days(2)
        val end = exec(w, GridCommand.TerminatePpa(w.buyer, id), 2L)
        assertTrue(end.ok)
        assertEquals(1, end.intents.size)
        assertEquals(ReasonCode.FEE, end.intents.single().reason)
        assertEquals(PpaStatus.TERMINATED, w.state.ppa(id)?.status)
    }

    @Test fun anOfferCanBeDeclined() {
        val w = GridWorld()
        w.unit(Technology.COAL, 100_000L, owner = w.owner)
        w.facility(20_000L)
        exec(w, GridCommand.OfferPpa(w.owner, w.grid.id, w.buyer, PpaProfile.BASELOAD, 30_000L, 0L, null, Money.units(50), 0L, 90, 0L))
        val id = w.state.ppas.single().id
        assertTrue(exec(w, GridCommand.DeclinePpa(w.buyer, id)).ok)
        assertEquals(PpaStatus.DECLINED, w.state.ppa(id)?.status)
        assertFalse(exec(w, GridCommand.DeclinePpa(w.buyer, id)).ok)
    }

    @Test fun refusalsKeepTheServicesWords() {
        val w = GridWorld()
        val r = exec(w, GridCommand.OfferPpa(w.owner, w.grid.id, w.buyer, PpaProfile.BASELOAD, 1L, 0L, null, Money.units(50), 0L, 90, 0L))
        assertFalse(r.ok)
        assertTrue(r.message.isNotBlank())
        assertEquals(r.rejection?.message, r.message)
    }

    @Test fun everyCommandHasADescription() {
        val c = CompanyId(1)
        val all = listOf<GridCommand>(
            GridCommand.ConnectFacility(c, GridId(1), FacilityId(2), CustomerClass.INDUSTRIAL, 1L), GridCommand.DisconnectFacility(c, FacilityId(2)),
            GridCommand.Reclassify(c, LoadId(1), CustomerClass.COMMERCIAL), GridCommand.SetDraw(c, LoadId(1), 5_000L),
            GridCommand.OrderUnit(c, GridId(1), Technology.WIND, 10_000L), GridCommand.RetireUnit(c, UnitId(1)), GridCommand.Mothball(c, UnitId(1)),
            GridCommand.Restart(c, UnitId(1)),
            GridCommand.OfferPpa(c, GridId(1), CompanyId(2), PpaProfile.BASELOAD, 1L, 0L, null, Money.units(5), 0L, 30, 0L),
            GridCommand.AcceptPpa(c, PpaId(1)), GridCommand.DeclinePpa(c, PpaId(1)), GridCommand.TerminatePpa(c, PpaId(1)),
        )
        val texts = all.map { GridCommandExecutor.describe(it) }
        assertTrue(texts.all { it.isNotBlank() })
        assertEquals(texts.size, texts.toSet().size)
        assertEquals("Order a 10 MW wind unit on grid 1", texts[4])
    }

    @Test fun commandsDoNotBreakTheInvariants() {
        val w = GridWorld()
        val u = w.unit(Technology.GAS, 100_000L, owner = w.owner)
        w.households(40_000L)
        exec(w, GridCommand.OrderUnit(w.owner, w.grid.id, Technology.WIND, 30_000L))
        exec(w, GridCommand.Mothball(w.owner, u.id))
        w.days(5)
        exec(w, GridCommand.Restart(w.owner, u.id), 5L)
        w.days(30)
        GridInvariants.check(w.state)
    }
}
