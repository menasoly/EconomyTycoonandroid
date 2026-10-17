package app.many.economytycoon.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiCheatTest {
    private val p = PlayerId(1); private val q = PlayerId(2)

    @Test fun cleanPlayersHaveNoScore() {
        val a = AntiCheat()
        assertEquals(0, a.score(p))
        assertEquals(Standing.GOOD, a.standing(p))
        assertTrue(a.incidents().isEmpty())
    }

    @Test fun offensesAddTheirWeight() {
        val a = AntiCheat()
        a.record(p, Offense.MALFORMED, 0)
        a.record(p, Offense.REPLAYED, 0)
        assertEquals(1 + 3, a.score(p))
        assertEquals(1, a.count(p, Offense.MALFORMED))
        assertEquals(0, a.count(p, Offense.BAD_SIGNATURE))
    }

    @Test fun standingEscalatesThroughTheThresholds() {
        val a = AntiCheat()
        assertEquals(Action.NONE, a.record(p, Offense.NOT_OWNER, 0))
        assertEquals(Standing.GOOD, a.standing(p))
        assertEquals(Action.NONE, a.record(p, Offense.NOT_OWNER, 0))
        assertEquals(Standing.WATCHED, a.standing(p))
        a.record(p, Offense.NOT_OWNER, 0)
        a.record(p, Offense.NOT_OWNER, 0)
        assertEquals(Action.WARN, a.record(p, Offense.NOT_OWNER, 0))
        assertEquals(Standing.WARNED, a.standing(p))
    }

    @Test fun forgeryGetsKickedAndThenBanned() {
        val a = AntiCheat()
        val actions = (1..13).map { a.record(p, Offense.BAD_SIGNATURE, 0) }
        assertEquals(1, actions.count { it == Action.WARN })
        assertEquals(1, actions.count { it == Action.KICK })
        assertEquals(Action.BAN, actions.last())
        assertEquals(Standing.BANNED, a.standing(p))
    }

    @Test fun kickIsOnlyAskedForOnce() {
        val a = AntiCheat()
        repeat(5) { a.record(p, Offense.BAD_SIGNATURE, 0) }
        assertEquals(Action.NONE, a.record(p, Offense.MALFORMED, 0))
    }

    @Test fun goodBehaviourIsSlowlyForgiven() {
        val a = AntiCheat()
        repeat(5) { a.record(p, Offense.NOT_OWNER, 0) }
        assertEquals(30, a.score(p))
        a.advance(100)
        assertEquals(20, a.score(p))
        a.advance(1000)
        assertEquals(0, a.score(p))
        assertEquals(Standing.GOOD, a.standing(p))
    }

    @Test fun forgivenessNeverGoesBelowZero() {
        val a = AntiCheat()
        a.record(p, Offense.MALFORMED, 0)
        a.advance(1_000_000)
        assertEquals(0, a.score(p))
    }

    @Test fun occasionalGlitchesOnABadConnectionNeverEscalate() {
        val a = AntiCheat()
        for (t in 0L until 2000L step 25L) {
            assertEquals(Action.NONE, a.record(p, Offense.STALE_SEQUENCE, t))
            a.record(p, Offense.MALFORMED, t)
        }
        assertEquals(Standing.GOOD, a.standing(p))
    }

    @Test fun playersAreTrackedSeparately() {
        val a = AntiCheat()
        repeat(4) { a.record(p, Offense.NOT_OWNER, 0) }
        assertEquals(0, a.score(q))
        assertEquals(listOf(p, q).first(), a.players().first())
        assertEquals(4, a.incidentsOf(p).size)
        assertTrue(a.incidentsOf(q).isEmpty())
    }

    @Test fun incidentLogIsBounded() {
        val a = AntiCheat(AntiCheatConfig(maxIncidents = 5))
        repeat(20) { a.record(p, Offense.MALFORMED, it.toLong(), "x".repeat(500)) }
        assertEquals(5, a.incidents().size)
        assertTrue(a.incidents().all { it.detail.length <= NetLimits.MAX_DETAIL_CHARS })
        assertEquals(19L, a.incidents().last().tick)
    }

    @Test fun mostlyRefusedCommandsAreAnOffense() {
        val a = AntiCheat(AntiCheatConfig(refusalWindow = 10, refusalLimitPpm = 700_000))
        var action = Action.NONE
        repeat(10) { action = a.observe(p, false, it.toLong()) }
        assertEquals(1, a.count(p, Offense.ENGINE_REFUSALS))
        assertEquals(Action.NONE, action)
    }

    @Test fun normalRefusalRatesAreFine() {
        val a = AntiCheat(AntiCheatConfig(refusalWindow = 10, refusalLimitPpm = 700_000))
        repeat(100) { a.observe(p, it % 3 != 0, it.toLong()) }
        assertEquals(0, a.count(p, Offense.ENGINE_REFUSALS))
    }

    @Test fun refusalCheckWaitsForEnoughSamples() {
        val a = AntiCheat(AntiCheatConfig(refusalWindow = 10))
        repeat(9) { a.observe(p, false, 0) }
        assertEquals(0, a.count(p, Offense.ENGINE_REFUSALS))
    }

    @Test fun forgetClearsAPlayer() {
        val a = AntiCheat()
        a.record(p, Offense.BAD_SIGNATURE, 0)
        a.forget(p)
        assertEquals(0, a.score(p))
    }

    @Test fun thresholdsMustRise() {
        try { AntiCheatConfig(watchAt = 50, warnAt = 20); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { assertTrue(true) }
    }

    // ---- desync attribution ----

    private fun h(n: Int) = NetFixtures.hash(n)

    @Test fun agreeingReportsMeanNoDesync() {
        val r = DesyncAnalysis.analyse(50, h(1), mapOf(p to h(1), q to h(1)))
        assertEquals(DesyncCause.NONE, r.cause)
        assertTrue(r.culprits.isEmpty())
    }

    @Test fun oneOddPlayerOutIsTheCulprit() {
        val r = DesyncAnalysis.analyse(50, h(1), mapOf(p to h(1), q to h(2), PlayerId(3) to h(1)))
        assertEquals(DesyncCause.CLIENTS, r.cause)
        assertEquals(listOf(q), r.culprits)
        assertEquals(2, r.groups.size)
        assertEquals(2, r.groups.first().players.size)
    }

    @Test fun everyoneDisagreeingWithTheServerPointsAtTheServer() {
        val r = DesyncAnalysis.analyse(50, h(1), mapOf(p to h(2), q to h(2)))
        assertEquals(DesyncCause.SERVER, r.cause)
        assertTrue(r.culprits.isEmpty())
    }

    @Test fun singleDivergentReporterIsBlamed() {
        val r = DesyncAnalysis.analyse(50, h(1), mapOf(p to h(2)))
        assertEquals(DesyncCause.CLIENTS, r.cause)
        assertEquals(listOf(p), r.culprits)
    }

    @Test fun allDifferentIsUnknown() {
        val r = DesyncAnalysis.analyse(50, h(1), mapOf(p to h(2), q to h(3), PlayerId(3) to h(4)))
        assertEquals(DesyncCause.UNKNOWN, r.cause)
        assertEquals(3, r.culprits.size)
    }

    @Test fun noReportsNoAccusation() {
        val r = DesyncAnalysis.analyse(50, h(1), emptyMap())
        assertEquals(DesyncCause.NONE, r.cause)
        assertEquals(0, r.groups.size)
    }

    @Test fun analysisIsIndependentOfReportOrder() {
        val a = DesyncAnalysis.analyse(5, h(1), linkedMapOf(p to h(2), q to h(1), PlayerId(3) to h(2)))
        val b = DesyncAnalysis.analyse(5, h(1), linkedMapOf(PlayerId(3) to h(2), q to h(1), p to h(2)))
        assertEquals(a.culprits, b.culprits)
        assertEquals(a.groups.map { it.hex }, b.groups.map { it.hex })
        assertEquals(12, a.serverShort.length)
    }
}
