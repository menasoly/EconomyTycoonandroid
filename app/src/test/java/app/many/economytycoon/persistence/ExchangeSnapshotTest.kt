package app.many.economytycoon.persistence

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeSnapshotTest {
    private fun market(): Pair<Simulation, Pair<CompanyId, CompanyId>> {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        fun found(name: String, p: Long) = CompanyId((sim.execute(Actor.Player(p), Command.FoundCompany(name, country.id, null, Money.units(900_000))) as Outcome.Ok).value.created!!)
        val issuer = found("Issuer Works", 1); val investor = found("Investor Works", 2)
        e.finance.mint(e.tick, e.finance.households, Money.units(60_000_000), "test:households")
        sim.advance(35)
        assertTrue(sim.execute(Actor.System, Command.ListCompany(issuer, 300_000L)) is Outcome.Ok)
        assertTrue(sim.execute(Actor.System, Command.BuyShares(investor, issuer, 500)) is Outcome.Ok)
        return sim to (issuer to investor)
    }

    @Test fun aSavedGameKeepsListingsPricesAndHoldings() {
        val (sim, ids) = market()
        sim.advance(25)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.hash(), copy.hash())
        val a = sim.economy.exchange.listing(ids.first)!!
        val b = copy.economy.exchange.listing(ids.first)!!
        assertEquals(a.price, b.price)
        assertEquals(a.history, b.history)
        assertEquals(sim.economy.exchange.index, copy.economy.exchange.index)
        assertEquals(sim.economy.exchangeRunner.holdingOf(ids.second, ids.first), copy.economy.exchangeRunner.holdingOf(ids.second, ids.first))
        assertEquals(
            sim.economy.exchange.basisOf(sim.economy.companies.require(ids.second).holder, ids.first),
            copy.economy.exchange.basisOf(copy.economy.companies.require(ids.second).holder, ids.first),
        )
    }

    @Test fun aLoadedMarketKeepsMovingTheSameWay() {
        val (sim, _) = market()
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        for (i in 0 until 30) { sim.advance(); copy.advance(); assertEquals("tick ${sim.tick.value}", sim.hash(), copy.hash()) }
    }

    @Test fun tradesRecordedInTheLogReplayToTheSameMarket() {
        val (sim, ids) = market()
        sim.execute(Actor.System, Command.SellShares(ids.second, ids.first, 100))
        sim.advance(10)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(sim), SimTestSupport.content)
        assertEquals(sim.commandLog().size, copy.commandLog().size)
        assertEquals(sim.hash(), copy.hash())
    }
}
