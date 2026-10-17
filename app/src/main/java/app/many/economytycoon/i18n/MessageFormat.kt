package app.many.economytycoon.i18n

/**
 * A small subset of the ICU message syntax, enough for game text:
 *
 *  - `{name}` inserts an argument,
 *  - `{n, plural, one {# day} other {# days}}` picks a form by the number `n`, with `#` standing for it,
 *  - `{kind, select, a {...} b {...} other {...}}` picks a form by a word,
 *  - `'{'` and `'}'` write literal braces, and `''` a literal quote.
 *
 * A message that cannot be parsed shows as written rather than failing, since text must never crash a screen.
 */
object MessageFormat {
    class ParseException(message: String) : IllegalArgumentException(message)

    fun format(pattern: String, locale: GameLocale, args: Map<String, Any?>): String =
        try {
            render(parse(pattern), locale, args, null)
        } catch (e: ParseException) {
            pattern
        }

    /** True when [pattern] is well formed. */
    fun isValid(pattern: String): Boolean = try { parse(pattern); true } catch (e: ParseException) { false }

    /** Names of every argument a pattern uses, in first-use order. */
    fun argumentNames(pattern: String): List<String> {
        val out = LinkedHashSet<String>()
        fun walk(nodes: List<Node>) {
            for (n in nodes) when (n) {
                is Node.Arg -> out += n.name
                is Node.Choice -> { out += n.name; n.forms.values.forEach { walk(it) } }
                else -> {}
            }
        }
        walk(try { parse(pattern) } catch (e: ParseException) { emptyList() })
        return out.toList()
    }

    sealed class Node {
        data class Text(val text: String) : Node()
        data class Arg(val name: String) : Node()
        object Hash : Node()
        data class Choice(val name: String, val plural: Boolean, val forms: Map<String, List<Node>>) : Node()
    }

    internal fun parse(pattern: String): List<Node> {
        val p = Parser(pattern)
        val nodes = p.nodes(topLevel = true, inPlural = false)
        if (p.pos < pattern.length) throw ParseException("unexpected '}' at ${p.pos}")
        return nodes
    }

    private class Parser(val s: String) {
        var pos = 0

        fun nodes(topLevel: Boolean, inPlural: Boolean): List<Node> {
            val out = ArrayList<Node>()
            val text = StringBuilder()
            fun flush() { if (text.isNotEmpty()) { out += Node.Text(text.toString()); text.setLength(0) } }
            while (pos < s.length) {
                val c = s[pos]
                when {
                    c == '\'' -> {
                        if (pos + 1 < s.length && s[pos + 1] == '\'') { text.append('\''); pos += 2 }
                        else if (pos + 1 < s.length && (s[pos + 1] == '{' || s[pos + 1] == '}')) {
                            // A quoted run: everything up to the closing quote is literal.
                            val end = s.indexOf('\'', pos + 1).let { if (it < 0) s.length else it }
                            text.append(s, pos + 1, end)
                            pos = minOf(end + 1, s.length)
                        }
                        else { text.append(c); pos++ }
                    }
                    c == '{' -> { flush(); out += argument() }
                    c == '}' -> { if (topLevel) throw ParseException("unmatched '}' at $pos"); flush(); return out }
                    c == '#' && inPlural -> { flush(); out += Node.Hash; pos++ }
                    else -> { text.append(c); pos++ }
                }
            }
            if (!topLevel) throw ParseException("unterminated '{'")
            flush()
            return out
        }

        private fun argument(): Node {
            pos++ // {
            val name = word()
            if (name.isEmpty()) throw ParseException("empty argument name")
            skip()
            if (peek() == '}') { pos++; return Node.Arg(name) }
            if (peek() != ',') throw ParseException("expected ',' or '}' after $name")
            pos++
            skip()
            val kind = word()
            val plural = when (kind) { "plural" -> true; "select" -> false; else -> throw ParseException("unknown argument type '$kind'") }
            skip()
            if (peek() != ',') throw ParseException("expected ',' after $kind")
            pos++
            val forms = LinkedHashMap<String, List<Node>>()
            while (true) {
                skip()
                if (peek() == '}') { pos++; break }
                val key = word()
                if (key.isEmpty()) throw ParseException("expected a form name")
                skip()
                if (peek() != '{') throw ParseException("expected '{' after form $key")
                pos++
                forms[key] = nodes(topLevel = false, inPlural = plural)
                if (peek() != '}') throw ParseException("unterminated form $key")
                pos++
            }
            if ("other" !in forms) throw ParseException("missing 'other' form for $name")
            return Node.Choice(name, plural, forms)
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null
        private fun skip() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        private fun word(): String {
            val start = pos
            while (pos < s.length && (s[pos].isLetterOrDigit() || s[pos] == '_' || s[pos] == '=' )) pos++
            return s.substring(start, pos)
        }
    }

    private fun render(nodes: List<Node>, locale: GameLocale, args: Map<String, Any?>, number: Long?): String {
        val sb = StringBuilder()
        for (n in nodes) when (n) {
            is Node.Text -> sb.append(n.text)
            is Node.Arg -> sb.append(args[n.name]?.toString() ?: "{${n.name}}")
            is Node.Hash -> sb.append(number?.let { LocaleFormat(locale).grouped(it) } ?: "#")
            is Node.Choice -> {
                val v = args[n.name]
                if (n.plural) {
                    val num = (v as? Number)?.toLong()
                    val form = if (num == null) n.forms.getValue("other") else pick(n, num, locale)
                    sb.append(render(form, locale, args, num))
                } else {
                    val form = n.forms[v?.toString()] ?: n.forms.getValue("other")
                    sb.append(render(form, locale, args, number))
                }
            }
        }
        return sb.toString()
    }

    private fun pick(c: Node.Choice, n: Long, locale: GameLocale): List<Node> {
        c.forms["=$n"]?.let { return it }
        val key = when (locale.plural(n)) { PluralCategory.ZERO -> "zero"; PluralCategory.ONE -> "one"; PluralCategory.OTHER -> "other" }
        return c.forms[key] ?: c.forms.getValue("other")
    }
}
