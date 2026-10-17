package app.many.economytycoon.urban

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UrbanCodecTest {
    private val T = UrbanTestSupport
    private val firm = CompanyId(5)

    /** A city that has seen plenty: petitions, a project, a concession, lobbying, a metro line, and years of events. */
    private fun busy(seed: Long = 11L): UrbanService {
        val svc = T.withCity(seed)
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(3_000_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val d = plan.districts.first { it.zone == ZoneUse.RESIDENTIAL }
        val p = svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.LobbyPetition(T.city, PetitionId(p.created!!), firm, 3, Money.units(20_000)), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.StartProject(T.city, ProjectKind.PARK, "Central park", d.index, null, null, 0L, false), Tick(0)).getOrThrow()
        val line = svc.execute(UrbanCommand.PlanLine(T.city, "Blue", TransitMode.METRO, listOf(0, 4, 8), 240, Money.units(2), true), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.StartProject(T.city, ProjectKind.TRANSIT_LINE, "Blue line", null, TransitLineId(line.created!!), null, 0L, true), Tick(0)).getOrThrow()
        val road = plan.network.roads().first()
        val c = svc.execute(UrbanCommand.TenderConcession(T.city, ConcessionKind.TOLL_ROAD, "Toll", road.id, null, PaymentModel.MIXED, 240, 6, Money.units(30_000_000), Money.units(2), Money.units(100_000), 150_000L, 5_000L), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.BidConcession(T.city, ConcessionId(c.created!!), firm, 90_000L), Tick(1)).getOrThrow()
        T.run(svc, 0, 14 * 30)
        return svc
    }

    private fun save(svc: UrbanService): ByteArray = ByteWriter().also { UrbanCodec.write(svc.state, it) }.toByteArray()

    private fun load(bytes: ByteArray, seed: Long = 11L, params: UrbanParams = T.params): UrbanService {
        val state = UrbanState(params, seed)
        UrbanCodec.read(ByteReader(bytes), state)
        return UrbanService(state).also { it.refreshScores() }
    }

    @Test fun `an empty state round trips`() {
        val svc = T.service()
        val back = load(save(svc), 7L)
        assertEquals(0, back.state.size)
        assertEquals(svc.stateHash(), back.stateHash())
    }

    @Test fun `a busy state round trips to the same hash`() {
        val svc = busy()
        val back = load(save(svc))
        assertEquals(svc.stateHash(), back.stateHash())
        assertEquals(svc.state.size, back.state.size)
    }

    @Test fun `every part of the plan survives`() {
        val a = T.plan(busy()); val b = T.plan(load(save(busy())))
        assertEquals(a.districts.map { it.zone to it.rentPerSqm }, b.districts.map { it.zone to it.rentPerSqm })
        assertEquals(a.network.roads().map { it.id to it.flowVph }, b.network.roads().map { it.id to it.flowVph })
        assertEquals(a.network.lines().map { it.id to it.status }, b.network.lines().map { it.id to it.status })
        assertEquals(a.petitions.all().map { it.id to it.status }, b.petitions.all().map { it.id to it.status })
        assertEquals(a.petitions.efforts(), b.petitions.efforts())
        assertEquals(a.projects.all().map { it.id to it.phase }, b.projects.all().map { it.id to it.phase })
        assertEquals(a.concessions.all().map { it.id to it.status }, b.concessions.all().map { it.id to it.status })
        assertEquals(a.events.all().map { it.id }, b.events.all().map { it.id })
        assertEquals(a.history(), b.history())
        assertEquals(a.council.seats.map { it.ideology }, b.council.seats.map { it.ideology })
        assertEquals(a.budget.levelsList(), b.budget.levelsList())
        assertEquals(a.budget.cash, b.budget.cash)
    }

    @Test fun `a loaded game carries on exactly like the original`() {
        val original = busy()
        val bytes = save(original)
        val restored = load(bytes)
        T.run(original, 14 * 30L, 12 * 30)
        T.run(restored, 14 * 30L, 12 * 30)
        assertEquals(original.stateHash(), restored.stateHash())
    }

    @Test fun `encoding is stable`() {
        assertTrue(save(busy()).contentEquals(save(busy())))
    }

    @Test fun `the version byte leads the data`() {
        val bytes = save(T.service())
        assertEquals(UrbanCodec.VERSION, ByteReader(bytes).readVarInt())
    }

    @Test fun `an unknown version is refused`() {
        val bytes = save(T.withCity()).copyOf()
        bytes[0] = 9
        try { load(bytes); fail() } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("version")) }
    }

    @Test fun `a save from another world is refused`() {
        val bytes = save(T.withCity(seed = 3L))
        try { load(bytes, seed = 4L); fail() } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("another world")) }
    }

    @Test fun `a save with another grid is refused`() {
        val bytes = save(T.withCity())
        try { load(bytes, 7L, UrbanParams(gridSize = 8, blockSize = 4)); fail() } catch (e: ByteReader.CorruptDataException) { }
    }

    @Test fun `truncated data is reported rather than half loaded silently`() {
        val bytes = save(busy())
        for (cut in listOf(1, 5, bytes.size / 3, bytes.size - 1)) {
            try { load(bytes.copyOf(cut)); fail("cut at $cut") } catch (e: ByteReader.CorruptDataException) { }
        }
    }

    @Test fun `loading replaces what the state held`() {
        val svc = busy()
        val other = T.withCity(seed = 11L, population = 30_000L, id = CityId(4))
        UrbanCodec.read(ByteReader(save(svc)), other.state)
        assertEquals(listOf(T.city), other.state.cities())
        assertEquals(svc.stateHash(), other.stateHash())
    }

    @Test fun `the hash changes when anything changes`() {
        val svc = busy()
        val h = svc.stateHash()
        svc.execute(UrbanCommand.SetServiceLevel(T.city, Service.PARKS, 123), Tick(500))
        assertTrue(h != svc.stateHash())
    }

    @Test fun `id counters are restored so new ids do not clash`() {
        val svc = busy()
        val before = svc.state.ids.snapshot().toList()
        val back = load(save(svc))
        assertEquals(before, back.state.ids.snapshot().toList())
        val d = T.plan(back).districts.last { it.zone == ZoneUse.RESIDENTIAL && T.plan(back).petitions.openFor(it.index) == null }
        val r = back.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null), Tick(9_999))
        if (r.isOk) assertTrue(T.plan(back).petitions.all().map { it.id }.toSet().size == T.plan(back).petitions.all().size)
    }

    @Test fun `a lobbying key sequence continues after loading`() {
        val svc = T.withCity()
        val d = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL }
        val id = PetitionId(svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm), Tick(0)).getOrThrow().created!!)
        val first = svc.execute(UrbanCommand.LobbyPetition(T.city, id, firm, null, Money.units(1_000)), Tick(1)).getOrThrow()
        val back = load(save(svc), 7L)
        val second = back.execute(UrbanCommand.LobbyPetition(T.city, id, firm, null, Money.units(1_000)), Tick(2)).getOrThrow()
        assertEquals("urban:lobby:${id.value}:1", first.intents[0].key)
        assertEquals("urban:lobby:${id.value}:2", second.intents[0].key)
    }
}
