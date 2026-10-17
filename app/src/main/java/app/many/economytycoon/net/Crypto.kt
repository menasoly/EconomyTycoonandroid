package app.many.economytycoon.net

import java.security.MessageDigest

/** Small helpers for hex text and constant-time comparison. */
object Hex {
    private val DIGITS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(DIGITS[(b.toInt() shr 4) and 0xF]).append(DIGITS[b.toInt() and 0xF])
        return sb.toString()
    }

    /** Decodes lowercase or uppercase hex; null when the text has an odd length or a non-hex character. */
    fun decode(text: String): ByteArray? {
        if (text.length % 2 != 0) return null
        val out = ByteArray(text.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(text[i * 2], 16)
            val lo = Character.digit(text[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}

/**
 * SHA-256 and HMAC-SHA-256 (RFC 2104), built on the same digest the state hash uses. Signing keys are
 * session secrets; verification compares in constant time so a wrong signature leaks nothing about a right one.
 */
object Hmac {
    private const val BLOCK = 64

    fun sha256(vararg parts: ByteArray): ByteArray {
        val d = MessageDigest.getInstance("SHA-256")
        for (p in parts) d.update(p)
        return d.digest()
    }

    fun sha256(data: ByteArray): ByteArray = sha256(*arrayOf(data))

    fun sign(key: ByteArray, data: ByteArray): ByteArray {
        val k = if (key.size > BLOCK) sha256(key) else key
        val ipad = ByteArray(BLOCK) { 0x36 }
        val opad = ByteArray(BLOCK) { 0x5c }
        for (i in k.indices) {
            ipad[i] = (ipad[i].toInt() xor k[i].toInt()).toByte()
            opad[i] = (opad[i].toInt() xor k[i].toInt()).toByte()
        }
        return sha256(opad, sha256(ipad, data))
    }

    /** A signature shortened to [length] bytes for the wire. */
    fun tag(key: ByteArray, data: ByteArray, length: Int = NetLimits.MAC_BYTES): ByteArray = sign(key, data).copyOf(length)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun verify(key: ByteArray, data: ByteArray, tag: ByteArray): Boolean =
        tag.isNotEmpty() && constantTimeEquals(sign(key, data).copyOf(tag.size.coerceAtMost(32)), tag)
}
