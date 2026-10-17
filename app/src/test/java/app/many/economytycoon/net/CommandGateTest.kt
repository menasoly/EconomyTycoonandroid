package app.many.economytycoon.net

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.companies.Department
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandGateTest {
    private val session = NetFixtures.session
    private val player = PlayerId(1)
    private val key = ByteArray(32) { 7 }
    private val mine = 5L

    private fun gate(config: GateConfig = GateConfig()) = CommandGate(session, config)

    private fun submit(seq: Long, tick: Long, cmd: Command = ToySimulation.rename(mine, "Name $seq"), signKey: ByteArray = key): NetMessage.Submit {
        val payload = CommandPayload.encode(cmd)
        return NetMessage.Submit(seq, tick, payload, Signing.commandMac(signKey, session, player, seq, tick, payload))
    }

    private fun eval(g: CommandGate, s: NetMessage.Submit, now: Long = 0, next: Long = 10, owns: (Long) -> Boolean = { it == mine }) =
        g.evaluate(player, key, s, now, next, owns)

    private fun refused(r: GateResult): GateResult.Refused = r as GateResult.Refused

    @Test fun validCommandIsAcceptedWithAnAck() {
        val r = eval(gate(), submit(1, 12)) as GateResult.Accepted
        assertEquals(NetMessage.Ack(1, 12), r.reply)
        assertEquals(player, r.entry.player)
        assertEquals(1L, r.entry.seq)
        assertTrue(r.command is Command.Rename)
    }

    @Test fun wrongSignatureIsRefusedWithoutUsingTheSequenceNumber() {
        val g = gate()
        val r = refused(eval(g, submit(1, 12, signKey = ByteArray(32))))
        assertEquals(NetError.BAD_SIGNATURE, r.error)
        assertEquals(false, r.consumed)
        assertTrue(eval(g, submit(1, 12)) is GateResult.Accepted)
    }

    @Test fun tamperedPayloadFailsTheSignature() {
        val s = submit(1, 12)
        val forged = NetMessage.Submit(s.seq, s.targetTick, CommandPayload.encode(ToySimulation.rename(mine, "Other name")), s.mac)
        assertEquals(NetError.BAD_SIGNATURE, refused(eval(gate(), forged)).error)
    }

    @Test fun retargetingASignedCommandFails() {
        val s = submit(1, 12)
        assertEquals(NetError.BAD_SIGNATURE, refused(eval(gate(), NetMessage.Submit(s.seq, 13, s.payload, s.mac))).error)
    }

    @Test fun repeatedSequenceGetsTheSameReplyAndIsNotAppliedTwice() {
        val g = gate()
        val s = submit(1, 12)
        val first = eval(g, s) as GateResult.Accepted
        val again = eval(g, s) as GateResult.Repeat
        assertEquals(first.reply, again.reply)
    }

    @Test fun repeatedRejectedSequenceRepeatsTheRejection() {
        val g = gate()
        val s = submit(1, 12, ToySimulation.rename(99, "Nope Name"))
        val first = refused(eval(g, s))
        assertEquals(NetError.NOT_OWNER, first.error)
        assertTrue(first.consumed)
        assertEquals(first.reply, (eval(g, s) as GateResult.Repeat).reply)
    }

    @Test fun oldAndFarAheadSequencesAreRefused() {
        val g = gate()
        eval(g, submit(200, 12))
        assertEquals(NetError.STALE_SEQUENCE, refused(eval(g, submit(100, 12))).error)
        assertEquals(NetError.SEQUENCE_AHEAD, refused(eval(g, submit(5000, 12))).error)
        assertEquals(NetError.STALE_SEQUENCE, refused(eval(g, submit(0, 12))).error)
    }

    @Test fun targetTickMustNotHavePassed() {
        val r = refused(eval(gate(), submit(1, 9), next = 10))
        assertEquals(NetError.TOO_LATE, r.error)
        assertEquals(10L, r.reply.serverTick)
    }

    @Test fun targetTickMustNotBeFarInTheFuture() {
        val g = gate(GateConfig(maxAheadTicks = 5))
        assertTrue(eval(g, submit(1, 15), next = 10) is GateResult.Accepted)
        assertEquals(NetError.TOO_EARLY, refused(eval(g, submit(2, 16), next = 10)).error)
    }

    @Test fun unreadablePayloadIsRefused() {
        val payload = byteArrayOf(99, 99, 99)
        val s = NetMessage.Submit(1, 12, payload, Signing.commandMac(key, session, player, 1, 12, payload))
        assertEquals(NetError.INVALID_COMMAND, refused(eval(gate(), s)).error)
    }

    @Test fun implausibleCommandIsRefusedByRule() {
        val r = refused(eval(gate(), submit(1, 12, Command.Borrow(CompanyId(mine), Money.units(-4), 30))))
        assertEquals(NetError.INVALID_COMMAND, r.error)
        assertEquals("impossible_value", r.rule)
    }

    @Test fun foundingCommandsAreBlocked() {
        val cmd = Command.FoundCompany("Mint Corp", app.many.economytycoon.core.CountryId(1), null, Money.units(100_000))
        assertEquals("founding_not_allowed", refused(eval(gate(), submit(1, 12, cmd))).rule)
    }

    @Test fun commandsForSomeoneElsesCompanyAreRefused() {
        assertEquals(NetError.NOT_OWNER, refused(eval(gate(), submit(1, 12, ToySimulation.rename(6, "Their Co")))).error)
    }

    @Test fun perTickCommandLimitApplies() {
        val g = gate(GateConfig(maxPerPlayerPerTick = 2))
        assertTrue(eval(g, submit(1, 12)) is GateResult.Accepted)
        assertTrue(eval(g, submit(2, 12)) is GateResult.Accepted)
        val third = refused(eval(g, submit(3, 12)))
        assertEquals("tick_budget", third.rule)
        assertTrue(eval(g, submit(4, 13)) is GateResult.Accepted)
    }

    @Test fun tickByteBudgetApplies() {
        val one = CommandPayload.encode(ToySimulation.rename(mine, "Name 1")).size
        val g = gate(GateConfig(maxTickBytes = NetLimits.MAX_COMMAND_BYTES, maxPerPlayerPerTick = 1000))
        var accepted = 0
        for (i in 1..400L) if (eval(g, submit(i, 12), now = i * 100) is GateResult.Accepted) accepted++
        assertTrue("accepted $accepted", accepted <= NetLimits.MAX_COMMAND_BYTES / one)
        assertTrue(accepted > 10)
    }

    @Test fun committingATickFreesItsBudget() {
        val g = gate(GateConfig(maxPerPlayerPerTick = 1))
        assertTrue(eval(g, submit(1, 12)) is GateResult.Accepted)
        assertEquals("tick_budget", refused(eval(g, submit(2, 12))).rule)
        g.committed(12)
        assertTrue(eval(g, submit(3, 12), next = 12) is GateResult.Accepted)
    }

    @Test fun messageRateLimitDoesNotConsumeTheSequence() {
        val g = gate(GateConfig(burst = 2, perSecond = 1, maxPerPlayerPerTick = 100))
        assertTrue(eval(g, submit(1, 12), now = 0) is GateResult.Accepted)
        assertTrue(eval(g, submit(2, 12), now = 0) is GateResult.Accepted)
        val limited = refused(eval(g, submit(3, 12), now = 0))
        assertEquals(NetError.RATE_LIMITED, limited.error)
        assertEquals(false, limited.consumed)
        assertTrue(eval(g, submit(3, 12), now = 1500) is GateResult.Accepted)
    }

    @Test fun outOfOrderSequencesAreBothAccepted() {
        val g = gate()
        assertTrue(eval(g, submit(2, 12)) is GateResult.Accepted)
        assertTrue(eval(g, submit(1, 12)) is GateResult.Accepted)
    }

    @Test fun playersAreIndependent() {
        val g = gate()
        val s = submit(1, 12)
        assertTrue(eval(g, s) is GateResult.Accepted)
        val other = PlayerId(2)
        val payload = CommandPayload.encode(ToySimulation.budget(mine, 3))
        val mac = Signing.commandMac(key, session, other, 1, 12, payload)
        assertTrue(g.evaluate(other, key, NetMessage.Submit(1, 12, payload, mac), 0, 10) { true } is GateResult.Accepted)
        assertNotNull(g)
    }

    @Test fun signatureFromAnotherSessionIsRefused() {
        val payload = CommandPayload.encode(ToySimulation.rename(mine, "Cross Session"))
        val s = NetMessage.Submit(1, 12, payload, Signing.commandMac(key, SessionId(1), player, 1, 12, payload))
        assertEquals(NetError.BAD_SIGNATURE, refused(eval(gate(), s)).error)
    }

    @Test fun hostileFloodNeverThrowsAndAcceptsNothing() {
        val g = gate()
        val rnd = app.many.economytycoon.core.DeterministicRandom(8)
        repeat(2000) {
            val payload = ByteArray(rnd.nextInt(60)) { rnd.nextInt(256).toByte() }
            val s = NetMessage.Submit(rnd.nextInt(500).toLong(), rnd.nextInt(30).toLong(), payload, ByteArray(16) { rnd.nextInt(256).toByte() })
            assertTrue(eval(g, s, now = it.toLong()) !is GateResult.Accepted)
        }
    }

    @Test fun budgetCommandThroughTheGateKeepsItsMeaning() {
        val r = eval(gate(), submit(1, 12, Command.SetBudget(CompanyId(mine), Department.RESEARCH, Money.units(3)))) as GateResult.Accepted
        assertEquals(Command.SetBudget(CompanyId(mine), Department.RESEARCH, Money.units(3)), r.command)
    }
}
