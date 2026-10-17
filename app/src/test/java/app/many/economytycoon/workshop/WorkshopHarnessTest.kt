package app.many.economytycoon.workshop

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.scenarios.GoalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopHarnessTest {
    private val harness = WorkshopHarness(Samples.content)
    private val quick = HarnessSettings(days = 30, sampleEvery = 10)

    private fun run(d: ScenarioDraft, kind: PolicyKind = PolicyKind.IDLE, settings: HarnessSettings = quick, seed: Long = d.seed): RunMetrics {
        val r = harness.run(d, kind, settings, seed)
        assertTrue("run refused: $r", r is Outcome.Ok)
        return (r as Outcome.Ok).value
    }

    @Test fun aDraftWithErrorsCannotBePlayed() {
        val r = harness.run(Samples.cheap().copy(goals = emptyList()), PolicyKind.IDLE, quick)
        assertTrue(r is Outcome.Rejected)
        assertEquals(Rules.INVALID_ARGUMENT, (r as Outcome.Rejected).rejection.rule)
        assertTrue(r.rejection.message, r.rejection.message.contains("goals"))
    }

    @Test fun samplesAreTakenOnSchedule() {
        val m = run(Samples.cheap())
        assertEquals(listOf(0, 10, 20, 30), m.samples.map { it.day })
        assertEquals(30, m.days)
        assertEquals(RunEnd.HORIZON, m.end)
    }

    @Test fun theLastPartialIntervalIsSampled() {
        val m = run(Samples.cheap(), settings = HarnessSettings(days = 25, sampleEvery = 10))
        assertEquals(listOf(0, 10, 20, 25), m.samples.map { it.day })
    }

    @Test fun theCompanyStartsWithItsCapitalAndItsStartingSite() {
        val m = run(Samples.cheap())
        val first = m.samples.first()
        assertEquals(300_000L, first.cash)
        assertEquals(1, first.facilities)
        assertTrue(first.netWorth in 250_000L..320_000L)
        assertEquals(2, first.goalValues.size)
        assertEquals(first.goalValues.size, first.goalPpm.size)
    }

    @Test fun noStartersMeansNoFacilitiesAtFirst() {
        val m = run(Samples.cheap().copy(company = Samples.cheap().company.copy(starters = emptyList())))
        assertEquals(0, m.samples.first().facilities)
    }

    @Test fun theStartingSiteIsStaffedAndEarnsPeople() {
        val m = run(Samples.cheap())
        assertTrue("employees ${m.last.employees}", m.last.employees > 0)
    }

    @Test fun theSameDraftPolicyAndSeedGiveTheSameRun() {
        val a = run(Samples.cheap(), PolicyKind.GREEDY)
        val b = run(Samples.cheap(), PolicyKind.GREEDY)
        assertEquals(a, b)
        assertEquals(a.stateHash, b.stateHash)
    }

    @Test fun anotherSeedGivesAnotherWorld() {
        val a = run(Samples.cheap(), seed = 7L)
        val b = run(Samples.cheap(), seed = 8L)
        assertEquals(7L, a.seed)
        assertEquals(8L, b.seed)
        assertNotEquals(a.stateHash, b.stateHash)
    }

    @Test fun theBooksStayBalancedWhileTheHarnessPlays() {
        val m = run(Samples.cheap(), PolicyKind.GREEDY, HarnessSettings(days = 25, sampleEvery = 25, checkInvariants = true))
        assertEquals(emptyList<String>(), m.invariantProblems)
    }

    // ---- events -------------------------------------------------------------------------------------

    @Test fun aGrantPaysTheCompanyOnItsDay() {
        val plain = run(Samples.cheap())
        val granted = run(Samples.cheap().copy(events = listOf(ScriptedEvent(12, EventKind.GRANT, 90_000, "Gift"))))
        assertEquals(plain.samples[1].cash, granted.samples[1].cash)
        assertEquals(plain.samples[2].cash + 90_000L, granted.samples[2].cash, )
        assertTrue(granted.log.any { it.contains("grant of 90000") && it.contains("Gift") })
    }

    @Test fun aLevyTakesCashButNeverMoreThanTheCompanyHas() {
        val levied = run(Samples.cheap().copy(events = listOf(ScriptedEvent(3, EventKind.LEVY, 50_000_000))))
        assertTrue("cash ${levied.samples[1].cash}", levied.samples[1].cash < 1_000L)
        assertTrue(levied.log.any { it.contains("levy of") })
    }

    @Test fun announcementsOnlyWriteTheLog() {
        val plain = run(Samples.cheap())
        val told = run(Samples.cheap().copy(events = listOf(ScriptedEvent(5, EventKind.ANNOUNCE, 0, "Storm warning"))))
        assertEquals(plain.last.cash, told.last.cash)
        assertTrue(told.log.contains("day 5: Storm warning"))
    }

    @Test fun rivalGrantsAreAppliedToEveryRival() {
        val m = run(Samples.cheap().copy(events = listOf(ScriptedEvent(2, EventKind.RIVAL_GRANT, 1_000))))
        assertTrue(m.log.any { it.contains("2 rivals receive 1000 each") })
    }

    @Test fun eventsAfterTheHorizonNeverHappen() {
        val m = run(Samples.cheap().copy(events = listOf(ScriptedEvent(200, EventKind.ANNOUNCE, 0, "too late"))))
        assertTrue(m.log.none { it.contains("too late") })
    }

    @Test fun aDayZeroEventHappensBeforeTheFirstTick() {
        val m = run(Samples.cheap().copy(events = listOf(ScriptedEvent(0, EventKind.ANNOUNCE, 0, "opening"))))
        assertTrue(m.log.contains("day 0: opening"))
    }

    // ---- ending a run ---------------------------------------------------------------------------------

    @Test fun reachingEveryRequiredGoalEndsTheRunAsAWin() {
        val d = Samples.cheap().copy(
            goals = listOf(DraftGoal(GoalKind.CASH, 340_000)),
            events = listOf(ScriptedEvent(2, EventKind.GRANT, 100_000)),
        )
        val m = run(d, settings = HarnessSettings(days = 60, sampleEvery = 10))
        assertEquals(RunEnd.WON, m.end)
        assertEquals(5, m.allDoneDay)
        assertEquals(5, m.days)
        assertEquals(5, m.goalFirstDay[0])
        assertTrue(m.log.any { it.contains("every required goal is met") })
    }

    @Test fun bonusGoalsDoNotDecideTheWin() {
        val d = Samples.cheap().copy(goals = listOf(DraftGoal(GoalKind.NET_WORTH, 900_000_000), DraftGoal(GoalKind.CASH, 100_000, bonus = true)))
        val m = run(d)
        assertEquals(RunEnd.HORIZON, m.end)
        assertEquals(5, m.goalFirstDay[1])
        assertNull(m.allDoneDay)
    }

    @Test fun aCashFloorLosesTheGame() {
        val d = Samples.cheap().copy(
            events = listOf(ScriptedEvent(1, EventKind.LEVY, 250_000)),
            fails = listOf(FailCondition(FailKind.CASH_BELOW, 100_000)),
        )
        val m = run(d)
        assertEquals(RunEnd.LOST, m.end)
        assertEquals(LossCause.CASH, m.lossCause)
        assertEquals(5, m.days)
        assertTrue(m.log.last().contains("failure condition 1"))
    }

    @Test fun aFailureConditionWaitsForItsGracePeriod() {
        val d = Samples.cheap().copy(
            events = listOf(ScriptedEvent(1, EventKind.LEVY, 250_000)),
            fails = listOf(FailCondition(FailKind.CASH_BELOW, 100_000, afterDay = 20)),
        )
        val m = run(d)
        assertEquals(RunEnd.LOST, m.end)
        assertEquals(20, m.days)
    }

    @Test fun aNetWorthFloorLosesTheGame() {
        val d = Samples.cheap().copy(
            events = listOf(ScriptedEvent(1, EventKind.LEVY, 290_000)),
            fails = listOf(FailCondition(FailKind.NET_WORTH_BELOW, 250_000)),
        )
        val m = run(d)
        assertEquals(LossCause.NET_WORTH, m.lossCause)
    }

    @Test fun tooFewFacilitiesLoseTheGame() {
        val d = Samples.cheap().copy(fails = listOf(FailCondition(FailKind.FACILITIES_BELOW, 2, afterDay = 5)))
        val m = run(d)
        assertEquals(LossCause.FACILITIES, m.lossCause)
        assertEquals(5, m.days)
    }

    @Test fun aLostRunStillHasItsLastSample() {
        val d = Samples.cheap().copy(fails = listOf(FailCondition(FailKind.FACILITIES_BELOW, 2, afterDay = 5)))
        val m = run(d)
        assertEquals(5, m.last.day)
    }

    // ---- stalls ---------------------------------------------------------------------------------------

    @Test fun aCompanyThatCannotAffordAnythingIsStuck() {
        val broke = Samples.cheap().copy(
            company = Samples.cheap().company.copy(capital = 20_000, starters = emptyList()),
            goals = listOf(DraftGoal(GoalKind.FACILITIES, 3)),
        )
        val m = run(broke, settings = HarnessSettings(days = 45, sampleEvery = 15, stallDays = 20))
        val sim = Simulation0.sim(broke)
        val reading = SoftlockProbe.read(sim.economy, companyOf(broke))
        assertTrue(reading.stalled)
        assertTrue(reading.cheapestBuild > 20_000L)
        assertEquals(0, reading.profitableSites)
        assertNotNull("stuck company should be flagged", m.softlockDay)
        assertNotNull(m.softlockNote)
        assertTrue(m.log.any { it.contains("stuck") })
    }

    @Test fun aWorkingCompanyIsNotStuck() {
        assertNull(run(Samples.cheap(), PolicyKind.GREEDY).softlockDay)
    }

    // ---- policies -------------------------------------------------------------------------------------

    @Test fun everyPolicyHasAKeyAndASummary() {
        for (k in PolicyKind.values()) {
            assertEquals(k, PolicyKind.byKey(k.key))
            assertTrue(k.summary.isNotBlank())
            assertEquals(k.key, k.create(Samples.cheap(), Samples.content).name)
        }
        assertNull(PolicyKind.byKey("mystery"))
    }

    @Test fun policiesDiffer() {
        val d = Samples.cheap()
        val hashes = PolicyKind.values().map { run(d, it).stateHash }
        assertEquals("every policy leaves a different world", hashes.size, hashes.toSet().size)
    }

    @Test fun anActivePolicyHiresMorePeopleThanIdle() {
        val d = Samples.cheap()
        val idle = run(d, PolicyKind.IDLE, HarnessSettings(days = 50, sampleEvery = 50))
        val greedy = run(d, PolicyKind.GREEDY, HarnessSettings(days = 50, sampleEvery = 50))
        assertTrue("greedy ${greedy.last.employees} vs idle ${idle.last.employees}", greedy.last.employees > idle.last.employees)
    }

    @Test fun aScriptedPolicyListsWhatItCouldNotDo() {
        val policy = ScriptedPolicy(listOf(ScriptStep.Build(1, "no_such_recipe"), ScriptStep.Research(2, "no_such_tech"), ScriptStep.Repay(3, 5_000)))
        val r = harness.run(Samples.cheap(), policy, HarnessSettings(days = 6, sampleEvery = 6))
        assertTrue(r is Outcome.Ok)
        assertEquals(3, policy.skipped.size)
        assertTrue(policy.skipped[0].contains("no such recipe"))
        assertTrue(policy.skipped[0].startsWith("day 1: build no_such_recipe"))
    }

    @Test fun aScriptedBuildIsStaffedWhenItIsFinished() {
        val d = Samples.cheap()
        val sim = app.many.economytycoon.simulation.Simulation(DraftConverter.config(d), Samples.content)
        val founded = HarnessSetup.found(sim, d).getOrNull() ?: error("founding failed")
        HarnessSetup.placeStarters(sim, d, founded)
        val policy = ScriptedPolicy(listOf(ScriptStep.Build(1, "grow_wheat", 1)))
        val ctx = PolicyContext(sim, founded.company, d)
        policy.attach(ctx)
        repeat(56) { policy.beforeTick(ctx); sim.advance() }
        val e = sim.economy
        assertEquals(emptyList<String>(), policy.skipped)
        val sites = e.production.facilitiesOf(founded.company)
        assertEquals(2, sites.size)
        val staffed = e.workforce.cohortsOf(founded.company).map { it.key.facility }.toSet()
        assertEquals("both sites have crews", sites.map { it.id }.toSet(), staffed)
        assertTrue(sites.all { s -> s.lines.first().recipe != null })
    }

    @Test fun theDefaultScriptBuildsOnTheStartingRecipe() {
        val script = ScriptedPolicy.defaultFor(Samples.cheap(), Samples.content)
        assertTrue(script.filterIsInstance<ScriptStep.Build>().all { it.recipe == "grow_wheat" })
        assertTrue(script.any { it is ScriptStep.Borrow })
        assertEquals(script.sortedBy { it.day }.map { it.day }, script.map { it.day })
    }

    @Test fun theDefaultScriptFallsBackToTheEnginesRecipe() {
        val script = ScriptedPolicy.defaultFor(Samples.cheap().copy(company = Samples.cheap().company.copy(starters = emptyList())), Samples.content)
        assertTrue(script.filterIsInstance<ScriptStep>().isNotEmpty())
    }

    @Test fun stepsDescribeThemselves() {
        assertEquals("day 3: build grow_wheat in region 1", ScriptStep.Build(3, "grow_wheat", 1).toString())
        assertEquals("day 4: borrow 1000 for 90 days", ScriptStep.Borrow(4, 1_000, 90).toString())
        assertEquals("day 5: repay 500", ScriptStep.Repay(5, 500).toString())
        assertEquals("day 6: research t1", ScriptStep.Research(6, "t1").toString())
    }

    @Test fun settingsMustBeSensible() {
        for (bad in listOf({ HarnessSettings(days = 0) }, { HarnessSettings(sampleEvery = 0) }, { HarnessSettings(checkEvery = 0) }, { HarnessSettings(stallDays = 0) })) {
            try { bad(); org.junit.Assert.fail("expected refusal") } catch (e: IllegalArgumentException) { /* expected */ }
        }
        assertFalse(HarnessSettings().checkInvariants)
    }

    @Test fun theEnginesDefaultRecipeIsOpenFromTheStart() {
        val r = SiteOperator.defaultRecipe(Samples.content)
        assertNotNull(r)
        assertEquals("grow_wheat", r?.id?.key)
    }
}
