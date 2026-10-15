package app.many.economytycoon.simulation

import app.many.economytycoon.contracts.ContractEvent
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.ContractStatus
import app.many.economytycoon.contracts.MissReason
import app.many.economytycoon.contracts.SupplyContract
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.ContractId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.Facility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractRunnerTest {
    private val wheat = GoodId("wheat")

    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        fun company(name: String, capital: Money = Money.units(300_000)): CompanyId {
            val r = sim.execute(Actor.Player(name.hashCode().toLong()), Command.FoundCompany(name, country.id, null, capital)) as Outcome.Ok
            return CompanyId(r.value.created!!)
        }
        fun farm(owner: CompanyId): Facility = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        fun cash(c: CompanyId) = e.finance.ledger.cash(LedgerOwner.company(c))
    }

    private fun Rig.deal(seller: CompanyId, buyer: CompanyId, sf: Facility, bf: Facility, qty: Long = 10, price: Long = 20, every: Int = 3, n: Int = 3): SupplyContract {
        val p = sim.execute(Actor.System, Command.ProposeContract(seller, buyer, ContractSide.SELLER, sf.id, wheat, Quantity.units(qty), Money.units(price), every, n)) as Outcome.Ok
        val id = ContractId(p.value.created!!)
        val a = sim.execute(Actor.System, Command.AcceptContract(buyer, id, bf.id))
        assertTrue(a.toString(), a is Outcome.Ok)
        return e.contracts.get(id)!!
    }

    private val Rig.refPrice get() = e.content.good(wheat).referencePrice.perUnit

    @Test fun aDeliveryMovesGoodsAndMoneyOnSchedule() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000))
        val k = r.deal(a, b, fa, fb, price = r.refPrice.wholeUnits().coerceAtLeast(1))
        val cashA = r.cash(a); val cashB = r.cash(b)
        val due = k.nextDue!!
        while (r.e.tick < due) r.e.contractRunner.run(r.e.tick).also { assertTrue(it.isEmpty()); r.e.tick = app.many.economytycoon.core.Tick(r.e.tick.value + 1) }
        val events = r.e.contractRunner.run(r.e.tick)
        assertTrue(events.single() is ContractEvent.Delivered)
        assertEquals(Quantity.units(90), fa.inventory.quantity(wheat))
        assertEquals(Quantity.units(10), fb.inventory.quantity(wheat))
        val value = k.terms.deliveryValue
        assertEquals(cashA + value, r.cash(a))
        assertEquals(cashB - value, r.cash(b))
        assertEquals(1, k.delivered)
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun theLastDeliveryFulfilsTheContract() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000))
        val k = r.deal(a, b, fa, fb, price = r.refPrice.wholeUnits().coerceAtLeast(1), n = 2, every = 1)
        var fulfilled = false
        repeat(5) {
            r.e.tick = app.many.economytycoon.core.Tick(r.e.tick.value + 1)
            if (r.e.contractRunner.run(r.e.tick).any { it is ContractEvent.Fulfilled }) fulfilled = true
        }
        assertTrue(fulfilled)
        assertEquals(ContractStatus.FULFILLED, k.status)
        assertEquals(Quantity.units(20), fb.inventory.quantity(wheat))
    }

    @Test fun aShortSellerMissesAndPaysAFine() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        val k = r.deal(a, b, fa, fb, price = r.refPrice.wholeUnits().coerceAtLeast(1), every = 1)
        val cashA = r.cash(a); val cashB = r.cash(b)
        r.e.tick = app.many.economytycoon.core.Tick(r.e.tick.value + 1)
        val events = r.e.contractRunner.run(r.e.tick)
        assertEquals(MissReason.SELLER_SHORT, (events.single() as ContractEvent.Missed).reason)
        assertTrue(r.cash(a) < cashA)
        assertEquals(cashA - r.cash(a), r.cash(b) - cashB)
        assertEquals(cashA - r.cash(a), k.penalties)
        assertEquals(Quantity.ZERO, fb.inventory.quantity(wheat))
    }

    @Test fun threeMissesBreachTheContractAgainstTheSeller() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        val k = r.deal(a, b, fa, fb, every = 1)
        val all = ArrayList<ContractEvent>()
        repeat(3) { r.e.tick = app.many.economytycoon.core.Tick(r.e.tick.value + 1); all += r.e.contractRunner.run(r.e.tick) }
        assertEquals(ContractStatus.BREACHED, k.status)
        assertEquals(a, all.filterIsInstance<ContractEvent.Breached>().single().at)
    }

    @Test fun aFullBuyerStoreMissesWithoutMovingAnything() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Cramped Works")
        val fa = r.farm(a); val fb = r.farm(b)
        fb.inventory.capacity = Quantity.units(1)
        fa.inventory.add(wheat, Quantity.units(100), Money.units(1_000))
        val k = r.deal(a, b, fa, fb, every = 1)
        r.e.tick = app.many.economytycoon.core.Tick(r.e.tick.value + 1)
        val ev = r.e.contractRunner.run(r.e.tick).single() as ContractEvent.Missed
        assertEquals(MissReason.BUYER_FULL, ev.reason)
        assertEquals(Quantity.units(100), fa.inventory.quantity(wheat))
        assertEquals(0, k.delivered)
    }

    @Test fun aBuyerWithoutCashMissesWithoutMovingAnything() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Broke Works", Money.units(50_000))
        val fa = r.farm(a); val fb = r.farm(b)
        fa.inventory.capacity = Quantity.units(100_000)
        fa.inventory.add(wheat, Quantity.units(5_000), Money.units(50_000))
        fb.inventory.capacity = Quantity.units(100_000)
        val k = r.deal(a, b, fa, fb, qty = 5_000, price = r.refPrice.wholeUnits().coerceAtLeast(1) * 4, every = 1)
        r.e.tick = app.many.economytycoon.core.Tick(r.e.tick.value + 1)
        val ev = r.e.contractRunner.run(r.e.tick)
        assertEquals(MissReason.BUYER_BROKE, (ev.single() as ContractEvent.Missed).reason)
        assertEquals(Quantity.units(5_000), fa.inventory.quantity(wheat))
        assertEquals(0, k.delivered)
    }

    @Test fun aDemolishedFacilityCountsAsAMiss() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        val k = r.deal(a, b, fa, fb, every = 1)
        r.e.production.demolish(b, fb.id)
        r.e.tick = app.many.economytycoon.core.Tick(r.e.tick.value + 1)
        val ev = r.e.contractRunner.run(r.e.tick).single() as ContractEvent.Missed
        assertEquals(MissReason.FACILITY_GONE, ev.reason)
        assertEquals(1, k.missed)
    }

    @Test fun cancellingARunningContractCostsTheLeaverAFee() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        val k = r.deal(a, b, fa, fb, price = r.refPrice.wholeUnits().coerceAtLeast(1))
        val cashA = r.cash(a); val cashB = r.cash(b)
        val res = r.sim.execute(Actor.System, Command.CancelContract(a, k.id))
        assertTrue(res.toString(), res is Outcome.Ok)
        val fee = r.e.contracts.cancelFee(k)
        assertEquals(cashA - fee, r.cash(a))
        assertEquals(cashB + fee, r.cash(b))
        assertEquals(ContractStatus.CANCELLED, k.status)
    }

    @Test fun withdrawingAnOpenOfferIsFree() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a)
        val p = r.sim.execute(Actor.System, Command.ProposeContract(a, b, ContractSide.SELLER, fa.id, wheat, Quantity.units(5), r.refPrice, 2, 4)) as Outcome.Ok
        val cash = r.cash(a)
        assertTrue(r.sim.execute(Actor.System, Command.CancelContract(a, ContractId(p.value.created!!))) is Outcome.Ok)
        assertEquals(cash, r.cash(a))
    }

    @Test fun proposalsNeedAFacilityTheProposerOwns() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fb = r.farm(b)
        val res = r.sim.execute(Actor.System, Command.ProposeContract(a, b, ContractSide.SELLER, fb.id, wheat, Quantity.units(5), r.refPrice, 2, 4))
        assertEquals(Rules.NOT_OWNER, (res as Outcome.Rejected).rejection.rule)
    }

    @Test fun acceptingNeedsAFacilityTheAccepterOwns() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a)
        val p = r.sim.execute(Actor.System, Command.ProposeContract(a, b, ContractSide.SELLER, fa.id, wheat, Quantity.units(5), r.refPrice, 2, 4)) as Outcome.Ok
        val res = r.sim.execute(Actor.System, Command.AcceptContract(b, ContractId(p.value.created!!), fa.id))
        assertEquals(Rules.NOT_OWNER, (res as Outcome.Rejected).rejection.rule)
    }

    @Test fun unknownGoodsAndCompaniesAreRefused() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a)
        val bad = r.sim.execute(Actor.System, Command.ProposeContract(a, b, ContractSide.SELLER, fa.id, GoodId("nothing"), Quantity.units(5), r.refPrice, 2, 4))
        assertEquals(Rules.UNKNOWN_ENTITY, (bad as Outcome.Rejected).rejection.rule)
        val ghost = r.sim.execute(Actor.System, Command.ProposeContract(a, CompanyId(999), ContractSide.SELLER, fa.id, wheat, Quantity.units(5), r.refPrice, 2, 4))
        assertEquals(Rules.UNKNOWN_ENTITY, (ghost as Outcome.Rejected).rejection.rule)
    }

    @Test fun aRetiredCompanyTakesItsContractsWithIt() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        val k = r.deal(a, b, fa, fb)
        r.e.retire(a)
        assertEquals(ContractStatus.CANCELLED, k.status)
    }

    @Test fun contractsRunInsideTheTickPipeline() {
        val r = Rig(); val a = r.company("Seller Works"); val b = r.company("Buyer Works")
        val fa = r.farm(a); val fb = r.farm(b)
        fa.inventory.add(wheat, Quantity.units(500), Money.units(5_000))
        fa.inventory.capacity = Quantity.units(10_000)
        val k = r.deal(a, b, fa, fb, price = r.refPrice.wholeUnits().coerceAtLeast(1), every = 1, n = 5)
        var delivered = 0; var missed = 0
        repeat(6) { val s = r.sim.advance(); delivered += s.contractsDelivered; missed += s.contractsMissed }
        assertTrue("delivered $delivered missed $missed ${k.status}", delivered + missed >= 5)
        assertTrue(r.e.finance.invariants().isEmpty())
    }
}
