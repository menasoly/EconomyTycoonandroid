package app.many.economytycoon.simulation

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.ContractStatus
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.production.Facility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContractsTest {
    private val wheat = GoodId("wheat")

    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val ref = e.content.good(GoodId("wheat")).referencePrice.perUnit
        fun company(name: String, kind: CompanyKind): CompanyId {
            val actor = if (kind == CompanyKind.AI) Actor.System else Actor.Player(name.hashCode().toLong())
            val r = sim.execute(actor, Command.FoundCompany(name, country.id, null, Money.units(300_000), kind)) as Outcome.Ok
            return CompanyId(r.value.created!!)
        }
        fun farm(owner: CompanyId): Facility = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        fun at(tick: Long) { e.tick = Tick(tick) }
        fun run(tick: Long) = AiContracts(e, CommandExecutor(e)).also { at(tick) }.run(e.tick)
    }

    @Test fun anAiSellerAcceptsAFairOfferToBuyFromIt() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); val fm = r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        fa.inventory.add(wheat, Quantity.units(300), Money.units(3_000))
        val p = r.sim.execute(Actor.System, Command.ProposeContract(me, ai, ContractSide.BUYER, fm.id, wheat, Quantity.units(20), r.ref, 5, 6)) as Outcome.Ok
        val rep = r.run(1)
        assertEquals(1, rep.accepted)
        assertEquals(ContractStatus.ACTIVE, r.e.contracts.get(ContractId(p.value.created!!))!!.status)
    }

    @Test fun anAiSellerDeclinesWhenItHasNothingToSend() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        r.farm(ai); val fm = r.farm(me)
        val p = r.sim.execute(Actor.System, Command.ProposeContract(me, ai, ContractSide.BUYER, fm.id, wheat, Quantity.units(20), r.ref, 5, 6)) as Outcome.Ok
        val rep = r.run(1)
        assertEquals(1, rep.declined)
        assertEquals(ContractStatus.DECLINED, r.e.contracts.get(ContractId(p.value.created!!))!!.status)
    }

    @Test fun anAiSellerDeclinesAPriceFarBelowValue() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); val fm = r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        fa.inventory.add(wheat, Quantity.units(300), Money.units(3_000))
        val cheap = Money(r.ref.raw / 3)
        r.sim.execute(Actor.System, Command.ProposeContract(me, ai, ContractSide.BUYER, fm.id, wheat, Quantity.units(20), cheap, 5, 6))
        assertEquals(1, r.run(1).declined)
    }

    @Test fun anAiBuyerAcceptsWhenItHasRoomAndTheDealIsFair() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); val fm = r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        r.sim.execute(Actor.System, Command.ProposeContract(me, ai, ContractSide.SELLER, fm.id, wheat, Quantity.units(20), r.ref, 5, 6))
        assertEquals(1, r.run(1).accepted)
    }

    @Test fun anAiBuyerDeclinesAPriceAboveValue() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); val fm = r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        r.sim.execute(Actor.System, Command.ProposeContract(me, ai, ContractSide.SELLER, fm.id, wheat, Quantity.units(20), Money(r.ref.raw * 2), 5, 6))
        assertEquals(1, r.run(1).declined)
    }

    @Test fun offersToPlayerCompaniesAreLeftForThePlayer() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); val fm = r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        val p = r.sim.execute(Actor.Ai(ai), Command.ProposeContract(ai, me, ContractSide.SELLER, fa.id, wheat, Quantity.units(20), r.ref, 5, 6)) as Outcome.Ok
        r.run(1)
        assertTrue(r.e.contracts.get(ContractId(p.value.created!!))!!.isOpen)
        assertEquals(1, r.e.contracts.proposalsFor(me).size)
        assertNotNull(fm)
    }

    @Test fun anAiOffersItsSurplusToACompanyThatWantsToBuyIt() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); val fm = r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        fa.inventory.add(wheat, Quantity.units(800), Money.units(8_000))
        r.e.companies.policies.setBuy(me, BuyRule(fm.id, wheat, Quantity.units(100), 1_100_000L, true))
        var proposed = 0
        for (t in 0L until 20L) proposed += r.run(t).proposed
        assertEquals(1, proposed)
        val offer = r.e.contracts.proposalsFor(me).single()
        assertEquals(ai, offer.proposer)
        assertEquals(ContractSide.SELLER, offer.proposerSide)
        assertEquals(wheat, offer.terms.good)
    }

    @Test fun anAiDoesNotOfferToCompaniesThatDoNotWantTheGood() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        fa.inventory.add(wheat, Quantity.units(800), Money.units(8_000))
        var proposed = 0
        for (t in 0L until 40L) proposed += r.run(t).proposed
        assertEquals(0, proposed)
    }

    @Test fun anAiDoesNotPileUpOffersToTheSameCompany() {
        val r = Rig(); val ai = r.company("Rival Works", CompanyKind.AI); val me = r.company("My Works", CompanyKind.PLAYER)
        val fa = r.farm(ai); val fm = r.farm(me)
        fa.inventory.capacity = Quantity.units(10_000)
        fa.inventory.add(wheat, Quantity.units(800), Money.units(8_000))
        r.e.companies.policies.setBuy(me, BuyRule(fm.id, wheat, Quantity.units(100), 1_100_000L, true))
        for (t in 0L until 100L) r.run(t)
        assertTrue(r.e.contracts.proposalsFor(me).size <= 1)
    }
}
