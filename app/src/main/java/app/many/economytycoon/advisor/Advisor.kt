package app.many.economytycoon.advisor

/** Runs the rules over the facts and keeps track of suggestions the player has set aside for a while. */
class Advisor(private val rules: List<AdvisorRule> = AdvisorRules.all, private val tuning: AdvisorTuning = AdvisorTuning()) {
    private val hiddenUntil = java.util.TreeMap<String, Long>()

    /** Suggestions that apply now and are not set aside, most pressing first. */
    fun advise(facts: AdvisorFacts, limit: Int = Int.MAX_VALUE): List<Insight> =
        rules.flatMap { it.check(facts, tuning) }
            .filter { !isHidden(it.code, facts.tick) }
            .sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.code })
            .take(limit)

    /** Every suggestion that applies, including those set aside. */
    fun all(facts: AdvisorFacts): List<Insight> =
        rules.flatMap { it.check(facts, tuning) }.sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.code })

    fun hide(code: String, tick: Long, days: Long = DEFAULT_HIDE_DAYS) { hiddenUntil[code] = tick + days.coerceAtLeast(1L) }

    fun unhide(code: String) { hiddenUntil.remove(code) }

    fun unhideAll() { hiddenUntil.clear() }

    fun isHidden(code: String, tick: Long): Boolean = (hiddenUntil[code] ?: Long.MIN_VALUE) > tick

    fun hiddenCodes(tick: Long): List<String> = hiddenUntil.filterValues { it > tick }.keys.toList()

    companion object { const val DEFAULT_HIDE_DAYS = 30L }
}
