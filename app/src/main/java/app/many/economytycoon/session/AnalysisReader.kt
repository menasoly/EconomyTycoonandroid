package app.many.economytycoon.session

import app.many.economytycoon.analysis.AnalysisInput
import app.many.economytycoon.analysis.Benchmark
import app.many.economytycoon.analysis.CompanyAnalysis
import app.many.economytycoon.analysis.Measure
import app.many.economytycoon.analysis.Standing
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.simulation.Economy

/** A company's analysis together with how it stands against the other active companies. */
class AnalysisView(val company: CompanyId, val name: String, val analysis: CompanyAnalysis, val standings: List<Standing>, val peerCount: Int)

/** Reads the live game into the statements the analysis package works on. */
class AnalysisReader(private val e: Economy, private val reports: SessionReports, private val stats: StatsRecorder) {
    private fun input(id: CompanyId, withHistory: Boolean): AnalysisInput? {
        val pack = reports.company.finance(id) ?: return null
        val history = if (withHistory) stats.of(id).map { it.netWorth } else emptyList()
        return AnalysisInput(
            income = pack.month, balance = pack.balance, cashFlow = pack.cashFlow, periodDays = PERIOD_DAYS,
            netWorthHistory = history, historyStepDays = STEP_DAYS, asOfDay = e.tick.value,
        )
    }

    fun analyse(id: CompanyId): CompanyAnalysis? = input(id, true)?.let { CompanyAnalysis.of(it) }

    /** Other active companies worth comparing with: those that have traded this month. */
    fun peersOf(id: CompanyId): List<CompanyId> =
        e.companies.active().filter { it.id != id && it.kind != CompanyKind.STATE }.sortedBy { it.id }.map { it.id }
            .filter { reports.company.finance(it)?.month?.revenue?.isPositive == true }

    fun view(id: CompanyId): AnalysisView? {
        val c = e.companies.company(id) ?: return null
        val mine = analyse(id) ?: return null
        val peers = peersOf(id).mapNotNull { p -> input(p, false)?.let { CompanyAnalysis.of(it).values } }
        return AnalysisView(id, c.name, mine, Benchmark.compare(mine.values, peers), peers.size)
    }

    companion object {
        const val PERIOD_DAYS = 30L
        const val STEP_DAYS = 5L

        fun measures(): List<Measure> = Measure.values().toList()
    }
}
