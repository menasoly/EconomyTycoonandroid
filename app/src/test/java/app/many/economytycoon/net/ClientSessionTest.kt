package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientSessionTest {
    private val serverId = EndpointId(1)

    /** A client talking to a script played by the test instead of a real server. */
    private class Bench(config: ClientConfig = ClientConfig(session = NetFixtures.session, name = "Ada Works"), credentials: Credentials? = null) {
        val net = MemoryNetwork(1)
        val server: Transport = net.endpoint(EndpointId(1))
        val sim = ToySimulation(income = false)
        val client = ClientSession(config, sim, net.endpoint(EndpointId(2)), EndpointId(1), credentials)
        var now = 0L
        var serverSeq = 0L
        var chain = 0L
        val sent = ArrayList<Packet>()

        fun tell(m: NetMessage) { server.send(EndpointId(2), MessageCodec.encode(++serverSeq, m)) }
        fun step(ms: Long = 10L) { now += ms; net.advanceTo(now); client.poll(now); net.advanceTo(now); collect() }
        fun steps(n: Int, ms: Long = 10L) { repeat(n) { step(ms) } }
        fun collect() { for (d in server.receive()) { val dec = MessageCodec.decode(d.bytes); if (dec is Decoded.Ok) sent += dec.packet } }
        fun messages(): List<NetMessage> = sent.map { it.message }
        fun last(): NetMessage? = sent.lastOrNull()?.message
        inline fun <reified T : NetMessage> all(): List<T> = sent.map { it.message }.filterIsInstance<T>()

        fun welcome(player: Long = 1, tick: Long = 0, snapshot: Boolean = false, chain: Long = 0, role: Role = Role.PLAYER, token: ByteArray = NetFixtures.bytes(32, 3)) = NetMessage.Welcome(
            1, Capabilities.ALL, PlayerId(player), role, NetFixtures.session, token, tick, 3, 5, snapshot, 9, chain,
        )

        fun members(vararg names: String) = names.mapIndexed { i, n -> MemberInfo(PlayerId(i + 1L), n, Role.PLAYER, i, true, Presence.CONNECTED) }

        fun lobby(version: Long, phase: LobbyPhase, members: List<MemberInfo>) = NetMessage.LobbyState(version, phase, PlayerId(1), members)

        fun commit(tick: Long, entries: List<CommitEntry> = emptyList(), transfers: List<Transfer> = emptyList()): NetMessage.Commit {
            val c = CommitChain.next(chain, tick, entries, transfers)
            chain = c
            return NetMessage.Commit(tick, entries, transfers, c)
        }

        /** Gets the client through hello, welcome and the start of a two-player game. */
        fun running() {
            client.connect(now); step()
            tell(welcome()); step()
            tell(lobby(1, LobbyPhase.OPEN, members("Ada Works", "Bob Works"))); step()
            tell(lobby(2, LobbyPhase.RUNNING, members("Ada Works", "Bob Works"))); step()
        }
    }

    @Test fun connectingSendsAHelloWithTheGameFingerprint() {
        val b = Bench()
        b.client.connect(b.now); b.step()
        val hello = b.all<NetMessage.Hello>().single()
        assertEquals("Ada Works", hello.name)
        assertEquals("toy:1", hello.content)
        assertEquals(NetFixtures.session, hello.session)
        assertNull(hello.resume)
        assertEquals(ClientState.CONNECTING, b.client.state)
    }

    @Test fun aWelcomeMovesTheClientIntoTheLobby() {
        val b = Bench()
        b.client.connect(b.now); b.step()
        b.tell(b.welcome(player = 7)); b.step()
        assertEquals(ClientState.LOBBY, b.client.state)
        assertEquals(PlayerId(7), b.client.player)
        assertTrue(b.client.drainEvents().contains(ClientEvent.Joined(PlayerId(7), Role.PLAYER)))
        val creds = assertNotNull(b.client.credentials()).let { b.client.credentials() ?: error("none") }
        assertEquals(PlayerId(7), creds.player)
    }

    @Test fun aWelcomeForAnotherSessionIsIgnored() {
        val b = Bench()
        b.client.connect(b.now); b.step()
        b.tell(NetMessage.Welcome(1, 0, PlayerId(7), Role.PLAYER, SessionId(5), NetFixtures.bytes(32), 0, 3, 5, false, 0, 0)); b.step()
        assertEquals(ClientState.CONNECTING, b.client.state)
    }

    @Test fun aSecondWelcomeChangesNothing() {
        val b = Bench()
        b.running()
        b.tell(b.welcome(player = 9)); b.step()
        assertEquals(PlayerId(1), b.client.player)
    }

    @Test fun aFatalErrorWhileJoiningEndsTheAttempt() {
        val b = Bench()
        b.client.connect(b.now); b.step()
        b.tell(NetMessage.Error(NetError.SESSION_FULL, "no room")); b.step()
        assertEquals(ClientState.FAILED, b.client.state)
        assertTrue(b.client.drainEvents().any { it is ClientEvent.Refused && it.error == NetError.SESSION_FULL })
    }

    @Test fun helloIsRepeatedUntilTheHostAnswersThenTheClientGivesUp() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", helloRetryMs = 100, maxHelloAttempts = 3))
        b.client.connect(b.now)
        b.steps(40, 20)
        assertEquals(3, b.all<NetMessage.Hello>().size)
        assertEquals(ClientState.FAILED, b.client.state)
        // retransmissions are the same bytes, so the host sees one hello however many arrive
        assertEquals(1, b.sent.map { it.seq }.distinct().size)
    }

    @Test fun theGameStartsWhenTheLobbyReportsItRunning() {
        val b = Bench()
        b.running()
        assertEquals(ClientState.RUNNING, b.client.state)
        assertEquals(2, b.sim.companies.size)
        assertEquals(setOf("Ada Works", "Bob Works"), b.sim.companies.values.map { it.name }.toSet())
        assertTrue(b.client.drainEvents().any { it is ClientEvent.GameStarted })
    }

    @Test fun everyLobbyStateIsAcknowledgedEvenWhenOld() {
        val b = Bench()
        b.running()
        val before = b.all<NetMessage.LobbyRequest>().count { it.action == LobbyAction.ACK_STATE }
        b.tell(b.lobby(1, LobbyPhase.OPEN, b.members("Ada Works"))); b.step()
        assertEquals(before + 1, b.all<NetMessage.LobbyRequest>().count { it.action == LobbyAction.ACK_STATE })
        assertEquals(2L, b.client.lobby()?.version)
    }

    @Test fun commitsApplyInOrderEvenWhenTheyArriveShuffled() {
        val b = Bench()
        b.running()
        val commits = (0L..5L).map { b.commit(it) }
        for (i in listOf(3, 0, 5, 1, 2, 4, 2, 0)) b.tell(commits[i])
        b.steps(5)
        assertEquals(6L, b.sim.tick)
        assertEquals(ClientState.RUNNING, b.client.state)
    }

    @Test fun aCommitWithABrokenChainStartsAResync() {
        val b = Bench()
        b.running()
        val good = b.commit(0)
        b.tell(NetMessage.Commit(0, emptyList(), emptyList(), good.chain + 1)); b.step()
        assertEquals(ClientState.SYNCING, b.client.state)
        assertTrue(b.all<NetMessage.SnapshotRequest>().isNotEmpty())
        assertTrue(b.client.drainEvents().any { it is ClientEvent.Desync })
    }

    @Test fun aMatchingCheckpointKeepsTheClientRunning() {
        val b = Bench()
        b.running()
        for (t in 0L..4L) b.tell(b.commit(t))
        b.steps(5)
        assertEquals(5L, b.sim.tick)
        assertEquals(listOf(0L, 5L), b.all<NetMessage.HashReport>().map { it.tick })
        b.tell(NetMessage.HashCheckpoint(5, b.sim.stateHash())); b.step()
        assertEquals(ClientState.RUNNING, b.client.state)
    }

    @Test fun aDifferentCheckpointStartsAResync() {
        val b = Bench()
        b.running()
        for (t in 0L..4L) b.tell(b.commit(t))
        b.steps(5)
        b.tell(NetMessage.HashCheckpoint(5, NetFixtures.hash(9))); b.step()
        assertEquals(ClientState.SYNCING, b.client.state)
    }

    @Test fun aStaleDesyncNoticeIsIgnored() {
        val b = Bench()
        b.running()
        for (t in 0L..4L) b.tell(b.commit(t))
        b.steps(5)
        b.tell(NetMessage.DesyncNotice(5, b.sim.stateHash())); b.step()
        assertEquals(ClientState.RUNNING, b.client.state)
        b.tell(NetMessage.DesyncNotice(5, NetFixtures.hash(4))); b.step()
        assertEquals(ClientState.SYNCING, b.client.state)
    }

    @Test fun aGapInTheCommitsIsRequestedAgain() {
        val b = Bench()
        b.running()
        val c = (0L..4L).map { b.commit(it) }
        b.tell(c[0]); b.tell(c[3]); b.tell(c[4]); b.steps(40, 10)
        val resend = b.all<NetMessage.Resend>()
        assertTrue(resend.isNotEmpty())
        assertEquals(1L, resend.first().fromTick)
        assertTrue(resend.first().count >= 2)
        b.tell(c[1]); b.tell(c[2]); b.steps(5)
        assertEquals(5L, b.sim.tick)
    }

    @Test fun submittingNeedsARunningGameAndAPlayerSeat() {
        val b = Bench()
        assertNull(b.client.submit(ToySimulation.rename(1, "New Name"), b.now))
        b.running()
        assertNotNull(b.client.submit(ToySimulation.rename(1, "New Name"), b.now))
        val s = Bench()
        s.client.connect(s.now); s.step()
        s.tell(s.welcome(role = Role.SPECTATOR)); s.step()
        s.tell(s.lobby(1, LobbyPhase.RUNNING, listOf(MemberInfo(PlayerId(1), "Ada Works", Role.SPECTATOR, -1, false, Presence.CONNECTED)))); s.step()
        assertNull(s.client.submit(ToySimulation.rename(1, "New Name"), s.now))
    }

    @Test fun submitsAreSignedForTheTargetTickAndPlayer() {
        val b = Bench()
        b.running()
        val seq = b.client.submit(ToySimulation.budget(1, 50), b.now) ?: error("not sent")
        b.step()
        val s = b.all<NetMessage.Submit>().single()
        assertEquals(seq, s.seq)
        assertEquals(3L, s.targetTick)
        val key = Signing.deriveKey(NetFixtures.bytes(32, 3), NetFixtures.session, PlayerId(1))
        assertTrue(Hmac.constantTimeEquals(Signing.commandMac(key, NetFixtures.session, PlayerId(1), s.seq, s.targetTick, s.payload), s.mac))
    }

    @Test fun anAcknowledgedSubmitIsDoneAndNotRepeated() {
        val b = Bench()
        b.running()
        val seq = b.client.submit(ToySimulation.budget(1, 50), b.now) ?: error("not sent")
        b.step()
        b.tell(NetMessage.Ack(seq, 3)); b.steps(80)
        assertEquals(1, b.all<NetMessage.Submit>().size)
        assertTrue(b.client.drainEvents().contains(ClientEvent.Accepted(seq, 3)))
        assertEquals(0, b.client.view(b.now).inFlight)
    }

    @Test fun anUnansweredSubmitIsRepeatedIdenticallyThenExpires() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", submitRetryMs = 100, maxSubmitAttempts = 3, silenceTimeoutMs = 100_000))
        b.running()
        val seq = b.client.submit(ToySimulation.budget(1, 50), b.now) ?: error("not sent")
        b.steps(60, 10)
        val submits = b.sent.filter { it.message is NetMessage.Submit }
        assertEquals(3, submits.size)
        assertEquals(1, submits.map { it.seq }.distinct().size)
        assertTrue(b.client.drainEvents().contains(ClientEvent.Expired(seq)))
    }

    @Test fun aTooLateRejectIsRetriedWithALaterTick() {
        val b = Bench()
        b.running()
        val seq = b.client.submit(ToySimulation.budget(1, 50), b.now) ?: error("not sent")
        b.step()
        b.tell(NetMessage.Reject(seq, NetError.TOO_LATE, "too_late", 10)); b.step()
        val submits = b.all<NetMessage.Submit>()
        assertEquals(2, submits.size)
        assertTrue(submits[1].seq != submits[0].seq)
        assertTrue(submits[1].targetTick > 10)
        assertTrue(submits[1].payload.contentEquals(submits[0].payload))
        assertEquals(1, b.client.view(b.now).inFlight)
    }

    @Test fun otherRejectsAreReportedAndForgotten() {
        val b = Bench()
        b.running()
        val seq = b.client.submit(ToySimulation.budget(1, 50), b.now) ?: error("not sent")
        b.step()
        b.tell(NetMessage.Reject(seq, NetError.NOT_OWNER, "not_owner", 0)); b.step()
        assertEquals(1, b.all<NetMessage.Submit>().size)
        assertTrue(b.client.drainEvents().contains(ClientEvent.Rejected(seq, NetError.NOT_OWNER, "not_owner")))
    }

    @Test fun theNumberOfCommandsInFlightIsCapped() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", maxInFlight = 3))
        b.running()
        val results = (1..5).map { b.client.submit(ToySimulation.budget(1, it.toLong()), b.now) }
        assertEquals(3, results.count { it != null })
    }

    @Test fun serverPingsAreAnsweredWithTheClientsTick() {
        val b = Bench()
        b.running()
        for (t in 0L..2L) b.tell(b.commit(t))
        b.steps(3)
        b.tell(NetMessage.Ping(41, 500)); b.step()
        val pong = b.all<NetMessage.Pong>().last()
        assertEquals(41L, pong.nonce)
        assertEquals(500L, pong.sentAt)
        assertEquals(3L, pong.serverTick)
    }

    private fun snapshotOf(sim: ToySimulation, chain: Long): SnapshotBundle = SnapshotBundle(1, sim.tick, sim.snapshot(), sim.stateHash(), chain, chunkSize = 4_096)

    private fun syncing(): Pair<Bench, ToySimulation> {
        val b = Bench()
        b.running()
        val source = ToySimulation(income = false)
        source.seat(PlayerId(1), "Ada Works"); source.seat(PlayerId(2), "Bob Works")
        repeat(12) { source.advance() }
        b.tell(NetMessage.Commit(0, emptyList(), emptyList(), 5)); b.step()
        assertEquals(ClientState.SYNCING, b.client.state)
        return b to source
    }

    @Test fun aSnapshotIsReassembledVerifiedAndAdopted() {
        val (b, source) = syncing()
        val bundle = snapshotOf(source, 0x1234)
        b.tell(bundle.header())
        for (i in 0 until bundle.chunkCount) b.tell(bundle.chunk(i))
        b.steps(5)
        assertEquals(ClientState.RUNNING, b.client.state)
        assertEquals(12L, b.sim.tick)
        assertTrue(b.sim.stateHash().contentEquals(source.stateHash()))
        assertTrue(b.all<NetMessage.SnapshotAck>().last().complete)
        assertTrue(b.client.drainEvents().any { it is ClientEvent.Resynced })
        assertEquals(1, b.client.view(b.now).resyncs)
    }

    @Test fun aDamagedChunkIsNotAcceptedAndTheTransferWaitsForTheRetransmission() {
        val (b, source) = syncing()
        val bundle = snapshotOf(source, 1)
        b.tell(bundle.header())
        val bad = bundle.chunk(0)
        b.tell(NetMessage.SnapshotChunk(bad.snapshotId, 0, bad.data.copyOf().also { it[0] = (it[0] + 1).toByte() }, bad.crc)); b.step()
        assertEquals(ClientState.SYNCING, b.client.state)
        for (i in 0 until bundle.chunkCount) b.tell(bundle.chunk(i))
        b.steps(5)
        assertEquals(ClientState.RUNNING, b.client.state)
    }

    @Test fun aStalledTransferAsksToResumeFromTheFirstMissingChunk() {
        val (b, source) = syncing()
        val bundle = snapshotOf(source, 1)
        assertTrue(bundle.chunkCount >= 3)
        b.tell(bundle.header()); b.tell(bundle.chunk(0)); b.tell(bundle.chunk(1)); b.steps(5)
        b.steps(80, 20)
        val resume = b.all<NetMessage.SnapshotRequest>().last()
        assertEquals(bundle.id, resume.snapshotId)
        assertEquals(2, resume.resumeChunk)
    }

    @Test fun aSnapshotThatDoesNotMatchItsHashIsRefusedAndAskedForAgain() {
        val (b, source) = syncing()
        val good = snapshotOf(source, 1)
        val lying = SnapshotBundle(1, good.tick, good.bytes, NetFixtures.hash(8), 1, chunkSize = 4_096)
        val before = b.all<NetMessage.SnapshotRequest>().size
        b.tell(lying.header())
        for (i in 0 until lying.chunkCount) b.tell(lying.chunk(i))
        b.steps(5)
        assertEquals(ClientState.SYNCING, b.client.state)
        assertTrue(b.all<NetMessage.SnapshotRequest>().size > before)
    }

    @Test fun anEndlesslyBadSnapshotEventuallyFailsTheClient() {
        val (b, source) = syncing()
        val good = snapshotOf(source, 1)
        val lying = SnapshotBundle(1, good.tick, good.bytes, NetFixtures.hash(8), 1, chunkSize = 4_096)
        repeat(8) {
            b.tell(lying.header())
            for (i in 0 until lying.chunkCount) b.tell(lying.chunk(i))
            b.steps(3)
        }
        assertEquals(ClientState.FAILED, b.client.state)
    }

    @Test fun silenceFromTheHostLeadsToAResumeAttempt() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", silenceTimeoutMs = 500, helloRetryMs = 200))
        b.running()
        b.steps(100, 10)
        assertEquals(ClientState.RECONNECTING, b.client.state)
        val resume = b.all<NetMessage.Hello>().last().resume ?: error("no resume info")
        assertEquals(PlayerId(1), resume.player)
        val key = Signing.deriveKey(NetFixtures.bytes(32, 3), NetFixtures.session, PlayerId(1))
        assertTrue(Hmac.constantTimeEquals(Signing.resumeProof(key, NetFixtures.session, PlayerId(1), resume.nonce, resume.lastTick), resume.proof))
        assertTrue(b.client.drainEvents().any { it is ClientEvent.Disconnected })
    }

    @Test fun resumeNoncesOnlyEverGrow() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", silenceTimeoutMs = 300, helloRetryMs = 100, reconnectWindowMs = 100_000))
        b.running()
        b.steps(200, 10)
        val nonces = b.all<NetMessage.Hello>().mapNotNull { it.resume?.nonce }
        assertTrue(nonces.size >= 3)
        assertEquals(nonces.sorted(), nonces)
        assertEquals(nonces.size, nonces.toSet().size)
    }

    @Test fun aWelcomeAfterResumingContinuesTheGameWithoutASnapshot() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", silenceTimeoutMs = 300, helloRetryMs = 100))
        b.running()
        for (t in 0L..3L) b.tell(b.commit(t))
        b.steps(100, 10)
        assertEquals(ClientState.RECONNECTING, b.client.state)
        b.tell(b.welcome(player = 1, tick = 4, chain = b.chain, token = ByteArray(32))); b.step()
        assertEquals(ClientState.RUNNING, b.client.state)
        assertTrue(b.client.drainEvents().any { it is ClientEvent.Reconnected })
        assertTrue(b.all<NetMessage.SnapshotRequest>().isEmpty())
        val c = b.commit(4)
        b.tell(c); b.step()
        assertEquals(5L, b.sim.tick)
    }

    @Test fun aWelcomeThatDisagreesWithTheLocalChainForcesASnapshot() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", silenceTimeoutMs = 300, helloRetryMs = 100))
        b.running()
        for (t in 0L..3L) b.tell(b.commit(t))
        b.steps(100, 10)
        b.tell(b.welcome(player = 1, tick = 4, chain = b.chain + 1, token = ByteArray(32))); b.step()
        assertEquals(ClientState.SYNCING, b.client.state)
        assertEquals(SnapshotReason.RECONNECT, b.all<NetMessage.SnapshotRequest>().last().reason)
    }

    @Test fun credentialsFromAnEarlierRunStartWithAResumeHello() {
        val creds = Credentials(PlayerId(4), NetFixtures.bytes(32, 8), 20)
        val b = Bench(credentials = creds)
        b.client.connect(b.now); b.step()
        val hello = b.all<NetMessage.Hello>().single()
        assertEquals(PlayerId(4), hello.resume?.player)
        assertEquals(21L, hello.resume?.nonce)
        assertEquals(ClientState.RECONNECTING, b.client.state)
    }

    @Test fun chatAndTradeNoticesAreRecorded() {
        val b = Bench()
        b.running()
        b.tell(NetMessage.ChatBroadcast(PlayerId(2), ChatKind.TEXT, 0, "hi there", 3)); b.step()
        assertEquals("hi there", b.client.chat.recent().single().text)
        val notice = NetMessage.TradeNotice(TradeId(1), TradeState.OFFERED, PlayerId(2), PlayerId(1), listOf(TradeAsset.cash(5)), emptyList(), 50)
        b.tell(notice); b.step()
        assertEquals(listOf(notice), b.client.trades())
        assertEquals(1, b.client.view(b.now).openTrades)
        b.tell(NetMessage.TradeNotice(TradeId(1), TradeState.SETTLED, PlayerId(2), PlayerId(1), listOf(TradeAsset.cash(5)), emptyList(), 50)); b.step()
        assertEquals(0, b.client.view(b.now).openTrades)
    }

    @Test fun lobbyRequestsAreRepeatedUntilTheHostConfirmsThem() {
        val b = Bench(ClientConfig(session = NetFixtures.session, name = "Ada Works", resendAfterMs = 100))
        b.client.connect(b.now); b.step()
        b.tell(b.welcome()); b.step()
        b.client.ready(b.now)
        b.steps(40, 10)
        val ready = b.sent.filter { (it.message as? NetMessage.LobbyRequest)?.action == LobbyAction.READY }
        assertTrue(ready.size >= 3)
        assertEquals(1, ready.map { it.seq }.distinct().size)
        b.tell(NetMessage.LobbyReply(ready.first().seq, 3)); b.steps(2)
        val count = b.sent.count { (it.message as? NetMessage.LobbyRequest)?.action == LobbyAction.READY }
        b.steps(40, 10)
        assertEquals(count, b.sent.count { (it.message as? NetMessage.LobbyRequest)?.action == LobbyAction.READY })
    }

    @Test fun leavingClosesTheSessionLocally() {
        val b = Bench()
        b.running()
        b.client.leave(b.now); b.step()
        assertEquals(ClientState.CLOSED, b.client.state)
        assertTrue(b.all<NetMessage.LobbyRequest>().any { it.action == LobbyAction.LEAVE })
        b.tell(b.commit(0)); b.step()
        assertEquals(0L, b.sim.tick)
    }

    @Test fun byeFromTheHostClosesTheClient() {
        val b = Bench()
        b.running()
        b.tell(NetMessage.Bye(ByeReason.KICKED)); b.step()
        assertEquals(ClientState.CLOSED, b.client.state)
        assertTrue(b.client.drainEvents().contains(ClientEvent.Closed(ByeReason.KICKED)))
    }

    @Test fun datagramsFromAnyoneButTheHostAreIgnored() {
        val b = Bench()
        b.client.connect(b.now); b.step()
        val intruder = b.net.endpoint(EndpointId(66))
        intruder.send(EndpointId(2), MessageCodec.encode(1, b.welcome(player = 66)))
        b.step()
        assertEquals(ClientState.CONNECTING, b.client.state)
    }

    @Test fun garbageNeverBreaksTheClient() {
        val b = Bench()
        b.running()
        val intruder = b.net.endpoint(EndpointId(1).let { EndpointId(1) })
        for (g in NetFixtures.garbage(5, 200)) intruder.send(EndpointId(2), g)
        b.steps(5)
        assertEquals(ClientState.RUNNING, b.client.state)
        assertFalse(b.client.drainEvents().any { it is ClientEvent.Desync })
    }
}
