package app.many.economytycoon.ui.present

import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.ContractStatus
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractsPresenterTest {
    private val wheat = GoodId("wheat")
    private val session by lazy { PresentSupport.session("Contract Works", 5L) }

    private fun rival() = session.economy.companies.all().first { it.id != session.selectedCompany }
    private fun myFarm() = session.economy.production.grantFacility(session.selectedCompany!!, FacilityTypeId("grain_farm"), session.economy.world.regions().first().id, null, null, null, session.economy.tick)
    private fun theirFarm() = session.economy.production.grantFacility(rival().id, FacilityTypeId("grain_farm"), session.economy.world.regions().first().id, null, null, null, session.economy.tick)
    private val ref get() = session.economy.content.good(wheat).referencePrice.perUnit

    @Test fun aNewCompanyHasNoContracts() {
        val m = ContractsPresenter.build(session)!!
        assertEquals(4, m.tiles.size)
        assertTrue(m.incoming.isEmpty() && m.outgoing.isEmpty() && m.running.isEmpty() && m.history.isEmpty())
        assertEquals("0", m.tiles[0].value)
    }

    @Test fun anOfferToYouListsTheFacilitiesThatSuitIt() {
        val me = session.selectedCompany!!
        val mine = myFarm(); val theirs = theirFarm()
        mine.inventory.capacity = Quantity.units(10_000)
        val p = session.issueNow(Command.ProposeContract(rival().id, me, ContractSide.SELLER, theirs.id, wheat, Quantity.units(20), ref, 5, 6))
        // The rival is not the player's to command, so the offer comes from the system on its behalf.
        val offer = session.economy.contracts.propose(rival().id, me, ContractSide.SELLER, theirs.id,
            app.many.economytycoon.contracts.ContractTerms(wheat, Quantity.units(20), app.many.economytycoon.core.Price(ref), 5, 6), session.economy.tick)
        assertTrue(p.toString(), p is Outcome.Rejected || p is Outcome.Ok)
        val id = (offer as Outcome.Ok).value.id.value
        val card = ContractsPresenter.build(session)!!.incoming.single { it.id == id }
        assertTrue(card.canAccept && card.canDecline)
        assertFalse(card.canCancel)
        assertEquals("You buy", card.role)
        assertTrue(card.facilities.single { it.id == mine.id }.suitable)
        assertEquals("Offer", card.status)
    }

    @Test fun anOfferWithNoSuitableFacilityCannotBeAccepted() {
        val me = session.selectedCompany!!
        val theirs = theirFarm()
        val offer = session.economy.contracts.propose(rival().id, me, ContractSide.BUYER, theirs.id,
            app.many.economytycoon.contracts.ContractTerms(wheat, Quantity.units(20), app.many.economytycoon.core.Price(ref), 5, 6), session.economy.tick) as Outcome.Ok
        val card = ContractsPresenter.build(session)!!.incoming.single { it.id == offer.value.id.value }
        assertFalse(card.canAccept)
        assertTrue(card.canDecline)
        assertEquals("You sell", card.role)
    }

    @Test fun yourOwnOfferCanBeWithdrawnButNotAnswered() {
        val mine = myFarm()
        val r = session.issueNow(Command.ProposeContract(session.selectedCompany!!, rival().id, ContractSide.SELLER, mine.id, wheat, Quantity.units(5), ref, 3, 4)) as Outcome.Ok
        val card = ContractsPresenter.build(session)!!.outgoing.single { it.id == r.value.created }
        assertTrue(card.canWithdraw)
        assertFalse(card.canAccept || card.canDecline || card.canCancel)
        assertTrue(card.title.startsWith("Sell to "))
    }

    @Test fun aRunningContractShowsProgressAndTheCostOfLeaving() {
        val me = session.selectedCompany!!
        val mine = myFarm(); val theirs = theirFarm()
        val r = session.issueNow(Command.ProposeContract(me, rival().id, ContractSide.SELLER, mine.id, wheat, Quantity.units(5), ref, 3, 4)) as Outcome.Ok
        val id = ContractId(r.value.created!!)
        session.economy.contracts.accept(id, rival().id, theirs.id, session.economy.tick)
        val k = session.economy.contracts.get(id)!!
        session.economy.contracts.recordDelivery(k, session.economy.tick, k.terms.deliveryValue)
        val card = ContractsPresenter.build(session)!!.running.single()
        assertEquals(0.25, card.progress, 1e-9)
        assertEquals("1 of 4 delivered", card.progressText)
        assertNotNull(card.cancelFee)
        assertTrue(card.canCancel)
        assertTrue(card.next.startsWith("next delivery"))
        assertEquals("Running", card.status)
        assertEquals(Tone.GOOD, card.statusTone)
    }

    @Test fun missesAreCountedInTheProgressText() {
        val me = session.selectedCompany!!
        val mine = myFarm(); val theirs = theirFarm()
        val r = session.issueNow(Command.ProposeContract(me, rival().id, ContractSide.SELLER, mine.id, wheat, Quantity.units(5), ref, 3, 4)) as Outcome.Ok
        val id = ContractId(r.value.created!!)
        session.economy.contracts.accept(id, rival().id, theirs.id, session.economy.tick)
        session.economy.contracts.recordMiss(session.economy.contracts.get(id)!!, session.economy.tick, app.many.economytycoon.contracts.MissReason.SELLER_SHORT)
        assertEquals("0 of 4 delivered, 1 missed", ContractsPresenter.build(session)!!.running.single().progressText)
    }

    @Test fun endedContractsMoveToTheHistory() {
        val mine = myFarm()
        val r = session.issueNow(Command.ProposeContract(session.selectedCompany!!, rival().id, ContractSide.SELLER, mine.id, wheat, Quantity.units(5), ref, 3, 4)) as Outcome.Ok
        session.issueNow(Command.CancelContract(session.selectedCompany!!, ContractId(r.value.created!!)))
        val m = ContractsPresenter.build(session)!!
        assertTrue(m.outgoing.isEmpty())
        assertEquals(ContractStatus.CANCELLED.let { "Cancelled" }, m.history.single().status)
        assertEquals(Tone.MUTED, m.history.single().statusTone)
    }

    @Test fun priceComparisonFavoursTheRightSide() {
        val me = session.selectedCompany!!
        val mine = myFarm()
        val dear = Money(ref.raw * 2)
        val sell = session.issueNow(Command.ProposeContract(me, rival().id, ContractSide.SELLER, mine.id, wheat, Quantity.units(5), dear, 3, 4)) as Outcome.Ok
        val buy = session.issueNow(Command.ProposeContract(me, rival().id, ContractSide.BUYER, mine.id, wheat, Quantity.units(5), dear, 3, 4)) as Outcome.Ok
        val m = ContractsPresenter.build(session)!!
        assertEquals(Tone.GOOD, m.outgoing.single { it.id == sell.value.created }.versusTone)
        assertEquals(Tone.WARN, m.outgoing.single { it.id == buy.value.created }.versusTone)
        assertTrue(m.outgoing.first().versusMarket.startsWith("+"))
    }

    @Test fun everyStatusHasALabelAndTone() {
        for (s in ContractStatus.values()) {
            assertTrue(ContractsPresenter.statusLabel(s).isNotBlank())
            ContractsPresenter.statusTone(s)
        }
        assertEquals(ContractStatus.values().size, ContractStatus.values().map { ContractsPresenter.statusLabel(it) }.toSet().size)
    }

    @Test fun setupListsPartnersGoodsAndFacilities() {
        myFarm()
        val s = ContractsPresenter.setup(session)!!
        assertTrue(s.partners.isNotEmpty() && s.partners.none { it.id == session.selectedCompany })
        assertEquals(session.economy.content.allGoods.size, s.goods.size)
        assertEquals(1, s.facilities.size)
        assertTrue(s.limits.contains("missed delivery"))
    }

    @Test fun facilityChoicesReflectStockForSellersAndRoomForBuyers() {
        val me = session.selectedCompany!!
        val f = myFarm()
        f.inventory.capacity = Quantity.units(1_000)
        f.inventory.add(wheat, Quantity.units(40), Money.units(400))
        val sell = ContractsPresenter.facilitiesFor(session, me, wheat, ContractSide.SELLER, Quantity.units(50)).single()
        assertFalse(sell.suitable)
        assertTrue(sell.detail.endsWith("on hand"))
        assertTrue(ContractsPresenter.facilitiesFor(session, me, wheat, ContractSide.SELLER, Quantity.units(40)).single().suitable)
        val buy = ContractsPresenter.facilitiesFor(session, me, wheat, ContractSide.BUYER, Quantity.units(50)).single()
        assertTrue(buy.suitable)
        assertTrue(buy.detail.startsWith("room for"))
    }
}
