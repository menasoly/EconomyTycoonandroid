package app.many.economytycoon.net

import app.many.economytycoon.companies.Department
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealSimulationPortTest {
    private fun port() = RealSimulationPort(SimTestSupport.sim(seed = 7, ai = 0), SimTestSupport.content)

    private class Game {
        val net = MemoryNetwork(3, LinkProfile(lossPpm = 50_000, minDelayMs = 0, maxDelayMs = 30))
        val serverPort = RealSimulationPort(SimTestSupport.sim(seed = 7, ai = 0), SimTestSupport.content)
        val shard = ServerShard(
            ShardConfig(session = NetFixtures.session, hashInterval = 5, lobby = LobbyConfig(maxPlayers = 2), snapshotMinIntervalMs = 100L),
            serverPort, net.endpoint(EndpointId(1)), SeededTokenSource(5),
        )
        val ports = List(2) { RealSimulationPort(SimTestSupport.sim(seed = 7, ai = 0), SimTestSupport.content) }
        val clients = ports.mapIndexed { i, p -> ClientSession(ClientConfig(session = NetFixtures.session, name = "Tycoon $i"), p, net.endpoint(EndpointId(10 + i)), EndpointId(1)) }
        var now = 0L

        fun frame() { now += 10; net.advanceTo(now); shard.poll(now); clients.forEach { it.poll(now) } }
        fun until(maxFrames: Int, c: () -> Boolean) { var n = 0; while (n++ < maxFrames && !c()) frame() }
        fun start() {
            clients.forEach { it.connect(now) }
            until(2000) { clients.all { it.lobby() != null } }
            clients.forEach { it.ready(now) }
            until(2000) { shard.lobby.players().all { it.ready } }
            clients.first { it.player == shard.lobby.hostId }.startGame(now)
            until(2000) { clients.all { it.state == ClientState.RUNNING } }
        }
        fun run(ticks: Int, each: (Int) -> Unit = {}) { repeat(ticks) { each(it); shard.stepTick(now); repeat(5) { frame() } } }
        fun converged() = ports.all { it.tick == serverPort.tick && it.stateHash().contentEquals(serverPort.stateHash()) }
    }

    @Test fun seatingFoundsOneCompanyPerPlayer() {
        val p = port()
        val results = Seating.seat(p, listOf(SeatAssignment(0, PlayerId(1), "Alpha Works"), SeatAssignment(1, PlayerId(2), "Beta Works")))
        assertTrue(results.all { it.company > 0 })
        assertEquals(1, p.companiesOf(PlayerId(1)).size)
        assertEquals(results[0].company, p.companiesOf(PlayerId(1)).single())
    }

    @Test fun aTakenNameFallsBackToTheNextCandidate() {
        val p = port()
        val results = Seating.seat(p, listOf(SeatAssignment(0, PlayerId(1), "Same Name"), SeatAssignment(1, PlayerId(2), "Same Name")))
        assertEquals("Same Name", results[0].name)
        assertEquals("Same Name Co", results[1].name)
    }

    @Test fun snapshotRoundTripRestoresTheExactState() {
        val a = port()
        Seating.seat(a, listOf(SeatAssignment(0, PlayerId(1), "Alpha Works")))
        repeat(5) { a.advance() }
        val bytes = a.snapshot()
        val b = port()
        assertTrue(b.restore(bytes) is app.many.economytycoon.core.Outcome.Ok)
        assertEquals(a.tick, b.tick)
        assertTrue(a.stateHash().contentEquals(b.stateHash()))
        b.advance(); a.advance()
        assertTrue(a.stateHash().contentEquals(b.stateHash()))
    }

    @Test fun restoringGarbageIsRefusedWithoutThrowing() {
        val p = port()
        assertTrue(p.restore(ByteArray(40) { it.toByte() }) is app.many.economytycoon.core.Outcome.Rejected)
        assertTrue(p.restore(ByteArray(0)) is app.many.economytycoon.core.Outcome.Rejected)
    }

    @Test fun cashTransfersMoveMoneyThroughTheLedgerAndConserveTheTotal() {
        val p = port()
        val r = Seating.seat(p, listOf(SeatAssignment(0, PlayerId(1), "Alpha Works"), SeatAssignment(1, PlayerId(2), "Beta Works")))
        val a = r[0].company; val b = r[1].company
        val total = p.available(a, TradeAsset.cash(1)) + p.available(b, TradeAsset.cash(1))
        val ok = p.applyTransfers(listOf(Transfer(TradeId(1), a, RealSimulationPort.ESCROW, TradeAsset.cash(1_000_000)), Transfer(TradeId(1), RealSimulationPort.ESCROW, b, TradeAsset.cash(1_000_000))))
        assertEquals(listOf(true, true), ok)
        assertEquals(total, p.available(a, TradeAsset.cash(1)) + p.available(b, TradeAsset.cash(1)))
        assertFalse(p.applyTransfers(listOf(Transfer(TradeId(2), a, b, TradeAsset.goods("wheat", 5)))).single())
        assertFalse(p.supports(AssetKind.GOODS))
    }

    @Test fun twoClientsStayInStepWithTheRealEconomy() {
        val g = Game()
        g.start()
        assertEquals(LobbyPhase.RUNNING, g.shard.lobby.phase)
        g.run(8) { i ->
            for ((n, c) in g.clients.withIndex()) {
                val co = g.serverPort.companiesOf(c.player).firstOrNull() ?: continue
                if ((i + n) % 4 == 0) c.submit(Command.SetBudget(CompanyId(co), Department.MARKETING, Money.units(5L + i)), g.now)
            }
        }
        g.until(3000) { g.converged() }
        assertTrue(g.converged())
        assertEquals(8L, g.serverPort.tick)
        val verdict = SessionReplay { port() }.verify(g.shard.record())
        assertTrue(verdict.toString(), verdict.ok)
    }

    @Test fun foundingACompanyThroughTheWireIsRefusedAsAMintingRisk() {
        val g = Game()
        g.start()
        val me = g.clients[0]
        val country = g.serverPort.simulation.economy.world.countries().minByOrNull { it.id }?.id ?: error("no country")
        me.submit(Command.FoundCompany("Mint Corp", country, null, Money.units(1_000_000_000L)), g.now)
        g.run(10)
        assertEquals(1, g.serverPort.companiesOf(me.player).size)
        assertTrue(g.shard.antiCheat.score(me.player) > 0)
    }
}
