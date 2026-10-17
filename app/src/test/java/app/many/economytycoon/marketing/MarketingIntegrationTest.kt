package app.many.economytycoon.marketing

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.Invariants
import app.many.economytycoon.simulation.SimTestSupport
import app.many.economytycoon.simulation.Simulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The marketing module as the simulation wires it: commands, settlement, snapshots, replay and the computer competitors. */
class MarketingIntegrationTest {
    private val bread = GoodId("bread")

    private class Fixture(seed: Long = 9L, ai: Int = 0) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regions().sortedBy { it.id }.first().id
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Ad Works", country.id, null, Money.units(800_000))) as Outcome.Ok).value.created!!)
        val other: CompanyId = CompanyId((sim.execute(Actor.Player(2), Command.FoundCompany("Other Co", country.id, null, Money.units(800_000))) as Outcome.Ok).value.created!!)
    }

    private fun create(f: Fixture, company: CompanyId = f.me, daily: Long = 400, days: Int = 20) = f.sim.execute(
        Actor.Player(1),
        Command.CreateCampaign(company, bread, f.region, null, Channel.RADIO, TargetSegment.EVERYONE, Money.units(daily), days, CreativeTier.PROFESSIONAL, null),
    )

    @Test fun anIdleGameCarriesNoMarketingState() {
        val f = Fixture()
        assertTrue(f.e.marketing.state.isPristine())
        f.sim.advance(10)
        assertTrue(f.e.marketing.state.isPristine())
        assertFalse(String(SimulationSnapshot.save(f.sim), Charsets.ISO_8859_1).contains("marketing"))
    }

    @Test fun bookingChargesCashAndRecordsACampaign() {
        val f = Fixture()
        val before = f.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(f.me))
        val r = create(f)
        assertTrue(r.rejectionOrNull()?.message, r is Outcome.Ok)
        val after = f.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(f.me))
        assertTrue(after < before)
        assertEquals(1, f.e.marketing.state.campaigns().size)
        assertFalse(f.e.marketing.state.isPristine())
    }

    @Test fun onlyTheOwnerMayChangeACampaign() {
        val f = Fixture()
        val id = CampaignId((create(f) as Outcome.Ok).value.created!!)
        val r = f.sim.execute(Actor.Player(2), Command.PauseCampaign(f.other, id))
        assertEquals(Rules.NOT_OWNER, r.rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, f.sim.execute(Actor.Player(1), Command.PauseCampaign(f.me, CampaignId(999))).rejectionOrNull()?.rule)
        assertTrue(f.sim.execute(Actor.Player(1), Command.PauseCampaign(f.me, id)) is Outcome.Ok)
        assertTrue(f.sim.execute(Actor.Player(1), Command.ResumeCampaign(f.me, id)) is Outcome.Ok)
        assertTrue(f.sim.execute(Actor.Player(1), Command.CancelCampaign(f.me, id)) is Outcome.Ok)
    }

    @Test fun aBookingTheCompanyCannotAffordIsRefusedAndLeavesNothingBehind() {
        val f = Fixture()
        val r = create(f, daily = 5_000_000, days = 90)
        assertTrue(r is Outcome.Rejected)
        assertTrue(f.e.marketing.state.campaigns().isEmpty())
    }

    @Test fun unknownProductsAndRegionsAreRefused() {
        val f = Fixture()
        val bad = Command.CreateCampaign(f.me, GoodId("nope"), f.region, null, Channel.RADIO, TargetSegment.EVERYONE, Money.units(100), 10, CreativeTier.BASIC, null)
        assertEquals(Rules.UNKNOWN_ENTITY, f.sim.execute(Actor.Player(1), bad).rejectionOrNull()?.rule)
    }

    @Test fun aRunningCampaignBuildsBrandAndPaysEachDay() {
        val f = Fixture()
        create(f)
        val cash0 = f.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(f.me))
        f.sim.advance(8)
        val cash1 = f.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(f.me))
        assertTrue(cash1 < cash0)
        assertTrue(f.e.marketing.state.campaigns().single().spentTotal.raw > 0)
        assertTrue(Invariants.check(f.e).isEmpty())
    }

    @Test fun theDepartmentBudgetIsAnEnvelopeNotAnExtraCharge() {
        val f = Fixture()
        create(f)
        val intents = f.e.marketingRunner.settlementIntents(emptyList())
        assertTrue(intents.isEmpty()) // nothing advanced yet, so no daily bill
        f.sim.advance(3)
        assertTrue(Invariants.check(f.e).isEmpty())
    }

    @Test fun demandIsUnchangedWithoutStocksAndLiftedWithThem() {
        val f = Fixture()
        assertEquals(1_000_000L, f.e.marketingRunner.scaleDemand(1_000_000L, bread, f.region))
        assertEquals(250_000L, f.e.marketingRunner.premium(f.me, bread, f.region, 250_000L))
        create(f, daily = 3_000, days = 60)
        f.sim.advance(30)
        assertTrue(f.e.marketingRunner.scaleDemand(1_000_000L, bread, f.region) >= 1_000_000L)
        assertTrue(f.e.marketingRunner.premium(f.me, bread, f.region, 250_000L) >= 250_000L)
    }

    @Test fun aPromotionLowersTheShelfPriceOnlyWhileItRuns() {
        val f = Fixture()
        val price = app.many.economytycoon.core.Price(Money.units(10))
        assertEquals(price, f.e.marketingRunner.shelfPrice(f.me, bread, f.region, f.sim.tick, price))
        val r = f.sim.execute(Actor.Player(1), Command.LaunchPromotion(f.me, bread, f.region, 5, 200_000L, Money.units(10), Money.units(4), 2_000_000L, false))
        assertTrue(r.rejectionOrNull()?.message, r is Outcome.Ok)
        assertTrue(f.e.marketingRunner.shelfPrice(f.me, bread, f.region, f.sim.tick, price) < price)
        val id = PromotionId((r as Outcome.Ok).value.created!!)
        assertEquals(Rules.NOT_OWNER, f.sim.execute(Actor.Player(2), Command.CancelPromotion(f.other, id)).rejectionOrNull()?.rule)
        assertTrue(f.sim.execute(Actor.Player(1), Command.CancelPromotion(f.me, id)) is Outcome.Ok)
    }

    @Test fun retiringACompanyEndsItsCampaigns() {
        val f = Fixture()
        create(f)
        f.e.retire(f.me)
        assertTrue(f.e.marketing.state.campaigns().none { it.company == f.me && it.isLive })
    }

    @Test fun commandsRoundTripThroughTheCodecAndDescriber() {
        for (c in app.many.economytycoon.persistence.CommandSamples.all().filter { it.javaClass.simpleName in names }) {
            val w = ByteWriter(); app.many.economytycoon.persistence.CommandCodec.writeCommand(c, w)
            assertEquals(c, app.many.economytycoon.persistence.CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertEquals(ActivityArea.MARKETING, CommandDescriber.area(c))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
        }
    }

    @Test fun savingAndLoadingContinuesExactly() {
        val f = Fixture(seed = 21L, ai = 4)
        create(f)
        f.sim.advance(70)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(f.sim), SimTestSupport.content)
        assertEquals(f.sim.hash(), copy.hash())
        assertEquals(f.e.marketing.stateHash(), copy.economy.marketing.stateHash())
        for (i in 0 until 40) { f.sim.advance(); copy.advance(); assertEquals("tick ${f.sim.tick.value}", f.sim.hash(), copy.hash()) }
    }

    @Test fun theFeatureChangesTheHashAndReplaysIdentically() {
        val f = Fixture(seed = 33L, ai = 4)
        val plain = Simulation(SimTestSupport.config(33L, 4), SimTestSupport.content)
        create(f)
        f.sim.advance(90)
        val log = f.sim.commandLog()
        val again = Simulation.replay(f.sim.config, SimTestSupport.content, log, f.sim.tick.value)
        assertEquals(f.sim.hash(), again.hash())
        plain.advance(90)
        assertNotEquals(plain.hash(), f.sim.hash())
        assertTrue(Invariants.check(f.e).isEmpty())
    }

    @Test fun twoIdenticalRunsWithComputerCompetitorsAgree() {
        val a = SimTestSupport.sim(41, 5); val b = SimTestSupport.sim(41, 5)
        a.advance(150); b.advance(150)
        assertEquals(a.hash(), b.hash())
        assertTrue(Invariants.check(a.economy).isEmpty())
    }

    private val names = setOf("CreateCampaign", "PauseCampaign", "ResumeCampaign", "ExtendCampaign", "SetCampaignBudget", "RefreshCreative", "CancelCampaign", "StartAbTest", "LaunchPromotion", "CancelPromotion")
}
