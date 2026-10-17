package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetSessionTest {
    private fun commands(rig: NetRig, i: Int) {
        for ((n, c) in rig.clients.withIndex()) {
            if (c.state != ClientState.RUNNING || c.role != Role.PLAYER) continue
            val company = rig.serverSim.companiesOf(c.player).firstOrNull() ?: continue
            if ((i + n) % 7 == 0) c.submit(ToySimulation.budget(company, (i * 13L + n) % 5_000), rig.now)
            if ((i + n * 3) % 31 == 0) c.submit(ToySimulation.rename(company, "Firm $n v$i"), rig.now)
        }
    }

    @Test fun clientsJoinReadyAndStart() {
        val rig = NetRig()
        rig.startGame()
        assertEquals(LobbyPhase.RUNNING, rig.shard.lobby.phase)
        for (c in rig.clients) assertEquals(ClientState.RUNNING, c.state)
        assertEquals(2, rig.serverSim.companies.size)
        for (sim in rig.sims) assertEquals(rig.serverSim.companies.keys, sim.companies.keys)
        assertTrue(rig.converged())
    }

    @Test fun twoClientsConvergeOverTwoHundredTicksOnAPerfectLink() {
        val rig = NetRig()
        rig.startGame()
        rig.run(220) { commands(rig, it) }
        rig.drain()
        assertEquals(220L, rig.serverSim.tick)
        assertTrue(rig.converged())
        assertEquals(0, rig.shard.desyncReports().size)
    }

    @Test fun twoClientsConvergeOverALossyDuplicatingReorderingLink() {
        val rig = NetRig(seed = 7, profile = LinkProfile(lossPpm = 150_000, duplicatePpm = 100_000, corruptPpm = 20_000, minDelayMs = 0, maxDelayMs = 60))
        rig.startGame()
        rig.run(230) { commands(rig, it) }
        rig.drain()
        assertTrue("clients never caught up", rig.converged())
        assertTrue(rig.net.stats().dropped > 100)
        assertTrue(rig.net.stats().duplicated > 50)
    }

    @Test fun commandsActuallyChangeTheSharedState() {
        val rig = NetRig(income = false)
        rig.startGame()
        val company = rig.serverSim.companiesOf(rig.client(0).player).first()
        val seq = rig.client(0).submit(ToySimulation.rename(company, "Brand New Name"), rig.now)
        assertNotNull(seq)
        rig.run(12)
        rig.drain()
        assertEquals("Brand New Name", rig.serverSim.companies[company]?.name)
        for (sim in rig.sims) assertEquals("Brand New Name", sim.companies[company]?.name)
        assertTrue(rig.client(0).drainEvents().any { it is ClientEvent.Accepted && it.seq == seq })
    }

    @Test fun sameSeedGivesTheSameFinalState() {
        fun play(): Pair<String, String> {
            val rig = NetRig(seed = 11, profile = LinkProfile.HOSTILE)
            rig.startGame()
            rig.run(120) { commands(rig, it) }
            rig.drain()
            return Hex.encode(rig.serverSim.stateHash()) to rig.net.stats().toString()
        }
        assertEquals(play(), play())
    }

    @Test fun differentSeedsTakeDifferentPathsButStillConverge() {
        val a = NetRig(seed = 1, profile = LinkProfile.HOSTILE)
        val b = NetRig(seed = 2, profile = LinkProfile.HOSTILE)
        for (rig in listOf(a, b)) { rig.startGame(); rig.run(80) { commands(rig, it) }; rig.drain(); assertTrue(rig.converged()) }
        assertFalse(a.net.stats() == b.net.stats())
    }

    @Test fun aSpectatorFollowsTheGameButCannotAct() {
        val rig = NetRig(spectators = 1)
        rig.startGame()
        val spectator = rig.client(2)
        assertEquals(Role.SPECTATOR, spectator.role)
        assertEquals(null, spectator.submit(ToySimulation.rename(1, "Nope Nope"), rig.now))
        rig.run(60) { commands(rig, it) }
        rig.drain()
        assertTrue(rig.converged())
    }

    @Test fun theServerReportsConnectionQualityForEveryone() {
        val rig = NetRig()
        rig.startGame()
        rig.run(60) { commands(rig, it) }
        val status = rig.shard.status()
        assertEquals(2, status.players.size)
        assertTrue(status.players.all { it.quality != Quality.OFFLINE })
        assertTrue(status.players.all { it.rttMs >= 0 })
        assertEquals(1, status.players.count { it.host })
    }

    @Test fun manySeedsOnAHostileLinkAllConverge() {
        for (seed in 20L..24L) {
            val rig = NetRig(seed = seed, profile = LinkProfile.HOSTILE)
            rig.startGame()
            rig.run(90) { commands(rig, it) }
            rig.drain(30_000L)
            assertTrue("seed $seed did not converge", rig.converged())
        }
    }

    @Test fun theCommitChainOfEveryClientMatchesTheServersLog() {
        val rig = NetRig(seed = 3, profile = LinkProfile.lossy(80_000))
        rig.startGame()
        rig.run(70) { commands(rig, it) }
        rig.drain()
        val log = rig.shard.log.all()
        assertEquals(70, log.size)
        var chain = 0L
        for (c in log) {
            chain = CommitChain.next(chain, c.tick, c.entries, c.transfers)
            assertEquals(chain, c.chain)
        }
    }

    @Test fun moreThanTwoPlayersShareOneConsistentWorld() {
        val rig = NetRig(seed = 4, profile = LinkProfile(lossPpm = 50_000, maxDelayMs = 30), clientCount = 4)
        rig.startGame()
        rig.run(100) { commands(rig, it) }
        rig.drain()
        assertTrue(rig.converged())
        assertEquals(4, rig.serverSim.companies.size)
    }
}
