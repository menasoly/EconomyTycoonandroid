package app.many.economytycoon.competitors

import app.many.economytycoon.competitors.CompetitorTestSupport.bakery
import app.many.economytycoon.competitors.CompetitorTestSupport.company
import app.many.economytycoon.competitors.CompetitorTestSupport.content
import app.many.economytycoon.competitors.CompetitorTestSupport.facility
import app.many.economytycoon.competitors.CompetitorTestSupport.line
import app.many.economytycoon.competitors.CompetitorTestSupport.region
import app.many.economytycoon.competitors.CompetitorTestSupport.region2
import app.many.economytycoon.competitors.CompetitorTestSupport.situation
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyTest {
    private val market = CompetitorTestSupport.Outlook()
    private val scanner = OpportunityScanner(content, market)
    private val expansion = ExpansionPlanner(content, scanner)
    private val acquisitions = AcquisitionPlanner()
    private fun eager(a: Archetype = Archetype.EXPANDER) = Personalities.of(a).copy(expansionAppetite = 1_000_000L)

    // ----- personalities -----

    @Test fun everyArchetypeHasSaneTraits() {
        for (a in Archetype.values()) {
            val p = Personalities.of(a)
            assertEquals(a, p.archetype)
            assertTrue(p.thinkInterval > 0 && p.maxFacilities > 0)
            for (v in listOf(p.aggression, p.riskTolerance, p.qualityFocus, p.costFocus, p.expansionAppetite)) assertTrue(v in 0L..1_000_000L)
        }
    }

    @Test fun expandersGrowAndConservativesDoNot() {
        assertTrue(Personalities.of(Archetype.EXPANDER).maxFacilities > Personalities.of(Archetype.CONSERVATIVE).maxFacilities)
        assertTrue(Personalities.of(Archetype.EXPANDER).riskTolerance > Personalities.of(Archetype.CONSERVATIVE).riskTolerance)
    }

    @Test fun rolledPersonalitiesAreDeterministicAndBounded() {
        val a = Personalities.roll(RandomProvider(9).stream("x")); val b = Personalities.roll(RandomProvider(9).stream("x"))
        assertEquals(a, b)
        repeat(50) {
            val p = Personalities.roll(RandomProvider(it.toLong()).stream("x"))
            assertTrue(p.aggression in 10_000L..990_000L && p.thinkInterval >= 1)
        }
        val kinds = (0 until 60).map { Personalities.roll(RandomProvider(it.toLong()).stream("x")).archetype }.toSet()
        assertTrue(kinds.size >= 4)
    }

    // ----- expansion -----

    @Test fun richCompaniesBuildTheBestOpportunity() {
        val memory = AiMemory()
        val s = situation(cash = 2_000_000, opex = 1_000, facilities = listOf(facility(1, listOf(line(1, "make_bread")))))
        val build = expansion.plan(eager(), s, memory, Money.units(15_000), RandomProvider(1).stream("e")).single() as AiAction.Build
        assertEquals(bakery, build.type)
    }

    @Test fun poorCompaniesDoNotBuild() {
        val s = situation(cash = 20_000, opex = 1_000)
        assertTrue(expansion.plan(eager(), s, AiMemory(), Money.units(15_000), RandomProvider(1).stream("e")).isEmpty())
    }

    @Test fun expansionHonoursCooldownAndPendingBuilds() {
        val memory = AiMemory(); memory.lastExpansion = Tick(95)
        val s = situation(cash = 2_000_000, tick = 100)
        assertTrue(expansion.plan(eager(), s, memory, Money.units(15_000), RandomProvider(1).stream("e")).isEmpty())
        val busy = situation(cash = 2_000_000, pending = 2)
        assertTrue(expansion.plan(eager(), busy, AiMemory(), Money.units(15_000), RandomProvider(1).stream("e")).isEmpty())
    }

    @Test fun facilityCapStopsGrowth() {
        val fs = (1L..4L).map { facility(it, listOf(line(it, "make_bread")), region = if (it % 2 == 0L) region else region2) }
        val capped = eager(Archetype.CONSERVATIVE).copy(maxFacilities = 4)
        assertTrue(expansion.plan(capped, situation(cash = 5_000_000, facilities = fs), AiMemory(), Money.ZERO, RandomProvider(1).stream("e")).isEmpty())
    }

    @Test fun crowdedRegionsAreSkipped() {
        val fs = listOf(facility(1, listOf(line(1, "make_bread"))), facility(2, listOf(line(2, "make_bread"))))
        val s = situation(cash = 5_000_000, facilities = fs, homes = listOf(region))
        assertTrue(expansion.plan(eager(), s, AiMemory(), Money.ZERO, RandomProvider(1).stream("e")).isEmpty())
        val open = situation(cash = 5_000_000, facilities = fs, homes = listOf(region, region2))
        val build = expansion.plan(eager(), open, AiMemory(), Money.ZERO, RandomProvider(1).stream("e")).single() as AiAction.Build
        assertEquals(region2, build.region)
    }

    @Test fun noAppetiteMeansNoBuilding() {
        val lazy = eager().copy(expansionAppetite = 0L)
        assertTrue(expansion.plan(lazy, situation(cash = 5_000_000), AiMemory(), Money.ZERO, RandomProvider(1).stream("e")).isEmpty())
    }

    // ----- acquisitions -----

    private fun target(id: Long = 20, available: Long = 1_000, total: Long = 1_000, price: Long = 10) =
        TargetView(CompanyId(id), Money.units(price), available, total, setOf(IndustryId("baking")))

    private fun lucky(): app.many.economytycoon.core.DeterministicRandom {
        for (seed in 0L..500L) {
            val r = RandomProvider(seed).stream("a")
            if (r.chance(app.many.economytycoon.core.Rate(100_000L))) return RandomProvider(seed).stream("a")
        }
        error("no lucky seed")
    }

    @Test fun aggressiveRichCompaniesBidForControl() {
        val bid = acquisitions.plan(Personalities.of(Archetype.DISCOUNTER), situation(cash = 5_000_000, targets = listOf(target())), AiMemory(), Money.units(25_000), lucky())
        val b = bid.single() as AiAction.Bid
        assertEquals(550L, b.shares)
        assertTrue(b.pricePerShare > Money.units(10))
    }

    @Test fun timidCompaniesNeverBid() {
        assertTrue(acquisitions.plan(Personalities.of(Archetype.CONSERVATIVE), situation(cash = 5_000_000, targets = listOf(target())), AiMemory(), Money.units(25_000), lucky()).isEmpty())
    }

    @Test fun bidsNeedCashAndAvailableShares() {
        val p = Personalities.of(Archetype.DISCOUNTER)
        assertTrue(acquisitions.plan(p, situation(cash = 50_000, targets = listOf(target())), AiMemory(), Money.units(25_000), lucky()).isEmpty())
        assertTrue(acquisitions.plan(p, situation(cash = 5_000_000, targets = listOf(target(available = 100))), AiMemory(), Money.units(25_000), lucky()).isEmpty())
        val recent = AiMemory(); recent.lastBid = Tick(50)
        assertTrue(acquisitions.plan(p, situation(cash = 5_000_000, targets = listOf(target())), recent, Money.units(25_000), lucky()).isEmpty())
    }

    // ----- retrenchment -----

    @Test fun repeatedLossesShutTheWorstFacility() {
        val agent = AiAgent(content, market)
        val memory = AiMemory()
        val p = Personalities.of(Archetype.BALANCED).copy(expansionAppetite = 0L)
        val fs = listOf(
            facility(1, listOf(line(1, "make_bread")), profit = 500), facility(2, listOf(line(2, "make_bread")), profit = -300),
        )
        var shut: List<AiAction> = emptyList()
        repeat(3) { shut = agent.plan(p, situation(facilities = fs), memory, RandomProvider(2).stream("r")).filterIsInstance<AiAction.Shutdown>() }
        assertEquals(FacilityId(2), (shut.single() as AiAction.Shutdown).facility)
        assertEquals(3, memory.streak(FacilityId(2)))
        assertEquals(0, memory.streak(FacilityId(1)))
    }

    @Test fun youngOrOnlyFacilitiesAreKept() {
        val memory = AiMemory()
        val young = listOf(facility(1, emptyList(), profit = 100), facility(2, emptyList(), profit = -100, age = 10))
        repeat(4) { RetrenchmentPlanner().plan(situation(facilities = young), memory) }
        assertTrue(RetrenchmentPlanner().plan(situation(facilities = young), memory).isEmpty())
        val lone = listOf(facility(1, emptyList(), profit = -100))
        memory.lossStreak[FacilityId(1)] = 9
        assertTrue(RetrenchmentPlanner().plan(situation(facilities = lone), memory).isEmpty())
    }

    @Test fun recoveredFacilitiesLoseTheirStreak() {
        val agent = AiAgent(content, market); val memory = AiMemory()
        val p = Personalities.of(Archetype.BALANCED).copy(expansionAppetite = 0L)
        agent.plan(p, situation(facilities = listOf(facility(1, emptyList(), profit = -5))), memory, RandomProvider(1).stream("r"))
        assertEquals(1, memory.streak(FacilityId(1)))
        agent.plan(p, situation(facilities = listOf(facility(1, emptyList(), profit = 5))), memory, RandomProvider(1).stream("r"))
        assertEquals(0, memory.streak(FacilityId(1)))
        agent.plan(p, situation(facilities = emptyList()), memory, RandomProvider(1).stream("r"))
        assertTrue(memory.lossStreak.isEmpty())
    }

    // ----- service -----

    @Test fun registrationIsUnique() {
        val s = CompetitorService(content, market)
        s.register(company, Personalities.of(Archetype.BALANCED)).getOrThrow()
        assertEquals(Rules.DUPLICATE, s.register(company, Personalities.of(Archetype.PREMIUM)).rejectionOrNull()?.rule)
        assertTrue(s.isAi(company))
        s.remove(company)
        assertFalse(s.isAi(company))
        assertNull(s.memory(company))
    }

    @Test fun planningRoundsAreStaggeredByCompanyId() {
        val s = CompetitorService(content, market)
        for (i in 1L..5L) s.register(CompanyId(i), Personalities.of(Archetype.BALANCED))
        val a = s.dueCompanies(Tick(0)); val b = s.dueCompanies(Tick(1))
        assertNotEquals(a, b)
        val every = (0L until 5L).flatMap { t -> s.dueCompanies(Tick(t)) }
        assertEquals(5, every.size)
        assertEquals(5, every.toSet().size)
    }

    @Test fun thinkingIsDeterministic() {
        fun run(): List<AiAction> {
            val s = CompetitorService(content, market)
            s.registerRandom(company, RandomProvider(4))
            val sit = situation(cash = 3_000_000, facilities = listOf(facility(1, listOf(line(1, null)))))
            return (1..5).flatMap { s.think(sit, RandomProvider(8)) }
        }
        assertEquals(run(), run())
        assertTrue(run().isNotEmpty())
    }

    @Test fun unknownCompaniesDoNotThink() {
        assertTrue(CompetitorService(content, market).think(situation(), RandomProvider(1)).isEmpty())
    }

    @Test fun saveAndLoadRoundTrips() {
        val s = CompetitorService(content, market)
        s.registerRandom(CompanyId(3), RandomProvider(1)); s.registerRandom(CompanyId(9), RandomProvider(1))
        s.memory(CompanyId(3))!!.lossStreak[FacilityId(4)] = 2
        s.think(situation(facilities = listOf(facility(4, emptyList(), profit = -1))).let { sit ->
            Situation(CompanyId(3), sit.tick, sit.cash, sit.debt, sit.creditAvailable, sit.operatingCostPerTick, sit.revenuePerTick, sit.facilities, sit.sellRules, sit.buyRules, sit.budgets, sit.dividend, sit.staffTargets, 0, emptyList(), sit.availableRecipes, sit.availableFacilityTypes, emptyList(), sit.homeRegions)
        }, RandomProvider(1))
        val w = ByteWriter(); CompetitorCodec.write(s, w)
        val copy = CompetitorService(content, market); CompetitorCodec.read(ByteReader(w.toByteArray()), copy)
        fun h(x: CompetitorService) = CanonicalHasher().also { x.hashInto(it) }.finish().toHex()
        assertEquals(h(s), h(copy))
        assertNotNull(copy.personality(CompanyId(9)))
    }

    @Test fun badVersionIsRejected() {
        val w = ByteWriter(); w.writeVarInt(5)
        assertTrue(runCatching { CompetitorCodec.read(ByteReader(w.toByteArray()), CompetitorService(content, market)) }.isFailure)
    }
}
