package app.many.economytycoon.competitors

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What a company remembers between planning rounds. */
class AiMemory {
    var lastThink: Tick = Tick(-1)
        internal set
    var lastExpansion: Tick = Tick(-1_000_000)
        internal set
    var lastBid: Tick = Tick(-1_000_000)
        internal set
    internal val lossStreak = java.util.TreeMap<FacilityId, Int>()

    fun streak(f: FacilityId): Int = lossStreak[f] ?: 0

    fun hashInto(h: CanonicalHasher) {
        h.putLong(lastThink.value).putLong(lastExpansion.value).putLong(lastBid.value).putInt(lossStreak.size)
        for ((f, n) in lossStreak) h.putLong(f.value).putInt(n)
    }
}

/** Builds new facilities in the most promising place when cash allows. */
class ExpansionPlanner(private val content: ContentRepository, private val scanner: OpportunityScanner) {
    fun cooldown(p: AiPersonality): Long = 30L + (1_000_000L - p.expansionAppetite) / 5_000L

    fun plan(p: AiPersonality, s: Situation, memory: AiMemory, buffer: Money, random: DeterministicRandom): List<AiAction> {
        if (s.operating.size + s.pendingBuilds >= p.maxFacilities) return emptyList()
        if (s.tick - memory.lastExpansion < cooldown(p)) return emptyList()
        if (s.pendingBuilds >= 2) return emptyList()
        if (!random.chance(Rate(p.expansionAppetite))) return emptyList()
        val regions = (s.homeRegions + s.facilities.map { it.region }).distinct().sorted()
        if (regions.isEmpty()) return emptyList()
        val allowed = { r: RecipeDef -> r.id in s.availableRecipes }
        val opportunities = scanner.scan(regions, allowed, s.availableFacilityTypes)
        // Crowding: avoid stacking many facilities of one type in one region.
        val crowd = HashMap<Pair<FacilityTypeId, app.many.economytycoon.core.RegionId>, Int>()
        for (f in s.facilities) crowd.merge(f.type to f.region, 1, Int::plus)
        val reserve = mulDiv(buffer.raw, 1_000_000L + (1_000_000L - p.riskTolerance) / 2, 1_000_000L)
        for (o in opportunities) {
            if ((crowd[o.facilityType to o.region] ?: 0) >= 2) continue
            val need = Money(o.capital.raw + reserve)
            if (s.cash < need) continue
            return listOf(AiAction.Build(s.company, o.facilityType, o.region))
        }
        return emptyList()
    }
}

/** Makes takeover bids when a cash-rich, aggressive company sees an affordable target. */
class AcquisitionPlanner {
    fun plan(p: AiPersonality, s: Situation, memory: AiMemory, buffer: Money, random: DeterministicRandom): List<AiAction> {
        if (p.aggression < 500_000L || s.targets.isEmpty()) return emptyList()
        if (s.tick - memory.lastBid < 90L) return emptyList()
        if (s.cash.raw < buffer.raw * 5) return emptyList()
        if (!random.chance(Rate(100_000L))) return emptyList()
        val premium = 1_120_000L + p.aggression / 10
        val budget = s.cash.raw - buffer.raw * 2
        val best = s.targets.sortedBy { it.company }.firstNotNullOfOrNull { t ->
            val want = mulDiv(t.totalShares, 550_000L, 1_000_000L).coerceAtMost(t.availableShares)
            if (want <= 0L || want * 2 < t.totalShares) return@firstNotNullOfOrNull null
            val price = Money(mulDiv(t.fairPricePerShare.raw, premium, 1_000_000L))
            if (price.raw * want > budget) null else Triple(t, want, price)
        } ?: return emptyList()
        return listOf(AiAction.Bid(s.company, best.first.company, best.second, best.third))
    }
}

/** Starts the most useful research when a slot is free. */
class ResearchChooser {
    fun plan(s: Situation): List<AiAction> {
        if (s.researchSlotsFree <= 0) return emptyList()
        return s.researchRanked.take(s.researchSlotsFree).map { AiAction.StartResearch(s.company, it) }
    }
}

/** Closes facilities that keep losing money. */
class RetrenchmentPlanner(private val lossLimit: Int = 3, private val minAge: Long = 60L) {
    fun plan(s: Situation, memory: AiMemory): List<AiAction> {
        if (s.operating.size <= 1) return emptyList()
        return s.operating.filter { memory.streak(it.id) >= lossLimit && it.ageTicks >= minAge }
            .sortedBy { it.profit.raw }.take(1).map { AiAction.Shutdown(s.company, it.id) }
    }
}

/** One company's decision procedure: runs every planner in priority order. */
class AiAgent(private val content: ContentRepository, market: MarketOutlook) {
    private val scanner = OpportunityScanner(content, market)
    private val pricing = PricingPlanner(content)
    private val production = ProductionPlanner(content, scanner)
    private val finance = FinancePlanner()
    private val expansion = ExpansionPlanner(content, scanner)
    private val acquisitions = AcquisitionPlanner()
    private val research = ResearchChooser()
    private val retrench = RetrenchmentPlanner()
    private val market = market

    private fun recipesOf(s: Situation, f: FacilityView): List<RecipeDef> =
        f.lines.mapNotNull { it.recipe }.distinct().mapNotNull { content.recipeOrNull(it) }

    fun plan(p: AiPersonality, s: Situation, memory: AiMemory, random: DeterministicRandom): List<AiAction> {
        for (f in s.facilities) {
            if (f.profit.raw < 0) memory.lossStreak.merge(f.id, 1, Int::plus) else memory.lossStreak.remove(f.id)
        }
        memory.lossStreak.keys.retainAll(s.facilities.map { it.id }.toSet())
        memory.lastThink = s.tick

        val buffer = finance.cashBuffer(p, s)
        val actions = ArrayList<AiAction>()
        actions += finance.plan(p, s, random)
        actions += retrench.plan(s, memory)
        actions += production.plan(p, s, market)
        actions += pricing.plan(p, s) { recipesOf(s, it) }
        actions += research.plan(s)
        val build = expansion.plan(p, s, memory, buffer, random)
        if (build.isNotEmpty()) memory.lastExpansion = s.tick
        actions += build
        val bid = acquisitions.plan(p, s, memory, buffer, random)
        if (bid.isNotEmpty()) memory.lastBid = s.tick
        actions += bid
        return actions
    }
}
