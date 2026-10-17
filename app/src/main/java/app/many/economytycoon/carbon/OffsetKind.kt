package app.many.economytycoon.carbon

/** The kinds of offset project a company can fund or buy credits from. */
enum class OffsetKind(val label: String, val blurb: String) {
    REFORESTATION("Reforestation", "Trees take years to grow and can burn or be felled, so credits arrive slowly and a share is held back as insurance."),
    METHANE_CAPTURE("Methane capture", "Capturing landfill and mine gas is quick, easy to verify and clearly additional."),
    CLEAN_COOKING("Clean cooking", "Cheap stoves for households. Cheap credits, but much of the saving would have happened anyway."),
    DIRECT_AIR_CAPTURE("Direct air capture", "Machines pull carbon out of the air. Dear, but certain and permanent."),
}
