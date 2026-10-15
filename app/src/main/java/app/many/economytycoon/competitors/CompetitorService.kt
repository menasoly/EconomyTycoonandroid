package app.many.economytycoon.competitors

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/**
 * Registry of AI-run companies and their planning rounds. Each company plans on its own rhythm,
 * offset by its id so the work spreads over ticks, and draws from its own random stream.
 */
class CompetitorService(private val content: ContentRepository, private val market: MarketOutlook) {
    private val personalities = java.util.TreeMap<CompanyId, AiPersonality>()
    private val memories = java.util.TreeMap<CompanyId, AiMemory>()
    private val agent = AiAgent(content, market)

    fun isAi(company: CompanyId) = personalities.containsKey(company)
    fun companies(): List<CompanyId> = personalities.keys.toList()
    fun personality(company: CompanyId): AiPersonality? = personalities[company]
    fun memory(company: CompanyId): AiMemory? = memories[company]

    fun register(company: CompanyId, personality: AiPersonality): Outcome<Unit> {
        if (personalities.containsKey(company)) return Outcome.reject(Rules.DUPLICATE, "already controlled by the AI")
        personalities[company] = personality
        memories[company] = AiMemory()
        return Outcome.ok(Unit)
    }

    /** Draws a personality from the company's own stream so a seed always gives the same rivals. */
    fun registerRandom(company: CompanyId, random: RandomProvider): AiPersonality {
        val p = Personalities.roll(random.stream("ai_personality", company.value))
        register(company, p)
        return p
    }

    fun setPersonality(company: CompanyId, personality: AiPersonality): Boolean {
        if (!personalities.containsKey(company)) return false
        personalities[company] = personality
        return true
    }

    fun remove(company: CompanyId) { personalities.remove(company); memories.remove(company) }

    /** True when the company's next planning round falls on [tick]. */
    fun due(company: CompanyId, tick: Tick): Boolean {
        val p = personalities[company] ?: return false
        return (tick.value + company.value) % p.thinkInterval == 0L
    }

    fun dueCompanies(tick: Tick): List<CompanyId> = personalities.keys.filter { due(it, tick) }

    /** Runs one planning round for the situation's company. */
    fun think(situation: Situation, random: RandomProvider): List<AiAction> {
        val p = personalities[situation.company] ?: return emptyList()
        val memory = memories.getOrPut(situation.company) { AiMemory() }
        return agent.plan(p, situation, memory, random.stream("ai", situation.company.value))
    }

    internal fun restore(company: CompanyId, p: AiPersonality, m: AiMemory) { personalities[company] = p; memories[company] = m }
    internal fun clearAll() { personalities.clear(); memories.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.section("competitors")
        h.putInt(personalities.size)
        for ((id, p) in personalities) { h.putLong(id.value); p.hashInto(h); memories[id]?.hashInto(h) }
    }
}
