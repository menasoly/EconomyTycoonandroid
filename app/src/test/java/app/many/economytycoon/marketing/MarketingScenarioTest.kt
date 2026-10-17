package app.many.economytycoon.marketing

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.audiences
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Multi-step stories across the whole module. */
class MarketingScenarioTest {
    private val key = BrandKey(acme, beer, north)

    @Test fun advertisingBuildsTheBrandAndStoppingLetsItFade() {
        val s = MarketingTestSupport.system()
        val c = book(s, request(channel = Channel.TELEVISION, daily = 1_500, days = 40))
        run(s, 0, 40)
        val peak = s.state.stock(key)?.awarenessPpm ?: 0L
        assertTrue(peak > 0L)
        val liftWhileKnown = s.demand.forBrand(key, Tick(40))
        run(s, 40, 120)
        val after = s.state.stock(key)?.awarenessPpm ?: 0L
        assertTrue("awareness should fade: $peak -> $after", after < peak)
        assertTrue(s.demand.forBrand(key, Tick(160)) <= liftWhileKnown)
        assertTrue(s.state.campaign(c.id)?.status.let { it == null || it == CampaignStatus.COMPLETED })
        assertTrue(s.invariants().toString(), s.invariants().isEmpty())
    }

    @Test fun aScandalHurtsAndTheBrandRecovers() {
        val s = MarketingTestSupport.system()
        book(s, request(daily = 800, days = 200))
        run(s, 0, 30)
        val before = s.demand.forBrand(key, Tick(30))
        s.damage.record(acme, null, null, DamageKind.SCANDAL, 800_000L, Tick(30)).getOrThrow()
        run(s, 30, 1)
        val hurt = s.demand.forBrand(key, Tick(31))
        assertTrue("$hurt should be below $before", hurt < before)
        run(s, 31, 150)
        val healed = s.demand.forBrand(key, Tick(181))
        assertTrue("$healed should recover above $hurt", healed > hurt)
    }

    @Test fun aRivalCampaignQuietsOurVoice() {
        val quiet = MarketingTestSupport.system()
        book(quiet, request(daily = 600, days = 60))
        run(quiet, 0, 40)
        val loud = MarketingTestSupport.system()
        book(loud, request(daily = 600, days = 60))
        book(loud, request(company = rival, channel = Channel.TELEVISION, daily = 3_000, days = 60))
        run(loud, 0, 40)
        val sellers = listOf(acme, rival)
        val alone = quiet.demand.relative(acme, beer, north, listOf(acme))
        val contested = loud.demand.relative(acme, beer, north, sellers)
        assertTrue("$contested should trail $alone", contested < alone)
    }

    @Test fun theWholeStoryRoundTripsAndStaysDeterministic() {
        fun play(): MarketingSystem = MarketingTestSupport.busy(seed = 11L, days = 40)
        val a = play()
        val b = play()
        assertEquals(a.stateHash(), b.stateHash())
        val bytes = ByteWriter().also { MarketingCodec.write(a.state, it) }.toByteArray()
        val back = MarketingState().also { MarketingCodec.read(ByteReader(bytes), it) }
        assertEquals(a.stateHash(), back.stateHash())
        assertTrue(a.invariants().toString(), a.invariants().isEmpty())
        val other = MarketingTestSupport.busy(seed = 12L, days = 40)
        assertTrue(other.invariants().isEmpty())
    }

    @Test fun spendIsSettledOncePerDayAndNeverDoubled() {
        val s = MarketingTestSupport.system()
        book(s, request(daily = 500, days = 10))
        val reports = run(s, 0, 10)
        val keys = reports.flatMap { it.intents }.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.isNotEmpty())
    }

    @Test fun theOptimiserPlanMatchesWhatCanBeBooked() {
        val s = MarketingTestSupport.system()
        val ctx = listOf(MarketingTestSupport.context(revenue = 60_000))
        val plan = s.optimiser.optimise(ctx, OptimiserConstraints(Money.units(3_000), step = Money.units(100)))
        assertTrue(plan.allocations.isNotEmpty())
        for (a in plan.allocations) {
            val r = s.campaigns.create(
                request(channel = a.channel, daily = a.dailyBudget.raw / Money.units(1).raw, days = 14),
                Tick(0), MarketingTestSupport.bank, audiences(),
            )
            assertTrue("allocation ${a.channel} rejected", r is app.many.economytycoon.core.Outcome.Ok)
        }
        assertTrue(s.invariants().isEmpty())
    }
}
