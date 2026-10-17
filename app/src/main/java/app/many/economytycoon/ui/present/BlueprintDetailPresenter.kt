package app.many.economytycoon.ui.present

import app.many.economytycoon.blueprint.BlueprintAnalysis
import app.many.economytycoon.blueprint.BlueprintIssue
import app.many.economytycoon.blueprint.IssueSeverity
import app.many.economytycoon.blueprint.NoteKind
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting

data class DetailLineRow(val title: String, val machines: String, val status: String, val tone: Tone, val profit: String?)

data class IssueRow(val text: String, val where: String, val tone: Tone)

data class BlueprintDetailModel(
    val title: String,
    val subtitle: String,
    val tiles: List<Tile>,
    val facts: List<Fact>,
    val lines: List<DetailLineRow>,
    val staffing: List<Fact>,
    val rules: List<Fact>,
    val issues: List<IssueRow>,
    val notes: List<String>,
    val canApply: Boolean,
    val verdict: String,
    val verdictTone: Tone,
)

/** One blueprint in full: what it costs, how fast it should pay back, what it contains, and what is wrong with it. */
object BlueprintDetailPresenter {
    fun issueTone(s: IssueSeverity): Tone = when (s) { IssueSeverity.ERROR -> Tone.BAD; IssueSeverity.WARNING -> Tone.WARN; IssueSeverity.INFO -> Tone.INFO }

    fun issue(i: BlueprintIssue) = IssueRow(i.message, i.path, issueTone(i.severity))

    fun payback(days: Long?): String = if (days == null) "Never at this pace" else Formatting.duration(days)

    fun paybackTone(days: Long?): Tone = when {
        days == null -> Tone.BAD
        days <= 180 -> Tone.GOOD
        days <= 720 -> Tone.NEUTRAL
        else -> Tone.WARN
    }

    fun model(a: BlueprintAnalysis, content: ContentRepository, money: (Money) -> String = { Formatting.money(it) }): BlueprintDetailModel {
        val spec = a.effective.spec
        val type = content.facilityTypeOrNull(spec.facilityType)
        val cost = a.cost
        val tiles = if (cost == null) emptyList() else listOf(
            Tile("Capital", money(cost.capital), note = "build ${money(cost.buildCost)}"),
            Tile("Pays back", payback(cost.paybackDays), paybackTone(cost.paybackDays)),
            Tile("Net a day", money(cost.netPerDay), Tones.bySign(cost.netPerDay.raw)),
            Tile("Return a year", Formatting.percentPpm(cost.annualReturnPpm), Tones.bySign(cost.annualReturnPpm)),
        )
        val facts = ArrayList<Fact>()
        facts += Fact("Facility", type?.name ?: spec.facilityType.key)
        if (cost != null) {
            facts += Fact("Build time", Formatting.duration(cost.buildTicks.toLong()))
            facts += Fact("Machines", money(cost.machines), hint = "${spec.machineCount} installed")
            facts += Fact("Extra lines", money(cost.extraLines))
            if (spec.storageExpansions > 0) facts += Fact("Storage", money(cost.storage), hint = "${spec.storageExpansions} expansions")
            facts += Fact("Upkeep a day", money(cost.upkeepPerDay + cost.machineUpkeepPerDay + cost.overheadStaffPerDay))
        }
        facts += Fact("Scheduling", spec.policy.name.lowercase().replace('_', ' '))
        if (spec.qualityPpm > 0) facts += Fact("Quality assurance", Formatting.percentPpm(spec.qualityPpm))
        if (a.blueprint.contentTag.isNotBlank()) facts += Fact("Made for", a.blueprint.contentTag)

        val econ = cost?.lineEconomies.orEmpty()
        val lines = spec.lines.mapIndexed { i, l ->
            val e = econ.getOrNull(i)
            val recipe = l.recipe?.let { content.recipeOrNull(it)?.name ?: it.key } ?: "No recipe"
            DetailLineRow(
                "Line ${i + 1}: $recipe",
                l.machines.joinToString(", ") { content.machineTypeOrNull(it)?.name ?: it.key }.ifEmpty { "No machines" },
                e?.idleReason?.let { it.replaceFirstChar { c -> c.uppercase() } } ?: "Running",
                if (e?.idleReason != null) Tone.WARN else Tone.GOOD,
                e?.takeIf { it.idleReason == null }?.let { money(it.profitPerDay) + " a day" },
            )
        }
        val staffing = spec.staff.map { s ->
            Fact(content.allRoles.firstOrNull { it.id == s.role }?.name ?: s.role.key, s.target.toString(),
                hint = listOfNotNull(s.wage?.let { "wage ${money(it)}" }, s.trainingPpm.takeIf { it > 0 }?.let { "training ${Formatting.percentPpm(it)}" }).joinToString(", ").ifEmpty { null })
        }
        val rules = spec.sells.map { s -> Fact("Sell ${content.goodOrNull(s.good)?.name ?: s.good.key}", s.strategy.name.lowercase().replace('_', ' '), hint = "keep ${Formatting.quantity(s.reserve)}") } +
            spec.buys.map { b -> Fact("Stock ${content.goodOrNull(b.good)?.name ?: b.good.key}", Formatting.quantity(b.target), hint = "up to ${Formatting.percentPpm(b.maxPriceMultiplePpm)} of market") }

        val issues = a.validation.issues.map { issue(it) }
        val notes = a.compat.notes.map { it.text } +
            if (a.compat.notes.any { it.kind == NoteKind.REMOVED }) listOf("Parts that no longer exist are left out when you apply this blueprint.") else emptyList()
        val (verdict, tone) = when {
            !a.isUsable -> "This blueprint can no longer be used." to Tone.BAD
            a.validation.errors.isNotEmpty() -> "Not everything in it can be built yet (${a.validation.errors.size} blocking)." to Tone.WARN
            a.validation.warnings.isNotEmpty() -> "Ready, with ${a.validation.warnings.size} thing${if (a.validation.warnings.size == 1) "" else "s"} to check." to Tone.INFO
            else -> "Ready to apply." to Tone.GOOD
        }
        return BlueprintDetailModel(a.blueprint.name, a.blueprint.note.ifBlank { type?.name ?: "" }, tiles, facts, lines, staffing, rules, issues, notes, a.isUsable, verdict, tone)
    }
}
