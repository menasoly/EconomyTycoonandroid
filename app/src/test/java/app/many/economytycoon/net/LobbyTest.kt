package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyTest {
    private val p1 = PlayerId(1); private val p2 = PlayerId(2); private val p3 = PlayerId(3); private val p4 = PlayerId(4); private val p5 = PlayerId(5)

    private fun lobby(config: LobbyConfig = LobbyConfig()) = Lobby(config)
    private fun rule(o: Outcome<List<LobbyEvent>>) = (o as Outcome.Rejected).rejection.rule
    private fun events(o: Outcome<List<LobbyEvent>>) = (o as Outcome.Ok).value

    private fun Lobby.add(id: PlayerId, name: String = "Player ${id.value}", spectate: Boolean = false, now: Long = 0): Lobby {
        events(join(id, name, now, spectate)); return this
    }

    private fun ready(l: Lobby): Lobby { l.players().forEach { l.setReady(it.id, true) }; return l }

    // ---- joining ----

    @Test fun firstMemberBecomesHostAndTakesSlotZero() {
        val l = lobby()
        val e = events(l.join(p1, "Ada", 0))
        assertEquals(p1, l.hostId)
        assertTrue(e.contains(LobbyEvent.HostChanged(PlayerId.SERVER, p1)))
        assertEquals(0, l.member(p1)?.slot)
        assertEquals(Role.PLAYER, l.member(p1)?.role)
    }

    @Test fun slotsFillInOrderAndFurtherMembersWatch() {
        val l = lobby(LobbyConfig(maxPlayers = 2))
        l.add(p1).add(p2).add(p3)
        assertEquals(listOf(0, 1), l.players().map { it.slot })
        assertEquals(Role.SPECTATOR, l.member(p3)?.role)
        assertEquals(-1, l.member(p3)?.slot)
    }

    @Test fun fullSessionWithoutSpectatorsRefuses() {
        val l = lobby(LobbyConfig(maxPlayers = 1, allowSpectators = false)).add(p1)
        assertEquals(Rules.LIMIT_EXCEEDED, rule(l.join(p2, "Bob", 0)))
    }

    @Test fun spectatorCapIsEnforced() {
        val l = lobby(LobbyConfig(maxPlayers = 1, maxSpectators = 1)).add(p1).add(p2)
        assertEquals(Rules.LIMIT_EXCEEDED, rule(l.join(p3, "Cy", 0)))
    }

    @Test fun duplicateIdAndNameAreRefused() {
        val l = lobby().add(p1, "Ada")
        assertEquals(Rules.DUPLICATE, rule(l.join(p1, "Other", 0)))
        assertEquals(Rules.NAME_TAKEN, rule(l.join(p2, "ada", 0)))
    }

    @Test fun badNamesAreRefused() {
        assertEquals(Rules.INVALID_ARGUMENT, rule(lobby().join(p1, "!", 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(lobby().join(p1, "a\u0001b", 0)))
    }

    @Test fun nameIsCleanedOnJoin() {
        val l = lobby().add(p1, "  Ada   L  ")
        assertEquals("Ada L", l.member(p1)?.name)
    }

    @Test fun spectatingJoinGetsNoSeat() {
        val l = lobby().add(p1).add(p2, spectate = true)
        assertEquals(Role.SPECTATOR, l.member(p2)?.role)
        assertEquals(1, l.players().size)
    }

    // ---- ready, roles, start ----

    @Test fun readyTogglesAndRejectsNoChange() {
        val l = lobby().add(p1)
        assertEquals(listOf<LobbyEvent>(LobbyEvent.ReadyChanged(p1, true)), events(l.setReady(p1, true)))
        assertEquals(Rules.DUPLICATE, rule(l.setReady(p1, true)))
        events(l.setReady(p1, false))
        assertFalse(l.member(p1)?.ready ?: true)
    }

    @Test fun spectatorsCannotReadyUp() {
        val l = lobby().add(p1).add(p2, spectate = true)
        assertEquals(Rules.WRONG_STATE, rule(l.setReady(p2, true)))
    }

    @Test fun switchingRolesFreesAndTakesSeats() {
        val l = lobby(LobbyConfig(maxPlayers = 1)).add(p1).add(p2)
        assertEquals(Rules.LIMIT_EXCEEDED, rule(l.setSpectating(p2, false)))
        events(l.setSpectating(p1, true))
        assertEquals(-1, l.member(p1)?.slot)
        events(l.setSpectating(p2, false))
        assertEquals(0, l.member(p2)?.slot)
        assertEquals(Rules.DUPLICATE, rule(l.setSpectating(p1, true)))
    }

    @Test fun switchingToSpectatorClearsReady() {
        val l = lobby().add(p1).add(p2)
        l.setReady(p2, true)
        l.setSpectating(p2, true)
        assertFalse(l.member(p2)?.ready ?: true)
    }

    @Test fun onlyTheHostStartsAndEveryoneMustBeReady() {
        val l = lobby().add(p1).add(p2)
        assertEquals(Rules.NOT_OWNER, rule(l.start(p2)))
        assertEquals(Rules.PREREQUISITE_MISSING, rule(l.start(p1)))
        ready(l)
        val e = events(l.start(p1))
        val plan = (e.single() as LobbyEvent.Started).plan
        assertEquals(listOf(p1, p2), plan.seats.map { it.player })
        assertEquals(LobbyPhase.RUNNING, l.phase)
    }

    @Test fun startingWithoutReadyWorksWhenNotRequired() {
        val l = lobby(LobbyConfig(requireAllReady = false)).add(p1)
        assertTrue(l.start(p1) is Outcome.Ok)
    }

    @Test fun minimumPlayersAreEnforced() {
        val l = lobby(LobbyConfig(minPlayers = 2)).add(p1)
        l.setReady(p1, true)
        assertEquals(Rules.PREREQUISITE_MISSING, rule(l.start(p1)))
    }

    @Test fun emptySeatsAreFilledByTheComputer() {
        val l = lobby(LobbyConfig(maxPlayers = 4)).add(p1)
        l.setReady(p1, true)
        val plan = (events(l.start(p1)).single() as LobbyEvent.Started).plan
        assertEquals(3, plan.aiSeats)
    }

    @Test fun noComputerFillWhenDisabled() {
        val l = lobby(LobbyConfig(maxPlayers = 4, fillWithAi = false)).add(p1)
        l.setReady(p1, true)
        assertEquals(0, (events(l.start(p1)).single() as LobbyEvent.Started).plan.aiSeats)
    }

    @Test fun cannotStartTwiceOrChangeThingsAfterStart() {
        val l = ready(lobby().add(p1).add(p2))
        l.start(p1)
        assertEquals(Rules.WRONG_STATE, rule(l.start(p1)))
        assertEquals(Rules.WRONG_STATE, rule(l.setReady(p1, false)))
        assertEquals(Rules.WRONG_STATE, rule(l.setSpectating(p1, true)))
    }

    @Test fun lateJoinersOnlyWatch() {
        val l = ready(lobby().add(p1)); l.start(p1)
        events(l.join(p2, "Late", 0))
        assertEquals(Role.SPECTATOR, l.member(p2)?.role)
    }

    @Test fun lateJoinersRefusedWhenSpectatorsAreOff() {
        val l = ready(lobby(LobbyConfig(allowSpectators = false)).add(p1)); l.start(p1)
        assertEquals(Rules.WRONG_STATE, rule(l.join(p2, "Late", 0)))
    }

    @Test fun startDropsMembersStillInGrace() {
        val l = lobby().add(p1).add(p2)
        l.dropped(p2, 0)
        l.setReady(p1, true)
        events(l.start(p1))
        assertNull(l.member(p2))
    }

    // ---- leaving, kicking, host ----

    @Test fun leavingBeforeTheGameRemovesTheMember() {
        val l = lobby().add(p1).add(p2)
        events(l.leave(p2))
        assertNull(l.member(p2))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(l.leave(p2)))
    }

    @Test fun leavingDuringTheGameKeepsTheSeat() {
        val l = ready(lobby().add(p1).add(p2)); l.start(p1)
        events(l.leave(p2))
        assertEquals(Presence.GONE, l.member(p2)?.presence)
        assertEquals(1, l.member(p2)?.slot)
        events(l.returned(p2))
        assertEquals(Presence.CONNECTED, l.member(p2)?.presence)
    }

    @Test fun kickIsHostOnlyAndBans() {
        val l = lobby().add(p1).add(p2).add(p3)
        assertEquals(Rules.NOT_OWNER, rule(l.kick(p2, p3)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(l.kick(p1, p1)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(l.kick(p1, p5)))
        events(l.kick(p1, p3))
        assertNull(l.member(p3))
        assertTrue(l.isBanned(p3))
        assertEquals(Rules.REGULATION, rule(l.join(p3, "Cy", 0)))
    }

    @Test fun kickedPlayerInAGameKeepsTheSeatButCannotReturn() {
        val l = ready(lobby().add(p1).add(p2)); l.start(p1)
        l.kick(p1, p2)
        assertEquals(Presence.GONE, l.member(p2)?.presence)
        assertEquals(Rules.REGULATION, rule(l.returned(p2)))
    }

    @Test fun hostMovesToTheOldestPlayerWhenTheHostLeaves() {
        val l = lobby().add(p1).add(p2).add(p3)
        val e = events(l.leave(p1))
        assertTrue(e.contains(LobbyEvent.HostChanged(p1, p2)))
        assertEquals(p2, l.hostId)
    }

    @Test fun oldestPlayerPolicyPrefersPlayersOverSpectators() {
        val l = lobby(LobbyConfig(maxPlayers = 2)).add(p1).add(p2, spectate = true).add(p3)
        l.leave(p1)
        assertEquals(p3, l.hostId)
    }

    @Test fun oldestMemberPolicyIgnoresRole() {
        val l = lobby(LobbyConfig(maxPlayers = 2, hostPolicy = HostPolicy.OLDEST_MEMBER)).add(p1).add(p2, spectate = true).add(p3)
        l.leave(p1)
        assertEquals(p2, l.hostId)
    }

    @Test fun closePolicyEndsTheSession() {
        val l = lobby(LobbyConfig(hostPolicy = HostPolicy.CLOSE_SESSION)).add(p1).add(p2)
        val e = events(l.leave(p1))
        assertTrue(e.any { it is LobbyEvent.Closed })
        assertEquals(LobbyPhase.CLOSED, l.phase)
        assertEquals(Rules.WRONG_STATE, rule(l.join(p3, "Cy", 0)))
    }

    @Test fun lastMemberLeavingClearsTheHostAndNextJoinerTakesOver() {
        val l = lobby().add(p1)
        l.leave(p1)
        assertEquals(PlayerId.SERVER, l.hostId)
        l.add(p2)
        assertEquals(p2, l.hostId)
    }

    @Test fun hostCanHandOverToAConnectedMemberOnly() {
        val l = lobby().add(p1).add(p2).add(p3)
        assertEquals(Rules.NOT_OWNER, rule(l.transferHost(p2, p3)))
        assertEquals(Rules.DUPLICATE, rule(l.transferHost(p1, p1)))
        l.dropped(p3, 0)
        assertEquals(Rules.WRONG_STATE, rule(l.transferHost(p1, p3)))
        events(l.transferHost(p1, p2))
        assertEquals(p2, l.hostId)
    }

    @Test fun onlyTheHostOrServerCanClose() {
        val l = lobby().add(p1).add(p2)
        assertEquals(Rules.NOT_OWNER, rule(l.close(p2)))
        events(l.close(p1))
        assertEquals(Rules.WRONG_STATE, rule(l.close(PlayerId.SERVER)))
    }

    @Test fun renameFollowsTheJoinRules() {
        val l = lobby().add(p1, "Ada").add(p2, "Bob")
        assertEquals(Rules.NAME_TAKEN, rule(l.rename(p2, "ada")))
        assertEquals(Rules.INVALID_ARGUMENT, rule(l.rename(p2, "*")))
        l.rename(p2, "Robert")
        assertEquals("Robert", l.member(p2)?.name)
        l.rename(p2, "ROBERT")
        assertEquals("ROBERT", l.member(p2)?.name)
    }

    // ---- connection state ----

    @Test fun droppedMemberKeepsTheirPlaceDuringGrace() {
        val l = lobby(LobbyConfig(graceMs = 1000)).add(p1).add(p2)
        events(l.dropped(p2, 100))
        assertEquals(Presence.GRACE, l.member(p2)?.presence)
        assertTrue(l.expire(1099).isEmpty())
        assertNotNull(l.member(p2))
        assertEquals(Rules.DUPLICATE, rule(l.dropped(p2, 200)))
    }

    @Test fun graceExpiryRemovesAMemberDuringSetup() {
        val l = lobby(LobbyConfig(graceMs = 1000)).add(p1).add(p2)
        l.dropped(p2, 0)
        val e = l.expire(1000)
        assertEquals(listOf<LobbyEvent>(LobbyEvent.GaveUp(p2, keptSeat = false)), e)
        assertNull(l.member(p2))
    }

    @Test fun graceExpiryKeepsAPlayersSeatDuringTheGame() {
        val l = ready(lobby(LobbyConfig(graceMs = 1000)).add(p1).add(p2)); l.start(p1)
        l.dropped(p2, 0)
        assertEquals(listOf<LobbyEvent>(LobbyEvent.GaveUp(p2, keptSeat = true)), l.expire(5000))
        assertEquals(Presence.GONE, l.member(p2)?.presence)
        events(l.returned(p2))
        assertEquals(Presence.CONNECTED, l.member(p2)?.presence)
    }

    @Test fun returnWithinGraceRestoresThePlayer() {
        val l = lobby().add(p1).add(p2)
        l.dropped(p2, 0)
        events(l.returned(p2))
        assertTrue(l.expire(1_000_000).isEmpty())
        assertEquals(Rules.DUPLICATE, rule(l.returned(p2)))
    }

    @Test fun droppedHostPassesTheTitleOn() {
        val l = lobby().add(p1).add(p2)
        val e = events(l.dropped(p1, 0))
        assertTrue(e.contains(LobbyEvent.HostChanged(p1, p2)))
        events(l.returned(p1))
        assertEquals(p2, l.hostId)
    }

    @Test fun lonelyDroppedHostKeepsTheTitle() {
        val l = lobby().add(p1)
        l.dropped(p1, 0)
        assertEquals(p1, l.hostId)
    }

    @Test fun expiredHostMovesToSomeoneConnected() {
        val l = lobby(LobbyConfig(graceMs = 10)).add(p1).add(p2).add(p3)
        l.dropped(p1, 0)   // passes the title to p2
        l.dropped(p2, 5)
        assertEquals(p3, l.hostId)
        l.expire(100)
        assertEquals(p3, l.hostId)
    }

    // ---- views and persistence ----

    @Test fun versionRisesWithEveryChangeAndNotWithRefusals() {
        val l = lobby()
        val v0 = l.version
        l.add(p1)
        val v1 = l.version
        assertTrue(v1 > v0)
        l.setReady(p1, true)
        val v2 = l.version
        assertTrue(v2 > v1)
        l.setReady(p1, true)
        assertEquals(v2, l.version)
    }

    @Test fun viewMatchesTheMembersInJoinOrder() {
        val l = lobby().add(p2).add(p1, spectate = true)
        val v = l.view()
        assertEquals(listOf(p2, p1), v.members.map { it.id })
        assertEquals(p2, v.host)
        assertEquals(1, v.players.size)
        assertEquals(1, v.spectators.size)
        val m = v.toMessage()
        assertEquals(v.members, m.members)
        assertTrue(MessageCodec.decode(MessageCodec.encode(1, m)) is Decoded.Ok)
    }

    @Test fun lobbyRoundTripsThroughItsCodec() {
        val l = lobby(LobbyConfig(graceMs = 5000)).add(p1).add(p2).add(p3, spectate = true)
        l.setReady(p1, true); l.setReady(p2, true); l.dropped(p2, 77); l.kick(p1, p3)
        l.assignCompany(p1, 42)
        val w = ByteWriter(); l.write(w)
        val copy = lobby(LobbyConfig(graceMs = 5000)); copy.read(ByteReader(w.toByteArray()))
        assertEquals(l.view().members, copy.view().members)
        assertEquals(l.version, copy.version)
        assertEquals(l.hostId, copy.hostId)
        assertTrue(copy.isBanned(p3))
        assertEquals(42L, copy.member(p1)?.company)
        assertEquals(77L, copy.member(p2)?.droppedAt)
        val w2 = ByteWriter(); copy.write(w2)
        assertArrayEquals(w.toByteArray(), w2.toByteArray())
    }

    @Test fun corruptLobbyDataIsRefusedAndLeavesTheLobbyAlone() {
        val l = lobby().add(p1)
        val w = ByteWriter(); l.write(w)
        val bytes = w.toByteArray()
        val target = lobby().add(p2)
        for (n in 0 until bytes.size) {
            try { target.read(ByteReader(bytes.copyOf(n))); org.junit.Assert.fail("cut at $n") } catch (e: ByteReader.CorruptDataException) { assertTrue(true) }
        }
        assertNotNull(target.member(p2))
        assertNull(target.member(p1))
    }

    @Test fun configRejectsInconsistentLimits() {
        for (bad in listOf({ LobbyConfig(maxPlayers = 0) }, { LobbyConfig(minPlayers = 5, maxPlayers = 4) }, { LobbyConfig(graceMs = -1) }, { LobbyConfig(maxPlayers = 16, maxSpectators = 20) })) {
            try { bad(); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
        }
    }

    @Test fun fullScenarioFromSetupThroughReconnect() {
        val l = lobby(LobbyConfig(maxPlayers = 3, graceMs = 500))
        l.add(p1, "Host").add(p2, "Guest").add(p3, "Watcher", spectate = true)
        l.setReady(p1, true); l.setReady(p2, true)
        val plan = (events(l.start(p1)).single() as LobbyEvent.Started).plan
        assertEquals(1, plan.aiSeats)
        l.assignCompany(p1, 11); l.assignCompany(p2, 12)
        l.dropped(p2, 1000)
        l.expire(1600)
        assertEquals(Presence.GONE, l.member(p2)?.presence)
        events(l.returned(p2))
        assertEquals(12L, l.member(p2)?.company)
        assertEquals(p1, l.hostId)
    }
}
