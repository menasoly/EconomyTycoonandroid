package app.many.economytycoon.content.audit

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Money

enum class AuditSeverity { INFO, WARN, ERROR }

data class AuditFinding(val code: String, val severity: AuditSeverity, val subject: String, val message: String) {
    override fun toString() = "${severity.name.padEnd(5)} $code  $subject: $message"
}

/** Limits that tell the audit what is out of balance. */
data class AuditTuning(
    /** A recipe losing more than this share of its cost is an error: nobody would run it. */
    val lossMarginPpm: Long = -50_000L,
    /** A recipe earning more than this share of its cost is suspect. */
    val windfallMarginPpm: Long = 3_000_000L,
    /** Facilities that take longer than this to earn back their cost are flagged. */
    val slowPaybackDays: Long = 2_160L,
    /** Facilities that pay back faster than this are flagged as too easy. */
    val fastPaybackDays: Long = 20L,
    /** A good priced below this share of its chain cost cannot be made at a profit. */
    val chainLossPpm: Long = 700_000L,
    /** A good priced above this multiple of its chain cost is a money machine. */
    val chainWindfallPpm: Long = 4_000_000L,
)

class AuditReport(
    val recipes: List<RecipeEconomy>,
    val paybacks: List<Payback>,
    val findings: List<AuditFinding>,
) {
    fun withCode(code: String) = findings.filter { it.code == code }
    fun bySeverity(s: AuditSeverity) = findings.filter { it.severity == s }
    val errors: List<AuditFinding> get() = bySeverity(AuditSeverity.ERROR)
    val warnings: List<AuditFinding> get() = bySeverity(AuditSeverity.WARN)
}

/**
 * Looks over loaded content for economic mistakes: recipes that lose money or print it, goods nothing makes
 * or buys, facilities that never pay back. It reads only definitions and reference prices, so it runs in
 * milliseconds and needs no simulation.
 */
object ContentAudit {
    fun run(content: ContentRepository, tuning: AuditTuning = AuditTuning()): AuditReport {
        val findings = ArrayList<AuditFinding>()
        val recipes = RecipeEconomics.all(content)
        val paybacks = FacilityPayback.all(content, recipes)
        val flows = GoodFlows(content)
        val chain = ChainCost(content)

        for (r in recipes) {
            val id = r.recipe.id.key
            when {
                r.marginPpm < tuning.lossMarginPpm ->
                    findings += AuditFinding("recipe.loss", AuditSeverity.ERROR, id, "loses ${-r.marginPpm / 10_000}% of its cost at reference prices")
                r.marginPpm > tuning.windfallMarginPpm ->
                    findings += AuditFinding("recipe.windfall", AuditSeverity.WARN, id, "earns ${r.marginPpm / 10_000}% over cost at reference prices")
            }
            if (r.outputValue <= 0L) findings += AuditFinding("recipe.worthless", AuditSeverity.WARN, id, "its outputs have no reference value")
        }

        for (g in flows.unmade()) {
            findings += AuditFinding("good.unmade", AuditSeverity.WARN, g.id.key, "no recipe or extractor produces it")
        }
        for (g in flows.unreachable()) {
            if (flows.producersOf(g.id).isNotEmpty()) findings += AuditFinding("good.unreachable", AuditSeverity.ERROR, g.id.key, "needs an ingredient that cannot be made from the ground")
        }
        for (g in flows.unused()) {
            if (flows.producersOf(g.id).isNotEmpty()) findings += AuditFinding("good.unused", AuditSeverity.INFO, g.id.key, "is made but nothing uses or buys it")
        }

        for (p in paybacks) {
            val id = p.facility.id.key
            val best = p.bestRecipe
            if (best == null) {
                if (p.facility.maxLines > 0) findings += AuditFinding("facility.norecipe", AuditSeverity.INFO, id, "has production lines but no recipe runs here")
                continue
            }
            val days = p.paybackDays
            when {
                days == null -> findings += AuditFinding("facility.neverpays", AuditSeverity.ERROR, id, "earns ${Money(p.netPerDay).wholeUnits()} a day after costs, so it never pays back")
                days > tuning.slowPaybackDays -> findings += AuditFinding("facility.slow", AuditSeverity.WARN, id, "takes $days days to pay back")
                days < tuning.fastPaybackDays -> findings += AuditFinding("facility.fast", AuditSeverity.WARN, id, "pays back in only $days days")
            }
        }

        for (g in content.allGoods) {
            if (flows.producersOf(g.id).isEmpty() || content.recipesProducing(g.id).isEmpty()) continue
            val ratio = chain.priceToCostPpm(g.id)
            if (ratio < tuning.chainLossPpm) findings += AuditFinding("chain.loss", AuditSeverity.WARN, g.id.key, "sells for ${ratio / 10_000}% of what it costs to make from the start")
            if (ratio > tuning.chainWindfallPpm) findings += AuditFinding("chain.windfall", AuditSeverity.WARN, g.id.key, "sells for ${ratio / 10_000}% of what it costs to make from the start")
        }
        return AuditReport(recipes, paybacks, findings.sortedWith(compareByDescending<AuditFinding> { it.severity }.thenBy { it.code }.thenBy { it.subject }))
    }
}
