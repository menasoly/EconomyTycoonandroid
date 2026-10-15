package app.many.economytycoon.core

import java.security.MessageDigest

/** 256-bit state hash used by replay, golden tests and parity checks. */
class StateHash(private val bytes: ByteArray) {
    init { require(bytes.size == 32) { "hash must be 32 bytes" } }

    fun toByteArray(): ByteArray = bytes.copyOf()

    fun toHex(): String {
        val sb = StringBuilder(64)
        for (b in bytes) {
            sb.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    /** Short form for logs. */
    fun short(): String = toHex().substring(0, 12)

    override fun equals(other: Any?) = other is StateHash && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = toHex()

    companion object {
        private val HEX = "0123456789abcdef".toCharArray()

        fun fromHex(hex: String): StateHash {
            require(hex.length == 64) { "hex hash must have 64 characters" }
            val out = ByteArray(32)
            for (i in 0 until 32) {
                out[i] = ((Character.digit(hex[i * 2], 16) shl 4) or Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
            return StateHash(out)
        }
    }
}

/**
 * Incremental canonical hasher. Every value is written in a fixed big-endian layout with a
 * type tag so that different sequences can never collide by concatenation.
 */
class CanonicalHasher {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val buf = ByteArray(8)

    fun putLong(v: Long): CanonicalHasher {
        digest.update(TAG_LONG)
        for (i in 0 until 8) buf[i] = (v ushr (56 - 8 * i)).toByte()
        digest.update(buf, 0, 8)
        return this
    }

    fun putInt(v: Int): CanonicalHasher = putLong(v.toLong())

    fun putBoolean(v: Boolean): CanonicalHasher = putLong(if (v) 1L else 0L)

    fun putString(v: String): CanonicalHasher {
        digest.update(TAG_STRING)
        val bytes = v.toByteArray(Charsets.UTF_8)
        putRawLength(bytes.size)
        digest.update(bytes)
        return this
    }

    fun putBytes(v: ByteArray): CanonicalHasher {
        digest.update(TAG_BYTES)
        putRawLength(v.size)
        digest.update(v)
        return this
    }

    fun putMoney(v: Money) = putLong(v.raw)
    fun putQuantity(v: Quantity) = putLong(v.raw)
    fun putRate(v: Rate) = putLong(v.ppm)

    /** Begins a named section; section names separate partitions in the hash stream. */
    fun section(name: String): CanonicalHasher {
        digest.update(TAG_SECTION)
        val bytes = name.toByteArray(Charsets.UTF_8)
        putRawLength(bytes.size)
        digest.update(bytes)
        return this
    }

    fun finish(): StateHash = StateHash(digest.digest())

    private fun putRawLength(n: Int) {
        for (i in 0 until 4) buf[i] = (n ushr (24 - 8 * i)).toByte()
        digest.update(buf, 0, 4)
    }

    private companion object {
        val TAG_LONG = byteArrayOf(1)
        val TAG_STRING = byteArrayOf(2)
        val TAG_BYTES = byteArrayOf(3)
        val TAG_SECTION = byteArrayOf(4)
    }
}

/** Implemented by state partitions that contribute to the canonical state hash. */
interface Hashable {
    fun hashInto(hasher: CanonicalHasher)
}
