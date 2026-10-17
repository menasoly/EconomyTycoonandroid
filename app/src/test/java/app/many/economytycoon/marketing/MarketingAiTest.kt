package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.audiences
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.context
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import app.many.economytycoon.marketing.MarketingTestSupport.south
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketingAiTest {
    private val s = MarketingTestSupport.system(seed = 3L)
    private val ai = MarketingAi(worldSeed = 3L)

    private fun view(
        company: CompanyId = acme, cash: Long = 2_000_000, revenue: Long = 600_000, roi: Map<CampaignId, Long> = emptyMap(),
        positions: List<AiPosition> = listOf(AiPosition(context(beer, north, 500_000L, 20_000), Money.units(20_000))),
    ) = AiCompanyView(company, Money.units(cash), Money.units(revenue), positions, roi)

    /** The first tick at which [company] reviews. */
    private fun reviewTick(company: CompanyId, from: Long = 100L): Long = (from..from + 7).first { ai.isReviewDay(company, Tick(it)) }

    @Test fun boldnessIsFixedPerCompanyAndSpreadAcrossCompanies() {
        assertEquals(ai.boldnessPpm(acme), ai.boldnessPpm(acme))
        val values = (1L..10L).map { ai.boldnessPpm(CompanyId(it)) }
        assertTrue(values.all { it in 0..999_999L })
        assertTrue(values.toSet().size > 5)
    }

    @Test fun boldnessDependsOnTheGameSeed() {
        assertTrue(MarketingAi(worldSeed = 1L).boldnessPpm(acme) != MarketingAi(worldSeed = 2L).boldnessPpm(acme))
    }

    @Test fun companiesReviewOncePerWeekOnDifferentDays() {
        val days = (1L..7L).associateWith { c -> (0L..13L).filter { ai.isReviewDay(CompanyId(c), Tick(it)) } }
        for ((_, d) in days) assertEquals(2, d.size)
        assertTrue(days.values.map { it.first() }.toSet().size > 3)
    }

    @Test fun theBudgetFollowsRevenueWhenCashIsPlentiful() {
        val b = ai.dailyBudget(view(revenue = 600_000))
        assertTrue(b >= Money.units(300) && b <= Money.units(1_400))
    }

    @Test fun theBudgetIsCappedByCash() {
        assertEquals(Money.units(1_000), ai.dailyBudget(view(cash = 20_000, revenue = 6_000_000)))
        assertEquals(Money.ZERO, ai.dailyBudget(view(cash = 0)))
        assertEquals(Money.ZERO, ai.dailyBudget(view(revenue = 0)))
    }

    @Test fun boldCompaniesSpendMoreOfTheirRevenue() {
        val ordered = (1L..40L).map { CompanyId(it) }.sortedBy { ai.boldnessPpm(it) }
        val timid = ai.dailyBudget(view(company = ordered.first()))
        val bold = ai.dailyBudget(view(company = ordered.last()))
        assertTrue(bold > timid)
    }

    @Test fun onlyReviewDaysProduceProposals() {
        val t = reviewTick(acme)
        assertTrue(ai.decide(Tick(t), listOf(view()), s.state, audiences()).isNotEmpty())
        assertTrue(ai.decide(Tick(t + 1), listOf(view()), s.state, audiences()).isEmpty())
    }

    @Test fun aCompanyWithNothingToSellProposesNothing() {
        assertTrue(ai.decideFor(Tick(reviewTick(acme)), view(positions = emptyList()), s.state, audiences()).isEmpty())
    }

    @Test fun aBrokeCompanyLaunchesNothing() {
        assertTrue(ai.decideFor(Tick(reviewTick(acme)), view(cash = 100), s.state, audiences()).isEmpty())
    }

    @Test fun launchesFitInsideTheBudgetAndAreValidRequests() {
        val v = view()
        val proposals = ai.decideFor(Tick(reviewTick(acme)), v, s.state, audiences())
        val launches = proposals.filterIsInstance<MarketingProposal.Launch>()
        assertTrue(launches.isNotEmpty())
        assertTrue(launches.size <= 3)
        assertTrue(Money.sum(launches.map { it.request.dailyBudget }) <= ai.dailyBudget(v) + Money.units(500))
        for (l in launches) {
            assertEquals(acme, l.company)
            assertTrue(l.reason.isNotBlank())
            val r = s.campaigns.create(l.request, Tick(reviewTick(acme)), v.cash, audiences())
            assertTrue("rejected: ${r.rejectionOrNull()}", r is Outcome.Ok)
        }
        assertEquals(emptyList<String>(), s.invariants())
    }

    @Test fun theSameSituationGivesTheSameProposals() {
        val a = ai.decideFor(Tick(reviewTick(acme)), view(), s.state, audiences())
        val b = MarketingAi(worldSeed = 3L).decideFor(Tick(reviewTick(acme)), view(), MarketingTestSupport.system(seed = 3L).state, audiences())
        assertEquals(a, b)
    }

    @Test fun itDoesNotRebookASlotThatIsAlreadyTaken() {
        val first = ai.decideFor(Tick(reviewTick(acme)), view(), s.state, audiences()).filterIsInstance<MarketingProposal.Launch>()
        for (l in first) s.campaigns.create(l.request, Tick(reviewTick(acme)), Money.units(2_000_000), audiences())
        val second = ai.decideFor(Tick(reviewTick(acme)), view(), s.state, audiences()).filterIsInstance<MarketingProposal.Launch>()
        val taken = first.map { Triple(it.request.good, it.request.region, it.request.channel) }.toSet()
        assertTrue(second.none { Triple(it.request.good, it.request.region, it.request.channel) in taken })
    }

    @Test fun itWorksAcrossSeveralMarkets() {
        val v = view(positions = listOf(
            AiPosition(context(beer, north, 500_000L, 20_000), Money.units(20_000)),
            AiPosition(context(bread, south, 200_000L, 15_000, income = 1_300_000L), Money.units(15_000)),
        ), revenue = 2_000_000)
        val launches = ai.decideFor(Tick(reviewTick(acme)), v, s.state, audiences()).filterIsInstance<MarketingProposal.Launch>()
        assertTrue(launches.map { it.request.region }.toSet().isNotEmpty())
    }

    // --- upkeep ---

    @Test fun wornCreativesAreRefreshed() {
        val c = book(s, request(daily = 500))
        c.creative.freshnessPpm = 100_000L
        val p = ai.decideFor(Tick(reviewTick(acme)), view(), s.state, audiences())
        assertTrue(p.any { it is MarketingProposal.Refresh && it.campaign == c.id })
    }

    @Test fun refreshWaitsForTheCooldown() {
        val c = book(s, request(daily = 500))
        c.creative.freshnessPpm = 100_000L
        c.creative.lastRefreshed = Tick(reviewTick(acme) - 2)
        assertFalse(ai.decideFor(Tick(reviewTick(acme)), view(), s.state, audiences()).any { it is MarketingProposal.Refresh })
    }

    @Test fun moneyLosersAreDropped() {
        val c = book(s, request(daily = 500))
        MarketingTestSupport.run(s, 0, 8)
        val p = ai.decideFor(Tick(reviewTick(acme)), view(roi = mapOf(c.id to -900_000L)), s.state, audiences())
        assertTrue(p.any { it is MarketingProposal.Cancel && it.campaign == c.id })
    }

    @Test fun earlyDaysAreNotJudged() {
        val c = book(s, request(daily = 500))
        val p = ai.decideFor(Tick(reviewTick(acme)), view(roi = mapOf(c.id to -900_000L)), s.state, audiences())
        assertTrue(p.none { it is MarketingProposal.Cancel })
    }

    @Test fun campaignsThatPayAreExtendedBeforeTheyEnd() {
        val c = book(s, request(daily = 500, days = 12))
        run(s, 0, 10)
        val p = ai.decideFor(Tick(reviewTick(acme, 10)), view(roi = mapOf(c.id to 500_000L)), s.state, audiences())
        assertTrue(p.any { it is MarketingProposal.Extend && it.campaign == c.id })
    }

    @Test fun whenCashRunsShortItPausesBeforeItDefaults() {
        val a = book(s, request(daily = 3_000, channel = Channel.ONLINE, tier = CreativeTier.BASIC))
        val p = ai.decideFor(Tick(reviewTick(acme)), view(cash = 10_000, revenue = 100_000), s.state, audiences())
        assertTrue(p.any { it is MarketingProposal.Pause && it.campaign == a.id })
    }

    @Test fun pausedCampaignsResumeWhenCashReturns() {
        val a = book(s, request(daily = 400))
        s.campaigns.pause(a.id)
        val p = ai.decideFor(Tick(reviewTick(acme)), view(), s.state, audiences())
        assertTrue(p.any { it is MarketingProposal.Resume && it.campaign == a.id })
    }

    @Test fun anOverlargeCommitmentIsTrimmed() {
        val a = book(s, request(daily = 30_000, channel = Channel.ONLINE, tier = CreativeTier.BASIC))
        val p = ai.decideFor(Tick(reviewTick(acme)), view(revenue = 300_000), s.state, audiences())
        val trim = p.filterIsInstance<MarketingProposal.SetBudget>().single { it.campaign == a.id }
        assertTrue(trim.dailyBudget < a.dailyBudget)
        assertTrue(trim.dailyBudget >= Channel.ONLINE.spec.minDailySpend)
    }

    @Test fun aLouderRivalDrawsACounterCampaign() {
        book(s, request(company = rival, daily = 9_000, days = 90, tier = CreativeTier.BASIC, channel = Channel.TELEVISION))
        book(s, request(daily = 300, days = 90, tier = CreativeTier.BASIC, channel = Channel.LOCAL_PRESS))
        run(s, 0, 30)
        val v = view(revenue = 3_000_000, positions = listOf(AiPosition(context(beer, north, 500_000L, 2_000), Money.units(2_000))))
        val p = ai.decideFor(Tick(reviewTick(acme, 30)), v, s.state, audiences())
        val launches = p.filterIsInstance<MarketingProposal.Launch>()
        assertTrue(launches.any { it.reason.contains("rival") })
    }

    @Test fun decideSortsCompaniesAndSkipsOffDays() {
        val t = Tick(reviewTick(acme))
        val all = ai.decide(t, listOf(view(rival), view(acme)), s.state, audiences())
        val companies = all.map { it.company }
        assertEquals(companies.sorted(), companies)
    }
}
