package app.many.economytycoon.session

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.SimTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPlannerTest {
    private val session = (NewGame.start(NewGameOptions("Planner Works", Difficulty.RELAXED, seed = 9L), SimTestSupport.content) as Outcome.Ok).value
    private val planner = BuildPlanner(session.economy)
    private val company = session.selectedCompany!!
    private val region = session.economy.world.regions().first().id

    @Test fun everyFacilityTypeGetsAnOption() {
        val options = planner.options(company, region)
        assertEquals(session.economy.content.allFacilityTypes.size, options.size)
        assertTrue(options.any { it.canBuild })
    }

    @Test fun buildableOptionsComeFirst() {
        val flags = planner.options(company, region).map { it.canBuild }
        assertEquals(flags.sortedByDescending { it }, flags)
    }

    @Test fun lockedTechnologyIsExplained() {
        val locked = planner.options(company, region).firstOrNull { it.requiredTech != null }
        if (locked != null) assertTrue(locked.blockers.any { it.startsWith("Needs the") })
    }

    @Test fun unaffordableTypesSayWhatTheyCost() {
        val poor = (NewGame.start(NewGameOptions("Poor Planner", Difficulty.CUTTHROAT, seed = 9L), SimTestSupport.content) as Outcome.Ok).value
        val options = BuildPlanner(poor.economy).options(poor.selectedCompany!!, region)
        val dear = options.filter { o -> o.blockers.any { it.startsWith("Costs") } }
        assertTrue(dear.all { it.cost.raw > 0 })
    }

    @Test fun aChosenOptionBecomesACommandThatTheEngineAccepts() {
        val option = planner.options(company, region).first { it.canBuild && !it.needsDeposit }
        val cmd = planner.buildCommand(company, option, region, "First plant")!!
        assertEquals(option.type, cmd.type)
        val result = session.issueNow(cmd)
        assertTrue(result.toString(), result.isOk)
        assertEquals(1, session.economy.production.openProjectsOf(company).size)
    }

    @Test fun blockedOptionsProduceNoCommand() {
        val blocked = planner.options(company, region).firstOrNull { !it.canBuild }
        if (blocked != null) assertNull(planner.buildCommand(company, blocked, region))
    }

    @Test fun extractionNeedsAClaimedDeposit() {
        val extractor = planner.options(company, region).firstOrNull { it.needsDeposit && it.deposits.isNotEmpty() && it.requiredTech == null }
        if (extractor != null) {
            assertTrue(extractor.blockers.any { it == "Claim a deposit first" })
            val dep = extractor.deposits.first()
            val surveyed = session.issueNow(app.many.economytycoon.simulation.Command.SurveyDeposit(company, dep.id))
            val claimed = session.issueNow(app.many.economytycoon.simulation.Command.ClaimDeposit(company, dep.id))
            assertTrue("$surveyed $claimed", claimed.isOk)
            val again = planner.options(company, region).first { it.type == extractor.type }
            assertTrue(again.deposits.first().ownedByYou)
            assertNotNull(planner.buildCommand(company, again, region).takeIf { again.canBuild } ?: planner.buildCommand(company, again.copy(blockers = emptyList()), region))
        }
    }

    @Test fun recipesListUnlockedOnesFirst() {
        val type = session.economy.content.allFacilityTypes.first { session.economy.content.recipesForFacility(it.id).size > 1 }
        val recipes = planner.recipes(company, type.id)
        assertEquals(recipes.map { it.locked }.sortedBy { it }, recipes.map { it.locked })
        assertTrue(recipes.all { it.inputs.isNotEmpty() || it.outputs.isNotEmpty() })
    }
}
