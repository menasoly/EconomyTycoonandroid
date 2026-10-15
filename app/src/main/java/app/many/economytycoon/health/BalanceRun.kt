package app.many.economytycoon.health

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.simulation.SimConfig
import app.many.economytycoon.simulation.Simulation
import app.many.economytycoon.world.WorldSize

/** One simulated world, sampled along the way. */
class BalanceResult(val seed: Long, val days: Long, val samples: List<HealthSample>, val problems: List<String>, val issues: List<HealthIssue>) {
    val last: HealthSample get() = samples.last()
    fun has(code: String) = issues.any { it.code == code }
    fun worst(): HealthSeverity? = issues.maxByOrNull { it.severity.ordinal }?.severity
}

/**
 * Plays whole games with no player and watches the economy. Each seed is run on its own, sampled every
 * [sampleEvery] days, with the ledger invariants checked on the way. Used to see whether a change to content or
 * rules leaves the world alive, and from tests to keep it that way.
 */
class BalanceRun(
    private val content: ContentRepository,
    private val size: WorldSize = WorldSize.SMALL,
    private val rivals: Int = 6,
    private val limits: HealthLimits = HealthLimits(),
) {
    fun run(seed: Long, days: Long, sampleEvery: Long = 30L): BalanceResult {
        val sim = Simulation(SimConfig(seed = seed, worldSize = size, aiCompanies = rivals), content)
        sim.pipeline.checkInvariants = true
        val samples = ArrayList<HealthSample>()
        val problems = ArrayList<String>()
        samples += HealthSample.take(sim.economy)
        var done = 0L
        while (done < days) {
            val s = sim.advance()
            done++
            if (s.problems.isNotEmpty() && problems.size < 20) problems += s.problems.map { "t${s.tick.value}: $it" }
            if (done % sampleEvery == 0L || done == days) samples += HealthSample.take(sim.economy)
        }
        return BalanceResult(seed, days, samples, problems, HealthRules.check(samples, limits))
    }

    fun runAll(seeds: List<Long>, days: Long, sampleEvery: Long = 30L): List<BalanceResult> = seeds.map { run(it, days, sampleEvery) }
}

object BalanceFormat {
    fun line(s: HealthSample): String =
        "day ${s.tick.toString().padStart(4)}  money ${s.moneySupply.toString().padStart(12)}  ai ${s.aiCompanies} strained ${s.aiStrained} broke ${s.aiWithoutCash}  " +
            "plants ${s.aiOperating}/${s.aiFacilities}  prices cheap ${s.marketsCheap} dear ${s.marketsDear} of ${s.markets}  median ${s.medianPriceRatioPpm / 10_000}%"

    fun result(r: BalanceResult): String = buildString {
        appendLine("seed ${r.seed}, ${r.days} days")
        for (s in r.samples) appendLine("  " + line(s))
        for (i in r.issues) appendLine("  ${i.severity.name.padEnd(7)} ${i.code}: ${i.message}")
        for (p in r.problems.take(5)) appendLine("  INVARIANT $p")
    }

    fun summary(results: List<BalanceResult>): String {
        val counts = results.flatMap { it.issues }.groupingBy { it.code }.eachCount().toSortedMap()
        return "${results.size} worlds: " + if (counts.isEmpty()) "no issues" else counts.entries.joinToString(", ") { "${it.key} x${it.value}" }
    }
}
