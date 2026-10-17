package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetShardTest {
    private fun started(clients: Int = 2, config: (ShardConfig) -> ShardConfig = { it }): NetRig {
        val rig = NetRig(clientCount = clients, shardConfig = config, income = false)
        rig.startGame()
        return rig
    }

    @Test fun aStrangerWhoHasNotSaidHelloIsToldToJoinFirst() {
        val rig = NetRig(clientCount = 0)
        val peer = RawPeer(rig, EndpointId(50))
        peer.send(NetMessage.Ping(1, 0))
        peer.pump()
        assertTrue(NetError.NOT_JOINED in peer.errors())
    }

    @Test fun anOldProtocolVersionIsRefusedPolitely() {
        val rig = NetRig(clientCount = 0)
        val peer = RawPeer(rig, EndpointId(50))
        peer.sendRaw(MessageCodec.encode(1, NetFixtures.samples().first(), 1).also { it[2] = 99 })
        peer.pump()
        assertTrue(peer.received.none { it is NetMessage.Welcome })
    }

    @Test fun aWrongContentFingerprintIsRefused() {
        val rig = NetRig(clientCount = 0)
        val peer = RawPeer(rig, EndpointId(50))
        peer.hello(content = "other:9")
        peer.pump()
        assertTrue(NetError.CONTENT_MISMATCH in peer.errors())
    }

    @Test fun aDuplicateNameIsRefused() {
        val rig = NetRig(clientCount = 0)
        assertTrue(RawPeer(rig, EndpointId(50), name = "Same Name").join())
        val second = RawPeer(rig, EndpointId(51), name = "same name")
        assertFalse(second.join())
        assertTrue(NetError.NAME_TAKEN in second.errors())
    }

    @Test fun theSessionRefusesPlayersBeyondItsCapacity() {
        val rig = NetRig(clientCount = 0, shardConfig = { it.copy(lobby = LobbyConfig(maxPlayers = 2, maxSpectators = 0)) })
        assertTrue(RawPeer(rig, EndpointId(50), name = "One").join())
        assertTrue(RawPeer(rig, EndpointId(51), name = "Two").join())
        val third = RawPeer(rig, EndpointId(52), name = "Three")
        assertFalse(third.join())
        assertTrue(NetError.SESSION_FULL in third.errors())
    }

    @Test fun aForgedSignatureIsRejectedAndNeverCommitted() {
        val rig = started()
        val peer = RawPeer(rig, EndpointId(60), name = "Mallory")
        assertTrue(peer.join())
        // Mallory joined after the start, so is a spectator; use a real player's identity from another angle:
        val victim = rig.client(0)
        val company = rig.serverSim.companiesOf(victim.player).first()
        val bogus = peer.signedSubmit(1, rig.serverSim.tick + 3, CommandPayload.encode(ToySimulation.rename(company, "Stolen Name")))
        peer.send(bogus)
        peer.pump()
        assertTrue(peer.rejects().isNotEmpty())
        rig.run(8)
        assertFalse(rig.serverSim.companies[company]?.name == "Stolen Name")
    }

    @Test fun spectatorsCannotSubmitCommands() {
        val rig = started()
        val peer = RawPeer(rig, EndpointId(60), name = "Looker")
        assertTrue(peer.join())
        val company = rig.serverSim.companiesOf(rig.client(0).player).first()
        peer.submit(ToySimulation.rename(company, "Spectator Move"), rig.serverSim.tick + 3)
        peer.pump()
        assertTrue(peer.rejects().any { it.error == NetError.SPECTATOR })
    }

    @Test fun aCommandForSomeoneElsesCompanyIsRefusedAndCounted() {
        val rig = started()
        val a = rig.client(0)
        val other = rig.serverSim.companiesOf(rig.client(1).player).first()
        val before = rig.shard.metrics().commandsRefused
        a.submit(ToySimulation.rename(other, "Hostile Takeover"), rig.now)
        rig.frames(30)
        assertTrue(rig.shard.metrics().commandsRefused > before)
        assertTrue(a.drainEvents().any { it is ClientEvent.Rejected && it.error == NetError.NOT_OWNER })
        assertTrue(rig.shard.antiCheat.score(a.player) > 0)
    }

    @Test fun aRepeatedCommandSequenceCommitsOnlyOnce() {
        val rig = NetRig(clientCount = 0, income = false)
        val peer = RawPeer(rig, EndpointId(60), name = "Solo")
        assertTrue(peer.join())
        peer.send(NetMessage.LobbyRequest(LobbyAction.READY, PlayerId.SERVER, 0))
        peer.pump(5)
        peer.send(NetMessage.LobbyRequest(LobbyAction.START, PlayerId.SERVER, 0))
        peer.pump(10)
        val company = rig.serverSim.companiesOf(peer.player).first()
        val target = rig.serverSim.tick + 4
        val payload = CommandPayload.encode(ToySimulation.budget(company, 777))
        repeat(3) { peer.send(peer.signedSubmit(1, target, payload)) }
        peer.pump(5)
        rig.run(8)
        assertEquals(1, rig.shard.log.all().sumOf { c -> c.entries.count { it.player == peer.player } })
        assertTrue(peer.received.filterIsInstance<NetMessage.Ack>().isNotEmpty())
    }

    @Test fun anOversizedFrameFromAJoinedPlayerIsNotAnException() {
        val rig = started()
        val peer = RawPeer(rig, EndpointId(60))
        assertTrue(peer.join())
        peer.sendRaw(ByteArray(NetLimits.MAX_FRAME_BYTES + 10))
        peer.pump()
        assertEquals(1, rig.shard.antiCheat.count(peer.player, Offense.OVERSIZED))
    }

    @Test fun garbageFromAJoinedPlayerNeverBreaksTheServer() {
        val rig = started()
        val peer = RawPeer(rig, EndpointId(60))
        assertTrue(peer.join())
        for (frame in NetFixtures.garbage(7, 150)) peer.sendRaw(frame)
        peer.pump(40)
        rig.run(10)
        rig.drain()
        assertTrue(rig.converged())
    }

    @Test fun aFloodIsDroppedWithoutHarmingOtherPlayers() {
        val rig = started { it.copy(inboundBurst = 20, inboundPerSecond = 20) }
        val peer = RawPeer(rig, EndpointId(60))
        assertTrue(peer.join())
        repeat(500) { peer.send(NetMessage.Ping(it.toLong(), 0)) }
        peer.pump(20)
        assertTrue(rig.shard.metrics().floodDropped > 100)
        rig.run(20)
        rig.drain()
        assertTrue(rig.converged())
    }

    @Test fun repeatedSignatureFailuresGetThePeerRemoved() {
        val lobbyRig = NetRig(clientCount = 0, income = false)
        val cheat = RawPeer(lobbyRig, EndpointId(61), name = "Cheater")
        assertTrue(cheat.join())
        cheat.send(NetMessage.LobbyRequest(LobbyAction.READY, PlayerId.SERVER, 0))
        cheat.pump(5)
        cheat.send(NetMessage.LobbyRequest(LobbyAction.START, PlayerId.SERVER, 0))
        cheat.pump(15)
        assertEquals(LobbyPhase.RUNNING, lobbyRig.shard.lobby.phase)
        var seq = 0L
        repeat(60) {
            cheat.send(cheat.signedSubmit(++seq, lobbyRig.serverSim.tick + 3, CommandPayload.encode(ToySimulation.rename(1, "Cheat Name")), key = ByteArray(32)))
            cheat.pump(1)
        }
        assertTrue(lobbyRig.shard.antiCheat.score(cheat.player) > 0)
        assertTrue(cheat.received.any { it is NetMessage.Bye })
        assertFalse(lobbyRig.shard.isConnected(cheat.player))
        assertTrue(lobbyRig.shard.antiCheat.count(cheat.player, Offense.BAD_SIGNATURE) >= 3)
    }

    @Test fun theHostCanKickAndAKickedPlayerCannotReturn() {
        val rig = NetRig(clientCount = 3, income = false)
        for (c in rig.clients) c.connect(rig.now)
        rig.waitFor(10_000L) { rig.clients.all { it.lobby() != null } }
        val host = rig.clients.first { it.player == rig.shard.lobby.hostId }
        val victim = rig.clients.first { it !== host }
        host.requestLobby(LobbyAction.KICK, rig.now, victim.player)
        rig.waitFor(5_000L) { rig.shard.lobby.member(victim.player) == null }
        assertEquals(null, rig.shard.lobby.member(victim.player))
        rig.settle(500L)
        assertEquals(ClientState.CLOSED, victim.state)
        assertFalse(rig.shard.isConnected(victim.player))
    }

    @Test fun onlyTheHostCanStartTheGame() {
        val rig = NetRig(clientCount = 2, income = false)
        for (c in rig.clients) c.connect(rig.now)
        rig.waitFor(10_000L) { rig.clients.all { it.lobby() != null } }
        for (c in rig.clients) c.ready(rig.now)
        rig.waitFor(5_000L) { rig.shard.lobby.players().all { it.ready } }
        val guest = rig.clients.first { it.player != rig.shard.lobby.hostId }
        guest.startGame(rig.now)
        rig.settle(1_000L)
        assertEquals(LobbyPhase.OPEN, rig.shard.lobby.phase)
        assertTrue(guest.drainEvents().any { it is ClientEvent.ServerError && it.error == NetError.NOT_HOST })
    }

    @Test fun chatReachesEveryoneAndIsRateLimited() {
        val rig = started()
        rig.client(0).say("hello everyone")
        rig.frames(10)
        assertTrue(rig.client(1).chat.recent().any { it.text == "hello everyone" })
        repeat(30) { rig.client(0).say("spam $it") }
        rig.frames(20)
        assertTrue(rig.client(1).chat.size < 30)
    }

    @Test fun shardStatusAndLeaderboardCoverEveryone() {
        val rig = NetRig(clientCount = 2)
        rig.startGame()
        rig.run(40)
        val board = rig.shard.leaderboard()
        assertEquals(2, board.size)
        assertEquals(listOf(1, 2), board.map { it.rank })
        assertEquals(2, rig.shard.status().players.size)
    }

    @Test fun theRecordedSessionReplaysToTheSameStates() {
        val rig = NetRig(clientCount = 2)
        rig.startGame()
        rig.run(60) { i ->
            for ((n, c) in rig.clients.withIndex()) {
                val co = rig.serverSim.companiesOf(c.player).firstOrNull() ?: continue
                if ((i + n) % 5 == 0) c.submit(ToySimulation.budget(co, i * 10L + n), rig.now)
            }
        }
        rig.drain()
        val record = rig.shard.record()
        val verdict = SessionReplay { ToySimulation() }.verify(record)
        assertTrue(verdict.toString(), verdict.ok)
    }
}
