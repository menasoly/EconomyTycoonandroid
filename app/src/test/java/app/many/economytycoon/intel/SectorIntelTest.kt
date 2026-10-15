package app.many.economytycoon.intel

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.production.Facility
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorIntelTest {
    private val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
    private val e = sim.economy
    private val country = e.world.countries().first()
    private val region = e.world.regionsOf(country.id).first().id
    private val intel = SectorIntel(e)

    private fun company(name: String, n: Long): CompanyId =
        CompanyId((sim.execute(Actor.Player(n), Command.FoundCompany(name, country.id, null, Money.units(900_000))) as Outcome.Ok).value.created!!)

    private fun farm(c: CompanyId, recipe: String? = "grow_wheat"): Facility {
        val f = e.production.grantFacility(c, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        if (recipe != null) e.production.setRecipe(c, f.id, f.lines[0].id, RecipeId(recipe)).getOrThrow()
        return f
    }

    @Test fun anEmptyWorldHasNoSectors() {
        assertTrue(intel.flows().isEmpty())
        assertTrue(intel.sectors().isEmpty())
        assertNull(intel.sector(GoodId("wheat")))
    }

    @Test fun aRecipeOnALineShowsUpAsAProducer() {
        val a = company("Alpha Farms", 1)
        farm(a)
        val s = intel.sector(GoodId("wheat"))!!
        assertEquals(a, s.leader!!.company)
        assertEquals("Alpha Farms", s.leader!!.name)
        assertEquals(1_000_000L, s.shareOf(a))
        assertEquals(Concentration.MONOPOLY, s.concentration)
    }

    @Test fun inputsShowUpAsIndustryUse() {
        val a = company("Alpha Farms", 1)
        farm(a)
        val seeds = intel.sector(GoodId("seeds"))!!
        assertEquals(Balance.UNMADE, seeds.balance)
        assertTrue(seeds.industryUsePerDay > 0L)
    }

    @Test fun aLineWithoutARecipeIsIgnored() {
        farm(company("Alpha Farms", 1), recipe = null)
        assertTrue(intel.flows().isEmpty())
    }

    @Test fun aLineSwitchedOffIsIgnored() {
        val a = company("Alpha Farms", 1)
        val f = farm(a)
        e.production.setLineEnabled(a, f.id, f.lines[0].id, false)
        assertTrue(intel.flows().isEmpty())
    }

    @Test fun twoGrowersSplitTheSector() {
        val a = company("Alpha Farms", 1); val b = company("Beta Farms", 2)
        farm(a); farm(b); farm(b)
        val s = intel.sector(GoodId("wheat"))!!
        assertEquals(2, s.producers.size)
        assertEquals(b, s.leader!!.company)
        assertEquals(1, s.rankOf(b))
        assertEquals(2, s.rankOf(a))
        assertTrue(s.shareOf(b) > 650_000L && s.shareOf(b) < 680_000L)
    }

    @Test fun positionsListTheGoodsACompanyMakesBiggestShareFirst() {
        val a = company("Alpha Farms", 1)
        farm(a); farm(a, "grow_corn")
        val pos = intel.positionsOf(a)
        assertEquals(2, pos.size)
        assertTrue(pos.all { it.second == 1_000_000L })
        assertTrue(intel.positionsOf(company("Idle Works", 2)).isEmpty())
    }

    @Test fun neededButUnmadeGoodsAreOpenings() {
        farm(company("Alpha Farms", 1))
        val open = intel.openings().map { it.good.key }
        assertTrue(open.toString(), open.contains("fertilizer") || open.contains("seeds"))
        assertFalse(open.contains("corn"))
    }

    @Test fun aShutDownFacilityNoLongerCounts() {
        val a = company("Alpha Farms", 1)
        val f = farm(a)
        e.production.shutdown(a, f.id)
        assertNotNull(f)
        assertTrue(intel.flows().isEmpty())
    }

    @Test fun priceRatioStartsAtTheReferencePrice() {
        farm(company("Alpha Farms", 1))
        assertEquals(1_000_000L, intel.sector(GoodId("wheat"))!!.priceRatioPpm)
    }
}
