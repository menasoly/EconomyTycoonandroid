package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShardStateTest {
    private fun traffic(rig: NetRig, i: Int) {
        for ((n, c) in rig.clients.withIndex()) {
            if (c.state != ClientState.RUNNING) continue
            val co = rig.serverSim.companiesOf(c.player).firstOrNull() ?: continue
            if ((i + n) % 6 == 0) c.submit(ToySimulation.budget(co, (i * 3L + n) % 900), rig.now)
        }
    }

    private fun saved(rig: NetRig) = ByteWriter().also { rig.shard.writeState(it) }.toByteArray()

    private fun blank(rig: NetRig) = ServerShard(rig.config, ToySimulation(), NetRig(clientCount = 0).net.endpoint(EndpointId(1)), SeededTokenSource(1))

    @Test fun savedStateRoundTripsToTheSameHash() {
        val rig = NetRig()
        rig.startGame()
        rig.run(40) { traffic(rig, it) }
        val first = blank(rig)
        first.readState(ByteReader(saved(rig)), rig.now)
        assertEquals(rig.shard.log.size, first.log.size)
        assertEquals(rig.shard.lobby.phase, first.lobby.phase)
        assertEquals(rig.shard.identities.all().map { it.name }, first.identities.all().map { it.name })
        // everyone is dropped on load, so compare a second round trip with the first load
        val second = blank(rig)
        second.readState(ByteReader(ByteWriter().also { first.writeState(it) }.toByteArray()), rig.now)
        assertArrayEquals(first.stateHash(), second.stateHash())
    }

    @Test fun theHashChangesWhenTheStateDoes() {
        val rig = NetRig()
        rig.startGame()
        val a = rig.shard.stateHash()
        assertArrayEquals(a, rig.shard.stateHash())
        rig.run(5)
        assertFalse(a.contentEquals(rig.shard.stateHash()))
    }

    @Test fun damagedStateIsRefusedWithoutOtherExceptions() {
        val rig = NetRig()
        rig.startGame()
        rig.run(20)
        val bytes = saved(rig)
        var refused = 0
        for (cut in listOf(0, 1, 5, bytes.size / 3, bytes.size - 1)) {
            val target = ServerShard(rig.config, ToySimulation(), NetRig(clientCount = 0).net.endpoint(EndpointId(1)), SeededTokenSource(1))
            try { target.readState(ByteReader(bytes.copyOf(cut)), 0); fail("accepted $cut bytes") } catch (e: ByteReader.CorruptDataException) { refused++ }
        }
        assertEquals(5, refused)
    }

    @Test fun aWrongVersionIsRefused() {
        val rig = NetRig()
        rig.startGame()
        val bytes = saved(rig)
        bytes[0] = 9
        val target = ServerShard(rig.config, ToySimulation(), NetRig(clientCount = 0).net.endpoint(EndpointId(1)), SeededTokenSource(1))
        try { target.readState(ByteReader(bytes), 0); fail("accepted") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message.orEmpty().contains("version")) }
    }

    @Test fun aRestartedHostLetsEveryoneResumeAndTheGameGoesOn() {
        val rig = NetRig(clientConfig = { it.copy(silenceTimeoutMs = 1_500L) })
        rig.startGame()
        rig.run(40) { traffic(rig, it) }
        rig.drain()
        val before = rig.serverSim.tick
        rig.restartServer(downMs = 3_000L)
        rig.waitFor(20_000L) { rig.clients.all { it.state == ClientState.RUNNING && rig.shard.isConnected(it.player) } }
        assertTrue(rig.clients.all { it.state == ClientState.RUNNING && rig.shard.isConnected(it.player) })
        rig.run(40) { traffic(rig, it) }
        rig.drain()
        assertTrue(rig.converged())
        assertEquals(before + 40, rig.serverSim.tick)
        assertEquals(before + 40, rig.shard.log.size.toLong())
    }

    @Test fun aRestartedHostKeepsPlayersEnrolledAndRefusesStrangersWithTheirNames() {
        val rig = NetRig(clientConfig = { it.copy(silenceTimeoutMs = 1_500L) })
        rig.startGame()
        rig.run(10)
        val ids = rig.shard.identities.all().map { it.id }
        rig.restartServer(downMs = 2_000L)
        assertEquals(ids, rig.shard.identities.all().map { it.id })
        assertTrue(rig.shard.lobby.members().all { it.presence == Presence.GRACE })
        val stranger = RawPeer(rig, EndpointId(90), name = rig.client(0).lobby()?.members?.first()?.name ?: "x")
        assertFalse(stranger.join())
    }

    @Test fun openTradesSurviveARestart() {
        val rig = NetRig(income = false, clientConfig = { it.copy(silenceTimeoutMs = 1_500L) })
        rig.startGame()
        val total = rig.serverSim.totalCash()
        rig.client(0).offerTrade(rig.client(1).player, listOf(TradeAsset.cash(400_000)), listOf(TradeAsset.cash(50_000)), 120)
        rig.run(6)
        rig.drain()
        assertEquals(1, rig.shard.escrow.open().size)
        assertEquals(rig.serverSim.escrowCash, rig.shard.escrow.heldTotals()[AssetKind.CASH to ""] ?: 0L)
        rig.restartServer(downMs = 2_500L)
        rig.waitFor(20_000L) { rig.clients.all { it.state == ClientState.RUNNING && rig.shard.isConnected(it.player) } }
        assertEquals(1, rig.shard.escrow.open().size)
        assertEquals(rig.serverSim.escrowCash, rig.shard.escrow.heldTotals()[AssetKind.CASH to ""] ?: 0L)
        val offer = rig.client(1).trades().first { it.state == TradeState.OFFERED }
        rig.client(1).answerTrade(offer.trade, TradeAction.ACCEPT)
        rig.run(12)
        rig.drain()
        assertTrue(rig.converged())
        assertEquals(total, rig.serverSim.totalCash())
        assertTrue(rig.client(1).trades().any { it.state == TradeState.SETTLED })
    }
}
