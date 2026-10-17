package app.many.economytycoon.workshop

/** A scenario in a bundle that could not be read: its number in the bundle (from one) and why. */
data class BundleFailure(val index: Int, val error: ShareError) {
    override fun toString() = "scenario $index: $error"
}

class BundleResult(val drafts: List<ScenarioDraft>, val failures: List<BundleFailure>, val warnings: List<String>) {
    val complete: Boolean get() = failures.isEmpty()
}

/**
 * Several scenarios in one piece of text: a header line and then one share code per scenario, separated by blank
 * lines. Each code keeps its own checksum, so one damaged scenario costs only itself, and a single code pasted on its
 * own is a bundle of one. Anything that is not part of a code (the header, a note someone wrote above it) is ignored.
 */
object ShareBundle {
    const val MAX_SCENARIOS = 25

    fun header(count: Int) = "Economy Tycoon scenario bundle: $count scenario" + if (count == 1) "" else "s"

    fun encode(drafts: List<ScenarioDraft>): String {
        require(drafts.size <= MAX_SCENARIOS) { "a bundle holds at most $MAX_SCENARIOS scenarios" }
        require(drafts.map { it.id }.distinct().size == drafts.size) { "scenario ids in a bundle must differ" }
        return (listOf(header(drafts.size)) + drafts.map { ShareCodec.encode(it) }).joinToString("\n\n") + "\n"
    }

    fun decode(text: String): BundleResult {
        val blocks = splitBlocks(text).filter { it.second.contains(ShareCodec.PREFIX, ignoreCase = true) }
        val drafts = ArrayList<ScenarioDraft>(); val failures = ArrayList<BundleFailure>(); val warnings = ArrayList<String>()
        for ((n, block) in blocks.withIndex()) {
            if (n >= MAX_SCENARIOS) { warnings += "only the first $MAX_SCENARIOS scenarios were read"; break }
            when (val r = ShareCodec.decode(block.second)) {
                is ShareResult.Decoded -> {
                    if (drafts.any { it.id == r.draft.id }) warnings += "scenario ${n + 1} repeats the id '${r.draft.id}' and was skipped"
                    else { drafts += r.draft; warnings += r.warnings.map { "scenario ${n + 1}: $it" } }
                }
                is ShareResult.Failed -> failures += BundleFailure(n + 1, ShareError(TextPosition(block.first + r.error.at.line - 1, r.error.at.column), r.error.message))
            }
        }
        return BundleResult(drafts, failures, warnings)
    }

    /** Blocks of consecutive non-blank lines, each with the line number (from one) it starts on. */
    private fun splitBlocks(text: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        val current = ArrayList<String>(); var start = 0
        fun flush() { if (current.isNotEmpty()) { out += start to current.joinToString("\n"); current.clear() } }
        for ((i, line) in text.lines().withIndex()) {
            if (line.isBlank()) flush() else { if (current.isEmpty()) start = i + 1; current += line }
        }
        flush()
        return out
    }
}
