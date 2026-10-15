package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.competitors.AiAction
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDriverTest {
    private val sim = SimTestSupport.sim(12, 3)
    private val e = sim.economy
    private val driver = AiDriver(e, sim.executor)
    private val ai = e.companies.ofKind(CompanyKind.AI).sortedBy { it.id }

    @Test fun situationsDescribeCashFacilitiesAndRules() {
        val c = ai.first()
        val s = driver.situation(c.id, e.tick)
        assertEquals(e.finance.ledger.cash(c.owner), s.cash)
        assertEquals(1, s.facilities.size)
        assertTrue(s.facilities.single().operating)
        assertTrue(s.sellRules.isNotEmpty() && s.buyRules.isNotEmpty())
        assertTrue(s.operatingCostPerTick.raw > 0)
        assertTrue(s.homeRegions.isNotEmpty())
        assertTrue(s.availableFacilityTypes.isNotEmpty() && s.availableRecipes.isNotEmpty())
    }

    @Test fun techGatedThingsAreHiddenUntilResearched() {
        val c = ai.first()
        val gated = e.content.allFacilityTypes.first { it.requiredTech != null }
        assertTrue(gated.id !in driver.situation(c.id, e.tick).availableFacilityTypes)
        e.research.grant(c.id, gated.requiredTech!!, e.tick)
        assertTrue(gated.id in driver.situation(c.id, e.tick).availableFacilityTypes)
    }

    @Test fun targetsExcludeSelfAndStateEnterprises() {
        val c = ai.first()
        val targets = driver.situation(c.id, e.tick).targets
        assertTrue(targets.none { it.company == c.id })
        assertTrue(targets.none { e.companies.require(it.company).kind == CompanyKind.STATE })
        assertEquals(ai.size - 1, targets.size)
    }

    @Test fun simpleActionsMapOneToOne() {
        val c = ai.first().id
        val f = e.production.facilitiesOf(c).first()
        assertEquals(listOf<Command>(Command.Shutdown(c, f.id)), driver.toCommands(AiAction.Shutdown(c, f.id)))
        assertEquals(listOf<Command>(Command.SetBudget(c, Department.MARKETING, Money.units(5))), driver.toCommands(AiAction.SetBudget(c, Department.MARKETING, Money.units(5))))
        assertEquals(listOf<Command>(Command.SetDividend(c, DividendPolicy(1, 2, 3))), driver.toCommands(AiAction.SetDividend(c, DividendPolicy(1, 2, 3))))
    }

    @Test fun extractionBuildsSurveyAndClaimADepositFirst() {
        val c = ai.first().id
        val mine = e.content.allFacilityTypes.first { it.extracts != null && it.requiredTech == null }
        val region = e.world.regions().first { r -> e.world.deposits.unclaimed(r.id).any { it.good == mine.extracts } }.id
        val cmds = driver.toCommands(AiAction.Build(c, mine.id, region))
        assertTrue(cmds.first() is Command.SurveyDeposit || cmds.first() is Command.ClaimDeposit)
        assertTrue(cmds.last() is Command.Build)
        assertTrue((cmds.last() as Command.Build).deposit != null)
    }

    @Test fun extractionWithoutADepositProducesNothing() {
        val c = ai.first().id
        val mine = e.content.allFacilityTypes.first { it.extracts != null && it.requiredTech == null }
        val region = RegionId(9_999)
        assertTrue(driver.toCommands(AiAction.Build(c, mine.id, region)).isEmpty())
    }

    @Test fun plainBuildsPickTheBiggestCityOfTheRegion() {
        val c = ai.first().id
        val region = e.world.regions().first().id
        val cmd = driver.toCommands(AiAction.Build(c, FacilityTypeId("grain_farm"), region)).single() as Command.Build
        assertEquals(e.world.citiesIn(region).maxByOrNull { e.world.population(it.id).total }?.id, cmd.city)
    }

    @Test fun roundsOnlyInvolveCompaniesWhoseTurnItIs() {
        var thinking = 0
        for (t in 0L until 60L) thinking += driver.run(app.many.economytycoon.core.Tick(t)).companiesThinking
        assertTrue(thinking > 0)
        assertTrue(thinking < 60 * ai.size)
    }
}
