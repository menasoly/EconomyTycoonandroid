package app.many.economytycoon.talent

import app.many.economytycoon.competitors.Archetype
import app.many.economytycoon.competitors.Personalities
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalentAiTest {
    private val c1 = CompanyId(1)
    private val c2 = CompanyId(2)

    private fun plan(sc: Scenario, who: CompanyId, a: Archetype, ai: TalentAi = TalentAi(sc.system)): List<TalentCommand> =
        ai.plan(who, Personalities.of(a), Tick(sc.day), sc.inputs(), sc.world)

    @Test fun stancesFollowCharacter() {
        val rough = TalentStance.of(Personalities.of(Archetype.DISCOUNTER))
        val careful = TalentStance.of(Personalities.of(Archetype.CONSERVATIVE))
        assertTrue(rough.poachingPpm > careful.poachingPpm)
        assertTrue(careful.retentionPpm > rough.retentionPpm)
        assertTrue(careful.successionPpm > rough.successionPpm)
        val builder = TalentStance.of(Personalities.of(Archetype.EXPANDER))
        assertTrue(builder.educationPpm > careful.educationPpm)
    }

    @Test fun everyStanceStaysInRange() {
        for (a in Archetype.values()) {
            val s = TalentStance.of(Personalities.of(a))
            for (v in listOf(s.trainingPpm, s.poachingPpm, s.brandPpm, s.retentionPpm, s.successionPpm, s.educationPpm)) assertTrue(v in 0L..950_000L)
        }
    }

    @Test fun aCompanyWithNobodyPlansNothing() {
        val sc = Scenario()
        assertTrue(plan(sc, CompanyId(9), Archetype.BALANCED).isEmpty())
    }

    @Test fun plansAreShort() {
        val sc = Scenario(); sc.run(60)
        for (a in Archetype.values()) assertTrue(plan(sc, c1, a).size <= TalentAiParams().maxActionsPerRound)
    }

    @Test fun planningDoesNotChangeTheState() {
        val sc = Scenario(); sc.run(60)
        val before = sc.system.stateHash()
        repeat(3) { plan(sc, c1, Archetype.EXPANDER) }
        assertEquals(before, sc.system.stateHash())
    }

    @Test fun unhappyCheaplyPaidPeopleGetARetentionGrant() {
        val sc = Scenario(); sc.run(40)
        sc.workforce.rows.getValue(sc.machinists1).morale = 200_000L
        val plan = plan(sc, c1, Archetype.CONSERVATIVE)
        assertTrue(plan.any { it is TalentCommand.GrantRetention && it.cohort == sc.machinists1 })
    }

    @Test fun carelessCompaniesDoNotBother() {
        val sc = Scenario(); sc.run(40)
        sc.workforce.rows.getValue(sc.machinists1).morale = 200_000L
        val ai = TalentAi(sc.system)
        val plan = ai.plan(c1, Personalities.of(Archetype.DISCOUNTER), Tick(sc.day), sc.inputs(), sc.world)
        assertFalse(plan.any { it is TalentCommand.GrantRetention })
    }

    @Test fun shortStaffedHotMarketsInviteAggressivePoaching() {
        val sc = Scenario(); sc.run(240)
        sc.workforce.rows.getValue(sc.machinists1).target = 400L
        val offers = plan(sc, c1, Archetype.DISCOUNTER, TalentAi(sc.system, TalentAiParams(maxActionsPerRound = 9))).filterIsInstance<TalentCommand.SubmitOffer>()
        assertTrue("premium=${sc.system.markets.premium(RegionId(1), DefaultTaxonomy.MACHINING)} rows=${sc.workforce.rows.mapValues { it.value.heads }}", offers.isNotEmpty())
        val offer = offers.first()
        assertEquals(sc.machinists1, offer.destination)
        assertTrue(offer.target is PoachTarget.Group && (offer.target as PoachTarget.Group).cohort.company == c2)
        assertTrue(offer.offeredWage > sc.workforce.rows.getValue(sc.machinists2).wage)
    }

    @Test fun gentleCompaniesDoNotPoach() {
        val sc = Scenario(); sc.run(60)
        assertTrue(plan(sc, c1, Archetype.CONSERVATIVE).none { it is TalentCommand.SubmitOffer })
    }

    @Test fun poorCompaniesDoNotSpendOnPeople() {
        val sc = Scenario(); sc.run(60)
        sc.workforce.fund(1, 1)
        val plan = plan(sc, c1, Archetype.DISCOUNTER)
        assertTrue(plan.none { it is TalentCommand.SubmitOffer || it is TalentCommand.LaunchCampaign || it is TalentCommand.StartTraining || it is TalentCommand.GrantRetention })
    }

    @Test fun openingsBringARecruitingCampaign() {
        val sc = Scenario(); sc.run(30)
        sc.workforce.rows.getValue(sc.machinists1).target = 200L
        val plan = plan(sc, c1, Archetype.EXPANDER)
        assertTrue(plan.any { it is TalentCommand.LaunchCampaign && it.role == sc.machinists1.role })
    }

    @Test fun everyCommandThePlannerMakesIsAccepted() {
        val sc = Scenario(); sc.run(60)
        val ai = TalentAi(sc.system)
        for (a in Archetype.values()) {
            for (who in listOf(c1, c2)) {
                for (cmd in ai.plan(who, Personalities.of(a), Tick(sc.day), sc.inputs(), sc.world)) {
                    val r = sc.system.execute(cmd, Tick(sc.day), sc.inputs(), sc.world)
                    assertTrue("$a ${cmd::class.simpleName} rejected: ${r.rejectionOrNull()}", r is Outcome.Ok)
                }
            }
        }
    }

    @Test fun twoYearsOfRivalsKeepTheStateSoundAndRepeatable() {
        fun play(seed: Long): app.many.economytycoon.core.StateHash {
            val sc = Scenario(seed)
            val ai = TalentAi(sc.system)
            val kinds = listOf(c1 to Archetype.DISCOUNTER, c2 to Archetype.PREMIUM)
            repeat(72) { round ->
                sc.run(10)
                for ((who, a) in kinds) for (cmd in ai.plan(who, Personalities.of(a), Tick(sc.day), sc.inputs(), sc.world)) {
                    val r = sc.system.execute(cmd, Tick(sc.day), sc.inputs(), sc.world)
                    sc.intents += (r as? Outcome.Ok)?.value?.intents ?: emptyList()
                }
                assertEquals(emptyList<String>(), TalentInvariants.violations(sc.system.state, sc.taxonomy, null, Tick(sc.day)))
            }
            return sc.system.stateHash()
        }
        assertEquals(play(4L), play(4L))
    }

    @Test fun rivalsOpenProvidersWhereTheMarketIsShort() {
        val sc = Scenario(); sc.run(60)
        val ai = TalentAi(sc.system)
        val builder = Personalities.of(Archetype.EXPANDER)
        val first = ai.planProvider(c1, builder, Tick(sc.day), sc.inputs(), sc.world)
        assertTrue(first is TalentCommand.OpenProvider)
        assertTrue(sc.system.execute(first!!, Tick(sc.day), sc.inputs(), sc.world) is Outcome.Ok)
        val second = ai.planProvider(c1, builder, Tick(sc.day), sc.inputs(), sc.world)
        assertTrue(second is TalentCommand.AddCourse)
        assertTrue(sc.system.execute(second!!, Tick(sc.day), sc.inputs(), sc.world) is Outcome.Ok)
        assertEquals(null, ai.planProvider(c1, builder, Tick(sc.day), sc.inputs(), sc.world))
        assertEquals(null, ai.planProvider(c1, Personalities.of(Archetype.CONSERVATIVE), Tick(sc.day), sc.inputs(), sc.world))
        assertTrue(RegionId(1) == sc.system.providers.providersOf(c1).first().region)
    }

    @Test fun vacantKeyPostsAreFilledByTheBestAvailablePerson() {
        val sc = Scenario(); sc.run(10)
        val exec = app.many.economytycoon.core.RoleId("executive")
        sc.system.succession.openPosition(c1, exec, RegionId(1), "CEO")
        sc.system.succession.addPerson(c1, "Ada", exec, RegionId(1), Money.units(300), 50, Tick(0))
        val plan = plan(sc, c1, Archetype.BALANCED)
        assertTrue(plan.any { it is TalentCommand.AppointPerson })
    }
}
