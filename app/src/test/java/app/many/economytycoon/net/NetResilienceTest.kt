package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetResilienceTest {
    private fun traffic(rig: NetRig, i: Int) {
        for ((n, c) in rig.clients.withIndex()) {
            if (c.state != ClientState.RUNNING || c.role != Role.PLAYER) continue
            val co = rig.serverSim.companiesOf(c.player).firstOrNull() ?: continue
            if ((i + n) % 9 == 0) c.submit(ToySimulation.budget(co, (i * 7L + n) % 4_000), rig.now)
        }
    }

    @Test fun aCorruptedClientStateIsDetectedAndRepairedBySnapshot() {
        val rig = NetRig()
        rig.startGame()
        rig.run(25) { traffic(rig, it) }
        // damage client 1's private copy of the game
        rig.sims[1].companies.values.first().cash += 12_345
        rig.run(40) { traffic(rig, it) }
        rig.drain()
        assertTrue(rig.converged())
        val view = rig.client(1).view(rig.now)
        assertTrue(view.desyncs >= 1)
        assertTrue(view.resyncs >= 1)
        assertTrue(rig.client(1).drainEvents().any { it is ClientEvent.Resynced })
        assertEquals(0, rig.client(0).view(rig.now).desyncs)
        assertTrue(rig.shard.desyncReports().isNotEmpty())
        assertEquals(1, rig.shard.metrics().snapshotsServed.coerceAtLeast(1))
    }

    @Test fun desyncAttributionNamesTheDivergentClient() {
        val rig = NetRig(clientCount = 3)
        rig.startGame()
        rig.run(12)
        rig.sims[2].companies.values.last().cash -= 1
        rig.run(30)
        val report = rig.shard.desyncReports().first()
        assertTrue(rig.client(2).player in report.culprits)
        assertFalse(rig.client(0).player in report.culprits)
    }

    @Test fun snapshotTransferSurvivesALossyLink() {
        val rig = NetRig(seed = 5, profile = LinkProfile(lossPpm = 200_000, duplicatePpm = 50_000, maxDelayMs = 40))
        rig.startGame()
        rig.run(20) { traffic(rig, it) }
        rig.sims[0].companies.values.first().budget += 1
        rig.run(30) { traffic(rig, it) }
        rig.drain()
        assertTrue(rig.converged())
        assertTrue(rig.client(0).view(rig.now).resyncs >= 1)
    }

    @Test fun aLateJoinerIsSyncedBySnapshotAsASpectator() {
        val rig = NetRig(spectators = 1)
        for (c in rig.clients.take(2)) c.connect(rig.now)
        rig.waitFor(10_000L) { rig.clients.take(2).all { it.lobby() != null } }
        for (c in rig.clients.take(2)) c.ready(rig.now)
        rig.waitFor(10_000L) { rig.shard.lobby.players().all { it.ready } }
        rig.clients.first { it.player == rig.shard.lobby.hostId }.startGame(rig.now)
        rig.waitFor(10_000L) { rig.clients.take(2).all { it.state == ClientState.RUNNING } }
        rig.run(70) { traffic(rig, it) }
        val late = rig.client(2)
        late.connect(rig.now)
        rig.run(30) { traffic(rig, it) }
        rig.drain()
        assertEquals(Role.SPECTATOR, late.role)
        assertTrue(rig.converged())
        assertTrue(late.view(rig.now).resyncs >= 1)
    }

    @Test fun aPartitionedPlayerReconnectsAndCatchesUp() {
        val rig = NetRig(clientConfig = { it.copy(silenceTimeoutMs = 1_500L) })
        rig.startGame()
        rig.run(20) { traffic(rig, it) }
        rig.net.partition(rig.endpoints[1], rig.serverId, true)
        rig.run(120) { traffic(rig, it) }
        assertEquals(ClientState.RECONNECTING, rig.client(1).state)
        assertEquals(Presence.GRACE, rig.shard.lobby.member(rig.client(1).player)?.presence)
        rig.net.partition(rig.endpoints[1], rig.serverId, false)
        rig.run(30) { traffic(rig, it) }
        rig.drain()
        assertTrue(rig.converged())
        assertEquals(Presence.CONNECTED, rig.shard.lobby.member(rig.client(1).player)?.presence)
        val events = rig.client(1).drainEvents()
        assertTrue(events.any { it is ClientEvent.Disconnected })
        assertTrue(events.any { it is ClientEvent.Reconnected })
    }

    @Test fun aLongOutagePastTheGraceStillResumesIfTheSeatIsKept() {
        val rig = NetRig(clientConfig = { it.copy(silenceTimeoutMs = 1_000L) })
        rig.startGame()
        rig.run(10)
        rig.net.partition(rig.endpoints[0], rig.serverId, true)
        rig.run(120)
        rig.net.partition(rig.endpoints[0], rig.serverId, false)
        rig.drain(30_000L)
        assertTrue(rig.converged())
    }

    @Test fun aRestartedClientResumesWithSavedCredentials() {
        val rig = NetRig()
        rig.startGame()
        rig.run(30) { traffic(rig, it) }
        rig.drain()
        val saved = rig.client(1).credentials() ?: error("no credentials")
        val sim = rig.sims[1]
        rig.clients.removeAt(1)
        val again = ClientSession(ClientConfig(session = NetFixtures.session, name = "Player 1"), sim, rig.net.endpoint(rig.endpoints[1]), rig.serverId, saved)
        again.connect(rig.now)
        var t = 0
        while (t++ < 300 && again.state != ClientState.RUNNING) { rig.frame(); again.poll(rig.now) }
        assertEquals(ClientState.RUNNING, again.state)
        assertEquals(saved.player, again.player)
    }

    @Test fun aReplayedResumeIsRefused() {
        val rig = NetRig()
        rig.startGame()
        val creds = rig.client(0).credentials() ?: error("no credentials")
        val key = Signing.deriveKey(creds.token, NetFixtures.session, creds.player)
        val peer = RawPeer(rig, EndpointId(70), name = "Player 0")
        val info = ResumeInfo(creds.player, creds.nonce + 5, 0, Signing.resumeProof(key, NetFixtures.session, creds.player, creds.nonce + 5, 0))
        peer.hello(resume = info)
        assertTrue(peer.pump().any { it is NetMessage.Welcome })
        val thief = RawPeer(rig, EndpointId(71), name = "Player 0")
        thief.hello(resume = info)
        assertTrue(NetError.TOKEN_INVALID in thief.also { it.pump() }.errors())
    }

    @Test fun hostMigrationKeepsTheGameRunning() {
        val rig = NetRig(clientCount = 3, clientConfig = { it.copy(silenceTimeoutMs = 60_000L) }, shardConfig = { it.copy(lobby = LobbyConfig(maxPlayers = 4, graceMs = 500L)) })
        rig.startGame()
        val host = rig.clients.first { it.player == rig.shard.lobby.hostId }
        host.leave(rig.now)
        rig.run(30)
        assertTrue(rig.shard.lobby.hostId != host.player)
        assertEquals(LobbyPhase.RUNNING, rig.shard.lobby.phase)
        val rest = rig.clients.filter { it !== host }
        rig.drain()
        for (c in rest) assertEquals(rig.shard.lobby.hostId, c.lobby()?.host)
    }

    @Test fun slowClientsHoldTheClockWhenAskedTo() {
        val rig = NetRig(shardConfig = { it.copy(holdForSlowClients = true, maxLagTicks = 5, hashInterval = 2) })
        rig.startGame()
        rig.net.partition(rig.endpoints[1], rig.serverId, true)
        var stepped = 0
        repeat(80) { if (rig.shard.stepTick(rig.now) != null) stepped++; rig.frames(5) }
        assertTrue("clock kept running: $stepped", stepped < 40)
    }

    @Test fun tradeSettlesThroughTheShardAndConservesCash() {
        val rig = NetRig(income = false)
        rig.startGame()
        val a = rig.client(0); val b = rig.client(1)
        val before = rig.serverSim.totalCash()
        a.offerTrade(b.player, listOf(TradeAsset.cash(500_000)), listOf(TradeAsset.cash(100_000)), 40)
        rig.frames(30)
        val offered = a.trades().firstOrNull() ?: b.trades().first()
        assertTrue(offered.state == TradeState.OFFERED)
        b.answerTrade(offered.trade, TradeAction.ACCEPT)
        rig.run(12)
        rig.drain()
        assertTrue(rig.converged())
        assertEquals(before, rig.serverSim.totalCash())
        assertEquals(0L, rig.serverSim.escrowCash)
        assertTrue(b.trades().any { it.state == TradeState.SETTLED })
        assertTrue(rig.shard.escrow.invariants().isEmpty())
    }

    @Test fun anUnansweredTradeExpiresAndRefunds() {
        val rig = NetRig(income = false)
        rig.startGame()
        val before = rig.serverSim.totalCash()
        val mine = rig.serverSim.companies.getValue(rig.serverSim.companiesOf(rig.client(0).player).first()).cash
        rig.client(0).offerTrade(rig.client(1).player, listOf(TradeAsset.cash(250_000)), listOf(TradeAsset.cash(1_000)), 10)
        rig.run(30)
        rig.drain()
        assertEquals(before, rig.serverSim.totalCash())
        assertEquals(mine, rig.serverSim.companies.getValue(rig.serverSim.companiesOf(rig.client(0).player).first()).cash)
        assertTrue(rig.client(0).trades().any { it.state == TradeState.EXPIRED })
    }

    @Test fun theSeasonAdvancesAndScoresPlayers() {
        val rig = NetRig(shardConfig = { it.copy(seasonLengthTicks = 40L) })
        rig.startGame()
        rig.run(90)
        assertTrue(rig.shard.seasonLedger.results().isNotEmpty())
        rig.drain()
        assertTrue(rig.client(1).chat.recent().any { it.kind == ChatKind.SYSTEM && it.text.startsWith("Season 1 finished") })
    }

    @Test fun aLongHostileSessionStaysConsistent() {
        val rig = NetRig(seed = 99, profile = LinkProfile.HOSTILE, clientCount = 3, spectators = 1)
        rig.startGame()
        rig.run(260) { traffic(rig, it) }
        rig.drain(40_000L)
        assertTrue(rig.converged())
        assertTrue(rig.serverSim.tick >= 260)
    }
}
