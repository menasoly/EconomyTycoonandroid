package app.many.economytycoon.ui.present

import app.many.economytycoon.session.Formatting
import app.many.economytycoon.workshop.BalanceReport
import app.many.economytycoon.workshop.CompatReport
import app.many.economytycoon.workshop.DifficultyBand
import app.many.economytycoon.workshop.IssueSeverity
import app.many.economytycoon.workshop.PolicyDigest
import app.many.economytycoon.workshop.RegressionReport
import app.many.economytycoon.workshop.RunMetrics
import app.many.economytycoon.workshop.ScoreBreakdown
import app.many.economytycoon.workshop.StudyResult
import app.many.economytycoon.workshop.SweepReport

data class DraftPolicyRow(val policy: String, val tone: Tone, val facts: List<Fact>, val curve: List<Int>)

data class FindingRow(val code: String, val text: String, val tone: Tone, val about: String?)

data class ReportModel(val headline: String, val tone: Tone, val tiles: List<Tile>, val policies: List<DraftPolicyRow>, val findings: List<FindingRow>, val empty: String?)

/** How a balance study reads on screen: a verdict, one block per policy with a small curve of net worth, and the findings. */
object WorkshopReportPresenter {
    const val NOTHING_YET = "Run a test to see how the scenario plays. Four kinds of player are tried on a few seeds."
    private val BLOCKS = listOf('▁', '▂', '▃', '▄', '▅', '▆', '▇', '█')

    fun bandTone(b: DifficultyBand): Tone = when (b) {
        DifficultyBand.TRIVIAL -> Tone.WARN
        DifficultyBand.EASY, DifficultyBand.MODERATE -> Tone.GOOD
        DifficultyBand.HARD -> Tone.INFO
        DifficultyBand.BRUTAL -> Tone.WARN
        DifficultyBand.IMPOSSIBLE -> Tone.BAD
    }

    fun severityTone(s: IssueSeverity): Tone = WorkshopValidationPresenter.tone(s)

    /** [values] scaled to 0..7 over [buckets] columns, each the last value in its stretch; flat data sits on the middle level. */
    fun curve(values: List<Long>, buckets: Int = 16): List<Int> {
        if (values.isEmpty() || buckets < 1) return emptyList()
        val picked = if (values.size <= buckets) values else List(buckets) { values[(it * (values.size - 1)) / (buckets - 1).coerceAtLeast(1)] }
        val lo = picked.min(); val hi = picked.max()
        if (hi == lo) return picked.map { 3 }
        return picked.map { (((it - lo) * 7 + (hi - lo) / 2) / (hi - lo)).toInt() }
    }

    fun spark(levels: List<Int>): String = levels.map { BLOCKS[it.coerceIn(0, 7)] }.joinToString("")

    fun policyRow(d: PolicyDigest, runs: List<RunMetrics>): DraftPolicyRow {
        val tone = when {
            d.wins * 2 >= d.runs -> Tone.GOOD
            d.losses > 0 || d.bankruptRuns > 0 || d.softlockRuns > 0 -> Tone.WARN
            else -> Tone.NEUTRAL
        }
        val facts = listOf(
            Fact("Runs", "${d.runs} (${d.wins} won, ${d.losses} lost)"),
            Fact("Progress", Formatting.percentPpm(d.progress.median), Tones.health(d.progress.median)),
            Fact("Net worth", Formatting.grouped(d.netWorth.median)),
            Fact("Cash", Formatting.grouped(d.cash.median), if (d.cash.median < 0) Tone.BAD else Tone.NEUTRAL),
            Fact("Goals met", d.daysToWin?.let { "day ${it.median}" } ?: d.projectedDays?.let { "about day $it" } ?: "not on track", if (d.daysToWin != null) Tone.GOOD else Tone.MUTED),
            Fact("Bankruptcies", d.bankruptRuns.toString(), if (d.bankruptRuns > 0) Tone.BAD else Tone.NEUTRAL),
            Fact("Stuck runs", d.softlockRuns.toString(), if (d.softlockRuns > 0) Tone.BAD else Tone.NEUTRAL),
        )
        val first = runs.firstOrNull { it.policy == d.policy }
        return DraftPolicyRow(d.policy, tone, facts, first?.let { curve(it.netWorthCurve.map { p -> p.second }) } ?: emptyList())
    }

    fun finding(f: app.many.economytycoon.workshop.BalanceFinding): FindingRow =
        FindingRow(f.code, f.message, severityTone(f.severity), listOfNotNull(f.policy, f.seed?.let { "seed $it" }).joinToString(", ").ifEmpty { null })

    fun model(study: StudyResult?): ReportModel {
        if (study == null) return ReportModel("No results yet", Tone.MUTED, emptyList(), emptyList(), emptyList(), NOTHING_YET)
        return model(study.report, study.runs)
    }

    fun model(report: BalanceReport, runs: List<RunMetrics>): ReportModel {
        val r = report.rating
        val tone = if (report.problems.isNotEmpty()) Tone.BAD else bandTone(r.band)
        val headline = "${r.band.label}: ${r.score} of 100" + if (report.problems.isNotEmpty()) ", ${report.problems.size} problems" else ""
        val tiles = listOf(
            Tile("Difficulty", r.band.label, bandTone(r.band), "${r.score} of 100"),
            Tile("Best player", r.bestPolicy ?: "none", note = r.projectedDays?.let { "goals by about day $it" }),
            Tile("Time allowed", "${report.deadlineDays} days", note = "played ${report.horizonDays}"),
            Tile("Seeds", report.seeds.size.toString()),
            Tile("Problems", report.problems.size.toString(), if (report.problems.isEmpty()) Tone.GOOD else Tone.BAD, "${report.warnings.size} warnings"),
        )
        return ReportModel(headline, tone, tiles, report.digests.map { policyRow(it, runs) }, report.findings.map { finding(it) }, null)
    }

    // ---- the other results --------------------------------------------------------------------------

    fun sweepLines(s: SweepReport?): List<String> {
        if (s == null) return listOf("No sweep has been run.")
        val head = "${s.spec.param.label}: ${s.rows.size} values, progress ${s.progressTrend.label}" + (s.bestValue?.let { ", best at ${Formatting.grouped(it)}" } ?: "")
        return listOf(head) + s.lines()
    }

    fun regressionLines(r: RegressionReport?): List<String> = r?.lines() ?: listOf("No baseline comparison yet.")

    fun regressionTone(r: RegressionReport?): Tone = when {
        r == null -> Tone.MUTED
        r.identical -> Tone.GOOD
        r.passed -> Tone.INFO
        else -> Tone.BAD
    }

    fun compatRows(c: CompatReport): List<FindingRow> =
        c.findings.map { FindingRow(it.code, "${it.path}: ${it.message}", severityTone(it.severity), null) }

    fun compatHeadline(c: CompatReport): String = when {
        c.findings.isEmpty() -> "Fits the loaded content."
        c.compatible -> "Fits the loaded content, with ${c.findings.size} remarks."
        else -> "Does not fit the loaded content: ${c.errors.size} errors."
    }

    fun scoreFacts(b: ScoreBreakdown): List<Fact> =
        b.lines.map { Fact(it.label, (if (it.points > 0) "+" else "") + Formatting.grouped(it.points), if (it.points < 0) Tone.BAD else Tone.NEUTRAL) } +
            Fact("Difficulty multiplier", "${b.multiplierPercent}%") + Fact("Score", Formatting.grouped(b.total), Tone.GOOD)
}
