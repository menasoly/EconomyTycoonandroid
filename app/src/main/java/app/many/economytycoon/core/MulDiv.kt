package app.many.economytycoon.core

import java.math.BigInteger

/**
 * Computes (a * b) / c exactly with a 128-bit intermediate and the requested rounding.
 * Throws [ArithmeticException] if the final result does not fit in a Long or c is zero.
 */
fun mulDiv(a: Long, b: Long, c: Long, mode: RoundingMode = RoundingMode.HALF_EVEN): Long {
    if (c == 0L) throw ArithmeticException("division by zero")
    // Fast path: product fits in 63 bits.
    val hi = Math.multiplyHigh(a, b)
    val lo = a * b
    if ((hi == 0L && lo >= 0L) || (hi == -1L && lo < 0L)) {
        return divRound(lo, c, mode)
    }
    val product = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))
    val divisor = BigInteger.valueOf(c)
    val qr = product.divideAndRemainder(divisor)
    var q = qr[0]
    val r = qr[1]
    if (r.signum() != 0) {
        val negative = (product.signum() < 0) != (divisor.signum() < 0)
        q = q.add(BigInteger.valueOf(roundingAdjustment(r, divisor, negative, q, mode)))
    }
    if (q.bitLength() > 63) throw ArithmeticException("mulDiv overflow")
    return q.toLong()
}

private fun roundingAdjustment(
    remainder: BigInteger,
    divisor: BigInteger,
    negative: Boolean,
    truncatedQuotient: BigInteger,
    mode: RoundingMode,
): Long {
    val direction = if (negative) -1L else 1L
    return when (mode) {
        RoundingMode.DOWN -> 0L
        RoundingMode.UP -> direction
        RoundingMode.FLOOR -> if (negative) -1L else 0L
        RoundingMode.CEIL -> if (negative) 0L else 1L
        RoundingMode.HALF_EVEN, RoundingMode.HALF_UP -> {
            val twice = remainder.abs().shiftLeft(1)
            val cmp = twice.compareTo(divisor.abs())
            when {
                cmp > 0 -> direction
                cmp < 0 -> 0L
                mode == RoundingMode.HALF_UP -> direction
                else -> if (truncatedQuotient.testBit(0)) direction else 0L
            }
        }
    }
}
