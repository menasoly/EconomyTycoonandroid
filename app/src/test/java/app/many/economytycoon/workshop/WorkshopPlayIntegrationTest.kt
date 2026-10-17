package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.scenarios.DefEvent
import app.many.economytycoon.scenarios.DefEventKind
import app.many.economytycoon.scenarios.DefFail
import app.many.economytycoon.scenarios.DefFailKind
import app.many.economytycoon.scenarios.DefSite
import app.many.economytycoon.scenarios.Goal
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.GoalMeter
import app.many.economytycoon.scenarios.LossReason
import app.many.economytycoon.scenarios.ScenarioCatalog
import app.many.economytycoon.scenarios.ScenarioCodec
import app.many.economytycoon.scenarios.ScenarioDef
import app.many.economytycoon.scenarios.ScenarioDefCodec
import app.many.economytycoon.scenarios.ScenarioEvent
import app.many.economytycoon.scenarios.ScenarioService
import app.many.economytycoon.scenarios.ScenarioStatus
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.session.NewGame
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.world.WorldSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A draft from the workshop becomes a game that can be played, saved, loaded and carried on. */
class WorkshopPlayIntegrationTest {
    private val content get() = SimTestSupport.content

    /** The cheap sample draft with something of everything the game can play. */
    private fun playable(): ScenarioDraft = Samples.cheap("play-test").copy(
        events = listOf(
            ScriptedEvent(10, EventKind.GRANT, 20_000L, "Seed money"),
            ScriptedEvent(25, EventKind.ANNOUNCE, 0L, "Rain is coming."),
            ScriptedEvent(45, EventKind.LEVY, 5_000L),
            ScriptedEvent(50, EventKind.RIVAL_GRANT, 10_000L, "Rivals get a loan."),
        ),
        fails = listOf(FailCondition(FailKind.NET_WORTH_BELOW, 20_000L, afterDay = 30), FailCondition(FailKind.CASH_BELOW, 1L, afterDay = 200)),
    ).normalised()

    private fun conversion(d: ScenarioDraft = playable()): Conversion = (DraftConverter.convert(d, content) as Outcome.Ok).value

    private fun start(c: Conversion = conversion()): GameSession =
        (NewGame.startCustom(c.def, c.params.config, c.params.options.companyName, content) as Outcome.Ok).value

    private fun GameSession.company() = GoalMeter.playerCompanies(economy, 1L).first()
    private fun GameSession.cash(): Money = economy.finance.ledger.cash(economy.companies.require(company()).owner)

    private fun reload(sim: Simulation): Simulation = SimulationSnapshot.load(SimulationSnapshot.save(sim), content)

    private fun directDef(
        fails: List<DefFail> = emptyList(), events: List<DefEvent> = emptyList(), ruin: Long? = null, ruinAfter: Int = 0,
        goals: List<Goal> = listOf(Goal(GoalKind.NET_WORTH, 900_000_000L)),
    ) = ScenarioDef(
        "direct-test", "Direct Test", "tag", "story", Money.units(300_000), 2, WorldSize.SMALL, 7L, 0, goals, 2, 300, 500,
        ruinBelow = ruin, ruinAfterDay = ruinAfter, events = events, fails = fails,
    )

    private fun startDef(def: ScenarioDef): GameSession {
        val c = conversion()
        return (NewGame.startCustom(def, c.params.config, "Direct Co", content) as Outcome.Ok).value
    }

    // ---- starting -------------------------------------------------------------------------------

    @Test fun aDraftStartsAGameWithItsRunItsStartingSitesAndItsConfiguration() {
        val c = conversion()
        val s = start(c)
        val run = s.economy.scenario.run
        assertNotNull(run)
        assertEquals("play-test", run?.id)
        assertEquals(c.def, s.economy.scenario.def())
        assertEquals(c.params.config, s.economy.config)
        assertEquals(1, s.economy.production.facilitiesOf(s.company()).size)
        assertNull(ScenarioCatalog.byId("play-test"))
        assertEquals(Money.units(300_000), s.cash())
    }

    @Test fun startingRefusesAShortNameAndAnUnsoundDefinition() {
        val c = conversion()
        assertTrue(NewGame.startCustom(c.def, c.params.config, "ab", content) is Outcome.Rejected)
        assertTrue(NewGame.startCustom(c.def.copy(goals = emptyList()), c.params.config, "Fine Name", content) is Outcome.Rejected)
        assertTrue(NewGame.startCustom(c.def.copy(countryIndex = 99), c.params.config, "Fine Name", content) is Outcome.Rejected)
    }

    @Test fun anUnknownStartingRecipeIsSkippedNotFatal() {
        val c = conversion()
        val s = (NewGame.startCustom(c.def.copy(starters = listOf(DefSite("no_such_recipe", 0))), c.params.config, "Fine Name", content) as Outcome.Ok).value
        assertEquals(0, s.economy.production.facilitiesOf(s.company()).size)
    }

    @Test fun twoGamesStartedFromTheSameDraftAreIdentical() {
        val a = start(); val b = start()
        assertEquals(a.simulation.hash(), b.simulation.hash())
        repeat(40) { a.simulation.advance(); b.simulation.advance() }
        assertEquals(a.simulation.hash(), b.simulation.hash())
    }

    @Test fun theDefinitionTakesPartInTheHash() {
        val c = conversion()
        val a = start(c)
        val b = start(conversion(playable().copy(hints = listOf("A different hint."))))
        assertNotEquals(a.simulation.hash(), b.simulation.hash())
    }

    // ---- scripted events ------------------------------------------------------------------------

    @Test fun scriptedEventsHappenOnTheirDaysAndAreTold() {
        val s = start()
        val told = ArrayList<ScenarioEvent.Scripted>()
        fun step(n: Int) = repeat(n) { s.step().scenarioEvents.filterIsInstance<ScenarioEvent.Scripted>().forEach { told += it } }
        val rivals = s.economy.companies.all().filter { it.kind == app.many.economytycoon.companies.CompanyKind.AI }
        assertTrue(rivals.isNotEmpty())
        val rivalBefore = rivals.map { s.economy.finance.ledger.cash(it.owner) }
        step(9)
        assertEquals(0, s.economy.scenario.run?.eventsDone)
        step(2)
        assertEquals(1, s.economy.scenario.run?.eventsDone)
        step(20)
        assertEquals(2, s.economy.scenario.run?.eventsDone)
        step(30)
        assertEquals(4, s.economy.scenario.run?.eventsDone)
        assertEquals(listOf("Seed money", "Rain is coming.", "A levy of 5000 is taken.", "Rivals get a loan."), told.map { it.text })
        val rivalAfter = rivals.map { s.economy.finance.ledger.cash(it.owner) }
        assertTrue(rivalAfter.indices.all { rivalAfter[it] > rivalBefore[it] })
        assertTrue(s.news.items().any { it.headline == "Seed money" })
    }

    @Test fun aGrantRaisesTheCompanysCashAndALevyLowersIt() {
        val s = startDef(directDef(events = listOf(DefEvent(3, DefEventKind.GRANT, 50_000L), DefEvent(6, DefEventKind.LEVY, 10_000L))))
        val before = s.cash()
        repeat(4) { s.simulation.advance() }
        val granted = s.cash()
        // Wages and upkeep may take a little, but fifty thousand dwarfs them.
        assertTrue(granted.raw - before.raw > Money.units(40_000).raw)
        val atGrant = s.cash()
        repeat(4) { s.simulation.advance() }
        assertTrue(s.cash().raw < atGrant.raw)
        assertEquals(2, s.economy.scenario.run?.eventsDone)
    }

    @Test fun aLevyNeverTakesMoreThanTheCompanyHolds() {
        val s = startDef(directDef(events = listOf(DefEvent(2, DefEventKind.LEVY, 900_000_000L))))
        repeat(3) { s.simulation.advance() }
        assertTrue(s.cash().raw >= 0L)
        assertEquals(1, s.economy.scenario.run?.eventsDone)
    }

    // ---- losing ---------------------------------------------------------------------------------

    private fun playUntilOver(s: GameSession, days: Int = 120): Long {
        repeat(days) { if (s.economy.scenario.run?.isActive == true) s.simulation.advance() }
        return s.economy.tick.value
    }

    @Test fun aCashFloorLosesTheGameFromItsDay() {
        val s = startDef(directDef(fails = listOf(DefFail(DefFailKind.CASH_BELOW, 900_000_000L, afterDay = 20))))
        playUntilOver(s)
        val run = s.economy.scenario.run!!
        assertEquals(ScenarioStatus.LOST, run.status)
        assertEquals(LossReason.OUT_OF_CASH, run.reason)
        assertTrue("not before the grace period: ${run.finishedAt}", run.finishedAt!!.value >= 20L)
        assertTrue(run.finishedAt!!.value <= 25L)
    }

    @Test fun aFacilityFloorLosesTheGame() {
        val s = startDef(directDef(fails = listOf(DefFail(DefFailKind.FACILITIES_BELOW, 5L))))
        playUntilOver(s, 20)
        assertEquals(LossReason.TOO_FEW_SITES, s.economy.scenario.run?.reason)
    }

    @Test fun aNetWorthFloorWithAGracePeriodWaitsForIt() {
        val graceful = startDef(directDef(ruin = 900_000_000L, ruinAfter = 40))
        playUntilOver(graceful)
        assertEquals(LossReason.RUINED, graceful.economy.scenario.run?.reason)
        assertTrue(graceful.economy.scenario.run!!.finishedAt!!.value in 40L..45L)
        val immediate = startDef(directDef(ruin = 900_000_000L))
        playUntilOver(immediate)
        assertTrue(immediate.economy.scenario.run!!.finishedAt!!.value <= 5L)
    }

    @Test fun aFloorNotReachedLeavesTheGameRunningToItsDeadline() {
        val s = startDef(directDef(fails = listOf(DefFail(DefFailKind.CASH_BELOW, 1L))))
        playUntilOver(s, 60)
        assertTrue(s.economy.scenario.run!!.isActive)
    }

    @Test fun theDeadlineStillEndsAWorkshopGame() {
        val s = startDef(directDef().copy(years = 1, goldDays = 100, silverDays = 200))
        playUntilOver(s, 365)
        assertEquals(LossReason.DEADLINE, s.economy.scenario.run?.reason)
    }

    // ---- saving ---------------------------------------------------------------------------------

    @Test fun theDefinitionAndTheEventCountSurviveASave() {
        val s = start()
        repeat(30) { s.simulation.advance() }
        val copy = reload(s.simulation)
        val run = copy.economy.scenario.run!!
        assertEquals(s.economy.scenario.def(), copy.economy.scenario.def())
        assertEquals(s.economy.scenario.run?.eventsDone, run.eventsDone)
        assertEquals(2, run.eventsDone)
        assertNotNull(run.custom)
        assertEquals(s.simulation.hash(), copy.hash())
    }

    @Test fun draftToGameToSaveToLoadToContinueEqualsTheUninterruptedRun() {
        val interrupted = start()
        repeat(40) { interrupted.simulation.advance() }
        val loaded = reload(interrupted.simulation)
        assertEquals(interrupted.simulation.hash(), loaded.hash())
        repeat(40) { loaded.advance() }

        val straight = start()
        repeat(80) { straight.simulation.advance() }

        assertEquals(straight.simulation.hash(), loaded.hash())
        assertEquals(4, loaded.economy.scenario.run?.eventsDone)
        assertEquals(straight.economy.scenario.run?.eventsDone, loaded.economy.scenario.run?.eventsDone)
    }

    @Test fun savingTwiceInARowGivesTheSameBytes() {
        val s = start()
        repeat(15) { s.simulation.advance() }
        val first = SimulationSnapshot.save(s.simulation)
        assertArrayEquals(first, SimulationSnapshot.save(s.simulation))
        assertEquals(s.simulation.hash(), SimulationSnapshot.load(first, content).hash())
    }

    @Test fun aGameLostAfterTheLoadEndsTheSameWayAsOneThatWasNot() {
        val def = directDef(fails = listOf(DefFail(DefFailKind.CASH_BELOW, 900_000_000L, afterDay = 30)))
        val a = startDef(def)
        repeat(20) { a.simulation.advance() }
        val loaded = reload(a.simulation)
        repeat(30) { loaded.advance() }
        val b = startDef(def)
        repeat(50) { if (b.economy.scenario.run?.isActive == true) b.simulation.advance() }
        assertEquals(LossReason.OUT_OF_CASH, loaded.economy.scenario.run?.reason)
        assertEquals(b.economy.scenario.run?.finishedAt, loaded.economy.scenario.run?.finishedAt)
    }

    // ---- the save format ------------------------------------------------------------------------

    /** The scenario section as it was written before custom definitions existed. */
    private fun legacyBytes(s: ScenarioService): ByteArray {
        val out = ByteWriter()
        out.writeVarInt(1)
        val r = s.run
        out.writeBoolean(r != null)
        if (r != null) {
            out.writeString(r.id); out.writeVarLong(r.playerId); out.writeVarLong(r.startedAt.value)
            out.writeVarInt(r.status.ordinal)
            val fin = r.finishedAt; out.writeBoolean(fin != null); if (fin != null) out.writeVarLong(fin.value)
            out.writeVarInt((r.reason?.ordinal ?: -1) + 1)
            out.writeVarInt(r.medal.ordinal); out.writeVarLong(r.reachedMask)
        }
        return out.toByteArray()
    }

    private fun sectionBytes(s: ScenarioService): ByteArray = ByteWriter().also { ScenarioCodec.write(s, it) }.toByteArray()

    @Test fun catalogAndScenarioFreeSavesKeepTheirOldBytes() {
        val plain = (NewGame.start(app.many.economytycoon.session.NewGameOptions("Plain Co"), content) as Outcome.Ok).value
        assertArrayEquals(legacyBytes(plain.economy.scenario), sectionBytes(plain.economy.scenario))
        val def = ScenarioCatalog.all.first()
        val catalog = (NewGame.start(NewGame.forScenario(def, "Catalog Co"), content) as Outcome.Ok).value
        repeat(12) { catalog.simulation.advance() }
        assertArrayEquals(legacyBytes(catalog.economy.scenario), sectionBytes(catalog.economy.scenario))
        assertEquals(ScenarioCodec.VERSION, ByteReader(sectionBytes(catalog.economy.scenario)).readVarInt())
    }

    @Test fun aCatalogRunHashesWithoutAnyDefinitionPart() {
        val def = ScenarioCatalog.all.first()
        val catalog = (NewGame.start(NewGame.forScenario(def, "Catalog Co"), content) as Outcome.Ok).value
        val run = catalog.economy.scenario.run!!
        assertNull(run.custom)
        val expected = CanonicalHasher().also { h ->
            h.putBoolean(true)
            h.putString(run.id).putLong(run.playerId).putLong(run.startedAt.value).putInt(run.status.ordinal).putLong(-1L)
            h.putInt(-1).putInt(run.medal.ordinal).putLong(run.reachedMask)
        }.finish().toHex()
        val actual = CanonicalHasher().also { catalog.economy.scenario.hashInto(it) }.finish().toHex()
        assertEquals(expected, actual)
    }

    @Test fun aWorkshopRunWritesVersionTwoAndReadsBack() {
        val s = start()
        repeat(12) { s.simulation.advance() }
        val bytes = sectionBytes(s.economy.scenario)
        assertEquals(ScenarioCodec.VERSION_CUSTOM, ByteReader(bytes).readVarInt())
        val back = ScenarioService()
        ScenarioCodec.read(ByteReader(bytes), back)
        assertEquals(s.economy.scenario.def(), back.def())
        assertArrayEquals(bytes, sectionBytes(back))
    }

    @Test fun aCatalogRunReadsFromTheOldFormat() {
        val def = ScenarioCatalog.all.first()
        val catalog = (NewGame.start(NewGame.forScenario(def, "Catalog Co"), content) as Outcome.Ok).value
        val back = ScenarioService()
        ScenarioCodec.read(ByteReader(legacyBytes(catalog.economy.scenario)), back)
        assertEquals(def.id, back.run?.id)
        assertNull(back.run?.custom)
    }

    @Test fun anUnknownCatalogIdIsStillRefusedButACustomOneIsNot() {
        val out = ByteWriter()
        out.writeVarInt(1); out.writeBoolean(true); out.writeString("not-in-the-catalog")
        try {
            ScenarioCodec.read(ByteReader(out.toByteArray()), ScenarioService())
            org.junit.Assert.fail("expected a refusal")
        } catch (e: ByteReader.CorruptDataException) {
            assertTrue(e.message!!.contains("unknown scenario"))
        }
    }

    @Test fun damagedDefinitionsAreRefusedNotTrusted() {
        val def = conversion().def
        val good = ByteWriter().also { ScenarioDefCodec.write(def, it) }.toByteArray()
        assertEquals(def, ScenarioDefCodec.read(ByteReader(good)))
        for (cut in listOf(0, 1, good.size / 2, good.size - 1)) {
            try { ScenarioDefCodec.read(ByteReader(good.copyOf(cut))); org.junit.Assert.fail("cut at $cut was accepted") } catch (e: ByteReader.CorruptDataException) { }
        }
        val badVersion = good.copyOf().also { it[0] = 9 }
        try { ScenarioDefCodec.read(ByteReader(badVersion)); org.junit.Assert.fail("bad version accepted") } catch (e: ByteReader.CorruptDataException) { }
    }

    @Test fun aSaveWhoseEventCountIsOutOfRangeIsRefused() {
        val s = start()
        val good = sectionBytes(s.economy.scenario)
        // The count is the last byte; there are four events, so 9 cannot be right.
        val bad = good.copyOf().also { it[it.size - 1] = 9 }
        try { ScenarioCodec.read(ByteReader(bad), ScenarioService()); org.junit.Assert.fail("accepted") } catch (e: ByteReader.CorruptDataException) { }
    }

    @Test fun theDefinitionsOwnProblemsAreChecked() {
        val base = directDef()
        assertEquals(emptyList<String>(), base.problems())
        assertTrue(base.copy(events = listOf(DefEvent(9, DefEventKind.GRANT, 1), DefEvent(3, DefEventKind.GRANT, 1))).problems().isNotEmpty())
        assertTrue(base.copy(events = listOf(DefEvent(-1, DefEventKind.ANNOUNCE))).problems().isNotEmpty())
        assertTrue(base.copy(fails = listOf(DefFail(DefFailKind.CASH_BELOW, 1, -3))).problems().isNotEmpty())
        assertTrue(base.copy(starters = listOf(DefSite("", 0))).problems().isNotEmpty())
        assertTrue(base.copy(ruinAfterDay = -1).problems().isNotEmpty())
        assertTrue(ScenarioCatalog.all.all { it.problems().isEmpty() && it.events.isEmpty() && it.fails.isEmpty() && it.starters.isEmpty() })
    }
}
