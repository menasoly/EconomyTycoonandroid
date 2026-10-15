package app.many.economytycoon.infra

/** Severity of a diagnostic report. */
enum class Severity { INFO, WARNING, DEFECT }

data class DiagnosticReport(
    val code: String,
    val severity: Severity,
    val message: String,
    val tick: Long = -1L,
)

/**
 * Collects invariant violations and other diagnostics. In debug mode [failFast] turns defects
 * into exceptions so tests fail loudly; in release defects are recorded and the offending
 * entity is quarantined by the caller.
 */
class Diagnostics(private val failFast: Boolean = false, private val capacity: Int = 1000) {
    private val reports = ArrayDeque<DiagnosticReport>()
    private var defectCount = 0

    fun report(code: String, severity: Severity, message: String, tick: Long = -1L) {
        if (reports.size >= capacity) reports.removeFirst()
        reports.addLast(DiagnosticReport(code, severity, message, tick))
        if (severity == Severity.DEFECT) {
            defectCount++
            if (failFast) throw IllegalStateException("defect $code: $message")
        }
    }

    fun defect(code: String, message: String, tick: Long = -1L) = report(code, Severity.DEFECT, message, tick)
    fun warning(code: String, message: String, tick: Long = -1L) = report(code, Severity.WARNING, message, tick)

    val defects: Int get() = defectCount
    fun all(): List<DiagnosticReport> = reports.toList()
    fun withCode(code: String): List<DiagnosticReport> = reports.filter { it.code == code }
    fun clear() { reports.clear(); defectCount = 0 }
}

/** Build-time switches resolved by the app module. */
data class BuildFlags(val debug: Boolean, val verboseSimulationLogs: Boolean = false)
