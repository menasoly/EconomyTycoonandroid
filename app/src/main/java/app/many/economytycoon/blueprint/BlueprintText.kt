package app.many.economytycoon.blueprint

import java.util.zip.CRC32

enum class TextEncoding(val tag: String) { BASE32("B32"), BASE64("B64") }

enum class ParseErrorCode {
    EMPTY, NO_HEADER, UNSUPPORTED_VERSION, UNKNOWN_ENCODING, BAD_CHARACTER, BAD_LENGTH, MISSING_PAYLOAD_END,
    MISSING_CHECKSUM, BAD_CHECKSUM, CHECKSUM_MISMATCH, TOO_LARGE, CORRUPT,
}

/** A problem found while reading shared text, with where it was: [offset] counts characters from the start of the text. */
data class ParseError(val code: ParseErrorCode, val message: String, val offset: Int, val line: Int, val column: Int) {
    val where: String get() = "line $line, column $column"
}

sealed class ParseResult {
    abstract val start: Int
    abstract val end: Int

    /** [start] and [end] are the character range of the block in the text. */
    data class Success(val blueprint: Blueprint, val encoding: TextEncoding, val notes: List<String>, override val start: Int, override val end: Int) : ParseResult()
    data class Failure(val error: ParseError, override val start: Int, override val end: Int) : ParseResult()
}

/**
 * The copy-paste form of a blueprint:
 *
 *     ETBP1-B32:<payload>:<checksum>
 *
 * `ETBP1` is the text format version, `B32` or `B64` the payload encoding. The payload is the binary blueprint
 * ([BlueprintCodec]); the checksum is the CRC-32 of those bytes in seven base32 characters. The reader ignores
 * white space and line breaks, letter case (base32), the usual look-alike letters, and any text around a block.
 */
object BlueprintText {
    const val VERSION = 1
    const val MAX_PAYLOAD_BYTES = 16_384
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val B64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val header = Regex("ETBP(\\d+)-([A-Za-z0-9]+):", RegexOption.IGNORE_CASE)

    fun encode(bp: Blueprint, encoding: TextEncoding = TextEncoding.BASE32, wrap: Int = 0): String {
        val bytes = BlueprintCodec.encode(bp)
        val payload = when (encoding) {
            TextEncoding.BASE32 -> base32(bytes)
            TextEncoding.BASE64 -> base64(bytes)
        }
        val body = if (wrap > 0) payload.chunked(wrap).joinToString("\n") else payload
        val sep = if (wrap > 0) "\n" else ""
        return "ETBP$VERSION-${encoding.tag}:$sep$body$sep:${checksum(bytes)}"
    }

    /** Reads the first block in [text]. */
    fun parse(text: String): ParseResult = parseAll(text).firstOrNull()
        ?: ParseResult.Failure(error(text, ParseErrorCode.EMPTY.takeIf { text.isBlank() } ?: ParseErrorCode.NO_HEADER,
            if (text.isBlank()) "There is nothing to read." else "No blueprint code found. A code starts with ETBP1-B32:.", 0), 0, text.length)

    /** Reads every block in [text], in order. Text between blocks is ignored. */
    fun parseAll(text: String): List<ParseResult> {
        val out = ArrayList<ParseResult>()
        var from = 0
        while (true) {
            val m = header.find(text, from) ?: break
            val r = block(text, m)
            out += r
            from = maxOf(r.end, m.range.last + 1)
        }
        return out
    }

    private fun block(text: String, m: MatchResult): ParseResult {
        val start = m.range.first
        val version = m.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
        val tag = m.groupValues[2].uppercase()
        val bodyStart = m.range.last + 1
        if (version != VERSION) return fail(text, ParseErrorCode.UNSUPPORTED_VERSION, "This code is format version ${m.groupValues[1]}; this game reads version $VERSION.", start, start, bodyStart)
        val encoding = TextEncoding.values().firstOrNull { it.tag == tag }
            ?: return fail(text, ParseErrorCode.UNKNOWN_ENCODING, "Unknown encoding '${m.groupValues[2]}'; expected B32 or B64.", start + 6, start, bodyStart)

        val payload = StringBuilder()
        val payloadAt = ArrayList<Int>()
        var i = bodyStart
        var closed = false
        while (i < text.length) {
            val c = text[i]
            if (c == ':') { closed = true; i++; break }
            if (startsBlock(text, i)) return fail(text, ParseErrorCode.MISSING_PAYLOAD_END, "The code ends before the ':' that closes the data; another code starts here.", i, start, i)
            if (!ignorable(c, encoding)) {
                if (!validPayloadChar(c, encoding)) return fail(text, ParseErrorCode.BAD_CHARACTER, "'${shown(c)}' is not allowed in a ${encoding.tag} code.", i, start, i + 1)
                payload.append(c); payloadAt += i
            }
            i++
        }
        if (!closed) return fail(text, ParseErrorCode.MISSING_PAYLOAD_END, "The code ends before the ':' that closes the data.", text.length, start, text.length)
        if (payload.isEmpty()) return fail(text, ParseErrorCode.BAD_LENGTH, "The code carries no data.", bodyStart, start, i)

        val sum = StringBuilder()
        var sumAt = i
        while (i < text.length && sum.length < CHECKSUM_CHARS) {
            val c = text[i]
            if (startsBlock(text, i)) return fail(text, ParseErrorCode.MISSING_CHECKSUM, "The checksum is missing or too short (${sum.length} of $CHECKSUM_CHARS characters); another code starts here.", i, start, i)
            if (!c.isWhitespace() && !isInvisible(c)) {
                if (decode32(c) < 0) return fail(text, ParseErrorCode.BAD_CHECKSUM, "'${shown(c)}' is not allowed in a checksum.", i, start, i + 1)
                if (sum.isEmpty()) sumAt = i
                sum.append(c)
            }
            i++
        }
        if (sum.length < CHECKSUM_CHARS) return fail(text, ParseErrorCode.MISSING_CHECKSUM, "The checksum is missing or too short (${sum.length} of $CHECKSUM_CHARS characters).", text.length, start, text.length)
        val end = i

        val bytes = try {
            when (encoding) {
                TextEncoding.BASE32 -> unbase32(payload.toString())
                TextEncoding.BASE64 -> unbase64(payload.toString())
            }
        } catch (e: IllegalArgumentException) {
            val badAt = (e.message ?: "").substringAfter("at ", "").toIntOrNull()
            val where = badAt?.let { payloadAt.getOrNull(it) } ?: payloadAt.lastOrNull() ?: bodyStart
            return fail(text, ParseErrorCode.BAD_LENGTH, "The data is cut off or has extra characters (${e.message?.substringBefore(" at ")}).", where, start, end)
        }
        if (bytes.size > MAX_PAYLOAD_BYTES) return fail(text, ParseErrorCode.TOO_LARGE, "The blueprint is too large to be real (${bytes.size} bytes).", bodyStart, start, end)
        val expected = checksum(bytes)
        val given = canonical32(sum.toString())
        if (expected != given) {
            return fail(text, ParseErrorCode.CHECKSUM_MISMATCH, "The checksum does not match (expected $expected, found $given). Part of the code was probably lost or changed in copying.", sumAt, start, end)
        }
        return when (val d = BlueprintCodec.decode(bytes)) {
            is Decoded.Ok -> ParseResult.Success(d.blueprint, encoding, d.notes, start, end)
            is Decoded.Failed -> fail(text, ParseErrorCode.CORRUPT, d.message, bodyStart, start, end)
        }
    }

    private fun startsBlock(text: String, at: Int) = (text[at] == 'E' || text[at] == 'e') && header.matchAt(text, at) != null

    private fun fail(text: String, code: ParseErrorCode, message: String, at: Int, start: Int, end: Int): ParseResult.Failure =
        ParseResult.Failure(error(text, code, message, at), start, maxOf(end, start + 1))

    private fun error(text: String, code: ParseErrorCode, message: String, at: Int): ParseError {
        val offset = at.coerceIn(0, text.length)
        var line = 1
        var col = 1
        for (k in 0 until offset) { if (text[k] == '\n') { line++; col = 1 } else col++ }
        return ParseError(code, message, offset, line, col)
    }

    private fun shown(c: Char) = if (c.code < 32 || c.code > 126) "U+%04X".format(c.code) else c.toString()
    private fun isInvisible(c: Char) = c == ' ' || c == '​' || c == '﻿' || c == '‌' || c == '‍'
    private fun ignorable(c: Char, enc: TextEncoding) = c.isWhitespace() || isInvisible(c) || (enc == TextEncoding.BASE32 && c == '-') || c == '='

    private fun validPayloadChar(c: Char, enc: TextEncoding) = when (enc) {
        TextEncoding.BASE32 -> decode32(c) >= 0
        TextEncoding.BASE64 -> B64URL.indexOf(c) >= 0 || c == '+' || c == '/'
    }

    // ---- checksum -------------------------------------------------------------------------------

    const val CHECKSUM_CHARS = 7

    fun checksum(bytes: ByteArray): String {
        val crc = CRC32().also { it.update(bytes, 0, bytes.size) }.value
        val sb = StringBuilder()
        for (k in CHECKSUM_CHARS - 1 downTo 0) sb.append(CROCKFORD[((crc ushr (5 * k)) and 31L).toInt()])
        return sb.toString()
    }

    private fun canonical32(s: String) = s.map { CROCKFORD[decode32(it)] }.joinToString("")

    // ---- base32 (Crockford) ----------------------------------------------------------------------

    /** Value of a base32 character, treating O as 0 and I/L as 1; -1 when it is not part of the alphabet. */
    private fun decode32(c: Char): Int {
        val u = c.uppercaseChar()
        val fixed = when (u) { 'O' -> '0'; 'I', 'L' -> '1'; else -> u }
        return CROCKFORD.indexOf(fixed)
    }

    fun base32(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) { sb.append(CROCKFORD[(buffer ushr (bits - 5)) and 31]); bits -= 5 }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(CROCKFORD[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun unbase32(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for ((i, c) in s.withIndex()) {
            val v = decode32(c)
            require(v >= 0) { "invalid character at $i" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) { out.write((buffer ushr (bits - 8)) and 0xFF); bits -= 8 }
            buffer = buffer and ((1 shl bits) - 1)
        }
        require(bits < 5 && buffer == 0) { "length is not a whole number of bytes at ${s.length - 1}" }
        return out.toByteArray()
    }

    // ---- base64 (URL alphabet, no padding) --------------------------------------------------------

    fun base64(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun unbase64(s: String): ByteArray {
        require(s.length % 4 != 1) { "length is not valid at ${s.length - 1}" }
        return try {
            java.util.Base64.getUrlDecoder().decode(s.replace('+', '-').replace('/', '_'))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("not valid base64 at ${s.length - 1}")
        }
    }
}
