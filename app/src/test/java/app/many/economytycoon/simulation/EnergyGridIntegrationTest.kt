package app.many.economytycoon.simulation

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.energygrid.GridId
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.persistence.SimulationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The electricity grids as the simulation wires them: commands, establishing, snapshots, replay and the computer competitors. */
class EnergyGridIntegrationTest {
    private class Fixture(seed: Long = 9L, ai: Int = 0, grids: Boolean = true) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().first()
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Volt Co", country.id, null, Money.units(800_000))) as Outcome.Ok).value.created!!)
        init { if (grids) sim.execute(Actor.System, Command.EstablishGrids) }
        val grid get() = e.energyGrids.grids.first()
    }

    @Test fun anUnusedGridFeatureLeavesNoStateAndNoSnapshotSection() {
        val f = Fixture(grids = false)
        f.sim.advance(10)
        assertTrue(f.e.energyGrids.isEmpty)
        assertFalse(String(SimulationSnapshot.save(f.sim), Charsets.ISO_8859_1).contains("energygrid"))
    }

    @Test fun onlyTheSystemMayEstablishGrids() {
        val f = Fixture(grids = false)
        assertTrue(f.sim.execute(Actor.Player(1), Command.EstablishGrids) is Outcome.Rejected)
        assertTrue(f.e.energyGrids.isEmpty)
        assertTrue(f.sim.execute(Actor.System, Command.EstablishGrids) is Outcome.Ok)
        assertEquals(f.e.world.regions().size, f.e.energyGrids.grids.size)
        assertTrue(f.grid.loads.isNotEmpty())
        assertTrue(f.grid.units.isNotEmpty())
        assertTrue(f.sim.execute(Actor.System, Command.EstablishGrids) is Outcome.Rejected)
    }

    @Test fun executorValidatesOwnershipFundsAndIds() {
        val f = Fixture()
        val other = CompanyId((f.sim.execute(Actor.Player(2), Command.FoundCompany("Other", f.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val g = f.grid.id.value
        assertEquals(Rules.UNKNOWN_ENTITY, f.sim.execute(Actor.Player(1), Command.RetireGridUnit(f.me, 99_999L)).rejectionOrNull()?.rule)
        assertTrue(f.sim.execute(Actor.Player(2), Command.OrderGridUnit(other, g, 0, 5_000_000L)) is Outcome.Rejected)
        assertTrue(f.sim.execute(Actor.Player(1), Command.OrderGridUnit(f.me, 99_999L, 0, 10_000L)) is Outcome.Rejected)
        val before = f.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(f.me))
        val r = f.sim.execute(Actor.Player(1), Command.OrderGridUnit(f.me, g, app.many.economytycoon.energygrid.Technology.SOLAR.ordinal, 5_000L))
        if (r is Outcome.Ok) assertTrue(f.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(f.me)) < before)
        assertTrue(Invariants.check(f.e).isEmpty())
    }

    @Test fun commandsRoundTripThroughTheCodecAndDescriber() {
        val names = setOf("EstablishGrids", "ConnectFacilityToGrid", "DisconnectFacilityFromGrid", "ReclassifyGridLoad", "SetGridLoadDraw", "OrderGridUnit", "RetireGridUnit", "MothballGridUnit", "RestartGridUnit", "OfferPowerAgreement", "AcceptPowerAgreement", "DeclinePowerAgreement", "TerminatePowerAgreement")
        val cs = CommandSamples.all().filter { it.javaClass.simpleName in names }
        assertTrue(cs.size >= names.size)
        for (c in cs) {
            val w = ByteWriter(); CommandCodec.writeCommand(c, w)
            assertEquals(c, CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertEquals(ActivityArea.ENERGY, CommandDescriber.area(c))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
        }
    }

    @Test fun savingAndLoadingContinuesExactly() {
        val f = Fixture(seed = 21L, ai = 4)
        f.sim.advance(70)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(f.sim), SimTestSupport.content)
        assertEquals(f.sim.hash(), copy.hash())
        assertEquals(f.e.energyGrids.stateHash(), copy.economy.energyGrids.stateHash())
        for (i in 0 until 40) { f.sim.advance(); copy.advance(); assertEquals("tick ${f.sim.tick.value}", f.sim.hash(), copy.hash()) }
    }

    @Test fun theFeatureChangesTheHashAndReplaysIdentically() {
        val f = Fixture(seed = 33L, ai = 4)
        val plain = Simulation(SimTestSupport.config(33L, 4), SimTestSupport.content, bootstrap = true)
        f.sim.advance(100)
        val again = Simulation.replay(f.sim.config, SimTestSupport.content, f.sim.commandLog(), f.sim.tick.value)
        assertEquals(f.sim.hash(), again.hash())
        plain.advance(100)
        assertNotEquals(plain.hash(), f.sim.hash())
        assertTrue(Invariants.check(f.e).isEmpty())
    }

    @Test fun aMultiMonthRunIsDeterministicAndKeepsTheBooksBalanced() {
        val a = Fixture(seed = 41L, ai = 5); val b = Fixture(seed = 41L, ai = 5)
        a.sim.advance(130); b.sim.advance(130)
        assertEquals(a.sim.hash(), b.sim.hash())
        assertTrue(Invariants.check(a.e).isEmpty())
        assertTrue(a.grid.history.isNotEmpty())
    }

    @Test fun retiringACompanyDoesNotBreakTheGrids() {
        val f = Fixture()
        f.sim.advance(5)
        f.e.retire(f.me)
        f.sim.advance(5)
        assertTrue(Invariants.check(f.e).isEmpty())
        assertTrue(f.e.energyGrids.ppasOfCompany(f.me).none { it.status.live })
    }
}
