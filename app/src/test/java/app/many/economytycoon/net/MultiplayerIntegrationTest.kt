package app.many.economytycoon.net

import app.many.economytycoon.companies.Department
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The multiplayer layer over the real economy: the escrow command, a hosted table of two players, and replay. */
private fun port(ai: Int = 2) = RealSimulationPort(SimTestSupport.sim(seed = 7, ai = ai), SimTestSupport.content)

class MultiplayerIntegrationTest {
    /** A host and a guest at one in-process table, each playing through its own copy of the real economy. */
    private class Table(tickMs: Long = 100L) {
        val authority = port()
        val ports = List(2) { port() }
        val table = LocalTable(
            ShardConfig(session = SessionId(5), seed = 7, hashInterval = 5, lobby = LobbyConfig(maxPlayers = 2), snapshotMinIntervalMs = 100L),
            authority, SeededTokenSource(5), tickMs = tickMs,
        )
        val clients = listOf(table.join("Host", ports[0]), table.join("Guest", ports[1]))
        val node = table.node()
        var now = 0L
        val headlines = ArrayList<String>()

        fun pump(ms: Long = 20L): PumpReport { now += ms; return node.pump(now).also { headlines += it.headlines } }
        fun until(maxMs: Long, c: () -> Boolean) { var t = 0L; while (t < maxMs && !c()) { pump(); t += 20L } }

        fun start() {
            clients.forEach { it.connect(now) }
            until(20_000L) { clients.all { it.lobby() != null } }
            clients.forEach { it.ready(now) }
            until(20_000L) { table.shard.lobby.players().all { it.ready } }
            clients.first { it.player == table.shard.lobby.hostId }.startGame(now)
            until(20_000L) { clients.all { it.state == ClientState.RUNNING } }
        }

        fun runTicks(n: Int, each: (Int) -> Unit = {}) {
            val target = authority.tick + n
            var i = 0
            while (authority.tick < target) { if (i % 5 == 0) each(i / 5); pump(); i++ }
        }

        fun settle() = until(10_000L) { converged() }
        fun converged() = ports.all { it.tick == authority.tick && it.stateHash().contentEquals(authority.stateHash()) }
    }

    // ---- the command ----------------------------------------------------------------------------

    private fun twoCompanies(): Triple<Simulation, Long, Long> {
        val sim = SimTestSupport.sim(seed = 7, ai = 0)
        val p = RealSimulationPort(sim, SimTestSupport.content)
        val r = Seating.seat(p, listOf(SeatAssignment(0, PlayerId(1), "Alpha Works"), SeatAssignment(1, PlayerId(2), "Beta Works")))
        return Triple(sim, r[0].company, r[1].company)
    }

    private fun cash(sim: Simulation, company: Long) =
        sim.economy.finance.ledger.cash(sim.economy.companies.company(CompanyId(company))!!.owner).raw

    @Test fun theHostMovesCashAndTheTotalIsConserved() {
        val (sim, a, b) = twoCompanies()
        val before = cash(sim, a) + cash(sim, b)
        val r = sim.execute(Actor.System, Command.EscrowTransfer(1, a, b, Money.units(1_000), 0))
        assertTrue(r.toString(), r is Outcome.Ok)
        assertEquals(before, cash(sim, a) + cash(sim, b))
        assertEquals(Money.units(1_000).raw, cash(sim, b) - (before / 2))
        assertTrue(Invariants.check(sim.economy).isEmpty())
        assertTrue(sim.commandLog().any { it.command is Command.EscrowTransfer && it.immediate })
    }

    @Test fun escrowAccountIsReachableFromBothSides() {
        val (sim, a, b) = twoCompanies()
        assertTrue(sim.execute(Actor.System, Command.EscrowTransfer(2, a, CommandExecutorEscrow, Money.units(500), 0)) is Outcome.Ok)
        assertTrue(sim.execute(Actor.System, Command.EscrowTransfer(2, CommandExecutorEscrow, b, Money.units(500), 1)) is Outcome.Ok)
    }

    @Test fun playersAndComputerCompaniesMayNotGiveIt() {
        val (sim, a, b) = twoCompanies()
        val cmd = Command.EscrowTransfer(3, a, b, Money.units(1), 0)
        for (actor in listOf(Actor.Player(1), Actor.Ai(CompanyId(a)))) {
            val r = sim.execute(actor, cmd)
            assertEquals(Rules.NOT_OWNER, (r as Outcome.Rejected).rejection.rule)
        }
        assertFalse(sim.commandLog().any { it.command is Command.EscrowTransfer })
    }

    @Test fun badTransfersAreRefusedAndChangeNothing() {
        val (sim, a, b) = twoCompanies()
        val h = sim.hash()
        fun rule(c: Command.EscrowTransfer) = (sim.execute(Actor.System, c) as Outcome.Rejected).rejection.rule
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(Command.EscrowTransfer(4, a, b, Money.units(1_000_000_000_000L), 0)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(Command.EscrowTransfer(4, 987_654, b, Money.units(1), 0)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(Command.EscrowTransfer(4, a, 987_654, Money.units(1), 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(Command.EscrowTransfer(4, a, b, Money.ZERO, 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(Command.EscrowTransfer(4, a, a, Money.units(1), 0)))
        assertEquals(h, sim.hash())
    }

    @Test fun repeatingTheSameLegInOneTickIsHarmless() {
        val (sim, a, b) = twoCompanies()
        val cmd = Command.EscrowTransfer(5, a, b, Money.units(10), 0)
        assertTrue(sim.execute(Actor.System, cmd) is Outcome.Ok)
        val after = cash(sim, a)
        assertEquals(Rules.DUPLICATE, (sim.execute(Actor.System, cmd) as Outcome.Rejected).rejection.rule)
        assertEquals(after, cash(sim, a))
    }

    @Test fun theCommandRoundTripsAndPlayersCannotSendIt() {
        val cmd = Command.EscrowTransfer(9, 4, 0, Money.units(75), 3)
        val w = ByteWriter(); CommandCodec.writeCommand(cmd, w)
        assertEquals(cmd, CommandCodec.readCommand(ByteReader(w.toByteArray())))
        assertEquals("server_only", CommandSanity.check(cmd))
        assertNotNull(app.many.economytycoon.activity.CommandDescriber.describe(cmd))
        assertTrue(app.many.economytycoon.activity.CommandDescriber.movesMoney(cmd))
    }

    @Test fun portTransfersAreLoggedSystemCommands() {
        val (sim, a, b) = twoCompanies()
        val p = RealSimulationPort(sim, SimTestSupport.content)
        val ok = p.applyTransfers(listOf(
            Transfer(TradeId(7), a, RealSimulationPort.ESCROW, TradeAsset.cash(Money.units(100).raw)),
            Transfer(TradeId(7), RealSimulationPort.ESCROW, b, TradeAsset.cash(Money.units(100).raw)),
            Transfer(TradeId(8), a, b, TradeAsset.cash(Money.units(1_000_000_000_000L).raw)),
        ))
        assertEquals(listOf(true, true, false), ok)
        val logged = sim.commandLog().filter { it.command is Command.EscrowTransfer }
        assertEquals(2, logged.size)
        assertTrue(logged.all { it.actor == Actor.System })
    }

    // ---- a hosted table ---------------------------------------------------------------------------

    @Test fun aHostedTableOfTwoStaysInStepAndReplaysEqual() {
        val t = Table()
        t.start()
        assertEquals(LobbyPhase.RUNNING, t.table.shard.lobby.phase)
        t.runTicks(60) { i ->
            for ((n, c) in t.clients.withIndex()) {
                val co = t.authority.companiesOf(c.player).firstOrNull() ?: continue
                if ((i + n) % 3 == 0) c.submit(Command.SetBudget(CompanyId(co), Department.MARKETING, Money.units(5L + i)), t.now)
            }
        }
        t.settle()
        assertTrue(t.converged())
        assertTrue(t.authority.tick >= 60L)
        assertTrue(Invariants.check(t.authority.simulation.economy).isEmpty())
        // the shard's record replays to the same hashes
        val verdict = SessionReplay { port() }.verify(t.table.shard.record())
        assertTrue(verdict.toString(), verdict.ok)
        // and so does the plain command log of the host's own simulation
        val sim = t.authority.simulation
        val replayed = Simulation.replay(sim.config, SimTestSupport.content, sim.commandLog(), sim.tick.value)
        assertEquals(sim.hash(), replayed.hash())
        // every copy ran the same commands
        for (p in t.ports) assertEquals(sim.hash(), p.simulation.hash())
    }

    @Test fun aCashTradeSettlesOnEveryCopyAndLandsInTheCommandLog() {
        val t = Table()
        t.start()
        t.runTicks(5)
        val giver = t.clients[0]; val taker = t.clients[1]
        val giverCo = t.authority.companiesOf(giver.player).single()
        val takerCo = t.authority.companiesOf(taker.player).single()
        val amount = Money.units(5_000).raw
        val giverBefore = t.authority.available(giverCo, TradeAsset.cash(1))
        val takerBefore = t.authority.available(takerCo, TradeAsset.cash(1))
        giver.offerTrade(taker.player, listOf(TradeAsset.cash(amount)), emptyList(), 60)
        t.until(10_000L) { taker.trades().isNotEmpty() }
        val id = taker.trades().first().trade
        taker.answerTrade(id, TradeAction.ACCEPT)
        t.until(20_000L) { giver.trades().any { it.state == TradeState.SETTLED } }
        t.runTicks(5)
        t.settle()
        assertTrue(giver.trades().any { it.state == TradeState.SETTLED })
        assertTrue(t.converged())
        assertTrue(t.authority.available(takerCo, TradeAsset.cash(1)) > takerBefore)
        assertTrue(t.authority.available(giverCo, TradeAsset.cash(1)) < giverBefore)
        val moved = t.authority.simulation.commandLog().filter { it.command is Command.EscrowTransfer }
        assertEquals(2, moved.size)
        for (p in t.ports) assertEquals(2, p.simulation.commandLog().count { it.command is Command.EscrowTransfer })
        val sim = t.authority.simulation
        assertEquals(sim.hash(), Simulation.replay(sim.config, SimTestSupport.content, sim.commandLog(), sim.tick.value).hash())
        assertTrue(Invariants.check(sim.economy).isEmpty())
        assertTrue(t.headlines.any { it.startsWith("Trade with ") && it.endsWith("settled") })
    }

    @Test fun theSameTableRunsTheSameWayTwice() {
        fun run(): String {
            val t = Table()
            t.start()
            t.runTicks(30)
            t.settle()
            return t.authority.simulation.hash()
        }
        assertEquals(run(), run())
    }

    @Test fun aSessionBoundPortStepsThroughItsDriver() {
        var steps = 0
        val sim = SimTestSupport.sim(seed = 7, ai = 0)
        val p = RealSimulationPort(sim, SimTestSupport.content, object : SimulationDriver {
            override fun step() = sim.advance().also { steps++ }
            override fun replace(next: Simulation) = false
        })
        p.advance(); p.advance()
        assertEquals(2, steps)
        // a game that is on screen cannot be swapped out underneath the screen
        assertTrue(p.restore(p.snapshot()) is Outcome.Rejected)
        assertEquals(2L, p.tick)
    }

    @Test fun secureTokensDifferAndHaveTheRequestedSize() {
        val s = SecureTokenSource()
        val a = s.next(32); val b = s.next(32)
        assertEquals(32, a.size)
        assertFalse(a.contentEquals(b))
    }

    private companion object { const val CommandExecutorEscrow = RealSimulationPort.ESCROW }
}
