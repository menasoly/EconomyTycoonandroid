package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository

/** Everything the detail screen says about one blueprint, computed together. */
data class BlueprintAnalysis(
    val blueprint: Blueprint,
    val compat: CompatibilityReport,
    /** The blueprint with renames applied, which is what validation and costing look at; the original when it cannot be used. */
    val effective: Blueprint,
    val validation: ValidationReport,
    val cost: CostEstimate?,
) {
    val isUsable: Boolean get() = compat.isUsable
    val canApply: Boolean get() = compat.isUsable && validation.isClean
}

/** Runs compatibility, validation and cost estimation for a blueprint in one go. */
class BlueprintAnalyzer(
    private val content: ContentRepository,
    renames: RenameTable = RenameTable.NONE,
    validationParams: ValidationParams = ValidationParams(),
) {
    private val compat = CompatibilityChecker(content, renames)
    private val validator = BlueprintValidator(content, validationParams)
    private val estimator = CostEstimator(content)

    fun analyze(bp: Blueprint, research: ResearchView = ResearchView.ALL, site: SiteInfo? = null): BlueprintAnalysis {
        val report = compat.check(bp)
        val effective = report.migrated ?: bp
        val validation = if (report.isUsable) validator.validate(effective.spec, research, site) else ValidationReport(
            listOf(BlueprintIssue(IssueSeverity.ERROR, IssueCodes.UNKNOWN_FACILITY, "This kind of facility is no longer in the game.", "facilityType")),
        )
        return BlueprintAnalysis(bp, report, effective, validation, if (report.isUsable) estimator.estimate(effective.spec) else null)
    }

    /** The blueprint to plan with: renames applied, nothing else touched. Null when it can no longer be used. */
    fun usable(bp: Blueprint): Blueprint? = compat.check(bp).migrated

    fun currentTag(): String = compat.currentTag()
}
