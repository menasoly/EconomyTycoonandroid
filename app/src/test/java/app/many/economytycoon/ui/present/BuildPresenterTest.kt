package app.many.economytycoon.ui.present

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPresenterTest {
    private val session = PresentSupport.session("Foundry Works", 12L)
    private val region = session.economy.world.regions().first().id

    @Test fun everyFacilityTypeIsListedWithACost() {
        val m = BuildPresenter.build(session, region)!!
        assertEquals(session.economy.content.allFacilityTypes.size, m.items.size)
        assertTrue(m.items.all { it.cost.isNotBlank() && it.summary.contains("production lines") })
        assertEquals(IndustryFamily.values().size + 1, m.families.size)
    }

    @Test fun familyAndAffordabilityFiltersApply() {
        val all = BuildPresenter.build(session, region)!!.items
        val family = session.economy.content.allFacilityTypes.first().family
        val one = BuildPresenter.build(session, region, family)!!.items
        assertTrue(one.isNotEmpty() && one.size <= all.size && one.all { it.family == BuildPresenter.familyLabel(family) })
        val ok = BuildPresenter.build(session, region, onlyBuildable = true)!!.items
        assertTrue(ok.all { it.canBuild && it.blockers.isEmpty() })
    }

    @Test fun blockedItemsExplainWhy() {
        val blocked = BuildPresenter.build(session, region)!!.items.filter { !it.canBuild }
        assertTrue(blocked.isNotEmpty())
        assertTrue(blocked.all { it.blockers.isNotEmpty() })
    }

    @Test fun commandsOnlyExistForBuildableTypes() {
        val m = BuildPresenter.build(session, region)!!
        val good = m.items.first { it.canBuild && it.deposits.isEmpty() }
        val cmd = BuildPresenter.command(session, good.type, region, "  New site  ")!!
        assertEquals("New site", cmd.name)
        val blocked = m.items.first { !it.canBuild }
        assertNull(BuildPresenter.command(session, blocked.type, region, "x"))
    }

    @Test fun unknownRegionsHaveNoModel() {
        assertNull(BuildPresenter.build(session, RegionId(9999)))
    }

    @Test fun depositActionsBuildCommands() {
        val item = BuildPresenter.build(session, region)!!.items.firstOrNull { it.deposits.isNotEmpty() }
        if (item != null) {
            val d = item.deposits.first()
            assertTrue(BuildPresenter.surveyCommand(session, d.id) is Command.SurveyDeposit)
            assertTrue(BuildPresenter.claimCommand(session, d.id) is Command.ClaimDeposit)
            assertFalse(d.yours)
        } else assertNotNull(item ?: "no deposits in this region")
    }
}
