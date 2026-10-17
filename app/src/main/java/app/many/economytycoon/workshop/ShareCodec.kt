package app.many.economytycoon.workshop

import app.many.economytycoon.core.ByteReader
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Where in a pasted text a problem was found. Lines and columns count from one. */
data class TextPosition(val line: Int, val column: Int) {
    override fun toString() = "line $line, column $column"
}

data class ShareError(val at: TextPosition, val message: String) {
    override fun toString() = "$at: $message"
}

sealed class ShareResult {
    data class Decoded(val draft: ScenarioDraft, val warnings: List<String>) : ShareResult()
    data class Failed(val error: ShareError) : ShareResult()

    fun draftOrNull(): ScenarioDraft? = (this as? Decoded)?.draft
}

/**
 * The text a draft is shared as: `ETW1-` and then the draft, compressed when that helps, with a checksum, written in
 * Crockford base32 in groups of five and lines of eight groups. Every line ends with its own check symbol, so a
 * typo can be pointed at by line. Reading is forgiving: case, spacing, dashes, look-alike letters, quotes and text
 * around the code do not matter, and a missing line check is not an error as long as the whole checksum holds.
 */
object ShareCodec {
    const val VERSION = 1
    const val PREFIX = "ETW"
    const val GROUPS_PER_LINE = 8
    const val GROUP = 5
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val MAX_RAW = 64 * 1024
    private const val RAW = 0
    private const val DEFLATED = 1

    fun encode(draft: ScenarioDraft): String {
        val raw = DraftCodec.toBytes(draft)
        val packed = deflate(raw)
        val flagged = if (packed.size < raw.size) byteArrayOf(DEFLATED.toByte()) + packed else byteArrayOf(RAW.toByte()) + raw
        val crc = crc(flagged)
        val all = flagged + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
        return format(symbols(all))
    }

    private fun format(symbols: String): String {
        val perLine = GROUP * GROUPS_PER_LINE
        val lines = symbols.chunked(perLine)
        return lines.withIndex().joinToString("\n") { (i, line) ->
            val body = line.chunked(GROUP).joinToString("-")
            val head = if (i == 0) "$PREFIX$VERSION-" else ""
            "$head$body:${lineCheck(line)}"
        }
    }

    private fun lineCheck(line: String): Char {
        var sum = 0
        for ((i, ch) in line.withIndex()) sum += (ALPHABET.indexOf(ch) + 1) * (i + 1)
        return ALPHABET[sum % 32]
    }

    private fun symbols(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) { sb.append(ALPHABET[(buffer ushr (bits - 5)) and 31]); bits -= 5 }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun crc(bytes: ByteArray): Long = CRC32().also { it.update(bytes) }.value

    private fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            d.setInput(raw); d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(512)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            return out.toByteArray()
        } finally { d.end() }
    }

    private fun inflate(packed: ByteArray): ByteArray? {
        val inf = Inflater(true)
        try {
            inf.setInput(packed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) return null
                out.write(buf, 0, n)
                if (out.size() > MAX_RAW) return null
            }
            return out.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            return null
        } finally { inf.end() }
    }

    private fun symbolValue(ch: Char): Int? {
        val c = when (ch.uppercaseChar()) { 'O' -> '0'; 'I', 'L' -> '1'; 'U' -> 'V'; else -> ch.uppercaseChar() }
        return ALPHABET.indexOf(c).takeIf { it >= 0 }
    }

    fun decode(text: String): ShareResult {
        // Find the marker; whatever comes before it is somebody's message.
        var start = -1
        var i = 0
        while (i + PREFIX.length < text.length) {
            if (text.regionMatches(i, PREFIX, 0, PREFIX.length, ignoreCase = true) && text[i + PREFIX.length].isDigit()) { start = i; break }
            i++
        }
        if (start < 0) return fail(text, 0, "this is not a shared scenario; it should start with ETW$VERSION-")
        val versionAt = start + PREFIX.length
        // The version is a single digit so that data may follow it directly when the dash was lost.
        val versionEnd = versionAt + 1
        val version = text[versionAt].digitToInt()
        if (version != VERSION) return fail(text, versionAt, if (version > VERSION) "this scenario was shared by a newer version of the game (code version $version)" else "unsupported share code version $version")

        val values = ArrayList<Int>()
        val warnings = ArrayList<String>()
        var pos = versionEnd
        var lineValues = ArrayList<Int>()
        var lineChecked = false
        var unchecked = 0
        var lineNo = lineOf(text, pos)
        var sawData = false

        fun closeLine() {
            if (lineValues.isNotEmpty()) { values.addAll(lineValues); sawData = true; if (!lineChecked) unchecked++ }
            lineValues = ArrayList()
            lineChecked = false
        }

        while (pos < text.length) {
            val ch = text[pos]
            when {
                ch == '\n' -> {
                    closeLine()
                    lineNo++
                    // A blank line ends the code; anything after it is somebody's message.
                    var look = pos + 1
                    while (look < text.length && (text[look] == ' ' || text[look] == '\t' || text[look] == '\r')) look++
                    if (look >= text.length || text[look] == '\n') { pos = text.length; continue }
                }
                ch == '\r' || ch == ' ' || ch == '\t' || ch == '-' || ch == '"' || ch == '\'' || ch == '`' || ch == '_' -> Unit
                ch == ':' -> {
                    val checkAt = nextSymbolPos(text, pos + 1)
                    val expected = lineCheck(lineValues.joinToString("") { ALPHABET[it].toString() })
                    if (checkAt < 0) return fail(text, pos, "the check symbol after ':' is missing")
                    val got = symbolValue(text[checkAt])?.let { ALPHABET[it] }
                    if (got != expected) return fail(text, checkAt, "line $lineNo does not match its check symbol; a character on it was mistyped, lost or added")
                    lineChecked = true
                    pos = checkAt
                }
                else -> {
                    val v = symbolValue(ch) ?: return fail(text, pos, "'$ch' cannot be part of a share code")
                    lineValues.add(v)
                }
            }
            pos++
        }
        closeLine()
        if (!sawData) return fail(text, text.length.coerceAtMost(versionEnd), "there is no data after the version")
        if (values.size * 5 / 8 < 6) return fail(text, text.length, "the code is too short to hold a scenario")
        val bytes = toBytes(values) ?: return fail(text, text.length, "the end of the code is damaged")
        if (unchecked > 0) warnings += "$unchecked line${if (unchecked == 1) " has" else "s have"} no check symbol, so only the final checksum vouches for ${if (unchecked == 1) "it" else "them"}"
        val body = bytes.copyOfRange(0, bytes.size - 4)
        val stored = bytes.copyOfRange(bytes.size - 4, bytes.size).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        if (stored != crc(body)) return fail(text, versionEnd, "the checksum does not match; the code was cut short or changed")
        val raw = when (body[0].toInt()) {
            RAW -> body.copyOfRange(1, body.size)
            DEFLATED -> inflate(body.copyOfRange(1, body.size)) ?: return fail(text, versionEnd, "the compressed data is damaged")
            else -> return fail(text, versionEnd, "unknown packing ${body[0]}")
        }
        val draft = try { DraftCodec.fromBytes(raw) } catch (e: ByteReader.CorruptDataException) {
            return fail(text, versionEnd, "the scenario inside is damaged: ${e.message}")
        }
        return ShareResult.Decoded(draft, warnings)
    }

    private fun nextSymbolPos(text: String, from: Int): Int {
        var p = from
        while (p < text.length && (text[p] == ' ' || text[p] == '\t')) p++
        return if (p < text.length && text[p] != '\n' && text[p] != '\r') p else -1
    }

    private fun toBytes(values: List<Int>): ByteArray? {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (v in values) {
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) { out.write((buffer ushr (bits - 8)) and 0xFF); bits -= 8; buffer = buffer and ((1 shl bits) - 1) }
        }
        if (buffer != 0) return null
        return out.toByteArray()
    }

    private fun lineOf(text: String, index: Int): Int = 1 + text.take(index).count { it == '\n' }

    private fun fail(text: String, index: Int, message: String): ShareResult.Failed {
        val at = index.coerceIn(0, text.length)
        val before = text.substring(0, at)
        val line = 1 + before.count { it == '\n' }
        val column = at - (before.lastIndexOf('\n') + 1) + 1
        return ShareResult.Failed(ShareError(TextPosition(line, column), message))
    }
}
