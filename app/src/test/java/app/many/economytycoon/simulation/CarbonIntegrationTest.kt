package app.many.economytycoon.simulation

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.carbon.AbatementEffect
import app.many.economytycoon.carbon.AbatementKind
import app.many.economytycoon.carbon.CarbonCalendar
import app.many.economytycoon.carbon.CarbonCommand
import app.many.economytycoon.carbon.RegimeKind
import app.many.economytycoon.carbon.RegimePresets
import app.many.economytycoon.carbon.Tonnes
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.production.Facility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The carbon package as the simulation wires it: policies, commands, emissions from real production, snapshots, replay and the computer competitors. */
class CarbonIntegrationTest {
    private class Rig(seed: Long = 9L, ai: Int = 0, grids: Boolean = false) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().sortedBy { it.id }.first()
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Smoke Works", country.id, null, Money.units(5_000_000))) as Outcome.Ok).value.created!!)
        init { if (grids) sim.execute(Actor.System, Command.EstablishGrids) }
        val region = e.world.regionsOf(country.id).sortedBy { it.id }.first().id

        /** A coal power plant with its burn recipe set, stocked and staffed for a while. */
        fun coalPlant(): Facility {
            val f = e.production.grantFacility(me, FacilityTypeId("coal_power_plant"), region, null, null, null, e.tick)
            e.production.setRecipe(me, f.id, f.lines[0].id, RecipeId("burn_coal"))
            f.inventory.add(app.many.economytycoon.core.GoodId("coal"), Quantity.units(30_000), Money.units(400_000))
            f.inventory.add(app.many.economytycoon.core.GoodId("water"), Quantity.units(12_000), Money.units(20_000))
            return f
        }

        /** Puts a full crew on the plant, as if hired long ago. */
        fun crew(f: Facility) {
            for (role in e.content.recipe(RecipeId("burn_coal")).labor) {
                val c = e.workforce.ensure(app.many.economytycoon.workforce.CohortKey(me, f.id, role.role, f.region))
                c.headcount = role.headcount.toLong() * 2; c.target = c.headcount; c.moralePpm = 800_000L
            }
        }

        /** Puts a tax on every tonne the country's heavy industry releases, from the first day. */
        fun taxEverything(rate: Long = 40) {
            val regime = RegimePresets.carbonTax(country.id, CarbonCalendar.yearOf(e.tick), Money.units(rate), coverage = RegimePresets.HEAVY, threshold = Tonnes.ZERO)
            assertTrue(e.carbon.installRegime(regime, e.tick) is Outcome.Ok)
        }

        fun cash(c: CompanyId = me) = e.finance.ledger.cash(LedgerOwner.company(c))
    }

    @Test fun aGameWithoutCarbonPolicyLeavesNoStateNoStreamAndNoSnapshotSection() {
        val r = Rig(ai = 3)
        r.sim.advance(40)
        assertTrue(r.e.carbonState.isPristine)
        assertFalse(r.e.carbonRunner.active)
        assertTrue(r.e.random.snapshot().keys.none { it.startsWith("carbon") })
        assertTrue(app.many.economytycoon.persistence.SectionedFile.read(SimulationSnapshot.save(r.sim)).none { it.name == "carbon" })
    }

    @Test fun onlyTheSystemSetsThePoliciesAndTheyFollowTheCountriesRules() {
        val r = Rig()
        assertTrue(r.sim.execute(Actor.Player(1), Command.EstablishCarbonPolicies(0)) is Outcome.Rejected)
        assertTrue(r.e.carbonState.isPristine)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0)) is Outcome.Ok)
        for (c in r.e.world.countries()) {
            val expected = RegimePresets.forStrictness(c.id, c.environmentalStrictnessPpm, CarbonCalendar.yearOf(r.e.tick)).kind
            assertEquals(expected, r.e.carbonState.regime(c.id)?.kind ?: RegimeKind.NONE)
        }
        assertTrue(r.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0)) is Outcome.Rejected)
    }

    @Test fun aDelayStartsThePolicyInALaterYear() {
        val r = Rig()
        r.sim.execute(Actor.System, Command.EstablishCarbonPolicies(2))
        val started = r.e.carbonState.regimes().flatMap { it.steps }.map { it.fromYear }.toSet()
        assertEquals(setOf(CarbonCalendar.yearOf(r.e.tick) + 2), started)
    }

    @Test fun carbonCommandsAreRefusedWithoutAPolicyAndByTheWrongPlayer() {
        val r = Rig()
        val buy = Command.BuyCarbonOffsets(r.me, app.many.economytycoon.carbon.OffsetKind.METHANE_CAPTURE, Tonnes.of(10))
        assertEquals(Rules.WRONG_STATE, r.sim.execute(Actor.Player(1), buy).rejectionOrNull()?.rule)
        r.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0))
        r.sim.advance(1)
        assertEquals(Rules.NOT_OWNER, r.sim.execute(Actor.Player(2), buy).rejectionOrNull()?.rule)
        val before = r.cash()
        val bad = r.sim.execute(Actor.Player(1), Command.BuyCarbonOffsets(r.me, app.many.economytycoon.carbon.OffsetKind.METHANE_CAPTURE, Tonnes.ZERO))
        assertTrue(bad is Outcome.Rejected)
        assertEquals(before, r.cash())
    }

    @Test fun buyingCreditsMovesTheMoneyAtOnce() {
        val r = Rig()
        r.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0))
        r.sim.advance(2)
        val before = r.cash()
        val ok = r.sim.execute(Actor.Player(1), Command.BuyCarbonOffsets(r.me, app.many.economytycoon.carbon.OffsetKind.METHANE_CAPTURE, Tonnes.of(20)))
        assertTrue(ok.toString(), ok is Outcome.Ok)
        assertTrue(r.cash() < before)
        assertTrue(r.e.carbonState.offsets.held(r.me, app.many.economytycoon.carbon.OffsetKind.METHANE_CAPTURE).isPositive)
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun carbonCommandsRoundTripThroughTheCodecAndDescriber() {
        val cs = CommandSamples.all().filter { CarbonCommandMap.of(it) != null || it is Command.EstablishCarbonPolicies }
        assertEquals(15, cs.size)
        for (c in cs) {
            val w = ByteWriter(); CommandCodec.writeCommand(c, w)
            assertEquals(c, CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertEquals(ActivityArea.PUBLIC, CommandDescriber.area(c))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
            CarbonCommandMap.of(c)?.let { assertEquals(c, CarbonCommandMap.toCommand(it)) }
        }
    }

    @Test fun aPlantOnAGridIsChargedForTheFuelItBurns() {
        val r = Rig(grids = true)
        val plant = r.coalPlant()
        r.taxEverything()
        val start = r.cash()
        r.sim.advance(65)
        val record = r.e.carbonState.inventory.facility(plant.id)
        assertTrue("the plant is on the books", record != null)
        val year = CarbonCalendar.yearOf(r.e.tick)
        val mine = r.e.carbonState.inventory.company(r.me, r.country.id, year)
        assertTrue("it released something", mine != null && mine.footprint.isPositive)
        assertTrue("and was taxed for it", r.e.carbonState.receipts(r.country.id).isPositive)
        assertTrue(r.cash() < start || r.e.carbonState.compliance.account(r.me, r.country.id) != null)
        assertTrue(Invariants.check(r.e).isEmpty())
    }

    @Test fun aPlantBurningItsOwnFuelIsAccountedFromTheProductionReport() {
        val r = Rig()
        val plant = r.coalPlant()
        r.taxEverything()
        r.crew(plant)
        r.sim.advance(40)
        val mine = r.e.carbonState.inventory.company(r.me, r.country.id, CarbonCalendar.yearOf(r.e.tick))
        assertTrue("batches ran: ${plant.lines[0].batchesCompleted}", plant.lines[0].batchesCompleted > 0)
        assertTrue(mine != null && mine.footprint.isPositive)
        assertTrue(r.e.carbonState.receipts(r.country.id).isPositive)
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun anEfficiencyRetrofitMakesAFacilityBurnLessFuel() {
        val plain = Rig(); val fitted = Rig()
        val a = plain.coalPlant(); val b = fitted.coalPlant()
        for (rig in listOf(plain, fitted)) rig.taxEverything()
        plain.crew(a); fitted.crew(b)
        plain.sim.advance(3); fitted.sim.advance(3)
        fitted.e.carbonState.inventory.facility(b.id)!!.installed += AbatementEffect(AbatementKind.EFFICIENCY_RETROFIT, 200_000L)
        val coal = app.many.economytycoon.core.GoodId("coal")
        val aBefore = a.inventory.quantity(coal).raw; val bBefore = b.inventory.quantity(coal).raw
        plain.sim.advance(20); fitted.sim.advance(20)
        val usedPlain = aBefore - a.inventory.quantity(coal).raw
        val usedFitted = bBefore - b.inventory.quantity(coal).raw
        assertTrue("plain burned $usedPlain", usedPlain > 0L)
        assertTrue("fitted $usedFitted burned less than plain $usedPlain", usedFitted < usedPlain)
        assertTrue(usedFitted > usedPlain * 7 / 10)
    }

    @Test fun savingAndLoadingContinuesExactly() {
        val r = Rig(seed = 21L, ai = 4, grids = true)
        r.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0))
        r.sim.advance(70)
        assertFalse(r.e.carbonState.isPristine)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertEquals(r.sim.hash(), copy.hash())
        assertEquals(r.e.carbonState.stateHash(), copy.economy.carbonState.stateHash())
        for (i in 0 until 40) { r.sim.advance(); copy.advance(); assertEquals("tick ${r.sim.tick.value}", r.sim.hash(), copy.hash()) }
    }

    @Test fun thePolicyChangesTheHashAndReplaysIdentically() {
        val a = Rig(seed = 33L, ai = 4, grids = true)
        val plain = Simulation(SimTestSupport.config(33L, 4), SimTestSupport.content, bootstrap = true)
        plain.execute(Actor.System, Command.EstablishGrids)
        plain.execute(Actor.Player(1), Command.FoundCompany("Smoke Works", a.country.id, null, Money.units(5_000_000)))
        a.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0))
        a.sim.advance(100); plain.advance(100)
        val again = Simulation.replay(a.sim.config, SimTestSupport.content, a.sim.commandLog(), a.sim.tick.value)
        assertEquals(a.sim.hash(), again.hash())
        assertNotEquals(plain.hash(), a.sim.hash())
        assertTrue(Invariants.check(a.e).isEmpty())
    }

    @Test fun aMultiMonthRunWithTheComputerCompetitorsIsDeterministicAndBalanced() {
        val x = Rig(seed = 41L, ai = 5, grids = true); val y = Rig(seed = 41L, ai = 5, grids = true)
        x.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0)); y.sim.execute(Actor.System, Command.EstablishCarbonPolicies(0))
        x.sim.advance(130); y.sim.advance(130)
        assertEquals(x.sim.hash(), y.sim.hash())
        assertTrue(Invariants.check(x.e).isEmpty())
        assertTrue(x.e.finance.invariants().isEmpty())
    }

    @Test fun retiringACompanyDoesNotBreakTheCarbonBooks() {
        val r = Rig(grids = true)
        r.coalPlant(); r.taxEverything()
        r.sim.advance(40)
        r.e.retire(r.me)
        r.sim.advance(40)
        assertTrue(Invariants.check(r.e).isEmpty())
    }

    @Test fun theCarbonPriceReachesTheGridDispatch() {
        val r = Rig(grids = true)
        val grid = r.e.energyGrids.gridOfRegion(r.region)!!
        assertTrue(r.e.carbonRunner.gridCarbonPrices(r.e.energyGrids.grids, r.e.tick).isEmpty())
        r.taxEverything()
        val prices = r.e.carbonRunner.gridCarbonPrices(r.e.energyGrids.grids, r.e.tick)
        assertTrue(prices[grid.id]?.isPositive == true)
        assertTrue(prices.keys.all { id -> r.e.energyGrids.grid(id)?.country == r.country.id })
    }

    @Test fun otherCountriesWithoutAPolicyPayNothing() {
        val r = Rig(seed = 12L)
        val other: CountryId = r.e.world.countries().sortedBy { it.id }.last().id
        r.taxEverything()
        assertEquals(Money.ZERO, r.e.carbon.carbonPrice(other, CarbonCalendar.yearOf(r.e.tick)).takeIf { other != r.country.id } ?: Money.ZERO)
        assertEquals(Calendar.DAYS_PER_YEAR, CarbonCalendar.DAYS_PER_YEAR)
    }
}
