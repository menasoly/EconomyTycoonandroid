package app.many.economytycoon.persistence

import app.many.economytycoon.contracts.ContractCodec
import app.many.economytycoon.contracts.ContractParams
import app.many.economytycoon.contracts.ContractService
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.ContractStatus
import app.many.economytycoon.contracts.ContractTerms
import app.many.economytycoon.contracts.MissReason
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractSnapshotTest {
    private fun hash(s: ContractService) = CanonicalHasher().also { s.hashInto(it) }.finish().toHex()

    private fun populated(): ContractService {
        val s = ContractService(IdAllocators(), ContractParams())
        val terms = ContractTerms(GoodId("wheat"), Quantity.units(12), Price(Money.units(21)), 4, 6)
        val running = (s.propose(CompanyId(1), CompanyId(2), ContractSide.SELLER, FacilityId(10), terms, Tick(0)) as Outcome.Ok).value
        s.accept(running.id, CompanyId(2), FacilityId(20), Tick(1))
        s.recordDelivery(running, Tick(5), Money.units(252))
        s.recordMiss(running, Tick(9), MissReason.BUYER_FULL)
        s.addPenalty(running, Money.units(7))
        val open = (s.propose(CompanyId(3), CompanyId(1), ContractSide.BUYER, FacilityId(30), terms, Tick(2)) as Outcome.Ok).value
        val ended = (s.propose(CompanyId(2), CompanyId(3), ContractSide.BUYER, FacilityId(21), terms, Tick(2)) as Outcome.Ok).value
        s.decline(ended.id, CompanyId(3), Tick(3))
        assertEquals(ContractStatus.PROPOSED, open.status)
        return s
    }

    @Test fun theRegisterSurvivesAWriteAndRead() {
        val s = populated()
        val w = ByteWriter(); ContractCodec.write(s, w)
        val back = ContractService(IdAllocators(), ContractParams())
        ContractCodec.read(ByteReader(w.toByteArray()), back)
        assertEquals(hash(s), hash(back))
        assertEquals(s.all().map { it.id }, back.all().map { it.id })
        val k = back.all().first()
        assertEquals(1, k.delivered); assertEquals(1, k.missed); assertEquals(Money.units(7), k.penalties)
        assertEquals(FacilityId(20), k.counterFacility)
    }

    @Test fun readingReplacesWhatWasThere() {
        val s = populated()
        val w = ByteWriter(); ContractCodec.write(s, w)
        val other = populated()
        ContractCodec.read(ByteReader(w.toByteArray()), other)
        assertEquals(3, other.all().size)
    }

    @Test fun anUnknownVersionIsRefused() {
        val w = ByteWriter(); w.writeVarInt(99)
        val failed = try { ContractCodec.read(ByteReader(w.toByteArray()), ContractService(IdAllocators())); false } catch (e: ByteReader.CorruptDataException) { true }
        assertTrue(failed)
    }

    @Test fun anOutOfRangeStatusIsRefused() {
        val s = populated()
        val w = ByteWriter(); ContractCodec.write(s, w)
        val bytes = w.toByteArray()
        var refused = 0
        for (i in bytes.indices) {
            val damaged = bytes.copyOf(); damaged[i] = (damaged[i].toInt() xor 0x7F).toByte()
            try { ContractCodec.read(ByteReader(damaged), ContractService(IdAllocators())) } catch (e: ByteReader.CorruptDataException) { refused++ } catch (e: RuntimeException) { refused++ }
        }
        assertTrue(refused > 0)
    }

    @Test fun aSavedGameKeepsItsContracts() {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val a = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Seller Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val b = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Buyer Works", country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val fa = e.production.grantFacility(a, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        val fb = e.production.grantFacility(b, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        fa.inventory.add(GoodId("wheat"), Quantity.units(200), Money.units(2_000))
        val ref = e.content.good(GoodId("wheat")).referencePrice.perUnit
        val p = sim.execute(Actor.System, Command.ProposeContract(a, b, ContractSide.SELLER, fa.id, GoodId("wheat"), Quantity.units(10), ref, 2, 8)) as Outcome.Ok
        sim.execute(Actor.System, Command.AcceptContract(b, app.many.economytycoon.core.ContractId(p.value.created!!), fb.id))
        sim.advance(12)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        assertEquals(e.contracts.all().size, copy.economy.contracts.all().size)
        assertEquals(e.contracts.all().single().delivered, copy.economy.contracts.all().single().delivered)
        for (i in 0 until 10) { sim.advance(); copy.advance(); assertEquals(sim.hash(), copy.hash()) }
    }
}
