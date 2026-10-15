package app.many.economytycoon.core

/** Rounding rules used for integer division in simulation arithmetic. */
enum class RoundingMode {
    /** Toward zero. */
    DOWN,
    /** Away from zero. */
    UP,
    /** Toward negative infinity. */
    FLOOR,
    /** Toward positive infinity. */
    CEIL,
    /** Nearest, ties to even. */
    HALF_EVEN,
    /** Nearest, ties away from zero. */
    HALF_UP,
}

/** Integer division a / b with explicit rounding. Throws on division by zero. */
fun divRound(a: Long, b: Long, mode: RoundingMode): Long {
    if (b == 0L) throw ArithmeticException("division by zero")
    val q = a / b
    val r = a % b
    if (r == 0L) return q
    val negative = (a < 0) != (b < 0)
    val direction = if (negative) -1L else 1L
    return when (mode) {
        RoundingMode.DOWN -> q
        RoundingMode.UP -> q + direction
        RoundingMode.FLOOR -> if (negative) q - 1 else q
        RoundingMode.CEIL -> if (negative) q else q + 1
        RoundingMode.HALF_EVEN, RoundingMode.HALF_UP -> {
            val absR = Math.abs(r)
            val absB = Math.abs(b)
            // Compare 2*|r| with |b| without overflow.
            val cmp = if (absR > absB - absR) 1 else if (absR < absB - absR) -1 else 0
            when {
                cmp > 0 -> q + direction
                cmp < 0 -> q
                mode == RoundingMode.HALF_UP -> q + direction
                else -> if (q % 2L != 0L) q + direction else q
            }
        }
    }
}

/**
 * Splits [total] across [weights] so that parts are proportional and always sum exactly to [total].
 * Uses the largest-remainder method; ties are broken by ascending index, which keeps the
 * result deterministic. Negative totals are split symmetrically.
 */
fun splitLargestRemainder(total: Long, weights: LongArray): LongArray {
    require(weights.isNotEmpty()) { "weights must not be empty" }
    var weightSum = 0L
    for (w in weights) {
        require(w >= 0L) { "weights must be non-negative" }
        weightSum = Math.addExact(weightSum, w)
    }
    require(weightSum > 0L) { "weights must have a positive sum" }

    val sign = if (total < 0) -1L else 1L
    val absTotal = Math.abs(total)
    val parts = LongArray(weights.size)
    val remainders = LongArray(weights.size)
    var assigned = 0L
    for (i in weights.indices) {
        val share = mulDiv(absTotal, weights[i], weightSum, RoundingMode.DOWN)
        parts[i] = share
        assigned += share
        remainders[i] = fractionPart(absTotal, weights[i], weightSum)
    }
    var leftover = absTotal - assigned
    // Order indices by remainder descending, then index ascending.
    val order = weights.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
    var k = 0
    while (leftover > 0L) {
        val idx = order[k % order.size]
        if (weights[idx] > 0L) {
            parts[idx] += 1
            leftover--
        }
        k++
    }
    if (sign < 0) for (i in parts.indices) parts[i] = -parts[i]
    return parts
}

/** Fractional part of (a*w)/sum scaled to 1e12, computed exactly via BigInteger. */
private fun fractionPart(a: Long, w: Long, sum: Long): Long {
    val num = java.math.BigInteger.valueOf(a).multiply(java.math.BigInteger.valueOf(w))
    val den = java.math.BigInteger.valueOf(sum)
    val rem = num.mod(den)
    return rem.multiply(java.math.BigInteger.valueOf(1_000_000_000_000L)).divide(den).toLong()
}

/** Clamps [value] into the closed range [min]..[max]. */
fun clampLong(value: Long, min: Long, max: Long): Long {
    require(min <= max) { "min must not exceed max" }
    return if (value < min) min else if (value > max) max else value
}

/** Integer square root, floor. */
fun isqrt(n: Long): Long {
    require(n >= 0) { "negative input" }
    if (n < 2) return n
    var x = Math.sqrt(n.toDouble()).toLong()
    while (x * x > n) x--
    while ((x + 1) * (x + 1) <= n) x++
    return x
}

/** Exponential moving average with [alpha] as a rate: new = prev + alpha*(sample-prev). */
fun ema(previous: Long, sample: Long, alpha: Rate): Long =
    previous + mulDiv(sample - previous, alpha.ppm, Rate.ONE_PPM, RoundingMode.HALF_EVEN)

/** Linear interpolation between a and b by t (rate), rounded half-even. */
fun lerp(a: Long, b: Long, t: Rate): Long = a + mulDiv(b - a, t.ppm, Rate.ONE_PPM, RoundingMode.HALF_EVEN)
