package app.many.economytycoon.workshop

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.scenarios.ScenarioCatalog

enum class IssueSeverity { ERROR, WARNING, NOTE }

/**
 * One finding about a draft. [path] names the exact field, e.g. `goals[1].target` or `timing.gold`, so a screen can
 * put the message next to it and the text form can point at the line it came from.
 */
data class DraftIssue(val code: String, val severity: IssueSeverity, val path: String, val message: String, val fix: String? = null) {
    override fun toString() = "${severity.name.padEnd(7)} $path  $code: $message"
}

class ValidationReport(val issues: List<DraftIssue>) {
    val errors: List<DraftIssue> get() = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings: List<DraftIssue> get() = issues.filter { it.severity == IssueSeverity.WARNING }
    val notes: List<DraftIssue> get() = issues.filter { it.severity == IssueSeverity.NOTE }

    /** A draft with no errors can be turned into a playable scenario. */
    val ok: Boolean get() = issues.none { it.severity == IssueSeverity.ERROR }

    fun has(code: String) = issues.any { it.code == code }
    fun withCode(code: String) = issues.filter { it.code == code }

    /** Findings about [path] itself or anything below it (`goals[1]` covers `goals[1].target`). */
    fun at(path: String): List<DraftIssue> = issues.filter { it.path == path || it.path.startsWith("$path.") || it.path.startsWith("$path[") }

    fun summary(): String = "${errors.size} errors, ${warnings.size} warnings, ${notes.size} notes"
}

/**
 * Checks a draft the way the game would on loading it, and some things the game would only discover while playing:
 * goals already met at the start, failure conditions that trigger on day one, targets no company could reach in the
 * time given. Every finding names the field it is about. With content at hand it also checks the names used.
 */
object DraftValidator {
    /** Growth in net worth beyond this factor a year is not something a tuned scenario should ask for, in ppm. */
    private const val AMBITIOUS_YEARLY_GROWTH_PPM = 1_600_000L

    fun validate(draft: ScenarioDraft, content: ContentRepository? = null): ValidationReport {
        val out = ArrayList<DraftIssue>()
        identity(draft, out)
        world(draft, out)
        company(draft, out, content)
        rules(draft, out)
        timing(draft, out)
        goals(draft, out, content)
        events(draft, out)
        fails(draft, out)
        extras(draft, out)
        return ValidationReport(out.sortedWith(compareBy<DraftIssue> { it.severity.ordinal }.thenBy { it.path }.thenBy { it.code }))
    }

    private fun err(out: MutableList<DraftIssue>, code: String, path: String, message: String, fix: String? = null) { out += DraftIssue(code, IssueSeverity.ERROR, path, message, fix) }
    private fun warn(out: MutableList<DraftIssue>, code: String, path: String, message: String, fix: String? = null) { out += DraftIssue(code, IssueSeverity.WARNING, path, message, fix) }
    private fun note(out: MutableList<DraftIssue>, code: String, path: String, message: String, fix: String? = null) { out += DraftIssue(code, IssueSeverity.NOTE, path, message, fix) }

    private fun identity(d: ScenarioDraft, out: MutableList<DraftIssue>) {
        if (!ScenarioDraft.validId(d.id)) err(out, "id.format", "id", "'${d.id}' is not a valid id; use lowercase letters, digits and single dashes, up to 40 characters", "for example 'my-scenario'")
        else if (ScenarioCatalog.byId(d.id) != null) err(out, "id.taken", "id", "'${d.id}' is the id of a scenario that ships with the game", "pick a different id")
        if (d.title.length !in DraftLimits.TITLE) err(out, "title.length", "title", "the title has ${d.title.length} characters; it needs ${DraftLimits.TITLE.first} to ${DraftLimits.TITLE.last}")
        if (d.title.any { it.code < 0x20 }) err(out, "title.chars", "title", "the title may not contain line breaks or control characters")
        if (d.tagline.length > DraftLimits.MAX_TAGLINE) err(out, "tagline.length", "tagline", "the tagline has ${d.tagline.length} characters; the most is ${DraftLimits.MAX_TAGLINE}")
        if (d.tagline.any { it.code < 0x20 }) err(out, "tagline.chars", "tagline", "the tagline may not contain line breaks or control characters")
        if (d.tagline.isBlank()) note(out, "tagline.empty", "tagline", "no tagline; the scenario list will show nothing under the title")
        if (d.story.length > DraftLimits.MAX_STORY) err(out, "story.length", "story", "the story has ${d.story.length} characters; the most is ${DraftLimits.MAX_STORY}")
        if (d.story.isBlank()) note(out, "story.empty", "story", "no story; players start without a briefing")
    }

    private fun world(d: ScenarioDraft, out: MutableList<DraftIssue>) {
        val w = d.world
        if (w.rivals !in 0..DraftLimits.MAX_RIVALS) err(out, "world.rivals", "world.rivals", "${w.rivals} rivals is outside 0 to ${DraftLimits.MAX_RIVALS}")
        else if (w.rivals > w.size.countries * w.size.regionsPerCountry) warn(out, "world.crowded", "world.rivals", "${w.rivals} rivals in ${w.size.countries * w.size.regionsPerCountry} regions will share regions with each other", "choose a larger world or fewer rivals")
        if (w.countryIndex !in 0 until w.size.countries) err(out, "world.country", "world.country", "country ${w.countryIndex} does not exist; a ${w.size.name.lowercase()} world has ${w.size.countries}", "use 0 to ${w.size.countries - 1}")
    }

    private fun company(d: ScenarioDraft, out: MutableList<DraftIssue>, content: ContentRepository?) {
        val c = d.company
        if (c.name.trim().length !in DraftLimits.COMPANY_NAME) err(out, "company.name", "company.name", "the company name needs ${DraftLimits.COMPANY_NAME.first} to ${DraftLimits.COMPANY_NAME.last} characters")
        if (c.capital <= 0L) err(out, "company.capital", "company.capital", "starting capital is ${c.capital}; it must be positive")
        else if (c.capital < 20_000L) warn(out, "company.poor", "company.capital", "${c.capital} is too little to build anything", "start with at least 20,000")
        if (c.starters.size > DraftLimits.MAX_STARTERS) err(out, "starters.count", "company.starters", "${c.starters.size} starting sites; the most is ${DraftLimits.MAX_STARTERS}")
        val seen = HashSet<StarterSite>()
        for ((i, s) in c.starters.withIndex()) {
            val p = "company.starters[$i]"
            if (!s.recipe.matches(Regex("[a-z0-9_]+"))) err(out, "starter.recipe", "$p.recipe", "'${s.recipe}' is not a recipe key")
            else if (content != null) starterAgainstContent(s, p, out, content)
            if (s.regionIndex !in 0 until d.world.size.regionsPerCountry) err(out, "starter.region", "$p.region", "region ${s.regionIndex} does not exist; a ${d.world.size.name.lowercase()} country has ${d.world.size.regionsPerCountry}", "use 0 to ${d.world.size.regionsPerCountry - 1}")
            if (!seen.add(s)) warn(out, "starter.duplicate", p, "the same site is listed twice")
        }
    }

    private fun starterAgainstContent(s: StarterSite, p: String, out: MutableList<DraftIssue>, content: ContentRepository) {
        val recipe = content.recipeOrNull(RecipeId(s.recipe))
        if (recipe == null) { err(out, "starter.unknown", "$p.recipe", "no recipe '${s.recipe}' in the loaded content"); return }
        val type = content.facilityType(recipe.facilityType)
        when {
            recipe.requiredTech != null || type.requiredTech != null -> err(out, "starter.locked", "$p.recipe", "'${s.recipe}' needs a technology the company does not start with")
            recipe.machines.isNotEmpty() -> err(out, "starter.machines", "$p.recipe", "'${s.recipe}' needs machines; a starting site has none")
            type.extracts != null -> err(out, "starter.extractor", "$p.recipe", "'${s.recipe}' extracts from a deposit and cannot be placed at the start")
            type.maxLines <= 0 -> err(out, "starter.lines", "$p.recipe", "'${type.name}' has no production lines")
        }
    }

    private fun rules(d: ScenarioDraft, out: MutableList<DraftIssue>) {
        for ((k, v) in d.rules.entries()) {
            if (!k.inRange(v)) err(out, "rules.range", "rules.${k.key}", "${k.label} is $v; it must be between ${k.min} and ${k.max}")
            else if (v == k.engineDefault()) note(out, "rules.redundant", "rules.${k.key}", "${k.label} is set to the engine default", "remove the override")
        }
    }

    private fun timing(d: ScenarioDraft, out: MutableList<DraftIssue>) {
        val t = d.timing
        if (t.years !in DraftLimits.YEARS) err(out, "timing.years", "timing.years", "${t.years} years is outside ${DraftLimits.YEARS.first} to ${DraftLimits.YEARS.last}")
        if (t.goldDays <= 0) err(out, "timing.gold", "timing.gold", "the gold time must be positive")
        else if (t.goldDays < 90) warn(out, "timing.gold_tight", "timing.gold", "gold within ${t.goldDays} days is under a quarter; nobody can reach that")
        if (t.silverDays < t.goldDays) err(out, "timing.silver", "timing.silver", "silver (${t.silverDays} days) comes before gold (${t.goldDays} days)")
        if (t.years in DraftLimits.YEARS && t.deadlineDays < t.silverDays) err(out, "timing.deadline", "timing.years", "the deadline (${t.deadlineDays} days) is before the silver time (${t.silverDays} days)", "allow at least ${(t.silverDays + 359) / 360} years")
    }

    private fun goals(d: ScenarioDraft, out: MutableList<DraftIssue>, content: ContentRepository?) {
        if (d.goals.isEmpty()) { err(out, "goals.none", "goals", "a scenario needs at least one goal"); return }
        if (d.goals.size > DraftLimits.MAX_GOALS) err(out, "goals.count", "goals", "${d.goals.size} goals; the most is ${DraftLimits.MAX_GOALS}")
        if (d.requiredGoals.isEmpty()) err(out, "goals.no_required", "goals", "every goal is a bonus goal, so the scenario can never be won", "make at least one goal required")
        val firstOfKind = HashMap<GoalKind, Int>()
        val capital = d.company.capital
        var met = 0
        for ((i, g) in d.goals.withIndex()) {
            val p = "goals[$i]"
            val first = firstOfKind.putIfAbsent(g.kind, i)
            if (first != null) err(out, "goal.duplicate", "$p.kind", "${g.kind.noun} is already goal ${first + 1}; each kind may appear once")
            if (g.target <= 0L) { err(out, "goal.target", "$p.target", "the target is ${g.target}; it must be positive"); continue }
            if (g.weight !in DraftLimits.WEIGHT) err(out, "goal.weight", "$p.weight", "weight ${g.weight} is outside ${DraftLimits.WEIGHT.first} to ${DraftLimits.WEIGHT.last}")
            kindRules(d, g, p, out, content)
            if (isMetAtStart(d, g)) { if (!g.bonus) met++; warn(out, "goal.met_at_start", "$p.target", "${g.kind.noun} of ${g.target} is already met by the starting position", "raise the target above ${capital}") }
            else if (g.kind == GoalKind.NET_WORTH && g.target > capital && tooSteep(capital, g.target, d.timing.years)) {
                warn(out, "goal.ambitious", "$p.target", "growing ${capital} to ${g.target} in ${d.timing.years} years needs more than 60% growth a year", "lower the target or allow more years")
            }
        }
        if (met > 0 && met == d.requiredGoals.size) err(out, "goals.won_at_start", "goals", "every required goal is met by the starting position; the game would be won on the first check")
        val totalWeight = d.goals.sumOf { it.weight }
        if (totalWeight > 0 && d.goals.size > 1 && d.goals.any { it.weight * 100 / totalWeight > 70 }) note(out, "goal.lopsided", "goals", "one goal carries over 70% of the weight")
    }

    private fun kindRules(d: ScenarioDraft, g: DraftGoal, p: String, out: MutableList<DraftIssue>, content: ContentRepository?) {
        when (g.kind) {
            GoalKind.MARKET_LEADER -> {
                if (g.target != 1L) err(out, "goal.leader_target", "$p.target", "market leadership has a target of 1, not ${g.target}")
                if (d.world.rivals == 0) note(out, "goal.leader_alone", "$p.kind", "with no rivals the company is the richest from the start")
            }
            GoalKind.CREDIT_RANK -> {
                val top = CreditRating.values().size.toLong()
                if (g.target > top) err(out, "goal.rank_range", "$p.target", "a credit rating of ${g.target} steps does not exist; the top is $top")
            }
            GoalKind.COMPANIES -> if (g.target > d.world.rivals) err(out, "goal.companies", "$p.target", "controlling ${g.target} companies is impossible with ${d.world.rivals} rivals")
            GoalKind.INDUSTRIES -> if (content != null && g.target > content.allIndustries.size) err(out, "goal.industries", "$p.target", "${g.target} industries, but the content has ${content.allIndustries.size}")
            GoalKind.TECHNOLOGIES -> if (content != null && g.target > content.allTechnologies.size) err(out, "goal.technologies", "$p.target", "${g.target} technologies, but the content has ${content.allTechnologies.size}")
            GoalKind.FACILITIES, GoalKind.DEBT_FREE -> if (g.target > 500L) warn(out, "goal.huge", "$p.target", "${g.target} facilities is more than a phone can show")
            else -> Unit
        }
    }

    /** Whether the company has already reached [g] on the first day, before it has done anything. */
    internal fun isMetAtStart(d: ScenarioDraft, g: DraftGoal): Boolean = when (g.kind) {
        GoalKind.NET_WORTH, GoalKind.CASH -> g.target <= d.company.capital
        GoalKind.FACILITIES, GoalKind.DEBT_FREE -> g.target <= d.company.starters.size
        else -> false
    }

    private fun tooSteep(from: Long, to: Long, years: Int): Boolean {
        if (from <= 0L || years <= 0) return false
        var reach = from
        repeat(years.coerceAtMost(40)) {
            reach = reach * AMBITIOUS_YEARLY_GROWTH_PPM / 1_000_000L
            if (reach >= to) return false
        }
        return true
    }

    private fun events(d: ScenarioDraft, out: MutableList<DraftIssue>) {
        if (d.events.size > DraftLimits.MAX_EVENTS) err(out, "events.count", "events", "${d.events.size} events; the most is ${DraftLimits.MAX_EVENTS}")
        var windfall = 0L
        for ((i, e) in d.events.withIndex()) {
            val p = "events[$i]"
            if (e.day < 0) err(out, "event.day", "$p.day", "day ${e.day} is before the start")
            else if (e.day > d.deadlineDays) warn(out, "event.late", "$p.day", "day ${e.day} is after the deadline (${d.deadlineDays}); it will never happen")
            if (e.kind.hasAmount && e.amount <= 0L) err(out, "event.amount", "$p.amount", "a ${e.kind.label.lowercase()} needs a positive amount, not ${e.amount}")
            if (!e.kind.hasAmount && e.amount != 0L) note(out, "event.amount_ignored", "$p.amount", "an announcement carries no money; the amount is ignored")
            if (e.kind == EventKind.ANNOUNCE && e.text.isBlank()) err(out, "event.text", "$p.text", "an announcement needs a message")
            if (e.text.length > DraftLimits.MAX_EVENT_TEXT) err(out, "event.text_length", "$p.text", "the message has ${e.text.length} characters; the most is ${DraftLimits.MAX_EVENT_TEXT}")
            if (e.kind == EventKind.GRANT && e.amount > 0L) windfall += e.amount
        }
        if (d.company.capital > 0L && windfall > d.company.capital * 5) warn(out, "events.windfall", "events", "grants add up to $windfall, more than five times the starting capital", "the grants may decide the game")
    }

    private fun fails(d: ScenarioDraft, out: MutableList<DraftIssue>) {
        if (d.fails.size > DraftLimits.MAX_FAILS) err(out, "fails.count", "fails", "${d.fails.size} failure conditions; the most is ${DraftLimits.MAX_FAILS}")
        val capital = d.company.capital
        var firstWorth = -1
        for ((i, f) in d.fails.withIndex()) {
            val p = "fails[$i]"
            if (f.afterDay < 0) err(out, "fail.after", "$p.after", "the grace period cannot be negative")
            else if (f.afterDay > d.deadlineDays) warn(out, "fail.after_deadline", "$p.after", "the condition starts on day ${f.afterDay}, after the deadline")
            when (f.kind) {
                FailKind.NET_WORTH_BELOW -> {
                    if (f.threshold >= capital && f.afterDay == 0) err(out, "fail.instant", "$p.threshold", "net worth starts at about $capital, so a limit of ${f.threshold} loses the game at once")
                    if (firstWorth < 0) firstWorth = i
                    else note(out, "fail.not_in_def", p, "only the first net worth limit can be kept when the draft becomes a scenario definition")
                }
                FailKind.CASH_BELOW -> {
                    if (f.threshold >= capital && f.afterDay == 0) err(out, "fail.instant", "$p.threshold", "cash starts at $capital, so a limit of ${f.threshold} loses the game at once")
                    note(out, "fail.not_in_def", p, "a cash limit is checked by the workshop only; the built-in scenario rules do not know it")
                }
                FailKind.FACILITIES_BELOW -> {
                    if (f.threshold > d.company.starters.size && f.afterDay == 0) err(out, "fail.instant", "$p.threshold", "the company starts with ${d.company.starters.size} facilities, so a limit of ${f.threshold} loses the game at once")
                    if (f.threshold <= 0L) err(out, "fail.threshold", "$p.threshold", "the facility limit must be positive")
                    note(out, "fail.not_in_def", p, "a facility limit is checked by the workshop only; the built-in scenario rules do not know it")
                }
            }
            for (j in 0 until i) if (d.fails[j] == f) { warn(out, "fail.duplicate", p, "the same condition is already listed as failure ${j + 1}"); break }
        }
    }

    private fun extras(d: ScenarioDraft, out: MutableList<DraftIssue>) {
        if (d.tags.size > DraftLimits.MAX_TAGS) err(out, "tags.count", "tags", "${d.tags.size} tags; the most is ${DraftLimits.MAX_TAGS}")
        if (d.tags.isEmpty()) note(out, "tags.none", "tags", "no difficulty tag; players cannot filter for it")
        if (DifficultyTag.GENTLE in d.tags && DifficultyTag.HARSH in d.tags) warn(out, "tags.conflict", "tags", "gentle and harsh contradict each other")
        if (DifficultyTag.SPRINT in d.tags && d.timing.years > 3) warn(out, "tags.sprint", "tags", "a sprint with ${d.timing.years} years to play is not short")
        if (DifficultyTag.MARATHON in d.tags && d.timing.years < 5) warn(out, "tags.marathon", "tags", "a marathon of ${d.timing.years} years is not long")
        if (d.hints.size > DraftLimits.MAX_HINTS) err(out, "hints.count", "hints", "${d.hints.size} hints; the most is ${DraftLimits.MAX_HINTS}")
        val seen = HashSet<String>()
        for ((i, h) in d.hints.withIndex()) {
            val p = "hints[$i]"
            if (h.isBlank()) err(out, "hint.empty", p, "a hint cannot be blank")
            if (h.length > DraftLimits.MAX_HINT) err(out, "hint.length", p, "the hint has ${h.length} characters; the most is ${DraftLimits.MAX_HINT}")
            if (h.isNotBlank() && !seen.add(h.trim().lowercase())) warn(out, "hint.duplicate", p, "this hint repeats an earlier one")
        }
        if (d.packs.size > DraftLimits.MAX_PACKS) err(out, "packs.count", "packs", "${d.packs.size} packs; the most is ${DraftLimits.MAX_PACKS}")
        for ((i, pack) in d.packs.withIndex()) {
            if (!pack.matches(PACK_FORMAT)) err(out, "pack.format", "packs[$i]", "'$pack' should look like 'base:1.0'")
        }
    }

    internal val PACK_FORMAT = Regex("[a-z0-9_-]+:\\d+\\.\\d+")
}
