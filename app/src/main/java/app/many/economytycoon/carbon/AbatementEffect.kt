package app.many.economytycoon.carbon

import app.many.economytycoon.core.Rate

/** What an installed abatement measure does. */
enum class AbatementKind(val label: String, val blurb: String) {
    EFFICIENCY_RETROFIT("Efficiency retrofit", "Better insulation, heat recovery and controls cut the fuel and electricity a plant burns."),
    FUEL_SWITCH("Fuel switch", "Replace the dirtiest fuel with a cleaner one. Cuts what that fuel releases, at a higher running cost."),
    CARBON_CAPTURE("Carbon capture", "Trap most of the carbon dioxide at the stack and store it. Expensive to build and to run."),
}

/** Which of a facility's emission sources a measure acts on. */
enum class SourceKind(val label: String) {
    FUEL("Fuel"),
    PROCESS("Process"),
    ELECTRICITY("Electricity"),
    OVERHEAD("Site"),
}

/**
 * The permanent effect of a finished abatement project on one facility's reported emissions: every direct source it
 * [appliesTo] is cut by [reductionPpm]. A measure limited to one fuel carries that fuel's key in [fuelKey].
 */
data class AbatementEffect(
    val kind: AbatementKind,
    val reductionPpm: Long,
    val fuelKey: String? = null,
) {
    init { require(reductionPpm in 0L..Rate.ONE_PPM) { "reduction must be between 0 and 100%" } }

    /** The kinds of source this measure cuts. */
    val appliesTo: Set<SourceKind>
        get() = when (kind) {
            AbatementKind.EFFICIENCY_RETROFIT -> setOf(SourceKind.FUEL, SourceKind.ELECTRICITY, SourceKind.OVERHEAD)
            AbatementKind.FUEL_SWITCH -> setOf(SourceKind.FUEL)
            AbatementKind.CARBON_CAPTURE -> setOf(SourceKind.FUEL, SourceKind.PROCESS, SourceKind.OVERHEAD)
        }

    /** The share of a source line that survives this measure, in ppm. */
    fun survivingPpm(source: SourceKind, key: String): Long {
        if (source !in appliesTo) return Rate.ONE_PPM
        if (fuelKey != null && (source != SourceKind.FUEL || key != fuelKey)) return Rate.ONE_PPM
        return Rate.ONE_PPM - reductionPpm
    }
}
