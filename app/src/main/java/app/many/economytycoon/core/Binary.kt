package app.many.economytycoon.core

import java.io.ByteArrayOutputStream

/**
 * Canonical binary writer. Integers use zig-zag varints; strings are length-prefixed UTF-8.
 * The layout is part of the save format specification and must stay stable per format version.
 */
class ByteWriter {
    private val out = ByteArrayOutputStream(256)

    fun writeByte(v: Int) { out.write(v and 0xFF) }

    fun writeBoolean(v: Boolean) = writeByte(if (v) 1 else 0)

    fun writeVarLong(value: Long) {
        var v = (value shl 1) xor (value shr 63) // zig-zag
        while ((v and -0x80L) != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    fun writeVarInt(value: Int) = writeVarLong(value.toLong())

    fun writeFixedLong(v: Long) {
        for (i in 0 until 8) out.write((v ushr (56 - 8 * i)).toInt() and 0xFF)
    }

    fun writeString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeVarInt(bytes.size)
        out.write(bytes, 0, bytes.size)
    }

    fun writeBytes(b: ByteArray) {
        writeVarInt(b.size)
        out.write(b, 0, b.size)
    }

    fun writeMoney(m: Money) = writeVarLong(m.raw)
    fun writeQuantity(q: Quantity) = writeVarLong(q.raw)
    fun writeRate(r: Rate) = writeVarLong(r.ppm)

    /** Appends bytes exactly as given, with no length prefix. */
    fun writeBytesRaw(b: ByteArray) { out.write(b, 0, b.size) }

    fun toByteArray(): ByteArray = out.toByteArray()
    val size: Int get() = out.size()
}

class ByteReader(private val data: ByteArray, private var pos: Int = 0) {

    class CorruptDataException(message: String) : RuntimeException(message)

    fun readByte(): Int {
        if (pos >= data.size) throw CorruptDataException("unexpected end of data")
        return data[pos++].toInt() and 0xFF
    }

    fun readBoolean(): Boolean = when (val b = readByte()) {
        0 -> false
        1 -> true
        else -> throw CorruptDataException("invalid boolean $b")
    }

    fun readVarLong(): Long {
        var shift = 0
        var result = 0L
        while (true) {
            val b = readByte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 63) throw CorruptDataException("varint too long")
        }
        return (result ushr 1) xor -(result and 1L)
    }

    fun readVarInt(): Int {
        val v = readVarLong()
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) throw CorruptDataException("int out of range")
        return v.toInt()
    }

    fun readFixedLong(): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or readByte().toLong()
        return v
    }

    fun readString(): String {
        val n = readVarInt()
        if (n < 0 || pos + n > data.size) throw CorruptDataException("bad string length $n")
        val s = String(data, pos, n, Charsets.UTF_8)
        pos += n
        return s
    }

    fun readBytes(): ByteArray {
        val n = readVarInt()
        if (n < 0 || pos + n > data.size) throw CorruptDataException("bad byte length $n")
        val b = data.copyOfRange(pos, pos + n)
        pos += n
        return b
    }

    fun readMoney() = Money(readVarLong())
    fun readQuantity() = Quantity(readVarLong())
    fun readRate() = Rate(readVarLong())

    val remaining: Int get() = data.size - pos
    val position: Int get() = pos
}
