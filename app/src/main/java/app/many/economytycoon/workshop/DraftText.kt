package app.many.economytycoon.workshop

import app.many.economytycoon.scenarios.GoalKind
import app.many.economytycoon.world.WorldSize

/**
 * The readable form of a draft, for writing by hand and for sharing in a message. A scenario is a block of one-line
 * statements:
 *
 *     scenario first-light {
 *       title "First Light"
 *       world small rivals 3 country 0
 *       company "Founders Ltd" capital 500_000
 *       goal net_worth 1_000_000 weight 3
 *       event 30 grant 20_000 "Seed money"
 *     }
 *
 * The printer writes the canonical layout; the parser reads that and anything close to it.
 */
object DraftPrinter {
    fun print(draft: ScenarioDraft): String {
        val d = draft.normalised()
        val sb = StringBuilder()
        sb.append("scenario ").append(d.id).append(" {\n")
        line(sb, "title ${quote(d.title)}")
        if (d.tagline.isNotEmpty()) line(sb, "tagline ${quote(d.tagline)}")
        if (d.story.isNotEmpty()) line(sb, "story ${quote(d.story)}")
        line(sb, "seed ${num(d.seed)}")
        line(sb, "world ${d.world.size.name.lowercase()} rivals ${d.world.rivals} country ${d.world.countryIndex}")
        line(sb, "company ${quote(d.company.name)} capital ${num(d.company.capital)}")
        for (s in d.company.starters) line(sb, "starter ${s.recipe} region ${s.regionIndex}")
        for ((k, v) in d.rules.entries()) line(sb, "rule ${k.key} ${num(v)}")
        line(sb, "timing years ${d.timing.years} gold ${d.timing.goldDays} silver ${d.timing.silverDays}")
        if (d.tags.isNotEmpty()) line(sb, "tags " + d.tags.joinToString(" ") { it.key })
        for (p in d.packs) line(sb, "pack $p")
        for (g in d.goals) line(sb, "goal ${if (g.bonus) "bonus " else ""}${g.kind.name.lowercase()} ${num(g.target)}" + if (g.weight != 1) " weight ${g.weight}" else "")
        for (f in d.fails) line(sb, "fail ${f.kind.key} ${num(f.threshold)}" + if (f.afterDay != 0) " after ${f.afterDay}" else "")
        for (e in d.events) {
            val body = if (e.kind.hasAmount) "${num(e.amount)}" + (if (e.text.isNotEmpty()) " ${quote(e.text)}" else "") else quote(e.text)
            line(sb, "event ${e.day} ${e.kind.key} $body")
        }
        for (h in d.hints) line(sb, "hint ${quote(h)}")
        sb.append("}\n")
        return sb.toString()
    }

    private fun line(sb: StringBuilder, text: String) { sb.append("  ").append(text).append('\n') }

    /** Numbers of five digits or more get underscores between thousands, as they are easier to read. */
    internal fun num(v: Long): String {
        val digits = Math.abs(v).toString()
        if (digits.length < 5) return v.toString()
        val grouped = digits.reversed().chunked(3).joinToString("_").reversed()
        return if (v < 0) "-$grouped" else grouped
    }

    internal fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) when {
            ch == '"' -> sb.append("\\\"")
            ch == '\\' -> sb.append("\\\\")
            ch == '\n' -> sb.append("\\n")
            ch == '\t' -> sb.append("\\t")
            ch.code < 0x20 || ch.code == 0x7F -> sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            else -> sb.append(ch)
        }
        return sb.append('"').toString()
    }
}

enum class ParseSeverity { ERROR, WARNING }

data class ParseIssue(val at: TextPosition, val severity: ParseSeverity, val message: String) {
    override fun toString() = "$at: $message"
}

/** Where each field of a parsed draft was written, by the same paths the validator uses. */
class SourceMap(private val positions: Map<String, TextPosition>) {
    /** The place of [path], or of the nearest enclosing field that has one. */
    fun locate(path: String): TextPosition? {
        var p = path
        while (true) {
            positions[p]?.let { return it }
            val cut = maxOf(p.lastIndexOf('.'), p.lastIndexOf('['))
            if (cut < 0) return null
            p = p.substring(0, cut)
        }
    }

    val size: Int get() = positions.size
}

class ParseResult(val draft: ScenarioDraft?, val issues: List<ParseIssue>, val sourceMap: SourceMap) {
    val errors: List<ParseIssue> get() = issues.filter { it.severity == ParseSeverity.ERROR }
    val warnings: List<ParseIssue> get() = issues.filter { it.severity == ParseSeverity.WARNING }
    val ok: Boolean get() = draft != null && errors.isEmpty()
}

/** A validation finding with the place in the text it points at, when the draft came from text. */
data class LocatedIssue(val issue: DraftIssue, val at: TextPosition?) {
    override fun toString() = if (at == null) issue.toString() else "$at: ${issue.message} [${issue.path}]"
}

object DraftParser {
    private const val MAX_TEXT = 200_000

    fun parse(text: String): ParseResult = Parser(text).run()

    /** Attaches text positions to the findings of the validator. */
    fun locate(report: ValidationReport, map: SourceMap): List<LocatedIssue> = report.issues.map { LocatedIssue(it, map.locate(it.path)) }

    private class Tok(val text: String, val quoted: Boolean, val at: TextPosition, val end: Int, val after: TextPosition)

    private class Parser(val src: String) {
        val issues = ArrayList<ParseIssue>()
        val where = LinkedHashMap<String, TextPosition>()
        val lines = ArrayList<List<Tok>>()

        fun run(): ParseResult {
            if (src.length > MAX_TEXT) {
                issues += ParseIssue(TextPosition(1, 1), ParseSeverity.ERROR, "the text is longer than $MAX_TEXT characters")
                return ParseResult(null, issues, SourceMap(where))
            }
            lex()
            val draft = block()
            return ParseResult(draft, issues, SourceMap(where))
        }

        fun error(at: TextPosition, message: String) { issues += ParseIssue(at, ParseSeverity.ERROR, message) }
        fun warn(at: TextPosition, message: String) { issues += ParseIssue(at, ParseSeverity.WARNING, message) }

        // ---- lexing: one list of tokens per line, braces split off ---------------------------------

        fun lex() {
            var line = 1
            var col = 1
            var i = 0
            var current = ArrayList<Tok>()
            fun endLine() { lines.add(current); current = ArrayList(); line++; col = 1 }
            while (i < src.length) {
                val ch = src[i]
                when {
                    ch == '\n' -> { endLine(); i++ }
                    ch == '\r' || ch == ' ' || ch == '\t' || ch == ' ' -> { i++; col++ }
                    ch == '#' -> { while (i < src.length && src[i] != '\n') { i++; col++ } }
                    ch == '/' && i + 1 < src.length && src[i + 1] == '/' -> { while (i < src.length && src[i] != '\n') { i++; col++ } }
                    ch == '{' || ch == '}' -> { current.add(Tok(ch.toString(), false, TextPosition(line, col), i + 1, TextPosition(line, col + 1))); i++; col++ }
                    ch == '"' || ch == '“' -> {
                        val at = TextPosition(line, col)
                        val closer = if (ch == '"') '"' else '”'
                        val sb = StringBuilder()
                        i++; col++
                        var closed = false
                        while (i < src.length) {
                            val c = src[i]
                            if (c == '\n') break
                            if (c == closer || (c == '"' && closer == '”')) { closed = true; i++; col++; break }
                            if (c == '\\' && i + 1 < src.length && src[i + 1] != '\n') {
                                val n = src[i + 1]
                                val escapeAt = TextPosition(line, col)
                                when (n) {
                                    '"' -> { sb.append('"'); i += 2; col += 2 }
                                    '\\' -> { sb.append('\\'); i += 2; col += 2 }
                                    'n' -> { sb.append('\n'); i += 2; col += 2 }
                                    't' -> { sb.append('\t'); i += 2; col += 2 }
                                    'u' -> {
                                        val hex = src.substring(i + 2, minOf(i + 6, src.length))
                                        val code = if (hex.length == 4) hex.toIntOrNull(16) else null
                                        if (code == null) { warn(escapeAt, "'\\u' needs four hex digits; kept as written"); sb.append("\\u"); i += 2; col += 2 }
                                        else { sb.append(code.toChar()); i += 6; col += 6 }
                                    }
                                    else -> { warn(escapeAt, "unknown escape '\\$n'; kept as written"); sb.append('\\').append(n); i += 2; col += 2 }
                                }
                                continue
                            }
                            sb.append(c); i++; col++
                        }
                        if (!closed) error(at, "this text has no closing quote on its line")
                        current.add(Tok(sb.toString(), true, at, i, TextPosition(line, col)))
                    }
                    else -> {
                        val at = TextPosition(line, col)
                        val start = i
                        while (i < src.length && src[i] !in " \t\r\n{}\"# ") { i++; col++ }
                        current.add(Tok(src.substring(start, i), false, at, i, TextPosition(line, col)))
                    }
                }
            }
            lines.add(current)
        }

        // ---- the block -----------------------------------------------------------------------------

        private var d = DraftDefaults.blank(id = "", title = "").copy(goals = emptyList(), tags = emptyList(), company = CompanySetup("", 0L, emptyList()), timing = Timing(0, 0, 0), world = WorldSettings(WorldSize.SMALL, 0, 0))
        private val seen = HashSet<String>()

        fun block(): ScenarioDraft? {
            val flat = lines.flatMapIndexed { _, l -> l.map { it } }
            val head = flat.firstOrNull()
            if (head == null) { error(TextPosition(1, 1), "the text is empty; expected 'scenario <id> {'"); return null }
            if (head.text.lowercase() != "scenario" || head.quoted) { error(head.at, "expected 'scenario <id> {' but found '${head.text}'"); return null }
            val idTok = flat.getOrNull(1)
            if (idTok == null || idTok.text == "{") { error(idTok?.at ?: head.at, "the scenario needs an id after 'scenario'"); return null }
            where["id"] = idTok.at
            d = d.copy(id = idTok.text)
            var idx = 2
            val open = flat.getOrNull(idx)
            if (open == null || open.text != "{" || open.quoted) {
                error(open?.at ?: idTok.at, "expected '{' after the scenario id")
                if (open != null && open.at.line == idTok.at.line) idx++ // keep going from the next line
            } else idx++
            var closed = false
            var lastAt = idTok.at
            // Group the remaining tokens by line again.
            val rest = flat.drop(idx).groupBy { it.at.line }.toSortedMap()
            for ((_, toks) in rest) {
                if (closed) { error(toks.first().at, "nothing may follow the closing '}'"); break }
                val first = toks.first()
                lastAt = toks.last().at
                if (first.text == "}" && !first.quoted) {
                    closed = true
                    if (toks.size > 1) error(toks[1].at, "nothing may follow the closing '}' on its line")
                    continue
                }
                statement(toks)
            }
            if (!closed) error(lastAt, "the scenario block is not closed; expected '}'")
            if (d.title.isEmpty() && "title" !in seen) warn(head.at, "no title given")
            applyDefaults()
            return d.normalised()
        }

        /** Parts the text left out take the values a new draft has. */
        fun applyDefaults() {
            val base = DraftDefaults.blank()
            if ("company" !in seen) d = d.copy(company = d.company.copy(name = base.company.name, capital = base.company.capital))
            if ("world" !in seen) d = d.copy(world = base.world)
            if ("timing" !in seen) d = d.copy(timing = base.timing)
            else {
                val t = d.timing
                d = d.copy(timing = Timing(if (t.years == 0) base.timing.years else t.years, if (t.goldDays == 0) base.timing.goldDays else t.goldDays, if (t.silverDays == 0) base.timing.silverDays else t.silverDays))
            }
        }

        // ---- statements ----------------------------------------------------------------------------

        private val keywords = listOf("title", "tagline", "story", "seed", "world", "company", "starter", "rule", "timing", "tags", "pack", "goal", "fail", "event", "hint")

        fun statement(toks: List<Tok>) {
            val key = toks[0]
            val word = key.text.trimEnd(':', '=').lowercase()
            val args = Args(toks.drop(1).filter { it.text != "=" && it.text != ":" || it.quoted }, key.at, key.after, this)
            if (key.quoted || word !in keywords) {
                val near = nearest(keywords, word, 2)
                error(key.at, "unknown statement '${key.text}'" + (near?.let { "; did you mean '$it'?" } ?: ""))
                return
            }
            if (word in SINGLE && !seen.add(word)) warn(key.at, "'$word' is given more than once; the last one counts")
            when (word) {
                "title" -> args.text("a title in quotes")?.let { d = d.copy(title = it.text); where["title"] = it.at }
                "tagline" -> args.text("a tagline in quotes")?.let { d = d.copy(tagline = it.text); where["tagline"] = it.at }
                "story" -> args.text("a story in quotes")?.let { d = d.copy(story = it.text); where["story"] = it.at }
                "seed" -> args.number("a whole number")?.let { d = d.copy(seed = it.first); where["seed"] = it.second }
                "world" -> world(args)
                "company" -> company(args)
                "starter" -> starter(args)
                "rule" -> rule(args)
                "timing" -> timing(args)
                "tags" -> tags(args)
                "pack" -> args.word("a pack such as base:1.0")?.let { where["packs[${d.packs.size}]"] = it.at; d = d.copy(packs = d.packs + it.text) }
                "goal" -> goal(args)
                "fail" -> fail(args)
                "event" -> event(args)
                "hint" -> args.text("a hint in quotes")?.let { where["hints[${d.hints.size}]"] = it.at; d = d.copy(hints = d.hints + it.text) }
            }
            args.finish()
        }

        fun world(a: Args) {
            where["world"] = a.start
            val sizeTok = a.word("a world size (${WorldSize.values().joinToString("/") { it.name.lowercase() }})") ?: return
            val size = WorldSize.values().firstOrNull { it.name.equals(sizeTok.text, ignoreCase = true) }
            if (size == null) { error(sizeTok.at, "'${sizeTok.text}' is not a world size; use ${WorldSize.values().joinToString(", ") { it.name.lowercase() }}"); }
            where["world.size"] = sizeTok.at
            var w = d.world.copy(size = size ?: d.world.size)
            for ((name, tok, value) in a.options(listOf("rivals", "country"))) {
                when (name) {
                    "rivals" -> { w = w.copy(rivals = toInt(value, tok) ?: w.rivals); where["world.rivals"] = tok.at }
                    "country" -> { w = w.copy(countryIndex = toInt(value, tok) ?: w.countryIndex); where["world.country"] = tok.at }
                }
            }
            d = d.copy(world = w)
        }

        fun company(a: Args) {
            where["company"] = a.start
            val name = a.text("the company name in quotes") ?: return
            where["company.name"] = name.at
            var c = d.company.copy(name = name.text)
            for ((opt, tok, value) in a.options(listOf("capital"))) if (opt == "capital") { c = c.copy(capital = value ?: c.capital); where["company.capital"] = tok.at }
            d = d.copy(company = c)
        }

        fun starter(a: Args) {
            val i = d.company.starters.size
            where["company.starters[$i]"] = a.start
            val recipe = a.word("a recipe key") ?: return
            where["company.starters[$i].recipe"] = recipe.at
            var region = 0
            for ((opt, tok, value) in a.options(listOf("region"))) if (opt == "region") { region = toInt(value, tok) ?: region; where["company.starters[$i].region"] = tok.at }
            d = d.copy(company = d.company.copy(starters = d.company.starters + StarterSite(recipe.text, region)))
        }

        fun rule(a: Args) {
            val k = a.word("a rule name") ?: return
            val key = RuleKey.byKey(k.text)
            if (key == null) {
                val near = nearest(RuleKey.values().map { it.key }, k.text.lowercase(), 3)
                error(k.at, "unknown rule '${k.text}'" + (near?.let { "; did you mean '$it'?" } ?: "; known rules: ${RuleKey.values().joinToString(", ") { it.key }}"))
                a.skip(); return
            }
            val v = a.number("a whole number") ?: return
            where["rules.${key.key}"] = k.at
            d = d.copy(rules = d.rules.with(key, v.first))
        }

        fun timing(a: Args) {
            where["timing"] = a.start
            var t = d.timing
            for ((opt, tok, value) in a.options(listOf("years", "gold", "silver"))) when (opt) {
                "years" -> { t = t.copy(years = toInt(value, tok) ?: t.years); where["timing.years"] = tok.at }
                "gold" -> { t = t.copy(goldDays = toInt(value, tok) ?: t.goldDays); where["timing.gold"] = tok.at }
                "silver" -> { t = t.copy(silverDays = toInt(value, tok) ?: t.silverDays); where["timing.silver"] = tok.at }
            }
            d = d.copy(timing = t)
        }

        fun tags(a: Args) {
            var any = false
            while (true) {
                val t = a.nextOrNull() ?: break
                any = true
                val tag = DifficultyTag.byKey(t.text)
                if (tag == null) {
                    val near = nearest(DifficultyTag.values().map { it.key }, t.text.lowercase(), 2)
                    error(t.at, "unknown tag '${t.text}'" + (near?.let { "; did you mean '$it'?" } ?: ""))
                } else { where["tags"] = where["tags"] ?: t.at; d = d.copy(tags = d.tags + tag) }
            }
            if (!any) error(a.start, "'tags' needs at least one tag")
        }

        fun goal(a: Args) {
            val i = d.goals.size
            where["goals[$i]"] = a.start
            var bonus = false
            var kindTok = a.word("a goal kind") ?: return
            if (kindTok.text.equals("bonus", ignoreCase = true)) { bonus = true; kindTok = a.word("a goal kind after 'bonus'") ?: return }
            val kind = GoalKind.values().firstOrNull { it.name.equals(kindTok.text, ignoreCase = true) }
            if (kind == null) {
                val near = nearest(GoalKind.values().map { it.name.lowercase() }, kindTok.text.lowercase(), 3)
                error(kindTok.at, "unknown goal kind '${kindTok.text}'" + (near?.let { "; did you mean '$it'?" } ?: ""))
                a.skip(); return
            }
            where["goals[$i].kind"] = kindTok.at
            val target = a.number("a target") ?: return
            where["goals[$i].target"] = target.second
            var weight = 1
            for ((opt, tok, value) in a.options(listOf("weight"), flags = listOf("bonus"))) when (opt) {
                "weight" -> { weight = toInt(value, tok) ?: weight; where["goals[$i].weight"] = tok.at }
                "bonus" -> bonus = true
            }
            d = d.copy(goals = d.goals + DraftGoal(kind, target.first, weight, bonus))
        }

        fun fail(a: Args) {
            val i = d.fails.size
            where["fails[$i]"] = a.start
            val kindTok = a.word("a failure kind (${FailKind.values().joinToString("/") { it.key }})") ?: return
            val kind = FailKind.byKey(kindTok.text)
            if (kind == null) { error(kindTok.at, "unknown failure kind '${kindTok.text}'; use ${FailKind.values().joinToString(", ") { it.key }}"); a.skip(); return }
            val threshold = a.number("a threshold") ?: return
            where["fails[$i].threshold"] = threshold.second
            var after = 0
            for ((opt, tok, value) in a.options(listOf("after"))) if (opt == "after") { after = toInt(value, tok) ?: after; where["fails[$i].after"] = tok.at }
            d = d.copy(fails = d.fails + FailCondition(kind, threshold.first, after))
        }

        fun event(a: Args) {
            val i = d.events.size
            where["events[$i]"] = a.start
            val day = a.number("a day") ?: return
            where["events[$i].day"] = day.second
            val kindTok = a.word("an event kind (${EventKind.values().joinToString("/") { it.key }})") ?: return
            val kind = EventKind.byKey(kindTok.text)
            if (kind == null) { error(kindTok.at, "unknown event kind '${kindTok.text}'; use ${EventKind.values().joinToString(", ") { it.key }}"); a.skip(); return }
            var amount = 0L
            if (kind.hasAmount) {
                val n = a.number("an amount") ?: return
                amount = n.first; where["events[$i].amount"] = n.second
            }
            val text = if (kind.hasAmount) a.optionalText() else a.text("the message in quotes")
            if (text != null) where["events[$i].text"] = text.at
            val dayInt = toInt(day.first, kindTok) ?: return
            d = d.copy(events = d.events + ScriptedEvent(dayInt, kind, amount, text?.text.orEmpty()))
        }

        fun toInt(v: Long?, at: Tok): Int? {
            if (v == null) return null
            if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) { error(at.at, "$v is too large here"); return null }
            return v.toInt()
        }

        /** The candidate closest to [word] by edit distance, also counting how well [word] matches the start of a long candidate. */
        fun nearest(candidates: List<String>, word: String, max: Int): String? {
            if (word.isEmpty()) return null
            fun score(c: String) = minOf(distance(c, word), distance(c.take(word.length), word))
            val best = candidates.minByOrNull { score(it) } ?: return null
            return best.takeIf { score(it) <= max }
        }

        fun distance(a: String, b: String): Int {
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = dp[0]; dp[0] = i
                for (j in 1..b.length) {
                    val tmp = dp[j]
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return dp[b.length]
        }

        private companion object { val SINGLE = setOf("title", "tagline", "story", "seed", "world", "company", "timing") }
    }

    /** The tokens of one statement after its keyword, read from the front. */
    private class Args(toks: List<Tok>, val start: TextPosition, keyAfter: TextPosition, val p: Parser) {
        private val list = toks
        /** Where something missing from the statement should have been: just after its last token. */
        private val missingAt: TextPosition = toks.lastOrNull()?.after ?: keyAfter
        private var pos = 0
        private var failed = false

        fun nextOrNull(): Tok? = list.getOrNull(pos)?.also { pos++ }

        fun skip() { pos = list.size; failed = true }

        fun text(what: String): Tok? {
            val t = list.getOrNull(pos)
            if (t == null) { p.error(missingAt, "expected $what"); failed = true; return null }
            pos++
            if (!t.quoted && t.text.contains('"')) { p.error(t.at, "expected $what"); return null }
            return t
        }

        fun optionalText(): Tok? = list.getOrNull(pos)?.takeIf { it.quoted }?.also { pos++ }

        fun word(what: String): Tok? {
            val t = list.getOrNull(pos)
            if (t == null) { p.error(missingAt, "expected $what"); failed = true; return null }
            if (t.quoted) { p.error(t.at, "expected $what, found a quoted text"); failed = true; return null }
            pos++
            return t
        }

        fun number(what: String): Pair<Long, TextPosition>? {
            val t = list.getOrNull(pos)
            if (t == null) { p.error(missingAt, "expected $what"); failed = true; return null }
            pos++
            val n = parseNumber(t)
            if (n == null) { p.error(t.at, "expected $what but found '${t.text}'"); failed = true; return null }
            return n to t.at
        }

        /** `name value` pairs and bare flags that may come in any order. A repeated name is a warning. */
        fun options(names: List<String>, flags: List<String> = emptyList()): List<Triple<String, Tok, Long?>> {
            val out = ArrayList<Triple<String, Tok, Long?>>()
            val used = HashSet<String>()
            while (pos < list.size) {
                val t = list[pos]
                val n = t.text.lowercase()
                if (!t.quoted && n in flags) { pos++; out += Triple(n, t, null); continue }
                if (t.quoted || n !in names) {
                    val near = p.nearest(names + flags, n, 2)
                    p.error(t.at, "unexpected '${t.text}'" + (near?.let { "; did you mean '$it'?" } ?: "; expected ${(names + flags).joinToString(" or ")}"))
                    pos++; failed = true
                    if (list.getOrNull(pos)?.let { parseNumber(it) } != null) pos++
                    continue
                }
                pos++
                val v = list.getOrNull(pos)
                if (v == null) { p.error(t.after, "'${t.text}' needs a number"); failed = true; break }
                val num = parseNumber(v)
                pos++
                if (num == null) { p.error(v.at, "'${t.text}' needs a number but found '${v.text}'"); failed = true; continue }
                if (!used.add(n)) p.warn(t.at, "'${t.text}' is given twice; the last one counts")
                out += Triple(n, Tok(t.text, false, v.at, v.end, v.after), num)
            }
            return out
        }

        fun finish() {
            if (!failed && pos < list.size) {
                val extra = list[pos]
                p.error(extra.at, "unexpected '${extra.text}' at the end of the statement")
            }
        }

        private fun parseNumber(t: Tok): Long? {
            if (t.quoted) return null
            val s = t.text
            if (!s.matches(Regex("-?[0-9][0-9_]*"))) return null
            return s.replace("_", "").toLongOrNull()
        }
    }
}
